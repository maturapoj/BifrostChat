package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUseCaseTest {

    private fun repoOf(vararg ids: String) = object : ChatRepository {
        override suspend fun getModels() = ids.map { LlmModel(it, it.substringBefore('/'), it.substringAfter('/')) }
        override fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> = emptyFlow()
    }

    @Test fun `groups by provider, sorts both levels, drops embedding models`() = runTest {
        val groups = ChatUseCase(
            repoOf("huawei/glm-5.3", "dashscope/qwen3.7-plus", "dashscope/qwen3.7-text-embedding", "huawei/glm-5.2", "dashscope/kimi-k3"),
        ).modelGroups()

        assertEquals(listOf("dashscope", "huawei"), groups.map { it.provider })
        assertEquals(listOf("kimi-k3", "qwen3.7-plus"), groups[0].models.map { it.name })
        assertEquals(listOf("glm-5.2", "glm-5.3"), groups[1].models.map { it.name })
    }
}
