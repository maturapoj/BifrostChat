package com.example.bifrostchat.data

/** One decoded piece of an OpenAI-style `chat.completion.chunk` stream. */
sealed interface StreamEvent {
    /** Thinking tokens (DeepSeek/Qwen send these in `delta.reasoning`). */
    data class Reasoning(val text: String) : StreamEvent

    /** Answer tokens from `delta.content`. */
    data class Content(val text: String) : StreamEvent

    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val reasoningTokens: Int,
    ) : StreamEvent

    data class Finished(val reason: String) : StreamEvent
}
