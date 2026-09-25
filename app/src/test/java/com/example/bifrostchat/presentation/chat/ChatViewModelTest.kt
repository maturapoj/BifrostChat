package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import com.example.bifrostchat.domain.usecase.CreateSessionUseCase
import com.example.bifrostchat.domain.usecase.DeleteSessionUseCase
import com.example.bifrostchat.domain.usecase.GetModelGroupsUseCase
import com.example.bifrostchat.domain.usecase.LoadSessionUseCase
import com.example.bifrostchat.domain.usecase.ObserveSessionsUseCase
import com.example.bifrostchat.domain.usecase.SaveMessageUseCase
import com.example.bifrostchat.domain.usecase.SetSessionModelUseCase
import com.example.bifrostchat.domain.usecase.StreamChatUseCase
import com.example.bifrostchat.fakes.FakeSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeChatRepository(
        var models: () -> List<LlmModel> = { listOf(LlmModel("p/m1", "p", "m1")) },
        var stream: () -> Flow<StreamEvent> = { flow {} },
    ) : ChatRepository {
        var lastHistory: List<ChatMessage> = emptyList()
        override suspend fun getModels() = models()
        override fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> {
            lastHistory = history
            return stream()
        }
    }

    private fun TestScope.viewModel(chat: ChatRepository, sessions: FakeSessionRepository = FakeSessionRepository()) =
        ChatViewModel(
            GetModelGroupsUseCase(chat),
            StreamChatUseCase(chat),
            SessionUseCases(
                ObserveSessionsUseCase(sessions),
                LoadSessionUseCase(sessions),
                CreateSessionUseCase(sessions),
                DeleteSessionUseCase(sessions),
                SaveMessageUseCase(sessions),
                SetSessionModelUseCase(sessions),
            ),
            clock = { testScheduler.currentTime },
        ).also { advanceUntilIdle() }

    private fun replying(vararg parts: String) = FakeChatRepository(stream = {
        flow { parts.forEach { emit(StreamEvent.Content(it)) } }
    })

    // --- streaming ---

    @Test fun `loads grouped models and selects the first`() = runTest(dispatcher) {
        val vm = viewModel(FakeChatRepository())
        assertEquals("p/m1", vm.state.value.selectedModelId)
        assertEquals(listOf("p"), vm.state.value.modelGroups.map { it.provider })
    }

    @Test fun `send streams reply into state`() = runTest(dispatcher) {
        val chat = FakeChatRepository(stream = {
            flow {
                emit(StreamEvent.Content("Hel"))
                emit(StreamEvent.Content("lo"))
                emit(StreamEvent.Usage(1, 2, 0))
            }
        })
        val vm = viewModel(chat)

        vm.onIntent(ChatIntent.Send("  hi  "))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("hi", "Hello"), s.messages.map { it.content })
        assertFalse(s.isStreaming)
        assertEquals(listOf(ChatMessage(Role.User, "hi")), chat.lastHistory)
    }

    @Test fun `stop cancels the stream and keeps partial text`() = runTest(dispatcher) {
        val vm = viewModel(FakeChatRepository(stream = {
            flow {
                emit(StreamEvent.Content("partial"))
                awaitCancellation()
            }
        }))

        vm.onIntent(ChatIntent.Send("hi"))
        advanceUntilIdle()
        assertTrue(vm.state.value.isStreaming)

        vm.onIntent(ChatIntent.Stop)
        advanceUntilIdle()

        val reply = vm.state.value.messages.last()
        assertEquals("partial", reply.content)
        assertFalse(reply.isStreaming)
        assertFalse(vm.state.value.isStreaming)
    }

    @Test fun `stream error is shown on the message and excluded from next history`() = runTest(dispatcher) {
        val chat = FakeChatRepository(stream = { flow { throw IllegalStateException("boom") } })
        val vm = viewModel(chat)

        vm.onIntent(ChatIntent.Send("first"))
        advanceUntilIdle()
        assertEquals("boom", vm.state.value.messages.last().error)

        chat.stream = { flow {} }
        vm.onIntent(ChatIntent.Send("second"))
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), chat.lastHistory.map { it.content })
    }

    @Test fun `model load failure emits effect`() = runTest(dispatcher) {
        val vm = viewModel(FakeChatRepository(models = { error("offline") }))
        assertEquals(ChatEffect.ModelsFailed("offline"), vm.effects.first())
    }

    @Test fun `send is ignored while streaming`() = runTest(dispatcher) {
        val vm = viewModel(FakeChatRepository(stream = { flow { awaitCancellation() } }))
        vm.onIntent(ChatIntent.Send("a"))
        advanceUntilIdle()
        vm.onIntent(ChatIntent.Send("b"))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.messages.size)
        vm.onIntent(ChatIntent.Stop)
    }

    // --- sessions ---

    @Test fun `first message creates a titled session and saves both turns`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        val vm = viewModel(replying("Hi", " there"), sessions)
        assertNull(vm.state.value.currentSessionId)

        vm.onIntent(ChatIntent.Send("Hello bot"))
        advanceUntilIdle()

        val id = vm.state.value.currentSessionId!!
        assertEquals("Hello bot", sessions.getSession(id)!!.title)
        assertEquals(listOf(Role.User to "Hello bot", Role.Assistant to "Hi there"), sessions.messages.getValue(id).map { it.role to it.content })
        assertEquals(listOf(id), vm.state.value.sessions.map { it.id })
    }

    @Test fun `stopped reply is saved with its partial text`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        val vm = viewModel(FakeChatRepository(stream = { flow { emit(StreamEvent.Content("part")); awaitCancellation() } }), sessions)

        vm.onIntent(ChatIntent.Send("q"))
        advanceUntilIdle()
        vm.onIntent(ChatIntent.Stop)
        advanceUntilIdle()

        val id = vm.state.value.currentSessionId!!
        assertEquals(listOf("q", "part"), sessions.messages.getValue(id).map { it.content })
    }

    @Test fun `latest session is reopened on launch`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        sessions.seed("older", "p/m1", SessionMessage(0, Role.User, "a"))
        val latest = sessions.seed("latest", "p/m1", SessionMessage(0, Role.User, "b"), SessionMessage(0, Role.Assistant, "c"))

        val vm = viewModel(FakeChatRepository(), sessions)

        assertEquals(latest, vm.state.value.currentSessionId)
        assertEquals(listOf("b", "c"), vm.state.value.messages.map { it.content })
    }

    @Test fun `continuing an opened session sends its history and appends to it`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        val id = sessions.seed("t", "p/m1", SessionMessage(0, Role.User, "q1"), SessionMessage(0, Role.Assistant, "a1"))
        val chat = replying("a2")
        val vm = viewModel(chat, sessions)

        vm.onIntent(ChatIntent.Send("q2"))
        advanceUntilIdle()

        assertEquals(listOf("q1", "a1", "q2"), chat.lastHistory.map { it.content })
        assertEquals(listOf("q1", "a1", "q2", "a2"), sessions.messages.getValue(id).map { it.content })
        assertEquals("t", sessions.getSession(id)!!.title) // title is only set once
    }

    @Test fun `switching sessions mid-stream saves the partial reply to the original session`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        val other = sessions.seed("other", "p/m1", SessionMessage(0, Role.User, "x"))
        val vm = viewModel(FakeChatRepository(stream = { flow { emit(StreamEvent.Content("half")); awaitCancellation() } }), sessions)
        vm.onIntent(ChatIntent.NewChat)
        advanceUntilIdle()

        vm.onIntent(ChatIntent.Send("q"))
        advanceUntilIdle()
        val first = vm.state.value.currentSessionId!!
        vm.onIntent(ChatIntent.OpenSession(other))
        advanceUntilIdle()

        assertEquals(other, vm.state.value.currentSessionId)
        assertEquals(listOf("x"), vm.state.value.messages.map { it.content })
        assertEquals(listOf("q", "half"), sessions.messages.getValue(first).map { it.content })
    }

    @Test fun `deleting the open session starts a new chat`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        val id = sessions.seed("t", "p/m1", SessionMessage(0, Role.User, "q"))
        val vm = viewModel(FakeChatRepository(), sessions)

        vm.onIntent(ChatIntent.DeleteSession(id))
        advanceUntilIdle()

        assertNull(vm.state.value.currentSessionId)
        assertTrue(vm.state.value.messages.isEmpty())
        assertTrue(vm.state.value.sessions.isEmpty())
    }

    @Test fun `model choice is saved on the open session`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository()
        val id = sessions.seed("t", "p/m1", SessionMessage(0, Role.User, "q"))
        val vm = viewModel(FakeChatRepository(), sessions)

        vm.onIntent(ChatIntent.SelectModel("p/m2"))
        advanceUntilIdle()

        assertEquals("p/m2", sessions.getSession(id)!!.modelId)
    }
}
