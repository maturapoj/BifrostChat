package io.github.maturapoj.tokenflow.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.model.asChatError
import io.github.maturapoj.tokenflow.domain.repository.SettingsRepository
import io.github.maturapoj.tokenflow.domain.usecase.ChatUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Quick-fill endpoints. Local ones use 10.0.2.2, the host machine as seen from the emulator. */
enum class Preset(val label: String, val baseUrl: String) {
    OpenAI("OpenAI", "https://api.openai.com"),
    OpenRouter("OpenRouter", "https://openrouter.ai/api"),
    Ollama("Ollama", "http://10.0.2.2:11434"),
    LmStudio("LM Studio", "http://10.0.2.2:1234"),
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Testing : SaveStatus
    data class Connected(val models: Int) : SaveStatus
    data class Failed(val error: ChatError) : SaveStatus
}

data class SettingsState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val showKey: Boolean = false,
    val invalidUrl: Boolean = false,
    val status: SaveStatus = SaveStatus.Idle,
)

sealed interface SettingsIntent {
    data class EditBaseUrl(val value: String) : SettingsIntent
    data class EditApiKey(val value: String) : SettingsIntent
    data class UsePreset(val preset: Preset) : SettingsIntent
    data object ToggleKeyVisibility : SettingsIntent
    /** Saves, then checks the endpoint by listing its models. */
    data object Save : SettingsIntent
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val chat: ChatUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.settings.first()
            _state.update { it.copy(baseUrl = current.baseUrl, apiKey = current.apiKey) }
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.EditBaseUrl -> _state.update { it.copy(baseUrl = intent.value, invalidUrl = false, status = SaveStatus.Idle) }
            is SettingsIntent.EditApiKey -> _state.update { it.copy(apiKey = intent.value, status = SaveStatus.Idle) }
            is SettingsIntent.UsePreset -> _state.update { it.copy(baseUrl = intent.preset.baseUrl, invalidUrl = false, status = SaveStatus.Idle) }
            SettingsIntent.ToggleKeyVisibility -> _state.update { it.copy(showKey = !it.showKey) }
            SettingsIntent.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        val baseUrl = GatewaySettings.normalizeBaseUrl(current.baseUrl)
        if (baseUrl == null) {
            _state.update { it.copy(invalidUrl = true) }
            return
        }
        _state.update { it.copy(baseUrl = baseUrl, status = SaveStatus.Testing) }
        viewModelScope.launch {
            settings.saveEndpoint(baseUrl, current.apiKey.trim())
            val status = try {
                SaveStatus.Connected(chat.modelGroups().sumOf { it.models.size })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveStatus.Failed(e.asChatError())
            }
            _state.update { it.copy(status = status) }
        }
    }
}
