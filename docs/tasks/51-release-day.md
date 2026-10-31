# Zlecenie 51: Dzień wydania i lista kontrolna przed publikacją

**Gałąź robocza:** `gpt/51-release-day` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

---

# Prompt ogólny (wspólny dla wszystkich zleceń)

Pracujesz nad grą **Century War**, oryginalną strategią na Androida typu „wojna na jednym pasie przez epoki”. Nazwy, jednostki, grafika i mechaniki są własne: **nie kopiuj niczego z innych gier i nie umieszczaj w grze ani materiałach nawiązań do innych gier**.

**Repozytorium:** `woogitsugames/century-war` (GitHub). **Gałąź bazowa:** `claude/bold-sagan-vwz5gm`.

## Zanim zaczniesz
1. Sklonuj repo i utwórz swoją gałąź z **najnowszej** gałęzi bazowej: `git fetch origin claude/bold-sagan-vwz5gm && git checkout -b <TWOJA_GAŁĄŹ> origin/claude/bold-sagan-vwz5gm`. Nazwa gałęzi jest podana w zleceniu.
2. Przeczytaj `README.md`, `docs/GDD.md` (projekt gry, plan rozwoju) i `docs/HANDOFF.md` (stan i ryzyka).
3. Struktura kodu (trzy moduły Gradle):
   - **`:sim`** – `sim/src/main/kotlin/com/centurywar/game/`: symulacja bitwy (czysty Kotlin/JVM, bez `android.*`, **deterministyczna**): `Battle.kt`, `Ai.kt`, `AiPersonality.kt`, `Defs.kt` (balans), `Modes.kt` (kampania, tryby, `BattleConfig`, `Bonuses`, `Daily`, `Rng`), `EndlessCards.kt`, `BattleController.kt` (jedyna droga komend gracza, nagrywa log), `Replay.kt` (kodowanie i weryfikacja powtórek), `BattleSnapshot.kt` (zapis i wznawianie przerwanej bitwy, format binarny z wersją). Ten sam kod kompiluje serwer.
   - **`:server`** – `server/`: backend rankingu (Ktor + SQLite), weryfikuje powtórki przez `Replay.verify`; opis API i podpisu HMAC w `server/README.md`.
   - **`:app`** – `app/src/main/java/com/centurywar/`:
     - `game/`: renderer `BattleRenderer.kt` + rysunki w `game/art/`,
     - `engine/`: pętla gry (`GameView`), `Game`, `Screen`, `Ui`, `Icons`, `L` (**teksty w 10 językach**), `Quality`, `Sound`, `Music`,
     - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `Achievements`, `Missions`, `MissionTexts`, `OnlineLeaderboards`, `PlayGamesLeaderboards`, `ScoreClient`/`ScoreQueue` (ranking globalny), `ReplayStore`, `BattleHistory`, `SkirmishSetup`,
     - `notifications/`: przypomnienie o wyzwaniu dnia,
     - `screens/`: menu, kampania, tryby (`ModeScreen`, `SkirmishSetupScreen`), badania, rankingi (`RankingsScreen`, `GlobalRankingsScreen`, `StatsScreen`), powtórki (`ReplayListScreen`, `ReplayScreen`), ustawienia (`SettingsScreen`, `AccessibilityScreen`, `HintSettingsScreen`), kodeks, misje, osiągnięcia, bitwa (`BattleScreen`, HUD, `BattleSfx`), `TutorialOverlay`, `HintOverlay`, `Chrome`, `MenuBg`,
     - `MainActivity.kt`.
   - Testy symulacji i aplikacji są w `app/src/test/java/com/centurywar/...` (także dla kodu z `:sim`), testy serwera w `server/src/test/kotlin/...`.

## Środowisko (obowiązkowe — w poprzedniej turze kilka gałęzi się nie kompilowało)
Kod, którego nie skompilowałeś i nie przetestowałeś, **nie jest gotowy**. Jeśli w Twoim środowisku nie ma Android SDK, zainstaluj go:
```bash
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk
curl -sSfL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-36" "build-tools;36.0.0" "platform-tools"
cd <repo> && echo "sdk.dir=$HOME/android-sdk" > local.properties
```
Jeśli Maven Central zwraca 429, przekieruj go na mirror `https://maven-central.storage-download.googleapis.com/maven2/` skryptem init Gradle (`~/.gradle/init.d/`), a obraz Robolectric (`org.robolectric:android-all-instrumented`) pobierz z tego mirrora do `~/.m2/repository`.
**Jeśli mimo to nie możesz uruchomić Gradle, napisz to na początku raportu wielkimi literami i nie twierdź, że testy przechodzą.**

Pułapki z poprzednich tur:
- Testy w `app/src/test` kompilują się z `android.jar`: nie ma tam `com.sun.*`, `java.lang.management`, `jdk.jfr` (użyj refleksji albo umieść test gdzie indziej).
- Nie wywołuj prywatnych metod refleksją po sygnaturze w testach, jeśli możesz tego uniknąć; zmiana parametrów psuje test dopiero w czasie uruchomienia.
- `Fighter.alive` to właściwość tylko do odczytu (`active && dying < 0f`).
- Raporty balansu (`BalanceReport`) uruchamiaj naprawdę i wklejaj liczby; nie oddawaj kryterium „niezweryfikowane”.

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek** (wyjątek: `play-services-games-v2`). Dodaj zależność tylko wtedy, gdy zlecenie wprost na to pozwala.
- Wolno edytować wszystkie pliki potrzebne do zlecenia. Zmiany w plikach współdzielonych (`Battle.kt`, `Ai.kt`, `BattleScreen.kt`, `L.kt`, `Profile.kt`, `MainActivity.kt`) rób możliwie małe i addytywne, bo inne zlecenia pracują równolegle.
- **Teksty UI:** tabela w `engine/L.kt` jest podzielona na funkcje `part0()`…`partN()` (limit 64 KB bajtkodu jednej metody JVM). Nowe klucze dopisuj **na końcu ostatniej części** albo dodaj nową część `partN+1()` i wywołanie `putAll`. Format `"klucz" to t("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")`, czyli **wszystkie 10 języków** (kolejność `L.CODES`). Etykiety przycisków krótkie. Test `LTest` sprawdza komplet języków, zgodność placeholderów (`%d`, `%s`) i brak zdublowanych kluczy, więc musi przechodzić.
- **Symulacja musi zostać deterministyczna:** w `sim/` nie używaj `System.currentTimeMillis()`/`nanoTime()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng` (`Rng`, osobne strumienie AI). Testy `deterministicForSameSeed` i `ReplayTest` muszą przechodzić.
- **Komendy gracza** idą wyłącznie przez `BattleController` (inaczej nie trafią do powtórki i serwer odrzuci wynik).
- **Zapis bitwy:** jeśli dodajesz pole stanu do `Battle`/`Side`/`Fighter`/`Projectile`/`Ai`/`BattleConfig`/`BattleController`, dopisz je do `BattleSnapshot.kt` (zapis i odczyt), **podbij `BattleSnapshot.VERSION`** (obecnie 11) i dodaj przypadek do `BattleSnapshotTest`, który sprawdza, że wznowiona bitwa daje identyczny wynik. Zmiana formatu powtórki = podbij `Replay.VERSION` (obecnie 4). Zmiana przebiegu symulacji (balans, AI, nowe reguły) = podbij wersję gry rankingu `ScoreClient.GAME_VERSION` i `CW_GAME_VERSION` w `server/` (obecnie `0.3.0`).
- **Zapis profilu:** nowe pola w `Profile` muszą być kompatybilne wstecz (brak pola w starym JSON = wartość domyślna) i przycinane do sensownych zakresów przy odczycie (patrz `ProfileTest`).
- **Grafika:** rysowana kodem w stylu płaskiego cartoonu z grubym ciemnym konturem. Kolor drużyny jest dominujący (niebieski gracz, czerwony wróg). UI używa `Ui.panel`/`Ui.button`/wstęg i ikon z `Icons`; **nie używaj emoji** (nie wszędzie się renderują).
- **Wydajność:** gra ma płynnie chodzić na tanich telefonach (2–3 GB RAM). W pętli gry i renderowania **zero alokacji** (pule, prealokowane tablice, cache stringów), żadnego I/O ani sieci na wątku gry. Ekrany muszą działać od 720p po rozłożonego Folda (~2184×1968, prawie kwadrat); respektuj `g.insetL/T/R/B` i `ui.dp`.
- Komentarze w kodzie po polsku, zwięzłe. Styl jak w istniejącym kodzie.
- **Bez identyfikatorów modeli AI** w commitach, komentarzach i plikach.

## Budowanie i testy (obowiązkowo przed pushem)
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # jeśli trzeba
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Counter*' --tests '*Endless*' --tests '*AiPersonality*' --console=plain
./gradlew :server:test -PserverOnly=true --console=plain   # gdy zmieniasz :sim lub :server
```
- Testy Robolectric (`ProfileTest`, `BattleScreenTest`, `ScreenshotTest`, `ArtGalleryTest`) pobierają ~200 MB przy pierwszym uruchomieniu. Przy błędach 429 z Maven Central ponawiaj z odstępem albo pobierz artefakt z `https://maven-central.storage-download.googleapis.com/maven2`. Dodaj `--no-configuration-cache`.
- Przy zmianach wyglądu uruchom `--tests '*ScreenshotTest*'` (~2 min). PNG lądują w `app/build/screenshots/`: obejrzyj je (720p i Fold) i dołącz najważniejsze do PR.
- Jeśli dodajesz logikę, **dodaj testy JUnit**.

## Git
- Commituj małymi krokami, wiadomości po polsku (pierwsza linia ≤ 72 znaki).
- Wypchnij **wyłącznie na swoją gałąź** (`git push -u origin <TWOJA_GAŁĄŹ>`). Nie pushuj na `claude/bold-sagan-vwz5gm` ani `main`, nie rób force-push.
- Otwórz Pull Request z Twojej gałęzi **do** `claude/bold-sagan-vwz5gm`, z opisem zmian i zrzutami ekranu, jeśli zmieniasz wygląd.

## Raport końcowy (w opisie PR)
- co zrobiono, lista zmienionych i nowych plików,
- wyniki budowania i testów (wklej końcówkę logu),
- czego nie zrobiono i dlaczego, ryzyka.

---

# Prompt zlecenia
## Cel
Daty włączenia nowej zawartości (Epoka żelaza w wyzwaniu dnia, warianty epok, pogoda) ustawia się jedną stałą dnia wydania, a przed zbudowaniem wersji do sklepu automat sprawdza listę rzeczy, których nie wolno zapomnieć.

## Stan obecny (sprawdzone w kodzie)
- `sim/.../Modes.kt` (obiekt `Daily`): `ERA_EXPANSION_DAY = 21153L` (1.12.2027, data zastępcza), `VARIANTS_DAY = ERA_EXPANSION_DAY`, `WEATHER_DAY = ERA_EXPANSION_DAY`. Wyzwania dnia sprzed tych dat mają starą konfigurację (`legacyConfig`), żeby historyczne powtórki się zgadzały.
- Gra nie była jeszcze publicznie wydana, więc nie ma prawdziwych historycznych wyzwań do zachowania.
- `app/src/main/res/values/games_ids.xml` ma `REPLACE_ME` (Play Games), `BuildConfig.CW_SERVER_URL`, `CW_HMAC_KEY`, `CW_PRIVACY_URL` z `gradle.properties`/zmiennych (patrz `docs/RELEASE.md`, `docs/CI.md`).

## Zakres
1. Jedna stała `Daily.RELEASE_DAY` (numer dnia UTC) zastępuje trzy daty; komentarz: ustawić na dzień publikacji przed pierwszym wydaniem, potem **nigdy nie zmieniać**. Wyzwania od `RELEASE_DAY` mają pełną zawartość. Zachowaj `legacyConfig` dla dni wcześniejszych (testy deterministyczności Daily muszą przejść bez zmian dla dni historycznych).
2. Zadanie Gradle `releaseChecklist` uruchamiane przed `bundleRelease`/`assembleRelease` (nie przed debug), które przerywa build z czytelnym komunikatem po polsku, gdy: `RELEASE_DAY` jest w przeszłości o > 30 dni od dziś lub w przyszłości o > 60 dni; `games_ids.xml` zawiera `REPLACE_ME` przy włączonym Play Games; URL serwera/polityki nie jest https; brak klucza podpisu; `versionCode` nie wzrósł względem pliku `release/last_version_code.txt`. Każdy warunek z flagą pominięcia (`-PskipCheck=…`) dla świadomej decyzji.
3. `docs/RELEASE.md`: lista kontrolna krok po kroku dla właściciela (Play Console, podpis, testy wewnętrzne, data wydania, zrzuty, polityka, ranking).
4. Testy: `DailyTest` dla dni przed i po `RELEASE_DAY`; test logiki sprawdzeń checklisty (wydzielonej do zwykłej klasy Kotlin w `buildSrc` lub skryptu testowalnego).

## Kryteria akceptacji
- `assembleDebug` bez zmian; `assembleRelease` bez konfiguracji kończy się komunikatem checklisty.
- `Daily*`, `ReplayTest`, `BattleSimTest`, `:server:test` przechodzą.

## Czego nie robić
Nie ustawiaj samodzielnie konkretnej daty wydania (zostaw obecną wartość 21153 z komentarzem). Nie zmieniaj innych parametrów wyzwań.
