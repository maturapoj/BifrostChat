package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent

fun reduce(state: ChatState, result: ChatResult): ChatState = when (result) {
    is ChatResult.ModelsLoaded -> {
        val ids = result.groups.flatMap { g -> g.models.map { it.id } }
        state.copy(
            modelGroups = result.groups,
            selectedModelId = state.selectedModelId.takeIf { it in ids } ?: ids.firstOrNull().orEmpty(),
        )
    }

    is ChatResult.ModelSelected -> state.copy(selectedModelId = result.modelId)

    is ChatResult.StreamStarted -> state.copy(
        messages = state.messages + result.user +
            UiMessage(id = result.assistantId, role = Role.Assistant, isStreaming = true, stats = StreamStats()),
        isStreaming = true,
    )

    is ChatResult.StreamEventReceived -> state.updateMessage(result.assistantId) { msg ->
        msg.applyEvent(result.event, result.elapsedMs)
    }

    is ChatResult.StreamEnded -> state
        .updateMessage(result.assistantId) { it.copy(isStreaming = false, error = result.error) }
        .copy(isStreaming = false)

    ChatResult.Cleared -> state.copy(messages = emptyList(), isStreaming = false)
}

private fun UiMessage.applyEvent(event: StreamEvent, elapsedMs: Long): UiMessage {
    val s = stats ?: StreamStats()
    return when (event) {
        is StreamEvent.Reasoning -> copy(reasoning = reasoning + event.text, stats = s.onToken(elapsedMs))
        is StreamEvent.Content -> copy(content = content + event.text, stats = s.onToken(elapsedMs))
        is StreamEvent.Usage -> copy(
            stats = s.copy(
                completionTokens = event.completionTokens,
                reasoningTokens = event.reasoningTokens,
                totalMs = elapsedMs,
                tokensPerSecond = if (elapsedMs > 0) event.completionTokens / (elapsedMs / 1000.0) else null,
            ),
        )
        is StreamEvent.Finished -> this
    }
}

private fun StreamStats.onToken(elapsedMs: Long) =
    copy(timeToFirstTokenMs = timeToFirstTokenMs ?: elapsedMs, chunks = chunks + 1)

private fun ChatState.updateMessage(id: Long, transform: (UiMessage) -> UiMessage) =
    copy(messages = messages.map { if (it.id == id) transform(it) else it })
