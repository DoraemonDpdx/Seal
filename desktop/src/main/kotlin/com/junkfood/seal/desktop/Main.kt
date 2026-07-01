package com.junkfood.seal.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.junkfood.seal.desktop.theme.SealDesktopTheme
import com.junkfood.seal.desktop.ui.HomeScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Seal",
        state = WindowState(width = 480.dp, height = 720.dp),
    ) {
        SealDesktopTheme { HomeScreen() }
    }
}
