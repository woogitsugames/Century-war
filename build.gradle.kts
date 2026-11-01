plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}

tasks.register("quickCheck") {
    group = "verification"
    description = "Szybka kompilacja i wybrane testy aplikacji bez zrzutów."
    dependsOn(":app:quickCheck")
}
