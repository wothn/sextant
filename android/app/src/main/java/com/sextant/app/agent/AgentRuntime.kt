package com.sextant.app.agent

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/** 每条完整模型响应与每个工具结果落盘；恢复时只执行尚未持久化结果的工具。 */
internal fun streamingLedgerStrategy(
  initialParts: List<MessagePart.RequestPart>? = null,
  appendUser: Boolean = true,
  runContext: String? = null,
  pendingCalls: List<MessagePart.Tool.Call> = emptyList(),
  checkpoint: suspend (List<Message>, Boolean) -> Unit = { _, _ -> },
  prepareRequest: (List<Message>) -> Unit = {},
  completeResponse: (Message.Assistant) -> Message.Assistant = { it },
) = strategy<String, String>("sextant_streaming") {
  var firstMessageCount = 0
  val request by node<String, Flow<StreamFrame>> { input ->
    llm.writeSession {
      if (appendUser) appendPrompt { user(initialParts ?: listOf(MessagePart.Text(input))) }
      firstMessageCount = prompt.messages.size
    }
    pendingCalls.forEach { call ->
      val result = environment.executeTool(call)
      llm.writeSession {
        appendPrompt { user { toolResult(result.toMessagePart()) } }
        checkpoint(prompt.messages.drop(firstMessageCount), false)
      }
    }
    llm.writeSession {
      // 先闭合恢复中的工具调用，再追加本次快照信息；持久化后才能请求模型。
      // 上下文属于本轮协议，后续运行原样重放，不能重写历史前缀。
      if (runContext != null) {
        // DeepSeek 从最后一条用户消息提取链接，应用上下文使用独立的 system 角色。
        appendPrompt { system(runContext) }
        checkpoint(prompt.messages.drop(firstMessageCount), false)
      }
      prepareRequest(prompt.messages)
      requestLLMStreaming()
    }
  }
  val sendResults by node<ReceivedToolResults, Flow<StreamFrame>> {
    llm.writeSession {
      prepareRequest(prompt.messages)
      requestLLMStreaming()
    }
  }
  val collectResponse by node<Flow<StreamFrame>, Message.Assistant> { stream ->
    val response = completeResponse(stream.toList().toMessageResponse())
    llm.writeSession {
      appendPrompt { message(response) }
      checkpoint(prompt.messages.drop(firstMessageCount), response.parts.none { it is MessagePart.Tool.Call })
    }
    response
  }
  val tools by node<ToolCalls, ReceivedToolResults> { calls ->
    val results = calls.toolCalls.map { call ->
      val result = environment.executeTool(call)
      llm.writeSession {
        appendPrompt { user { toolResult(result.toMessagePart()) } }
        checkpoint(prompt.messages.drop(firstMessageCount), false)
      }
      result
    }
    ReceivedToolResults(results)
  }
  edge(nodeStart forwardTo request)
  edge(request forwardTo collectResponse)
  edge(collectResponse forwardTo tools onToolCalls { true })
  edge(collectResponse forwardTo nodeFinish onTextMessage { true })
  edge(tools forwardTo sendResults)
  edge(sendResults forwardTo collectResponse)
}
