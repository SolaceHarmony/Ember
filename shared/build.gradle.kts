plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.20"
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                entryPoint = "ai.solace.core.kognitive.main"
            }
            framework {
                baseName = "LNNTransformer"
                isStatic = true
            }

        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                runtimeOnly("space.kscience:kmath-core:0.3.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val macosArm64Main by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-macosarm64:1.9.0")
            }
        }
    }
}

tasks.named("build") {
    dependsOn("linkDebugFrameworkMacosArm64")
}