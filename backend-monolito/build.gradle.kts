import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.noarg") version "2.2.0"
    kotlin("plugin.allopen") version "2.2.0" // 👈 adicionado

    id("org.springframework.boot") version "3.4.10"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.flywaydb.flyway") version "10.20.0"
}

group = "com.luizgasparetto"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

/** Classpath dedicado às tasks do Flyway */
val flywaySupport by configurations.creating

dependencies {
    // BOM do Spring Boot (3.4.10)
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.10"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // HTTP
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.apache.httpcomponents.core5:httpcore5")

    // SDK EFI (mantido)
    implementation("br.com.efipay.efisdk:sdk-java-apis-efi:1.2.2")

    // Validation
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // Swagger/springdoc (2.x estável)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")

    // Lombok (opcional)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // @ConfigurationProperties metadata
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // JDBC driver
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ---- Flyway libs na app (10.x conforme stack) ----
    implementation("org.flywaydb:flyway-core:10.20.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.0")

    // ---- Classpath dedicado das tasks do Flyway ----
    add(flywaySupport.name, "org.flywaydb:flyway-core:10.20.0")
    add(flywaySupport.name, "org.flywaydb:flyway-database-postgresql:10.20.0")
    add(flywaySupport.name, "org.postgresql:postgresql:42.7.7")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
    }
    jvmToolchain(21)
}

/** ✅ Configuração JPA via allOpen + noarg (no lugar do plugin.jpa) */
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.test { useJUnitPlatform() }

/* ============ Flyway via ENV (sem travar IDE) ============ */

val isFlywayTaskRequested = gradle.startParameter.taskNames.any {
    it.startsWith("flyway", ignoreCase = true) && it != "bootRun"
}

fun requiredEnv(vararg keys: String): String =
    keys.firstNotNullOfOrNull(System::getenv)
        ?: throw GradleException("Missing required env. Set ONE of: ${keys.joinToString(", ")}")

fun envOr(placeholder: String, vararg keys: String): String =
    keys.firstNotNullOfOrNull(System::getenv) ?: placeholder

val urlValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_URL", "JDBC_DATABASE_URL", "DATABASE_URL", "SPRING_DATASOURCE_URL")
else
    envOr("jdbc:postgresql://localhost:5432/__placeholder__", "FLYWAY_URL", "JDBC_DATABASE_URL", "DATABASE_URL", "SPRING_DATASOURCE_URL")

val userValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_USER", "JDBC_DATABASE_USERNAME", "DB_USERNAME", "SPRING_DATASOURCE_USERNAME")
else
    envOr("__placeholder__", "FLYWAY_USER", "JDBC_DATABASE_USERNAME", "DB_USERNAME", "SPRING_DATASOURCE_USERNAME")

val passValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_PASSWORD", "JDBC_DATABASE_PASSWORD", "DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD")
else
    envOr("__placeholder__", "FLYWAY_PASSWORD", "JDBC_DATABASE_PASSWORD", "DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD")

val schemasValue = if (isFlywayTaskRequested) requiredEnv("FLYWAY_SCHEMAS") else envOr("public", "FLYWAY_SCHEMAS")
val locationsValue = if (isFlywayTaskRequested) requiredEnv("FLYWAY_LOCATIONS") else envOr("filesystem:src/main/resources/db/migration", "FLYWAY_LOCATIONS")
val cleanDisabledValue = (System.getenv("FLYWAY_CLEAN_DISABLED") ?: "true").toBooleanStrictOrNull() ?: true

flyway {
    configurations = arrayOf("flywaySupport")
    url = urlValue
    user = userValue
    password = passValue
    schemas = arrayOf(schemasValue)
    locations = arrayOf(locationsValue)
    cleanDisabled = cleanDisabledValue
}

/* ============ BootJar nome estável ============ */
tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("ecommerceag-backend")
}
