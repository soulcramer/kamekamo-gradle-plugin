package kamekamo.gradle.tasks

/**
 * Downloads the Detekt binary from its GitHub releases.
 *
 * Usage:
 * ```
 *     ./gradlew updateDetekt
 * ```
 */
internal abstract class DetektDownloadTask :
    BaseDownloadTask(
        targetName = "Detekt",
        // https://github.com/detekt/detekt/issues/3895
        addExecPrefix = true,
        urlTemplate = { version ->
            "https://github.com/detekt/detekt/releases/download/v$version/detekt-cli-$version-all.jar"
        }
    )