package io.github.maturapoj.tokenflow.data.remote

import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.ChatException
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.model.StreamEvent
import io.github.maturapoj.tokenflow.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Talks to an OpenAI-compatible gateway's `/v1` endpoints. */
class GatewayApi(
    /** Read on every request, so a change in Settings applies without a restart. */
    private val settings: SettingsRepository,
    private val http: OkHttpClient,
    /** Blocking socket I/O runs here; injectable so tests can control it. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun listModelIds(): List<String> = withContext(io) {
        val request = requestTo("v1/models", endpoint()).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GatewayHttpException(response.code, body)
            GatewayJson.decodeFromString<ModelsResponseDto>(body).data.map { it.id }
        }
    }

    /**
     * Streams a chat completion. Each SSE line is read as soon as it arrives,
     * so tokens reach the collector one chunk at a time.
     *
     * The blocking socket read runs in a child coroutine, and [awaitClose] cancels the
     * call as soon as the collector is cancelled. Closing the socket unblocks the read,
     * so Stop takes effect immediately instead of waiting for the next chunk (or the
     * read timeout, if the server has gone quiet).
     */
    fun streamChat(model: String, messages: List<MessageDto>): Flow<StreamEvent> = channelFlow {
        val body = GatewayJson.encodeToString(ChatRequestDto(model, messages))

        val request = requestTo("v1/chat/completions", endpoint())
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = http.newCall(request)
        launch(io) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw GatewayHttpException(response.code, response.body?.string().orEmpty())
                    }
                    val source = response.body!!.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when (val parsed = SseChunkParser.parseLine(line)) {
                            SseLine.Done -> break
                            SseLine.Ignored -> Unit
                            is SseLine.Chunk -> parsed.events.forEach { send(it) }
                        }
                    }
                }
            } catch (e: IOException) {
                // "Socket closed" after awaitClose cancelled the call is expected, not a failure.
                if (!call.isCanceled()) throw e
            }
            channel.close()
        }
        awaitClose { call.cancel() }
    }

    private suspend fun endpoint(): GatewaySettings =
        settings.settings.first().takeIf { it.isConfigured } ?: throw ChatException(ChatError.NotConfigured)

    /** Local servers (Ollama, LM Studio) need no key, so the header is only sent when there is one. */
    private fun requestTo(path: String, endpoint: GatewaySettings): Request.Builder =
        Request.Builder().url("${endpoint.baseUrl}/$path").apply {
            if (endpoint.apiKey.isNotEmpty()) header("Authorization", "Bearer ${endpoint.apiKey}")
        }
}
