plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.gradleup.shadow") version "8.3.3"
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.bowerzlabs"
version = "0.1.2-beta"

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":kraft-core"))
    api(project(":kraftadmin-springboot-adapter"))
    implementation(project(":kraftadmin-ui"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

//tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
//    archiveBaseName.set("kraft-admin")
//    archiveClassifier.set("")
//    mergeServiceFiles()
//    minimize()
//}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("kraft-admin")
    archiveClassifier.set("")

    // Use from() instead of configurations = ... to avoid the "Val cannot be reassigned" error
    from(project.configurations.runtimeClasspath.get())

    // RELOCATE kotlin-reflect to keep it away from the parent app's classpath
    relocate("kotlin.reflect", "com.bowerzlabs.kraftadmin.shaded.kotlin.reflect")
    relocate("kotlin", "com.bowerzlabs.kraftadmin.shaded.kotlin")
    relocate("kotlinx", "com.bowerzlabs.kraftadmin.shaded.kotlinx")

    // THE FIX FOR VERSION 65:
    // We tell Shadow to physically ignore the Java 21 folders inside the JARs it scans.
    exclude("META-INF/versions/21/**")
    exclude("META-INF/versions/17/**") // Optional, but keeps things clean
    exclude("**/module-info.class")

    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.bowerzlabs"
            artifactId = "kraft-admin"
            version = "0.1.2-beta"

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

    useInMemoryPgpKeys(signingKey, signingPassphrase)
    sign(publishing.publications["mavenJava"])
}