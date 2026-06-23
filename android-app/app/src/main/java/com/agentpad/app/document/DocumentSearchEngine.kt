package com.agentpad.app.document

import com.agentpad.app.domain.DocumentIndexEntry
import com.agentpad.app.domain.DocumentSearchResult
import java.util.Locale
import kotlin.math.min

class DocumentSearchEngine {
    fun looksLikeDocumentSearch(prompt: String): Boolean {
        val lower = prompt.lowercase(Locale.US)
        val hasSearchVerb = listOf(
            "find",
            "search",
            "locate",
            "look for",
            "\u627e",
            "\u641c\u7d22",
            "\u67e5\u627e",
            "\u641c\u4e00\u4e0b",
            "\u627e\u4e00\u4e0b"
        ).any { lower.contains(it) }
        val hasDocumentTarget = listOf(
            "file",
            "document",
            "doc",
            "pdf",
            "folder",
            "\u6587\u6863",
            "\u6587\u4ef6",
            "\u8d44\u6599",
            "\u5408\u540c",
            "\u53d1\u7968",
            "\u8d26\u5355",
            "\u622a\u56fe",
            "\u56fe\u7247",
            "\u8868\u683c"
        ).any { lower.contains(it) }
        val contentClue = listOf(
            "mentions",
            "contains",
            "about",
            "\u63d0\u5230",
            "\u5185\u5bb9",
            "\u5305\u542b",
            "\u5199\u4e86",
            "\u91cc\u9762",
            "\u5173\u4e8e"
        ).any { lower.contains(it) }
        return hasSearchVerb && (hasDocumentTarget || contentClue)
    }

    fun search(query: String, entries: List<DocumentIndexEntry>, limit: Int = 8): List<DocumentSearchResult> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val scored = score(entry, tokens)
            if (scored.score <= 0.0) {
                null
            } else {
                DocumentSearchResult(
                    entry = entry,
                    score = scored.score,
                    reason = scored.reason,
                    snippet = snippet(entry, tokens)
                )
            }
        }
            .sortedWith(compareByDescending<DocumentSearchResult> { it.score }.thenByDescending { it.entry.lastModified ?: 0L })
            .take(limit)
    }

    private fun score(entry: DocumentIndexEntry, tokens: List<String>): ScoredMatch {
        val name = entry.name.lowercase(Locale.US)
        val text = entry.text.lowercase(Locale.US)
        val summary = entry.summary.lowercase(Locale.US)
        var score = 0.0
        val reasons = mutableListOf<String>()
        tokens.forEach { token ->
            if (name.contains(token)) {
                score += 12.0
                reasons += "file name matches \"$token\""
            }
            val textHits = hitCount(text, token)
            if (textHits > 0) {
                score += min(24, textHits * 4).toDouble()
                reasons += "content mentions \"$token\""
            }
            if (summary.contains(token)) {
                score += 4.0
            }
        }
        if ((entry.lastModified ?: 0L) > 0L) {
            score += 1.5
        }
        return ScoredMatch(score, reasons.distinct().take(3).joinToString("; ").ifBlank { "metadata match" })
    }

    private fun snippet(entry: DocumentIndexEntry, tokens: List<String>): String {
        val text = entry.text.ifBlank { entry.summary }
        if (text.isBlank()) return "No readable text snippet is available."
        val lower = text.lowercase(Locale.US)
        val firstHit = tokens.mapNotNull { token ->
            lower.indexOf(token).takeIf { it >= 0 }
        }.minOrNull() ?: 0
        val start = (firstHit - 90).coerceAtLeast(0)
        val end = (firstHit + 260).coerceAtMost(text.length)
        return text.substring(start, end).replace(Regex("\\s+"), " ").trim()
    }

    private fun hitCount(text: String, token: String): Int {
        if (token.isBlank()) return 0
        var count = 0
        var index = text.indexOf(token)
        while (index >= 0 && count < 20) {
            count += 1
            index = text.indexOf(token, index + token.length)
        }
        return count
    }

    fun tokens(value: String): List<String> {
        val lower = value.lowercase(Locale.US)
        val english = lower.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        val chinese = Regex("[\\u4E00-\\u9FFF]+").findAll(value).flatMap { match ->
            val text = match.value
            buildList {
                if (text.length >= 2) add(text)
                for (index in 0 until text.length - 1) {
                    add(text.substring(index, index + 2))
                }
            }
        }
        val stopWords = setOf(
            "help",
            "find",
            "search",
            "file",
            "document",
            "please",
            "\u5e2e\u6211",
            "\u627e\u51fa",
            "\u67e5\u627e",
            "\u641c\u7d22",
            "\u6587\u6863",
            "\u6587\u4ef6",
            "\u8d44\u6599",
            "\u54ea\u4e2a",
            "\u54ea\u91cc"
        )
        return (english + chinese)
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
            .take(24)
    }

    private data class ScoredMatch(val score: Double, val reason: String)
}
