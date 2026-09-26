package io.github.maturapoj.tokenflow.domain.repository

import io.github.maturapoj.tokenflow.domain.model.ChatMessage
import io.github.maturapoj.tokenflow.domain.model.LlmModel
import io.github.maturapoj.tokenflow.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getModels(): List<LlmModel>

    /** Cold flow; cancelling the collector aborts the request. */
    fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent>
}
