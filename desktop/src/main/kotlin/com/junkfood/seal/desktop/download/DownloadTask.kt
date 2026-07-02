package com.junkfood.seal.desktop.download

/** A single download tracked by the desktop UI. Mirrors the shape of the Android app's Task. */
data class DownloadTask(val id: Long, val url: String, val state: DownloadState = DownloadState.FetchingInfo)

sealed interface DownloadState {
    data object FetchingInfo : DownloadState

    data class Running(
        val title: String? = null,
        val progress: Float = 0f,
        val progressText: String = "",
        val speedText: String = "",
    ) : DownloadState

    data class Completed(val title: String?, val filePath: String) : DownloadState

    data class Canceled(val title: String? = null) : DownloadState

    data class Error(val message: String) : DownloadState
}
