package kamekamo.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessExtensionPredeclare
import kamekamo.gradle.util.synchronousEnvProperty
import kamekamo.stats.ModuleStatsTasks
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.retry
import org.gradle.kotlin.dsl.withType
import java.util.Locale
import java.util.Optional
import kotlin.math.max

/**
 * Simple base plugin over [StandardProjectConfigurations]. Eventually functionality from this will
 * be split into more granular plugins.
 *
 * The goal of separating this from [KameKamoRootPlugin] is project isolation.
 */
internal class KameKamoBasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val kameKamoProperties = KameKamoProperties(target)

        if (!target.isRootProject) {
            StandardProjectConfigurations().applyTo(target)
            target.configureTests(kameKamoProperties)

            // Configure Gradle's test-retry plugin for insights on build scans on CI only
            // Thinking here is that we don't want them to retry when iterating since failure
            // there is somewhat expected.
            if (kameKamoProperties.autoApplyTestRetry && target.isCi) {
                target.apply(plugin = "org.gradle.test-retry")
            }

            if (kameKamoProperties.autoApplyCacheFix) {
                target.pluginManager.withPlugin("com.android.base") {
                    target.apply(plugin = "org.gradle.android.cache-fix")
                }
            }

            if (kameKamoProperties.autoApplyDetekt) {
                // Would be nice to have a single Kotlin hook
                // https://youtrack.jetbrains.com/issue/KT-48008
                target.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
                    target.apply(plugin = "io.gitlab.arturbosch.detekt")
                }
                target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                    target.apply(plugin = "io.gitlab.arturbosch.detekt")
                }
            }

            if (kameKamoProperties.autoApplyNullaway) {
                // Always apply the NullAway plugin with errorprone
                target.pluginManager.withPlugin("net.ltgt.errorprone") {
                    target.apply(plugin = "net.ltgt.nullaway")
                }
            }

            // Add the experimental EitherNet APIs where it's used
            // TODO make a more general solution for these
            target.configurations.configureEach {
                incoming.afterResolve {
                    dependencies.forEach { dependency ->
                        if (dependency.name == "eithernet") {
                            target.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
                                .configureEach {
                                    kotlinOptions {
                                        @Suppress("SuspiciousCollectionReassignment")
                                        freeCompilerArgs += "-opt-in=com.kamekamo.eithernet.ExperimentalEitherNetApi"
                                    }
                                }
                        }
                    }
                }
            }

            ModuleStatsTasks.configureSubproject(target)
        }

        // Everything in here applies to all projects
        target.configureSpotless(kameKamoProperties)
        target.configureClasspath(kameKamoProperties)
        val scanApi = ScanApi(target)
        if (scanApi.isAvailable) {
            scanApi.addTestParallelization(target)
            scanApi.addTestSystemProperties(target)
        }
    }

    /** Configures Spotless for formatting. Note we do this per-project for improved performance. */
    private fun Project.configureSpotless(kameKamoProperties: KameKamoProperties) {
        val isRootProject = this.isRootProject
        if (kameKamoProperties.autoApplySpotless) {
            apply(plugin = "com.diffplug.spotless")
        } else {
            return
        }
        pluginManager.withPlugin("com.diffplug.spotless") {
            val spotlessFormatters: SpotlessExtension.() -> Unit = {
                format("misc") {
                    target("*.md", ".gitignore")
                    trimTrailingWhitespace()
                    endWithNewline()
                }

                val ktlintVersion = kameKamoProperties.versions.ktlint
                if (ktlintVersion != null) {
                    val ktlintUserData = mapOf("indent_size" to "2", "continuation_indent_size" to "2")
                    kotlin { ktlint(ktlintVersion).userData(ktlintUserData) }
                    kotlinGradle { ktlint(ktlintVersion).userData(ktlintUserData) }
                }

                val ktfmtVersion = kameKamoProperties.versions.ktfmt
                if (ktfmtVersion != null) {
                    kotlin { ktfmt(ktfmtVersion).googleStyle() }
                    kotlinGradle { ktfmt(ktfmtVersion).googleStyle() }
                }

                if (ktlintVersion != null || ktfmtVersion != null) {
                    check(!(ktlintVersion != null && ktfmtVersion != null)) {
                        "Cannot have both ktlint and ktfmt enabled, please pick one and remove the other from the version catalog!"
                    }
                    kotlin {
                        target("src/**/*.kt")
                        trimTrailingWhitespace()
                        endWithNewline()
                    }
                    kotlinGradle {
                        target("src/**/*.kts")
                        trimTrailingWhitespace()
                        endWithNewline()
                    }
                }

                kameKamoProperties.versions.gjf?.let { gjfVersion ->
                    java {
                        target("src/**/*.java")
                        googleJavaFormat(gjfVersion).reflowLongStrings()
                        trimTrailingWhitespace()
                        endWithNewline()
                    }
                }
                kameKamoProperties.versions.gson?.let { gsonVersion ->
                    json {
                        target("src/**/*.json", "*.json")
                        target("*.json")
                        gson().indentWithSpaces(2).version(gsonVersion)
                    }
                }
            }
            // Pre-declare in root project for better performance and also to work around
            // https://github.com/diffplug/spotless/issues/1213
            configure<SpotlessExtension> {
                spotlessFormatters()
                if (isRootProject) {
                    predeclareDeps()
                }
            }
            if (isRootProject) {
                configure<SpotlessExtensionPredeclare> { spotlessFormatters() }
            }
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun Project.configureClasspath(kameKamoProperties: KameKamoProperties) {
        val hamcrestDep = kameKamoProperties.versions.catalog.findLibrary("testing-hamcrest")
        val checkerDep = kameKamoProperties.versions.catalog.findLibrary("checkerFrameworkQual")
        val isTestProject = "test" in name || "test" in path
        configurations.configureEach {
            configureConfigurationResolutionStrategies(this, isTestProject, hamcrestDep, checkerDep)
        }

        val enableMavenLocal = kameKamoProperties.enableMavenLocal
        val enableSnapshots = kameKamoProperties.enableSnapshots
        // Check if we're running a `dependencyUpdates` task is running by looking for its `-Drevision=`
        // property, which this
        // breaks otherwise.
        val dependencyUpdatesRevision = providers.systemProperty("revision").isPresent
        if (!enableMavenLocal && !enableSnapshots && !dependencyUpdatesRevision) {
            configurations.configureEach { resolutionStrategy { failOnNonReproducibleResolution() } }
        }
    }

    private fun configureConfigurationResolutionStrategies(
        configuration: Configuration,
        isTestProject: Boolean,
        hamcrestDepOptional: Optional<Provider<MinimalExternalModuleDependency>>,
        checkerDepOptional: Optional<Provider<MinimalExternalModuleDependency>>
    ) {
        val configurationName = configuration.name
        val lowercaseName = configurationName.toLowerCase(Locale.US)
        // Hamcrest switched to a single jar starting in 2.1, so exclude the old ones but replace the
        // core one with the
        // new one (as cover for transitive users like junit).
        if (hamcrestDepOptional.isPresent && (isTestProject || "test" in lowercaseName)) {
            val hamcrestDepProvider = hamcrestDepOptional.get()
            if (hamcrestDepProvider.isPresent) {
                val hamcrestDep = hamcrestDepProvider.get().toString()
                configuration.resolutionStrategy {
                    dependencySubstitution {
                        substitute(module("org.hamcrest:hamcrest-core")).apply {
                            using(module(hamcrestDep))
                            because("hamcrest 2.1 removed the core/integration/library artifacts")
                        }
                        substitute(module("org.hamcrest:hamcrest-integration")).apply {
                            using(module(hamcrestDep))
                            because("hamcrest 2.1 removed the core/integration/library artifacts")
                        }
                        substitute(module("org.hamcrest:hamcrest-library")).apply {
                            using(module(hamcrestDep))
                            because("hamcrest 2.1 removed the core/integration/library artifacts")
                        }
                    }
                }
            }
        }

        configuration.resolutionStrategy {
            dependencySubstitution {
                // Checker Framework dependencies are all over the place. We clean up some old
                // ones and force to a consolidated version.
                checkerDepOptional.ifPresent { checkerDep ->
                    substitute(module("org.checkerframework:checker-compat-qual")).apply {
                        using(module(checkerDep.get().toString()))
                        because("checker-compat-qual no longer exists and was replaced with just checker-qual")
                    }
                }
            }
            checkerDepOptional.ifPresent { checkerDep ->
                val checkerDepVersion = checkerDep.get().versionConstraint.toString()
                eachDependency {
                    if (requested.group == "org.checkerframework") {
                        useVersion(checkerDepVersion)
                        because(
                            "Checker Framework dependencies are all over the place, so we force their version to a " +
                                "single latest one"
                        )
                    }
                }
            }
        }
    }

    @Suppress("LongMethod")
    private fun Project.configureTests(kameKamoProperties: KameKamoProperties) {
        val maxParallel = max(Runtime.getRuntime().availableProcessors() / 2, 1)
        // Create "ciUnitTest" tasks in all subprojects
        apply(plugin = "kamekamo.unit-test")

        // Unit test task configuration
        tasks.withType<Test>().configureEach {
            // Run unit tests in parallel if multiple CPUs are available. Use at most half the available
            // CPUs.
            maxParallelForks = maxParallel

            // Denote flaky failures as <flakyFailure> instead of <failure> in JUnit test XML files
            reports.junitXml.mergeReruns.set(true)

            if (kameKamoProperties.testVerboseLogging) {
                // Add additional logging on Jenkins to help debug hanging or OOM-ing unit tests.
                testLogging {
                    showStandardStreams = true
                    showStackTraces = true

                    // Set options for log level LIFECYCLE
                    events("started", "passed", "failed", "skipped")
                    setExceptionFormat("short")

                    // Setting this to 0 (the default is 2) will display the test executor that each test is
                    // running on.
                    displayGranularity = 0
                }
            }

            if (isCi) {
                //
                // Trying to improve memory management on CI
                // https://github.com/tinyspeck/slack-android-ng/issues/22005
                //

                // Improve JVM memory behavior in tests to avoid OOMs
                // https://www.royvanrijn.com/blog/2018/05/java-and-docker-memory-limits/
                if (JavaVersion.current().isJava10Compatible) {
                    jvmArgs("-XX:+UseContainerSupport")
                } else {
                    jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap")
                }

                val workspaceDir =
                    when {
                        isJenkins -> synchronousEnvProperty("WORKSPACE")
                        isActionsCi -> synchronousEnvProperty("GITHUB_WORKSPACE")
                        else -> rootProject.projectDir.absolutePath
                    }

                // helps when tests leak memory
                @Suppress("MagicNumber") setForkEvery(1000L)

                // Cap JVM args per test
                minHeapSize = "128m"
                maxHeapSize = "1g"
                jvmArgs(
                    "-XX:+HeapDumpOnOutOfMemoryError",
                    "-XX:+UseGCOverheadLimit",
                    "-XX:GCHeapFreeLimit=10",
                    "-XX:GCTimeLimit=20",
                    "-XX:HeapDumpPath=$workspaceDir/fs_oom_err_pid<pid>.hprof",
                    "-XX:OnError=cat $workspaceDir/fs_oom.log",
                    "-XX:OnOutOfMemoryError=cat $workspaceDir/fs_oom_err_pid<pid>.hprof",
                    "-Xss1m" // Stack size
                )
            }
        }

        if (isCi) {
            pluginManager.withPlugin("org.gradle.test-retry") {
                tasks.withType<Test>().configureEach {
                    @Suppress("MagicNumber")
                    retry {
                        failOnPassedAfterRetry.set(false)
                        maxFailures.set(20)
                        maxRetries.set(1)
                    }
                }
            }
        }
    }
}