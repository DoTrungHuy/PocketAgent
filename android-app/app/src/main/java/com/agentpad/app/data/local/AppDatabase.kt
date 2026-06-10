package com.agentpad.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks", primaryKeys = ["id"])
data class TaskEntity(
    val id: String,
    val title: String,
    val goal: String,
    val status: String,
    val planJson: String,
    val result: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "audit_events", primaryKeys = ["id"])
data class AuditEventEntity(
    val id: String,
    val taskId: String,
    val actionId: String?,
    val eventType: String,
    val summary: String,
    val createdAt: Long
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Update
    suspend fun update(entity: TaskEntity)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AuditEventEntity)

    @Query("DELETE FROM audit_events")
    suspend fun clear()
}

@Database(
    entities = [TaskEntity::class, AuditEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AgentPadDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var instance: AgentPadDatabase? = null

        fun get(context: Context): AgentPadDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentPadDatabase::class.java,
                    "agentpad.db"
                ).build().also { instance = it }
            }
    }
}
