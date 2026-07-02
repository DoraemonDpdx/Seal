package com.junkfood.seal.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.junkfood.seal.desktop.data.DesktopSettings
import com.junkfood.seal.desktop.data.HistoryEntry
import com.junkfood.seal.desktop.data.HistoryStore
import com.junkfood.seal.desktop.data.SettingsStore
import com.junkfood.seal.desktop.download.DownloadState
import com.junkfood.seal.desktop.download.DownloadTask
import com.junkfood.seal.desktop.download.YtDlpDownloader
import java.io.File
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Home : Screen

    data object VideoList : Screen

    data object Settings : Screen
}

/**
 * Root composable wiring the home, video list, and settings screens together.
 *
 * The download task list and [YtDlpDownloader] live here (not in [HomeScreen]) and the screen
 * switch below uses [Crossfade] instead of a raw `when`, so navigating to Settings/Downloads and
 * back doesn't wipe an in-progress download or cause an abrupt full-tree rebuild.
 */
@Composable
fun SealApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var settings by remember { mutableStateOf(SettingsStore.load()) }
    var history by remember { mutableStateOf(HistoryStore.load()) }

    val downloader = remember { YtDlpDownloader() }
    val tasks = remember { mutableStateListOf<DownloadTask>() }
    val scope = rememberCoroutineScope()
    var nextId by remember { mutableStateOf(0L) }

    fun startDownload(url: String) {
        val taskId = nextId++
        tasks.add(0, DownloadTask(id = taskId, url = url))
        scope.launch {
            val outputDir = File(settings.downloadDirectory)
            downloader.download(url, outputDir).collect { state ->
                val index = tasks.indexOfFirst { it.id == taskId }
                if (index >= 0) tasks[index] = tasks[index].copy(state = state)
                if (state is DownloadState.Completed) {
                    history =
                        HistoryStore.append(
                            HistoryEntry(
                                id = taskId,
                                title = state.title ?: url,
                                url = url,
                                filePath = state.filePath,
                            )
                        )
                }
            }
        }
    }

    Crossfade(targetState = screen) { current ->
        when (current) {
            Screen.Home ->
                HomeScreen(
                    settings = settings,
                    tasks = tasks,
                    onStartDownload = ::startDownload,
                    onOpenVideoList = { screen = Screen.VideoList },
                    onOpenSettings = { screen = Screen.Settings },
                )

            Screen.VideoList ->
                VideoListScreen(
                    entries = history,
                    onBack = { screen = Screen.Home },
                    onDelete = { id -> history = HistoryStore.remove(id) },
                )

            Screen.Settings ->
                SettingsScreen(
                    settings = settings,
                    onBack = { screen = Screen.Home },
                    onSettingsChange = { updated: DesktopSettings ->
                        settings = updated
                        SettingsStore.save(updated)
                    },
                )
        }
    }
}
