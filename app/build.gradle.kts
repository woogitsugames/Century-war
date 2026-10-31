import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionCode = providers.gradleProperty("releaseVersionCode").getOrElse("1").toIntOrNull()
    ?: error("releaseVersionCode musi być liczbą całkowitą")
require(releaseVersionCode in 1..2_100_000_000) { "releaseVersionCode poza zakresem Google Play" }

// Tag bieżącego commita ma pierwszeństwo; na gałęziach roboczych używamy wartości z gradle.properties.
val releaseTagPattern = Regex("""v?\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""")
val releaseTag = providers.exec {
    workingDir = rootProject.projectDir
    commandLine("git", "tag", "--points-at", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().lineSequence()
    .map { it.trim() }
    .filter { releaseTagPattern.matches(it) }
    .sorted()
    .lastOrNull()
val releaseVersionName = releaseTag?.removePrefix("v")
    ?: providers.gradleProperty("releaseVersionName").getOrElse("0.1.0-dev")

// Klucz do publikacji pochodzi wyłącznie ze środowiska, nigdy z repozytorium.
val uploadStoreFile = providers.environmentVariable("CW_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("CW_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("CW_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("CW_UPLOAD_KEY_PASSWORD").orNull
val uploadSecrets = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
val hasUploadSigning = uploadSecrets.all { !it.isNullOrBlank() }
require(uploadSecrets.all { it.isNullOrBlank() } || hasUploadSigning) {
    "Ustaw wszystkie cztery zmienne CW_UPLOAD_*, albo żadnej"
}
val releaseTestSigning = providers.gradleProperty("releaseTestSigning").orNull == "true"
require(!releaseTestSigning || !hasUploadSigning) {
    "Nie łącz releaseTestSigning z kluczem produkcyjnym"
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
        if (hasUploadSigning) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword!!
                keyAlias = uploadKeyAlias!!
                keyPassword = uploadKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Bez klucza upload release jest niepodpisany; debug podpis tylko po jawnym żądaniu.
            signingConfig = when {
                hasUploadSigning -> signingConfigs.getByName("upload")
                releaseTestSigning -> signingConfigs.getByName("debug")
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
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}
