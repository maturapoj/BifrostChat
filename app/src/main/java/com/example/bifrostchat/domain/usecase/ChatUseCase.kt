package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

/** Talking to the model gateway: which models to offer, and streaming a reply. */
class ChatUseCase(private val repository: ChatRepository) {

    /** Chat-capable models grouped by provider, both sorted by name. */
    suspend fun modelGroups(): List<ModelGroup> =
        repository.getModels()
            .filterNot { NON_CHAT.containsMatchIn(it.name) }
            .groupBy { it.provider }
            .map { (provider, models) -> ModelGroup(provider, models.sortedBy { it.name }) }
            .sortedBy { it.provider }

    /** Blank turns are dropped: the gateway rejects empty message content. */
    fun stream(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> =
        repository.streamChat(modelId, history.filter { it.content.isNotBlank() })

    private companion object {
        // Embedding/rerank models are listed by /v1/models but can't do chat completions.
        val NON_CHAT = Regex("embedding|rerank", RegexOption.IGNORE_CASE)
    }
}
