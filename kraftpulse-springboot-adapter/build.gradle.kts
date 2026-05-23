plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinSpring)
    id("io.spring.dependency-management") version "1.1.7"
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.1")
    }
}

dependencies {
    api(project(":kraftpulse-core"))

    // Spring APIs — consumer provides these
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.springframework.data:spring-data-jpa")
    compileOnly("org.springframework.data:spring-data-mongodb")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")

    // Jackson — implementation, this is YOUR internal detail
    // Shadow JAR will relocate these so they never conflict with consumer's Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("net.ttddyy:datasource-proxy:1.10")

    // Hibernate — consumer provides at runtime, you only reference via reflection
    compileOnly("org.hibernate.orm:hibernate-core:6.4.4.Final")

    // Hibernate-Jackson bridge — consumer-provided, never bundle or relocate
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6:2.17.0")

    compileOnly("org.springframework.boot:spring-boot-starter-aop")

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}