package kamekamo.gradle.tasks

import kamekamo.gradle.getVersionsCatalog
import kamekamo.gradle.safeCapitalize
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/**
 * A task that checks expected versions (from [mappedIdentifiersToVersions]) against runtime
 * versions found in [identifiersToVersions].
 *
 * This is important to check for some dependencies pulling newer versions unexpectedly.
 */
@CacheableTask
public abstract class CheckDependencyVersionsTask : BaseDependencyCheckTask() {

    @get:Input
    public abstract val mappedIdentifiersToVersions: MapProperty<String, String>

    // Only present for cacheability
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    init {
        group = "verification"
    }

    override fun handleDependencies(identifiersToVersions: Map<String, String>) {
        val identifierMap =
            mappedIdentifiersToVersions.get().filterValues {
                // Blank versions mean it's managed by a BOM and we don't need to check it here
                it.isNotBlank()
            }

        val actualVersions = identifiersToVersions.filterKeys { it in identifierMap }

        val issues =
            mutableListOf<String>().apply {
                mappedIdentifiersToVersions.get().forEach { (identifier, declared) ->
                    val actual = actualVersions[identifier] ?: return@forEach
                    if (actual != declared) {
                        this += "$identifier - declared $declared - actual $actual"
                    }
                }
            }

        val issuesString = issues.joinToString("\n")
        if (issues.isNotEmpty()) {
            throw GradleException(
                "Mismatched dependency versions! Please update their versions in" +
                    " libs.versions.toml to match their resolved versions.\n\n" +
                    "${issuesString}\n\nIf you just updated a library, it may pull " +
                    "in a newer version of a dependency that we separately specify in libs.versions.toml. " +
                    "Keeping the versions in libs.versions.toml in sync with the final resolved versions " +
                    "makes it easier to see what version of a library we depend on at a glance."
            )
        }

        outputFile.asFile.get().writeText("Issues:\n$issuesString")
    }

    public companion object {
        public fun register(
            project: Project,
            name: String,
            configuration: Configuration
        ): TaskProvider<CheckDependencyVersionsTask> {
            return project.tasks.register<CheckDependencyVersionsTask>(
                "check${name.safeCapitalize()}Versions"
            ) {
                configureIdentifiersToVersions(configuration)
                outputFile.set(
                    project.layout.buildDirectory.file(
                        "reports/kamekamo/dependencyVersionsIssues/$name/issues.txt"
                    )
                )
                val catalog = project.getVersionsCatalog()
                this.mappedIdentifiersToVersions.putAll(
                    project.provider {
                        catalog.libraryAliases.associate {
                            val dep = catalog.findLibrary(it).get().get()
                            dep.module.toString() to dep.versionConstraint.toString()
                        }
                    }
                )
            }
        }
    }
}