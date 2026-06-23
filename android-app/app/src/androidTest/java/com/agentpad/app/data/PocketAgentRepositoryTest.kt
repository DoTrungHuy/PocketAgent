package com.agentpad.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agentpad.app.data.local.PocketAgentDatabase
import com.agentpad.app.domain.PlannedAction
import com.agentpad.app.domain.RiskLevel
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.domain.ThreadAttachment
import com.agentpad.app.domain.TurnStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketAgentRepositoryTest {
    private lateinit var database: PocketAgentDatabase
    private lateinit var repository: PocketAgentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PocketAgentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PocketAgentRepository(database.threadDao(), database.auditDao(), database.documentDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun followUpSupersedesUnexecutedPlanAndCreatesImmutableNextTurn() = runBlocking {
        val first = repository.beginTurn(null, "first turn", null)
        repository.savePlan(first, plan("plan-1", "first turn"))

        val second = repository.beginTurn(first.threadId, "follow up", null)
        val snapshot = requireNotNull(repository.loadThread(first.threadId))

        assertEquals(listOf(1, 2), snapshot.turns.map { it.ordinal })
        assertEquals(TurnStatus.SUPERSEDED, snapshot.turns.first().status)
        assertEquals(TurnStatus.PLANNING, snapshot.turns.last().status)
        assertEquals(second.id, snapshot.turns.last().id)
    }

    @Test
    fun deletingThreadReleasesOnlyUrisNoLongerUsedByAnotherThread() = runBlocking {
        val sharedUri = "content://pocketagent/shared"
        val first = repository.beginTurn(null, "thread one", attachment(sharedUri))
        val second = repository.beginTurn(null, "thread two", attachment(sharedUri))

        assertTrue(repository.deleteThread(first.threadId).isEmpty())
        assertEquals(sharedUri, repository.deleteThread(second.threadId).single().uri)
    }

    @Test
    fun startupRecoveryMarksActiveTurnInterrupted() = runBlocking {
        val turn = repository.beginTurn(null, "recovery", null)
        val planned = repository.savePlan(turn, plan("plan-recovery", "recovery"))
        repository.updateStatus(planned, TurnStatus.RUNNING)

        repository.interruptActiveTurns()

        val recovered = requireNotNull(repository.loadThread(turn.threadId)).turns.single()
        assertEquals(TurnStatus.INTERRUPTED, recovered.status)
    }

    private fun attachment(uri: String) = ThreadAttachment(
        threadId = "",
        turnId = null,
        uri = uri,
        name = "shared.txt",
        mimeType = "text/plain",
        size = 12
    )

    private fun plan(id: String, goal: String) = TaskPlan(
        id = id,
        goal = goal,
        title = goal,
        summary = "summary",
        actions = listOf(
            PlannedAction(
                title = "Inspect",
                description = "Inspect task",
                tool = "inspect_task",
                risk = RiskLevel.READ_ONLY,
                reversible = true
            )
        )
    )
}