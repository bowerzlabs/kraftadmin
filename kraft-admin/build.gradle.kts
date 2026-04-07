plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("os.libera.central-portal-publishing") version "0.0.4"
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

//        maven {
//            name = "OSSRH" // Open Source Software Repository Hosting
//            url = uri("https://central.sonatype.com/repository/maven-releases/")
//            credentials {
//                username = System.getenv("MAVEN_USERNAME")
//                password = System.getenv("MAVEN_PASSWORD")
//            }
//        }

            maven {
                name = "CentralPortal"
                // This is the correct "upload" URL for the Central Portal API
                url = uri("https://central.sonatype.com/api/v1/publisher/upload")
                credentials {
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
    }
}

tasks.test {
    useJUnitPlatform()
}

signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassphrase = System.getenv("GPG_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        // This ensures the mavenJava publication is signed
        sign(publishing.publications["mavenJava"])
    }
}