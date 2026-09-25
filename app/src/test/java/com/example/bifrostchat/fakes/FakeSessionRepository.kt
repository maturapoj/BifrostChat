package com.example.bifrostchat.fakes

import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.repository.SessionRepository
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory SessionRepository; `time` ticks on every write so ordering is deterministic. */
class FakeSessionRepository : SessionRepository {
    private var time = 0L
    private var nextSessionId = 1L
    private var nextMessageId = 1L
    private val sessions = MutableStateFlow<Map<Long, ChatSession>>(emptyMap())
    val messages = mutableMapOf<Long, MutableList<SessionMessage>>()

    /** When set, getMessages suspends until it completes, to simulate a slow load. */
    var loadGate: CompletableDeferred<Unit>? = null

    fun seed(title: String, modelId: String, vararg msgs: SessionMessage): Long {
        val id = nextSessionId++
        sessions.value += id to ChatSession(id, title, modelId, tick())
        messages[id] = msgs.map { it.copy(id = nextMessageId++) }.toMutableList()
        return id
    }

    override fun observeSessions(): Flow<List<ChatSession>> =
        sessions.map { it.values.sortedByDescending(ChatSession::updatedAt) }

    override suspend fun getSession(id: Long) = sessions.value[id]

    override suspend fun createSession(title: String, modelId: String): Long {
        val id = nextSessionId++
        sessions.value += id to ChatSession(id, title, modelId, tick())
        messages[id] = mutableListOf()
        return id
    }

    override suspend fun setTitle(id: Long, title: String) = update(id) { it.copy(title = title) }
    override suspend fun setModel(id: Long, modelId: String) = update(id) { it.copy(modelId = modelId) }

    override suspend fun deleteSession(id: Long) {
        sessions.value -= id
        messages.remove(id)
    }

    override suspend fun getMessages(sessionId: Long): List<SessionMessage> {
        loadGate?.await()
        return messages[sessionId].orEmpty().toList()
    }

    override suspend fun addMessage(sessionId: Long, message: SessionMessage): Long {
        val id = nextMessageId++
        messages.getValue(sessionId) += message.copy(id = id)
        update(sessionId) { it.copy(updatedAt = tick()) }
        return id
    }

    private fun tick() = Instant.fromEpochMilliseconds(++time)

    private fun update(id: Long, transform: (ChatSession) -> ChatSession) {
        sessions.value[id]?.let { sessions.value += id to transform(it) }
    }
}
