package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.repository.ChatRepository

/** Chat-capable models grouped by provider, both sorted by name. */
class GetModelGroupsUseCase(private val repository: ChatRepository) {

    suspend operator fun invoke(): List<ModelGroup> =
        repository.getModels()
            .filterNot { NON_CHAT.containsMatchIn(it.name) }
            .groupBy { it.provider }
            .map { (provider, models) -> ModelGroup(provider, models.sortedBy { it.name }) }
            .sortedBy { it.provider }

    private companion object {
        // Embedding/rerank models are listed by /v1/models but can't do chat completions.
        val NON_CHAT = Regex("embedding|rerank", RegexOption.IGNORE_CASE)
    }
}
