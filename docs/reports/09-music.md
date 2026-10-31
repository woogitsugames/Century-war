# Zlecenie 09 — muzyka proceduralna

## Zmiany

- Dodano engine/MusicSynth.kt: czysto obliczeniowy, deterministyczny syntezator PCM bez Androida; 8 motywów epok i motyw menu, tablice fal, kroki 1/16 nuty, perkusja, bas, akordy, melodia, regulacja intensywności i głośności, crossfade 2 s.
- Dodano engine/Music.kt: AudioTrack mono / PCM16 / 22 050 Hz w osobnym wątku o niskim priorytecie; bufor około 250 ms, porcje 1024 próbek, bez alokacji i I/O w pętli generowania.
- MainActivity.kt: tworzenie muzyki oraz start/stop zgodnie z onResume/onPause/onDestroy.
- engine/Game.kt: pole music obok sound, wybór motywu menu lub początkowego motywu bitwy przy przejściu między ekranami.
- meta/Profile.kt: musicVolume, domyślnie 0.5, zapis/odczyt JSON z walidacją wartości.
- Test engine/MusicSynthTest.kt: 6 testów (skończoność próbek i zakres, powtarzalność seeda, crossfade 2 s, kolejka epok, PCM16 i nieprawidłowe sterowanie, różne seedy).

## Weryfikacja

Lokalnie: kompilacja MusicSynth.kt przez kotlinc — OK.
Kompilacja Music.kt razem z MusicSynth.kt przy użyciu atrap API Android — OK.
Samodzielny zestaw 6 scenariuszy uruchomiony na JVM — OK; maksymalna amplituda w badanych scenariuszach: 0.47227874 (przed ograniczeniem PCM).
Wstępny benchmark na JVM: około 15–20 ms CPU na 10,2 s dźwięku (nie jest to pomiar na urządzeniu Android).

NIE wykonano:
- ./gradlew :app:assembleDebug --console=plain
- ./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --console=plain
- ./gradlew :app:testDebugUnitTest --tests '*MusicSynthTest*' --console=plain

Przyczyna: środowisko wykonawcze nie ma Android SDK ani działającej instalacji Gradle, a bezpośrednie połączenie do serwerów GitHub jest niedostępne. Zmiany wprowadzono przez integrację repozytorium. Brak wyników tych poleceń nie oznacza ich zaliczenia.

## Zmiana wymagana w zakazanym BattleScreen.kt

Zgodnie z poleceniem NIE modyfikowano app/src/main/java/com/centurywar/screens/BattleScreen.kt.
Wskazany fragment w metodzie update(dt: Float) należy wstawić tuż przed playSounds() (po aktualizacji battle.step()):

~~~diff
@@
-        playSounds()
+        // Odtwarzana epoka wynika z rzeczywistego stanu bitwy.
+        g.music?.setEra(battle.player.era)
+        var danger = 0f
+        for (u in battle.units) {
+            if (!u.alive || u.side != 1) continue
+            val proximity = (1f - (u.x - battle.player.baseFront) / (Defs.LANE * 0.35f))
+                .coerceIn(0f, 1f)
+            if (proximity > danger) danger = proximity
+        }
+        g.music?.setIntensity(if (paused || finished) 0f else danger)
+        playSounds()
~~~

BattleScreen.kt już importuje Defs, więc nowe importy nie są potrzebne. Fragment nie alokuje obiektów; iteruje po stałej puli 72 jednostek.
Do czasu jego zastosowania na wejściu do bitwy wybierany jest motyw epoki 0; gra automatycznie nie przełączy melodii przy późniejszych awansach ani nie zmieni intensywności w zależności od zagrożenia.

## Ustawienia UI i ryzyka

SettingsScreen.kt również objęty jest zakazem edycji. W profilu istnieje pole musicVolume i Game.applyMusicVolume(), ale nie dodano jeszcze kontrolki głośności na ekranie ustawień. Po zmianie profilu można wywołać Game.applyMusicVolume() dla natychmiastowej aktualizacji.

- Android AudioTrack nie został zweryfikowany na fizycznym telefonie; trzeba sprawdzić urządzenia z nietypowymi sterownikami dźwięku i częste przejścia aplikacji w tło.
- Docelowy limit <3% CPU należy zmierzyć na tanim urządzeniu; benchmark lokalnej JVM tego nie gwarantuje.
- Gdy zmiana epoki następuje podczas crossfade, kolejna zmiana jest kolejkowana do jego zakończenia: zapobiega to gwałtownym skokom, lecz reakcja może być opóźniona do 2 sekund.
