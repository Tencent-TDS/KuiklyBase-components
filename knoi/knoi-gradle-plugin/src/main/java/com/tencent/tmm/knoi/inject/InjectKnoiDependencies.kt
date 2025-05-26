package com.tencent.tmm.knoi.inject

import com.google.devtools.ksp.gradle.KspExtension
import com.tencent.tmm.knoi.KnoiExtension
import com.tencent.tmm.knoi.OPTION_CONFIG_FILE
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

val SUPPORTED_PLATFORMS = setOf(KotlinPlatformType.androidJvm, KotlinPlatformType.native)
fun injectKNOIConfig(
    project: Project, kmp: KotlinMultiplatformExtension, extension: KnoiExtension
) {
    try {
        project.pluginManager.apply("com.google.devtools.ksp")
    } catch (e: Exception) {
        error(
            "knoi requires KSP to be applied to the project. "
                    + "Please apply the KSP Gradle plugin ('com.google.devtools.ksp') to your buildscript and try again."
        )
    }
    kmp.targets.matching { it.platformType in SUPPORTED_PLATFORMS }.configureEach {
        val configName =
            "ksp${it.targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
        project.dependencies.add(configName, getKNOIProcessorDependencies(extension, project))
    }
    addKNOIDependencies(project, kmp, extension)
    project.extensions.configure(KspExtension::class.java) { ksp ->
        // 注：由于 KSP bug： https://github.com/google/ksp/issues/1524
        // 无法在 afterEvaluate 时注册 KSP，但 knoi 需要注入参数给到 processor
        // 而参数又需要 Module gradle apply 完成后
        // 故采用配置文件方式。
        ksp.arg(OPTION_CONFIG_FILE, getKNOIConfigPath(project))
    }
}

private fun addKNOIDependencies(
    project: Project,
    kmp: KotlinMultiplatformExtension,
    extension: KnoiExtension
) {
    kmp.sourceSets.getByName("commonMain").dependencies {
        implementation(getKNOIAnnotationDependencies(extension, project))
        implementation(getKNOIDependencies(extension, project))
    }
}

fun getKNOIAnnotationDependencies(extension: KnoiExtension, project: Project): Any {
    return if (extension.debug) {
        project.project(":knoi-annotation")
    } else {
        "com.tencent.kuiklybase:knoi-annotation:${getKNOIVersion(extension)}"
    }
}

fun getKNOIProcessorDependencies(extension: KnoiExtension, project: Project): Any {
    return if (extension.debug) {
        project.project(":knoi-processor")
    } else {
        "com.tencent.kuiklybase:knoi-processor:${getKNOIVersion(extension)}"
    }
}

fun getKNOIDependencies(extension: KnoiExtension, project: Project): Any {
    return if (extension.debug) {
        project.project(":knoi")
    } else {
        "com.tencent.kuiklybase:knoi:${getKNOIVersion(extension)}"
    }
}

fun getKNOIVersion(extension: KnoiExtension): String {
    var version = extension.javaClass.classLoader.getResource("version.txt")?.readText()
    if (version == null) {
        version = "0.0.1"
    }
    println("inject knoi version $version")
    return version
}

val Project.projectCacheDir
    get() = project.gradle.startParameter.projectCacheDir ?: this.rootProject.projectDir.resolve(".gradle")

fun getKNOIConfigPath(project: Project) =
    "${project.projectCacheDir.absolutePath}/knoi/${project.name}-config.ini"
