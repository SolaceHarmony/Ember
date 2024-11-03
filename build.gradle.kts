// build.gradle.kts

val kotlinVersion = "2.0.21"
val logbackVersion = "1.5.6"

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.ktor.plugin") version "2.3.12"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "ai.solace.core.kognitive"
version = "0.0.1"

application {
    mainClass.set("ai.solace.core.KognitiveKt")

    val isDevelopment: Boolean = project.hasProperty("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor core dependencies
    // Note remove -jvm for other platforms
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")

    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-cio:2.3.11")
    implementation("io.ktor:ktor-client-auth:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-gson:2.3.12")

    // Scripting engine
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.0.0")


    // Content Negotiation
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")

    // CORS Support
    implementation("io.ktor:ktor-server-cors-jvm:2.3.12")

    // Logging
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.11")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:2.3.12")

    // Swagger/OpenAPI Support
    implementation("io.ktor:ktor-server-openapi-jvm:2.3.12")
    implementation("io.ktor:ktor-server-swagger-jvm:2.3.12")

    // WebSockets and Sessions
    implementation("io.ktor:ktor-server-sessions-jvm:2.3.12")
    implementation("io.ktor:ktor-server-websockets-jvm:2.3.12")

    // Observability
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:2.3.12")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.2")

    // Authentication
    implementation("io.ktor:ktor-server-auth-jvm:2.3.12")

    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}
