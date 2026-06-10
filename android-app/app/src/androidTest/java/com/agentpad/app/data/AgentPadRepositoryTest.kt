package com.agentpad.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agentpad.app.data.local.AgentPadDatabase
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
class AgentPadRepositoryTest {
    private lateinit var database: AgentPadDatabase
    private lateinit var repository: AgentPadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgentPadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AgentPadRepository(database.threadDao(), database.auditDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun followUpSupersedesUnexecutedPlanAndCreatesImmutableNextTurn() = runBlocking {
        val first = repository.beginTurn(null, "第一回合", null)
        repository.savePlan(first, plan("plan-1", "第一回合"))

        val second = repository.beginTurn(first.threadId, "继续追问", null)
        val snapshot = requireNotNull(repository.loadThread(first.threadId))

        assertEquals(listOf(1, 2), snapshot.turns.map { it.ordinal })
        assertEquals(TurnStatus.SUPERSEDED, snapshot.turns.first().status)
        assertEquals(TurnStatus.PLANNING, snapshot.turns.last().status)
        assertEquals(second.id, snapshot.turns.last().id)
    }

    @Test
    fun deletingThreadReleasesOnlyUrisNoLongerUsedByAnotherThread() = runBlocking {
        val sharedUri = "content://agentpad/shared"
        val first = repository.beginTurn(
            null,
            "线程一",
            ThreadAttachment(
                threadId = "",
                turnId = null,
                uri = sharedUri,
                name = "shared.txt",
                mimeType = "text/plain",
                size = 12
            )
        )
        val second = repository.beginTurn(
            null,
            "线程二",
            ThreadAttachment(
                threadId = "",
                turnId = null,
                uri = sharedUri,
                name = "shared.txt",
                mimeType = "text/plain",
                size = 12
            )
        )

        assertTrue(repository.deleteThread(first.threadId).isEmpty())
        assertEquals(sharedUri, repository.deleteThread(second.threadId).single().uri)
    }

    @Test
    fun startupRecoveryMarksActiveTurnInterrupted() = runBlocking {
        val turn = repository.beginTurn(null, "恢复测试", null)
        val planned = repository.savePlan(turn, plan("plan-recovery", "恢复测试"))
        repository.updateStatus(planned, TurnStatus.RUNNING)

        repository.interruptActiveTurns()

        val recovered = requireNotNull(repository.loadThread(turn.threadId)).turns.single()
        assertEquals(TurnStatus.INTERRUPTED, recovered.status)
    }

    private fun plan(id: String, goal: String) = TaskPlan(
        id = id,
        goal = goal,
        title = goal,
        summary = "摘要",
        actions = listOf(
            PlannedAction(
                title = "检查",
                description = "检查任务",
                tool = "inspect_task",
                risk = RiskLevel.READ_ONLY,
                reversible = true
            )
        )
    )
}
