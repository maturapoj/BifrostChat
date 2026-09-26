package io.github.maturapoj.tokenflow.presentation.chat.state

import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.ChatSession
import io.github.maturapoj.tokenflow.domain.model.LlmModel
import io.github.maturapoj.tokenflow.domain.model.ModelGroup
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.domain.model.SessionMessage
import io.github.maturapoj.tokenflow.domain.model.StreamStats

data class ChatState(
    val modelGroups: List<ModelGroup> = emptyList(),
    /** Starts as the last used model (from settings); if the endpoint doesn't list it, the first model is picked. */
    val selectedModelId: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val sessions: List<ChatSession> = emptyList(),
    /** null = a new chat that isn't saved yet; it gets an id when the first message is sent. */
    val currentSessionId: Long? = null,
    /** False until an endpoint is set up; the chat then shows a prompt to open Settings. */
    val isConfigured: Boolean = true,
) {
    val selectedModel: LlmModel?
        get() = modelGroups.asSequence().flatMap { it.models }.firstOrNull { it.id == selectedModelId }
}

data class UiMessage(
    val id: Long,
    val role: Role,
    val content: String = "",
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val error: ChatError? = null,
    val stats: StreamStats? = null,
)

/** What the user asked for. The only way into [ChatViewModel]. */
sealed interface ChatIntent {
    data object LoadModels : ChatIntent
    data class SelectModel(val modelId: String) : ChatIntent
    data class Send(val text: String) : ChatIntent
    data object Stop : ChatIntent
    data object NewChat : ChatIntent
    data class OpenSession(val id: Long) : ChatIntent
    data class DeleteSession(val id: Long) : ChatIntent
}

/** One-off events for the UI that should not survive in state. */
sealed interface ChatEffect {
    data class ModelsFailed(val error: ChatError) : ChatEffect
}

/** What happened, as input to [reduce]. */
sealed interface ChatResult {
    data class ModelsLoaded(val groups: List<ModelGroup>) : ChatResult
    data class ModelSelected(val modelId: String) : ChatResult
    data class StreamStarted(val user: UiMessage, val assistantId: Long) : ChatResult
    /** Latest snapshot of the reply; the domain folds stream events into it. */
    data class ReplyUpdated(val assistantId: Long, val reply: SessionMessage) : ChatResult
    /** The send failed before the stream could report it on the reply (e.g. saving to the database). */
    data class ReplyFailed(val assistantId: Long, val error: ChatError) : ChatResult
    data class StreamEnded(val assistantId: Long) : ChatResult
    data class SessionsUpdated(val sessions: List<ChatSession>) : ChatResult
    data class SessionCreated(val id: Long) : ChatResult
    data class SessionOpened(val id: Long, val modelId: String, val messages: List<UiMessage>) : ChatResult
    data object NewChatStarted : ChatResult
    data class SettingsChanged(val isConfigured: Boolean, val lastModelId: String) : ChatResult
}

fun SessionMessage.toUi() = UiMessage(id = id, role = role, content = content, reasoning = reasoning, error = error, stats = stats)
