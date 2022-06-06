package kamekamo.gradle.tasks

/**
 * Downloads the KtLint binary from its GitHub releases.
 *
 * Usage:
 * ```
 *     ./gradlew updateKtLint
 * ```
 */
internal abstract class KtLintDownloadTask :
    BaseDownloadTask(
        targetName = "KtLint",
        addExecPrefix = false,
        urlTemplate = { version ->
            "https://github.com/pinterest/ktlint/releases/download/$version/ktlint"
        }
    )