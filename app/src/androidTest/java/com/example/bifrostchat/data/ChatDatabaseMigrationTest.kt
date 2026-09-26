package com.example.bifrostchat.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bifrostchat.data.local.ChatDatabase
import com.example.bifrostchat.data.repository.SessionRepositoryImpl
import com.example.bifrostchat.domain.model.ChatError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the 1 → 2 auto-migration against the exported schemas in app/schemas. */
@RunWith(AndroidJUnit4::class)
class ChatDatabaseMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ChatDatabase::class.java)

    @Test fun v1RowsSurviveAndOldErrorTextReadsAsUnknown() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO sessions (id, title, modelId, createdAt, updatedAt) VALUES (1, 'old chat', 'p/m', 1, 1)")
            execSQL(
                "INSERT INTO messages (id, sessionId, role, content, reasoning, error, createdAt) " +
                    "VALUES (1, 1, 'user', 'hi', '', NULL, 1), (2, 1, 'assistant', 'par', '', 'HTTP 500: boom', 2)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ChatDatabase::class.java, dbName).build()
        try {
            val messages = SessionRepositoryImpl(db.chatDao()).getMessages(1)
            assertEquals(listOf("hi", "par"), messages.map { it.content })
            assertEquals(null, messages[0].error)
            assertEquals(ChatError.Unknown("HTTP 500: boom"), messages[1].error)
        } finally {
            db.close()
        }
    }
}
