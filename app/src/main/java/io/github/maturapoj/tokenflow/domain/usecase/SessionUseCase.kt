package io.github.maturapoj.tokenflow.domain.usecase

import io.github.maturapoj.tokenflow.domain.model.ChatSession
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.domain.model.SessionMessage
import io.github.maturapoj.tokenflow.domain.repository.SessionRepository

data class LoadedSession(val session: ChatSession, val messages: List<SessionMessage>)

/**
 * Session operations that carry logic. Plain reads and writes (observe, create,
 * delete, set model) go straight to [SessionRepository].
 */
class SessionUseCase(private val repository: SessionRepository) {

    suspend fun load(id: Long): LoadedSession? =
        repository.getSession(id)?.let { LoadedSession(it, repository.getMessages(id)) }

    /** Appends [message]; the first user message also becomes the chat's title. */
    suspend fun saveMessage(sessionId: Long, message: SessionMessage): Long {
        val id = repository.addMessage(sessionId, message)
        if (message.role == Role.User && repository.getSession(sessionId)?.title.isNullOrEmpty()) {
            repository.setTitle(sessionId, titleFrom(message.content))
        }
        return id
    }

    companion object {
        private const val MAX_TITLE = 40

        /** First line of the first user message, cut at a word boundary where possible. */
        fun titleFrom(text: String): String {
            val line = text.trim().lineSequence().first().trim()
            if (line.length <= MAX_TITLE) return line
            val cut = line.take(MAX_TITLE).substringBeforeLast(' ').ifEmpty { line.take(MAX_TITLE) }
            return cut.trimEnd() + "…"
        }
    }
}
