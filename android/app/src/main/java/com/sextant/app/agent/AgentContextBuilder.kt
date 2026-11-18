package com.sextant.app.agent

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import com.sextant.app.data.AgentConversation
import com.sextant.app.data.AgentImageStore
import com.sextant.app.data.AgentTurnTranscript
import kotlinx.serialization.json.Json

/** 协议记录独立于 UI trace；不裁剪 reasoning、调用参数或工具结果。 */
internal object AgentTranscriptCodec {
  private val json = Json { ignoreUnknownKeys = true }
  fun encode(messages: List<Message>) = messages.map { json.encodeToJsonElement(Message.serializer(), it) }
  fun decode(turn: AgentTurnTranscript): List<Message> {
    require(turn.formatVersion == 1) { "对话协议版本不受支持，请升级应用" }
    return turn.messages.map { json.decodeFromJsonElement(Message.serializer(), it) }
  }
  fun pendingCalls(messages: List<Message>): List<MessagePart.Tool.Call> {
    val lastAssistant = messages.indexOfLast { it is Message.Assistant }
    if (lastAssistant < 0) return emptyList()
    val results = messages.drop(lastAssistant + 1).flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().map { it.id }.toSet()
    return messages[lastAssistant].parts.filterIsInstance<MessagePart.Tool.Call>().filter { it.id !in results }
  }
}

internal class AgentContextBuilder(private val images: AgentImageStore) {
  suspend fun build(conversation: AgentConversation, documentMarkers: Map<String, String> = emptyMap()): List<Message> {
    val userParts = agentMessageParts(conversation.messages, images, documentMarkers)
    val turns = conversation.transcript.associateBy { it.userMessageId }
    val recordedRuns = conversation.transcript.map { it.runId }.toSet()
    return buildList {
      conversation.messages.forEach { item ->
        when (item.role) {
          "user" -> {
            add(Message.User(userParts.getValue(item.id), RequestMetaInfo.Empty))
            turns[item.id]?.let { turn ->
              val protocol = AgentTranscriptCodec.decode(turn)
              addAll(protocol)
              if (turn.runId != conversation.runId || !conversation.running) {
                // 用户已开始新请求，不自动执行上次未完成的工具；明确回传中断错误并闭合协议。
                val missing = AgentTranscriptCodec.pendingCalls(protocol)
                if (missing.isNotEmpty()) add(Message.User(missing.map {
                  MessagePart.Tool.Result(it.id, it.tool, "工具执行中断，结果未保存；请按用户最新请求重新查询或生成草稿。", isError = true)
                }, RequestMetaInfo.Empty))
              }
            }
          }
          "assistant" -> if (item.runId !in recordedRuns) {
            // 老版本没有保存 reasoning，不能伪造一个符合 DeepSeek 工具协议的 assistant。
            add(Message.User("旧版对话中的助手回复（原始工具协议已缺失，仅供参考）：${item.text}", RequestMetaInfo.Empty))
          }
          else -> add(Message.User("应用操作回执：${item.text}", RequestMetaInfo.Empty))
        }
      }
    }
  }
}
