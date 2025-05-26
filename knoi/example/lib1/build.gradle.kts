import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("com.tencent.kuiklybase.knoi.plugin")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class) kotlin {
    targetHierarchy.default()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    ohosArm64 {
        binaries.all {
            freeCompilerArgs += "-Xadd-light-debug=enable"
        }
    }

    sourceSets {
        val ohosArm64Main by getting {
            dependencies {
            }
        }
        val commonTest by getting {
            dependencies {
                api(kotlin("test"))
            }
        }
    }
}

knoi {
    debug = true
}

