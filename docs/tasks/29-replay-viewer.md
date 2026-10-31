# Zlecenie 29: Odtwarzacz powtórek bitew

**Gałąź robocza:** `gpt/29-replay-viewer` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Gracz może obejrzeć zapisaną bitwę (ostatnie bitwy, rekordy z rankingów) z pauzą, przewijaniem i prędkością ×1/×3/×8. Powtórka powstaje z logu komend (`Replay.Log`), więc zajmuje kilka KB i jest bit w bit zgodna z oryginałem.

## Stan obecny (sprawdzone w kodzie)
- `sim/.../Replay.kt`: `Replay.Log(steps, commands)`, `encode/decode` (Base64url, `VERSION = 3`), `verify(cfg, log)` odtwarza bez renderera: `Battle(cfg)`, `playerAi = null`, `BattleController(battle, record = false)`, komendy kroku N wykonywane **przed** `step()`, po każdym kroku `battle.fx.count = 0`.
- `BattleController.log()` tworzy kopię logu na koniec bitwy. Aplikacja **nigdzie nie zapisuje logu** (w `app/src/main` brak użycia `Replay`); jedyny zapis to zrzut przerwanej bitwy (`BattleScreen.snapshotBytes()` → `SnapshotStore` w `BattleSnapshot.kt`, plik `filesDir/battle.snap`, zapis w tle przez jednowątkowy executor).
- Do odtworzenia potrzebny jest pełny `BattleConfig` (seed, epoki, modyfikatory, `playerBonuses`, `aiPersonality`, `tutorial`). Kodek konfiguracji istnieje jako prywatne `BattleSnapshot.writeConfig/readConfig`. `BattleSnapshot.defsFingerprint` zmienia się po każdej zmianie balansu: stara powtórka po aktualizacji nie da tego samego przebiegu.
- `BattleSnapshot.encode` wymaga kontrolera z `record = true` (`writeSnapshot` ma `check(record)`).
- Koniec bitwy: `BattleScreen.onFinished()` (~l. 546), panel wyników `drawResults()` z `continueBtn` i `restartEnd`. Rankingi lokalne: `Profile.submit/top`, `ScoreEntry(name, score, day)` (JSON `n/s/d`), ekran `screens/RankingsScreen.kt`.
- Renderer: `BattleRenderer(g, battle)` z `camX`, `k`, `follow(dt)`, `clampCam()`, `lerp`; `render()` zużywa i zeruje `battle.fx`. Dźwięki z `fx` gra prywatne `BattleScreen.playSounds()`.

## Zakres
1. **Plik powtórki** (`:sim`, nowy `ReplayRecord.kt`): `encode(cfg, log, meta): ByteArray` z nagłówkiem (magic, wersja formatu, `Replay.VERSION`, `defsFingerprint`, CRC32), konfiguracją (udostępnij istniejący kodek z `BattleSnapshot` jako `internal`/publiczny, **bez zmiany formatu zrzutu**), logiem (`Replay.encode`) i metadanymi (tryb, etap, zwycięzca, wynik, czas bitwy, fala, data w ms podawana przez aplikację, nick). `decode` odrzuca złe wersje, CRC, nadmiarowe dane i przekroczone limity (jak `Replay.decode`). Pole `playable` = zgodny fingerprint i `Replay.VERSION`.
2. **Odtwarzacz bez Androida** (`:sim`, nowy `ReplayPlayer.kt`): trzyma `Battle`, kontroler (`record = true`, bo potrzebny do keyframe'ów), indeks komendy; `advance(steps)`, `seek(step)`, keyframe co 30 s czasu bitwy (`BattleSnapshot.encode`) budowany przy pierwszym przejściu. Komendy wykonuje dokładnie jak `Replay.verify`; komenda odrzucona albo wyjątek = stan `broken` (bez crasha). Skok wstecz: najbliższy keyframe + dosymulowanie; skok w przód poza znane keyframe'y: porcjami (API `seekStep(budgetNanos)`), żeby ekran mógł pokazywać postęp.
3. **Magazyn** (`app/.../meta/ReplayStore.kt`): katalog `filesDir/replays/`, pliki `<id>.cwr`, zapis atomowy w tle (wzoruj się na `SnapshotStore`), lista nagłówków czytana w tle. Retencja: 20 ostatnich + do 20 przypiętych (rekordy, ręcznie przypięte). Żadnego I/O na wątku gry.
4. **Zapis po bitwie:** w `onFinished()` jedno `ReplayRecord.encode(cfg, commands.log(), …)` i `saveAsync`. Bitwa wznowiona ze zrzutu ma ciągły log (kontroler jest w zrzucie) – test. Gdy wynik trafia do lokalnej tablicy, przypnij powtórkę i zapisz jej id w `ScoreEntry` (nowe opcjonalne pole JSON `r`; brak pola = brak powtórki).
5. **`screens/ReplayScreen.kt`:** renderer bitwy + HUD tylko do odczytu (epoka i złoto obu stron, HP baz, fala w Endless, doktryny/karty, czas), pauza, ×1/×3/×8, oś czasu z przeciąganiem i znacznikami (awanse epok, doktryny/karty, ostrzały, bossowie), −10 s/+10 s, kamera: śledzenie jak w bitwie albo ręczne przeciąganie, minimapa. Przy szybkim przewijaniu wyciszony dźwięk i `battle.fx.count = 0` po każdym kroku (bufor ma 256 miejsc). Komunikat zamiast obrazu dla `broken` i dla powtórek z innej wersji gry.
6. **Wejścia:** przycisk „Powtórka” w panelu wyników, ikona przy wpisie z powtórką w `RankingsScreen`, lista ostatnich powtórek (zakładka w `RankingsScreen` albo `ReplayListScreen`) z przypinaniem i usuwaniem. Wstecz wraca do ekranu źródłowego.
7. **Dźwięk:** wyciągnij mapowanie fx → dźwięk z `BattleScreen.playSounds()` do wspólnej klasy (np. `screens/BattleSfx.kt`) bez zmiany zachowania; używa jej bitwa i powtórka (ułatwia scalenie ze zleceniami 26 i 38).
8. **Teksty** (10 języków): m.in. `replay`, `replay_list`, `replay_pin`, `replay_delete`, `replay_broken`, `replay_old_version`, `replay_loading`.

## Kryteria akceptacji
- Testy JVM: `ReplayRecordTest` (round-trip, odrzucanie uszkodzonych i obcych danych), `ReplayPlayerTest`: odtworzenie daje ten sam `Replay.Result` co oryginał (kampania z samouczkiem, Endless z kartami, wyzwanie dnia, bitwa wznowiona ze zrzutu); `seek` wstecz i w przód daje w kroku N stan identyczny z liniowym odtwarzaniem (porównanie bajtów `BattleSnapshot.encode`).
- Przewinięcie na dowolny moment 15-minutowej bitwy < 2 s na JVM; na urządzeniu praca porcjami, bez zamrożenia UI.
- Zero alokacji na klatkę podczas odtwarzania (wyjątek: keyframe co 30 s przy pierwszym przejściu); brak I/O na wątku gry.
- `ProfileTest`: stary JSON bez `r` się wczytuje, nowe pole przetrwa zapis.
- `ScreenshotTest`: ekran powtórki i lista powtórek (phone720, phone20x9, foldOpen, foldPortrait); zrzuty w PR.
- `Replay.VERSION` i `BattleSnapshot.VERSION` bez zmian (kodek konfiguracji tylko udostępniony). `LTest`, `ReplayTest`, `BattleSnapshotTest` przechodzą; `:server:test` przechodzi.

## Pliki
`sim/src/main/kotlin/com/centurywar/game/ReplayRecord.kt` (nowy), `ReplayPlayer.kt` (nowy), `BattleSnapshot.kt` (widoczność kodeka konfiguracji); `app/.../meta/ReplayStore.kt` (nowy), `meta/Profile.kt` (`ScoreEntry.replay`), `screens/ReplayScreen.kt` (nowy), `screens/BattleSfx.kt` (nowy), `screens/BattleScreen.kt`, `screens/RankingsScreen.kt`, `engine/Game.kt` (pole magazynu), `MainActivity.kt`, `engine/L.kt`, `engine/Icons.kt` (jeśli potrzebna ikona); testy w `app/src/test/java/com/centurywar/{game,meta,screens}/`.
