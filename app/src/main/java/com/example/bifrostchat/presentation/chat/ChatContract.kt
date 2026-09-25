package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent

data class ChatState(
    val modelGroups: List<ModelGroup> = emptyList(),
    val selectedModelId: String = DEFAULT_MODEL_ID,
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
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

data class StreamStats(
    val timeToFirstTokenMs: Long? = null,
    /** UI updates after coalescing, not raw SSE chunks. */
    val chunks: Int = 0,
    val completionTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val totalMs: Long? = null,
    /** completion tokens / whole request time; the gateway delivers chunks in bursts, so per-chunk timing is meaningless. */
    val tokensPerSecond: Double? = null,
)

/** What the user asked for. The only way into [ChatViewModel]. */
sealed interface ChatIntent {
    data object LoadModels : ChatIntent
    data class SelectModel(val modelId: String) : ChatIntent
    data class Send(val text: String) : ChatIntent
    data object Stop : ChatIntent
    data object Clear : ChatIntent
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
    data object Cleared : ChatResult
}
