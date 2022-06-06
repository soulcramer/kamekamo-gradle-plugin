package kamekamo.gradle

import kamekamo.gradle.tasks.detektbaseline.MergeDetektBaselinesTask
import kamekamo.gradle.tasks.robolectric.UpdateRobolectricJarsTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File

/** Registry of global configuration info. */
public class GlobalConfig
private constructor(
    internal val updateRobolectricJarsTask: TaskProvider<UpdateRobolectricJarsTask>,
    internal val mergeDetektBaselinesTask: TaskProvider<MergeDetektBaselinesTask>?,
    internal val shouldEnableBugsnagOnRelease: Boolean,
    internal val kotlinDaemonArgs: List<String>,
    internal val errorProneCheckNamesAsErrors: List<String>
) {

    internal companion object {
        operator fun invoke(project: Project): GlobalConfig {
            check(project == project.rootProject) { "Project is not root project!" }
            val globalKameKamoProperties = KameKamoProperties(project)
            val robolectricJarsDownloadTask =
                project.createRobolectricJarsDownloadTask(globalKameKamoProperties)
            val mergeDetektBaselinesTask =
                if (project.gradle.startParameter.taskNames.any { it == MergeDetektBaselinesTask.TASK_NAME }
                ) {
                    project.tasks.register<MergeDetektBaselinesTask>(MergeDetektBaselinesTask.TASK_NAME) {
                        outputFile.set(project.layout.projectDirectory.file("config/detekt/baseline.xml"))
                    }
                } else {
                    null
                }
            return GlobalConfig(
                updateRobolectricJarsTask = robolectricJarsDownloadTask,
                mergeDetektBaselinesTask = mergeDetektBaselinesTask,
                // Release builds are cut from a combination of main and branches starting with "release"
                shouldEnableBugsnagOnRelease = project.shouldEnableBugsnagPlugin,
                kotlinDaemonArgs = globalKameKamoProperties.kotlinDaemonArgs.split(" "),
                errorProneCheckNamesAsErrors =
                globalKameKamoProperties.errorProneCheckNamesAsErrors?.split(":").orEmpty()
            )
        }
    }
}

private fun Project.createRobolectricJarsDownloadTask(
    kameKamoProperties: KameKamoProperties
): TaskProvider<UpdateRobolectricJarsTask> {
    check(isRootProject) {
        "Robolectric jars task should only be created once on the root project. Tried to apply on $name"
    }

    return tasks.register<UpdateRobolectricJarsTask>("updateRobolectricJars") {
        val sdksProvider = providers.provider { kameKamoProperties.robolectricTestSdks }
        val iVersionProvider = providers.provider { kameKamoProperties.robolectricIVersion }
        sdkVersions.set(sdksProvider)
        instrumentedVersion.set(iVersionProvider)
        val gradleUserHomeDir = gradle.gradleUserHomeDir
        outputDir.set(project.layout.dir(project.provider { robolectricJars(gradleUserHomeDir) }))
        offline.set(project.gradle.startParameter.isOffline)

        // If we already have the expected jars downloaded locally, then we can mark this task as up
        // to date.
        val robolectricJarsDir = robolectricJars(gradleUserHomeDir, createDirsIfMissing = false)
        outputs.upToDateWhen {
            // Annoyingly this doesn't seem to actually seem to make the task _not_ run even if it
            // returns true because Gradle APIs make no sense.
            if (robolectricJarsDir.exists()) {
                val actual =
                    UpdateRobolectricJarsTask.jarsIn(robolectricJarsDir).mapTo(LinkedHashSet(), File::getName)
                val expected = sdksProvider.get().mapTo(LinkedHashSet()) { sdkFor(it).dependencyJar().name }

                // If there's any delta here, let's re-run to be safe. Covers:
                // - New jars to download
                // - Stale old jars to delete
                actual == expected
            } else {
                false
            }
        }
        // We can't reliably cache this. This call is redundant since we don't declare output, but
        // just to be explicit.
        outputs.cacheIf { false }
    }
}