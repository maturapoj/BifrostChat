package io.github.maturapoj.tokenflow.presentation.chat.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class SmoothRevealTest {

    @Test fun `small backlog reveals at the minimum pace`() {
        assertEquals(MIN_CHARS_PER_SECOND, revealRate(5), 0.001f)
    }

    @Test fun `a burst speeds up so it drains within the catch-up time`() {
        assertEquals(600f, revealRate(300), 0.001f) // 300 chars / 0.5 s
    }

    @Test fun `never cuts an emoji in half`() {
        val text = "hi 😀!"
        val highSurrogate = text.indexOfFirst { it.isHighSurrogate() }
        assertEquals(highSurrogate + 2, safeEnd(text, highSurrogate + 1))
        assertEquals(2, safeEnd(text, 2))
    }
}
