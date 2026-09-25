package com.example.bifrostchat.data

import org.json.JSONObject

/**
 * Turns one SSE line (`data: {...}`) into zero or more [StreamEvent]s.
 * Returns null when the server sends `data: [DONE]`.
 */
object SseChunkParser {

    fun parseLine(line: String): List<StreamEvent>? {
        if (!line.startsWith("data:")) return emptyList() // blank keep-alive, `event:`, `: comment`
        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") return null
        if (payload.isEmpty()) return emptyList()

        val json = JSONObject(payload)
        json.optJSONObject("error")?.let { throw BifrostException(it.optString("message", payload)) }

        val events = mutableListOf<StreamEvent>()
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        if (choice != null) {
            val delta = choice.optJSONObject("delta")
            if (delta != null) {
                delta.optNonEmpty("reasoning")
                    ?.let { events += StreamEvent.Reasoning(it) }
                    ?: delta.optNonEmpty("reasoning_content")?.let { events += StreamEvent.Reasoning(it) }
                delta.optNonEmpty("content")?.let { events += StreamEvent.Content(it) }
            }
            choice.optNonEmpty("finish_reason")?.let { events += StreamEvent.Finished(it) }
        }

        json.optJSONObject("usage")?.let { usage ->
            events += StreamEvent.Usage(
                promptTokens = usage.optInt("prompt_tokens"),
                completionTokens = usage.optInt("completion_tokens"),
                reasoningTokens = usage.optJSONObject("completion_tokens_details")
                    ?.optInt("reasoning_tokens") ?: 0,
            )
        }
        return events
    }

    // optString returns "null" for JSON null, so check explicitly.
    private fun JSONObject.optNonEmpty(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}

class BifrostException(message: String) : Exception(message)
