package com.example.bifrostchat.data.remote

import com.example.bifrostchat.domain.model.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseChunkParserTest {

    @Test fun `content delta`() {
        val line = """data: {"choices":[{"index":0,"delta":{"content":"Hi","reasoning":""}}],"usage":null}"""
        assertEquals(listOf(StreamEvent.Content("Hi")), SseChunkParser.parseLine(line))
    }

    @Test fun `reasoning delta`() {
        val line = """data: {"choices":[{"index":0,"delta":{"content":"","reasoning":"We"}}],"usage":null}"""
        assertEquals(listOf(StreamEvent.Reasoning("We")), SseChunkParser.parseLine(line))
    }

    @Test fun `finish and usage`() {
        val finish = """data: {"choices":[{"index":0,"finish_reason":"stop","delta":{"content":""}}],"usage":null}"""
        assertEquals(listOf(StreamEvent.Finished("stop")), SseChunkParser.parseLine(finish))

        val usage = """data: {"choices":[{"index":0,"delta":{}}],"usage":{"prompt_tokens":89,"completion_tokens":96,"completion_tokens_details":{"reasoning_tokens":89}}}"""
        assertEquals(listOf(StreamEvent.Usage(89, 96, 89)), SseChunkParser.parseLine(usage))
    }

    @Test fun `done and blank lines`() {
        assertNull(SseChunkParser.parseLine("data: [DONE]"))
        assertEquals(emptyList<StreamEvent>(), SseChunkParser.parseLine(""))
        assertEquals(emptyList<StreamEvent>(), SseChunkParser.parseLine(": keep-alive"))
    }

    @Test(expected = BifrostException::class) fun `error payload throws`() {
        SseChunkParser.parseLine("""data: {"error":{"message":"bad key"}}""")
    }
}
