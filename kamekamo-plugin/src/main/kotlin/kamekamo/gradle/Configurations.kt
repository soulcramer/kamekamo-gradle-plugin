package kamekamo.gradle

internal object Configurations {
    const val COMPILE_ONLY = "compileOnly"
    const val CORE_LIBRARY_DESUGARING = "coreLibraryDesugaring"
    const val KAPT = "kapt"
    const val KSP = "ksp"

    object ErrorProne {
        const val ERROR_PRONE = "errorprone"
        const val ERROR_PRONE_JAVAC = "errorproneJavac"
    }

    object Groups {
        val APT: Set<String> = setOf(KAPT, "annotationProcessor")
        val ERROR_PRONE: Set<String> = setOf(ErrorProne.ERROR_PRONE, ErrorProne.ERROR_PRONE_JAVAC)

        /** Configurations that never run on Android and are also not visible to implementation. */
        val JRE: Set<String> = ERROR_PRONE + APT
        val RUNTIME: Set<String> = setOf("api", "compile", "implementation", "runtimeOnly")

        @Suppress("SpreadOperator")
        val PLATFORM =
            setOf(
                *APT.toTypedArray(),
                *ERROR_PRONE.toTypedArray(),
                *RUNTIME.toTypedArray(),
                COMPILE_ONLY,
                CORE_LIBRARY_DESUGARING,
                "androidTestUtil",
                "lintChecks",
                "lintRelease"
            )
    }
}