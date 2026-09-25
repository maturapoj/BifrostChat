package com.example.bifrostchat.data.repository

import com.example.bifrostchat.data.local.ChatDao
import com.example.bifrostchat.data.local.MessageEntity
import com.example.bifrostchat.data.local.SessionEntity
import com.example.bifrostchat.data.local.StatsEntity
import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.model.StreamStats
import com.example.bifrostchat.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val dao: ChatDao,
    private val now: () -> Long = System::currentTimeMillis,
) : SessionRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        dao.observeSessions().map { list -> list.map { it.toDomain() } }

    override suspend fun getSession(id: Long): ChatSession? = dao.session(id)?.toDomain()

    override suspend fun createSession(title: String, modelId: String): Long {
        val time = now()
        return dao.insertSession(SessionEntity(title = title, modelId = modelId, createdAt = time, updatedAt = time))
    }

    override suspend fun setTitle(id: Long, title: String) = dao.setTitle(id, title)
    override suspend fun setModel(id: Long, modelId: String) = dao.setModel(id, modelId)
    override suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    override suspend fun getMessages(sessionId: Long): List<SessionMessage> =
        dao.messages(sessionId).map { it.toDomain() }

    override suspend fun addMessage(sessionId: Long, message: SessionMessage): Long =
        dao.addMessage(message.toEntity(sessionId, now()))
}

private fun SessionEntity.toDomain() = ChatSession(id, title, modelId, updatedAt)

private fun MessageEntity.toDomain() = SessionMessage(
    id = id,
    role = if (role == "user") Role.User else Role.Assistant,
    content = content,
    reasoning = reasoning,
    error = error,
    stats = stats?.let {
        StreamStats(it.timeToFirstTokenMs, it.chunks ?: 0, it.completionTokens, it.reasoningTokens, it.totalMs, it.tokensPerSecond)
    },
)

private fun SessionMessage.toEntity(sessionId: Long, time: Long) = MessageEntity(
    sessionId = sessionId,
    role = if (role == Role.User) "user" else "assistant",
    content = content,
    reasoning = reasoning,
    error = error,
    stats = stats?.let {
        StatsEntity(it.timeToFirstTokenMs, it.chunks, it.completionTokens, it.reasoningTokens, it.totalMs, it.tokensPerSecond)
    },
    createdAt = time,
)
