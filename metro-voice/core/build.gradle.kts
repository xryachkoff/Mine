import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// ./gradlew :core:cli --args="import-osm data/osm/tbilisi-overpass.json app/src/main/assets/cities/tbilisi.json tbilisi"
tasks.register<JavaExec>("cli") {
    group = "metro-voice"
    description = "Developer tools: import-osm, replay"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("metrovoice.core.tools.CliKt")
    workingDir = rootDir
}
