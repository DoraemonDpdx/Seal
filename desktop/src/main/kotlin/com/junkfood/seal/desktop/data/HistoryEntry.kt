package com.junkfood.seal.desktop.data

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val id: Long,
    val title: String,
    val url: String,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
)
