package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.ChatException
import com.example.bifrostchat.domain.model.ChatMessage
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.model.apply
import com.example.bifrostchat.domain.model.hasContent
import com.example.bifrostchat.domain.repository.ChatRepository
import com.example.bifrostchat.domain.repository.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

data class SendRequest(
    /** null starts a new session. */
    val sessionId: Long?,
    val modelId: String,
    /** Earlier turns, oldest first, without the new message. */
    val history: List<ChatMessage>,
    val text: String,
)

/** The latest state of the reply being streamed, and the session it belongs to. */
data class ReplyUpdate(val sessionId: Long, val reply: SessionMessage)

/** Talking to the model gateway: which models to offer, and sending a message. */
class ChatUseCase(
    private val chat: ChatRepository,
    private val sessions: SessionRepository,
    private val sessionUseCase: SessionUseCase,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val saveEvery: Duration = 1.seconds,
    private val maxContextMessages: Int = 40,
) {

    /** Chat-capable models grouped by provider, both sorted by name. */
    suspend fun modelGroups(): List<ModelGroup> =
        chat.getModels()
            .filterNot { NON_CHAT.containsMatchIn(it.name) }
            .groupBy { it.provider }
            .map { (provider, models) -> ModelGroup(provider, models.sortedBy { it.name }) }
            .sortedBy { it.provider }

    /**
     * Sends [request.text] and streams the reply as full snapshots (not deltas).
     *
     * - Creates the session if needed and saves the user message first.
     * - Inserts the reply row up front and saves it at most every [saveEvery] while it
     *   streams, so a killed process loses at most that much text.
     * - Always saves the final reply, also when the collector is cancelled (Stop,
     *   switching chats); an empty reply is removed instead.
     * - A failing stream ends normally with the error on the reply. Setup failures
     *   (e.g. the database) are thrown.
     */
    fun send(request: SendRequest): Flow<ReplyUpdate> = channelFlow {
        val sessionId = request.sessionId ?: sessions.createSession(title = "", modelId = request.modelId)
        sessionUseCase.saveMessage(sessionId, SessionMessage(0, Role.User, request.text))
        val replyId = sessions.addMessage(sessionId, SessionMessage(0, Role.Assistant, ""))

        val reply = MutableStateFlow(SessionMessage(replyId, Role.Assistant, ""))
        send(ReplyUpdate(sessionId, reply.value))

        // A save is scheduled only when there is something new to write, so an idle stream
        // (a long model pause) doesn't keep a timer ticking.
        var pendingSave: Job? = null
        fun scheduleSave() {
            if (pendingSave?.isActive == true) return
            pendingSave = launch {
                delay(saveEvery)
                sessions.updateMessage(reply.value)
            }
        }

        val context = (request.history + ChatMessage(Role.User, request.text))
            .filter { it.content.isNotBlank() } // the gateway rejects empty message content
            .takeLast(maxContextMessages)
        val start = timeSource.markNow()
        try {
            chat.streamChat(request.modelId, context).collect { event ->
                reply.value = reply.value.apply(event, start.elapsedNow())
                send(ReplyUpdate(sessionId, reply.value))
                scheduleSave()
            }
        } catch (e: ChatException) {
            reply.value = reply.value.copy(error = e.error)
            send(ReplyUpdate(sessionId, reply.value))
        } finally {
            withContext(NonCancellable) {
                pendingSave?.cancelAndJoin()
                val final = reply.value
                if (final.hasContent) sessions.updateMessage(final) else sessions.deleteMessage(final.id)
            }
        }
    }

    private companion object {
        // Embedding/rerank models are listed by /v1/models but can't do chat completions.
        val NON_CHAT = Regex("embedding|rerank", RegexOption.IGNORE_CASE)
    }
}
