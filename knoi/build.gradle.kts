plugins {
    id("com.android.application").version("8.1.0").apply(false)
    id("com.android.library").version("8.1.0").apply(false)
    kotlin("multiplatform").version("2.0.21-KBA-003").apply(false)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    id("com.tencent.kuiklybase.knoi.plugin").version("0.0.4").apply(false)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

allprojects {
    if (project != rootProject && !project.path.contains("example")) {
        apply(from = file(rootProject.file("gradle/publishing.gradle")))
    }
}