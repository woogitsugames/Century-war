# Zlecenie 34: Oblężenie bez końca: nowi bossowie i tematy fal

**Gałąź robocza:** `gpt/34-endless-bosses` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Oblężenie bez końca ma więcej różnorodności w długich podejściach: pięć typów bossów z czytelnymi zdolnościami i kontrą, dwa nowe tematy fal oraz wyraźnie widoczny boss na polu bitwy. Wynik i weryfikacja powtórek pozostają deterministyczne.

## Stan obecny (sprawdzone w kodzie)
- `sim/.../Battle.kt`: `spawnPendingBoss()` (~l. 1091) co 10 fal tworzy elitę bieżącej epoki wroga przez `spawnFighter(e, def, extraHpMul = 3 + fala/10 × 0,45)`, `bossKind = 1` (odrzut, co 6 s w `updateUnit`) albo `2` (aura +25% tempa ataku w promieniu 380, `bossAuraX`), na zmianę wg `(bossWave / 10) % 2`; obrażenia ×(1,4 + 0,1 × fala/10). Pokonanie: `bossesDefeated++`, `bossRewardsPending++` (dodatkowa karta) w `damageUnit`.
- `Fighter.bossKind`, `bossCd`; `BattleSnapshot` zapisuje je i **waliduje `bossKind in 0..2`** (`readUnit`, ~l. 256).
- Tematy fal: `EndlessWaveTheme` (`MIXED`, `RANGED_SWARM`, `ARMORED_COLUMN`, `FAST_RUSH`, `ELITE_GUARD`, `ARTILLERY`) w `EndlessCards.kt`; `nextTheme(rng)` losuje z `values()`, `Battle.waveRole(roll)` zmienia tylko rozkład ról; budżet fali w `director()`.
- HUD (`BattleScreen` ~l. 779): pasek bossa z napisem `boss_knockback`/`boss_aura` (wybór `if`), zapowiedź `next_wave` + `nextWaveKey`.
- **Renderer nie odróżnia bossa** (`BattleRenderer` nie czyta `isBoss`/`bossKind`): boss wygląda jak zwykła elita.
- Testy: `EndlessCardsTest.bossAppearsEveryTenWavesAndGivesExtraCardOnDefeat` i pokrewne.
- Wynik Endless: `Battle.score()` = fale × 250 + zabici × 12 + czas/2 + bossowie × 1500.

## Zakres
1. **Bossowie** (stałe `BOSS_*` zamiast liczb magicznych): zostają 1 (odrzut) i 2 (aura), dochodzą (propozycja, możesz uzasadnić inne):
   - 3 **Tarczownik**: dopóki HP > 50%, obrażenia od pocisków (jednostek dystansowych i wież) ×0,5; kontra: wręcz i ostrzał,
   - 4 **Werbownik**: co 10 s przyzywa 2 jednostki wręcz bieżącej epoki wroga obok siebie (w limicie `cap` i puli `MAX_UNITS`); kontra: szybkie zabicie, ostrzał,
   - 5 **Szał**: poniżej 50% HP obrażenia ×1,5 i tempo ataku ×1,3 (jednorazowy wybuch efektu `FxBuffer.EVOLVE`); kontra: wieże i dystans.
   Kolejność bossów deterministyczna z fali: pierwsze dwa jak dziś (fala 10 = odrzut, 20 = aura), dalej rotacja przez 5 typów; od fali 60 boss co 10 fal dostaje drugą zdolność (para typów), żeby późne podejścia były trudniejsze. Wszystko bez nowych źródeł losowości (tylko `Battle.rng`, jeśli w ogóle potrzebne) i bez alokacji.
2. **Nowe tematy fal** (dopisane **na końcu** `EndlessWaveTheme`): np. `SIEGE_TRAIN` (ciężkie + elita dystansowa, wolniejsza, większy budżet ×1,15) i `SKIRMISHERS` (dużo tanich jednostek: budżet ×1,3, HP fali ×0,8). Jeśli temat zmienia budżet/HP, rób to w `director()` przez istniejące mnożniki. Zapowiedź w HUD jak dziś.
3. **Zapis:** rozszerz walidację `bossKind` w `BattleSnapshot`, dopisz nowe pola stanu (np. licznik przywołań, flaga „szał aktywny”, druga zdolność) do zapisu i odczytu, podbij `BattleSnapshot.VERSION`, przypadek w `BattleSnapshotTest` (wznowienie w trakcie walki z każdym bossem). Format `Replay` bez zmian; zmienia się przebieg Endless, więc jeśli scalone 23, podbij `CW_GAME_VERSION`.
4. **Wygląd bossa** (`BattleRenderer`, tylko nakładka, bez zmiany animacji ciała ze zleceń 25 i 27): skala ×1,25, aura/pierścień pod stopami w kolorze typu, symbol typu nad głową (ikona z `Icons` rysowana bez alokacji), widoczny zasięg aury dla typu 2, efekt przywołania dla typu 4.
5. **HUD:** nazwa i krótki opis zdolności bossa (tablica kluczy po `bossKind`, bez `if/else` na każdy typ i bez formatowania w klatce), zapowiedź typu bossa falę wcześniej („Następna fala: boss — Tarczownik”).
6. **Kodeks:** sekcja bossów i tematów fal Endless (opis, kontra).
7. **Balans:** AI-gracz w Endless (test jak `EndlessCardsTest`/`BattleSimTest`) na ≥ 10 seedach: średnia osiągnięta fala zmienia się o ≤ 15% względem dziś (raport w PR). HANDOFF: AI-gracz ginie ok. 13. fali.
8. **Teksty** (10 języków): nazwy bossów, opisy, tematy fal.

## Kryteria akceptacji
- `EndlessBossTest`: kolejność typów wg fali (10 = odrzut, 20 = aura, dalej rotacja), każda zdolność działa (tarcza tnie obrażenia pocisków tylko > 50% HP, przywołanie respektuje limity, szał włącza się raz), podwójna zdolność od fali 60, pokonanie daje kartę jak dziś.
- Istniejące `EndlessCardsTest`, `ReplayTest`, `BattleSnapshotTest`, `deterministicForSameSeed` przechodzą (dopuszczalna aktualizacja oczekiwań z uzasadnieniem w PR).
- `ScreenshotTest`: każdy typ bossa na polu i w HUD, nowa zapowiedź fali, Kodeks na phone720, phone20x9, foldOpen, foldPortrait.
- Zero alokacji w `step()` i w `render`; `LTest` przechodzi; `:server:test` przechodzi.

## Pliki
`sim/.../Battle.kt`, `EndlessCards.kt`, `BattleSnapshot.kt`; `app/.../game/BattleRenderer.kt`, `screens/BattleScreen.kt` (HUD bossa), `screens/CodexScreen.kt`, `engine/L.kt`; testy `app/src/test/java/com/centurywar/game/EndlessBossTest.kt` (nowy), `EndlessCardsTest.kt`, `BattleSnapshotTest.kt`, `screens/ScreenshotTest.kt`.
