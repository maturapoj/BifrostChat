package io.github.maturapoj.tokenflow.presentation.chat.state

import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.LlmModel
import io.github.maturapoj.tokenflow.domain.model.ModelGroup
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.domain.model.SessionMessage
import io.github.maturapoj.tokenflow.domain.model.StreamStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

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

    @Test fun `reply snapshot replaces the assistant's text, stats and error`() {
        val stats = StreamStats(timeToFirstToken = 700.milliseconds, chunks = 2)
        val snapshot = SessionMessage(99, Role.Assistant, "Hello", reasoning = "think", stats = stats)

        val msg = streaming.after(ChatResult.ReplyUpdated(1, snapshot)).assistant()

        assertEquals("Hello", msg.content)
        assertEquals("think", msg.reasoning)
        assertEquals(stats, msg.stats)
        assertEquals(1L, msg.id) // the UI id stays; the database id doesn't leak in
        assertTrue(msg.isStreaming)
    }

    @Test fun `stream end clears streaming flags and a failure shows on the message`() {
        val s = streaming.after(ChatResult.ReplyFailed(1, ChatError.Network), ChatResult.StreamEnded(1))
        assertFalse(s.isStreaming)
        assertFalse(s.assistant().isStreaming)
        assertEquals(ChatError.Network, s.assistant().error)
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
