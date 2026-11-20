package com.sextant.app.agent

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.sextant.app.data.AgentSettingsStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import okio.ByteString

internal class AgentSpeechException(message: String) : IOException(message)

internal class AgentSpeech(private val context: Context, private val settings: AgentSettingsStore) {
  suspend fun readAloud(text: String) {
    val key = settings.read().apiKey
    if (key.isBlank()) throw AgentSpeechException("请先在右上角配置 DeepSeek API Key")
    if (text.toByteArray().size > 64 * 1024) throw AgentSpeechException("这条回复太长，无法朗读")

    val message = buildJsonObject { put("event", "text"); put("text", text) }.toString()
    val request = Request.Builder()
      .url("wss://api.deepseek.com/v1/tools/tts?format=pcm&filter_markdown=true")
      .header("Authorization", "Bearer $key")
      .build()
    withContext(Dispatchers.IO) {
      // Bound the queue when synthesis runs faster than the audio device.
      val audio = Channel<ByteString>(8)
      val socket = client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          if (!webSocket.send(message) || !webSocket.send("""{"event":"finish_text"}""")) {
            audio.close(AgentSpeechException("朗读连接已中断，请重试"))
          }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
          audio.trySendBlocking(bytes)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          val event = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
          if ((event?.get("event") as? JsonPrimitive)?.content != "finish") return
          val error = when ((event["code"] as? JsonPrimitive)?.content) {
            "success" -> null
            "invalid_input", "content_filter" -> "DeepSeek 无法朗读这条回复"
            "input_timeout" -> "朗读请求超时，请重试"
            else -> "DeepSeek 朗读暂时不可用，请稍后重试"
          }
          audio.close(error?.let(::AgentSpeechException))
          webSocket.close(1000, null)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
          audio.close(AgentSpeechException("朗读连接已中断，请重试"))
          webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          val reason = when (response?.code) {
            401, 403 -> "请检查 DeepSeek API Key"
            402 -> "请检查 DeepSeek 账户余额"
            429 -> "朗读请求过多，请稍后重试"
            else -> "朗读连接失败，请检查网络后重试"
          }
          audio.close(AgentSpeechException(reason))
        }
      })
      try {
        val first = nextPacket(audio) ?: throw AgentSpeechException("返回的朗读音频为空，请重试")
        play(first, audio)
      } finally {
        audio.cancel()
        socket.cancel()
      }
    }
  }

  private suspend fun nextPacket(audio: Channel<ByteString>): ByteString? {
    val result = withTimeoutOrNull(60_000) { audio.receiveCatching() }
      ?: throw AgentSpeechException("朗读请求超时，请重试")
    result.exceptionOrNull()?.let { throw it }
    return result.getOrNull()
  }

  private suspend fun play(first: ByteString, audio: Channel<ByteString>) {
    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()
    val manager = context.getSystemService(AudioManager::class.java)
    val playback = currentCoroutineContext().job
    val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(attributes)
      .setOnAudioFocusChangeListener { change -> if (change < 0) playback.cancel() }
      .build()
    if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      throw AgentSpeechException("暂时无法播放朗读音频，请稍后重试")
    }
    try {
      val track = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
      try {
        track.play()
        var packet: ByteString? = first
        var bytesWritten = 0L
        val samples = Buffer()
        while (packet != null) {
          if (bytesWritten + samples.size + packet.size > MAX_AUDIO_BYTES) {
            throw AgentSpeechException("返回的朗读音频无效，请重试")
          }
          samples.write(packet)
          // Keep a trailing half-sample for the next WebSocket message.
          while (samples.size >= 2) {
            val count = minOf(samples.size / 2 * 2, 8192L)
            track.writePcm(samples.readByteArray(count))
            bytesWritten += count
          }
          packet = nextPacket(audio)
        }
        if (!samples.exhausted()) throw AgentSpeechException("返回的朗读音频不完整，请重试")
        if (bytesWritten == 0L) throw AgentSpeechException("返回的朗读音频为空，请重试")
        // A short utterance (or final chunk after an underrun) may need more data
        // to start the device. Wait only for the actual speech to play.
        track.writePcm(ByteArray(track.bufferSizeInFrames * 2))
        while (track.playbackHeadPosition.toLong() < bytesWritten / 2) delay(10)
      } finally {
        try {
          if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
        } finally {
          track.release()
        }
      }
    } finally {
      manager.abandonAudioFocusRequest(focus)
    }
  }

  private suspend fun AudioTrack.writePcm(bytes: ByteArray) {
    var offset = 0
    while (offset < bytes.size) {
      currentCoroutineContext().ensureActive()
      val count = write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
      if (count < 0) throw AgentSpeechException("朗读音频播放失败，请重试")
      if (count == 0) delay(10) else offset += count
    }
  }

  private companion object {
    const val SAMPLE_RATE = 24_000
    const val MAX_AUDIO_BYTES = 30L * 60 * SAMPLE_RATE * 2
    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .connectTimeout(20, TimeUnit.SECONDS)
      .build()
  }
}
