package com.agentpad.app.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.agentpad.app.domain.DocumentGrant
import com.agentpad.app.domain.DocumentGrantKind
import com.agentpad.app.domain.DocumentIndexEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

class DocumentExtractor(private val context: Context) {
    private val resolver = context.contentResolver

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun createFileGrant(uri: Uri): DocumentGrant {
        val metadata = metadataFor(uri)
        return DocumentGrant(
            uri = uri.toString(),
            name = metadata.name,
            kind = DocumentGrantKind.FILE
        )
    }

    fun createTreeGrant(uri: Uri): DocumentGrant {
        val document = DocumentFile.fromTreeUri(context, uri)
        return DocumentGrant(
            uri = uri.toString(),
            name = document?.name ?: "Authorized folder",
            kind = DocumentGrantKind.TREE
        )
    }

    fun indexGrant(grant: DocumentGrant): List<DocumentIndexEntry> {
        return when (grant.kind) {
            DocumentGrantKind.FILE -> listOfNotNull(indexUri(grant.id, Uri.parse(grant.uri)))
            DocumentGrantKind.TREE -> {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(grant.uri)) ?: return emptyList()
                collectDocuments(root)
                    .sortedByDescending { it.lastModified() }
                    .take(MAX_TREE_DOCUMENTS)
                    .mapNotNull { file -> indexDocumentFile(grant.id, file) }
            }
        }
    }

    private fun collectDocuments(root: DocumentFile): List<DocumentFile> {
        val output = mutableListOf<DocumentFile>()
        fun visit(node: DocumentFile, depth: Int) {
            if (output.size >= MAX_TREE_DOCUMENTS || depth > MAX_TREE_DEPTH) return
            node.listFiles().forEach { child ->
                when {
                    child.isDirectory -> visit(child, depth + 1)
                    child.isFile && isSupported(child.name.orEmpty(), child.type.orEmpty()) -> output += child
                }
            }
        }
        visit(root, 0)
        return output
    }

    private fun indexDocumentFile(grantId: String, file: DocumentFile): DocumentIndexEntry? {
        val uri = file.uri
        return indexUri(
            grantId = grantId,
            uri = uri,
            fallbackName = file.name,
            fallbackMimeType = file.type,
            fallbackSize = file.length().takeIf { it >= 0L },
            fallbackLastModified = file.lastModified().takeIf { it > 0L }
        )
    }

    private fun indexUri(
        grantId: String,
        uri: Uri,
        fallbackName: String? = null,
        fallbackMimeType: String? = null,
        fallbackSize: Long? = null,
        fallbackLastModified: Long? = null
    ): DocumentIndexEntry? {
        val metadata = metadataFor(uri, fallbackName, fallbackMimeType, fallbackSize, fallbackLastModified)
        if (!isSupported(metadata.name, metadata.mimeType)) return null
        if ((metadata.size ?: 0L) > MAX_SOURCE_BYTES) {
            return DocumentIndexEntry(
                grantId = grantId,
                uri = uri.toString(),
                name = metadata.name,
                mimeType = metadata.mimeType,
                size = metadata.size,
                lastModified = metadata.lastModified,
                text = "",
                summary = "Skipped because the file is larger than the current 8 MB indexing limit."
            )
        }
        val text = runCatching { extractText(uri, metadata.name, metadata.mimeType) }.getOrDefault("")
            .cleanForIndex()
            .take(MAX_INDEX_CHARS)
        val summary = if (text.isBlank()) {
            "No readable text layer was found."
        } else {
            text.take(420)
        }
        return DocumentIndexEntry(
            id = stableId(uri.toString()),
            grantId = grantId,
            uri = uri.toString(),
            name = metadata.name,
            mimeType = metadata.mimeType,
            size = metadata.size,
            lastModified = metadata.lastModified,
            text = text,
            summary = summary
        )
    }

    private fun extractText(uri: Uri, name: String, mimeType: String): String {
        val normalized = mimeType.lowercase(Locale.US)
        return when {
            name.endsWith(".docx", ignoreCase = true) ||
                normalized == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                resolver.openInputStream(uri)?.use(::readDocx).orEmpty()
            name.endsWith(".pdf", ignoreCase = true) || normalized == "application/pdf" ->
                resolver.openInputStream(uri)?.use(::readPdf).orEmpty()
            name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true) ||
                normalized == "text/html" ->
                readText(uri).stripTags()
            else -> readText(uri)
        }
    }

    private fun readText(uri: Uri): String {
        val bytes = readBytes(uri, MAX_SOURCE_BYTES)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readDocx(input: InputStream): String {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytesLimited(MAX_SOURCE_BYTES).toString(Charsets.UTF_8)
                    return xml
                        .replace("<w:tab/>", " ")
                        .replace("</w:p>", "\n")
                        .stripTags()
                        .decodeXmlEntities()
                }
            }
        }
        return ""
    }

    private fun readPdf(input: InputStream): String {
        return PDDocument.load(input).use { document ->
            PDFTextStripper().getText(document).orEmpty()
        }
    }

    private fun metadataFor(
        uri: Uri,
        fallbackName: String? = null,
        fallbackMimeType: String? = null,
        fallbackSize: Long? = null,
        fallbackLastModified: Long? = null
    ): DocumentMetadata {
        var name = fallbackName ?: "Document"
        var size = fallbackSize
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        return DocumentMetadata(
            name = name.take(240),
            mimeType = (resolver.getType(uri) ?: fallbackMimeType ?: mimeFromName(name)).lowercase(Locale.US),
            size = size,
            lastModified = fallbackLastModified
        )
    }

    private fun isSupported(name: String, mimeType: String): Boolean {
        val lower = name.lowercase(Locale.US)
        val normalized = mimeType.lowercase(Locale.US)
        return normalized.startsWith("text/") ||
            normalized in setOf(
                "application/json",
                "application/xml",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ) ||
            lower.endsWith(".txt") ||
            lower.endsWith(".md") ||
            lower.endsWith(".json") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm") ||
            lower.endsWith(".docx") ||
            lower.endsWith(".pdf")
    }

    private fun mimeFromName(name: String): String = when {
        name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        name.endsWith(".json", ignoreCase = true) -> "application/json"
        name.endsWith(".xml", ignoreCase = true) -> "application/xml"
        name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true) -> "text/html"
        else -> "text/plain"
    }

    private fun readBytes(uri: Uri, maxBytes: Long): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytesLimited(maxBytes) }
            ?: error("Unable to read authorized document")

    private fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Document is larger than the current indexing limit." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun String.stripTags(): String =
        replace(Regex("<[^>]+>"), " ")

    private fun String.decodeXmlEntities(): String =
        replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

    private fun String.cleanForIndex(): String =
        replace('\u0000', ' ')
            .replace(Regex("[\\t\\r ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private fun stableId(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray()).toString()

    private data class DocumentMetadata(
        val name: String,
        val mimeType: String,
        val size: Long?,
        val lastModified: Long?
    )

    private companion object {
        const val MAX_TREE_DOCUMENTS = 120
        const val MAX_TREE_DEPTH = 4
        const val MAX_SOURCE_BYTES = 8L * 1024L * 1024L
        const val MAX_INDEX_CHARS = 120_000
    }
}
