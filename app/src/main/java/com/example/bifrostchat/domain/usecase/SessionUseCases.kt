package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class ObserveSessionsUseCase(private val repository: SessionRepository) {
    operator fun invoke(): Flow<List<ChatSession>> = repository.observeSessions()
}

data class LoadedSession(val session: ChatSession, val messages: List<SessionMessage>)

class LoadSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(id: Long): LoadedSession? =
        repository.getSession(id)?.let { LoadedSession(it, repository.getMessages(id)) }
}

class CreateSessionUseCase(private val repository: SessionRepository) {
    /** Starts untitled; [SaveMessageUseCase] names it after the first user message. */
    suspend operator fun invoke(modelId: String): Long = repository.createSession(title = "", modelId = modelId)
}

class DeleteSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteSession(id)
}

class SetSessionModelUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(id: Long, modelId: String) = repository.setModel(id, modelId)
}

class SaveMessageUseCase(private val repository: SessionRepository) {

    suspend operator fun invoke(sessionId: Long, message: SessionMessage): Long {
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
