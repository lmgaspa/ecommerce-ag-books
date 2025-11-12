// =======================================================
// build.gradle.kts — Ecommerce AG Books (Spring Boot + Kotlin + Flyway)
// Compatível com PostgreSQL 18 e Flyway 11.17.0
// Baseline/Legacy/Live escolhidos por FLYWAY_MODE (baseline|legacy|legacy+live)
// =======================================================

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.bundling.BootJar

// >>> MUITO IMPORTANTE: estas deps entram no CLASSPATH DO PLUGIN do Flyway (não da app)
buildscript {
    dependencies {
        classpath("org.postgresql:postgresql:42.7.7")
        classpath("org.flywaydb:flyway-database-postgresql:11.17.0")
    }
}

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.noarg") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"

    id("org.springframework.boot") version "3.4.10"
    id("io.spring.dependency-management") version "1.1.6"

    // Flyway atualizado p/ Postgres 18
    id("org.flywaydb.flyway") version "11.17.0"
}

group = "com.luizgasparetto"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    // BOM do Spring Boot
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

    // Lombok (opcional)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Metadata de @ConfigurationProperties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // JDBC driver (runtime da APP)
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    // Flyway na APP (runtime) — não conflita com o classpath do plugin já resolvido acima
    implementation("org.flywaydb:flyway-core:11.17.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.17.0")

    // Testes
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mitigação CVE (ex.: logback)
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

/** Recursos: não empacotar templates operacionais */
sourceSets {
    named("main") {
        resources {
            exclude("db/migration/T__*.sql")
            exclude("db/migration/**/T__*.sql")
        }
    }
}

/* ====================== Flyway via ENV ======================
   Variáveis esperadas:
   - FLYWAY_URL / JDBC_DATABASE_URL / DATABASE_URL / SPRING_DATASOURCE_URL
   - FLYWAY_USER / JDBC_DATABASE_USERNAME / DB_USERNAME / SPRING_DATASOURCE_USERNAME
   - FLYWAY_PASSWORD / JDBC_DATABASE_PASSWORD / DB_PASSWORD / SPRING_DATASOURCE_PASSWORD
   - FLYWAY_SCHEMAS (ex.: "public")
   - FLYWAY_MODE = baseline | legacy | live (padrão: legacy)
   - (opcional) FLYWAY_LOCATIONS = "filesystem:... , filesystem:..."
   - (opcional) FLYWAY_CLEAN_DISABLED=true|false (padrão true)
   - (opcional) -Dflyway.outOfOrder=true para cicatrizar buracos históricos
   ========================================================== */

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

val schemasValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_SCHEMAS")
else
    envOr("public", "FLYWAY_SCHEMAS")

// Mapeia diretórios pelo modo escolhido
val flywayMode = (System.getenv("FLYWAY_MODE") ?: "legacy").lowercase()

val locationsFromMode: Array<String> = when (flywayMode) {
    "baseline" -> arrayOf(
        "filesystem:src/main/resources/db/migration/baseline",
        "filesystem:src/main/resources/db/migration/live"
    )
    "live" -> arrayOf(
        "filesystem:src/main/resources/db/migration/live"
    )
    else -> arrayOf( // "legacy" (padrão)
        "filesystem:src/main/resources/db/migration/legacy",
        "filesystem:src/main/resources/db/migration/live"
    )
}

// Se FLYWAY_LOCATIONS vier setada, ela prevalece
val effectiveLocations: Array<String> =
    System.getenv("FLYWAY_LOCATIONS")
        ?.takeIf { it.isNotBlank() }
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map { if (it.startsWith("filesystem:")) it else "filesystem:$it" }
        ?.toTypedArray()
        ?: locationsFromMode

val cleanDisabledValue = (System.getenv("FLYWAY_CLEAN_DISABLED") ?: "true")
    .toBooleanStrictOrNull() ?: true

flyway {
    url = urlValue
    user = userValue
    password = passValue
    schemas = arrayOf(schemasValue)
    locations = effectiveLocations
    cleanDisabled = cleanDisabledValue
}

/* ====== BootJar com nome previsível ====== */
tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("ecommerceag-backend")
}
