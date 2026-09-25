package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamEvent
import com.example.bifrostchat.domain.repository.ChatRepository
import com.example.bifrostchat.domain.usecase.GetModelGroupsUseCase
import com.example.bifrostchat.domain.usecase.StreamChatUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepository(
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

    private fun TestScope.viewModel(repo: ChatRepository) =
        ChatViewModel(GetModelGroupsUseCase(repo), StreamChatUseCase(repo), clock = { testScheduler.currentTime })
            .also { advanceUntilIdle() }

    @Test fun `loads grouped models and selects the first`() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository())
        assertEquals("p/m1", vm.state.value.selectedModelId)
        assertEquals(listOf("p"), vm.state.value.modelGroups.map { it.provider })
    }

    @Test fun `send streams reply into state`() = runTest(dispatcher) {
        val repo = FakeRepository(stream = {
            flow {
                emit(StreamEvent.Content("Hel"))
                emit(StreamEvent.Content("lo"))
                emit(StreamEvent.Usage(1, 2, 0))
            }
        })
        val vm = viewModel(repo)

        vm.onIntent(ChatIntent.Send("  hi  "))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("hi", "Hello"), s.messages.map { it.content })
        assertFalse(s.isStreaming)
        assertEquals(listOf(ChatMessage(Role.User, "hi")), repo.lastHistory)
    }

    @Test fun `stop cancels the stream and keeps partial text`() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(stream = {
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
        val repo = FakeRepository(stream = { flow { throw IllegalStateException("boom") } })
        val vm = viewModel(repo)

        vm.onIntent(ChatIntent.Send("first"))
        advanceUntilIdle()
        assertEquals("boom", vm.state.value.messages.last().error)

        repo.stream = { flow {} }
        vm.onIntent(ChatIntent.Send("second"))
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), repo.lastHistory.map { it.content })
    }

    @Test fun `model load failure emits effect`() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(models = { error("offline") }))
        assertEquals(ChatEffect.ModelsFailed("offline"), vm.effects.first())
    }

    @Test fun `send is ignored while streaming`() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(stream = { flow { awaitCancellation() } }))
        vm.onIntent(ChatIntent.Send("a"))
        advanceUntilIdle()
        vm.onIntent(ChatIntent.Send("b"))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.messages.size)
        vm.onIntent(ChatIntent.Stop)
    }
}
