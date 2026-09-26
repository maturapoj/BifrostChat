package com.example.bifrostchat.data.repository

import com.example.bifrostchat.data.local.ChatDao
import com.example.bifrostchat.data.local.MessageEntity
import com.example.bifrostchat.data.local.SessionEntity
import com.example.bifrostchat.data.local.StatsEntity
import com.example.bifrostchat.data.remote.roleFromWire
import com.example.bifrostchat.data.remote.wire
import com.example.bifrostchat.domain.model.ChatError
import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.model.StreamStats
import com.example.bifrostchat.domain.repository.SessionRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val dao: ChatDao,
    private val clock: Clock = Clock.System,
) : SessionRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        dao.observeSessions().map { list -> list.map { it.toDomain() } }

    override suspend fun getSession(id: Long): ChatSession? = dao.session(id)?.toDomain()

    override suspend fun createSession(title: String, modelId: String): Long {
        val time = clock.now().toEpochMilliseconds()
        return dao.insertSession(SessionEntity(title = title, modelId = modelId, createdAt = time, updatedAt = time))
    }

    override suspend fun setTitle(id: Long, title: String) = dao.setTitle(id, title)
    override suspend fun setModel(id: Long, modelId: String) = dao.setModel(id, modelId)
    override suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    override suspend fun getMessages(sessionId: Long): List<SessionMessage> =
        dao.messages(sessionId).map { it.toDomain() }

    override suspend fun addMessage(sessionId: Long, message: SessionMessage): Long =
        dao.addMessage(message.toEntity(sessionId, clock.now().toEpochMilliseconds()))

    override suspend fun updateMessage(message: SessionMessage) {
        val (kind, code, detail) = message.error.toColumns()
        val s = message.stats
        dao.updateMessage(
            message.id, message.content, message.reasoning, detail, kind, code,
            s?.timeToFirstToken?.inWholeMilliseconds, s?.chunks, s?.completionTokens, s?.reasoningTokens,
            s?.total?.inWholeMilliseconds, s?.tokensPerSecond,
        )
    }

    override suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)
}

private fun SessionEntity.toDomain() = ChatSession(id, title, modelId, Instant.fromEpochMilliseconds(updatedAt))

private fun MessageEntity.toDomain() = SessionMessage(
    id = id,
    role = roleFromWire(role),
    content = content,
    reasoning = reasoning,
    error = errorFromColumns(errorKind, errorCode, error),
    stats = stats?.let {
        StreamStats(it.timeToFirstTokenMs?.milliseconds, it.chunks ?: 0, it.completionTokens, it.reasoningTokens, it.totalMs?.milliseconds, it.tokensPerSecond)
    },
)

private fun SessionMessage.toEntity(sessionId: Long, time: Long): MessageEntity {
    val (kind, code, detail) = error.toColumns()
    return MessageEntity(
        sessionId = sessionId,
        role = role.wire,
        content = content,
        reasoning = reasoning,
        error = detail,
        errorKind = kind,
        errorCode = code,
        stats = stats?.let {
            StatsEntity(
                it.timeToFirstToken?.inWholeMilliseconds, it.chunks, it.completionTokens, it.reasoningTokens,
                it.total?.inWholeMilliseconds, it.tokensPerSecond,
            )
        },
        createdAt = time,
    )
}

private data class ErrorColumns(val kind: String?, val code: Int?, val detail: String?)

private fun ChatError?.toColumns(): ErrorColumns = when (this) {
    null -> ErrorColumns(null, null, null)
    ChatError.Network -> ErrorColumns("network", null, null)
    ChatError.Unauthorized -> ErrorColumns("unauthorized", null, null)
    ChatError.RateLimited -> ErrorColumns("rate_limited", null, null)
    is ChatError.Server -> ErrorColumns("server", code, detail)
    is ChatError.Gateway -> ErrorColumns("gateway", null, detail)
    is ChatError.Unknown -> ErrorColumns("unknown", null, detail)
}

/** Rows saved by schema v1 have only the detail text; they read back as [ChatError.Unknown]. */
private fun errorFromColumns(kind: String?, code: Int?, detail: String?): ChatError? = when (kind) {
    "network" -> ChatError.Network
    "unauthorized" -> ChatError.Unauthorized
    "rate_limited" -> ChatError.RateLimited
    "server" -> ChatError.Server(code ?: 0, detail.orEmpty())
    "gateway" -> ChatError.Gateway(detail.orEmpty())
    "unknown" -> ChatError.Unknown(detail.orEmpty())
    else -> detail?.let { ChatError.Unknown(it) }
}
