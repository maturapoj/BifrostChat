package com.example.bifrostchat.domain.model

data class ChatSession(
    val id: Long,
    val title: String,
    val modelId: String,
    val updatedAt: Long,
)

/** A message as stored in a session. */
data class SessionMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val reasoning: String = "",
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
