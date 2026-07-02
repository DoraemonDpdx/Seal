package com.junkfood.seal.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.junkfood.seal.desktop.download.FormatInfo
import com.junkfood.seal.desktop.download.VideoInfo

/**
 * Format selector mirroring the Android app's FormatPage: pick a video-only format plus an
 * audio-only format (merged with `+`), or a single combined/audio format on its own.
 */
@Composable
fun FormatSelectorDialog(
    videoInfo: VideoInfo?,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (formatId: String) -> Unit,
) {
    var selectedVideo by remember { mutableStateOf<FormatInfo?>(null) }
    var selectedAudio by remember { mutableStateOf<FormatInfo?>(null) }

    // Build the `-f` string. A picked video-only stream must be merged with an audio track, or the
    // saved file is silent (the "mp4 no audio" bug). We append `+bestaudio` in that case and keep a
    // fallback to the bare video id for the rare stream that has no separate audio to merge.
    val formatId: String? =
        when {
            selectedVideo != null && selectedAudio != null ->
                "${selectedVideo!!.formatId}+${selectedAudio!!.formatId}"
            selectedVideo != null && selectedVideo!!.hasAudio -> selectedVideo!!.formatId
            selectedVideo != null ->
                "${selectedVideo!!.formatId}+bestaudio/${selectedVideo!!.formatId}"
            selectedAudio != null -> selectedAudio!!.formatId
            else -> null
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(640.dp),
        title = { Text(videoInfo?.title?.takeIf { it.isNotBlank() } ?: "Select formats") },
        text = {
            when {
                error != null ->
                    Text(error, color = MaterialTheme.colorScheme.error)

                videoInfo == null ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                "Fetching available formats…",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }

                else -> {
                    // yt-dlp lists formats worst-first; show best-first like the Android app.
                    val formats = videoInfo.formats.asReversed()
                    val videoFormats = formats.filter { it.hasVideo }
                    val audioFormats = formats.filter { it.hasAudio && !it.hasVideo }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(440.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (videoFormats.isNotEmpty()) {
                            item { SectionHeader("Video") }
                            items(videoFormats, key = { "v" + it.formatId }) { format ->
                                FormatCard(
                                    format = format,
                                    selected = selectedVideo?.formatId == format.formatId,
                                    onClick = {
                                        selectedVideo =
                                            if (selectedVideo?.formatId == format.formatId) null
                                            else format
                                        // A combined format already has audio — drop the extra track.
                                        if (selectedVideo?.hasAudio == true) selectedAudio = null
                                    },
                                )
                            }
                        }
                        if (audioFormats.isNotEmpty()) {
                            item { SectionHeader("Audio") }
                            items(audioFormats, key = { "a" + it.formatId }) { format ->
                                FormatCard(
                                    format = format,
                                    selected = selectedAudio?.formatId == format.formatId,
                                    onClick = {
                                        selectedAudio =
                                            if (selectedAudio?.formatId == format.formatId) null
                                            else format
                                        if (selectedVideo?.hasAudio == true) selectedVideo = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { formatId?.let(onConfirm) }, enabled = formatId != null) {
                Text("Download")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun FormatCard(format: FormatInfo, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors =
            if (selected) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title =
                    buildList {
                            if (format.hasVideo) add(format.resolution)
                            format.fps?.let { if (it > 0) add("${it.toInt()}fps") }
                            add(format.ext.uppercase())
                        }
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                Text(title, style = MaterialTheme.typography.titleSmall)
                val detail =
                    buildList {
                            format.formatNote?.takeIf { it.isNotBlank() }?.let(::add)
                            if (format.hasVideo) format.vcodec?.let(::add)
                            if (format.hasAudio) format.acodec?.let(::add)
                            format.tbr?.let { add("%.0f kbps".format(it)) }
                        }
                        .joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            format.sizeText()?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
