package com.agentpad.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To3Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration-1-3-test.db"

    @Before
    fun createVersionOneDatabase() {
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE tasks (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    goal TEXT NOT NULL,
                    status TEXT NOT NULL,
                    planJson TEXT NOT NULL,
                    result TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE audit_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    taskId TEXT NOT NULL,
                    actionId TEXT,
                    eventType TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO tasks
                    (id, title, goal, status, planJson, result, createdAt, updatedAt)
                VALUES
                    ('task-1', 'old task', 'continue old task', 'RUNNING', '', 'old result', 100, 200)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO audit_events
                    (id, taskId, actionId, eventType, summary, createdAt)
                VALUES
                    ('audit-1', 'task-1', NULL, 'STATUS_CHANGED', 'old audit', 150)
                """.trimIndent()
            )
            database.version = 1
        }
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesOldThreadsAndCreatesDocumentSearchTables() {
        val room = Room.databaseBuilder(context, PocketAgentDatabase::class.java, databaseName)
            .addMigrations(PocketAgentDatabase.MIGRATION_1_2, PocketAgentDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        room.openHelper.writableDatabase.query("SELECT title, status FROM agent_threads WHERE id = 'task-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("old task", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
        }
        room.openHelper.writableDatabase.query("SELECT goal, status, result FROM agent_turns WHERE id = 'task-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("continue old task", cursor.getString(0))
            assertEquals("INTERRUPTED", cursor.getString(1))
            assertEquals("old result", cursor.getString(2))
        }
        room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM document_grants").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        room.close()
    }
}