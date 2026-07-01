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
        return runCatching { json.decodeFromString<List<HistoryEntry>>(file.readText()) }
            .getOrDefault(emptyList())
    }

    fun append(entry: HistoryEntry): List<HistoryEntry> {
        val updated = listOf(entry) + load()
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
