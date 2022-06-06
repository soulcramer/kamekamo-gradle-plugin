package kamekamo.gradle

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kamekamo.gradle.dependencies.DependencyCollection
import kamekamo.gradle.dependencies.DependencyDef
import kamekamo.gradle.dependencies.boms
import kamekamo.gradle.dependencies.flattenedPlatformCoordinates
import kamekamo.gradle.dependencies.identifierMap
import kamekamo.gradle.util.sneakyNull
import okio.buffer
import okio.source
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.dependencies
import java.io.File
import java.util.Locale

public object Platforms {

    private val IGNORED_GROUPS =
        setOf(
            // Lint is handled by the regular shadow build since they're tied to AGP
            "com.android.tools.lint",
            "com.android.tools",

            // Databinding is tied to viewbinding, which in turn is tied to AGP like lint
            "androidx.databinding",

            // Bugsnag's 5.x snapshot contains breaking changes
            "com.jakewharton.timber",

            // Junit will actually see JUnit 5, which is basically a different library.
            "junit",

            // Kotlin updates are handled by the regular shadow build.
            "org.jetbrains.kotlin",
            "org.jetbrains.kotlinx"
        )
    private val IGNORED_IDENTIFIERS =
        setOf(
            // Some google libraries' snapshots seems to actually be old.
            "com.google.auto:auto-common",
            "com.google.testing.compile:compile-testing",
            // Calls is rarely updated and usually contains breaking changes.
            "com.slack.android:calls",
            // These aren't reliable for semver checks and only updated ad-hoc
            "com.slack.data:client-thrifty"
        )

    /** Generates a `libs.versions.toml` representation of this [dependencyCollection]. */
    public fun generateLibsToml(
        targetFile: File,
        catalogName: String,
        providers: ProviderFactory,
        dependencyCollection: DependencyCollection,
        logger: Logger
    ) {
        logger.lifecycle("Generating $catalogName.toml")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.createNewFile()

        val identifierMap = dependencyCollection.identifierMap()

        targetFile.bufferedWriter().use { writer ->
            val versionsToLibs =
                dependencyCollection.flattenedPlatformCoordinates().groupBy { it.gradleProperty }.mapKeys {
                    it.key.removePrefix("kamekamo.dependencies.")
                }

            writer.append("[versions]")
            writer.appendLine()
            versionsToLibs.keys.sorted().forEach {
                writer.append(
                    "${tomlKey(it)} = \"${providers.gradleProperty("kamekamo.dependencies.$it").get()}\""
                )
                writer.appendLine()
            }
            writer.appendLine()
            writer.append("[libraries]")
            writer.appendLine()
            versionsToLibs
                .flatMap { (key, deps) ->
                    deps.map { dep ->
                        val libPath = tomlLibIdentifier(identifierMap, dep.identifier)
                        val versionRef =
                            if (dep.isBomManaged) {
                                ""
                            } else {
                                ", version.ref = \"${tomlKey(key)}\""
                            }
                        "$libPath = { module = \"${dep.identifier}\"$versionRef }"
                    }
                }
                // Add `-bom` deps to libraries
                .plus(
                    dependencyCollection.boms().map { group ->
                        val def = group.toBomDependencyDef()
                        val tomlKey = tomlKey(def.gradleProperty)
                        val libPath = "$tomlKey-bom"
                        "$libPath = { module = \"${def.identifier}\", version.ref = \"$tomlKey\" }"
                    }
                )
                .sorted()
                .forEach { lib ->
                    writer.append(lib)
                    writer.appendLine()
                }
            writer.appendLine()

            // Generate possible bundles. Note that we should check these manually and delete/adjust any
            // as needed.
            writer.append("[bundles]")
            writer.appendLine()
            for ((key, deps) in versionsToLibs.toSortedMap()) {
                // bundles only make sense when there's multiple
                if (deps.size < 2) continue
                val bundleKeys =
                    deps.joinToString(", ") { dep ->
                        "\"${tomlLibIdentifier(identifierMap, dep.identifier)}\""
                    }
                writer.append("${tomlKey(key)} = [ $bundleKeys ]")
                writer.appendLine()
            }
        }
        logger.lifecycle("Wrote toml to $targetFile")
    }

    private fun tomlLibIdentifier(identifierMap: Map<String, String>, identifier: String) =
        tomlLibForPath(identifierMap.getValue(identifier))

    private fun tomlLibForPath(path: String) =
        path.removePrefix("KameKamoDependencies.").split(".").joinToString("-") {
            it.decapitalize(Locale.US)
        }

    /** Rewrites build files in the new toml format instead. */
    public fun rewriteBuildFiles(
        rootProjectDir: File,
        logger: Logger,
        dependencyCollection: DependencyCollection
    ) {
        val identifierMap = dependencyCollection.identifierMap()
        val pathMap = identifierMap.entries.associateBy({ it.value }) { it.key }
        rootProjectDir
            .walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            // Don't modify kamekamo-platform, its usage is intentional
            .filterNot { it.parentFile.name == "kamekamo-platform" }
            .forEach { buildFile ->
                var modified = false
                val newLines = mutableListOf<String>()
                var skipNext = false
                for (line in buildFile.readLines()) {
                    if (line.isBlank() && skipNext) {
                        skipNext = false
                        continue
                    }
                    skipNext = false
                    if ("(KameKamoDependencies." in line) {
                        val newLine =
                            line.replace(Regex("\\((KameKamoDependencies.[a-zA-Z_0-9.-]+)\\)")) { match ->
                                val path = match.groupValues[1]
                                modified = true
                                val identifier = pathMap[path] ?: error("Couldn't find $path in $identifierMap")
                                val tomlIdentifier = tomlLibIdentifier(identifierMap, identifier)
                                val newIdentifier = tomlIdentifier.replace("-", ".")
                                "(libs.$newIdentifier)"
                            }
                        newLines.add(newLine)
                    } else if (line.trim() == "import KameKamoDependencies") {
                        // skip this line and the next one if empty
                        skipNext = true
                        modified = true
                    } else {
                        newLines.add(line)
                    }
                }
                if (modified) {
                    // Pad with a trailing newline
                    logger.lifecycle("Patching $buildFile")
                    buildFile.writeText(newLines.joinToString("\n") + "\n")
                }
            }
    }

    /** Applies constraints from a given [catalog]. */
    public fun applyFromCatalog(
        project: Project,
        catalog: VersionCatalog = project.getVersionsCatalog()
    ) {
        check(project.pluginManager.hasPlugin("org.gradle.java-platform")) {
            "Must be a java-platform project!"
        }
        // TODO
        //  - Support overriding values from a VERSIONS_JSON env property
        //    - snapshots should use strict versions

        project.dependencies {
            constraints {
                for (alias in catalog.libraryAliases) {
                    add("api", catalog.findLibrary(alias).get())
                }
            }
        }
    }

    /**
     * Applies dependencies in `java-platform` [project] by bridging their definitions from a given
     * [dependency collection][dependencies] to versions defined in gradle properties (usually via
     * `gradle.properties`.
     *
     * It currently has support for overriding values from a `VERSIONS_JSON` env property, but this
     * will be removed eventually.
     */
    public fun applyToProject(project: Project, dependencies: DependencyCollection) {
        check(project.pluginManager.hasPlugin("org.gradle.java-platform")) {
            "Must be a java-platform project!"
        }

        val logger = project.logger
        val kameKamoProperties = KameKamoProperties(project)
        val snapshotsEnabled = kameKamoProperties.enableSnapshots

        // Overrides provider, used when testing newer dependency versions on shadow builds
        // TODO We should just make the shadow build replace the versions in gradle.properties and
        // remove all this extra
        //  logic
        val overridesProvider =
            project.providers.provider {
                val path = kameKamoProperties.versionsJson ?: return@provider sneakyNull()
                println("Parsing versions json at $path")
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                path.source().buffer().use { moshi.adapter(VersionsOutput::class.java).fromJson(it)!! }
            }

        val providers = project.providers

        val flattened = dependencies.flattenedPlatformCoordinates()
        project.dependencies {
            constraints {
                for (def in flattened) {
                    if (def.isBomManaged) continue
                    val version = getOrOverride(providers, def, overridesProvider, logger)
                    if (snapshotsEnabled && version.endsWith("-SNAPSHOT")) {
                        add("api", def.coordinates) { version { strictly(version) } }
                    } else {
                        add("api", def.coordinates) { version { require(version) } }
                    }
                }
            }
        }
    }

    private fun getOrOverride(
        providers: ProviderFactory,
        dependencyDef: DependencyDef,
        overridesProvider: Provider<VersionsOutput>,
        logger: Logger
    ): String {
        val isOverridable =
            dependencyDef.group !in IGNORED_GROUPS &&
                dependencyDef.identifier !in IGNORED_IDENTIFIERS &&
                overridesProvider.isPresent

        val expectedProperty = dependencyDef.gradleProperty
        val defaultProvider = providers.gradleProperty(expectedProperty)

        val versionProvider =
            if (isOverridable) {
                providers
                    .provider {
                        overridesProvider.get().identifierMap[dependencyDef.identifier]?.available?.newTarget()
                    }
                    .zip(defaultProvider) { overridden, default ->
                        if (overridden != null) {
                            println("[KameKamoPlatform] override: ${dependencyDef.identifier}:$overridden")
                            overridden
                        } else {
                            logger.debug("[KameKamoPlatform] constraining ${dependencyDef.identifier} to $default")
                            default
                        }
                    }
            } else {
                defaultProvider
            }

        return try {
            versionProvider.get()
        } catch (e: MissingValueException) {
            val message =
                "No version found for '${dependencyDef.identifier}' " +
                    "(key: '$expectedProperty'). Please add " +
                    "'${expectedProperty.replace(":", "\\:")}' in gradle.properties"
            throw GradleException(message)
        }
    }
}

internal data class VersionsOutput(val outdated: Outdated) {
    val identifierMap = outdated.dependencies.associateBy { "${it.group}:${it.name}" }
}

internal data class Outdated(val dependencies: Set<Artifact>)

internal data class Artifact(
    val group: String,
    val available: Available,
    val version: String,
    val name: String
)

internal data class Available(val release: String?, val integration: String?) {
    fun newTarget(): String {
        return release ?: integration ?: error("No available target found")
    }
}