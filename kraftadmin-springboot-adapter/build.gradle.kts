plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinSpring)
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bowerzlabs"
version = "0.1.6-beta"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.1")
    }
}

dependencies {
    api(project(":kraft-core"))

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

    // Jackson — compileOnly, no duplicates
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Hibernate — compileOnly so the adapter can use Hibernate APIs internally
    // but the consumer's app Hibernate version is used at runtime, not this one
    compileOnly("org.hibernate.orm:hibernate-core:6.4.4.Final")
    // Jackson-Hibernate integration — also compileOnly for same reason
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6:2.17.0")

    // kotlin-reflect must be implementation, not compileOnly
    // because it's needed at runtime by Spring and Jackson
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

tasks.named<ProcessResources>("processResources") {
    from(project(":kraftadmin-ui").layout.buildDirectory.dir("dist")) {
        into("static")
    }
}