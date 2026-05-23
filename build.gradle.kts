plugins {
    alias(libs.plugins.kotlinPluginSerialization) apply false
    kotlin("jvm")
}


allprojects {
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
        javaParameters = true // This MUST be true for @ConfigurationProperties on data classes
    }
}

