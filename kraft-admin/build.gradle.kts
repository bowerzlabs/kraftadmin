plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.bowerzlabs"
version = "0.1.6-beta"

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

//dependencies {
//    api(project(":kraft-core"))
//    api(project(":kraftadmin-springboot-adapter"))
//    implementation(project(":kraftadmin-ui"))
//    // bundled
//    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
//    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")
//    testImplementation(kotlin("test"))
//}

kotlin {
    jvmToolchain(17)
}

//tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
//    archiveBaseName.set("kraft-admin")
//    archiveClassifier.set("")
//    mergeServiceFiles()
//    minimize()
//}

dependencies {
    api(project(":kraft-core"))
    api(project(":kraftadmin-springboot-adapter"))
    implementation(project(":kraftadmin-ui"))

    // api = appears in published POM as transitive dep
    // Java consumers without Kotlin get these pulled in automatically
    api("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    api("org.jetbrains.kotlin:kotlin-reflect:1.9.24")

    // compileOnly = Spring Boot provides these at runtime in consumer apps
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // ❌ Never bundle hibernate6 module — requires Hibernate internals

    testImplementation(kotlin("test"))
    testRuntimeOnly("com.fasterxml.jackson.core:jackson-databind")
    testRuntimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("kraft-admin")
    archiveClassifier.set("")

    // Don't bundle Kotlin — declared as api deps, consumers get them via POM
    exclude("kotlin/**")
    exclude("kotlinx/**")

    // Don't bundle Jackson — Spring Boot manages its own versions
    exclude("com/fasterxml/**")

    // Prevent any service registrations from leaking into consumer classpath
    exclude("META-INF/services/com.fasterxml.jackson.databind.Module")
    exclude("META-INF/services/com.fasterxml.jackson.core.JsonFactory")

    exclude("META-INF/versions/21/**")
    exclude("**/module-info.class")

    // ❌ No mergeServiceFiles() — leaks registrations from all bundled deps
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.bowerzlabs"
            artifactId = "kraft-admin"
            version = "0.1.6-beta"

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

//signing {
//    val signingKey = System.getenv("GPG_KEY")
//    val signingPassphrase = System.getenv("GPG_PASSPHRASE")
//
//    useInMemoryPgpKeys(signingKey, signingPassphrase)
//    sign(publishing.publications["mavenJava"])
//}

signing {
    val signingKey = System.getenv("GPG_KEY")
    val signingPassphrase = System.getenv("GPG_PASSPHRASE")

    // Only attempt to sign if the key is actually there
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications["mavenJava"])
    }
}