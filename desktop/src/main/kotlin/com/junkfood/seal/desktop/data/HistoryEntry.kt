package com.junkfood.seal.desktop.data

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    /**
     * Stable, history-wide unique id used as the list key. This is deliberately NOT the in-session
     * download task id: task ids restart at 0 every launch, so reusing them here would make
     * persisted entries collide (the "key N already defined" crash). See [HistoryStore.append].
     */
    val id: Long,
    val title: String,
    val url: String,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
)
