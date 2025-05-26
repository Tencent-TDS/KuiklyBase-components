import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("com.android.library")

}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Android平台
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        // 添加以下配置，将Android平台打包到组件产物中
        publishAllLibraryVariants()
        publishLibraryVariantsGroupedByFlavor = true
        publishLibraryVariants("release", "debug")
    }
    ohosArm64 {
        val main by compilations.getting
        val interop by main.cinterops.creating {
            includeDirs("$projectDir/src/nativeInterop/cinterop/cpp/include")
        }
        compilations.forEach {
            it.compilerOptions.options.optIn.addAll(
                "kotlinx.cinterop.ExperimentalForeignApi",
                "kotlin.experimental.ExperimentalNativeApi",
            )
        }
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.knoi.annotation)
                implementation("org.jetbrains.kotlinx:atomicfu:0.23.2-KBA-001")
//                api(project(":knoi-annotation"))
            }
        }
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

android {
    namespace = "com.tencent.kmm.knoi"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}