package kamekamo.gradle

import kamekamo.gradle.util.booleanProperty
import kamekamo.gradle.util.getOrCreateExtra
import kamekamo.gradle.util.intProperty
import kamekamo.gradle.util.optionalStringProperty
import kamekamo.gradle.util.safeProperty
import org.gradle.api.Project
import java.io.File

/**
 * (Mostly Gradle) properties for configuration of KamekamoPlugin.
 *
 * Order attempted as described by [safeProperty].
 */
public class KameKamoProperties private constructor(private val project: Project) {

    private fun presenceProperty(key: String): Boolean = optionalStringProperty(key) != null

    private fun fileProperty(key: String): File? = optionalStringProperty(key)?.let(project::file)

    private fun intProperty(key: String, defaultValue: Int = -1): Int =
        project.intProperty(key, defaultValue = defaultValue)

    private fun booleanProperty(key: String, defaultValue: Boolean = false): Boolean =
        project.booleanProperty(key, defaultValue = defaultValue)

    private fun stringProperty(key: String): String =
        optionalStringProperty(key)
            ?: error("No property for $key found and no default value was provided.")

    private fun stringProperty(key: String, defaultValue: String): String =
        optionalStringProperty(key, defaultValue)!!

    private fun optionalStringProperty(key: String, defaultValue: String? = null): String? =
        project.optionalStringProperty(key, defaultValue = defaultValue)

    internal val versions: KameKamoVersions by lazy {
        project.rootProject.getOrCreateExtra("kamekamo-versions") {
            KameKamoVersions(project.rootProject.getVersionsCatalog(this))
        }
    }

    /** Indicates that this android library project has variants. Flag-only, value is ignored. */
    public val libraryWithVariants: Boolean
        get() = booleanProperty("kamekamo.gradle.config.libraryWithVariants")

    /**
     * Indicates that the gradle versions plugin should allow unstable versions. By default unstable
     * versions are excluded due to the frequent androidx alpha/beta/rc cycle noise. Flag-only, value
     * is ignored.
     */
    public val versionsPluginAllowUnstable: Boolean
        get() = booleanProperty("kamekamo.gradle.config.versionsPluginAllowUnstable")

    /** Opt-out flag to skip the androidx dependency check. Should only be used for debugging. */
    public val skipAndroidxCheck: Boolean
        get() = booleanProperty("kamekamo.gradle.skipAndroidXCheck")

    /** Opt-in flag to enable snapshots repos, used for the dependencies build shadow job. */
    public val enableSnapshots: Boolean
        get() = booleanProperty("kamekamo.gradle.config.enableSnapshots")

    /** Opt-in flag to enable mavenLocal repos, used for local testing. */
    public val enableMavenLocal: Boolean
        get() = booleanProperty("kamekamo.gradle.config.enableMavenLocal")

    /**
     * Flag to indicate that that this project should have no api dependencies, such as if it's solely
     * an annotation processor.
     */
    public val rakeNoApi: Boolean
        get() = booleanProperty("kamekamo.gradle.config.rake.noapi")

    /**
     * Flag to enable the Gradle Dependency Analysis Plugin, which is disabled by default due to
     * https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/204
     */
    public val enableAnalysisPlugin: Boolean
        get() = booleanProperty("kamekamo.gradle.config.enableAnalysisPlugin")

    /**
     * Flag to indicate this project should be exempted from platforms, usually platform projects
     * themselves.
     */
    public val noPlatform: Boolean
        get() = booleanProperty("kamekamo.gradle.config.noPlatform")

    /** Property corresponding to the supported languages in GA builds */
    public val supportedLanguages: String
        get() = stringProperty("kamekamo.supportedLanguages")

    /** Property corresponding to the supported languages in Internal builds */
    public val supportedLanguagesInternal: String
        get() = stringProperty("kamekamo.supportedLanguagesInternal")

    /** Property corresponding to the supported languages in Beta builds */
    public val supportedLanguagesBeta: String
        get() = stringProperty("kamekamo.supportedLanguagesBeta")

    /**
     * Property corresponding to the file path of a custom versions.json file for use with
     * dependencies shadow jobs.
     */
    public val versionsJson: File?
        get() = fileProperty("kamekamo.versionsJson")

    /** Toggle for enabling Jetpack Compose in Android subprojects. */
    public val enableCompose: Boolean
        get() =
            booleanProperty("kamekamo.enableCompose")

    /**
     * When this property is present, the "internalRelease" build variant will have an application id
     * of "com.Kamekamo.prototype", instead of "com.Kamekamo.internal".
     *
     * We build and distribute "prototype" builds that are equivalent to the "internalRelease" build
     * variants, except with a different application id so they can be installed side-by-side. To
     * avoid adding a new flavor & flavor dimension (or other somewhat hacky solutions like sharing
     * source sets), we swap the application id suffix at configuration time.
     */
    public val usePrototypeAppId: Boolean
        get() = presenceProperty("kamekamo.usePrototypeAppId")

    /**
     * Property corresponding to the SDK versions we test in Robolectric tests. Its value should be a
     * comma-separated list of SDK ints to download.
     */
    public val robolectricTestSdks: List<Int>
        get() =
            stringProperty("kamekamo.robolectricTestSdks").splitToSequence(",").map { it.toInt() }.toList()

    /** Property corresponding to the preinstrumented jars version (the `-i2` suffix in jars). */
    public val robolectricIVersion: Int
        get() = intProperty("kamekamo.robolectricIVersion")

    /** Opt out for -Werror, should only be used for prototype projects. */
    public val allowWarnings: Boolean
        get() = booleanProperty("kamekamo.allowWarnings")

    /**
     * Anvil generator projects that should always be included when Anvil is enabled.
     *
     * This should be semicolon-delimited Gradle project paths.
     */
    public val anvilGeneratorProjects: String?
        get() = optionalStringProperty("kamekamo.anvil.generatorProjects")

    /**
     * Anvil runtime projects that should always be included when Anvil is enabled.
     *
     * This should be semicolon-delimited Gradle project paths.
     */
    public val anvilRuntimeProjects: String?
        get() = optionalStringProperty("kamekamo.anvil.runtimeProjects")

    /** Log Kamekamo extension configuration state verbosely. */
    public val kamekamoExtensionVerbose: Boolean
        get() = booleanProperty("kamekamo.extension.verbose")

    /**
     * Flag for Error-Prone auto-patching. Enable when running an auto-patch of EP, such as when it's
     * being introduced to a new module or upgrading EP itself.
     */
    public val errorProneAutoPatch: Boolean
        get() = booleanProperty("kamekamo.epAutoPatch")

    /**
     * Error-Prone checks that should be considered errors.
     *
     * This should be colon-delimited string.
     *
     * Example: "AnnotationMirrorToString:AutoValueSubclassLeaked"
     */
    public val errorProneCheckNamesAsErrors: String?
        get() = optionalStringProperty("kamekamo.epCheckNamesAsErrors")

    /**
     * Flag for Nullaway baselining. When enabled along with [errorProneAutoPatch], existing
     * nullability issues will be baselined with a `castToNonNull` call to wrap it.
     */
    public val nullawayBaseline: Boolean
        get() = booleanProperty("kamekamo.nullaway.baseline")

    /**
     * Ndk version to use for android projects.
     *
     * Latest versions can be found at https://developer.android.com/ndk/downloads
     */
    public val ndkVersion: String?
        get() = optionalStringProperty("kamekamo.ndkVersion")

    /** Flag to enable verbose logging in unit tests. */
    public val testVerboseLogging: Boolean
        get() =
            booleanProperty(
                "kamekamo.test.verboseLogging",
            )

    /**
     * Flag to enable kapt in tests. By default these are disabled due to this undesirable (but
     * surprisingly intented) behavior of running kapt + stub generation even if no processors are
     * present.
     *
     * See https://youtrack.jetbrains.com/issue/KT-29481#focus=Comments-27-4651462.0-0
     */
    public val enableKaptInTests: Boolean
        get() = booleanProperty("kamekamo.enabled-kapt-in-tests")

    /** Flag to enable errors only in lint checks. */
    public val lintErrorsOnly: Boolean
        get() = booleanProperty("kamekamo.lint.errors-only")

    /** Flag to indicate that we're currently running a baseline update. */
    public val lintUpdateBaselines: Boolean
        get() = booleanProperty("kamekamo.lint.update-baselines")

    /** Flag to enable/disable KSP. */
    public val allowKsp: Boolean
        get() = booleanProperty("kamekamo.allow-ksp")

    /** Flag to enable/disable Moshi-IR. */
    public val allowMoshiIr: Boolean
        get() = booleanProperty("kamekamo.allow-moshi-ir")

    /** Variants that should be disabled in a given subproject. */
    public val disabledVariants: String?
        get() = optionalStringProperty("kamekamo.disabledVariants")

    /**
     * The Kamekamo-specific kotlin.daemon.jvmargs computed by bootstrap.
     *
     * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute other
     * projects.
     */
    public val kotlinDaemonArgs: String
        get() = stringProperty(KOTLIN_DAEMON_ARGS_KEY, defaultValue = "")

    /**
     * Flag to enable ciUnitTest on this project. Default is true.
     *
     * When enabled, a task named "ciUnitTest" will be created in this project, which will depend on
     * the unit test task for a single build variant (e.g. "testReleaseUnitTest").
     */
    public val ciUnitTestEnabled: Boolean
        get() = booleanProperty("kamekamo.ci-unit-test.enable", defaultValue = true)

    /** CI unit test variant (Android only). Defaults to `release`. */
    public val ciUnitTestVariant: String
        get() = stringProperty("kamekamo.ci-unit-test.variant", "release")

    /**
     * Location for robolectric-core to be referenced by app. Temporary till we have a better solution
     * for "always add these" type of deps.
     *
     * Should be `:path:to:robolectric-core` format
     */
    public val robolectricCoreProject: Project
        get() = project.project(stringProperty("kamekamo.location.robolectric-core"))

    /**
     * Gradle path to a platform project to be referenced by other projects.
     *
     * Should be `:path:to:kamekamo-platform` format
     *
     * @see Platforms
     */
    public val platformProjectPath: String?
        get() = optionalStringProperty("kamekamo.location.kamekamo-platform")

    /**
     * Opt-in path for commit hooks in the consuming repo that should be automatically installed
     * automatically. This is passed into [org.gradle.api.Project.file] from the root project.
     *
     * Corresponds to git's `core.hooksPath`.
     */
    public val gitHooksFile: File?
        get() = fileProperty("kamekamo.git.hooksPath")

    /**
     * Opt-in path for a pre-commit hook in the consuming repo that should be automatically installed
     * automatically. This is passed into [org.gradle.api.Project.file] from the root project.
     *
     * Corresponds to git's `blame.ignoreRevsFile`.
     */
    public val gitIgnoreRevsFile: File?
        get() = fileProperty("kamekamo.git.ignoreRevsFile")

    /* Controls for Java/JVM/JDK versions uses in compilations and execution of tests. */

    /** Flag to enable strict JDK mode, forcing some things like JAVA_HOME. */
    public val strictJdk: Boolean
        get() = booleanProperty("kamekamoToolchainsStrict", defaultValue = true)

    /** The JDK version to use for compilations. */
    public val jdkVersion: Int
        get() = versions.jdk

    /** The JDK runtime to target for compilations. */
    public val jvmTarget: Int
        get() = versions.jvmTarget

    /** Android cache fix plugin. */
    public val enableAndroidCacheFix: Boolean = booleanProperty("kamekamo.plugins.android-cache-fix")

    /* Controls for auto-applied plugins. */
    public val autoApplyTestRetry: Boolean
        get() = booleanProperty("kamekamo.auto-apply.test-retry", defaultValue = true)
    public val autoApplySpotless: Boolean
        get() = booleanProperty("kamekamo.auto-apply.spotless", defaultValue = true)
    public val autoApplyDetekt: Boolean
        get() = booleanProperty("kamekamo.auto-apply.detekt", defaultValue = true)
    public val autoApplyNullaway: Boolean
        get() = booleanProperty("kamekamo.auto-apply.nullaway", defaultValue = true)
    public val autoApplyCacheFix: Boolean
        get() = booleanProperty("kamekamo.auto-apply.cache-fix", defaultValue = true)

    /* Detekt configs. */
    /** Detekt config files, evaluated from rootProject.file(...). */
    public val detektConfigs: List<String>?
        get() = optionalStringProperty("kamekamo.detekt.configs")?.split(",")

    /** Detekt baseline file, evaluated from rootProject.file(...). */
    public val detektBaseline: String?
        get() = optionalStringProperty("kamekamo.detekt.baseline")

    /**
     * Global control for enabling stricter validation of projects, such as ensuring Kotlin projects
     * have at least one `.kt` source file.
     *
     * Note that these are expected to be slow and not used anywhere outside of debugging or CI.
     *
     * Granular controls should depend on this check + include their own opt-out check as-needed.
     */
    public val strictMode: Boolean
        get() = booleanProperty("kamekamo.strict", defaultValue = false)

    /** Specific toggle for validating the presence of `.kt` files in Kotlin projects. */
    public val strictValidateKtFilePresence: Boolean
        get() = booleanProperty("kamekamo.strict.validateKtFiles", defaultValue = true)

    /** Specified the name of the versions catalog to use for bom management. */
    public val versionCatalogName: String
        get() = stringProperty("kamekamo.catalog", defaultValue = "libs")

    internal fun requireAndroidSdkProperties(): AndroidSdkProperties {
        val compileSdk = compileSdkVersion ?: error("kamekamo.compileSdkVersion not set")
        val minSdk = minSdkVersion?.toInt() ?: error("kamekamo.minSdkVersion not set")
        val targetSdk = targetSdkVersion?.toInt() ?: error("kamekamo.targetSdkVersion not set")
        return AndroidSdkProperties(compileSdk, minSdk, targetSdk)
    }

    internal data class AndroidSdkProperties(
        val compileSdk: String,
        val minSdk: Int,
        val targetSdk: Int
    )

    public val compileSdkVersion: String?
        get() = optionalStringProperty("kamekamo.compileSdkVersion")

    public fun latestCompileSdkWithSources(defaultValue: Int): Int =
        intProperty("kamekamo.latestCompileSdkWithSources", defaultValue = defaultValue)

    private val minSdkVersion: String?
        get() = optionalStringProperty("kamekamo.minSdkVersion")

    private val targetSdkVersion: String?
        get() = optionalStringProperty("kamekamo.targetSdkVersion")

    public companion object {
        /**
         * The Kamekamo-specific kotlin.daemon.jvmargs computed by bootstrap.
         *
         * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute
         * other projects.
         */
        public const val KOTLIN_DAEMON_ARGS_KEY: String = "kamekamo.kotlin.daemon.jvmargs"

        /** Experimental flag to enable logging thermal throttling on macOS devices. */
        // Key-only because it's used in a task init without a project instance
        public const val LOG_THERMALS: String = "kamekamo.log-thermals"

        /** Minimum xmx value for the Gradle daemon. Value is an integer and unit is gigabytes. */
        // Key-only because it's used in a task init without a project instance
        public const val MIN_GRADLE_XMX: String = "kamekamo.bootstrap.minGradleXmx"

        private const val CACHED_PROVIDER_EXT_NAME = "kamekamo.properties.provider"

        public operator fun invoke(project: Project): KameKamoProperties =
            project.getOrCreateExtra(CACHED_PROVIDER_EXT_NAME, ::KameKamoProperties)
    }
}