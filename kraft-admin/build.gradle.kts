plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.bowerzlabs"
version = "0.1.0-beta"

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // These are bundled into the Shadow JAR
    api(project(":kraft-core"))
    api(project(":kraftadmin-springboot-adapter"))
    implementation(project(":kraftadmin-ui"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21) // Updated to 21 to match your system goals
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("kraft-admin")
    archiveClassifier.set("")
    mergeServiceFiles()
    minimize()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.bowerzlabs"
            artifactId = "kraft-admin"
            version = "0.1.0-beta"

            project.shadow.component(this)

            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))

            pom {
                name.set("KraftAdmin")
                description.set("A high-performance, reactive admin dashboard & telemetry engine for Spring Boot.")
                url.set("https://github.com/bowerzlabs/kraftadmin")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("nyadero")
                        name.set("Nyadero Brian Odhiambo")
                        email.set("hello@bowerzlabs.com")
                    }
                }
            }
        }
    }

    repositories {
        // kept for local testing
        maven {
            name = "local"
            url = uri(System.getProperty("user.home") + "/.m2/repository")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}