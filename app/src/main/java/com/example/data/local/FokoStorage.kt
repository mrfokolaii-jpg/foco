package com.example.data.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.OutputStream

/**
 * Where FOKO keeps the user's files.
 *
 * The user can point FOKO at any folder on the phone (internal storage, Downloads, SD card)
 * through the Android folder picker; that choice is remembered and used for offline models,
 * downloads and the knowledge cache. When no folder is picked, the app's own FOKO folder is used.
 */
class FokoStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- chosen folder --

    fun pickedTreeUri(): String? = prefs.getString(KEY_TREE, null)

    fun rememberPickedFolder(uri: Uri) {
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun clearPickedFolder() {
        prefs.edit().remove(KEY_TREE).apply()
    }

    fun hasPickedFolder(): Boolean = !pickedTreeUri().isNullOrBlank()

    /** Plain-language description of where files are written right now. */
    fun locationLabel(): String {
        val raw = pickedTreeUri()
        if (raw.isNullOrBlank()) return "حافظهٔ خودِ برنامه (پوشهٔ FOKO)"
        return try {
            val name = DocumentsContract.getTreeDocumentId(Uri.parse(raw)).substringAfterLast(':')
            if (name.isBlank()) "پوشهٔ انتخابی تو" else "پوشهٔ انتخابی تو: $name"
        } catch (_: Exception) {
            "پوشهٔ انتخابی تو"
        }
    }

    // ------------------------------------------------------------------ size limits --

    /** Largest single download FOKO is allowed to make. */
    var maxDownloadBytes: Long
        get() = prefs.getLong(KEY_MAX_DOWNLOAD, DEFAULT_MAX_DOWNLOAD)
        set(value) = prefs.edit()
            .putLong(KEY_MAX_DOWNLOAD, value.coerceIn(1L * 1024 * 1024, 2L * 1024 * 1024 * 1024))
            .apply()

    // ------------------------------------------------------------------- read/write --

    /** Opens [fileName] for writing in the chosen folder (or the app folder as a fallback). */
    fun openForWrite(fileName: String): OutputStream? {
        val safeName = sanitize(fileName)
        val raw = pickedTreeUri()
        if (raw.isNullOrBlank()) {
            return try {
                File(appDir(), safeName).outputStream()
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val tree = Uri.parse(raw)
            val resolver = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val existing = findChild(rootDoc, safeName)
            val target = existing
                ?: DocumentsContract.createDocument(resolver, rootDoc, "application/octet-stream", safeName)
                ?: return null
            resolver.openOutputStream(target)
        } catch (_: Exception) {
            null
        }
    }

    /** Writes [text] as [fileName]. Returns a label to show the user, or null on failure. */
    fun writeText(fileName: String, text: String): String? {
        return try {
            openForWrite(fileName)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: return null
            if (hasPickedFolder()) fileName else File(appDir(), sanitize(fileName)).absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** Reads [fileName] back, wherever it lives. */
    fun readText(fileName: String): String? {
        val safeName = sanitize(fileName)
        val raw = pickedTreeUri()
        if (raw.isNullOrBlank()) {
            return try {
                val f = File(appDir(), safeName)
                if (f.exists()) f.readText() else null
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val tree = Uri.parse(raw)
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val target = findChild(rootDoc, safeName) ?: return null
            context.contentResolver.openInputStream(target)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** Names of the files FOKO is holding, with their sizes. */
    fun listFiles(): List<Pair<String, Long>> {
        val raw = pickedTreeUri()
        if (raw.isNullOrBlank()) {
            return try {
                appDir().listFiles()?.filter { it.isFile }
                    ?.map { it.name to it.length() }
                    ?.sortedBy { it.first }
                    .orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }
        return try {
            val tree = Uri.parse(raw)
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val out = mutableListOf<Pair<String, Long>>()
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                    out.add(name to size)
                }
            }
            out.sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun totalUsedBytes(): Long = listFiles().sumOf { it.second }

    /** Deletes everything FOKO put in the chosen folder. */
    fun clearAll(): Int {
        val raw = pickedTreeUri()
        if (raw.isNullOrBlank()) {
            var removed = 0
            try {
                appDir().listFiles()?.forEach { if (it.isFile && it.delete()) removed++ }
            } catch (_: Exception) {
                // ignore
            }
            return removed
        }
        var removed = 0
        try {
            val tree = Uri.parse(raw)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val uris = mutableListOf<Uri>()
            context.contentResolver.query(
                children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    uris.add(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                }
            }
            uris.forEach { if (DocumentsContract.deleteDocument(context.contentResolver, it)) removed++ }
        } catch (_: Exception) {
            // ignore
        }
        return removed
    }

    // ---------------------------------------------------------------------- helpers --

    private fun appDir(): File {
        val publicDir = File(Environment.getExternalStorageDirectory(), "FOKO")
        return try {
            if (!publicDir.exists()) publicDir.mkdirs()
            if (publicDir.canWrite()) publicDir
            else File(context.getExternalFilesDir(null), "FOKO").apply { mkdirs() }
        } catch (_: Exception) {
            File(context.getExternalFilesDir(null), "FOKO").apply { mkdirs() }
        }
    }

    private fun findChild(parentDoc: Uri, name: String): Uri? {
        return try {
            val tree = Uri.parse(pickedTreeUri() ?: return null)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getDocumentId(parentDoc)
            )
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "foko_file" }

    companion object {
        private const val PREFS = "foko_storage"
        private const val KEY_TREE = "save_tree_uri"
        private const val KEY_MAX_DOWNLOAD = "max_download_bytes"

        /** 50 MB — enough for documents and small media without eating the phone. */
        const val DEFAULT_MAX_DOWNLOAD = 50L * 1024 * 1024

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> "%.2fGB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "${bytes / 1024}KB"
            else -> "${bytes}B"
        }
    }
}
