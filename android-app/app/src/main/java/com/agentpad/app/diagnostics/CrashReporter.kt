package com.agentpad.app.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import com.agentpad.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

class CrashReporter(private val context: Context) {
    private val reportFile = context.filesDir.resolve("diagnostics/last_crash.json")
    private val state = context.getSharedPreferences("pocketagent_diagnostics", Context.MODE_PRIVATE)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        if (previousHandler != null) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrashReport(): Boolean = reportFile.isFile

    fun recordUiContext(section: String, widthDp: Int) {
        state.edit()
            .putString(KEY_LAST_SECTION, section)
            .putInt(KEY_LAST_WIDTH_DP, widthDp)
            .apply()
    }

    fun updateAuditSummaries(summaries: List<String>) {
        state.edit()
            .putString(KEY_AUDIT_SUMMARIES, JSONArray(summaries.take(20)).toString())
            .apply()
    }

    fun export(uri: Uri) {
        val payload = buildDiagnosticPayload()
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(payload.toString(2))
        } ?: error("无法创建诊断文件")
    }

    fun clearCrashReport() {
        reportFile.delete()
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        reportFile.parentFile?.mkdirs()
        val stack = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString("\n") { frame -> "at $frame" }
        val report = basePayload()
            .put("thread", thread.name.take(100))
            .put("exception", throwable.javaClass.name)
            .put("message", "Exception details omitted to protect task and model content.")
            .put("stackTrace", redactDiagnosticText(stack).take(MAX_STACK_CHARS))
            .put("createdAt", System.currentTimeMillis())
        reportFile.writeText(report.toString(), Charsets.UTF_8)
    }

    private fun buildDiagnosticPayload(): JSONObject {
        val payload = basePayload()
            .put("exportedAt", System.currentTimeMillis())
            .put("auditSummaries", readAuditSummaries())
        if (reportFile.isFile) {
            val crash = runCatching { JSONObject(reportFile.readText(Charsets.UTF_8)) }.getOrNull()
            payload.put("lastCrash", crash ?: JSONObject.NULL)
        } else {
            payload.put("lastCrash", JSONObject.NULL)
        }
        return payload
    }

    private fun basePayload(): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("gitSha", BuildConfig.GIT_SHA)
        .put("buildChannel", BuildConfig.BUILD_CHANNEL)
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(160))
        .put("androidApi", Build.VERSION.SDK_INT)
        .put("lastSection", state.getString(KEY_LAST_SECTION, "unknown"))
        .put("lastWidthDp", state.getInt(KEY_LAST_WIDTH_DP, 0))

    private fun readAuditSummaries(): JSONArray {
        val encoded = state.getString(KEY_AUDIT_SUMMARIES, null) ?: return JSONArray()
        return runCatching { JSONArray(encoded) }.getOrDefault(JSONArray())
    }

    private companion object {
        const val KEY_LAST_SECTION = "last_section"
        const val KEY_LAST_WIDTH_DP = "last_width_dp"
        const val KEY_AUDIT_SUMMARIES = "audit_summaries"
        const val MAX_STACK_CHARS = 24_000
        const val MAX_STACK_FRAMES = 120
    }
}

internal fun redactDiagnosticText(value: String): String = value
    .replace(Regex("""sk-[A-Za-z0-9_-]{8,}"""), "***REDACTED***")
    .replace(
        Regex("""Bearer\s+[A-Za-z0-9._-]+""", RegexOption.IGNORE_CASE),
        "Bearer ***REDACTED***"
    )
    .replace(
        Regex("""(?i)(authorization|api[_ -]?key)\s*[:=]\s*\S+"""),
        "$1=***REDACTED***"
    )
