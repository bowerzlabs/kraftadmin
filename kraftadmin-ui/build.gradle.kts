plugins {
    base
    kotlin("jvm")
}

group = "com.kraftadmin"
version = "0.0.4-beta"

repositories {
    mavenCentral()
}

val uiDir = projectDir.resolve("kraftadmin-ui")
val uiDistDir = uiDir.resolve("dist")
// Standard location for auto-serving static content in JARs
val resourcesDir = projectDir.resolve("src/main/resources/META-INF/resources/admin")

tasks.register<Exec>("npmInstall") {
    group = "build"
    workingDir = uiDir
    // Use ci for faster, more reliable builds in automated environments
    commandLine(if (org.apache.tools.ant.taskdefs.condition.Os.isFamily("windows")) listOf("npm.cmd", "install") else listOf("npm", "install"))

    inputs.file(uiDir.resolve("package.json"))
    outputs.dir(uiDir.resolve("node_modules"))
}

tasks.register<Exec>("buildUi") {
    group = "build"
    dependsOn("npmInstall")
    workingDir = uiDir
    commandLine(if (org.apache.tools.ant.taskdefs.condition.Os.isFamily("windows")) listOf("npm.cmd", "run", "build") else listOf("npm", "run", "build"))

    inputs.dir(uiDir.resolve("src"))
    outputs.dir(uiDistDir)
}

tasks.register<Sync>("embedUi") {
    group = "build"
    dependsOn("buildUi")

    // Use Sync instead of Copy to automatically remove old files in the destination
    from(uiDistDir)
    into(resourcesDir)
}

// Ensure the UI is built before the resources are processed into the JAR
tasks.named("processResources") {
    dependsOn("embedUi")
}

kotlin {
    jvmToolchain(17)
}

//plugins {
//    base
//    kotlin("jvm")
//}
//
//group = "com.kraftadmin"
//version = "0.0.1"
//
//val uiDir = projectDir.resolve("kraftadmin-ui")
//val uiDistDir = uiDir.resolve("dist")
//val resourcesDir = projectDir.resolve("src/main/resources/META-INF/resources/admin")
//
///**
// * Workaround: Helper function to find the absolute path of npm.
// * This ensures that even if 'sh' can't find it, we provide the full path to Exec.
// */
//fun findNpmPath(): String {
//    val isWindows = org.apache.tools.ant.taskdefs.condition.Os.isFamily("windows")
//    val command = if (isWindows) listOf("where", "npm") else listOf("which", "npm")
//
//    return try {
//        ProcessBuilder(command)
//            .start()
//            .inputStream
//            .bufferedReader()
//            .readLines()
//            .firstOrNull()
//            ?.trim() ?: (if (isWindows) "npm.cmd" else "npm")
//    } catch (e: Exception) {
//        if (isWindows) "npm.cmd" else "npm"
//    }
//}

//tasks.register<Exec>("npmInstall") {
//    group = "build"
//    workingDir = uiDir
//
//    val npmPath = findNpmPath()
//    executable = npmPath
//    args("install")
//
//    inputs.file(uiDir.resolve("package.json"))
//    outputs.dir(uiDir.resolve("node_modules"))
//}
//
//tasks.register<Exec>("buildUi") {
//    group = "build"
//    dependsOn("npmInstall")
//    workingDir = uiDir
//
//    val npmPath = findNpmPath()
//    executable = npmPath
//    args("run", "build")
//
//    inputs.dir(uiDir.resolve("src"))
//    outputs.dir(uiDistDir)
//}
//
//tasks.register<Sync>("embedUi") {
//    group = "build"
//    dependsOn("buildUi")
//    from(uiDistDir)
//    into(resourcesDir)
//}
//
//tasks.named("processResources") {
//    dependsOn("embedUi")
//}

//kotlin {
//    jvmToolchain(17)
//}