import com.centurywar.build.ReleaseChecklist
import com.centurywar.build.ReleaseChecklistInput
import java.time.LocalDate
import java.time.ZoneOffset
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---- Wersjonowanie -------------------------------------------------------------------------
// versionCode: releaseVersionCode z gradle.properties (zwiększ przed każdym wydaniem w Google Play).
val releaseVersionCode = providers.gradleProperty("releaseVersionCode").getOrElse("1").toIntOrNull()
    ?: error("releaseVersionCode musi być liczbą całkowitą")
require(releaseVersionCode in 1..2_100_000_000) { "releaseVersionCode poza zakresem Google Play" }

// versionName: tag SemVer bieżącego commita (v1.2.3 -> 1.2.3), inaczej releaseVersionName.
// Brak gita (np. build ze źródeł bez .git) nie psuje builda.
val releaseTagPattern = Regex("""v?\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""")
val releaseTag = runCatching {
    providers.exec {
        workingDir = rootProject.projectDir
        commandLine("git", "tag", "--points-at", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get()
}.getOrDefault("").lineSequence()
    .map { it.trim() }
    .filter { releaseTagPattern.matches(it) }
    .sorted()
    .lastOrNull()
val releaseVersionName = releaseTag?.removePrefix("v")
    ?: providers.gradleProperty("releaseVersionName").getOrElse("0.1.0-dev")

// Konfiguracja rankingu tylko z lokalnego pliku lub zmiennych CI; bez wartości domyślnych.
val localSettings = Properties().apply {
    val settings = rootProject.file("local.properties")
    if (settings.exists()) settings.inputStream().use { load(it) }
}
fun scoreSetting(key: String): String =
    providers.environmentVariable(key).orNull ?: localSettings.getProperty(key, "")
fun quotedBuildValue(value: String): String = "\"" +
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
val scoreServerUrl = scoreSetting("CW_SERVER_URL").trim()
val scoreHmacKey = scoreSetting("CW_HMAC_KEY")
val scorePrivacyUrl = scoreSetting("CW_PRIVACY_URL").trim()

// ---- Podpis release ------------------------------------------------------------------------
// Klucz upload wyłącznie ze środowiska (CI: sekrety GitHub, patrz docs/CI.md).
// Brak podpisu nigdy nie oznacza automatycznego użycia klucza debug.
val releaseStoreFile = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
check(!releaseSigningValues.any { !it.isNullOrBlank() } || hasReleaseSigning) {
    "Niekompletne zmienne RELEASE_KEYSTORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"
}
val ciTestDebugSigning = providers.gradleProperty("ciTestDebugSigning").orNull == "true"
val ciDebugSigningAllowed = ReleaseChecklist.canUseCiDebugSigning(
    ciTestDebugSigning,
    providers.environmentVariable("GITHUB_ACTIONS").orNull,
    providers.environmentVariable("GITHUB_EVENT_NAME").orNull,
    providers.environmentVariable("CW_RELEASE_SMOKE").orNull,
)
check(!ciTestDebugSigning || ciDebugSigningAllowed) {
    "-PciTestDebugSigning=true jest dozwolone wyłącznie w oznaczonym smoke-teście CI dla PR/manual."
}
val releaseKeyIsDebug = releaseKeyAlias.equals("androiddebugkey", ignoreCase = true) ||
    releaseStoreFile?.let { file(it).name.equals("debug.keystore", ignoreCase = true) } == true
val validReleaseSigning = hasReleaseSigning && !releaseKeyIsDebug &&
    releaseStoreFile?.let { file(it).isFile } == true

// Zabezpieczenie dodatkowe: nawet -x releaseChecklist nie omija wymogu podpisu.
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') in setOf("assembleRelease", "bundleRelease", "releaseChecklist")
}
check(!releaseTaskRequested || validReleaseSigning || ciDebugSigningAllowed) {
    "Brak prawidłowego podpisu release: wymagany upload JKS i cztery zmienne RELEASE_*."
}

android {
    namespace = "com.centurywar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.centurywar.game"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        buildConfigField("String", "CW_SERVER_URL", quotedBuildValue(scoreServerUrl))
        buildConfigField("String", "CW_HMAC_KEY", quotedBuildValue(scoreHmacKey))
        buildConfigField("String", "CW_PRIVACY_URL", quotedBuildValue(scorePrivacyUrl))
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Podpis debug jest dostępny tylko dla świadomego testu w CI.
            signingConfig = when {
                hasReleaseSigning -> signingConfigs.getByName("release")
                ciDebugSigningAllowed -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric widzi zasoby aplikacji (ekran startowy, ikony).
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sim"))
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}


// Szybka ścieżka uruchamiana przez :app:quickCheck albo zadanie główne quickCheck.
val quickCheckRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') == "quickCheck"
}


// Jedna kategoria na wywołanie: filtry działają na oryginalnym teście AGP,
// dzięki czemu Robolectric zachowuje zasoby i konfigurację Androida.
val requestedTestCategories = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .filter { it in setOf("fastTest", "screenshotTest", "balanceTest") }
    .distinct()
require(requestedTestCategories.size <= 1) {
    "Uruchom fastTest, screenshotTest i balanceTest w osobnych wywołaniach Gradle."
}
val requestedTestCategory = requestedTestCategories.singleOrNull()
val testCategories = Properties().apply {
    rootProject.file("gradle/test-categories.properties").inputStream().use { load(it) }
}
fun categoryPatterns(category: String): List<String> =
    testCategories.getProperty(category)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: error("Brak kategorii testów: $category")
val screenshotTestPatterns = categoryPatterns("screenshots")
val balanceTestPatterns = categoryPatterns("balance")

// Limit dwóch procesów JVM i pamięci, również na komputerach z 8 GB RAM.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val configuredForks = providers.gradleProperty("testForks").orNull?.toIntOrNull()
    maxParallelForks = (configuredForks ?: 2).coerceIn(1, 2)
    forkEvery = 15L
    maxHeapSize = "1024m"
    testLogging.showStandardStreams = providers.environmentVariable("PERF_REPORT").orNull == "true"

    if (name == "testDebugUnitTest") {
        filter {
            when (requestedTestCategory) {
                "fastTest" -> (screenshotTestPatterns + balanceTestPatterns)
                    .forEach { excludeTestsMatching(it) }
                "screenshotTest" -> screenshotTestPatterns.forEach { includeTestsMatching(it) }
                "balanceTest" -> balanceTestPatterns.forEach { includeTestsMatching(it) }
            }
        }
    }

    if (quickCheckRequested && name == "testDebugUnitTest") {
        filter {
            listOf(
                "com.centurywar.game.BattleRulesTest",
                "com.centurywar.game.BattleSnapshotTest",
                "com.centurywar.game.ReplayTest",
                "com.centurywar.game.EndlessCardsTest",
                "com.centurywar.game.AiPersonalityTest",
                "com.centurywar.game.DailyTest",
                "com.centurywar.game.BattleSimTest.deterministicForSameSeed",
                "com.centurywar.engine.LTest",
                "com.centurywar.meta.ProfileTest",
                "com.centurywar.screens.BattleScreenTest",
            ).forEach { includeTestsMatching(it) }
        }
    }
}

// Korzysta z oryginalnego testDebugUnitTest: zachowuje zasoby i konfigurację Robolectric.
tasks.register("quickCheck") {
    group = "verification"
    description = "Buduje debug APK i wykonuje szybki zestaw testów symulacji oraz UI."
    dependsOn("assembleDebug", "testDebugUnitTest", ":sim:test")
}

// Domyślne :app:test obejmuje wyłącznie wariant debug. Release sprawdza osobna
// lista kontrolna; konfiguracja testReleaseUnitTest nie uruchamia jej przypadkiem.
tasks.matching { it.name == "test" }.configureEach {
    setDependsOn(listOf("testDebugUnitTest"))
}

tasks.register("fastTest") {
    group = "verification"
    description = "Wszystkie testy aplikacji bez ciężkich zrzutów i raportów balansu."
    dependsOn("testDebugUnitTest")
}

tasks.register("screenshotTest") {
    group = "verification"
    description = "Testy graficzne i zrzuty ekranu Robolectric."
    dependsOn("testDebugUnitTest")
}

tasks.register("balanceTest") {
    group = "verification"
    description = "Testy balansu, długie symulacje i raporty."
    dependsOn("testDebugUnitTest")
}


// ---- Kontrola przed publikacją ---------------------------------------------------------------
// Nie podpinaj do zadań debug. Każdy release (APK i AAB) wymaga tej samej kontroli.
val dailySourceFile = rootProject.file("sim/src/main/kotlin/com/centurywar/game/Modes.kt")
val gamesIdsFile = file("src/main/res/values/games_ids.xml")
val gamesManifestFile = file("src/main/AndroidManifest.xml")
val lastVersionCodeFile = rootProject.file("release/last_version_code.txt")
val storeDocsDir = rootProject.file("docs/store")
val ownerInputFile = rootProject.file("docs/store/OWNER_INPUT.md")
val privacyTemplates = listOf("privacy_policy_pl.md", "privacy_policy_en.md")
val skippedReleaseChecks = providers.gradleProperty("skipCheck").getOrElse("")
check("ownerInput" !in ReleaseChecklist.parseSkips(skippedReleaseChecks) || ciDebugSigningAllowed) {
    "-PskipCheck=ownerInput jest dozwolone wyłącznie w smoke-teście CI."
}
val releaseHasHmacKey = scoreHmacKey.isNotBlank()

val releaseChecklist = tasks.register("releaseChecklist") {
    group = "verification"
    description = "Sprawdza konfigurację, datę i numer publikacji przed release."
    inputs.files(dailySourceFile, gamesIdsFile, gamesManifestFile, lastVersionCodeFile)
    inputs.files(fileTree(storeDocsDir) { include("*.md") })
    inputs.property("releaseVersionCode", releaseVersionCode)
    inputs.property("scoreServerUrl", scoreServerUrl)
    inputs.property("scorePrivacyUrl", scorePrivacyUrl)
    inputs.property("hasHmacKey", releaseHasHmacKey)
    inputs.property("validReleaseSigning", validReleaseSigning)
    inputs.property("ciDebugSigningAllowed", ciDebugSigningAllowed)
    inputs.property("skipCheck", skippedReleaseChecks)

    doLast {
        val checks = ReleaseChecklistInput(
            releaseDay = ReleaseChecklist.releaseDayFromSource(
                dailySourceFile.takeIf { it.isFile }?.readText().orEmpty(),
            ),
            todayUtcDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            playGamesEnabled = gamesManifestFile.takeIf { it.isFile }?.readText()
                ?.contains("com.google.android.gms.games.APP_ID") == true,
            gamesIdsXml = gamesIdsFile.takeIf { it.isFile }?.readText() ?: "REPLACE_ME",
            serverUrl = scoreServerUrl,
            privacyUrl = scorePrivacyUrl,
            hasHmacKey = releaseHasHmacKey,
            versionCode = releaseVersionCode,
            lastVersionCode = lastVersionCodeFile.takeIf { it.isFile }
                ?.readText()?.trim()?.toIntOrNull(),
            skipChecks = ReleaseChecklist.parseSkips(skippedReleaseChecks),
            hasReleaseSigning = validReleaseSigning,
            ciTestDebugSigning = ciDebugSigningAllowed,
            enforceStoreDocuments = true,
            storeDocuments = storeDocsDir.listFiles()
                ?.filter { it.isFile && it.extension == "md" }
                ?.associate { it.name to it.readText() }.orEmpty(),
            ownerInputText = ownerInputFile.takeIf { it.isFile }?.readText(),
        )
        val problems = ReleaseChecklist.problems(checks)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "LISTA KONTROLNA WYDANIA NIEZALICZONA:\n" +
                    problems.joinToString("\n") { " - $it" } +
                    "\nWyjątek tylko po świadomej decyzji: " +
                    "-PskipCheck=releaseDay,playGames,serverUrl,privacyUrl,hmacKey,versionCode " +
                    "(wybierz wyłącznie potrzebne flagi; patrz docs/RELEASE.md).",
            )
        }
        logger.lifecycle("Lista kontrolna wydania: OK (pominięte: " +
            (checks.skipChecks.sorted().joinToString(", ").ifEmpty { "brak" }) + ")")
    }
}

// preReleaseBuild jest poprzednikiem kompilacji release, więc checklista kończy się przed nią.
tasks.matching {
    it.name == "preReleaseBuild" || it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    dependsOn(releaseChecklist)
}


// Polityki przeznaczone do umieszczenia na stronie: dane z OWNER_INPUT.md.
tasks.register("renderStorePolicies") {
    group = "publishing"
    description = "Przygotowuje polskie i angielskie polityki do opublikowania."
    inputs.files(ownerInputFile, fileTree(storeDocsDir) { include("privacy_policy_*.md") })
    val destination = rootProject.layout.buildDirectory.dir("store-policies")
    outputs.dir(destination)
    doLast {
        val ownerText = ownerInputFile.takeIf { it.isFile }?.readText()
        val problems = ReleaseChecklist.ownerInputProblems(ownerText)
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
        val docs = privacyTemplates.associateWith { storeDocsDir.resolve(it).readText() }
        val errors = ReleaseChecklist.storeDocumentProblems(
            storeDocsDir.listFiles()
                ?.filter { it.isFile && it.extension == "md" }
                ?.associate { it.name to it.readText() }.orEmpty(),
        )
        if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n"))
        val fields = ReleaseChecklist.ownerFields(ownerText)
        val out = destination.get().asFile.apply { mkdirs() }
        docs.forEach { (name, template) ->
            val published = ReleaseChecklist.renderOwnerTemplate(template, fields)
                .replace(" (źródło: [OWNER_INPUT.md](OWNER_INPUT.md))", "")
                .replace(" (source: [OWNER_INPUT.md](OWNER_INPUT.md))", "")
            check(!published.contains("{{OWNER.")) { "Nierozwiązany znacznik w " + name }
            out.resolve(name).writeText(published)
        }
        logger.lifecycle("Polityki do publikacji: " + out.absolutePath)
    }
}
