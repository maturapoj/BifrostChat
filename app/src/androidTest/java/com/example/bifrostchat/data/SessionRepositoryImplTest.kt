package com.example.bifrostchat.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bifrostchat.data.local.ChatDatabase
import com.example.bifrostchat.data.repository.SessionRepositoryImpl
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.SessionMessage
import com.example.bifrostchat.domain.model.StreamStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against a real in-memory Room database on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class SessionRepositoryImplTest {

    private lateinit var db: ChatDatabase
    private lateinit var repo: SessionRepositoryImpl
    private var time = 0L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChatDatabase::class.java).build()
        repo = SessionRepositoryImpl(db.chatDao(), now = { ++time })
    }

    @After fun tearDown() = db.close()

    @Test fun messagesRoundTripWithAndWithoutStats() = runTest {
        val id = repo.createSession("", "p/m")
        val stats = StreamStats(timeToFirstTokenMs = 900, chunks = 4, completionTokens = 50, reasoningTokens = 10, totalMs = 2000, tokensPerSecond = 25.0)
        repo.addMessage(id, SessionMessage(0, Role.User, "q"))
        repo.addMessage(id, SessionMessage(0, Role.Assistant, "a", reasoning = "think", stats = stats))
        repo.addMessage(id, SessionMessage(0, Role.Assistant, "", error = "HTTP 500"))

        val messages = repo.getMessages(id)
        assertEquals(listOf(Role.User, Role.Assistant, Role.Assistant), messages.map { it.role })
        assertNull(messages[0].stats)
        assertEquals(stats, messages[1].stats)
        assertEquals("think", messages[1].reasoning)
        assertEquals("HTTP 500", messages[2].error)
    }

    @Test fun newestActivityListsFirst() = runTest {
        val a = repo.createSession("a", "p/m")
        val b = repo.createSession("b", "p/m")
        assertEquals(listOf(b, a), repo.observeSessions().first().map { it.id })

        repo.addMessage(a, SessionMessage(0, Role.User, "bump"))
        assertEquals(listOf(a, b), repo.observeSessions().first().map { it.id })
    }

    @Test fun deletingASessionRemovesItsMessages() = runTest {
        val id = repo.createSession("t", "p/m")
        repo.addMessage(id, SessionMessage(0, Role.User, "q"))

        repo.deleteSession(id)

        assertNull(repo.getSession(id))
        assertTrue(repo.getMessages(id).isEmpty())
    }

    @Test fun titleAndModelUpdates() = runTest {
        val id = repo.createSession("", "p/m1")
        repo.setTitle(id, "Named")
        repo.setModel(id, "p/m2")
        val session = repo.getSession(id)!!
        assertEquals("Named", session.title)
        assertEquals("p/m2", session.modelId)
    }
}
