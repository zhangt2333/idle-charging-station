plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "com.github.zhangt2333.spider"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // Kotlin HTML
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // jsoup
    implementation("org.jsoup:jsoup:1.17.2")
    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("SpiderKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
