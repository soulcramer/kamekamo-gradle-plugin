@file:JvmName("KotlinBuildConfig")

package kamekamo.gradle.dependencies

internal object KotlinBuildConfig {
  const val kotlin = "$kotlinVersion"
  const val kotlinJvmTarget = "$kotlinJvmTarget"
  val kotlinCompilerArgs = listOf($kotlinCompilerArgs)
}