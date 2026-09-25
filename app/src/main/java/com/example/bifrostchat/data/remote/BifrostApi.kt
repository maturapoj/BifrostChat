package com.example.bifrostchat.data.remote

import com.example.bifrostchat.domain.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Wire format of one OpenAI chat message (`role` is "user" / "assistant"). */
data class MessageDto(val role: String, val content: String)

/** Talks to the Bifrost gateway's OpenAI-compatible `/v1` endpoints. */
class BifrostApi(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient,
) {

    suspend fun listModelIds(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw BifrostException("HTTP ${response.code}: $body")
            val data = JSONObject(body).getJSONArray("data")
            List(data.length()) { data.getJSONObject(it).getString("id") }
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
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_usage", true))
            .put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
            .toString()

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = http.newCall(request)
        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw BifrostException("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
                    }
                    val source = response.body!!.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val events = SseChunkParser.parseLine(line) ?: break
                        events.forEach { send(it) }
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
}
