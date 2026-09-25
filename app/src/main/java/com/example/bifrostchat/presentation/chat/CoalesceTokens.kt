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
 * instead of once per chunk. Emits deltas (not accumulated text), so collectors keep appending.
 *
 * A timer flushes the batch, not the next token's arrival: the gateway sends chunks in
 * bursts, and waiting for the next token would leave the tail of each burst on hold until
 * the next burst. Usage/Finished flush the pending text first, then pass through.
 */
fun Flow<StreamEvent>.coalesceTokens(windowMillis: Long = 50L): Flow<StreamEvent> = channelFlow {
    val lock = Mutex()
    val reasoning = StringBuilder()
    val content = StringBuilder()
    var timer: Job? = null

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

    lock.withLock {
        timer?.cancel()
        drain()
    }
}
