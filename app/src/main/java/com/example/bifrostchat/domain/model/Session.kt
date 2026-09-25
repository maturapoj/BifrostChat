package com.example.bifrostchat.domain.model
import kotlin.time.Duration
import kotlin.time.Instant

data class ChatSession(
    val id: Long,
    val title: String,
    val modelId: String,
    val updatedAt: Instant,
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
    val timeToFirstToken: Duration? = null,
    /** UI updates after coalescing, not raw SSE chunks. */
    val chunks: Int = 0,
    val completionTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val total: Duration? = null,
    /** completion tokens / whole request time; the gateway delivers chunks in bursts, so per-chunk timing is meaningless. */
    val tokensPerSecond: Double? = null,
)
