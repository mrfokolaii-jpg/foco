package com.example.data.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MemoryEntry(
    val id: String,
    val kind: String,
    val title: String,
    val content: String,
    val createdAt: Long
)

/**
 * Tiny JSON-file backed long term memory. FOKO writes notes, harvested knowledge and
 * tool outcomes here so later conversations (and the floating widget) can recall them.
 */
object FokoMemory {

    private const val FILE_NAME = "foko_memory.json"
    private const val MAX_ENTRIES = 500

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    suspend fun add(context: Context, kind: String, title: String, content: String): MemoryEntry =
        withContext(Dispatchers.IO) {
            val entry = MemoryEntry(
                id = java.util.UUID.randomUUID().toString(),
                kind = kind,
                title = title.take(120),
                content = content.take(8000),
                createdAt = System.currentTimeMillis()
            )
            val all = readAll(context).toMutableList()
            all.add(0, entry)
            writeAll(context, all.take(MAX_ENTRIES))
            entry
        }

    fun readAll(context: Context): List<MemoryEntry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<MemoryEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    MemoryEntry(
                        id = o.optString("id"),
                        kind = o.optString("kind"),
                        title = o.optString("title"),
                        content = o.optString("content"),
                        createdAt = o.optLong("createdAt")
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun search(context: Context, query: String, limit: Int = 5): List<MemoryEntry> {
        val all = readAll(context)
        if (query.isBlank()) return all.take(limit)
        val terms = query.lowercase().split(' ', '،', ',').filter { it.length > 2 }
        return all.map { entry ->
            val haystack = (entry.title + " " + entry.content).lowercase()
            val score = terms.count { haystack.contains(it) }
            entry to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Compact context block injected into the agent system prompt. */
    fun contextBlock(context: Context, query: String): String {
        val relevant = search(context, query, 4)
        if (relevant.isEmpty()) return ""
        return buildString {
            appendLine("حافظه بلندمدت فوکو (آنچه قبلاً یاد گرفته یا ذخیره کرده):")
            relevant.forEach { entry ->
                appendLine("- [${entry.kind}] ${entry.title}: ${entry.content.take(300)}")
            }
        }.trim()
    }

    private fun writeAll(context: Context, entries: List<MemoryEntry>) {
        try {
            val arr = JSONArray()
            entries.forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("kind", entry.kind)
                        put("title", entry.title)
                        put("content", entry.content)
                        put("createdAt", entry.createdAt)
                    }
                )
            }
            file(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
