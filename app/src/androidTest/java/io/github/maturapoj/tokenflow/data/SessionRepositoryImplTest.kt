package io.github.maturapoj.tokenflow.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.maturapoj.tokenflow.data.local.ChatDatabase
import io.github.maturapoj.tokenflow.data.repository.SessionRepositoryImpl
import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.domain.model.SessionMessage
import io.github.maturapoj.tokenflow.domain.model.StreamStats
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
    /** Ticks one millisecond per call so ordering by updatedAt is deterministic. */
    private val clock = object : Clock {
        private var time = 0L
        override fun now() = Instant.fromEpochMilliseconds(++time)
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChatDatabase::class.java).build()
        repo = SessionRepositoryImpl(db.chatDao(), clock)
    }

    @After fun tearDown() = db.close()

    @Test fun messagesRoundTripWithAndWithoutStats() = runTest {
        val id = repo.createSession("", "p/m")
        val stats = StreamStats(timeToFirstToken = 900.milliseconds, chunks = 4, completionTokens = 50, reasoningTokens = 10, total = 2.seconds, tokensPerSecond = 25.0)
        repo.addMessage(id, SessionMessage(0, Role.User, "q"))
        repo.addMessage(id, SessionMessage(0, Role.Assistant, "a", reasoning = "think", stats = stats))
        repo.addMessage(id, SessionMessage(0, Role.Assistant, "", error = ChatError.Server(500, "boom")))

        val messages = repo.getMessages(id)
        assertEquals(listOf(Role.User, Role.Assistant, Role.Assistant), messages.map { it.role })
        assertNull(messages[0].stats)
        assertEquals(stats, messages[1].stats)
        assertEquals("think", messages[1].reasoning)
        assertEquals(ChatError.Server(500, "boom"), messages[2].error)
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

    @Test fun streamedReplyIsUpdatedInPlaceOrDeleted() = runTest {
        val id = repo.createSession("", "p/m")
        repo.addMessage(id, SessionMessage(0, Role.User, "q"))
        val replyId = repo.addMessage(id, SessionMessage(0, Role.Assistant, ""))
        val emptyId = repo.addMessage(id, SessionMessage(0, Role.Assistant, ""))

        val stats = StreamStats(timeToFirstToken = 300.milliseconds, chunks = 2)
        repo.updateMessage(SessionMessage(replyId, Role.Assistant, "partial", reasoning = "r", error = ChatError.RateLimited, stats = stats))
        repo.deleteMessage(emptyId)

        val messages = repo.getMessages(id)
        assertEquals(listOf("q", "partial"), messages.map { it.content })
        assertEquals(ChatError.RateLimited, messages[1].error)
        assertEquals(stats, messages[1].stats)
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
