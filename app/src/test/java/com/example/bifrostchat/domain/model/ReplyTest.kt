package com.example.bifrostchat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReplyTest {

    private val empty = SessionMessage(1, Role.Assistant, "")

    @Test fun `chunks append per channel and the first sets time to first token`() {
        val reply = empty
            .apply(StreamEvent.Reasoning("think"), 800.milliseconds)
            .apply(StreamEvent.Content("Hel"), 900.milliseconds)
            .apply(StreamEvent.Content("lo"), 950.milliseconds)

        assertEquals("think", reply.reasoning)
        assertEquals("Hello", reply.content)
        assertEquals(800.milliseconds, reply.stats?.timeToFirstToken)
        assertEquals(3, reply.stats?.chunks)
    }

    @Test fun `usage sets tokens per second over the whole request`() {
        val stats = empty.apply(StreamEvent.Usage(10, 100, 20), 2.seconds).stats!!
        assertEquals(100, stats.completionTokens)
        assertEquals(20, stats.reasoningTokens)
        assertEquals(2.seconds, stats.total)
        assertEquals(50.0, stats.tokensPerSecond!!, 0.001)
    }

    @Test fun `finish changes nothing`() {
        assertEquals(empty, empty.apply(StreamEvent.Finished("stop"), 1.seconds))
    }

    @Test fun `hasContent needs text, reasoning or an error`() {
        assertFalse(empty.hasContent)
        assertTrue(empty.copy(reasoning = "r").hasContent)
        assertTrue(empty.copy(error = ChatError.Network).hasContent)
    }
}
