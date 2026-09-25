package com.example.bifrostchat.data.repository

import com.example.bifrostchat.data.remote.BifrostApi
import com.example.bifrostchat.data.remote.MessageDto
import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.data.remote.wire
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class ChatRepositoryImpl(private val api: BifrostApi) : ChatRepository {

    override suspend fun getModels(): List<LlmModel> = api.listModelIds().map(::toLlmModel)

    override fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> =
        api.streamChat(modelId, history.map { MessageDto(it.role.wire, it.content) })

    internal companion object {
        /** Gateway ids are `provider/name`; ids without a slash go under "other". */
        fun toLlmModel(id: String): LlmModel {
            val provider = id.substringBefore('/', missingDelimiterValue = "")
            return if (provider.isNotEmpty()) LlmModel(id, provider, id.substringAfter('/')) else LlmModel(id, "other", id)
        }
    }
}
