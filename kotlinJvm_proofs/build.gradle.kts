// build.gradle.kts

plugins {
    kotlin("jvm") version "2.0.20"
}

group = "ai.solace.core.kognitive.kotlinJvm_proofs"
version = "0.0.1"


repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
    implementation("space.kscience:kmath-core-jvm:0.3.1")
    implementation("org.jetbrains.kotlinx:multik-core:0.2.3")
    implementation("org.jetbrains.kotlinx:multik-default:0.2.3")
    implementation("org.jetbrains.kotlinx:multik-openblas:0.2.3")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-tensorflow:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-api:0.3.0")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-impl-jvm:0.5.1")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-onnx-jvm:0.5.1")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-visualization-jvm:0.5.1")
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}


kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}
