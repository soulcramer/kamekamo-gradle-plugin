import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    kotlin("jvm")
}

if (hasProperty("KameKamoRepositoryUrl")) {
    apply(plugin = "com.vanniktech.maven.publish")
}

gradlePlugin {
    plugins.create("unitTest") {
        id = "kamekamo.unit-test"
        implementationClass = "kamekamo.unittest.UnitTestPlugin"
    }
    plugins.create("kamekamo-root") {
        id = "kamekamo.root"
        implementationClass = "kamekamo.gradle.KameKamoRootPlugin"
    }
    plugins.create("kamekamo-base") {
        id = "kamekamo.base"
        implementationClass = "kamekamo.gradle.KameKamoBasePlugin"
    }
    plugins.create("apkVersioning") {
        id = "kamekamo.apk-versioning"
        implementationClass = "kamekamo.gradle.ApkVersioningPlugin"
    }
}

sourceSets {
    main.configure {
        java.srcDir(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
    }
}

// NOTE: DON'T CHANGE THIS TASK NAME WITHOUT CHANGING IT IN THE ROOT BUILD FILE TOO!
val copyVersionTemplatesProvider = tasks.register<Copy>("copyVersionTemplates") {
    from(project.layout.projectDirectory.dir("version-templates"))
    into(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
    filteringCharset = "UTF-8"

    doFirst {
        if (destinationDir.exists()) {
            // Clear output dir first if anything is present
            destinationDir.listFiles()?.forEach { it.delete() }
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(copyVersionTemplatesProvider)
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.gradlePlugins.enterprise)

    compileOnly(platform(kotlin("bom", version = libs.versions.kotlin.get())))
    compileOnly(kotlin("gradle-plugin", version = libs.versions.kotlin.get()))
    implementation(kotlin("reflect", version = libs.versions.kotlin.get()))

    // compileOnly because we want to leave versioning to the consumers
    // Add gradle plugins for the slack project itself, separate from plugins. We do this so we can de-dupe version
    // management between this plugin and the root build.gradle.kts file.
    compileOnly(libs.gradlePlugins.bugsnag)
    compileOnly(libs.gradlePlugins.doctor)
    compileOnly(libs.gradlePlugins.versions)
    compileOnly(libs.gradlePlugins.detekt)
    compileOnly(libs.detekt)
    compileOnly(libs.gradlePlugins.errorProne)
    compileOnly(libs.gradlePlugins.nullaway)
    compileOnly(libs.gradlePlugins.dependencyAnalysis)
    compileOnly(libs.gradlePlugins.retry)
    compileOnly(libs.gradlePlugins.anvil)
    compileOnly(libs.gradlePlugins.spotless)
    compileOnly(libs.gradlePlugins.ksp)
    compileOnly(libs.gradlePlugins.redacted)
    compileOnly(libs.gradlePlugins.moshix)

    implementation(libs.oshi) {
        because("To read hardware information")
    }

    compileOnly(libs.agp)
    api(projects.agpHandlers.agpHandlerApi)
    implementation(projects.agpHandlers.agpHandler71)
    testImplementation(libs.agp)

    implementation(libs.commonsText) {
        because("For access to its StringEscapeUtils")
    }
    implementation(libs.guava)
    implementation(libs.kotlinCliUtil)
    implementation(libs.jna)

    implementation(libs.rxjava)

    api(platform(libs.okhttp.bom))
    api(libs.okhttp)

    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Graphing library with Betweenness Centrality algo for modularization score
    implementation(libs.jgrapht)

    // Progress bar for downloads
    implementation(libs.progressBar)

    // Better I/O
    api(libs.okio)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}