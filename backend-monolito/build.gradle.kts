import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"

    id("org.springframework.boot") version "3.4.10"
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
    // (sem milestone/snapshot p/ build previsível)
}

/** Configuração dedicada para as tasks do Flyway (driver + adaptador) */
val flywaySupport by configurations.creating

dependencies {
    // Use o BOM do Spring Boot para evitar conflito de versões
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.10"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Deixe o Boot gerenciar httpclient/httpcore (evita CNF de TlsSocketStrategy)
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.apache.httpcomponents.core5:httpcore5")

    implementation("br.com.efipay.efisdk:sdk-java-apis-efi:1.2.2")

    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Metadata para @ConfigurationProperties (opcional – se tiver classes Kotlin, considere kapt)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Flyway também na aplicação (se quiser)
    implementation("org.flywaydb:flyway-core:10.20.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.0")

    // ---- Classpath dedicado das tasks do Flyway (sem encadear .also / sem genéricos) ----
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

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.test {
    useJUnitPlatform()
}

/* ============================
 * Flyway via ENV (sem quebrar IDE)
 * ============================ */

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
