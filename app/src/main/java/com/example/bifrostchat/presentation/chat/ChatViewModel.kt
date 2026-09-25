package com.example.bifrostchat.presentation.chat

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.usecase.GetModelGroupsUseCase
import com.example.bifrostchat.domain.usecase.StreamChatUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val getModelGroups: GetModelGroupsUseCase,
    private val streamChat: StreamChatUseCase,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = _effects.receiveAsFlow()

    private var streamJob: Job? = null
    private var nextId = 0L

    init {
        onIntent(ChatIntent.LoadModels)
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            ChatIntent.LoadModels -> loadModels()
            is ChatIntent.SelectModel -> dispatch(ChatResult.ModelSelected(intent.modelId))
            is ChatIntent.Send -> send(intent.text)
            ChatIntent.Stop -> streamJob?.cancel()
            ChatIntent.Clear -> {
                streamJob?.cancel()
                dispatch(ChatResult.Cleared)
            }
        }
    }

    private fun dispatch(result: ChatResult) = _state.update { reduce(it, result) }

    private fun loadModels() {
        viewModelScope.launch {
            try {
                dispatch(ChatResult.ModelsLoaded(getModelGroups()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(ChatEffect.ModelsFailed(e.message ?: e.toString()))
            }
        }
    }

    private fun send(text: String) {
        val current = _state.value
        if (text.isBlank() || current.isStreaming) return

        val user = UiMessage(id = nextId++, role = Role.User, content = text.trim())
        val assistantId = nextId++
        val history = (current.messages + user)
            .filter { it.error == null }
            .map { ChatMessage(it.role, it.content) }

        dispatch(ChatResult.StreamStarted(user, assistantId))

        streamJob = viewModelScope.launch {
            val start = clock()
            var error: String? = null
            try {
                streamChat(current.selectedModelId, history).coalesceTokens().collect { event ->
                    dispatch(ChatResult.StreamEventReceived(assistantId, event, clock() - start))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                dispatch(ChatResult.StreamEnded(assistantId, error))
            }
        }
    }
}
