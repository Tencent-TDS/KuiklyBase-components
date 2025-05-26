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
            resources.srcDir("src/main/resources")
        }

    }
}