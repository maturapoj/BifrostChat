package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class StreamChatUseCase(private val repository: ChatRepository) {

    /** Blank turns are dropped: the gateway rejects empty message content. */
    operator fun invoke(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> =
        repository.streamChat(modelId, history.filter { it.content.isNotBlank() })
}
