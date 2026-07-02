import java.net.URI
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.animation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
}

// Fetches the standalone yt-dlp binary for the host OS so it can be bundled into the installer
// (see YtDlpDownloader.resolveBundledBinaryPath). Only runs at packaging time, not on every
// build/run, so it doesn't slow down day-to-day development.
val ytDlpBinDir = layout.buildDirectory.dir("ytdlp-bin")

val downloadYtDlp by
    tasks.registering {
        description = "Downloads the yt-dlp standalone binary for the host OS to bundle into the installer"

        val outputDirProvider = ytDlpBinDir
        outputs.dir(outputDirProvider)

        doLast {
            val (assetName, targetName) =
                when {
                    OperatingSystem.current().isWindows -> "yt-dlp.exe" to "yt-dlp.exe"
                    OperatingSystem.current().isMacOsX -> "yt-dlp_macos" to "yt-dlp"
                    else -> "yt-dlp_linux" to "yt-dlp"
                }

            val outputDir = outputDirProvider.get().asFile.apply { mkdirs() }
            val target = outputDir.resolve(targetName)

            if (!target.exists()) {
                val url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$assetName"
                logger.lifecycle("Downloading yt-dlp binary from $url")
                URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                if (!OperatingSystem.current().isWindows) target.setExecutable(true)
            }
        }
    }

compose.desktop {
    application {
        mainClass = "com.junkfood.seal.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Seal"
            packageVersion = "1.0.0"
            windows { menuGroup = "Seal" }

            appResourcesRootDir.set(ytDlpBinDir)
        }
    }
}

tasks.matching { it.name.startsWith("package") }.configureEach { dependsOn(downloadYtDlp) }
