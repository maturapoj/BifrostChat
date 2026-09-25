package com.example.bifrostchat.presentation.chat

import com.example.bifrostchat.domain.model.StreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Batches Reasoning/Content deltas so the UI updates at most once per [windowMillis]
 * instead of once per chunk. Every chunk would otherwise mean a new ChatState, a
 * recomposition and a full re-layout of the message text, while the gateway can deliver
 * ~20 chunks within a few milliseconds.
 *
 * Emits deltas (not accumulated text), so collectors keep appending. Reasoning and Content
 * are buffered separately because the UI renders them in separate blocks.
 *
 * ```
 * t(ms)  in                          out
 *    0   Content("Hel")  starts timer
 *    2   Content("lo")
 *   50                               Content("Hello")   timer fires
 * 1000   Content("!")    starts timer
 * 1050                               Content("!")
 * 1060   Usage(...)                  Usage(...)         passes through immediately
 * ```
 *
 * A timer flushes the batch, not the next token's arrival: the gateway sends chunks in
 * bursts, and waiting for the next token would leave the tail of each burst on hold until
 * the next burst. Usage/Finished flush the pending text first, then pass through, so stats
 * never land before the text they describe.
 *
 * Why not a built-in operator:
 * - `sample`/`conflate` keep only the latest value, which would drop deltas.
 * - `debounce` restarts on every value, so a steady stream would never update the UI.
 *
 * Known limitation: if the collector is cancelled (Stop), up to [windowMillis] of buffered
 * text is dropped, because cancellation tears down this scope before the final flush.
 */
fun Flow<StreamEvent>.coalesceTokens(windowMillis: Long = 50L): Flow<StreamEvent> = channelFlow {
    // channelFlow (not flow) because two coroutines send: the upstream collector and the timer.
    // The mutex keeps them from touching the buffers at the same time.
    val lock = Mutex()
    val reasoning = StringBuilder()
    val content = StringBuilder()
    var timer: Job? = null // null = no flush scheduled

    // Caller must hold [lock].
    suspend fun drain() {
        if (reasoning.isNotEmpty()) send(StreamEvent.Reasoning(reasoning.toString()))
        if (content.isNotEmpty()) send(StreamEvent.Content(content.toString()))
        reasoning.clear()
        content.clear()
    }

    collect { event ->
        lock.withLock {
            when (event) {
                is StreamEvent.Reasoning -> reasoning.append(event.text)
                is StreamEvent.Content -> content.append(event.text)
                else -> {
                    timer?.cancel()
                    timer = null
                    drain()
                    send(event)
                    return@withLock
                }
            }
            // Only the first token of a batch starts the timer; later ones don't reset it,
            // so no token waits longer than windowMillis to reach the UI.
            if (timer == null) {
                timer = launch {
                    delay(windowMillis)
                    lock.withLock {
                        timer = null
                        drain()
                    }
                }
            }
        }
    }

    // Upstream completed (e.g. [DONE] with no Usage): flush whatever is left.
    lock.withLock {
        timer?.cancel()
        drain()
    }
}
