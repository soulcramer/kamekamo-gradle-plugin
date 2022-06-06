package kamekamo.gradle.tasks

/**
 * Downloads the GJF binary from its GitHub releases.
 *
 * Usage:
 * ```
 *     ./gradlew updateGjf
 * ```
 */
internal abstract class GjfDownloadTask :
    BaseDownloadTask(
        targetName = "GoogleJavaFormat",
        // https://github.com/google/google-java-format#jdk-16
        addExecPrefix = true,
        urlTemplate = { version ->
            "https://github.com/google/google-java-format/releases/download/v$version/google-java-format-$version-all-deps.jar"
        }
    )