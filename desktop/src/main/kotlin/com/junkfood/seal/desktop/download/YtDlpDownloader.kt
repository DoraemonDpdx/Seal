package com.junkfood.seal.desktop.download

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the `yt-dlp` CLI as a subprocess and turns its `--progress-template` JSON output into
 * [DownloadState] updates.
 *
 * Binary resolution: when packaged, the installer bundles a yt-dlp binary via Compose Desktop's
 * app-resources mechanism (see `desktop/build.gradle.kts`'s `downloadYtDlp` task), and that
 * binary is preferred. When running unpackaged (e.g. `./gradlew :desktop:run`), no bundled
 * resources dir exists, so this falls back to `yt-dlp` on PATH.
 */
class YtDlpDownloader(binaryPath: String? = null) {

    private val resolvedBinaryPath = binaryPath ?: resolveBinaryPath() ?: "yt-dlp"

    private val json = Json { ignoreUnknownKeys = true }

    fun download(url: String, outputDir: File): Flow<DownloadState> =
        flow {
                outputDir.mkdirs()

                val command =
                    listOf(
                        resolvedBinaryPath,
                        "--newline",
                        "--no-color",
                        "--concurrent-fragments",
                        "4",
                        "--progress-template",
                        "download:%(progress)j",
                        "-o",
                        "%(title).200B [%(id)s].%(ext)s",
                        url,
                    )

                val process =
                    try {
                        ProcessBuilder(command).directory(outputDir).start()
                    } catch (e: Exception) {
                        val message =
                            if (resolvedBinaryPath == "yt-dlp") {
                                "yt-dlp was not found. Install it (e.g. \"winget install yt-dlp\" " +
                                    "on Windows) or place the yt-dlp executable next to the Seal app."
                            } else {
                                "Failed to launch $resolvedBinaryPath: ${e.message}"
                            }
                        emit(DownloadState.Error(message))
                        return@flow
                    }

                // Kill the subprocess when the collecting coroutine is cancelled (e.g. the user
                // cancels the download). Destroying the process also closes its stdout, which
                // unblocks the readLine() loop below.
                currentCoroutineContext().job.invokeOnCompletion { cause ->
                    if (cause != null) process.destroy()
                }

                var lastTitle: String? = null
                val stderrLines = StringBuilder()

                val stderrThread =
                    Thread {
                        process.errorStream.bufferedReader().forEachLine {
                            stderrLines.appendLine(it)
                        }
                    }
                stderrThread.isDaemon = true
                stderrThread.start()

                val stdout = process.inputStream.bufferedReader()
                var line = stdout.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("{")) {
                        line = stdout.readLine()
                        continue
                    }

                    val progress =
                        runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
                    if (progress == null) {
                        line = stdout.readLine()
                        continue
                    }

                    lastTitle = progress.stringOrNull("filename")?.let(::fileNameToTitle) ?: lastTitle

                    when (progress.stringOrNull("status")) {
                        "downloading" -> {
                            val percent = progress.doubleOrNull("_percent") ?: 0.0
                            emit(
                                DownloadState.Running(
                                    title = lastTitle,
                                    progress = (percent / 100.0).toFloat().coerceIn(0f, 1f),
                                    progressText = progress.stringOrNull("_percent_str")?.trim() ?: "",
                                    speedText = progress.stringOrNull("_speed_str")?.trim() ?: "",
                                )
                            )
                        }
                        "finished" -> {
                            val filePath = File(outputDir, progress.stringOrNull("filename") ?: "").path
                            emit(DownloadState.Completed(title = lastTitle, filePath = filePath))
                        }
                    }

                    line = stdout.readLine()
                }

                val exitCode = process.waitFor()
                stderrThread.join(1000)

                if (exitCode != 0) {
                    val message = stderrLines.lineSequence().lastOrNull { it.isNotBlank() }
                            ?: "yt-dlp exited with code $exitCode"
                    emit(DownloadState.Error(message))
                }
            }
            .flowOn(Dispatchers.IO)

    private fun fileNameToTitle(fileName: String): String =
        fileName.substringBeforeLast('.').substringBeforeLast(" [")

    private fun kotlinx.serialization.json.JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun kotlinx.serialization.json.JsonObject.doubleOrNull(key: String): Double? =
        this[key]?.jsonPrimitive?.content?.toDoubleOrNull()

    companion object {
        /**
         * Looks for a yt-dlp binary in, by priority:
         * 1. The bundled app-resources folder — Compose Desktop sets
         *    `compose.application.resources.dir` only for packaged native distributions
         *    (MSI/EXE/DMG/DEB); it is absent when running via `./gradlew :desktop:run`.
         * 2. Next to the launcher executable, so users can drop a yt-dlp binary alongside the app.
         * 3. The current working directory.
         *
         * Returns null when none of those exist, in which case the caller falls back to `yt-dlp`
         * on PATH.
         */
        private fun resolveBinaryPath(): String? {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val binaryName = if (isWindows) "yt-dlp.exe" else "yt-dlp"

            val candidates = buildList {
                System.getProperty("compose.application.resources.dir")?.let {
                    add(File(it, binaryName))
                }
                ProcessHandle.current().info().command().orElse(null)?.let {
                    File(it).parentFile?.let { dir -> add(File(dir, binaryName)) }
                }
                add(File(System.getProperty("user.dir"), binaryName))
            }
            return candidates.firstOrNull { it.isFile }?.absolutePath
        }
    }
}
