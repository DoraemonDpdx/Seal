package com.junkfood.seal.desktop.download

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Subset of yt-dlp's `-J` output needed by the format selector. */
data class VideoInfo(val title: String, val duration: Double?, val formats: List<FormatInfo>)

data class FormatInfo(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val fps: Double?,
    val vcodec: String?,
    val acodec: String?,
    val tbr: Double?,
    val fileSizeApprox: Long?,
    val formatNote: String?,
) {
    val hasVideo: Boolean
        get() = vcodec != null && vcodec != "none"

    val hasAudio: Boolean
        get() = acodec != null && acodec != "none"

    fun sizeText(): String? =
        fileSizeApprox?.let {
            when {
                it >= 1 shl 30 -> "%.2f GiB".format(it / (1 shl 30).toDouble())
                it >= 1 shl 20 -> "%.1f MiB".format(it / (1 shl 20).toDouble())
                else -> "%.0f KiB".format(it / 1024.0)
            }
        }
}

internal fun parseVideoInfo(root: JsonObject): VideoInfo {
    val formats =
        root["formats"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val formatId = obj.string("format_id") ?: return@mapNotNull null
            FormatInfo(
                formatId = formatId,
                ext = obj.string("ext").orEmpty(),
                resolution = obj.string("resolution").orEmpty(),
                fps = obj.double("fps"),
                vcodec = obj.string("vcodec"),
                acodec = obj.string("acodec"),
                tbr = obj.double("tbr"),
                fileSizeApprox = obj.long("filesize") ?: obj.long("filesize_approx"),
                formatNote = obj.string("format_note"),
            )
        }
    return VideoInfo(
        title = root.string("title").orEmpty(),
        duration = root.double("duration"),
        formats = formats,
    )
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
