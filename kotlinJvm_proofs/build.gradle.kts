// build.gradle.kts

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "ai.solace.core.kognitive.kotlinJvm_proofs"
version = "0.0.1"


repositories {
    mavenCentral()
    maven {
        name = "reposiliteRepository"
        url = uri("https://maven.sciprog.center/kscience")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
    runtimeOnly("space.kscience:kmath-core:0.3.1")
    implementation("space.kscience:plotlykt-script:0.6.0")


    // Scripting engine
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.0.0")

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
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}
