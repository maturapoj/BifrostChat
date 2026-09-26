package com.example.bifrostchat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.asChatError
import com.example.bifrostchat.domain.repository.SessionRepository
import com.example.bifrostchat.domain.usecase.ChatUseCase
import com.example.bifrostchat.domain.usecase.SendRequest
import com.example.bifrostchat.domain.usecase.SessionUseCase
import com.example.bifrostchat.presentation.chat.state.ChatEffect
import com.example.bifrostchat.presentation.chat.state.ChatIntent
import com.example.bifrostchat.presentation.chat.state.ChatResult
import com.example.bifrostchat.presentation.chat.state.ChatState
import com.example.bifrostchat.presentation.chat.state.UiMessage
import com.example.bifrostchat.presentation.chat.state.reduce
import com.example.bifrostchat.presentation.chat.state.toUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chat: ChatUseCase,
    private val sessionUseCase: SessionUseCase,
    /** Plain session reads and writes; anything with rules goes through the use cases. */
    private val sessions: SessionRepository,
    /** Preferred model id from config; ignored if the gateway doesn't list it. */
    initialModelId: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(selectedModelId = initialModelId))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = _effects.receiveAsFlow()

    private var streamJob: Job? = null

    /** Latest queued chat switch or delete; see [runSessionChange]. */
    private var switchJob: Job? = null
    private var nextId = 0L

    init {
        onIntent(ChatIntent.LoadModels)
        observeSessions()
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            ChatIntent.LoadModels -> loadModels()
            is ChatIntent.SelectModel -> selectModel(intent.modelId)
            is ChatIntent.Send -> send(intent.text)
            ChatIntent.Stop -> streamJob?.cancel()
            ChatIntent.NewChat -> switchTo(null)
            is ChatIntent.OpenSession -> switchTo(intent.id)
            is ChatIntent.DeleteSession -> deleteSession(intent.id)
        }
    }

    private fun dispatch(result: ChatResult) = _state.update { reduce(it, result) }

    private fun loadModels() {
        viewModelScope.launch {
            try {
                dispatch(ChatResult.ModelsLoaded(chat.modelGroups()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(ChatEffect.ModelsFailed(e.asChatError()))
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            sessions.observeSessions().collectIndexed { index, list ->
                dispatch(ChatResult.SessionsUpdated(list))
                // Reopen the latest chat on launch, unless the user already started typing into a new one.
                if (index == 0 && _state.value.messages.isEmpty()) list.firstOrNull()?.let { switchTo(it.id) }
            }
        }
    }

    private fun selectModel(modelId: String) {
        dispatch(ChatResult.ModelSelected(modelId))
        _state.value.currentSessionId?.let { id -> viewModelScope.launch { sessions.setModel(id, modelId) } }
    }

    /** Opens a saved session, or a new unsaved chat when [id] is null. */
    private fun switchTo(id: Long?) = runSessionChange {
        if (id == null) {
            dispatch(ChatResult.NewChatStarted)
            return@runSessionChange
        }
        val loaded = sessionUseCase.load(id) ?: return@runSessionChange
        nextId = (loaded.messages.maxOfOrNull { it.id } ?: 0) + 1
        dispatch(ChatResult.SessionOpened(id, loaded.session.modelId, loaded.messages.map { it.toUi() }))
    }

    private fun deleteSession(id: Long) {
        if (id != _state.value.currentSessionId) {
            viewModelScope.launch { sessions.deleteSession(id) }
            return
        }
        runSessionChange {
            dispatch(ChatResult.NewChatStarted)
            sessions.deleteSession(id)
        }
    }

    /**
     * Runs a chat switch or delete after stopping (and saving) any running stream.
     * Changes are queued rather than cancelled, so a delete always finishes and the
     * reply is saved before its session can be removed. [send] is ignored while one runs.
     */
    private fun runSessionChange(block: suspend () -> Unit) {
        val previous = switchJob
        switchJob = viewModelScope.launch {
            previous?.join()
            streamJob?.cancelAndJoin()
            block()
        }
    }

    private fun send(text: String) {
        val current = _state.value
        if (text.isBlank() || current.isStreaming || switchJob?.isActive == true) return

        val user = UiMessage(id = nextId++, role = Role.User, content = text.trim())
        val assistantId = nextId++
        val request = SendRequest(
            sessionId = current.currentSessionId,
            modelId = current.selectedModelId,
            history = current.messages.filter { it.error == null }.map { ChatMessage(it.role, it.content) },
            text = user.content,
        )
        dispatch(ChatResult.StreamStarted(user, assistantId))

        // Saving, timing and error handling live in ChatUseCase.send; this only mirrors its snapshots.
        streamJob = viewModelScope.launch {
            try {
                chat.send(request).collect { update ->
                    if (_state.value.currentSessionId != update.sessionId) dispatch(ChatResult.SessionCreated(update.sessionId))
                    dispatch(ChatResult.ReplyUpdated(assistantId, update.reply))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(ChatResult.ReplyFailed(assistantId, e.asChatError()))
            } finally {
                dispatch(ChatResult.StreamEnded(assistantId))
            }
        }
    }
}
