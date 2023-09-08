plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "com.github.zhangt2333.spider"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Kotlin HTML
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.9.1")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    // jsoup
    implementation("org.jsoup:jsoup:1.16.1")
    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
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
