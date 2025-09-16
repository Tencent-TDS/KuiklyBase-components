import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import java.util.*

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("com.android.library")
    id("com.tencent.kuiklybase.knoi.plugin")
    `maven-publish`
    signing
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // 使用默认层级结构模板
    KotlinHierarchyTemplate.default

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

    // iOS平台
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        name = "network"
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "12.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "network"
            // 如果依赖的 pod 组件是静态库，那么建议打开下面注释
            // 同时需要去除 iosApp 中对应脚本：iosApp Target -> Build Phases -> Run Script
//            isStatic = true
        }
    }

    // Harmony Native平台
    ohosArm64 {

        val main by compilations.getting

        // 需要在shared里调来自外部C的时候加这个
        val interop by main.cinterops.creating {
//            includeDirs("src/nativeInterop/cinterop/include")
            includeDirs("${project.rootDir}/ohosApp/pbcurlwrapper/src/main/cpp/wrapper/include")
        }

        compilations.forEach {
            // 抑制 NativeApi 提示
            it.compilerOptions.options.optIn.addAll(
                "kotlinx.cinterop.ExperimentalForeignApi",
                "kotlin.experimental.ExperimentalNativeApi",
            )
        }
        binaries.all {
            freeCompilerArgs += "-Xadd-light-debug=enable"
            freeCompilerArgs += "-Xbinary=sourceInfoType=libbacktrace"
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.ktor.client.core)
                // Android 平台的 Ktor 引擎
                implementation(libs.ktor.client.android)
            }
        }

        val commonMain by getting {
            dependencies {
                // 协程
                implementation(libs.kotlinx.coroutines.core)
                // 原子操作
                implementation(libs.atomicfu)
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation(libs.ktor.ktor.client.core)
                // iOS 平台的 Ktor 引擎
                implementation(libs.ktor.client.darwin)
            }
        }

        val ohosArm64Main by getting {
            dependencies {
                // 鸿蒙依赖
            }
        }

    }
}

android {
    namespace = "com.tencent.tmm.networkkmm"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}

// Stub secrets to let the project sync and build without the publication values set up
extra["signing.keyId"] = null
extra["signing.password"] = null
extra["signing.secretKeyRingFile"] = null
extra["ossrhUsername"] = null
extra["ossrhPassword"] = null

// Grabbing secrets from local.properties file or from environment variables, which could be used on CI
val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        extra[name.toString()] = value
    }
} else {
    extra["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    extra["signing.password"] = System.getenv("SIGNING_PASSWORD")
    extra["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    extra["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    extra["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

val publishFile = project.rootProject.file("gradle.properties")
if (publishFile.exists()) {
    publishFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        extra[name.toString()] = value
    }
}

val publishGroupID = extra["gruopID"] as String
val publishVersion = extra["mavenVersion"] as String
val publishArtifactID = extra["artifactID"] as String

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

fun getExtraString(name: String) = try {
    extra[name]?.toString()
} catch (ignore: Exception) {
    null
}

publishing {
    // Configure maven central repository
    repositories {
        maven {
            name = "maven"
//            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
//            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
            url = if (publishVersion.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
    }

    // Configure all publications
    publications.withType<MavenPublication> {
        // Stub javadoc.jar artifact
//        artifact(javadocJar.get())

        // Provide artifacts information requited by Maven Central
        pom {
            name.set(publishArtifactID)
            description.set("Tencent Http Service Library.")
            url.set("https://github.com/Tencent-TDS/KuiklyBase-components.git")

            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("Tencent-TDS")
                    name.set("Tencent-TDS")
                    organization.set("Tencent-TDS")
                    organizationUrl.set("https://framework.tds.qq.com/")
                }
            }
            scm {
                url.set("https://github.com/Tencent-TDS/KuiklyBase-components")
            }
        }
    }
}

extensions.configure<SigningExtension> {
    sign(publishing.publications)
}