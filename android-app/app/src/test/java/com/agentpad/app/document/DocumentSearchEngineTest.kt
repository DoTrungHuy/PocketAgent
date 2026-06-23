package com.agentpad.app.document

import com.agentpad.app.domain.DocumentIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSearchEngineTest {
    private val engine = DocumentSearchEngine()

    @Test
    fun detectsChineseDocumentSearchIntent() {
        assertTrue(engine.looksLikeDocumentSearch("\u5e2e\u6211\u627e\u4e00\u4e0b\u63d0\u5230\u62a5\u9500\u91d1\u989d\u7684\u6587\u6863"))
    }

    @Test
    fun ranksContentMatchesAboveUnrelatedFiles() {
        val results = engine.search(
            query = "\u5e2e\u6211\u627e\u63d0\u5230\u62a5\u9500\u91d1\u989d\u7684\u6587\u6863",
            entries = listOf(
                entry("notes.txt", "meeting notes without the target"),
                entry("invoice.md", "\u8fd9\u91cc\u5199\u4e86\u62a5\u9500\u91d1\u989d 1280 \u5143\u548c\u9879\u76ee\u540d\u79f0")
            )
        )

        assertEquals("invoice.md", results.first().entry.name)
        assertTrue(results.first().reason.contains("content"))
    }

    private fun entry(name: String, text: String) = DocumentIndexEntry(
        grantId = "grant",
        uri = "content://docs/$name",
        name = name,
        mimeType = "text/plain",
        size = text.length.toLong(),
        lastModified = 200,
        text = text,
        summary = text
    )
}
