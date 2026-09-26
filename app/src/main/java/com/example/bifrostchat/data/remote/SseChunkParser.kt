package com.example.bifrostchat.data.remote

import com.example.bifrostchat.domain.model.StreamEvent

/** What one SSE line means for the stream. */
sealed interface SseLine {
    /** Blank keep-alive, `event:` or `: comment` line. */
    data object Ignored : SseLine

    /** `data: [DONE]`: the server finished the stream. */
    data object Done : SseLine

    data class Chunk(val events: List<StreamEvent>) : SseLine
}

/** Turns one SSE line (`data: {...}`) into an [SseLine]. */
object SseChunkParser {

    fun parseLine(line: String): SseLine {
        if (!line.startsWith("data:")) return SseLine.Ignored
        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") return SseLine.Done
        if (payload.isEmpty()) return SseLine.Ignored

        val chunk = GatewayJson.decodeFromString<ChunkDto>(payload)
        chunk.error?.let { throw BifrostException(it.message ?: payload) }

        val events = buildList {
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta?.let { delta ->
                    (delta.reasoning.nonEmpty() ?: delta.reasoningContent.nonEmpty())?.let { add(StreamEvent.Reasoning(it)) }
                    delta.content.nonEmpty()?.let { add(StreamEvent.Content(it)) }
                }
                choice.finishReason.nonEmpty()?.let { add(StreamEvent.Finished(it)) }
            }
            chunk.usage?.let {
                add(StreamEvent.Usage(it.promptTokens, it.completionTokens, it.details?.reasoningTokens ?: 0))
            }
        }
        return SseLine.Chunk(events)
    }

    private fun String?.nonEmpty(): String? = this?.takeIf { it.isNotEmpty() }
}
