package io.github.maturapoj.tokenflow.domain.model

import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Folds one stream event into an assistant reply. [elapsed] is the time since the
 * request started; it sets time-to-first-token and, with usage, tokens per second.
 */
fun SessionMessage.apply(event: StreamEvent, elapsed: Duration): SessionMessage {
    val s = stats ?: StreamStats()
    return when (event) {
        is StreamEvent.Reasoning -> copy(reasoning = reasoning + event.text, stats = s.onChunk(elapsed))
        is StreamEvent.Content -> copy(content = content + event.text, stats = s.onChunk(elapsed))
        is StreamEvent.Usage -> copy(
            stats = s.copy(
                completionTokens = event.completionTokens,
                reasoningTokens = event.reasoningTokens,
                total = elapsed,
                // Over the whole request: the gateway delivers chunks in bursts, so per-chunk timing is meaningless.
                tokensPerSecond = if (elapsed.isPositive()) event.completionTokens / elapsed.toDouble(DurationUnit.SECONDS) else null,
            ),
        )
        is StreamEvent.Finished -> this
    }
}

/** True once the reply has anything worth keeping. */
val SessionMessage.hasContent: Boolean
    get() = content.isNotEmpty() || reasoning.isNotEmpty() || error != null

private fun StreamStats.onChunk(elapsed: Duration) =
    copy(timeToFirstToken = timeToFirstToken ?: elapsed, chunks = chunks + 1)
