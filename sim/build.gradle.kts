import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDate
import java.time.ZoneOffset

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
        val date = LocalDate.now(ZoneOffset.UTC)
        rootProject.file("docs/BALANCE.md").appendText(
            "\n- ${date} — regeneracja złotych powtórek: ${reason}.\n"
        )
    }
}


// Wspólna konwencja nazw dla metod i klas - pozostałe testy są szybkie.
val requestedTestCategories = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .filter { it in setOf("fastTest", "screenshotTest", "balanceTest") }
    .distinct()
require(requestedTestCategories.size <= 1) {
    "Uruchom kategorie testów w osobnych wywołaniach Gradle."
}
val requestedTestCategory = requestedTestCategories.singleOrNull()
val categoryConfig = java.util.Properties().apply {
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
    description = "Testy symulacji bez długich pojedynków i raportów."
    dependsOn("test")
}

tasks.register("balanceTest") {
    group = "verification"
    description = "Długie testy symulacji i raporty balansu."
    dependsOn("test")
}
