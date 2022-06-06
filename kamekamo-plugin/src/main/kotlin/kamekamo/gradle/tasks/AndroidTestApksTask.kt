package kamekamo.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/**
 * A task that aggregates all the androidTest apk paths and writes them (newline-delimited) to an
 * [outputFile] in the format that Fladle expects.
 *
 * Not cacheable because this outputs absolute paths.
 */
// TODO in the future, Gradle technically has a more "correct" mechanism for
//  exposing information like this via "Outgoing Variants". This API is not remotely easy to grok
//  so let's save implementing that for a day if/when we have multiple app targets in the Android
//  repo that need to only run dependent libraries' test APKs.
//  The Jacoco Report Aggregation Plugin is one such example of a plugin that contributes such
//  metadata: https://docs.gradle.org/current/userguide/jacoco_plugin.html#sec:outgoing_variants
//  Full docs: https://docs.gradle.org/current/userguide/cross_project_publications.html
public abstract class AndroidTestApksTask : DefaultTask() {
    @get:PathSensitive(RELATIVE)
    @get:InputFiles
    public abstract val androidTestApkDirs: ConfigurableFileCollection

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    init {
        group = "kamekamo"
    }

    @TaskAction
    public fun writeFiles() {
        outputFile
            .asFile
            .get()
            .writeText(
                androidTestApkDirs
                    .asSequence()
                    .flatMap { it.walk() }
                    .filter { it.isFile && it.extension == "apk" }
                    .joinToString("\n") { apk -> "- test: ${apk.absolutePath}" }
            )
    }

    public companion object {
        public const val NAME: String = "aggregateAndroidTestApks"

        internal fun register(project: Project): TaskProvider<AndroidTestApksTask> {
            return project.tasks.register<AndroidTestApksTask>(NAME) {
                outputFile.set(
                    project.layout.buildDirectory.file("kamekamo/androidTestAggregator/aggregatedTestApks.txt")
                )
            }
        }
    }
}