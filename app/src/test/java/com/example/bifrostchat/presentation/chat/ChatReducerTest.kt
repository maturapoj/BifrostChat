package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    private val user = UiMessage(id = 0, role = Role.User, content = "hi")
    private val streaming = reduce(ChatState(), ChatResult.StreamStarted(user, assistantId = 1))

    private fun ChatState.after(vararg results: ChatResult) = results.fold(this, ::reduce)
    private fun ChatState.assistant() = messages.single { it.id == 1L }

    @Test fun `stream start appends user and empty streaming assistant`() {
        assertEquals(listOf(0L, 1L), streaming.messages.map { it.id })
        assertTrue(streaming.isStreaming)
        assertTrue(streaming.assistant().isStreaming)
    }

    @Test fun `tokens append per channel and first token sets TTFT once`() {
        val s = streaming.after(
            ChatResult.StreamEventReceived(1, StreamEvent.Reasoning("think"), elapsedMs = 800),
            ChatResult.StreamEventReceived(1, StreamEvent.Content("Hel"), elapsedMs = 900),
            ChatResult.StreamEventReceived(1, StreamEvent.Content("lo"), elapsedMs = 950),
        )
        val msg = s.assistant()
        assertEquals("think", msg.reasoning)
        assertEquals("Hello", msg.content)
        assertEquals(800L, msg.stats?.timeToFirstTokenMs)
        assertEquals(3, msg.stats?.chunks)
    }

    @Test fun `usage sets tokens per second over whole request`() {
        val s = streaming.after(ChatResult.StreamEventReceived(1, StreamEvent.Usage(10, 100, 20), elapsedMs = 2_000))
        val stats = s.assistant().stats!!
        assertEquals(100, stats.completionTokens)
        assertEquals(20, stats.reasoningTokens)
        assertEquals(50.0, stats.tokensPerSecond!!, 0.001)
    }

    @Test fun `stream end clears streaming flags and keeps error`() {
        val s = streaming.after(ChatResult.StreamEnded(1, error = "HTTP 500"))
        assertFalse(s.isStreaming)
        assertFalse(s.assistant().isStreaming)
        assertEquals("HTTP 500", s.assistant().error)
    }

    @Test fun `models loaded keeps selection if available, else picks first of first group`() {
        val groups = listOf(
            ModelGroup("p1", listOf(LlmModel("p1/a", "p1", "a"))),
            ModelGroup("p2", listOf(LlmModel("p2/b", "p2", "b"))),
        )
        val kept = reduce(ChatState(selectedModelId = "p2/b"), ChatResult.ModelsLoaded(groups))
        assertEquals("b", kept.selectedModel?.name)
        val replaced = reduce(ChatState(selectedModelId = "gone"), ChatResult.ModelsLoaded(groups))
        assertEquals("p1/a", replaced.selectedModelId)
    }

    @Test fun `clear empties messages`() {
        val s = streaming.after(ChatResult.Cleared)
        assertTrue(s.messages.isEmpty())
        assertFalse(s.isStreaming)
    }
}
