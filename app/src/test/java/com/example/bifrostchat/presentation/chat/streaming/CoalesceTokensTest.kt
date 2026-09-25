package com.example.bifrostchat.presentation.chat.streaming

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.bifrostchat.domain.model.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalesceTokensTest {

    @Test fun `burst is merged into one update`() = runTest {
        val out = flow {
            listOf("a", "b", "c").forEach { emit(StreamEvent.Content(it)) }
        }.coalesceTokens(50).toList()

        assertEquals(listOf(StreamEvent.Content("abc")), out)
    }

    @Test fun `tail of a burst is flushed by the timer, not held until the next burst`() = runTest {
        val out = flow {
            emit(StreamEvent.Content("a"))
            emit(StreamEvent.Content("b"))
            delay(1_000) // gap between gateway bursts
            emit(StreamEvent.Content("c"))
        }.coalesceTokens(50).toList()

        assertEquals(listOf(StreamEvent.Content("ab"), StreamEvent.Content("c")), out)
    }

    @Test fun `usage flushes pending text first and keeps channels apart`() = runTest {
        val usage = StreamEvent.Usage(1, 2, 0)
        val out = flow {
            emit(StreamEvent.Reasoning("think"))
            emit(StreamEvent.Content("hi"))
            emit(usage)
        }.coalesceTokens(50).toList()

        assertEquals(listOf(StreamEvent.Reasoning("think"), StreamEvent.Content("hi"), usage), out)
    }

    @Test fun `nothing is emitted twice`() = runTest {
        val out = flow {
            emit(StreamEvent.Content("a"))
            delay(100)
        }.coalesceTokens(50).toList()

        assertEquals(listOf(StreamEvent.Content("a")), out)
    }
}
