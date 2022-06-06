package kamekamo.gradle

import kamekamo.gradle.KameKamoTools.Companion.SERVICE_NAME
import kamekamo.gradle.KameKamoTools.Parameters
import kamekamo.gradle.agp.AgpHandler
import kamekamo.gradle.util.Thermals
import kamekamo.gradle.util.ThermalsWatcher
import kamekamo.gradle.util.mapToBoolean
import okhttp3.OkHttpClient
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.registerIfAbsent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.reflect.KClass

/** Misc tools for Kamekamo Gradle projects, usable in tasks as a [BuildService] too. */
public abstract class KameKamoTools @Inject constructor(providers: ProviderFactory) :
    BuildService<Parameters>, AutoCloseable {

    public val agpHandler: AgpHandler by lazy { AgpHandlers.createHandler() }

    // I really really wish we could do this the "correct" way but Gradle is problematic with its
    // inconsistent expectations of Serializability. Specifically - it seems that `@Nested` does not
    // work for BuildService parameters
    public lateinit var globalConfig: GlobalConfig

    private val logger = Logging.getLogger("KamekamoTools")
    private val extensions = ConcurrentHashMap<KClass<out KamekamoToolsExtension>, KamekamoToolsExtension>()

    public lateinit var okHttpClient: Lazy<OkHttpClient>

    private var thermalsReporter: ThermalsReporter? = null
    private val logThermals =
        OperatingSystem.current().isMacOsX &&
            !parameters.cleanRequested.get() &&
            providers.gradleProperty(KameKamoProperties.LOG_THERMALS).mapToBoolean().getOrElse(false)

    private val thermalsWatcher = if (logThermals) ThermalsWatcher(::thermalsFile) else null
    private var thermalsAtClose: Thermals? = null

    /** Returns the current or latest captured thermals log. */
    public val thermals: Thermals?
        get() {
            return thermalsAtClose ?: peekThermals()
        }

    init {
        thermalsWatcher?.start()
    }

    public fun registerExtension(extension: KamekamoToolsExtension) {
        val dependencies =
            object : KamekamoToolsDependencies {
                override val okHttpClient: Lazy<OkHttpClient>
                    get() = this@KameKamoTools.okHttpClient
            }
        val previous = extensions.put(extension::class, extension)
        check(previous == null) { "Duplicate extension registered for ${extension::class.simpleName}" }
        extension.bind(dependencies)
        if (extension is ThermalsReporter) {
            thermalsReporter = extension
        }
    }

    private fun thermalsFile(): File {
        return parameters.thermalsOutputFile.asFile.get().apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
            }
        }
    }

    private fun peekThermals(): Thermals? {
        return thermalsWatcher?.peek()
    }

    override fun close() {
        // Close thermals process and save off its current value
        thermalsAtClose = thermalsWatcher?.stop()
        try {
            if (!parameters.offline.get()) {
                thermalsAtClose?.let { thermalsReporter?.reportThermals(it) }
            }
        } catch (t: Throwable) {
            logger.error("Failed to report thermals", t)
        } finally {
            if (okHttpClient.isInitialized()) {
                with(okHttpClient.value) {
                    dispatcher.executorService.shutdown()
                    connectionPool.evictAll()
                    cache?.close()
                }
            }
        }
    }

    internal companion object {
        internal const val SERVICE_NAME = "kamekamo-tools"

        internal fun register(
            project: Project,
            okHttpClient: Lazy<OkHttpClient>,
        ): Provider<KameKamoTools> {
            return project.gradle.sharedServices
                .registerIfAbsent(SERVICE_NAME, KameKamoTools::class) {
                    parameters.thermalsOutputFile.set(
                        project.layout.buildDirectory.file("outputs/logs/last-build-thermals.log")
                    )
                    parameters.offline.set(project.gradle.startParameter.isOffline)
                    parameters.cleanRequested.set(
                        project.gradle.startParameter.taskNames.any { it.equals("clean", ignoreCase = true) }
                    )
                }
                .apply {
                    get().apply {
                        globalConfig = GlobalConfig(project)
                        this.okHttpClient = okHttpClient
                    }
                }
        }
    }

    public interface Parameters : BuildServiceParameters {
        /** An output file that the thermals process (continuously) writes to during the build. */
        public val thermalsOutputFile: RegularFileProperty
        public val offline: Property<Boolean>
        public val cleanRequested: Property<Boolean>
    }
}

public interface ThermalsReporter {
    public fun reportThermals(thermals: Thermals)
}

public interface KamekamoToolsDependencies {
    public val okHttpClient: Lazy<OkHttpClient>
}

/** An extension for KamekamoTools. */
public interface KamekamoToolsExtension {
    public fun bind(sharedDependencies: KamekamoToolsDependencies)
}

@Suppress("UNCHECKED_CAST")
public fun Project.kamekamoTools(): KameKamoTools {
    return kamekamoToolsProvider().get()
}

@Suppress("UNCHECKED_CAST")
public fun Project.kamekamoToolsProvider(): Provider<KameKamoTools> {
    return (project.gradle.sharedServices.registrations.getByName(SERVICE_NAME) as
        BuildServiceRegistration<KameKamoTools, Parameters>)
        .service
}