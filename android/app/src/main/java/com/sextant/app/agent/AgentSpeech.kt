package com.sextant.app.agent

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.sextant.app.data.AgentSettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AgentSpeechException(message: String) : IOException(message)

internal class AgentSpeech(private val context: Context, private val settings: AgentSettingsStore) {
  suspend fun readAloud(text: String) {
    val key = settings.read().apiKey
    if (key.isBlank()) throw AgentSpeechException("请先在右上角配置 DeepSeek API Key")

    withContext(Dispatchers.IO) {
      val audio = File.createTempFile("agent-speech-", ".wav", context.cacheDir)
      try {
        downloadSpeech(text, key, audio)
        withContext(Dispatchers.Main) { play(audio) }
      } finally {
        audio.delete()
      }
    }
  }

  private suspend fun downloadSpeech(text: String, key: String, audio: File) {
    val requestBody = buildJsonObject {
      put("text", text)
      put("format", "wav")
      put("filter_markdown", true)
    }.toString()
    if (requestBody.toByteArray(Charsets.UTF_8).size > 64 * 1024) {
      throw AgentSpeechException("这条回复太长，无法朗读")
    }
    HttpClient(OkHttp) {
      followRedirects = false
      install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 30_000
      }
    }.use { client ->
      client.preparePost("https://api.deepseek.com/v1/tools/tts") {
        bearerAuth(key)
        contentType(ContentType.Application.Json)
        setBody(requestBody)
      }.execute { response ->
        when (response.status) {
          HttpStatusCode.OK -> Unit
          HttpStatusCode.BadRequest -> throw AgentSpeechException("DeepSeek 无法朗读这条回复")
          HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw AgentSpeechException("请检查 DeepSeek API Key")
          HttpStatusCode.PaymentRequired -> throw AgentSpeechException("请检查 DeepSeek 账户余额")
          HttpStatusCode.TooManyRequests -> throw AgentSpeechException("朗读请求过多，请稍后重试")
          else -> throw AgentSpeechException("DeepSeek 朗读暂时不可用，请稍后重试")
        }
        audio.outputStream().use { output ->
          val count = response.bodyAsChannel().copyTo(output.channel, MAX_WAV_BYTES + 1)
          if (count <= 44 || count > MAX_WAV_BYTES) throw AgentSpeechException("返回的朗读音频无效，请重试")
        }
      }
    }
  }

  private suspend fun play(audio: File) {
    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()
    val audioManager = context.getSystemService(AudioManager::class.java)
    val playback = currentCoroutineContext().job
    val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(attributes)
      .setOnAudioFocusChangeListener { change -> if (change < 0) playback.cancel() }
      .build()
    if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      throw AgentSpeechException("暂时无法播放朗读音频，请稍后重试")
    }
    try {
      val player = MediaPlayer()
      try {
        player.setAudioAttributes(attributes)
        player.setDataSource(audio.absolutePath)
        suspendCancellableCoroutine<Unit> { continuation ->
          player.setOnPreparedListener {
            if (continuation.isActive) {
              try { player.start() }
              catch (error: IllegalStateException) { continuation.resumeWithException(error) }
            }
          }
          player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
          player.setOnErrorListener { _, _, _ ->
            if (continuation.isActive) continuation.resumeWithException(AgentSpeechException("朗读音频播放失败，请重试"))
            true
          }
          player.prepareAsync()
        }
      } finally {
        player.release()
      }
    } finally {
      audioManager.abandonAudioFocusRequest(focus)
    }
  }

  private companion object {
    // DeepSeek returns at most 30 minutes of 24 kHz, 16-bit mono audio.
    const val MAX_WAV_BYTES = 30L * 60 * 24_000 * 2 + 1024
  }
}
