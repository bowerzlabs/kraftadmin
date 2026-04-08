plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinSpring)
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bowerzlabs"
version = "0.1.5-beta"

repositories {
    mavenCentral()
}


dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.1") // pick your Spring Boot version
    }
}

dependencies {
    // KraftAdmin core
    api(project(":kraft-core"))

    // Spring Boot auto-config support (NO boot plugin!)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // JPA (compile-only)
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.springframework.data:spring-data-jpa")

    // Mongo (compile-only)
    compileOnly("org.springframework.data:spring-data-mongodb")

    // Jackson - essential for safe JSON handling
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Force the Kotlin module to a version that supports the Builder syntax
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Ensure these are also present and aligned
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6")

    // recommended for JPA: handles Hibernate lazy proxies safely
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6")

    // Validation API and Spring's integration
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    compileOnly("org.hibernate.orm:hibernate-core:6.5.2.Final")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    compileOnly("org.springframework.boot:spring-boot-starter-security")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<ProcessResources>("processResources") {
    from(project(":kraftadmin-ui").layout.buildDirectory.dir("dist")) {
        into("static")
    }
}


