import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.Configuration
import org.flywaydb.gradle.task.AbstractFlywayTask

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"

    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.7"

    id("org.flywaydb.flyway") version "10.20.0"
}

group = "com.luizgasparetto"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    // Remova se não precisa de milestone/snapshot:
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2.1")

    implementation("br.com.efipay.efisdk:sdk-java-apis-efi:1.2.2")

    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // (Opcional) Flyway na app
    implementation("org.flywaydb:flyway-core:10.20.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.0")
}

/* =========================================================
 * Classpath DEDICADO às tasks do Flyway (driver + adaptador)
 * ========================================================= */
val flywaySupport: Configuration by configurations.creating

dependencies {
    add("flywaySupport", "org.flywaydb:flyway-core:10.20.0")
    add("flywaySupport", "org.flywaydb:flyway-database-postgresql:10.20.0")
    add("flywaySupport", "org.postgresql:postgresql:42.7.4")
}

// Configuração do Flyway - usando configuração padrão

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.test {
    useJUnitPlatform()
}

/* ============================
 * Flyway via ENV (sem quebrar o sync do IDE)
 * ============================ */

// Só força ENVs quando houver tasks do Flyway na linha de comando.
val isFlywayTaskRequested = gradle.startParameter.taskNames.any {
    it.startsWith("flyway", ignoreCase = true) && it != "bootRun"
}


fun requiredEnv(vararg keys: String): String =
    keys.firstNotNullOfOrNull(System::getenv)
        ?: throw GradleException("Missing required env. Set ONE of: ${keys.joinToString(", ")}")

fun envOrPlaceholder(placeholder: String, vararg keys: String): String =
    keys.firstNotNullOfOrNull(System::getenv) ?: placeholder

val urlValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_URL", "JDBC_DATABASE_URL", "DATABASE_URL", "SPRING_DATASOURCE_URL")
else
    envOrPlaceholder("jdbc:postgresql://localhost:5432/__placeholder__", "FLYWAY_URL", "JDBC_DATABASE_URL", "DATABASE_URL", "SPRING_DATASOURCE_URL")

val userValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_USER", "JDBC_DATABASE_USERNAME", "DB_USERNAME", "SPRING_DATASOURCE_USERNAME")
else
    envOrPlaceholder("__placeholder__", "FLYWAY_USER", "JDBC_DATABASE_USERNAME", "DB_USERNAME", "SPRING_DATASOURCE_USERNAME")

val passValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_PASSWORD", "JDBC_DATABASE_PASSWORD", "DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD")
else
    envOrPlaceholder("__placeholder__", "FLYWAY_PASSWORD", "JDBC_DATABASE_PASSWORD", "DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD")

val schemasValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_SCHEMAS")
else
    envOrPlaceholder("public", "FLYWAY_SCHEMAS")

val locationsValue = if (isFlywayTaskRequested)
    requiredEnv("FLYWAY_LOCATIONS")
else
    envOrPlaceholder("filesystem:src/main/resources/db/migration", "FLYWAY_LOCATIONS")

val cleanDisabledValue = (System.getenv("FLYWAY_CLEAN_DISABLED") ?: "true")
    .toBooleanStrictOrNull() ?: true

flyway {
    // (não precisa configurations = ..., já setamos o classpath nas tasks)
    url = urlValue
    user = userValue
    password = passValue
    schemas = arrayOf(schemasValue)
    locations = arrayOf(locationsValue)
    cleanDisabled = cleanDisabledValue
}
