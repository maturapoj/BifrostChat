package com.example.bifrostchat.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bifrostchat.BuildConfig
import com.example.bifrostchat.data.BifrostClient
import com.example.bifrostchat.data.ChatMessage
import com.example.bifrostchat.data.StreamEvent
import com.example.bifrostchat.data.coalesceTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StreamStats(
    val timeToFirstTokenMs: Long? = null,
    /** UI updates after coalescing, not raw SSE chunks. */
    val chunks: Int = 0,
    val completionTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val totalMs: Long? = null,
    /** completion tokens / whole request time; the gateway delivers chunks in bursts, so per-chunk timing is meaningless. */
    val tokensPerSecond: Double? = null,
)

data class UiMessage(
    val id: Long,
    val role: String,
    val content: String = "",
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val stats: StreamStats? = null,
)

data class ChatUiState(
    val models: List<String> = emptyList(),
    val selectedModel: String = "dashscope/deepseek-v4-flash-0731",
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val client: BifrostClient = BifrostClient(BuildConfig.BIFROST_BASE_URL, BuildConfig.BIFROST_API_KEY),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var nextId = 0L

    init {
        loadModels()
    }

    fun loadModels() {
        viewModelScope.launch {
            runCatching { client.listModels() }
                .onSuccess { models ->
                    _state.update {
                        it.copy(
                            models = models,
                            selectedModel = it.selectedModel.takeIf { m -> m in models } ?: models.firstOrNull().orEmpty(),
                            error = null,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = "โหลด models ไม่ได้: ${e.message}") } }
        }
    }

    fun selectModel(model: String) = _state.update { it.copy(selectedModel = model) }

    fun send(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return

        val user = UiMessage(id = nextId++, role = "user", content = text.trim())
        val assistantId = nextId++
        val history = (_state.value.messages + user)
            .filter { it.error == null && it.content.isNotEmpty() }
            .map { ChatMessage(it.role, it.content) }

        _state.update {
            it.copy(
                messages = it.messages + user + UiMessage(id = assistantId, role = "assistant", isStreaming = true),
                isStreaming = true,
            )
        }

        streamJob = viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            var firstTokenAt: Long? = null
            var stats = StreamStats()

            try {
                client.streamChat(_state.value.selectedModel, history).coalesceTokens().collect { event ->
                    if (firstTokenAt == null && (event is StreamEvent.Content || event is StreamEvent.Reasoning)) {
                        firstTokenAt = SystemClock.elapsedRealtime()
                        stats = stats.copy(timeToFirstTokenMs = firstTokenAt!! - start)
                    }
                    when (event) {
                        is StreamEvent.Reasoning -> {
                            stats = stats.copy(chunks = stats.chunks + 1)
                            updateMessage(assistantId) { it.copy(reasoning = it.reasoning + event.text, stats = stats) }
                        }
                        is StreamEvent.Content -> {
                            stats = stats.copy(chunks = stats.chunks + 1)
                            updateMessage(assistantId) { it.copy(content = it.content + event.text, stats = stats) }
                        }
                        is StreamEvent.Usage -> {
                            val totalMs = SystemClock.elapsedRealtime() - start
                            stats = stats.copy(
                                completionTokens = event.completionTokens,
                                reasoningTokens = event.reasoningTokens,
                                totalMs = totalMs,
                                tokensPerSecond = event.completionTokens / (totalMs / 1000.0),
                            )
                            updateMessage(assistantId) { it.copy(stats = stats) }
                        }
                        is StreamEvent.Finished -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateMessage(assistantId) { it.copy(error = e.message ?: e.toString()) }
            } finally {
                updateMessage(assistantId) { it.copy(isStreaming = false) }
                _state.update { it.copy(isStreaming = false) }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
    }

    fun clear() {
        stop()
        _state.update { it.copy(messages = emptyList()) }
    }

    private fun updateMessage(id: Long, transform: (UiMessage) -> UiMessage) {
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) transform(it) else it }) }
    }
}
