package kamekamo.gradle

import com.google.common.base.CaseFormat
import kamekamo.executeWithResult
import kamekamo.gradle.agp.VersionNumber
import kamekamo.gradle.dependencies.DependencyDef
import kamekamo.gradle.dependencies.DependencyGroup
import kamekamo.gradle.util.mapToBoolean
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.util.Locale

/** If true, this is currently running on GitHub Actions CI. */
public val Project.isActionsCi: Boolean
    get() = providers.environmentVariable("GITHUB_ACTIONS").mapToBoolean().getOrElse(false)

/** If true, this is currently running on Buildkite. */
public val Project.isBuildkite: Boolean
    get() = providers.environmentVariable("BUILDKITE").mapToBoolean().getOrElse(false)

/** If true, this is currently running on Jenkins CI. */
public val Project.isJenkins: Boolean
    get() = jenkinsHome.isPresent

/** If true, this is currently running on any CI. */
public val Project.isCi: Boolean
    get() = isJenkins || isActionsCi || isBuildkite

/** Useful helper for resolving a `group:name:version` bom notation for a [DependencyGroup]. */
internal fun DependencyGroup.toBomDependencyDef(): DependencyDef {
    checkNotNull(bomArtifact) { "No bom found for group ${this::class.simpleName}" }
    return DependencyDef(group, bomArtifact, gradleProperty = groupGradleProperty)
}

/** Returns the git branch this is running on. */
public fun Project.gitBranch(): Provider<String> {
    return when {
        isJenkins ->
            providers
                .environmentVariable("CHANGE_BRANCH")
                .orElse(providers.environmentVariable("BRANCH_NAME"))
        isBuildkite -> providers.environmentVariable("BUILDKITE_BRANCH")
        else ->
            executeWithResult(
                project.providers,
                rootProject.rootDir,
                listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
                isRelevantToConfigurationCache = false
            )
                .map { it.lines()[0].trim() }
    }
}

/**
 * We only enable bugsnag on CI branches starting with "release" (the prefix release team uses) or
 * main and disable the bugsnag gradle plugin in everywhere else to speed up build times. Note that
 * this includes a few things: preventing manifest modifications per-build, uploading mapping files
 * to their slow endpoints, etc.
 */
public val Project.shouldEnableBugsnagPlugin: Boolean
    get() {
        return (isCi) && gitBranch().map { it == "main" || it.startsWith("release") }.getOrElse(false)
    }

private const val GIT_VERSION_PREFIX = "git version "

/**
 * Parses a git [VersionNumber] from a given [gitVersion], usually from a command line `git
 * --version` output.
 */
internal fun parseGitVersion(gitVersion: String?): VersionNumber {
    if (!gitVersion.isNullOrBlank()) {
        val trimmed = gitVersion.trim()
        val split = trimmed.split("\n").map { it.trim() }
        val versionLine =
            if (split.size > 1) {
                split.first { it.startsWith(GIT_VERSION_PREFIX) }
            } else {
                split[0]
            }
        val version = versionLine.removePrefix("git version ")
        return VersionNumber.parse(version)
    }

    return VersionNumber.UNKNOWN
}

internal fun robolectricJars(gradleUserHomeDir: File, createDirsIfMissing: Boolean = true): File {
    val kamekamoHome =
        File(gradleUserHomeDir, "kamekamo").apply {
            if (createDirsIfMissing) {
                if (!exists()) {
                    mkdir()
                }
            }
        }
    return File(kamekamoHome, "robolectric-jars").apply {
        if (createDirsIfMissing) {
            if (!exists()) {
                mkdir()
            }
        }
    }
}

public fun Project.supportedLanguages(supportedLanguages: SupportedLanguagesEnum): List<String> {
    val kameKamoProperties = KameKamoProperties(project)
    val gaLanguages = kameKamoProperties.supportedLanguages.split(",")

    val internalLanguages = kameKamoProperties.supportedLanguagesInternal.split(",")

    val betaLanguages = kameKamoProperties.supportedLanguagesBeta.split(",")

    return when (supportedLanguages) {
        SupportedLanguagesEnum.GA -> gaLanguages.toList().filter { it.isNotBlank() }
        SupportedLanguagesEnum.INTERNAL ->
            internalLanguages.union(gaLanguages).toList().filter { it.isNotBlank() }
        SupportedLanguagesEnum.BETA ->
            betaLanguages.union(gaLanguages).toList().filter { it.isNotBlank() }
    }
}

public enum class SupportedLanguagesEnum {
    /** Languages included in the GA release */
    GA,

    /** Languages included in internal builds */
    INTERNAL,

    /** Languages included in Beta builds */
    BETA
}

public val Project.fullGitSha: Provider<String>
    get() {
        return executeWithResult(
            providers,
            rootProject.rootDir,
            listOf("git", "rev-parse", "HEAD"),
            isRelevantToConfigurationCache = true
        )
    }

public val Project.gitSha: Provider<String>
    get() {
        return executeWithResult(
            providers,
            rootProject.rootDir,
            listOf("git", "rev-parse", "--short", "HEAD"),
            isRelevantToConfigurationCache = true
        )
    }

public val Project.ciBuildNumber: Provider<String>
    get() {
        return providers
            .environmentVariable("BUILD_NUMBER")
            .orElse(providers.environmentVariable("BUILDKITE_BUILD_NUMBER"))
    }

public val Project.jenkinsHome: Provider<String>
    get() {
        return providers.environmentVariable("JENKINS_HOME")
    }

public val Project.usePrototypeAppId: Boolean
    get() {
        return KameKamoProperties(this).usePrototypeAppId
    }

// Exposed for use in projects since this uses an experimental API that's understood to be allowed
// here but not in
// downstream projects.
public fun String.safeCapitalize(): String {
    return capitalize(Locale.US)
}

/** Returns the variant used for `ciUnitTest` tasks on this (presumably) Android project. */
internal fun Project.ciUnitTestAndroidVariant(): String {
    val ciUnitTestVariant = KameKamoProperties(this).ciUnitTestVariant
    return ciUnitTestVariant.capitalize(Locale.US)
}

internal fun Project.jdkVersion(): Int {
    return KameKamoProperties(this).jdkVersion
}

internal fun Project.jvmTargetVersion(): Int {
    return KameKamoProperties(this).jvmTarget
}

internal fun Project.getVersionsCatalog(
    properties: KameKamoProperties = KameKamoProperties(this)
): VersionCatalog {
    return getVersionsCatalogOrNull(properties) ?: error("No versions catalog found!")
}

internal fun Project.getVersionsCatalogOrNull(
    properties: KameKamoProperties = KameKamoProperties(this)
): VersionCatalog? {
    val name = properties.versionCatalogName
    return try {
        project.extensions.getByType<VersionCatalogsExtension>().named(name)
    } catch (ignored: Exception) {
        null
    }
}

/** Returns a map of module identifiers to toml library reference aliases */
internal fun VersionCatalog.identifierMap(): Map<String, String> {
    return libraryAliases.associateBy { findLibrary(it).get().get().module.toString() }
}

/**
 * We want the following conversions:
 * - `bugsnag-gradle` -> `bugsnagGradle`
 * - `bugsnag_gradle` -> `bugsnagGradle`
 * - `bugsnag.gradle` -> `bugsnag-gradle`
 *
 * This is because `bugsnag-gradle` is converted to a nesting `bugsnag.gradle` in version accessors
 * and `bugsnag.gradle` is converted to `bugsnagGradle`. We've historically done the opposite with
 * gradle property versions though and used -/_ as separators in a continuous word and `.` for
 * nesting.
 */
internal fun tomlKey(key: String): String =
    key.replace("-", "%").replace(".", "-").replace("%", ".").replace("_", ".").snakeToCamel()

internal fun String.snakeToCamel(upper: Boolean = false): String {
    return buildString {
        var capNext = upper
        for (c in this@snakeToCamel) {
            if (c == '_' || c == '-' || c == '.') {
                capNext = true
                continue
            } else {
                if (capNext) {
                    append(c.uppercaseChar())
                    capNext = false
                } else {
                    append(c)
                }
            }
        }
    }
}

private fun kebabCaseToCamelCase(s: String): String {
    return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, s)
}

/**
 * Returns a project accessor representation of the given [projectPath].
 *
 * Example: `:libraries:foundation` -> `libraries.foundation`.
 */
internal fun convertProjectPathToAccessor(projectPath: String): String {
    return projectPath.removePrefix(":").split(":").joinToString(separator = ".") { segment ->
        kebabCaseToCamelCase(segment)
    }
}

/**
 * Similar to [TaskContainer.named], but waits until the task is registered if it doesn't exist,
 * yet. If the task is never registered, then this method will throw an error after the
 * configuration phase.
 */
internal inline fun <reified T : Task> Project.namedLazy(
    targetName: String,
    crossinline action: (TaskProvider<T>) -> Unit
) {
    try {
        action(tasks.named<T>(targetName))
        return
    } catch (ignored: UnknownTaskException) {
    }

    var didRun = false

    tasks.withType<T> {
        if (name == targetName) {
            action(tasks.named<T>(name))
            didRun = true
        }
    }

    afterEvaluate {
        if (!didRun) {
            throw GradleException("Didn't find task $name with type ${T::class}.")
        }
    }
}