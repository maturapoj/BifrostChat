package com.example.bifrostchat.data.remote

import com.example.bifrostchat.domain.model.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SseChunkParserTest {

    private fun events(line: String) = (SseChunkParser.parseLine(line) as SseLine.Chunk).events

    @Test fun `content delta`() {
        val line = """data: {"choices":[{"index":0,"delta":{"content":"Hi","reasoning":""}}],"usage":null}"""
        assertEquals(listOf(StreamEvent.Content("Hi")), events(line))
    }

    @Test fun `reasoning delta, and reasoning_content from other providers`() {
        assertEquals(
            listOf(StreamEvent.Reasoning("We")),
            events("""data: {"choices":[{"index":0,"delta":{"content":"","reasoning":"We"}}],"usage":null}"""),
        )
        assertEquals(
            listOf(StreamEvent.Reasoning("Hmm")),
            events("""data: {"choices":[{"index":0,"delta":{"reasoning_content":"Hmm"}}]}"""),
        )
    }

    @Test fun `finish and usage`() {
        val finish = """data: {"choices":[{"index":0,"finish_reason":"stop","delta":{"content":""}}],"usage":null}"""
        assertEquals(listOf(StreamEvent.Finished("stop")), events(finish))

        val usage = """data: {"choices":[{"index":0,"delta":{}}],"usage":{"prompt_tokens":89,"completion_tokens":96,"completion_tokens_details":{"reasoning_tokens":89}}}"""
        assertEquals(listOf(StreamEvent.Usage(89, 96, 89)), events(usage))
    }

    @Test fun `real gateway chunk with extra fields parses`() {
        // Captured from Bifrost: fields we don't model must be ignored.
        val line = """data: {"id":"chatcmpl-ff30","choices":[{"index":0,"delta":{"content":"","reasoning":"We","reasoning_details":[{"index":0,"type":"reasoning.text","text":"We"}]}}],"created":1790367126,"model":"deepseek-v4-flash-0731","object":"chat.completion.chunk","system_fingerprint":"","usage":null,"extra_fields":{"request_type":"chat_completion_stream","routing_info":{"provider":"dashscope","key":"production_key"},"latency":0,"chunk_index":1}}"""
        assertEquals(listOf(StreamEvent.Reasoning("We")), events(line))
    }

    @Test fun `done and ignored lines`() {
        assertEquals(SseLine.Done, SseChunkParser.parseLine("data: [DONE]"))
        assertEquals(SseLine.Ignored, SseChunkParser.parseLine(""))
        assertEquals(SseLine.Ignored, SseChunkParser.parseLine(": keep-alive"))
        assertEquals(SseLine.Ignored, SseChunkParser.parseLine("event: message"))
    }

    @Test(expected = BifrostException::class) fun `error payload throws`() {
        SseChunkParser.parseLine("""data: {"error":{"message":"bad key"}}""")
    }
}
