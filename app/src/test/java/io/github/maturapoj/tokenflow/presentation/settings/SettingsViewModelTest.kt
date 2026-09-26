package io.github.maturapoj.tokenflow.presentation.settings

import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.ChatException
import io.github.maturapoj.tokenflow.domain.model.ChatMessage
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.model.LlmModel
import io.github.maturapoj.tokenflow.domain.model.StreamEvent
import io.github.maturapoj.tokenflow.domain.repository.ChatRepository
import io.github.maturapoj.tokenflow.domain.usecase.ChatUseCase
import io.github.maturapoj.tokenflow.domain.usecase.SessionUseCase
import io.github.maturapoj.tokenflow.fakes.FakeSessionRepository
import io.github.maturapoj.tokenflow.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Models(private val result: () -> List<LlmModel>) : ChatRepository {
        override suspend fun getModels() = result()
        override fun streamChat(modelId: String, history: List<ChatMessage>): Flow<StreamEvent> = emptyFlow()
    }

    private fun TestScope.viewModel(settings: FakeSettingsRepository, models: () -> List<LlmModel> = { emptyList() }): SettingsViewModel {
        val sessions = FakeSessionRepository()
        return SettingsViewModel(settings, ChatUseCase(Models(models), sessions, SessionUseCase(sessions)))
            .also { advanceUntilIdle() }
    }

    @Test fun `loads the saved endpoint into the form`() = runTest(dispatcher) {
        val vm = viewModel(FakeSettingsRepository(GatewaySettings("https://a.test", "secret")))
        assertEquals("https://a.test", vm.state.value.baseUrl)
        assertEquals("secret", vm.state.value.apiKey)
    }

    @Test fun `preset fills the base url`() = runTest(dispatcher) {
        val vm = viewModel(FakeSettingsRepository(GatewaySettings()))
        vm.onIntent(SettingsIntent.UsePreset(Preset.OpenRouter))
        assertEquals("https://openrouter.ai/api", vm.state.value.baseUrl)
    }

    @Test fun `invalid url is flagged and not saved`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(GatewaySettings())
        val vm = viewModel(settings)
        vm.onIntent(SettingsIntent.EditBaseUrl("api.openai.com"))
        vm.onIntent(SettingsIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.invalidUrl)
        assertEquals("", settings.settings.value.baseUrl)
    }

    @Test fun `save normalizes, stores and reports how many models the endpoint has`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(GatewaySettings())
        val vm = viewModel(settings) { listOf(LlmModel("a", "models", "a"), LlmModel("b", "models", "b")) }
        vm.onIntent(SettingsIntent.EditBaseUrl("https://api.openai.com/v1/"))
        vm.onIntent(SettingsIntent.EditApiKey("  sk-1  "))
        vm.onIntent(SettingsIntent.Save)
        advanceUntilIdle()

        assertEquals(GatewaySettings("https://api.openai.com", "sk-1"), settings.settings.value)
        assertEquals(SaveStatus.Connected(2), vm.state.value.status)
    }

    @Test fun `failed connection check shows the error`() = runTest(dispatcher) {
        val vm = viewModel(FakeSettingsRepository(GatewaySettings())) { throw ChatException(ChatError.Unauthorized) }
        vm.onIntent(SettingsIntent.EditBaseUrl("https://api.openai.com"))
        vm.onIntent(SettingsIntent.Save)
        advanceUntilIdle()

        assertEquals(SaveStatus.Failed(ChatError.Unauthorized), vm.state.value.status)
    }
}
