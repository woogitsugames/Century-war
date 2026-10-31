# Zlecenie 35: Potyczka konfigurowalna

**Gałąź robocza:** `gpt/35-custom-skirmish` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Potyczka staje się trybem „zbuduj własną bitwę”: gracz wybiera zakres epok, osobowość przeciwnika, modyfikatory, pogodę (jeśli scalone 33) i siłę AI. Szybkie ustawienia zapamiętane, a ranking i osiągnięcia pozostają uczciwe.

## Stan obecny (sprawdzone w kodzie)
- `screens/ModeScreen.kt`: potyczka ma tylko 4 przyciski trudności (`diff`, `SKIRMISH_POWER = 0,8 / 1,05 / 1,3 / 1,6`, `Profile.skirmishDiff`) i start `BattleConfig(Mode.SKIRMISH, seed, aiPower(), playerBonuses = pr.bonuses())` – zawsze wszystkie epoki (`0..ERA_COUNT-1`), bez modyfikatorów, osobowość losowana z seeda (`Ai.personalityForSeed`).
- `sim/.../Modes.kt`: `BattleConfig` już ma `startEra`, `maxEra`, `modifiers`, `aiPersonality`; `AiPersonality` (`ASSAULT`, `ENGINEER`, `ECONOMIST`, `SIEGER`, `BALANCED`); `Modifier` (10, klucze `m_*`).
- Nagroda i ranking: `BattleScreen.onFinished()` – potyczka: `15 + skirmishDiff × 14` za zwycięstwo, wpis do `Profile.BOARD_SKIRMISH` i `g.online.submit`. Osiągnięcie `insane_win` (`Achievements.kt` ~l. 147: `cfg.aiPower >= 1.6f`), misja `HARD_WINS` (`Missions.kt` ~l. 254: `aiPower >= 1.2f`).
- `docs/BALANCE.md`: **mgła wojny osłabia AI** (nie widzi armii gracza), więc mgła + najwyższa trudność dawałaby tanie osiągnięcia.
- Restart z wyników: `BattleScreen.copyWithSeed()` kopiuje konfigurację (zachowa ustawienia).
- Zlecenie 23 (klient rankingu) wysyła dla potyczki tylko `difficulty`; serwer buduje `BattleConfig(Mode.SKIRMISH, seed, …)` bez epok i modyfikatorów.

## Zakres
1. **Ekran ustawień potyczki** (`screens/SkirmishSetupScreen.kt` albo rozbudowa `ModeScreen` z przewijaniem): 
   - zakres epok: epoka startowa i maksymalna (`startEra ≤ maxEra`), nazwy z `Defs.eraKeys`,
   - przeciwnik: „Losowy” albo jedna z 5 osobowości (nazwa + jedno zdanie opisu, ikona),
   - siła AI: 4 poziomy jak dziś + suwak 60–200% w krokach co 5% (jawny mnożnik, pokazany liczbą),
   - modyfikatory: do 3 z listy `Modifier` (przełączniki z opisem), 
   - pogoda (tylko gdy w kodzie jest `Weather` ze zlecenia 33; inaczej pomiń sekcję),
   - przyciski „Domyślne” i „Losuj” (losowanie UI, nie w symulacji) oraz podsumowanie przed startem.
   Działa na 720p (przewijana lista sekcji) i na Foldzie (dwie kolumny).
2. **Profil:** zapamiętane ustawienia (`skirmishSetup`: epoki, osobowość, moc ×100, maska modyfikatorów, pogoda), kompatybilne wstecz i przycinane (epoki w zakresie, maks. 3 modyfikatory, moc 60–200). Istniejące `skirmishDiff` zostaje.
3. **Uczciwość:**
   - ranking `BOARD_SKIRMISH` (lokalnie i online) tylko dla ustawień domyślnych (wszystkie epoki, brak modyfikatorów, osobowość losowa, jeden z 4 poziomów); dla własnych ustawień wynik tylko w historii, na ekranie wyników etykieta „Bitwa własna – poza rankingiem”,
   - nagroda medalowa skalowana mocą AI i liczbą epok (krótsza bitwa = mniejsza nagroda, wzór w PR), bez sposobu na farmienie (np. 1 epoka + najsłabsze AI),
   - `insane_win` i `HARD_WINS` nie liczą bitew z `FOG_OF_WAR`; moc liczona z rzeczywistego `cfg.aiPower`.
4. **Start i restart:** `BattleConfig(Mode.SKIRMISH, seed, power, startEra, maxEra, modifiers, bonuses, aiPersonality = …)`; `copyWithSeed()` zachowuje wszystko. Bez zmian w `:sim` (pola już istnieją), chyba że potrzebny helper `SkirmishSetup.toConfig()` – wtedy w `:sim`, z testem.
5. **Podsumowanie w HUD/pauzie:** osobowość przeciwnika i modyfikatory widoczne w menu pauzy (dziś niewidoczne).
6. **Teksty** (10 języków): sekcje, opisy osobowości (krótkie), „Domyślne”, „Losuj”, „poza rankingiem”.

## Kryteria akceptacji
- `SkirmishSetupTest`: zapis/odczyt ustawień, przycinanie, mapowanie na `BattleConfig` (epoki, osobowość, modyfikatory, moc), rozpoznanie „domyślnych” dla rankingu, wzór nagrody (brak farmienia: nagroda za minutę gry nie wyższa niż w domyślnej potyczce), wykluczenie mgły z `insane_win`/`HARD_WINS`.
- `ProfileTest`: stary profil bez `skirmishSetup` = dzisiejsze zachowanie.
- `ScreenshotTest`: ekran ustawień (domyślne i pełne), wyniki „poza rankingiem”, menu pauzy z osobowością na phone720, phone20x9, foldOpen, foldPortrait.
- Determinizm i powtórki bez zmian (`ReplayTest`, `BattleSnapshotTest`); zero alokacji w `render`; `LTest` przechodzi.

## Pliki
`app/.../screens/SkirmishSetupScreen.kt` (nowy) lub `screens/ModeScreen.kt`, `screens/BattleScreen.kt` (`onFinished`, pauza), `meta/Profile.kt`, `meta/Achievements.kt`, `meta/Missions.kt`, `engine/L.kt`, ewentualnie `sim/.../Modes.kt` (helper); testy `app/src/test/java/com/centurywar/meta/SkirmishSetupTest.kt` (nowy), `ProfileTest.kt`, `screens/ScreenshotTest.kt`.
