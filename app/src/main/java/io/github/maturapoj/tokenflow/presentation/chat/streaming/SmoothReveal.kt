package io.github.maturapoj.tokenflow.presentation.chat.streaming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.first

/** Never crawl slower than this, so a short tail doesn't trickle in. */
internal const val MIN_CHARS_PER_SECOND = 80f

/** Aim to empty the current backlog within this time: bigger bursts reveal faster. */
internal const val CATCH_UP_SECONDS = 0.5f

internal fun revealRate(backlog: Int): Float = max(MIN_CHARS_PER_SECOND, backlog / CATCH_UP_SECONDS)

/** Moves [end] past a low surrogate so an emoji is never cut in half. */
internal fun safeEnd(text: String, end: Int): Int =
    if (end in 1 until text.length && text[end - 1].isHighSurrogate()) end + 1 else end

/**
 * Returns a prefix of [target] that grows at a steady, backlog-adaptive pace, so bursty
 * delivery (the gateway sends ~20 chunks at once, then pauses) reads as smooth typing.
 *
 * Text already present on first composition is shown at once (e.g. after rotation or
 * scrolling back to an old message). The frame loop only runs while there is a backlog,
 * so idle messages don't request frames.
 */
@Composable
fun rememberSmoothReveal(target: String): String {
    val latest = rememberUpdatedState(target)
    var shown by remember { mutableIntStateOf(target.length) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { latest.value.length }.first { it != shown }
            if (latest.value.length < shown) {
                shown = latest.value.length
                continue
            }
            var carry = 0f
            var last = withFrameNanos { it }
            while (shown < latest.value.length) {
                withFrameNanos { now ->
                    val backlog = latest.value.length - shown
                    carry += revealRate(backlog) * (now - last) / 1_000_000_000f
                    last = now
                    val step = carry.toInt()
                    if (step > 0) {
                        shown = min(latest.value.length, shown + step)
                        carry -= step
                    }
                }
            }
        }
    }

    val end = safeEnd(target, min(shown, target.length))
    return target.substring(0, end)
}
