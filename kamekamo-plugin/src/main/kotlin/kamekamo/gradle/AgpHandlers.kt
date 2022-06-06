package kamekamo.gradle

import kamekamo.gradle.agp.AgpHandler
import kamekamo.gradle.agp.AgpHandlerFactory
import kamekamo.gradle.agp.VersionNumber
import java.util.ServiceLoader

internal object AgpHandlers {
    fun createHandler(): AgpHandler {
        /** Load handlers and pick the highest compatible version (by [AgpHandlerFactory.minVersion]) */
        val targetFactory =
            ServiceLoader.load(AgpHandlerFactory::class.java)
                .iterator()
                .asSequence()
                .mapNotNull { factory ->
                    // Filter out any factories that can't compute the AGP version, as
                    // they're _definitely_ not compatible
                    try {
                        FactoryData(VersionNumber.parse(factory.currentVersion()), factory)
                    } catch (t: Throwable) {
                        null
                    }
                }
                .filter { (agpVersion, factory) -> agpVersion.baseVersion >= factory.minVersion }
                .maxByOrNull { (_, factory) -> factory.minVersion }
                ?.factory
                ?: error("Unrecognized AGP version!")

        return targetFactory.create()
    }
}

private data class FactoryData(val agpVersion: VersionNumber, val factory: AgpHandlerFactory)