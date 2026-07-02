package com.junkfood.seal.desktop.data

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Minimal JSON-file-backed download history. No Room/SQLDelight yet — see the Windows port plan. */
object HistoryStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(System.getProperty("user.home"), ".seal/history.json")

    fun load(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        val entries =
            runCatching { json.decodeFromString<List<HistoryEntry>>(file.readText()) }
                .getOrDefault(emptyList())
        // Defensive de-dup: older builds seeded ids from the per-session task counter, so files
        // written by them can contain colliding ids. Keep the first occurrence of each id so the
        // history list never crashes with a duplicate key.
        return entries.distinctBy { it.id }
    }

    /**
     * Appends a new entry, assigning it a fresh history-wide unique id (max existing + 1) so it
     * can never collide with an entry from a previous session. The caller passes everything but
     * the id.
     */
    fun append(
        title: String,
        url: String,
        filePath: String,
    ): List<HistoryEntry> {
        val existing = load()
        val nextId = (existing.maxOfOrNull { it.id } ?: -1L) + 1L
        val updated = listOf(HistoryEntry(id = nextId, title = title, url = url, filePath = filePath)) + existing
        save(updated)
        return updated
    }

    fun remove(id: Long): List<HistoryEntry> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        return updated
    }

    fun clear(): List<HistoryEntry> {
        save(emptyList())
        return emptyList()
    }

    private fun save(entries: List<HistoryEntry>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(entries))
    }
}
