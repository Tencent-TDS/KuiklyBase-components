import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("com.tencent.kuiklybase.knoi.plugin")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class) kotlin {
    targetHierarchy.default()
    ohosArm64 {
        binaries.sharedLib {
            baseName = "kn"
        }
        binaries.all {
            freeCompilerArgs += "-Xadd-light-debug=enable"
        }
    }

    sourceSets {
        val ohosArm64Main by getting {
            dependencies {
            }
        }
    }
}

