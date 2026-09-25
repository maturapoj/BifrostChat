package com.example.bifrostchat.presentation.chat.state

import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.model.StreamStats
import kotlin.time.Duration
import kotlin.time.DurationUnit

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
        msg.applyEvent(result.event, result.elapsed)
    }

    is ChatResult.StreamEnded -> state
        .updateMessage(result.assistantId) { it.copy(isStreaming = false, error = result.error) }
        .copy(isStreaming = false)

    is ChatResult.SessionsUpdated -> state.copy(sessions = result.sessions)

    is ChatResult.SessionCreated -> state.copy(currentSessionId = result.id)

    is ChatResult.SessionOpened -> state.copy(
        currentSessionId = result.id,
        selectedModelId = result.modelId,
        messages = result.messages,
        isStreaming = false,
    )

    ChatResult.NewChatStarted -> state.copy(currentSessionId = null, messages = emptyList(), isStreaming = false)
}

private fun UiMessage.applyEvent(event: StreamEvent, elapsed: Duration): UiMessage {
    val s = stats ?: StreamStats()
    return when (event) {
        is StreamEvent.Reasoning -> copy(reasoning = reasoning + event.text, stats = s.onToken(elapsed))
        is StreamEvent.Content -> copy(content = content + event.text, stats = s.onToken(elapsed))
        is StreamEvent.Usage -> copy(
            stats = s.copy(
                completionTokens = event.completionTokens,
                reasoningTokens = event.reasoningTokens,
                total = elapsed,
                tokensPerSecond = if (elapsed.isPositive()) event.completionTokens / elapsed.toDouble(DurationUnit.SECONDS) else null,
            ),
        )
        is StreamEvent.Finished -> this
    }
}

private fun StreamStats.onToken(elapsed: Duration) =
    copy(timeToFirstToken = timeToFirstToken ?: elapsed, chunks = chunks + 1)

private fun ChatState.updateMessage(id: Long, transform: (UiMessage) -> UiMessage) =
    copy(messages = messages.map { if (it.id == id) transform(it) else it })
