package com.example.bifrostchat.data.local

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** "user" or "assistant". */
    val role: String,
    val content: String,
    val reasoning: String,
    /** Error detail text (v1 stored only this). */
    val error: String?,
    /** ChatError kind, e.g. "network", "rate_limited"; null for no error or rows from v1. Added in v2. */
    @ColumnInfo(defaultValue = "NULL") val errorKind: String? = null,
    /** HTTP status for server errors. Added in v2. */
    @ColumnInfo(defaultValue = "NULL") val errorCode: Int? = null,
    @Embedded(prefix = "stats_") val stats: StatsEntity?,
    val createdAt: Long,
)

/** All columns nullable so Room can store a null [MessageEntity.stats] as all-null columns. */
data class StatsEntity(
    val timeToFirstTokenMs: Long?,
    val chunks: Int?,
    val completionTokens: Int?,
    val reasoningTokens: Int?,
    val totalMs: Long?,
    val tokensPerSecond: Double?,
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    @Query("UPDATE sessions SET modelId = :modelId WHERE id = :id")
    suspend fun setModel(id: Long, modelId: String)

    @Query("UPDATE sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id")
    suspend fun messages(sessionId: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    /** Rewrites the streamed parts of a message; keeps its session and position. */
    @Query(
        """UPDATE messages SET content = :content, reasoning = :reasoning, error = :error,
           errorKind = :errorKind, errorCode = :errorCode,
           stats_timeToFirstTokenMs = :ttftMs, stats_chunks = :chunks, stats_completionTokens = :completionTokens,
           stats_reasoningTokens = :reasoningTokens, stats_totalMs = :totalMs, stats_tokensPerSecond = :tokensPerSecond
           WHERE id = :id""",
    )
    suspend fun updateMessage(
        id: Long, content: String, reasoning: String, error: String?, errorKind: String?, errorCode: Int?,
        ttftMs: Long?, chunks: Int?, completionTokens: Int?, reasoningTokens: Int?, totalMs: Long?, tokensPerSecond: Double?,
    )

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Transaction
    suspend fun addMessage(message: MessageEntity): Long {
        val id = insertMessage(message)
        touch(message.sessionId, message.createdAt)
        return id
    }
}

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
