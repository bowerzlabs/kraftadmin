plugins {
    kotlin("jvm")
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":kraft-common"))
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}