package com.example.bifrostchat.presentation.chat.state

import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamStats

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

    is ChatResult.ReplyUpdated -> state.updateMessage(result.assistantId) {
        it.copy(content = result.reply.content, reasoning = result.reply.reasoning, stats = result.reply.stats, error = result.reply.error)
    }

    is ChatResult.ReplyFailed -> state.updateMessage(result.assistantId) { it.copy(error = result.error) }

    is ChatResult.StreamEnded -> state
        .updateMessage(result.assistantId) { it.copy(isStreaming = false) }
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

private fun ChatState.updateMessage(id: Long, transform: (UiMessage) -> UiMessage) =
    copy(messages = messages.map { if (it.id == id) transform(it) else it })
