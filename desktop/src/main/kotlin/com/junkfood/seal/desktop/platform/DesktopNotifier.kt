package com.junkfood.seal.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fires a native desktop notification when a download finishes. Uses whatever the host OS provides
 * — a PowerShell balloon tip on Windows, `osascript` on macOS, and the freedesktop notification
 * stack (gdbus / notify-send) on Linux. Best-effort: any failure is swallowed so a missing
 * notifier never affects the download itself.
 */
object DesktopNotifier {
    private val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
    private val isMac = System.getProperty("os.name")?.lowercase()?.contains("mac") == true

    suspend fun notify(title: String, message: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    isWindows -> notifyWindows(title, message)
                    isMac -> notifyMac(title, message)
                    else -> notifyLinux(title, message)
                }
            }
        }
    }

    private fun notifyWindows(title: String, message: String) {
        // Show a tray balloon via a short PowerShell script, passed Base64-encoded so quoting in the
        // title/message can't break the command line.
        val script =
            """
            [Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms") | Out-Null;
            ${'$'}n = New-Object System.Windows.Forms.NotifyIcon;
            ${'$'}n.Icon = [System.Drawing.SystemIcons]::Information;
            ${'$'}n.Visible = ${'$'}true;
            ${'$'}n.ShowBalloonTip(8000, ${quote(title)}, ${quote(message)}, [System.Windows.Forms.ToolTipIcon]::None);
            Start-Sleep -Milliseconds 8000;
            ${'$'}n.Dispose();
            """
                .trimIndent()
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        ProcessBuilder("powershell", "-NoProfile", "-EncodedCommand", encoded).start()
    }

    private fun notifyMac(title: String, message: String) {
        val script = "display notification ${quote(message)} with title ${quote(title)}"
        ProcessBuilder("osascript", "-e", script).start()
    }

    private fun notifyLinux(title: String, message: String) {
        // Try notify-send first, then a couple of common fallbacks.
        val candidates =
            listOf(
                arrayOf("notify-send", "Seal", "$title\n$message"),
                arrayOf("kdialog", "--title", "Seal", "--passivepopup", "$title\n$message", "5"),
                arrayOf("zenity", "--notification", "--text=$title\n$message"),
            )
        for (command in candidates) {
            val ok = runCatching { ProcessBuilder(*command).start().waitFor() == 0 }.getOrDefault(false)
            if (ok) return
        }
    }

    /** Wraps a string in double quotes for use in a PowerShell/AppleScript string literal. */
    private fun quote(text: String): String = "\"" + text.replace("\"", "'") + "\""
}
