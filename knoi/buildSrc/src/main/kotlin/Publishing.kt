@file:Suppress("UnstableApiUsage")

import org.gradle.api.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.provider.*
import org.gradle.api.publish.maven.*
import org.gradle.plugins.signing.*
import java.net.*

infix fun <T> Property<T>.by(value: T) {
    set(value)
}

fun MavenPom.configureMavenCentralMetadata(project: Project) {
    name by project.name
    description by "Kotlin Native Ohos Interaction"
    url by "https://github.com/Tencent-TDS/KuiklyBase-components"

    licenses {
        license {
            name by "The Apache Software License, Version 2.0"
            url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution by "repo"
        }
    }

    developers {
        developer {
            id by "Tencent-TDS"
            name by "Tencent-TDS"
            organization by "Tencent-TDS"
            organizationUrl by "https://framework.tds.qq.com/"
        }
    }

    scm {
        url by "https://github.com/Tencent-TDS/KuiklyBase-components"
    }
}

fun configureMavenPublication(rh: RepositoryHandler, project: Project) {
    rh.maven {
        val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
        val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots"
        val version = project.version.toString()
        url = URI(if (version.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
        credentials {
            username = project.getSensitiveProperty("libs.sonatype.user")
            password = project.getSensitiveProperty("libs.sonatype.password")
        }
    }
}

fun signPublicationIfKeyPresent(project: Project, publication: MavenPublication) {
    project.extensions.configure<SigningExtension>("signing") {
        sign(publication)
    }
}

private fun Project.getSensitiveProperty(name: String): String? {

    val localProperties = project.rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { file ->
            java.util.Properties().apply {
                load(file.inputStream())
            }
        } ?: java.util.Properties()

    return localProperties.getProperty(name)
}
