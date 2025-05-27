val kspVersion: String by project
plugins {
    kotlin("multiplatform")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    sourceSets {

        commonMain.dependencies {
//            api(project(":knoi-annotation"))
            api(libs.knoi.annotation)
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.squareup:kotlinpoet-ksp:1.16.0")
                implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDirs("src/main/resources", buildDir.absolutePath + "/version/")
        }

    }
}

// 生成 version.txt，用于注入版本
tasks.register("genVersionFile") {
    val file = File(buildDir.absolutePath + "/version/version.txt")
    file.delete()
    file.parentFile.mkdirs()
    file.createNewFile()
    val version = project.version.toString()
    println("genVersionFile version = $version")
    file.writeText(version)
}

tasks.findByName("jvmProcessResources")?.dependsOn(tasks.findByName("genVersionFile"))