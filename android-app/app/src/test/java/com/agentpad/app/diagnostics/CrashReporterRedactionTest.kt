package com.agentpad.app.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterRedactionTest {
    @Test
    fun removesCommonCredentialForms() {
        val raw = """
            Authorization: Bearer secret.token-value
            api_key=sk-abcdefghijklmnopqrstuvwxyz
        """.trimIndent()

        val redacted = redactDiagnosticText(raw)

        assertFalse(redacted.contains("secret.token-value"))
        assertFalse(redacted.contains("sk-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(redacted.contains("***REDACTED***"))
    }
}
