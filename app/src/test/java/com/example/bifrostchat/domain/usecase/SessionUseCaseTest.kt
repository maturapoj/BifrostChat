package com.example.bifrostchat.domain.usecase

import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.fakes.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionUseCaseTest {

    @Test fun `title comes from the first user message only`() = runTest {
        val repo = FakeSessionRepository()
        val id = repo.createSession(title = "", modelId = "p/m")
        val useCase = SessionUseCase(repo)

        useCase.saveMessage(id, SessionMessage(0, Role.User, "How do I reverse a string?"))
        useCase.saveMessage(id, SessionMessage(0, Role.User, "Another question"))

        assertEquals("How do I reverse a string?", repo.getSession(id)!!.title)
    }

    @Test fun `assistant message does not set the title`() = runTest {
        val repo = FakeSessionRepository()
        val id = repo.createSession(title = "", modelId = "p/m")
        SessionUseCase(repo).saveMessage(id, SessionMessage(0, Role.Assistant, "Hello"))
        assertEquals("", repo.getSession(id)!!.title)
    }

    @Test fun `long first line is cut at a word boundary`() {
        assertEquals(
            "Explain the difference between…",
            SessionUseCase.titleFrom("Explain the difference between coroutines and threads in Kotlin\nplease"),
        )
        assertEquals("short", SessionUseCase.titleFrom("  short  \nsecond line"))
    }
}
