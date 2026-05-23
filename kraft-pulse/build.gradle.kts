import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    `maven-publish`
    signing
    id("buildsrc.convention.kotlin-jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.bowerzlabs"
version = "0.1.12-beta"

java {
    withSourcesJar()
    withJavadocJar()
}

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

    val coreProject = project(":kraftpulse-core")
    val adapterProject = project(":kraftpulse-springboot-adapter")

    from(coreProject.layout.buildDirectory.dir("classes/kotlin/main"))
    from(coreProject.layout.buildDirectory.dir("resources/main"))
    from(adapterProject.layout.buildDirectory.dir("classes/kotlin/main"))
    from(adapterProject.layout.buildDirectory.dir("resources/main"))

    mergeServiceFiles {
        setPath("META-INF/spring")
    }

    exclude("org/springframework/**")
    exclude("jakarta/**")
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("META-INF/versions/21/**")
    exclude("**/module-info.class")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.bowerzlabs"
            artifactId = "kraft-pulse"
            version = project.version.toString()

            project.shadow.component(this)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))

            pom {
                name.set("KraftPulse")
                description.set("A lightweight telemetry and metrics engine for Spring Boot.")
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
                scm {
                    connection.set("scm:git:git://github.com/bowerzlabs/kraftadmin.git")
                    developerConnection.set("scm:git:ssh://github.com/bowerzlabs/kraftadmin.git")
                    url.set("https://github.com/bowerzlabs/kraftadmin")
                }
            }
        }
    }

    repositories {
        maven {
            name = "StagingDir"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

signing {
    val signingKey = System.getenv("GPG_KEY")
    val signingPassphrase = System.getenv("GPG_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications["mavenJava"])
    }
}