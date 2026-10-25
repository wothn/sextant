package com.sextant.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.message.MessagePart
import com.sextant.app.agent.agentMessageParts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentImagesTest {
  private lateinit var context: Context
  private lateinit var images: AgentImageStore
  private lateinit var source: File
  private val imported = mutableListOf<String>()

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    images = AgentImageStore(context)
    source = File(context.cacheDir, "agent-image-test.png")
    File(context.noBackupFilesDir, "agent-images-test.json").delete()
  }

  @After fun tearDown() = runBlocking {
    imported.forEach { images.removePending(it) }
    source.delete()
    File(context.noBackupFilesDir, "agent-images-test.json").delete()
    Unit
  }

  private suspend fun image(width: Int = 32, height: Int = 20): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try { source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    finally { bitmap.recycle() }
    return images.import(Uri.fromFile(source)).also { imported += it }
  }

  @Test fun imageIsCopiedCompressedAndStillReadableWithoutOriginalUri() = runBlocking {
    val id = image(3000, 1200)
    source.delete()
    val bytes = images.read(id)
    assertEquals(0xff, bytes[0].toInt() and 0xff)
    assertEquals(0xd8, bytes[1].toInt() and 0xff)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    assertEquals(2048, options.outWidth)
    assertEquals(819, options.outHeight)
    assertTrue(bytes.size <= 2 * 1024 * 1024)
    assertTrue(images.thumbnail(id).width <= 1024)
  }

  @Test fun invalidFilesMissingImagesAndPathTraversalAreRejected() = runBlocking {
    source.writeText("not an image")
    assertEquals("图片格式无效或分辨率过大，请换一张图片", runCatching { images.import(Uri.fromFile(source)) }.exceptionOrNull()?.message)
    assertEquals("无效的图片标识", runCatching { images.read("../agent-settings.enc") }.exceptionOrNull()?.message)
    assertEquals("图片已不可用，请重新选择后发送", runCatching { images.read(UUID.randomUUID().toString()) }.exceptionOrNull()?.message)
  }

  @Test fun oversizedSourceIsRejectedBeforeCreatingAnAttachment() = runBlocking {
    RandomAccessFile(source, "rw").use { it.setLength(21L * 1024 * 1024) }
    assertEquals("图片超过20MB，请选择较小的图片", runCatching { images.import(Uri.fromFile(source)) }.exceptionOrNull()?.message)
  }

  @Test fun imageOnlyMessageAndAttachmentSurviveHistoryAndRestart() = runBlocking {
    val id = image()
    val message = AgentMessage("m", "user", "", "run", listOf(id))
    val store = AgentConversationStore(context, "agent-images-test.json")
    store.update { it.copy(messages = listOf(message)) }
    val sessionId = requireNotNull(store.activeId.value)
    assertEquals("[图片]", store.history.value.single().title)
    store.newConversation()
    assertTrue(store.containsImage(id))
    val restored = AgentConversationStore(context, "agent-images-test.json")
    restored.openConversation(sessionId)
    assertEquals(message, restored.load().messages.single())
    assertArrayEquals(images.read(id), AgentImageStore(context).read(id))
    val parts = agentMessageParts(listOf(message), images).getValue("m")
    assertTrue(parts.filterIsInstance<MessagePart.Text>().single().text.isNotBlank())
    assertEquals(1, parts.filterIsInstance<MessagePart.Attachment>().size)
  }

  @Test fun onlyMostRecentEightImagesAreSentAndOmittedImagesAreExplained() = runBlocking {
    val id = image()
    val history = (1..9).map { AgentMessage("m$it", "user", "查看第${it}张图片", images = listOf(id)) }
    val parts = agentMessageParts(history, images)
    assertEquals(8, parts.values.sumOf { it.filterIsInstance<MessagePart.Attachment>().size })
    assertEquals(0, parts.getValue("m1").filterIsInstance<MessagePart.Attachment>().size)
    assertTrue(parts.getValue("m1").filterIsInstance<MessagePart.Text>().last().text.contains("较早的图片未包含"))
    assertEquals(1, parts.getValue("m9").filterIsInstance<MessagePart.Attachment>().size)
  }

  @Test fun missingAttachmentFailsInsteadOfSilentlySendingTextOnly() = runBlocking {
    val message = AgentMessage("m", "user", "看图", images = listOf(UUID.randomUUID().toString()))
    assertEquals("图片已不可用，请重新选择后发送", runCatching { agentMessageParts(listOf(message), images) }.exceptionOrNull()?.message)
  }

  @Test fun deepSeekRequestCarriesActualImageBytesInUserContent() = runBlocking {
    val id = image()
    val parts = agentMessageParts(listOf(AgentMessage("m", "user", "这是什么图片", images = listOf(id))), images).getValue("m")
    var body: JSONObject? = null
    val engine = MockEngine { request ->
      body = JSONObject((request.body as OutgoingContent.ByteArrayContent).bytes().toString(Charsets.UTF_8))
      respond("""{"id":"test","object":"chat.completion","created":1,"model":"deepseek-flash","system_fingerprint":"test","choices":[{"index":0,"message":{"role":"assistant","content":"已读取图片"},"finish_reason":"stop"}]}""",
        headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val http = HttpClient(engine)
    val client = DeepSeekLLMClient(apiKey = "test-key", httpClientFactory = KtorKoogHttpClient.Factory(http))
    try {
      client.execute(prompt("image-test") { user(parts) }, DeepSeekModels.DeepSeekV4FlashVisionExp.copy(id = "deepseek-flash"), emptyList())
      val request = requireNotNull(body)
      assertEquals("deepseek-flash", request.getString("model"))
      val user = request.getJSONArray("messages").let { messages ->
        (0 until messages.length()).map { messages.getJSONObject(it) }.single { it.getString("role") == "user" }
      }
      val content = user.getJSONArray("content")
      assertEquals("这是什么图片", content.getJSONObject(0).getString("text"))
      val url = content.getJSONObject(1).getJSONObject("image_url").getString("url")
      assertTrue(url.startsWith("data:image/jpeg;base64,"))
      assertArrayEquals(images.read(id), java.util.Base64.getDecoder().decode(url.substringAfter(',')))
      assertFalse(request.toString().contains(context.noBackupFilesDir.path))
    } finally { client.close(); http.close() }
  }
}
