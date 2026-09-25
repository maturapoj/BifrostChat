package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.model.StreamStats

data class ChatState(
    val modelGroups: List<ModelGroup> = emptyList(),
    val selectedModelId: String = DEFAULT_MODEL_ID,
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val sessions: List<ChatSession> = emptyList(),
    /** null = a new chat that isn't saved yet; it gets an id when the first message is sent. */
    val currentSessionId: Long? = null,
) {
    val selectedModel: LlmModel?
        get() = modelGroups.asSequence().flatMap { it.models }.firstOrNull { it.id == selectedModelId }

    companion object {
        const val DEFAULT_MODEL_ID = "dashscope/deepseek-v4-flash-0731"
    }
}

data class UiMessage(
    val id: Long,
    val role: Role,
    val content: String = "",
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
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
    data class ModelsFailed(val message: String) : ChatEffect
}

/** What happened, as input to [reduce]. Timing is passed in so the reducer stays pure. */
sealed interface ChatResult {
    data class ModelsLoaded(val groups: List<ModelGroup>) : ChatResult
    data class ModelSelected(val modelId: String) : ChatResult
    data class StreamStarted(val user: UiMessage, val assistantId: Long) : ChatResult
    data class StreamEventReceived(val assistantId: Long, val event: StreamEvent, val elapsedMs: Long) : ChatResult
    data class StreamEnded(val assistantId: Long, val error: String?) : ChatResult
    data class SessionsUpdated(val sessions: List<ChatSession>) : ChatResult
    data class SessionCreated(val id: Long) : ChatResult
    data class SessionOpened(val id: Long, val modelId: String, val messages: List<UiMessage>) : ChatResult
    data object NewChatStarted : ChatResult
}

fun SessionMessage.toUi() = UiMessage(id = id, role = role, content = content, reasoning = reasoning, error = error, stats = stats)

fun UiMessage.toSessionMessage() =
    SessionMessage(id = id, role = role, content = content, reasoning = reasoning, error = error, stats = stats)
