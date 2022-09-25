import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
}

group = "dev.zerite"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    api(kotlin("stdlib"))

    // Ktor
    api("io.ktor:ktor-client-okhttp:${project.property("ktor_version")}")
    api("io.ktor:ktor-client-content-negotiation:${project.property("ktor_version")}")
    api("io.ktor:ktor-serialization-kotlinx-json:${project.property("ktor_version")}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
