plugins {
    alias(libs.plugins.kotlinPluginSerialization) apply false
    kotlin("jvm")
}

allprojects {
    group = "com.bowerzlabs"
    version = properties["version"] ?: error("version not set in gradle.properties")
    repositories {
        mavenCentral()
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        javaParameters = true
    }
}

