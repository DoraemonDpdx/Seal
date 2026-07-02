package com.junkfood.seal.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextDecoration
import com.junkfood.seal.desktop.data.DesktopSettings
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

private const val REPO_URL = "https://github.com/DoraemonDpdx/Seal"
private const val UPSTREAM_URL = "https://github.com/JunkFood02/Seal"

@Composable
private fun LinkText(url: String) {
    Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.padding(top = 4.dp).clickable {
                runCatching { Desktop.getDesktop().browse(URI(url)) }
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: DesktopSettings,
    ytDlpDirectory: String?,
    onBack: () -> Unit,
    onSettingsChange: (DesktopSettings) -> Unit,
) {
    var downloadDirectory by remember(settings) { mutableStateOf(settings.downloadDirectory) }
    var prefs by remember(settings) { mutableStateOf(settings.downloadPreferences) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding: PaddingValues ->
        // Cap the content width and center it, so settings don't stretch edge-to-edge on a
        // desktop-sized window.
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .wrapContentWidth()
                    .widthIn(max = 720.dp)
        ) {
            Text(text = "Download directory", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = downloadDirectory,
                    onValueChange = { downloadDirectory = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        // Compose Desktop dispatches clicks on the AWT event thread, where a
                        // modal Swing chooser is safe to show.
                        val chooser =
                            JFileChooser(File(downloadDirectory).takeIf { it.isDirectory }).apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Choose download directory"
                            }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            downloadDirectory = chooser.selectedFile.path
                        }
                    }
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Text(text = "Browse", modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text(
                text = "Download speed",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "Concurrent fragments: ${prefs.concurrentFragments}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Slider(
                value = prefs.concurrentFragments.toFloat(),
                onValueChange = { prefs = prefs.copy(concurrentFragments = it.toInt().coerceIn(1, 16)) },
                valueRange = 1f..16f,
                steps = 14,
            )
            Text(
                text =
                    "Downloads video fragments in parallel — higher is faster on fast " +
                        "connections, at the cost of more CPU and connections.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "yt-dlp",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text =
                    ytDlpDirectory?.let { "Using bundled binaries in $it" }
                        ?: "Using yt-dlp from PATH",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (settings.addedToPath) {
                Text(
                    text = "Added to your PATH — yt-dlp is available in any terminal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    onSettingsChange(
                        settings.copy(
                            downloadDirectory = downloadDirectory,
                            downloadPreferences = prefs,
                        )
                    )
                },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Save")
            }

            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = "Seal for desktop — built by DoraemonDpdx using Claude Code, powered by Fable 5.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            LinkText(url = REPO_URL)
            Text(
                text = "Based on Seal for Android by JunkFood02:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            LinkText(url = UPSTREAM_URL)
        }
    }
}
