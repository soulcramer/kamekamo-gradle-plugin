import kamekamo.gradle.AndroidHandler
import kamekamo.gradle.KameKamoExtension
import kamekamo.gradle.KamekamoAndroidAppExtension
import kamekamo.gradle.KamekamoAndroidLibraryExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType

/*
 * This file exists because of a strange behavior in Gradle. If you want to access buildSrc code from the root project's
 * buildscript block, it cannot directly access elements that contain a package name. This is really weird, and
 * hopefully a bug.
 *
 * TODO(zsweers) link the bug!
 */

/**
 * Common entry point for configuring kamekamo-specific bits of projects.
 *
 * ```
 * kamekamo {
 *   android {
 *     library {
 *       // ...
 *     }
 *   }
 * }
 * ```
 */
public fun Project.kamekamo(body: KameKamoExtension.() -> Unit) {
    extensions.findByType<KameKamoExtension>()?.let(body) ?: error("KameKamo extension not found.")
}

/**
 * Common entry point for configuring kamekamo-android-specific bits of projects.
 *
 * ```
 * kamekamoAndroid {
 *   library {
 *     // ...
 *   }
 * }
 * ```
 */
public fun Project.kamekamoAndroid(action: Action<AndroidHandler>) {
    kamekamo { android(action) }
}

/**
 * Common entry point for configuring kamekamo-android-library-specific bits of projects.
 *
 * ```
 * androidLibrary {
 *   // ...
 * }
 * ```
 */
public fun Project.kamekamoAndroidLibrary(action: Action<KamekamoAndroidLibraryExtension>) {
    kamekamo { android { library(action) } }
}

/**
 * Common entry point for configuring kamekamo-android-library-specific bits of projects.
 *
 * ```
 * androidApp {
 *   // ...
 * }
 * ```
 */
public fun Project.kamekamoAndroidApp(action: Action<KamekamoAndroidAppExtension>) {
    kamekamo { android { app(action) } }
}