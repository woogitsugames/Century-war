# Zlecenie 09 — muzyka proceduralna

## Zrealizowany zakres

- **MusicSynth.kt**: osiem autorskich motywów epok i spokojny motyw menu, krokowy sekwencer, perkusja, bas, akordy, melodia, tablice fal, 2-sekundowe przejścia; czysty Kotlin.
- **Music.kt**: AudioTrack PCM16, mono, 22 050 Hz, osobny wątek o niskim priorytecie, bufor ~250 ms, ponownie używane tablice próbek.
- **MainActivity.kt / Game.kt**: start, pauza i zwalnianie strumienia zgodnie z cyklem życia, wybór motywu menu poza bitwą.
- **BattleScreen.kt**: bieżąca epoka gracza i natężenie zależne od najbliższej żywej jednostki wroga przy bazie; brak ingerencji w symulację i snapshoty.
- **Profile.kt / SettingsScreen.kt / Screen.kt / L.kt**: zapamiętywana głośność muzyki (domyślnie 50%, zakres 0–100%), suwak dotykowy, lokalizacja etykiety na 10 języków.
- **MusicSynthTest.kt / ProfileTest.kt**: deterministyczność, zakres i skończoność próbek, przejścia między epokami, PCM i walidacja ustawienia po odczycie.

## Bazowanie i współpraca

Scalono istniejące commity gałęzi `gpt/09-music` z aktualną gałęzią `claude/bold-sagan-vwz5gm` bez force-push. Zachowano nowe funkcje profilu, UI, snapshotów i lokalizacji z gałęzi bazowej.

## Weryfikacja i ograniczenia

- W poprzednich commitach: kompilacja samodzielnego MusicSynth przez kotlinc i scenariusze testowe JVM (6 przypadków); dokumentacja szczegółowa w historii PR.
- W bieżącym środowisku nie ma Android SDK ani działającej instalacji Gradle i dostępu sieciowego do klonowania — nie uruchomiono `./gradlew :app:assembleDebug` ani `./gradlew :app:testDebugUnitTest`.
- Zmiany w ekranach i integracji nie były renderowane w Robolectric; zalecany screenshot test menu ustawień i test na urządzeniu Android.
- Limit CPU poniżej 3% jednego rdzenia na tanim telefonie wymaga profilowania na fizycznym urządzeniu. Brak nagrań audio i nowych zależności.
