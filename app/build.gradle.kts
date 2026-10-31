import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

// ---- Podpis release ------------------------------------------------------------------------
// Klucz upload wyłącznie ze środowiska (CI: sekrety GitHub, patrz docs/CI.md). Brak wszystkich
// zmiennych oznacza lokalny podpis kluczem debug (jak dotąd); konfiguracja częściowa jest błędem.
val releaseStoreFile = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
check(!releaseSigningValues.any { !it.isNullOrBlank() } || hasReleaseSigning) {
    "Niekompletne zmienne RELEASE_KEYSTORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"
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
    }

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
            // Bez sekretów lokalne kompilacje release używają klucza debug (nie do publikacji).
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sim"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}
