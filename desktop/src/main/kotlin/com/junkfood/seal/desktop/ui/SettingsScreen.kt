package com.junkfood.seal.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.junkfood.seal.desktop.data.DesktopSettings
import com.junkfood.seal.desktop.download.DownloadPreferences
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import kotlinx.coroutines.launch

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

@Composable
private fun SectionTitle(text: String, topPadding: Int = 24) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = topPadding.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: DesktopSettings,
    ytDlpDirectory: String?,
    onBack: () -> Unit,
    onSettingsChange: (DesktopSettings) -> Unit,
    fetchYtDlpVersion: suspend () -> String?,
    updateYtDlp: suspend () -> String,
) {
    var downloadDirectory by remember(settings) { mutableStateOf(settings.downloadDirectory) }
    var prefs by remember(settings) { mutableStateOf(settings.downloadPreferences) }
    var notifyOnComplete by remember(settings) { mutableStateOf(settings.notifyOnComplete) }

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
            SectionTitle("Download directory", topPadding = 0)
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

            SectionTitle("Download speed")
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

            SectionTitle("Network")
            DropdownSelector(
                label = "Cookies from browser",
                options = DownloadPreferences.cookiesBrowserOptions,
                selected = prefs.cookiesFromBrowser,
                onSelect = { prefs = prefs.copy(cookiesFromBrowser = it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                text =
                    "Extracts cookies from the selected browser so yt-dlp can download " +
                        "age-restricted or members-only content you're logged into.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = prefs.proxyUrl,
                onValueChange = { prefs = prefs.copy(proxyUrl = it) },
                label = { Text("Proxy") },
                placeholder = { Text("http://127.0.0.1:7890 or socks5://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = prefs.rateLimit,
                onValueChange = { prefs = prefs.copy(rateLimit = it) },
                label = { Text("Download rate limit") },
                placeholder = { Text("e.g. 1M, 500K — empty for unlimited") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            SwitchRow(
                title = "Force IPv4",
                description = "Use IPv4 only — a common fix for stalled or throttled downloads.",
                checked = prefs.forceIpv4,
                onCheckedChange = { prefs = prefs.copy(forceIpv4 = it) },
            )

            SectionTitle("Notifications")
            SwitchRow(
                title = "Notify when a download finishes",
                description = "Show a native desktop notification on completion or failure.",
                checked = notifyOnComplete,
                onCheckedChange = { notifyOnComplete = it },
            )

            Button(
                onClick = {
                    onSettingsChange(
                        settings.copy(
                            downloadDirectory = downloadDirectory,
                            downloadPreferences = prefs,
                            notifyOnComplete = notifyOnComplete,
                        )
                    )
                },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Save")
            }

            SectionTitle("yt-dlp", topPadding = 32)
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
            YtDlpVersionCard(fetchYtDlpVersion = fetchYtDlpVersion, updateYtDlp = updateYtDlp)

            SectionTitle("About", topPadding = 32)
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

@Composable
private fun YtDlpVersionCard(
    fetchYtDlpVersion: suspend () -> String?,
    updateYtDlp: suspend () -> String,
) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<String?>(null) }
    var loadingVersion by remember { mutableStateOf(true) }
    var updating by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        version = runCatching { fetchYtDlpVersion() }.getOrNull()
        loadingVersion = false
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    when {
                        loadingVersion -> "Checking version…"
                        version != null -> "Version: $version"
                        else -> "Version unavailable"
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            updateResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        OutlinedButton(
            onClick = {
                updating = true
                updateResult = null
                scope.launch {
                    updateResult =
                        runCatching { updateYtDlp() }
                            .getOrElse { e -> e.message ?: "Update failed" }
                    version = runCatching { fetchYtDlpVersion() }.getOrNull()
                    updating = false
                }
            },
            enabled = !updating,
        ) {
            if (updating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Update")
            }
        }
    }
}
