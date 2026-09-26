package io.github.maturapoj.tokenflow.data.repository

import io.github.maturapoj.tokenflow.data.remote.GatewayApi
import io.github.maturapoj.tokenflow.data.remote.MessageDto
import io.github.maturapoj.tokenflow.data.remote.toChatException
import io.github.maturapoj.tokenflow.data.remote.wire
import io.github.maturapoj.tokenflow.domain.model.ChatMessage
import io.github.maturapoj.tokenflow.domain.model.LlmModel
import io.github.maturapoj.tokenflow.domain.model.StreamEvent
import io.github.maturapoj.tokenflow.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class ChatRepositoryImpl(private val api: GatewayApi) : ChatRepository {

    override suspend fun getModels(): List<LlmModel> =
        try {
            api.listModelIds().map(::toLlmModel)
        } catch (e: Throwable) {
            throw e.toChatException()
        }

    override fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> =
        api.streamChat(modelId, history.map { MessageDto(it.role.wire, it.content) })
            .catch { throw it.toChatException() }

    internal companion object {
        /** Gateway ids like `provider/name` are grouped by provider; plain ids (OpenAI, Ollama) go under "models". */
        fun toLlmModel(id: String): LlmModel {
            val provider = id.substringBefore('/', missingDelimiterValue = "")
            return if (provider.isNotEmpty()) LlmModel(id, provider, id.substringAfter('/')) else LlmModel(id, "models", id)
        }
    }
}
