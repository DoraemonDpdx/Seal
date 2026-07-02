package com.junkfood.seal.desktop.download

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the `yt-dlp` CLI as a subprocess and turns its `--progress-template` JSON output into
 * [DownloadState] updates.
 *
 * Binary resolution: when packaged, the installer bundles yt-dlp (and ffmpeg on Windows) via
 * Compose Desktop's app-resources mechanism (see `desktop/build.gradle.kts`), and those binaries
 * are preferred. When running unpackaged (e.g. `./gradlew :desktop:run`), this falls back to a
 * binary next to the launcher, in the working directory, and finally `yt-dlp` on PATH.
 */
class YtDlpDownloader(binaryPath: String? = null) {

    private val resolvedBinaryPath = binaryPath ?: resolveBinaryPath() ?: "yt-dlp"

    private val json = Json { ignoreUnknownKeys = true }

    /** Directory containing the yt-dlp binary Seal is actually using, or null for PATH lookup. */
    val binaryDirectory: String?
        get() = File(resolvedBinaryPath).takeIf { it.isAbsolute }?.parent

    /**
     * Runs `yt-dlp -J` to fetch metadata (title + available formats) for the format selector.
     * Executes on [Dispatchers.IO]; throws with yt-dlp's stderr on failure.
     */
    suspend fun fetchVideoInfo(url: String): VideoInfo =
        withContext(Dispatchers.IO) {
            val command =
                buildList {
                    add(resolvedBinaryPath)
                    add("-J")
                    add("--no-playlist")
                    ffmpegLocationArgs().forEach(::add)
                    add(url)
                }
            val process = ProcessBuilder(command).start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error(stderr.lineSequence().lastOrNull { it.isNotBlank() } ?: "yt-dlp exited with code $exitCode")
            }
            parseVideoInfo(json.parseToJsonElement(stdout).jsonObject)
        }

    fun download(
        url: String,
        outputDir: File,
        preferences: DownloadPreferences = DownloadPreferences(),
    ): Flow<DownloadState> =
        flow {
                outputDir.mkdirs()

                val command = buildCommand(url, preferences)

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

    private fun buildCommand(url: String, prefs: DownloadPreferences): List<String> = buildList {
        add(resolvedBinaryPath)
        add("--newline")
        add("--no-color")
        add("--concurrent-fragments")
        add(prefs.concurrentFragments.coerceIn(1, 16).toString())
        add("--progress-template")
        add("download:%(progress)j")
        add("-o")
        add("%(title).200B [%(id)s].%(ext)s")

        add(if (prefs.downloadPlaylist) "--yes-playlist" else "--no-playlist")

        ffmpegLocationArgs().forEach(::add)

        when {
            prefs.formatId != null -> {
                add("-f")
                add(prefs.formatId)
            }
            prefs.downloadType == DownloadType.Audio -> {
                add("-x")
                if (prefs.audioFormat.isNotEmpty()) {
                    add("--audio-format")
                    add(prefs.audioFormat)
                }
            }
            else -> {
                add("-f")
                add(videoFormatSelector(prefs))
                if (prefs.videoFormat == "mp4") {
                    add("--merge-output-format")
                    add("mp4")
                }
            }
        }

        if (prefs.downloadSubtitles) {
            add("--write-subs")
            add("--sub-langs")
            add("all,-live_chat")
        }
        if (prefs.embedThumbnail) add("--embed-thumbnail")
        if (prefs.embedMetadata) add("--embed-metadata")
        if (prefs.sponsorBlock) {
            add("--sponsorblock-remove")
            add("default")
        }

        if (prefs.customArgs.isNotBlank()) addAll(tokenizeArgs(prefs.customArgs))

        add(url)
    }

    private fun videoFormatSelector(prefs: DownloadPreferences): String {
        val height = if (prefs.videoQuality > 0) "[height<=${prefs.videoQuality}]" else ""
        val ext =
            when (prefs.videoFormat) {
                "mp4" -> "[ext=mp4]"
                "webm" -> "[ext=webm]"
                else -> ""
            }
        return "bv*$height$ext+ba/b$height$ext/b$height"
    }

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
        fun resolveBinaryPath(): String? {
            val binaryName = if (isWindows) "yt-dlp.exe" else "yt-dlp"
            return candidateDirs().map { File(it, binaryName) }.firstOrNull { it.isFile }?.absolutePath
        }

        /** Points yt-dlp at the bundled ffmpeg (needed for merging/extraction), when present. */
        private fun ffmpegLocationArgs(): List<String> {
            val ffmpegName = if (isWindows) "ffmpeg.exe" else "ffmpeg"
            val ffmpeg = candidateDirs().map { File(it, ffmpegName) }.firstOrNull { it.isFile }
            return if (ffmpeg != null) listOf("--ffmpeg-location", ffmpeg.parent) else emptyList()
        }

        private fun candidateDirs(): List<File> = buildList {
            System.getProperty("compose.application.resources.dir")?.let { add(File(it)) }
            ProcessHandle.current().info().command().orElse(null)?.let { File(it).parentFile?.let(::add) }
            add(File(System.getProperty("user.dir")))
        }

        private val isWindows: Boolean
            get() = System.getProperty("os.name")?.lowercase()?.contains("win") == true

        /** Splits custom args, honouring double and single quotes. */
        internal fun tokenizeArgs(input: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            for (c in input) {
                when {
                    quote != null -> if (c == quote) quote = null else current.append(c)
                    c == '"' || c == '\'' -> quote = c
                    c.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            tokens += current.toString()
                            current.clear()
                        }
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }
    }
}
