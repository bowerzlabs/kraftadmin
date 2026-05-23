plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.9")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    testImplementation(kotlin("test"))
}

val generateBuildInfo by tasks.registering {
    val outputFile = file("src/main/kotlin/BuildInfo.kt")

    inputs.property("version", project.version.toString())
    inputs.property("group", project.group.toString())
    outputs.file(outputFile)

    doLast {
        val version = inputs.properties["version"] as String
        val group = inputs.properties["group"] as String
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package com.kraftadmin

            object BuildInfo {
                const val VERSION = "$version"
                const val GROUP = "$group"
                const val ARTIFACT_ID = "kraft-admin"
            }
            """.trimIndent()
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}