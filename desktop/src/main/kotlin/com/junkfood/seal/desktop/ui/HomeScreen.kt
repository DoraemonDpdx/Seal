package com.junkfood.seal.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.junkfood.seal.desktop.download.DownloadState
import com.junkfood.seal.desktop.download.DownloadTask
import com.junkfood.seal.desktop.download.YtDlpDownloader
import java.io.File
import kotlinx.coroutines.launch

private val defaultOutputDir = File(System.getProperty("user.home"), "Downloads/Seal")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val downloader = remember { YtDlpDownloader() }
    val scope = rememberCoroutineScope()
    val tasks = remember { mutableStateListOf<DownloadTask>() }
    var showInputDialog by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf(0L) }

    if (showInputDialog) {
        InputUrlDialog(
            onDismiss = { showInputDialog = false },
            onConfirm = { url ->
                showInputDialog = false
                val taskId = nextId++
                tasks.add(0, DownloadTask(id = taskId, url = url))
                scope.launch {
                    downloader.download(url, defaultOutputDir).collect { state ->
                        val index = tasks.indexOfFirst { it.id == taskId }
                        if (index >= 0) tasks[index] = tasks[index].copy(state = state)
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seal") },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Menu, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInputDialog = true },
                text = { Text("Paste link") },
                icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
            )
        },
    ) { padding: PaddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tasks.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    items(tasks, key = { it.id }) { task -> TaskCard(task) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.FileDownload,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Paste a video link to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskCard(task: DownloadTask) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title =
                when (val state = task.state) {
                    is DownloadState.Running -> state.title
                    is DownloadState.Completed -> state.title
                    else -> null
                } ?: task.url
            Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 2)

            when (val state = task.state) {
                is DownloadState.FetchingInfo ->
                    Text(
                        text = "Fetching info…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                is DownloadState.Running -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        text = listOf(state.progressText, state.speedText)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                is DownloadState.Completed ->
                    Text(
                        text = "Saved to ${state.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                is DownloadState.Error ->
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
            }
        }
    }
}
