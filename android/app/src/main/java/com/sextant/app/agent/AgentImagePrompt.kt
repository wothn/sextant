package com.sextant.app.agent

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.MessagePart
import com.sextant.app.data.AgentImageStore
import com.sextant.app.data.AgentMessage

/** 限制每次请求的图片体积；旧图片仍在历史中可查看，省略时向模型明确说明。 */
internal suspend fun agentMessageParts(history: List<AgentMessage>, images: AgentImageStore,
  documentMarkers: Map<String, String> = emptyMap()): Map<String, List<MessagePart.RequestPart>> {
  val included = history.filter { it.role == "user" }.flatMap { message -> message.images.indices.map { message.id to it } }
    .takeLast(AgentImageStore.MAX_CONTEXT_IMAGES).toSet()
  return history.filter { it.role == "user" }.associate { message ->
    message.id to buildList {
      val text = message.text.take(6000).ifBlank {
        if (message.documents.isEmpty()) "请查看图片，帮我分析其中的信息。" else "请查看附件，帮我分析其中的信息。"
      }
      val documents = message.documents.joinToString("") { document ->
        documentMarkers[document.id] ?: "\n[附件 ${document.name} 未包含在本次上下文中；需要查看原文件时，请用户重新发送。]"
      }
      add(MessagePart.Text(text + documents))
      message.images.forEachIndexed { index, id ->
        if (message.id to index in included) add(MessagePart.Attachment(AttachmentSource.Image(
          content = AttachmentContent.Binary.Bytes(images.read(id)), format = "jpg", mimeType = "image/jpeg", fileName = "$id.jpg")))
        else add(MessagePart.Text("[较早的图片未包含在本次上下文中；需要查看时请用户重新发送，不要猜测图片内容。]"))
      }
    }
  }
}
