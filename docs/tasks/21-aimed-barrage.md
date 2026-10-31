# Zlecenie 21: Celowanie ostrzału dotykiem dla gracza

**Gałąź robocza:** `gpt/21-aimed-barrage` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Gracz wskazuje miejsce ostrzału dotykiem, tak jak już robi to AI. Logika istnieje, brakuje UI.

## Stan obecny (sprawdzone w kodzie)
- `sim/.../Battle.kt`: `castBarrage(side, targetX)` – 16 pocisków wokół `targetX` z rozrzutem ±120, odległość od `side.baseFront` przycinana do 220..1370 (`fireBarrage`); `castBarrage(side)` = klasyczny losowy rozrzut 220..1370.
- `sim/.../BattleController.kt`: `barrage()` (log `Replay.Kind.BARRAGE`) i `barrage(targetX)` (zaokrągla do całości, `coerceIn(0, Defs.LANE)`, log `Replay.Kind.BARRAGE_AT`). Serwer weryfikuje obie komendy.
- `app/.../screens/BattleScreen.kt`: przycisk `barrageBtn` (`BARRAGE`) woła `commands.barrage()` w `onClick`; kamera: `onDrag`, `onTap` (minimapa, panel wież), `r.camX`, `r.k`, `r.worldVisible`.
- `app/.../game/BattleRenderer.kt`: `camX`, `k`, prywatne `sx(x) = (x - camX) * k + shakeX`.
- Samouczek: `TutorialOverlay.kt` krok `S_BARRAGE` (warunek `p.abilityCd[0] > 0f`, teksty `tut_barrage_t`, `tut_barrage`); Kodeks: `CodexScreen.kt` (`cx_barrage`, `cx_barrage_d`: „pole przed twoją bazą”).

## Zakres
1. **Tryb celowania** w `BattleScreen`:
   - dotknięcie gotowego `barrageBtn` włącza celowanie (przycisk podświetlony, krótka podpowiedź na wstędze „Wskaż cel ostrzału”), ponowne dotknięcie przycisku = **szybki ostrzał** klasyczny (`commands.barrage()`), żeby nie spowalniać doświadczonych graczy; Wstecz/`onBack` lub dotknięcie poza polem walki anuluje,
   - dotknięcie pola walki w trybie celowania: przelicz ekran → świat (`worldX = r.camX + (x - shake) / r.k`; dodaj publiczną funkcję w `BattleRenderer`, np. `worldX(screenX)`), wywołaj `commands.barrage(worldX)`, wyjdź z trybu,
   - przeciąganie kamery w trybie celowania nadal działa (`onDrag` nie może wystrzelić); strzał tylko na `onTap`,
   - tryb celowania wyłącza się sam przy pauzie, końcu bitwy, wyborze doktryny/karty i gdy zdolność przestaje być gotowa.
2. **Podgląd celu** (rysowany w świecie, bez alokacji: prealokowane `Paint`/`RectF`): zakres dozwolony (pas 220..1370 od własnej bazy, półprzezroczysty) i znacznik w miejscu palca/ostatniego celu z szerokością rozrzutu ±120 (po przycięciu do zakresu). Styl jak reszta HUD (gruby kontur, kolor drużyny). Czytelne na 720p i na Foldzie.
3. **Samouczek:** krok `S_BARRAGE` uczy celowania: dziura (`hole`) najpierw na przycisku, potem na grupie wrogów; tekst `tut_barrage` zaktualizuj (10 języków). Warunek zaliczenia bez zmian (`abilityCd[0] > 0f`), więc szybki ostrzał też zalicza krok.
4. **Kodeks:** zaktualizuj `cx_barrage_d` („wskaż miejsce, pociski spadną w promieniu…”), 10 języków.
5. Stan trybu celowania to stan UI: **nie** trafia do `BattleSnapshot`. Po wznowieniu bitwy tryb jest wyłączony.
6. **Testy:**
   - `ReplayTest`: gracz (przez `BattleController`) wykonuje `barrage(x)` dla kilku x (w tym poza zakresem i ułamkowe); `Replay.verify` odtwarza identyczny wynik, a log zawiera `BARRAGE_AT` z zaokrągloną wartością,
   - `BattleScreenTest` (Robolectric): tap przycisku → tap pola → `battle.player.barragesUsed == 1` i ostatnia komenda logu to `BARRAGE_AT` z x zgodnym z przeliczeniem kamery (tolerancja 1); dwa tapy przycisku → `BARRAGE`; Wstecz w trybie celowania nie pauzuje gry, tylko anuluje,
   - `ScreenshotTest`: zrzut trybu celowania (720p i Fold).

## Kryteria akceptacji
- Gracz może celować i nadal ma szybki ostrzał jednym gestem więcej niż dziś (dwa tapy).
- Strzał nie wypala przy przeciąganiu kamery ani przy tapnięciu w przyciski HUD.
- `ReplayTest`, `BattleScreenTest`, `LTest`, `BattleSnapshotTest` przechodzą; zero alokacji w `draw`.
- Zrzuty w PR.

## Pliki
`app/.../screens/BattleScreen.kt`, `app/.../game/BattleRenderer.kt`, `app/.../screens/TutorialOverlay.kt`, `app/.../screens/CodexScreen.kt`, `app/.../engine/L.kt`, testy w `app/src/test/java/com/centurywar/{game/ReplayTest.kt,screens/BattleScreenTest.kt,screens/ScreenshotTest.kt}`. Symulacji (`sim/`) nie trzeba zmieniać.
