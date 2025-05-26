
plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

sourceSets {
    main {
        resources {
            srcDirs(buildDir.absolutePath + "/version/" )
        }
    }
}

dependencies {
    //implementation(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21-KBA-003")
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
    implementation(gradleApi())
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

gradlePlugin {
    plugins {
        create(property("ID").toString()) {
            id = property("ID").toString()
            implementationClass = property("IMPLEMENTATION_CLASS").toString()
            version = version
            description = property("DESCRIPTION").toString()
            displayName = property("DISPLAY_NAME").toString()
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

tasks.findByName("processResources")?.dependsOn(tasks.findByName("genVersionFile"))