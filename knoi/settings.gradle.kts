pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}

rootProject.name = "knoi"
include(":knoi-annotation")
include(":knoi")
include(":knoi-processor")
include(":knoi-gradle-plugin")
//
include(":example:sample")
include(":example:sample-api")
include(":example:lib1")
include(":example:lib2")
include(":example:empty")
include(":example:knoiapp")
