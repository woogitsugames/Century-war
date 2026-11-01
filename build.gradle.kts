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

tasks.register("fastTest") {
    group = "verification"
    description = "Testy aplikacji, symulacji i serwera bez ciężkich raportów oraz zrzutów."
    dependsOn(":app:fastTest", ":sim:fastTest", ":server:fastTest")
}

tasks.register("screenshotTest") {
    group = "verification"
    description = "Testy zrzutów ekranu i renderowania aplikacji."
    dependsOn(":app:screenshotTest")
}

tasks.register("balanceTest") {
    group = "verification"
    description = "Raporty balansu i długie scenariusze aplikacji, symulacji i serwera."
    dependsOn(":app:balanceTest", ":sim:balanceTest", ":server:balanceTest")
}
