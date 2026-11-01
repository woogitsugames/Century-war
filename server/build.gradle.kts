import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

val ktorVersion = "3.2.3"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sim"))
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.3")
}

application {
    mainClass.set("com.centurywar.server.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}


// Serwer również dzieli testy według nazw, bez duplikowania ciężkich raportów.
val requestedTestCategories = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .filter { it in setOf("fastTest", "screenshotTest", "balanceTest") }
    .distinct()
require(requestedTestCategories.size <= 1) {
    "Uruchom kategorie testów w osobnych wywołaniach Gradle."
}
val requestedTestCategory = requestedTestCategories.singleOrNull()
val categoryConfig = Properties().apply {
    rootProject.file("gradle/test-categories.properties").inputStream().use { load(it) }
}
val balanceTestPatterns = categoryConfig.getProperty("balance")?.split(',')
    ?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: error("Brak kategorii testów balance")

tasks.named<org.gradle.api.tasks.testing.Test>("test") {
    filter {
        when (requestedTestCategory) {
            "fastTest" -> balanceTestPatterns.forEach { excludeTestsMatching(it) }
            "balanceTest" -> balanceTestPatterns.forEach { includeTestsMatching(it) }
        }
    }
}

tasks.register("fastTest") {
    group = "verification"
    description = "Testy serwera bez długich raportów balansu."
    dependsOn("test")
}

tasks.register("balanceTest") {
    group = "verification"
    description = "Długie testy balansu po stronie serwera."
    dependsOn("test")
}
