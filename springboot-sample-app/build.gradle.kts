plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.springframework.boot") version "3.3.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.spring") version libs.versions.kotlin.get()
    kotlin("plugin.jpa") version libs.versions.kotlin.get()
    application
}

group = "com.kraftadmin"
version = "0.0.1-beta"

repositories {
    mavenCentral()
}

dependencies {
    // This connects the sample app to the library
    implementation(project(":kraft-admin"))

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    runtimeOnly("com.h2database:h2")

    // Introspection for our library
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

tasks.getByName<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set("com.kraftadmin.SpringSampleAppKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}