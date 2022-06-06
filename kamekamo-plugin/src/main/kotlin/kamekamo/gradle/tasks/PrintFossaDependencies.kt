package kamekamo.gradle.tasks

import kamekamo.gradle.safeCapitalize
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/**
 * A task that writes runtime dependency info found in [identifiersToVersions].
 *
 * This is used by the Fossa tool to parse and look up our dependencies. The output file is in the
 * form of a newline-delimited list of `<module identifier>:<version>`.
 *
 * Example:
 *
 * ```
 * mvn+com.google.gson:gson:2.8.6
 * mvn+com.google.guava:guava:29-jre
 * ```
 *
 * More details:
 * https://slack-pde.slack.com/archives/C012A55CZNH/p1607469397011200?thread_ts=1607384582.004300&cid=C012A55CZNH
 */
@CacheableTask
public abstract class PrintFossaDependencies : BaseDependencyCheckTask() {

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    init {
        group = "kamekamo"
    }

    override fun handleDependencies(identifiersToVersions: Map<String, String>) {
        val file = outputFile.asFile.get()
        file.bufferedWriter().use { writer ->
            identifiersToVersions
                .entries
                .map { (moduleIdentifier, version) -> "mvn+$moduleIdentifier:$version" }
                .sorted() // Important for deterministic ouputs
                .joinTo(writer, separator = "\n")
        }

        logger.lifecycle("Fossa deps written to $file")
    }

    public companion object {
        public fun register(
            project: Project,
            name: String,
            configuration: Configuration
        ): TaskProvider<PrintFossaDependencies> {
            return project.tasks.register<PrintFossaDependencies>(
                "print${name.safeCapitalize()}FossaDependencies"
            ) {
                outputFile.set(project.layout.buildDirectory.file("reports/kamekamo/fossa/$name.txt"))
                configureIdentifiersToVersions(configuration)
            }
        }
    }
}