package com.junkfood.seal.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.junkfood.seal.desktop.data.DesktopSettings
import com.junkfood.seal.desktop.data.HistoryStore
import com.junkfood.seal.desktop.data.SettingsStore

private sealed interface Screen {
    data object Home : Screen

    data object VideoList : Screen

    data object Settings : Screen
}

/** Root composable wiring the home, video list, and settings screens together. */
@Composable
fun SealApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var settings by remember { mutableStateOf(SettingsStore.load()) }
    var history by remember { mutableStateOf(HistoryStore.load()) }

    when (screen) {
        Screen.Home ->
            HomeScreen(
                settings = settings,
                onOpenVideoList = { screen = Screen.VideoList },
                onOpenSettings = { screen = Screen.Settings },
                onDownloadCompleted = { entry -> history = HistoryStore.append(entry) },
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
