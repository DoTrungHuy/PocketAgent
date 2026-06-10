package com.agentpad.app.provider

import com.agentpad.app.domain.MessageKind
import com.agentpad.app.domain.MessageRole
import com.agentpad.app.domain.ThreadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadContextPolicyTest {
    @Test
    fun requiresExplicitCompressionWhenMessageLimitIsExceeded() {
        val policy = ThreadContextPolicy(maxMessages = 2, maxCharacters = 10_000)
        val messages = (1..3).map { message("m$it", "消息 $it") }

        assertTrue(policy.needsCompression(messages))
    }

    @Test
    fun requiresExplicitCompressionWhenCharacterLimitIsExceeded() {
        val policy = ThreadContextPolicy(maxMessages = 60, maxCharacters = 5)

        assertTrue(policy.needsCompression(listOf(message("m1", "123456"))))
    }

    @Test
    fun latestCheckpointReplacesOnlyRequestContextNotOriginalHistory() {
        val policy = ThreadContextPolicy(maxMessages = 3, maxCharacters = 100)
        val original = listOf(
            message("before", "原始历史"),
            message(
                "checkpoint",
                "压缩摘要",
                role = MessageRole.SYSTEM,
                kind = MessageKind.CONTEXT_SUMMARY
            ),
            message("after", "检查点后的消息")
        )

        val request = policy.requestMessages(original)

        assertEquals(listOf("checkpoint", "after"), request.map { it.id })
        assertEquals(3, original.size)
        assertFalse(policy.needsCompression(original))
    }

    private fun message(
        id: String,
        content: String,
        role: MessageRole = MessageRole.USER,
        kind: MessageKind = MessageKind.GOAL
    ) = ThreadMessage(
        id = id,
        threadId = "thread-1",
        turnId = "turn-1",
        role = role,
        kind = kind,
        content = content,
        createdAt = 1L
    )
}
