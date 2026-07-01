package com.junkfood.seal.desktop.download

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the `yt-dlp` CLI as a subprocess and turns its `--progress-template` JSON output into
 * [DownloadState] updates. Resolves the binary from PATH for now; bundling/locating a
 * platform-specific binary is future work (see the Windows port plan).
 */
class YtDlpDownloader(private val binaryPath: String = "yt-dlp") {

    private val json = Json { ignoreUnknownKeys = true }

    fun download(url: String, outputDir: File): Flow<DownloadState> =
        flow {
                outputDir.mkdirs()

                val command =
                    listOf(
                        binaryPath,
                        "--newline",
                        "--no-color",
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
                        emit(DownloadState.Error("Failed to launch $binaryPath: ${e.message}"))
                        return@flow
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
}
