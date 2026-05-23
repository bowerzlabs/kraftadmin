import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("buildsrc.convention.kotlin-jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = "com.bowerzlabs"
version = "0.1.12-beta"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kraftpulse-core"))
    implementation(project(":kraftpulse-springboot-adapter"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("kraft-pulse")
    archiveClassifier.set("")

    mergeServiceFiles {
        setPath("META-INF/spring")
    }

    // FIX: Using evaluationDependsOn ensures the other modules are ready
    // and using project.extensions to find the sourceSets safely
    val coreProject = project(":kraftpulse-core")
    val adapterProject = project(":kraftpulse-springboot-adapter")

    evaluationDependsOn(coreProject.path)
    evaluationDependsOn(adapterProject.path)

    // Pull from the classes and resources directly
    from(coreProject.layout.buildDirectory.dir("classes/kotlin/main"))
    from(coreProject.layout.buildDirectory.dir("resources/main"))

    from(adapterProject.layout.buildDirectory.dir("classes/kotlin/main"))
    from(adapterProject.layout.buildDirectory.dir("resources/main"))

    exclude("org/springframework/**")
    exclude("jakarta/**")
    exclude("kotlin/**")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Manual artifact registration is often more stable in KTS
            artifact(tasks.shadowJar)

            groupId = "com.bowerzlabs"
            artifactId = "kraft-pulse"
            version = "0.1.12-beta"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}