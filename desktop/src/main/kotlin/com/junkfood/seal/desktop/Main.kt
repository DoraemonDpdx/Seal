package com.junkfood.seal.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.junkfood.seal.desktop.theme.SealDesktopTheme
import com.junkfood.seal.desktop.ui.SealApp
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Seal",
        state =
            WindowState(
                size = DpSize(1100.dp, 760.dp),
                position = WindowPosition(Alignment.Center),
            ),
    ) {
        // Don't let the window shrink below a usable desktop layout.
        window.minimumSize = Dimension(720, 540)
        SealDesktopTheme { SealApp() }
    }
}
