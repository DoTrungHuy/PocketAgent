package com.agentpad.app.data

import com.agentpad.app.data.local.AgentThreadEntity
import com.agentpad.app.data.local.AgentTurnEntity
import com.agentpad.app.data.local.AuditDao
import com.agentpad.app.data.local.AuditEventEntity
import com.agentpad.app.data.local.DocumentDao
import com.agentpad.app.data.local.DocumentGrantEntity
import com.agentpad.app.data.local.DocumentIndexEntryEntity
import com.agentpad.app.data.local.DocumentSearchRunEntity
import com.agentpad.app.data.local.ThreadAttachmentEntity
import com.agentpad.app.data.local.ThreadDao
import com.agentpad.app.data.local.ThreadMessageEntity
import com.agentpad.app.domain.AgentThread
import com.agentpad.app.domain.AgentTurn
import com.agentpad.app.domain.DocumentGrant
import com.agentpad.app.domain.DocumentGrantKind
import com.agentpad.app.domain.DocumentIndexEntry
import com.agentpad.app.domain.DocumentSearchStage
import com.agentpad.app.domain.MessageKind
import com.agentpad.app.domain.MessageRole
import com.agentpad.app.domain.TaskPlan
import com.agentpad.app.domain.ThreadAttachment
import com.agentpad.app.domain.ThreadMessage
import com.agentpad.app.domain.ThreadSnapshot
import com.agentpad.app.domain.ThreadStatus
import com.agentpad.app.domain.TurnStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PocketAgentRepository(
    private val threadDao: ThreadDao,
    private val auditDao: AuditDao,
    private val documentDao: DocumentDao? = null
) {
    fun observeThreads(): Flow<List<AgentThread>> = threadDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun interruptActiveTurns() {
        threadDao.interruptActiveTurns(System.currentTimeMillis())
    }

    suspend fun loadThread(threadId: String): ThreadSnapshot? {
        val thread = threadDao.getThread(threadId) ?: return null
        return ThreadSnapshot(
            thread = thread.toDomain(),
            turns = threadDao.getTurns(threadId).map { it.toDomain() },
            messages = threadDao.getMessages(threadId).map { it.toDomain() },
            attachments = threadDao.getAttachments(threadId).map { it.toDomain() }
        )
    }

    suspend fun beginTurn(
        threadId: String?,
        goal: String,
        attachment: ThreadAttachment?
    ): AgentTurn = beginTurnInternal(threadId, goal, TurnStatus.PLANNING, attachment, titleFallback = "New task")

    suspend fun beginChatTurn(
        threadId: String?,
        prompt: String,
        attachment: ThreadAttachment?
    ): AgentTurn {
        val stored = beginTurnInternal(threadId, prompt, TurnStatus.RUNNING, attachment, titleFallback = "New chat")
        audit(stored.id, null, "CHAT_STARTED", "Chat request started")
        return stored
    }

    private suspend fun beginTurnInternal(
        threadId: String?,
        goal: String,
        status: TurnStatus,
        attachment: ThreadAttachment?,
        titleFallback: String
    ): AgentTurn {
        val now = System.currentTimeMillis()
        val resolvedThreadId = threadId ?: UUID.randomUUID().toString()
        val title = goal.lineSequence().firstOrNull().orEmpty().take(80).ifBlank { titleFallback }
        val newThread = if (threadId == null) {
            AgentThreadEntity(
                id = resolvedThreadId,
                title = title,
                status = ThreadStatus.ACTIVE.name,
                createdAt = now,
                updatedAt = now
            )
        } else {
            null
        }
        val turn = AgentTurn(
            threadId = resolvedThreadId,
            ordinal = 0,
            goal = goal,
            plan = null,
            status = status,
            result = null,
            createdAt = now,
            updatedAt = now
        )
        val stored = threadDao.insertTurnBundle(
            newThread = newThread,
            turn = turn.toEntity(title = title),
            message = ThreadMessage(
                threadId = resolvedThreadId,
                turnId = turn.id,
                role = MessageRole.USER,
                kind = MessageKind.GOAL,
                content = goal,
                createdAt = now
            ).toEntity(),
            attachment = attachment?.copy(threadId = resolvedThreadId, turnId = turn.id)?.toEntity(),
            now = now
        )
        return stored.toDomain()
    }

    suspend fun completeChatTurn(turn: AgentTurn, reply: String): AgentTurn {
        val now = System.currentTimeMillis()
        val updated = turn.copy(status = TurnStatus.COMPLETED, result = reply, updatedAt = now)
        threadDao.upsertTurn(updated.toEntity(turn.goal.take(80)))
        threadDao.insertMessage(
            ThreadMessage(
                threadId = turn.threadId,
                turnId = turn.id,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.RESULT,
                content = reply,
                createdAt = now
            ).toEntity()
        )
        threadDao.touchThread(turn.threadId, ThreadStatus.ACTIVE.name, now)
        audit(turn.id, null, "CHAT_COMPLETED", "Chat reply saved")
        return updated
    }

    suspend fun failChatTurn(turn: AgentTurn, error: String): AgentTurn {
        val now = System.currentTimeMillis()
        val updated = turn.copy(status = TurnStatus.FAILED, result = error, updatedAt = now)
        threadDao.upsertTurn(updated.toEntity(turn.goal.take(80)))
        threadDao.touchThread(turn.threadId, ThreadStatus.ACTIVE.name, now)
        audit(turn.id, null, "CHAT_FAILED", error)
        return updated
    }

    suspend fun savePlan(turn: AgentTurn, plan: TaskPlan): AgentTurn {
        val now = System.currentTimeMillis()
        val updated = turn.copy(plan = plan, status = TurnStatus.AWAITING_APPROVAL, updatedAt = now)
        threadDao.upsertTurn(updated.toEntity(plan.title))
        threadDao.insertMessage(
            ThreadMessage(
                threadId = turn.threadId,
                turnId = turn.id,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.PLAN,
                content = plan.summary,
                createdAt = now
            ).toEntity()
        )
        threadDao.touchThread(turn.threadId, ThreadStatus.ACTIVE.name, now)
        audit(turn.id, null, "PLAN_CREATED", "Task plan created with ${plan.actions.size} actions")
        return updated
    }

    suspend fun updateStatus(turn: AgentTurn, status: TurnStatus, result: String? = null): AgentTurn {
        val now = System.currentTimeMillis()
        val updated = turn.copy(status = status, result = result ?: turn.result, updatedAt = now)
        threadDao.upsertTurn(updated.toEntity(turn.plan?.title ?: turn.goal.take(80)))
        if (!result.isNullOrBlank()) {
            threadDao.insertMessage(
                ThreadMessage(
                    threadId = turn.threadId,
                    turnId = turn.id,
                    role = MessageRole.ASSISTANT,
                    kind = MessageKind.RESULT,
                    content = result,
                    createdAt = now
                ).toEntity()
            )
        }
        val threadStatus = when (status) {
            TurnStatus.COMPLETED -> ThreadStatus.COMPLETED
            TurnStatus.FAILED -> ThreadStatus.FAILED
            else -> ThreadStatus.ACTIVE
        }
        threadDao.touchThread(turn.threadId, threadStatus.name, now)
        audit(turn.id, null, "STATUS_CHANGED", "Status changed to ${status.name}")
        return updated
    }

    suspend fun addContextSummary(threadId: String, summary: String) {
        val now = System.currentTimeMillis()
        threadDao.insertMessage(
            ThreadMessage(
                threadId = threadId,
                turnId = null,
                role = MessageRole.SYSTEM,
                kind = MessageKind.CONTEXT_SUMMARY,
                content = summary,
                createdAt = now
            ).toEntity()
        )
        threadDao.touchThread(threadId, ThreadStatus.ACTIVE.name, now)
    }

    suspend fun addAssistantResult(threadId: String, turnId: String?, content: String) {
        val now = System.currentTimeMillis()
        threadDao.insertMessage(
            ThreadMessage(
                threadId = threadId,
                turnId = turnId,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.RESULT,
                content = content,
                createdAt = now
            ).toEntity()
        )
        threadDao.touchThread(threadId, ThreadStatus.ACTIVE.name, now)
    }

    suspend fun removeAttachment(attachmentId: String): ThreadAttachment? {
        val attachment = threadDao.getAttachment(attachmentId)?.toDomain() ?: return null
        threadDao.deleteAttachment(attachmentId)
        return attachment.takeIf { threadDao.countAttachmentsByUri(it.uri) == 0 }
    }

    suspend fun deleteThread(threadId: String): List<ThreadAttachment> {
        val attachments = threadDao.getAttachments(threadId).map { it.toDomain() }
        auditDao.deleteForThread(threadId)
        threadDao.deleteThread(threadId)
        return attachments.distinctBy { it.uri }.filter { threadDao.countAttachmentsByUri(it.uri) == 0 }
    }

    suspend fun recentAuditSummaries(limit: Int = 20): List<String> =
        auditDao.getRecent(limit).map { "${it.eventType}: ${it.summary}" }

    suspend fun audit(taskId: String, actionId: String?, eventType: String, summary: String) {
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

    suspend fun loadDocumentGrants(): List<DocumentGrant> =
        requireDocumentDao().getGrants().map { it.toDomain() }

    suspend fun saveDocumentGrant(grant: DocumentGrant): DocumentGrant {
        requireDocumentDao().upsertGrant(grant.toEntity())
        return grant
    }

    suspend fun removeDocumentGrant(grantId: String): DocumentGrant? {
        val grant = loadDocumentGrants().firstOrNull { it.id == grantId } ?: return null
        requireDocumentDao().deleteGrant(grantId)
        return grant
    }

    suspend fun loadDocumentIndex(): List<DocumentIndexEntry> =
        requireDocumentDao().getIndexEntries().map { it.toDomain() }

    suspend fun replaceDocumentIndex(grantId: String, entries: List<DocumentIndexEntry>) {
        val dao = requireDocumentDao()
        dao.deleteIndexForGrant(grantId)
        if (entries.isNotEmpty()) {
            dao.upsertIndexEntries(entries.map { it.toEntity() })
        }
        dao.markIndexed(grantId, System.currentTimeMillis())
    }

    suspend fun recordDocumentSearch(query: String, stage: DocumentSearchStage) {
        val now = System.currentTimeMillis()
        requireDocumentDao().upsertSearchRun(
            DocumentSearchRunEntity(
                id = UUID.randomUUID().toString(),
                query = query,
                stage = stage.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun requireDocumentDao(): DocumentDao =
        documentDao ?: error("Document search storage is not configured")

    private fun AgentThreadEntity.toDomain() = AgentThread(
        id = id,
        title = title,
        status = ThreadStatus.valueOf(status),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun AgentTurnEntity.toDomain() = AgentTurn(
        id = id,
        threadId = threadId,
        ordinal = ordinal,
        goal = goal,
        plan = PlanCodec.decode(planJson),
        status = TurnStatus.valueOf(status),
        result = result,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ThreadMessageEntity.toDomain() = ThreadMessage(
        id = id,
        threadId = threadId,
        turnId = turnId,
        role = MessageRole.valueOf(role),
        kind = MessageKind.valueOf(kind),
        content = content,
        createdAt = createdAt
    )

    private fun ThreadAttachmentEntity.toDomain() = ThreadAttachment(
        id = id,
        threadId = threadId,
        turnId = turnId,
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        createdAt = createdAt
    )

    private fun DocumentGrantEntity.toDomain() = DocumentGrant(
        id = id,
        uri = uri,
        name = name,
        kind = DocumentGrantKind.valueOf(kind),
        createdAt = createdAt,
        lastIndexedAt = lastIndexedAt
    )

    private fun DocumentIndexEntryEntity.toDomain() = DocumentIndexEntry(
        id = id,
        grantId = grantId,
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        text = text,
        summary = summary,
        indexedAt = indexedAt
    )

    private fun AgentTurn.toEntity(title: String) = AgentTurnEntity(
        id = id,
        threadId = threadId,
        ordinal = ordinal,
        title = title,
        goal = goal,
        status = status.name,
        planJson = plan?.let(PlanCodec::encode).orEmpty(),
        result = result,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ThreadMessage.toEntity() = ThreadMessageEntity(
        id = id,
        threadId = threadId,
        turnId = turnId,
        role = role.name,
        kind = kind.name,
        content = content,
        createdAt = createdAt
    )

    private fun ThreadAttachment.toEntity() = ThreadAttachmentEntity(
        id = id,
        threadId = threadId,
        turnId = turnId,
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        createdAt = createdAt
    )

    private fun DocumentGrant.toEntity() = DocumentGrantEntity(
        id = id,
        uri = uri,
        name = name,
        kind = kind.name,
        createdAt = createdAt,
        lastIndexedAt = lastIndexedAt
    )

    private fun DocumentIndexEntry.toEntity() = DocumentIndexEntryEntity(
        id = id,
        grantId = grantId,
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        text = text,
        summary = summary,
        indexedAt = indexedAt
    )
}