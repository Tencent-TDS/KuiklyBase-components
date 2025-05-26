plugins {
    kotlin("multiplatform")
    id("com.tencent.kuiklybase.knoi.plugin")
    id("com.android.library")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class) kotlin {
    targetHierarchy.default()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    ohosArm64 {
    }

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    sourceSets {
        val ohosArm64Main by getting {
            dependencies {
            }
        }
    }
}

android {
    namespace = "com.tencent.kmm.knoi.example.api"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}

knoi {
    debug = true
}

