package com.junkfood.seal.desktop.platform

/**
 * Adds the directory containing the bundled yt-dlp/ffmpeg binaries to the user's PATH on Windows,
 * so the tools Seal installs are also usable from any terminal.
 *
 * MSI packages produced by Compose Desktop (jpackage) can't run custom actions, so this runs on
 * first launch instead of inside the installer. It edits only the per-user PATH (HKCU), never the
 * system one, via PowerShell's [Environment] API — unlike `setx`, that doesn't truncate long PATH
 * values.
 */
object PathIntegration {

    private val isWindows: Boolean
        get() = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    /** Returns true if [directory] was added (or already present); false if unsupported/failed. */
    fun ensureOnUserPath(directory: String): Boolean {
        if (!isWindows) return false
        val script =
            """
            ${'$'}dir = '${directory.replace("'", "''")}'
            ${'$'}path = [Environment]::GetEnvironmentVariable('Path', 'User')
            if (${'$'}null -eq ${'$'}path) { ${'$'}path = '' }
            ${'$'}parts = ${'$'}path -split ';' | Where-Object { ${'$'}_ -ne '' }
            if (-not (${'$'}parts -contains ${'$'}dir)) {
                [Environment]::SetEnvironmentVariable('Path', ((${'$'}parts + ${'$'}dir) -join ';'), 'User')
            }
            """
                .trimIndent()
        return runCatching {
                val process =
                    ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                        .redirectErrorStream(true)
                        .start()
                process.waitFor() == 0
            }
            .getOrDefault(false)
    }
}
