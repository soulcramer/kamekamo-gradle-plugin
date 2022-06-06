package kamekamo.gradle

import kamekamo.executeBlocking
import kamekamo.executeBlockingWithResult
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

private val GITHUB_ORIGIN_REGEX = Regex("(.*)github\\.com[/|:](.*)")

internal fun Project.configureBuildScanMetadata(scanApi: ScanApi) {
    if (invokedFromIde) {
        scanApi.tag("ide")
        if (isSyncing) {
            scanApi.tag("studio-sync")
        }
    }
    scanApi.value("java-version", JavaVersion.current().toString())
    scanApi.value("Architecture", System.getProperty("os.arch"))
    val isCi = isCi

    scanApi.tagOs()
    scanApi.tagIde(project, isCi)
    scanApi.tagCiOrLocal(isCi)
    if (isCi) {
        scanApi.addCiMetadata(project)
    }
    scanApi.addGitMetadata(project)
    scanApi.addTestParallelization(project)
    scanApi.addTestSystemProperties(project)
    scanApi.addGradleEnterpriseVersion()
}

private fun ScanApi.tagOs() {
    tag(System.getProperty("os.name"))
}

private fun ScanApi.tagIde(project: Project, isCi: Boolean) {
    if (project.hasProperty("android.injected.invoked.from.ide")) {
        tag("Android Studio")
        project.findProperty("android.injected.studio.version")?.let {
            value("Android Studio version", it.toString())
        }
    } else if (System.getProperty("idea.version") != null) {
        tag("IntelliJ IDEA")
    } else if (!isCi) {
        tag("Cmd Line")
    }
}

private fun ScanApi.tagCiOrLocal(isCi: Boolean) {
    tag(if (isCi) "CI" else "LOCAL")
}

private fun ScanApi.addCiMetadata(project: Project) {
    if (project.isJenkins) {
        if (System.getenv("BUILD_URL") != null) {
            link("Jenkins build", System.getenv("BUILD_URL"))
        }
        if (System.getenv("BUILD_NUMBER") != null) {
            value("CI build number", System.getenv("BUILD_NUMBER"))
        }
        if (System.getenv("NODE_NAME") != null) {
            val nodeNameLabel = "CI node"
            val nodeName = System.getenv("NODE_NAME")
            value(nodeNameLabel, nodeName)
            addCustomLinkWithSearchTerms("CI node build scans", mapOf(nodeNameLabel to nodeName))
        }
        if (System.getenv("JOB_NAME") != null) {
            val jobNameLabel = "CI job"
            val jobName = System.getenv("JOB_NAME")
            value(jobNameLabel, jobName)
            addCustomLinkWithSearchTerms("CI job build scans", mapOf(jobNameLabel to jobName))
        }
        if (System.getenv("STAGE_NAME") != null) {
            val stageNameLabel = "CI stage"
            val stageName = System.getenv("STAGE_NAME")
            value(stageNameLabel, stageName)
            addCustomLinkWithSearchTerms("CI stage build scans", mapOf(stageNameLabel to stageName))
        }
    }

    if (project.isActionsCi) {
        if (System.getenv("GITHUB_REPOSITORY") != null && System.getenv("GITHUB_RUN_ID") != null) {
            link(
                "GitHub Actions build",
                "https://github.com/${System.getenv("GITHUB_REPOSITORY")}/actions/runs/${System.getenv("GITHUB_RUN_ID")}"
            )
        }
        if (System.getenv("GITHUB_WORKFLOW") != null) {
            val workflowNameLabel = "GitHub workflow"
            val workflowName = System.getenv("GITHUB_WORKFLOW")
            value(workflowNameLabel, workflowName)
            addCustomLinkWithSearchTerms(
                "GitHub workflow build scans",
                mapOf(workflowNameLabel to workflowName)
            )
        }
    }
}

private fun ScanApi.addGitMetadata(project: Project) {
    val projectDir = project.projectDir
    val providers = project.providers
    background {
        if (!isGitInstalled(providers, projectDir)) {
            return@background
        }

        val gitCommitId =
            executeBlockingWithResult(
                providers,
                projectDir,
                listOf("git", "rev-parse", "--short=8", "--verify", "HEAD"),
                isRelevantToConfigurationCache = false
            )
        val gitBranchName =
            executeBlockingWithResult(
                providers,
                projectDir,
                listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
                isRelevantToConfigurationCache = false
            )

        val gitStatus =
            executeBlockingWithResult(
                providers,
                projectDir,
                listOf("git", "status", "--porcelain"),
                isRelevantToConfigurationCache = false
            )

        if (gitCommitId != null) {
            val gitCommitIdLabel = "Git commit id"
            value(gitCommitIdLabel, gitCommitId)
            addCustomLinkWithSearchTerms(
                "Git commit id build scans",
                mapOf(gitCommitIdLabel to gitCommitId)
            )

            val originUrl =
                executeBlockingWithResult(
                    providers,
                    projectDir,
                    listOf("git", "config", "--get", "remote.origin.url"),
                    isRelevantToConfigurationCache = false
                )
            if (originUrl != null) {
                if ("github.com/" in originUrl || "github.com:" in originUrl) {
                    GITHUB_ORIGIN_REGEX.find(originUrl)?.groups?.get(2)?.value?.removeSuffix(".git")?.let { repoPath ->
                        link("Github Source", "https://github.com/$repoPath/tree/$gitCommitId")
                    }
                }
            }
        }
        if (gitBranchName != null) {
            tag(gitBranchName)
            value("Git branch", gitBranchName)
        }
        if (gitStatus != null) {
            tag("Dirty")
            value("Git status", gitStatus)
        }
    }
}

internal fun ScanApi.addTestParallelization(project: Project) {
    project.tasks.withType<Test>().configureEach {
        doFirst { value("$identityPath#maxParallelForks", maxParallelForks.toString()) }
    }
}

internal fun ScanApi.addTestSystemProperties(project: Project) {
    project.tasks.withType<Test>().configureEach {
        doFirst {
            systemProperties.forEach { (key, entryValue) ->
                hash(entryValue)?.let { hash -> value("$identityPath#sysProps-$key", hash) }
            }
        }
    }
}

private fun ScanApi.addGradleEnterpriseVersion() {
    javaClass
        .classLoader
        .getResource("com.gradle.scan.plugin.internal.meta.buildAgentVersion.txt")
        ?.readText()
        ?.let { buildAgentVersion -> value("GE Gradle plugin version", buildAgentVersion) }
}

private fun ScanApi.addCustomLinkWithSearchTerms(title: String, search: Map<String, String>) {
    server?.let {
        val searchParams = customValueSearchParams(search)
        val url =
            "${it.appendIfMissing("/")}scans?$searchParams#selection.buildScanB=${urlEncode("{SCAN_ID}")}"
        link(title, url)
    }
}

private fun customValueSearchParams(search: Map<String, String>): String {
    return search.entries.joinToString("&") { (name, value) ->
        "search.names=${urlEncode(name)}&search.values=${urlEncode(value)}"
    }
}

private fun String.appendIfMissing(suffix: String): String {
    return if (endsWith(suffix)) this else this + suffix
}

private fun urlEncode(url: String): String {
    return URLEncoder.encode(url, Charsets.UTF_8.name())
}

private fun isGitInstalled(providers: ProviderFactory, workingDir: File): Boolean {
    return try {
        "git --version".executeBlocking(providers, workingDir, isRelevantToConfigurationCache = false)
        true
    } catch (ignored: IOException) {
        false
    }
}

private val MESSAGE_DIGEST = MessageDigest.getInstance("SHA-256")

private fun hash(value: Any?): String? {
    return if (value == null) {
        null
    } else {
        val string = value.toString()
        val encodedHash = MESSAGE_DIGEST.digest(string.toByteArray(Charsets.UTF_8))
        buildString {
            for (i in 0 until encodedHash.size / 4) {
                val hex = Integer.toHexString(0xff and encodedHash[i].toInt())
                if (hex.length == 1) {
                    append("0")
                }
                append(hex)
            }
            append("...")
        }
    }
}