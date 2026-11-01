import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

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
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnit()
}

// Jawna aktualizacja wzorców wymaga uzasadnienia zapisywanego w historii balansu.
tasks.register<JavaExec>("regenerateGolden") {
    group = "verification"
    description = "Odtwarza 10 złotych powtórek i zapisuje powód w docs/BALANCE.md."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.centurywar.game.GoldenFixtures")
    args("--regenerate")
    doFirst {
        val reason = providers.gradleProperty("goldenReason").orNull?.trim()
        check(!reason.isNullOrBlank()) {
            "Podaj -PgoldenReason='opis świadomej zmiany balansu i powtórek'"
        }
        check(!reason.contains('\n') && reason.length <= 240) {
            "Uzasadnienie musi być jedną linią (maks. 240 znaków)"
        }
    }
    doLast {
        val reason = providers.gradleProperty("goldenReason").get().trim()
        val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        rootProject.file("docs/BALANCE.md").appendText(
            "\n- ${date} — regeneracja złotych powtórek: ${reason}.\n"
        )
    }
}
