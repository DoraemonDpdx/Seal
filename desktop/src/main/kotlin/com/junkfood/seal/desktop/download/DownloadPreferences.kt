package com.junkfood.seal.desktop.download

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadType {
    Video,
    Audio,
}

/**
 * Per-download options, mirroring the Android app's DownloadDialog. Defaults are stored in
 * [com.junkfood.seal.desktop.data.DesktopSettings] and can be tweaked per download in the
 * download dialog.
 */
@Serializable
data class DownloadPreferences(
    val downloadType: DownloadType = DownloadType.Video,
    /** Max video height in pixels; 0 means best available. */
    val videoQuality: Int = 0,
    /** Preferred container for video downloads: "" (auto), "mp4", or "webm". */
    val videoFormat: String = "",
    /** Preferred codec for audio downloads: "" (auto), "mp3", "m4a", "opus", or "flac". */
    val audioFormat: String = "",
    val downloadPlaylist: Boolean = false,
    val downloadSubtitles: Boolean = false,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = true,
    val sponsorBlock: Boolean = false,
    val concurrentFragments: Int = 8,
    /** Extra command-line arguments appended verbatim to the yt-dlp invocation. */
    val customArgs: String = "",
    /** Explicit `-f` format id chosen in the format selector; overrides quality/format prefs. */
    val formatId: String? = null,
) {
    companion object {
        val videoQualityOptions =
            listOf(0 to "Best quality", 2160 to "2160p", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p")
        val videoFormatOptions = listOf("" to "Auto", "mp4" to "MP4", "webm" to "WebM")
        val audioFormatOptions =
            listOf("" to "Auto", "mp3" to "MP3", "m4a" to "M4A", "opus" to "OPUS", "flac" to "FLAC")
    }
}
