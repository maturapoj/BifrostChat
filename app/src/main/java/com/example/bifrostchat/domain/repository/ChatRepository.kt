package com.example.bifrostchat.domain.repository

import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getModels(): List<LlmModel>

    /** Cold flow; cancelling the collector aborts the request. */
    fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent>
}
