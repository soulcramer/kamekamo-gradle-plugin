package kamekamo.gradle.tasks.robolectric

internal data class DependencyJar(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null
) {
    val name: String = "$artifactId-$version.jar"
}