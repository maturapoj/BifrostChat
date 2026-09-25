package com.example.bifrostchat.presentation.chat.state

import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
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
            ChatResult.StreamEventReceived(1, StreamEvent.Reasoning("think"), elapsed = 800.milliseconds),
            ChatResult.StreamEventReceived(1, StreamEvent.Content("Hel"), elapsed = 900.milliseconds),
            ChatResult.StreamEventReceived(1, StreamEvent.Content("lo"), elapsed = 950.milliseconds),
        )
        val msg = s.assistant()
        assertEquals("think", msg.reasoning)
        assertEquals("Hello", msg.content)
        assertEquals(800.milliseconds, msg.stats?.timeToFirstToken)
        assertEquals(3, msg.stats?.chunks)
    }

    @Test fun `usage sets tokens per second over whole request`() {
        val s = streaming.after(ChatResult.StreamEventReceived(1, StreamEvent.Usage(10, 100, 20), elapsed = 2.seconds))
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

    @Test fun `new chat empties messages and detaches from the session`() {
        val s = streaming.after(ChatResult.SessionCreated(7), ChatResult.NewChatStarted)
        assertTrue(s.messages.isEmpty())
        assertFalse(s.isStreaming)
        assertEquals(null, s.currentSessionId)
    }

    @Test fun `opening a session restores its messages and model`() {
        val restored = listOf(UiMessage(id = 3, role = Role.User, content = "old"))
        val s = streaming.after(ChatResult.SessionOpened(7, "p/m", restored))
        assertEquals(7L, s.currentSessionId)
        assertEquals("p/m", s.selectedModelId)
        assertEquals(restored, s.messages)
        assertFalse(s.isStreaming)
    }
}
