package kamekamo.gradle.dependencies

public abstract class DependencySet : DependencyCollection {
    internal fun artifact(
        group: String,
        artifact: String? = null,
        gradleProperty: String? = null
    ): DependencyDelegate {
        return DependencyDelegate(
            group = group,
            artifact = artifact,
            gradleProperty = gradleProperty?.let { "${DependencyCollection.GRADLE_PROPERTY_PREFIX}$it" }
        )
    }

    internal fun artifactWithExtension(
        group: String,
        artifact: String,
        ext: String,
        gradleProperty: String? = null
    ): DependencyDelegate {
        return DependencyDelegate(
            group = group,
            artifact = artifact,
            ext = ext,
            gradleProperty = gradleProperty?.let { "${DependencyCollection.GRADLE_PROPERTY_PREFIX}$it" }
        )
    }
}