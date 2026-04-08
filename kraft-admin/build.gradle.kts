plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.bowerzlabs"
version = "0.1.8-beta"

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":kraft-core"))
    api(project(":kraftadmin-springboot-adapter"))
    implementation(project(":kraftadmin-ui"))

    // implementation = bundled into shadow JAR and relocated
    // Java consumers get Kotlin without declaring it manually
    // Kotlin consumers use their own Kotlin — no conflict because it's relocated
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")

    testImplementation(kotlin("test"))
}

//tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
//    archiveBaseName.set("kraft-admin")
//    archiveClassifier.set("")
//
//    // Relocate Kotlin into a private package so it doesn't clash
//    // with the consumer app's own Kotlin runtime
//    relocate("kotlin", "com.bowerzlabs.kraftadmin.internal.kotlin")
//    relocate("kotlinx", "com.bowerzlabs.kraftadmin.internal.kotlinx")
//
//    // Don't bundle Jackson — Spring Boot provides it
//    exclude("com/fasterxml/**")
//    exclude("META-INF/services/com.fasterxml.jackson.databind.Module")
//    exclude("META-INF/services/com.fasterxml.jackson.core.JsonFactory")
//
//    exclude("META-INF/versions/21/**")
//    exclude("**/module-info.class")
//
//    // Keep service files for your own library's auto-configuration
//    // but merge carefully — only kraftadmin services should be here
//    // after excluding Jackson's service registrations above
//    mergeServiceFiles()
//}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("kraft-admin")
    archiveClassifier.set("")

    // 1. Relocate the actual Bytecode
    relocate("kotlin", "com.bowerzlabs.kraftadmin.internal.kotlin")
    relocate("kotlinx", "com.bowerzlabs.kraftadmin.internal.kotlinx")

    // 2. CRITICAL: Relocate the Metadata/Built-ins
    // Reflection looks for 'kotlin/kotlin.kotlin_builtins'.
    // Since we moved 'kotlin' to 'internal.kotlin', we must move these too.
    relocate("META-INF/kotlin/", "com/bowerzlabs/kraftadmin/internal/kotlin/")

    // 3. Clean up the JAR
    exclude("META-INF/versions/21/**")
    exclude("**/module-info.class")

    // Explicitly kill Jackson auto-registration to protect non-JPA apps
    exclude("META-INF/services/com.fasterxml.jackson.databind.Module")

    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.bowerzlabs"
            artifactId = "kraft-admin"
            version = "0.1.8-beta"

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

                // Kotlin is bundled+relocated inside the JAR so NOT a POM dependency
                // Java consumers get it automatically, Kotlin consumers use their own
                // No need to declare kotlin-stdlib/reflect here
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