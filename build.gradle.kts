plugins {
//    alias(libs.plugins.kotlin.jvm) apply false
//    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlinPluginSerialization) apply false
    kotlin("jvm")
}


allprojects {
    repositories {
        mavenCentral()
    }
}

// Optional: common dependencies across all modules
//subprojects {
//    apply(plugin = "org.jetbrains.kotlin.jvm")
//
//    dependencies {
//        implementation(kotlin("stdlib"))
//    }
//
//    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//        kotlinOptions.jvmTarget = "17"
//    }
//}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}

