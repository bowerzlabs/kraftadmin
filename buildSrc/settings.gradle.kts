//dependencyResolutionManagement {
//
//    // Use Maven Central and the Gradle Plugin Portal for resolving dependencies in the shared build logic (`buildSrc`) project.
//    @Suppress("UnstableApiUsage")
//    repositories {
//        mavenCentral()
//    }
//
//    // Reuse the version catalog from the main build.
//    versionCatalogs {
//        create("libs") {
//            from(files("../gradle/libs.versions.toml"))
//        }
//    }
//}
//
//rootProject.name = "buildSrc"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.gradle.kotlin.kotlin-dsl") {
                useVersion("5.2.0")  // Use a compatible version for Gradle 8.14
            }
        }
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"