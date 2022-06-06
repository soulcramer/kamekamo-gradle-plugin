package kamekamo.gradle.tasks

/**
 * Downloads the ktfmt binary from maven central.
 *
 * Usage:
 * ```
 *     ./gradlew updateKtfmt
 * ```
 */
internal abstract class KtfmtDownloadTask :
    BaseDownloadTask(
        targetName = "ktfmt",
        addExecPrefix = true,
        urlTemplate = { version ->
            "https://repo1.maven.org/maven2/com/facebook/ktfmt/$version/ktfmt-$version-jar-with-dependencies.jar"
        }
    )