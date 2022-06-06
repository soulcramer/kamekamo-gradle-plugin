package kamekamo.gradle

import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.provider.Provider
import java.util.Optional

// TODO generate something to map these in the future? Or with reflection?
internal class KameKamoVersions(val catalog: VersionCatalog) {
    val agp: String?
        get() = getOptionalValue("agp").orElse(null)
    val composeCompiler: String?
        get() = getOptionalValue("compose-compiler").orElse(null)
    val detekt: String?
        get() = getOptionalValue("detekt").orElse(null)
    val gjf: String?
        get() = getOptionalValue("google-java-format").orElse(null)
    val gson: String?
        get() = getOptionalValue("gson").orElse(null)
    val ktlint: String?
        get() = getOptionalValue("ktlint").orElse(null)
    val ktfmt: String?
        get() = getOptionalValue("ktfmt").orElse(null)
    val objenesis: String?
        get() = getOptionalValue("objenesis").orElse(null)
    val jdk: Int
        get() = getValue("jdk").toInt()
    val jvmTarget: Int
        get() = getOptionalValue("jvmTarget").map { it.toInt() }.orElse(11)

    val bundles = Bundles()

    inner class Bundles {
        val commonAnnotations: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
            catalog.findBundle("common-annotations")
        }
        val commonLint: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
            catalog.findBundle("common-lint")
        }
        val commonTest: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
            catalog.findBundle("common-test")
        }
    }

    internal fun getValue(key: String): String {
        return getOptionalValue(key).orElseThrow {
            IllegalStateException("No catalog version found for ${tomlKey(key)}")
        }
    }

    internal fun getOptionalValue(key: String): Optional<String> {
        val tomlKey = tomlKey(key)
        return catalog.findVersion(tomlKey).map { it.toString() }
    }

    internal val boms: Set<Provider<MinimalExternalModuleDependency>> by lazy {
        catalog.libraryAliases
            .filter {
                // Library alias is as it appears in usage, not as it appears in the toml
                // So, "coroutines-bom" in the toml is "coroutines.bom" in usage
                it.endsWith(".bom")
            }
            .mapTo(LinkedHashSet()) { catalog.findLibrary(it).get() }
    }
}