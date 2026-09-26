package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ChatError
import com.example.bifrostchat.domain.model.ChatException
import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import com.example.bifrostchat.fakes.FakeSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUseCaseTest {

    private class FakeChat(
        private val ids: List<String> = emptyList(),
        var stream: () -> Flow<StreamEvent> = { emptyFlow() },
    ) : ChatRepository {
        var lastContext: List<ChatMessage> = emptyList()
        override suspend fun getModels() = ids.map { LlmModel(it, it.substringBefore('/'), it.substringAfter('/')) }
        override fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> {
            lastContext = history
            return stream()
        }
    }

    private val sessions = FakeSessionRepository()

    private fun TestScope.useCase(chat: ChatRepository, maxContext: Int = 40) =
        ChatUseCase(chat, sessions, SessionUseCase(sessions), testScheduler.timeSource, saveEvery = 1_000.milliseconds, maxContextMessages = maxContext)

    private fun request(text: String = "q", sessionId: Long? = null, history: List<ChatMessage> = emptyList()) =
        SendRequest(sessionId, "p/m", history, text)

    private fun stored(id: Long) = sessions.messages.getValue(id).map { it.role to it.content }

    // --- models ---

    @Test fun `groups by provider, sorts both levels, drops embedding models`() = runTest {
        val groups = useCase(
            FakeChat(listOf("huawei/glm-5.3", "dashscope/qwen3.7-plus", "dashscope/qwen3.7-text-embedding", "huawei/glm-5.2", "dashscope/kimi-k3")),
        ).modelGroups()

        assertEquals(listOf("dashscope", "huawei"), groups.map { it.provider })
        assertEquals(listOf("kimi-k3", "qwen3.7-plus"), groups[0].models.map { it.name })
        assertEquals(listOf("glm-5.2", "glm-5.3"), groups[1].models.map { it.name })
    }

    // --- send ---

    @Test fun `new chat creates a titled session and saves both turns`() = runTest {
        val chat = FakeChat(stream = {
            flow {
                delay(800)
                emit(StreamEvent.Content("Hi"))
                emit(StreamEvent.Content(" there"))
                delay(1_200)
                emit(StreamEvent.Usage(1, 40, 0))
            }
        })

        val updates = useCase(chat).send(request("Hello bot")).toList()
        val last = updates.last()

        assertEquals("Hi there", last.reply.content)
        assertEquals(800.milliseconds, last.reply.stats?.timeToFirstToken)
        assertEquals(20.0, last.reply.stats?.tokensPerSecond!!, 0.001)
        assertTrue("every update names the session", updates.all { it.sessionId == last.sessionId })
        assertEquals("Hello bot", sessions.getSession(last.sessionId)!!.title)
        assertEquals(listOf(Role.User to "Hello bot", Role.Assistant to "Hi there"), stored(last.sessionId))
        assertEquals(last.reply.stats, sessions.messages.getValue(last.sessionId).last().stats)
    }

    @Test fun `partial reply is saved while streaming, at most once per interval`() = runTest {
        val chat = FakeChat(stream = {
            flow {
                repeat(10) { emit(StreamEvent.Content("x")); delay(100) } // 10 chunks within 1 s
                awaitCancellation() // then the stream stalls, like a paused model
            }
        })
        val job = launch { useCase(chat).send(request()).collect {} }

        advanceTimeBy(2_500)
        runCurrent()
        val id = sessions.messages.keys.single()
        assertEquals("saved without waiting for the end", "xxxxxxxxxx", stored(id).last().second)
        assertTrue("throttled to about one write per second, was ${sessions.updates}", sessions.updates in 1..2)

        job.cancel()
    }

    @Test fun `cancelled reply is kept with the text received so far`() = runTest {
        val chat = FakeChat(stream = { flow { emit(StreamEvent.Content("half")); awaitCancellation() } })
        val job = launch { useCase(chat).send(request("q")).collect {} }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf(Role.User to "q", Role.Assistant to "half"), stored(sessions.messages.keys.single()))
    }

    @Test fun `reply with nothing in it is removed`() = runTest {
        val job = launch { useCase(FakeChat(stream = { flow { awaitCancellation() } })).send(request("q")).collect {} }
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf(Role.User to "q"), stored(sessions.messages.keys.single()))
    }

    @Test fun `stream error ends the flow normally with the error on the reply, and is saved`() = runTest {
        val chat = FakeChat(stream = {
            flow {
                emit(StreamEvent.Content("par"))
                throw ChatException(ChatError.RateLimited)
            }
        })

        val last = useCase(chat).send(request()).last()

        assertEquals("par", last.reply.content)
        assertEquals(ChatError.RateLimited, last.reply.error)
        assertEquals(ChatError.RateLimited, sessions.messages.getValue(last.sessionId).last().error)
    }

    @Test fun `continuing a session appends and sends history plus the new message`() = runTest {
        val id = sessions.createSession("t", "p/m")
        val chat = FakeChat(stream = { flow { emit(StreamEvent.Content("a2")) } })
        val history = listOf(ChatMessage(Role.User, "q1"), ChatMessage(Role.Assistant, "a1"))

        useCase(chat).send(request("q2", sessionId = id, history = history)).toList()

        assertEquals(listOf("q1", "a1", "q2"), chat.lastContext.map { it.content })
        assertEquals(listOf("q2", "a2"), stored(id).map { it.second })
        assertEquals("t", sessions.getSession(id)!!.title)
    }

    @Test fun `context is capped to the most recent messages and blanks are dropped`() = runTest {
        val chat = FakeChat()
        val history = (1..10).map { ChatMessage(if (it % 2 == 1) Role.User else Role.Assistant, if (it == 9) " " else "m$it") }

        useCase(chat, maxContext = 4).send(request("new", history = history)).toList()

        assertEquals(listOf("m7", "m8", "m10", "new"), chat.lastContext.map { it.content })
    }
}
