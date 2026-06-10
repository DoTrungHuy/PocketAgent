package com.agentpad.app.data

import com.agentpad.app.data.local.AuditDao
import com.agentpad.app.data.local.AuditEventEntity
import com.agentpad.app.data.local.TaskDao
import com.agentpad.app.data.local.TaskEntity
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.domain.TaskRecord
import com.agentpad.app.domain.TaskStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class AgentPadRepository(
    private val taskDao: TaskDao,
    private val auditDao: AuditDao
) {
    fun observeRecentTasks(): Flow<List<TaskRecord>> = taskDao.observeRecent().map { entities ->
        entities.map { entity ->
            TaskRecord(
                id = entity.id,
                title = entity.title,
                goal = entity.goal,
                status = TaskStatus.valueOf(entity.status),
                updatedAt = entity.updatedAt,
                result = entity.result
            )
        }
    }

    suspend fun savePlan(plan: TaskPlan, status: TaskStatus = TaskStatus.AWAITING_APPROVAL) {
        val now = System.currentTimeMillis()
        val existing = taskDao.get(plan.id)
        taskDao.upsert(
            TaskEntity(
                id = plan.id,
                title = plan.title,
                goal = plan.goal,
                status = status.name,
                planJson = encodePlan(plan),
                result = existing?.result,
                createdAt = existing?.createdAt ?: plan.createdAt,
                updatedAt = now
            )
        )
        audit(plan.id, null, "PLAN_CREATED", "任务计划已生成，共 ${plan.actions.size} 步")
    }

    suspend fun updateStatus(plan: TaskPlan, status: TaskStatus, result: String? = null) {
        val existing = taskDao.get(plan.id) ?: return
        taskDao.upsert(
            existing.copy(
                status = status.name,
                result = result ?: existing.result,
                updatedAt = System.currentTimeMillis()
            )
        )
        audit(plan.id, null, "STATUS_CHANGED", "任务状态变更为 ${status.name}")
    }

    suspend fun audit(
        taskId: String,
        actionId: String?,
        eventType: String,
        summary: String
    ) {
        auditDao.insert(
            AuditEventEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                actionId = actionId,
                eventType = eventType,
                summary = summary.take(500),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun encodePlan(plan: TaskPlan): String = JSONObject()
        .put("id", plan.id)
        .put("goal", plan.goal)
        .put("title", plan.title)
        .put("summary", plan.summary)
        .put("stopCondition", plan.stopCondition)
        .put(
            "actions",
            JSONArray().apply {
                plan.actions.forEach { action ->
                    put(
                        JSONObject()
                            .put("id", action.id)
                            .put("title", action.title)
                            .put("description", action.description)
                            .put("tool", action.tool)
                            .put("risk", action.risk.name)
                            .put("reversible", action.reversible)
                            .put("arguments", JSONObject(action.arguments))
                    )
                }
            }
        )
        .toString()
}
