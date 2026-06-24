plugins {
    kotlin("jvm")
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":kraft-common"))

    // Core Micrometer (Data collection standard)
    api("io.micrometer:micrometer-observation:1.13.0") // Or latest
    api("io.micrometer:micrometer-core:1.13.0")

    compileOnly("org.slf4j:slf4j-api:2.0.9")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}