package kamekamo.gradle.tasks.robolectric

/**
 * Represents a unique build of the Android SDK.
 *
 * Adapted from Robolectric's implementation.
 */
internal sealed class Sdk : Comparable<Sdk> {

    /**
     * Returns the [Android API level](https://source.android.com/setup/start/build-numbers) for this
     * SDK.
     *
     * It must match the version reported by `android.os.Build.VERSION.SDK_INT` provided within.
     */
    abstract val apiLevel: Int

    /**
     * Returns the [Android Version](https://source.android.com/setup/start/build-numbers) for this
     * SDK.
     *
     * It should match the version reported by `android.os.Build.VERSION.RELEASE` provided within.
     *
     * If this is an expensive operation, the implementation should cache the return value.
     */
    abstract val androidVersion: String?

    /**
     * Returns the Android codename for this SDK.
     *
     * It should match the version reported by `android.os.Build.VERSION.CODENAME` provided within.
     *
     * If this is an expensive operation, the implementation should cache the return value.
     */
    abstract val androidCodeName: String?

    /** The actual dependency jar to fetch. */
    abstract fun dependencyJar(): DependencyJar

    /** Instances of `Sdk` are ordered by the API level they implement. */
    override fun compareTo(other: Sdk): Int {
        return apiLevel - other.apiLevel
    }
}

/**
 * Represents an Android SDK stored at Maven Central.
 *
 * Adapted from Robolectric's implementation.
 */
internal data class DefaultSdk(
    override val apiLevel: Int,
    override val androidVersion: String,
    private val robolectricVersion: String,
    override val androidCodeName: String,
    private val requiredJavaVersion: Int,
    private val iVersion: Int
) : Sdk() {
    override fun dependencyJar(): DependencyJar {
        val version =
            listOf(androidVersion, "robolectric", robolectricVersion, "i$iVersion").joinToString("-")
        return DependencyJar("org.robolectric", "android-all-instrumented", version, null)
    }
}