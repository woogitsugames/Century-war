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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

application {
    mainClass.set("com.centurywar.server.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
