package com.agentpad.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agent_threads")
data class AgentThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "agent_turns",
    foreignKeys = [
        ForeignKey(
            entity = AgentThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId"), Index(value = ["threadId", "ordinal"], unique = true)]
)
data class AgentTurnEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val ordinal: Int,
    val title: String,
    val goal: String,
    val status: String,
    val planJson: String,
    val result: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "thread_messages",
    foreignKeys = [
        ForeignKey(
            entity = AgentThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId"), Index("turnId")]
)
data class ThreadMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val turnId: String?,
    val role: String,
    val kind: String,
    val content: String,
    val createdAt: Long
)

@Entity(
    tableName = "thread_attachments",
    foreignKeys = [
        ForeignKey(
            entity = AgentThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId"), Index("turnId")]
)
data class ThreadAttachmentEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val turnId: String?,
    val uri: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val createdAt: Long
)

@Entity(tableName = "audit_events", indices = [Index("taskId")])
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val actionId: String?,
    val eventType: String,
    val summary: String,
    val createdAt: Long
)

@Dao
interface ThreadDao {
    @Query("SELECT * FROM agent_threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AgentThreadEntity>>

    @Query("SELECT * FROM agent_threads WHERE id = :id LIMIT 1")
    suspend fun getThread(id: String): AgentThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(entity: AgentThreadEntity)

    @Query("SELECT * FROM agent_turns WHERE threadId = :threadId ORDER BY ordinal ASC")
    fun observeTurns(threadId: String): Flow<List<AgentTurnEntity>>

    @Query("SELECT * FROM agent_turns WHERE threadId = :threadId ORDER BY ordinal ASC")
    suspend fun getTurns(threadId: String): List<AgentTurnEntity>

    @Query("SELECT * FROM agent_turns WHERE id = :id LIMIT 1")
    suspend fun getTurn(id: String): AgentTurnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTurn(entity: AgentTurnEntity)

    @Query("SELECT COALESCE(MAX(ordinal), 0) FROM agent_turns WHERE threadId = :threadId")
    suspend fun maxOrdinal(threadId: String): Int

    @Query(
        """
        UPDATE agent_turns
        SET status = 'INTERRUPTED', updatedAt = :now
        WHERE status IN ('PLANNING', 'RUNNING', 'VERIFYING')
        """
    )
    suspend fun interruptActiveTurns(now: Long)

    @Query(
        """
        UPDATE agent_turns
        SET status = 'SUPERSEDED', updatedAt = :now
        WHERE threadId = :threadId
          AND status IN ('DRAFT', 'AWAITING_APPROVAL', 'INTERRUPTED')
        """
    )
    suspend fun supersedePendingTurns(threadId: String, now: Long)

    @Query("SELECT * FROM thread_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeMessages(threadId: String): Flow<List<ThreadMessageEntity>>

    @Query("SELECT * FROM thread_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun getMessages(threadId: String): List<ThreadMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ThreadMessageEntity)

    @Query("SELECT * FROM thread_attachments WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun getAttachments(threadId: String): List<ThreadAttachmentEntity>

    @Query("SELECT COUNT(*) FROM thread_attachments WHERE uri = :uri")
    suspend fun countAttachmentsByUri(uri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(entity: ThreadAttachmentEntity)

    @Query("UPDATE agent_threads SET updatedAt = :now, status = :status WHERE id = :threadId")
    suspend fun touchThread(threadId: String, status: String, now: Long)

    @Query("DELETE FROM agent_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: String)

    @Transaction
    suspend fun insertTurnBundle(
        newThread: AgentThreadEntity?,
        turn: AgentTurnEntity,
        message: ThreadMessageEntity,
        attachment: ThreadAttachmentEntity?,
        now: Long
    ): AgentTurnEntity {
        if (newThread == null) {
            supersedePendingTurns(turn.threadId, now)
            touchThread(turn.threadId, "ACTIVE", now)
        } else {
            upsertThread(newThread)
        }
        val storedTurn = turn.copy(ordinal = maxOrdinal(turn.threadId) + 1)
        upsertTurn(storedTurn)
        insertMessage(message)
        attachment?.let { insertAttachment(it) }
        return storedTurn
    }
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<AuditEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AuditEventEntity)

    @Query("DELETE FROM audit_events")
    suspend fun clear()

    @Query("DELETE FROM audit_events WHERE taskId IN (SELECT id FROM agent_turns WHERE threadId = :threadId)")
    suspend fun deleteForThread(threadId: String)
}

@Database(
    entities = [
        AgentThreadEntity::class,
        AgentTurnEntity::class,
        ThreadMessageEntity::class,
        ThreadAttachmentEntity::class,
        AuditEventEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AgentPadDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun auditDao(): AuditDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_threads (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_turns (
                        id TEXT NOT NULL PRIMARY KEY,
                        threadId TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        status TEXT NOT NULL,
                        planJson TEXT NOT NULL,
                        result TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(threadId) REFERENCES agent_threads(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS thread_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        threadId TEXT NOT NULL,
                        turnId TEXT,
                        role TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(threadId) REFERENCES agent_threads(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS thread_attachments (
                        id TEXT NOT NULL PRIMARY KEY,
                        threadId TEXT NOT NULL,
                        turnId TEXT,
                        uri TEXT NOT NULL,
                        name TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        size INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(threadId) REFERENCES agent_threads(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_turns_threadId ON agent_turns(threadId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_agent_turns_threadId_ordinal " +
                        "ON agent_turns(threadId, ordinal)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thread_messages_threadId ON thread_messages(threadId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thread_messages_turnId ON thread_messages(turnId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thread_attachments_threadId ON thread_attachments(threadId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thread_attachments_turnId ON thread_attachments(turnId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_taskId ON audit_events(taskId)")

                db.execSQL(
                    """
                    INSERT INTO agent_threads (id, title, status, createdAt, updatedAt)
                    SELECT id, title,
                        CASE WHEN status = 'COMPLETED' THEN 'COMPLETED'
                             WHEN status = 'FAILED' THEN 'FAILED'
                             ELSE 'ACTIVE' END,
                        createdAt, updatedAt
                    FROM tasks
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO agent_turns
                        (id, threadId, ordinal, title, goal, status, planJson, result, createdAt, updatedAt)
                    SELECT id, id, 1, title, goal,
                        CASE WHEN status IN ('PLANNING', 'RUNNING', 'VERIFYING')
                             THEN 'INTERRUPTED' ELSE status END,
                        planJson, result, createdAt, updatedAt
                    FROM tasks
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO thread_messages
                        (id, threadId, turnId, role, kind, content, createdAt)
                    SELECT 'legacy-user-' || id, id, id, 'USER', 'GOAL', goal, createdAt
                    FROM tasks
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO thread_messages
                        (id, threadId, turnId, role, kind, content, createdAt)
                    SELECT 'legacy-result-' || id, id, id, 'ASSISTANT', 'RESULT', result, updatedAt
                    FROM tasks WHERE result IS NOT NULL AND result != ''
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE tasks")
            }
        }

        @Volatile
        private var instance: AgentPadDatabase? = null

        fun get(context: Context): AgentPadDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentPadDatabase::class.java,
                    "agentpad.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
