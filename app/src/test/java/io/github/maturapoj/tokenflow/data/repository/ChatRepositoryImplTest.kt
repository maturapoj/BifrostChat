package io.github.maturapoj.tokenflow.data.repository

import io.github.maturapoj.tokenflow.domain.model.LlmModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRepositoryImplTest {

    @Test fun `splits provider from gateway id at the first slash`() {
        assertEquals(LlmModel("dashscope/qwen3.7-plus", "dashscope", "qwen3.7-plus"), ChatRepositoryImpl.toLlmModel("dashscope/qwen3.7-plus"))
        assertEquals(LlmModel("a/b/c", "a", "b/c"), ChatRepositoryImpl.toLlmModel("a/b/c"))
    }

    @Test fun `id without provider goes under models`() {
        assertEquals(LlmModel("gpt-x", "models", "gpt-x"), ChatRepositoryImpl.toLlmModel("gpt-x"))
    }
}
