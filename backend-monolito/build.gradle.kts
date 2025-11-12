import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.bundling.BootJar

// ----------------------------------------------------------------------
// Classpath do PLUGIN do Flyway (necessário p/ "jdbc:postgresql://...")
// ----------------------------------------------------------------------
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:10.20.0")
        classpath("org.postgresql:postgresql:42.7.7")
    }
}

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.noarg") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"

    id("org.springframework.boot") version "3.4.10"
    id("io.spring.dependency-management") version "1.1.6"

    // Flyway conforme a stack
    id("org.flywaydb.flyway") version "10.20.0"
}

group = "com.luizgasparetto"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

/** Classpath dedicado para as tasks do Flyway */
val flywaySupport by configurations.creating

dependencies {
    // BOM do Spring Boot 3.4.x
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.10"))

    // Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // HTTP (gerenciados pelo BOM)
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.apache.httpcomponents.core5:httpcore5")

    // SDK EFI
    implementation("br.com.efipay.efisdk:sdk-java-apis-efi:1.2.2")

    // Validation
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // OpenAPI (springdoc 2.x)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")

    // Lombok (opcional) + metadata
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Driver JDBC na aplicação
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    // Testes
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Flyway na app
    implementation("org.flywaydb:flyway-core:10.20.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.0")

    // ---- Classpath dedicado das tasks do Flyway ----
    add(flywaySupport.name, "org.flywaydb:flyway-core:10.20.0")
    add(flywaySupport.name, "org.flywaydb:flyway-database-postgresql:10.20.0")
    add(flywaySupport.name, "org.postgresql:postgresql:42.7.7")

    // ---- Mitigação CVE (Logback) ----
    constraints {
        implementation("ch.qos.logback:logback-classic:1.5.20")
        implementation("ch.qos.logback:logback-core:1.5.20")
    }
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

/** JPA via allOpen + noArg (estável) */
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

/** Templates/ops fora do classpath da app */
sourceSets {
    named("main") {
        resources {
            exclude("db/migration/T__*.sql")
            exclude("db/migration/**/T__*.sql")
        }
    }
}

/* =================== Flyway por ENV =================== */

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

val schemasValue = envOr("public", "FLYWAY_SCHEMAS")

// Locais padrão por modo (sobreponha via FLYWAY_LOCATIONS se quiser)
val flywayLocationsFromMode: Array<String> = when ((System.getenv("FLYWAY_MODE") ?: "legacy").lowercase()) {
    "baseline" -> arrayOf(
        "filesystem:src/main/resources/db/migration/baseline",
        "filesystem:src/main/resources/db/migration/live"
    )
    "legacy" -> arrayOf(
        "filesystem:src/main/resources/db/migration/legacy",
        "filesystem:src/main/resources/db/migration/live"
    )
    else -> arrayOf("filesystem:src/main/resources/db/migration/live")
}

val effectiveLocations: Array<String> =
    if (isFlywayTaskRequested) {
        val envLoc = System.getenv("FLYWAY_LOCATIONS")
        if (!envLoc.isNullOrBlank())
            envLoc.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.startsWith("filesystem:")) it else "filesystem:$it" }
                .toTypedArray()
        else flywayLocationsFromMode
    } else {
        flywayLocationsFromMode
    }

val cleanDisabledValue = (System.getenv("FLYWAY_CLEAN_DISABLED") ?: "true").toBooleanStrictOrNull() ?: true

flyway {
    // Faz o plugin usar também o classpath extra c/ driver/adapter
    configurations = arrayOf(
        "compileClasspath",
        "runtimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath",
        flywaySupport.name
    )
    driver = "org.postgresql.Driver"
    url = urlValue
    user = userValue
    password = passValue
    schemas = arrayOf(schemasValue)
    locations = effectiveLocations
    cleanDisabled = cleanDisabledValue
}

/** BootJar com nome estável */
tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("ecommerceag-backend")
}
