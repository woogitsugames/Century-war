# Zlecenie 36: Oblężenie tygodnia z progami nagród

**Gałąź robocza:** `gpt/36-weekly-siege` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
     - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `Achievements`, `Missions`, `MissionTexts`, `OnlineLeaderboards`, `PlayGamesLeaderboards`,
     - `notifications/`: przypomnienie o wyzwaniu dnia,
     - `screens/`: menu, kampania, tryby (`ModeScreen`), badania, rankingi, ustawienia, kodeks, misje, osiągnięcia, bitwa (`BattleScreen`, HUD), `TutorialOverlay`, `Chrome`, `MenuBg`,
     - `MainActivity.kt`.
   - Testy symulacji i aplikacji są w `app/src/test/java/com/centurywar/...` (także dla kodu z `:sim`), testy serwera w `server/src/test/kotlin/...`.

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek** (wyjątek: `play-services-games-v2`). Dodaj zależność tylko wtedy, gdy zlecenie wprost na to pozwala.
- Wolno edytować wszystkie pliki potrzebne do zlecenia. Zmiany w plikach współdzielonych (`Battle.kt`, `Ai.kt`, `BattleScreen.kt`, `L.kt`, `Profile.kt`, `MainActivity.kt`) rób możliwie małe i addytywne, bo inne zlecenia pracują równolegle.
- **Teksty UI:** dodawaj do `engine/L.kt` w formacie `"klucz" to t("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")`, czyli **wszystkie 10 języków** (kolejność `L.CODES`). Etykiety przycisków krótkie. Test `LTest` sprawdza komplet języków, zgodność placeholderów (`%d`, `%s`) i brak zdublowanych kluczy, więc musi przechodzić.
- **Symulacja musi zostać deterministyczna:** w `sim/` nie używaj `System.currentTimeMillis()`/`nanoTime()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng` (`Rng`, osobne strumienie AI). Testy `deterministicForSameSeed` i `ReplayTest` muszą przechodzić.
- **Komendy gracza** idą wyłącznie przez `BattleController` (inaczej nie trafią do powtórki i serwer odrzuci wynik).
- **Zapis bitwy:** jeśli dodajesz pole stanu do `Battle`/`Side`/`Fighter`/`Projectile`/`Ai`/`BattleConfig`/`BattleController`, dopisz je do `BattleSnapshot.kt` (zapis i odczyt), **podbij `BattleSnapshot.VERSION`** i dodaj przypadek do `BattleSnapshotTest`, który sprawdza, że wznowiona bitwa daje identyczny wynik. Zmiana formatu powtórki = podbij `Replay.VERSION`.
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
Co tydzień nowe **Oblężenie tygodnia**: te same zasady i seed dla wszystkich graczy, nielimitowane podejścia (liczy się najlepsze), progi nagród (brąz/srebro/złoto) i własna lokalna tablica. Daje cel na cały tydzień obok wyzwania dnia.

## Stan obecny (sprawdzone w kodzie)
- Wyzwanie dnia: `sim/.../Modes.kt` `Daily.dayNumber()`, `Daily.config(day, bonuses)` (seed = dzień, modyfikatory z `Modifier.values()`, zamrożone losowanie przez `ERA_EXPANSION_DAY`, `normalizedBonuses()` – bez badań). `ModeScreen` (tryb `DAILY`): opis, epoki, siła AI, modyfikatory, informacja o podejściu rankingowym (`Profile.lastRankedDaily`), najlepszy wynik `Profile.dailyBoard(day)`.
- Tydzień w misjach: `meta/Missions.kt` `week = Math.floorDiv(day + 3L, 7L)` (tydzień od poniedziałku UTC).
- Endless: `BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = pr.bonuses())`, karty co 5 fal (`Battle.applyEndlessCard`), bossowie, wynik `Battle.score()`.
- Rozliczenie: `BattleScreen.onFinished()` gałąź `Mode.ENDLESS` (nagroda `wave × 3 + kills / 10`, `bestWave`, `BOARD_ENDLESS`, `g.online.submit`); `Achievements.onBattleEnd` aktualizuje `bestWave` dla `Mode.ENDLESS` (~l. 141); misja `ENDLESS_BATTLES` liczy `cfg.mode == Mode.ENDLESS`.
- `RankingsScreen`: 4 zakładki (`endless`, `daily`, `campaign`, `skirmish`).
- Serwer (`ReplayVerifier`) zna tablice `endless`, `daily`, `campaign_N`, `skirmish`.

## Zakres
1. **Model** (`:sim`, `Modes.kt`): `object Weekly { fun weekNumber(day): Long; fun config(week): BattleConfig }`. Rekomendacja: tryb `Mode.ENDLESS` z nowym polem `BattleConfig.weekly: Long = -1` (zamiast nowego `Mode`, żeby nie ruszać wszystkich `when(mode)`); uzasadnij wybór w PR. `config(week)` z `Rng(week * stała + stała)`:
   - epoka startowa 0..5 i maksymalna (+2..+3),
   - 2 modyfikatory z **jawnej, zamrożonej listy** (nie `Modifier.values()`, żeby przyszłe modyfikatory nie zmieniały minionych tygodni),
   - karta startowa z zamrożonej listy `EndlessCard` stosowana w `Battle.init` (przez `applyEndlessCard`, bez nowych losowań w symulacji),
   - pogoda, jeśli w kodzie jest `Weather` (zlecenie 33),
   - bonusy znormalizowane jak `Daily.normalizedBonuses()` (uczciwie, bez badań).
2. **Zapis:** `weekly` w `BattleSnapshot.writeConfig/readConfig`, podbij `BattleSnapshot.VERSION`, przypadek w `BattleSnapshotTest`. `Replay` bez zmian formatu.
3. **Profil:** maska odebranych progów dla bieżącego tygodnia (`weeklyWeek`, `weeklyClaimed`), kompatybilne wstecz; najlepsze wyniki w `boards` pod `weekly_<week>` (przycinaj stare tygodnie: zostaw 8 ostatnich).
4. **Rozliczenie** w `onFinished()`: osobna gałąź dla `cfg.weekly >= 0`: wpis do `weekly_<week>`, **nie** do `BOARD_ENDLESS` i nie podnosi `bestWave`; progi (np. 10/20/30 fal; dobierz po teście AI-gracza tak, by złoto było trudne) dają jednorazowo medale; zwykła nagroda za podejście mniejsza niż w Endless (np. ×0,5), żeby nie farmić. Misja `ENDLESS_BATTLES` liczy też tydzień (zdecyduj i opisz). Podejście rozpoczęte w poprzednim tygodniu rozlicza się do swojego tygodnia (z `cfg.weekly`).
5. **UI:** w `ModeScreen` dla wyzwań dwie zakładki „Dzień” | „Tydzień” (bez nowego przycisku w menu głównym); karta tygodnia: zasady (epoki, modyfikatory, karta startowa, pogoda), czas do końca tygodnia (odświeżany raz na sekundę, cache stringa), progi z ikonami medali (zdobyte/niezdobyte), najlepszy wynik. W `RankingsScreen` zakładka „Tydzień” z tablicą bieżącego tygodnia i przełącznikiem na poprzedni.
6. **Serwer (opcjonalnie, jeśli scalone 08/23):** tablica `weekly_<week>` w `ReplayVerifier` przez `Weekly.config(week)`; walidacja, że tydzień jest bieżący lub poprzedni. Jeśli nie robisz, opisz punkt rozszerzenia w PR.
7. **Teksty** (10 języków): nazwy, opisy, progi, „Koniec za %s”.

## Kryteria akceptacji
- `WeeklyTest`: ten sam tydzień = identyczna konfiguracja; tydzień zmienia się w poniedziałek 00:00 UTC (zgodnie z `Missions`); zamrożone listy (test z zapisanymi oczekiwanymi wartościami dla 3 tygodni); karta startowa zastosowana; bonusy bez badań; determinizm i `Replay.verify` dla podejścia tygodniowego.
- Rozliczenie: brak wpisu do `BOARD_ENDLESS`/`bestWave`, progi odbierane raz, przycinanie starych tablic (`ProfileTest`).
- `BattleSnapshotTest`, `ReplayTest`, `EndlessCardsTest`, `LTest` przechodzą; `:server:test` przechodzi.
- `ScreenshotTest`: zakładka tygodnia (progi częściowo zdobyte), wyniki z progiem, ranking tygodnia na phone720, phone20x9, foldOpen, foldPortrait.
- Zero alokacji w `render` (licznik czasu z cache).

## Pliki
`sim/.../Modes.kt`, `Battle.kt` (karta startowa), `BattleSnapshot.kt`; `app/.../screens/ModeScreen.kt`, `screens/BattleScreen.kt` (`onFinished`), `screens/RankingsScreen.kt`, `meta/Profile.kt`, `meta/Achievements.kt`, `meta/Missions.kt`, `engine/L.kt`; opcjonalnie `server/.../ReplayVerifier.kt`; testy `app/src/test/java/com/centurywar/game/WeeklyTest.kt` (nowy), `BattleSnapshotTest.kt`, `meta/ProfileTest.kt`, `screens/ScreenshotTest.kt`.
