plugins {
    kotlin("multiplatform")
    id("com.tencent.kuiklybase.knoi.plugin")
    id("com.android.library")
    kotlin("native.cocoapods")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class) kotlin {
    targetHierarchy.default()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "11.0"
//        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "sample"
        }
    }

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
        binaries.sharedLib {
            baseName = "kn"
        }
        binaries.all {
            freeCompilerArgs += "-opt=true"
            freeCompilerArgs += "-Xadd-light-debug=enable"
            freeCompilerArgs += "-Xcontext-receivers"
            freeCompilerArgs += "-memory-model=experimental"
            freeCompilerArgs += "-Xbinary=gc=pmcs"
            freeCompilerArgs += "-Xbinary=sourceInfoType=libbacktrace"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":example:lib2"))
                api(project(":example:sample-api"))
            }
        }
        val ohosArm64Main by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-KBA-001")
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
    namespace = "com.tencent.kmm.knoi.example"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}

knoi {
    tsGenDir = rootDir.absolutePath + "/ohosApp/entry/src/main/ets/knoi"
    ignoreTypeAssert = false
    debug = true
}

tasks.register("copyBinariesToOhos", Copy::class) {
    println("copyBinariesToOhos to " + rootDir.absolutePath + "/ohosApp/entry/libs/arm64-v8a/")
    from(project.buildDir.absolutePath + "/bin/ohosArm64/debugShared/libkn.so")
    destinationDir = File(rootDir.absolutePath + "/ohosApp/entry/libs/arm64-v8a/")
}

tasks.register("copyTypeScriptToOhos", Copy::class) {
    from(projectDir.absolutePath + "/ts-api/")
    destinationDir = File(rootDir.absolutePath + "/ohosApp/entry/src/main/ets/knoi/")
}

tasks.findByName("ohosArm64Binaries")?.finalizedBy(tasks.findByName("copyBinariesToOhos"))
tasks.findByName("ohosArm64Binaries")?.finalizedBy(tasks.findByName("copyTypeScriptToOhos"))