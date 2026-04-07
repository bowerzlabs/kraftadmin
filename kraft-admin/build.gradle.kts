plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.kraftadmin"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":kraft-core"))
    api(project(":kraftadmin-springboot-adapter"))
    implementation(project(":kraftadmin-ui"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// Optional: configure fat/uber jar
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("kraft-admin")
    archiveVersion.set("0.0.1")
    archiveClassifier.set("")  // No -all
    mergeServiceFiles()        // important for Spring Boot META-INF/services
}

// Maven publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.kraftadmin"
            artifactId = "kraft-admin"
            version = "0.0.1"

            // Use our fat JAR as the main artifact
//            artifact(tasks["uberJar"])
            artifact(tasks.named("shadowJar").get())
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(System.getProperty("user.home") + "/.m2/repository")
        }
    }
}

// Optional: use JUnit platform for tests
tasks.test {
    useJUnitPlatform()
}

