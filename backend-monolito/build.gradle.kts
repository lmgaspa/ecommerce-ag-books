// =======================================================
// build.gradle.kts — Ecommerce AG Books (Spring Boot + Kotlin + Flyway)
// Compatível com PostgreSQL 18 e Flyway 11.17.0
// Baseline/Legacy/Live escolhidos por FLYWAY_MODE (baseline|legacy|live)
// =======================================================

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.bundling.BootJar

// >>> Estes artefatos entram no CLASSPATH DO PLUGIN do Flyway (não da app)
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.postgresql:postgresql:42.7.7")
        classpath("org.flywaydb:flyway-database-postgresql:11.17.0")
    }
}

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.noarg") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"

    id("org.springframework.boot") version "3.4.10"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.flywaydb.flyway") version "11.17.0" // PG18-ready
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
    implementation("org.springframework.boot:spring-boot-starter-aop")

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

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")

    // Lombok (opcional)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Metadata de @ConfigurationProperties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // JDBC driver (runtime da APP)
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    // Flyway na APP (runtime) — independente do classpath do plugin
    implementation("org.flywaydb:flyway-core:11.17.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.17.0")

    // Testes
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mitigações de segurança
    constraints {
        implementation("ch.qos.logback:logback-classic:1.5.20")
        implementation("ch.qos.logback:logback-core:1.5.20")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
    jvmToolchain(21)
}

/** JPA via allOpen + noArg */
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
/* ====================== Flyway via ENV (simplificado) ======================
   - Uma única pasta de migrações: src/main/resources/db/migration
   - FLYWAY_LOCATIONS (se setado) sobrescreve a localização padrão
   - Aceita URL vinda do Heroku (postgres://...) e normaliza para jdbc:postgresql://...
   - Se USER/PASSWORD não vierem por envs, tenta extrair da URL
   Variáveis aceitas (aliases):
     URL:   FLYWAY_URL | JDBC_DATABASE_URL | DATABASE_URL | SPRING_DATASOURCE_URL
     USER:  FLYWAY_USER | JDBC_DATABASE_USERNAME | DB_USERNAME | SPRING_DATASOURCE_USERNAME
     PASS:  FLYWAY_PASSWORD | JDBC_DATABASE_PASSWORD | DB_PASSWORD | SPRING_DATASOURCE_PASSWORD
     SCHEMAS: FLYWAY_SCHEMAS (padrão: "public")
   Extras:
     FLYWAY_LOCATIONS = "filesystem:...,classpath:..."
     FLYWAY_CLEAN_DISABLED=true|false (padrão true)
     -Dflyway.outOfOrder=true  (passado na linha de comando)
   Placeholders:
     SITE_AUTHOR_NAME, SITE_AUTHOR_EMAIL, SITE_AUTHOR_PIX_KEY
   ========================================================================== */

val isFlywayTaskRequested = gradle.startParameter.taskNames.any {
    it.startsWith("flyway", ignoreCase = true) && it != "bootRun"
}

fun envOr(default: String, vararg keys: String): String =
    keys.firstNotNullOfOrNull(System::getenv) ?: default

fun firstEnv(vararg keys: String): String? =
    keys.firstNotNullOfOrNull(System::getenv)

/** Converte "postgres://user:pass@host:5432/db?..." → "jdbc:postgresql://host:5432/db?..."  */
fun normalizeToJdbc(raw: String): String {
    if (raw.isBlank()) return raw
    return if (raw.startsWith("postgres://")) {
        // remove "postgres://user:pass@" → sobra "host:5432/db?..."
        val hostPart = raw.removePrefix("postgres://").substringAfter("@", raw.removePrefix("postgres://"))
        val base = "jdbc:postgresql://$hostPart"
        // garante ssl no Heroku, se não veio
        if ('?' in base) {
            if (base.contains("sslmode=")) base else "$base&sslmode=require"
        } else {
            "$base?sslmode=require"
        }
    } else if (raw.startsWith("jdbc:postgresql://")) {
        raw
    } else {
        // Ex.: URL sem esquema explícito (raro) → tenta prefixar
        "jdbc:postgresql://$raw"
    }
}

/** Extrai user/pass de uma URL estilo postgres://user:pass@host/db */
fun parseUserFromUrl(url: String): String? = try {
    val afterScheme = url.substringAfter("://", url)
    val creds = afterScheme.substringBefore('@', "")
    creds.substringBefore(':').ifEmpty { null }
} catch (_: Throwable) { null }

fun parsePassFromUrl(url: String): String? = try {
    val afterScheme = url.substringAfter("://", url)
    val creds = afterScheme.substringBefore('@', "")
    creds.substringAfter(':').ifEmpty { null }
} catch (_: Throwable) { null }

/* -------- URL -------- */
val rawUrl = firstEnv("FLYWAY_URL", "JDBC_DATABASE_URL", "DATABASE_URL", "SPRING_DATASOURCE_URL")
val urlValue = when {
    isFlywayTaskRequested && rawUrl == null ->
        throw GradleException(
            "Missing Flyway URL. Set ONE of: FLYWAY_URL | JDBC_DATABASE_URL | DATABASE_URL | SPRING_DATASOURCE_URL"
        )
    rawUrl != null -> normalizeToJdbc(rawUrl)
    else -> "jdbc:postgresql://localhost:5432/__placeholder__"
}

/* -------- USER / PASS (prefer envs; fallback: extrai da URL) -------- */
val userValue = firstEnv("FLYWAY_USER", "JDBC_DATABASE_USERNAME", "DB_USERNAME", "SPRING_DATASOURCE_USERNAME")
    ?: parseUserFromUrl(firstEnv("DATABASE_URL", "JDBC_DATABASE_URL", "FLYWAY_URL", "SPRING_DATASOURCE_URL") ?: "")
    ?: if (isFlywayTaskRequested)
        throw GradleException("Missing Flyway user. Set env FLYWAY_USER (ou um dos aliases) ou inclua na URL.")
    else "__placeholder__"

val passValue = firstEnv("FLYWAY_PASSWORD", "JDBC_DATABASE_PASSWORD", "DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD")
    ?: parsePassFromUrl(firstEnv("DATABASE_URL", "JDBC_DATABASE_URL", "FLYWAY_URL", "SPRING_DATASOURCE_URL") ?: "")
    ?: if (isFlywayTaskRequested)
        throw GradleException("Missing Flyway password. Set env FLYWAY_PASSWORD (ou um dos aliases) ou inclua na URL.")
    else "__placeholder__"

/* -------- SCHEMAS -------- */
val schemasValue =
    envOr("public", "FLYWAY_SCHEMAS")

/* -------- LOCATIONS --------
   Padrão: única pasta de migração. Incluo filesystem e classpath para cobrir Gradle task e empacotado. */
val effectiveLocations: Array<String> =
    firstEnv("FLYWAY_LOCATIONS")
        ?.takeIf { it.isNotBlank() }
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map { loc -> if (loc.startsWith("filesystem:") || loc.startsWith("classpath:")) loc else "filesystem:$loc" }
        ?.toTypedArray()
        ?: arrayOf(
            "filesystem:src/main/resources/db/migration",
            "classpath:db/migration"
        )

/* -------- CLEAN DISABLED -------- */
val cleanDisabledValue =
    (System.getenv("FLYWAY_CLEAN_DISABLED") ?: "true")
        .toBooleanStrictOrNull() ?: true

/* -------- Placeholders -------- */
val placeholderName  = envOr("", "SITE_AUTHOR_NAME")
val placeholderEmail = envOr("", "SITE_AUTHOR_EMAIL")
val placeholderPix   = envOr("", "SITE_AUTHOR_PIX_KEY")

flyway {
    url = urlValue
    user = userValue
    password = passValue
    schemas = arrayOf(schemasValue)
    locations = effectiveLocations
    cleanDisabled = cleanDisabledValue

    placeholders = mapOf(
        "SITE_AUTHOR_NAME" to placeholderName,
        "SITE_AUTHOR_EMAIL" to placeholderEmail,
        "SITE_AUTHOR_PIX_KEY" to placeholderPix
    )
}

// ====== BootJar com nome previsível ======
tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("ecommerceag-backend")
}
