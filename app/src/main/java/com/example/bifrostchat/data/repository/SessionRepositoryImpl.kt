package com.example.bifrostchat.data.repository

import com.example.bifrostchat.data.local.ChatDao
import com.example.bifrostchat.data.local.MessageEntity
import com.example.bifrostchat.data.local.SessionEntity
import com.example.bifrostchat.data.local.StatsEntity
import com.example.bifrostchat.data.remote.roleFromWire
import com.example.bifrostchat.data.remote.wire
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
}

private fun SessionEntity.toDomain() = ChatSession(id, title, modelId, Instant.fromEpochMilliseconds(updatedAt))

private fun MessageEntity.toDomain() = SessionMessage(
    id = id,
    role = roleFromWire(role),
    content = content,
    reasoning = reasoning,
    error = error,
    stats = stats?.let {
        StreamStats(it.timeToFirstTokenMs?.milliseconds, it.chunks ?: 0, it.completionTokens, it.reasoningTokens, it.totalMs?.milliseconds, it.tokensPerSecond)
    },
)

private fun SessionMessage.toEntity(sessionId: Long, time: Long) = MessageEntity(
    sessionId = sessionId,
    role = role.wire,
    content = content,
    reasoning = reasoning,
    error = error,
    stats = stats?.let {
        StatsEntity(
            it.timeToFirstToken?.inWholeMilliseconds, it.chunks, it.completionTokens, it.reasoningTokens,
            it.total?.inWholeMilliseconds, it.tokensPerSecond,
        )
    },
    createdAt = time,
)
