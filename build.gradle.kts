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

