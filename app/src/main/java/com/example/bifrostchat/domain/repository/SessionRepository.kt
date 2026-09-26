package com.example.bifrostchat.domain.repository

import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.domain.model.SessionMessage
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    /** Most recently updated first. */
    fun observeSessions(): Flow<List<ChatSession>>

    suspend fun getSession(id: Long): ChatSession?
    suspend fun createSession(title: String, modelId: String): Long
    suspend fun setTitle(id: Long, title: String)
    suspend fun setModel(id: Long, modelId: String)
    suspend fun deleteSession(id: Long)

    suspend fun getMessages(sessionId: Long): List<SessionMessage>

    /** Appends the message and bumps the session's updatedAt. Returns the new message id. */
    suspend fun addMessage(sessionId: Long, message: SessionMessage): Long

    /** Replaces content, reasoning, error and stats of an existing message (matched by [SessionMessage.id]). */
    suspend fun updateMessage(message: SessionMessage)

    suspend fun deleteMessage(id: Long)
}
