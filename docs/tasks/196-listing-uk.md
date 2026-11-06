# Zlecenie 196: Natywny przegląd karty sklepu: ukraiński (`listing_uk.md`)

> **Repozytorium GitHub: `woogitsugames/century-war`** (https://github.com/woogitsugames/century-war).
> **Gałąź bazowa: `main`** — z niej utwórz swoją gałąź i wypchnij ją; koordynator scali (bez PR).

**Gałąź robocza:** `gpt/196-listing-uk` (utwórz z `origin/main`)  
**Rozmiar:** S (jedna sesja) · **Grupa:** E · **Zależności:** —  
**Rezerwacja tekstów:** brak — **nie zmieniaj `engine/L.kt`**  
**Raport:** `docs/reports/gpt-196-listing-uk.md`

---

# Prompt ogólny (wspólny dla wszystkich zleceń)

Pracujesz nad grą **Century War**, oryginalną strategią na Androida typu „wojna na jednym pasie przez epoki”. Nazwy, jednostki, grafika i mechaniki są własne: **nie kopiuj niczego z innych gier i nie umieszczaj w grze ani w materiałach (także marketingowych) nawiązań do innych gier, ich nazw ani porównań z nimi**.

**Repozytorium:** `woogitsugames/century-war` (GitHub). **Gałąź bazowa:** `main`.

## Zanim zaczniesz
1. Sklonuj repo i utwórz swoją gałąź z **najnowszej** gałęzi bazowej: `git fetch origin main && git checkout -b <TWOJA_GAŁĄŹ> origin/main`. Nazwa gałęzi jest podana w zleceniu.
2. Przeczytaj `README.md`, `century-war.md` (aktualny stan projektu i decyzje właściciela), górę `docs/COORDINATOR_HANDOVER.md` (najnowsze wpisy) i `docs/GDD.md` (projekt gry). `docs/HANDOFF.md` jest starszy (31.10) — tylko dla kontekstu.
3. Stan wersji (sprawdź w kodzie, mogły się zmienić): `SimVersion.PROTOCOL` = **0.8.12** na `main` (**0.8.13** wkrótce — koordynator scala drugą partię Rozkazów sztabu), `BattleSnapshot.VERSION` = **22**, `Replay.VERSION` = 7 (`MIN_VERSION` = 5), 83 osiągnięcia / **89 ID Play Games**, 9 epok, 36 etapów kampanii, 10 języków.
4. Nowe od paczki 166–171: tryb **Rozkazy sztabu** (`sim/.../game/Orders.kt`, `Mode.ORDERS`, `screens/OrdersScreen.kt`, ranking `leaderboard_orders`, 4 osiągnięcia za Rozkazy), **`engine/FramePacer.kt`** (limit 60 FPS poza bitwą), **pasek „Następny cel”** pod logo w menu (`screens/MenuScreen.kt`, `meta/NextGoal.kt`), dźwięk wg LUFS (d63: `Sound`, `SfxMix`, `SoundSynth`). Raporty: `docs/reports/d59-wydajnosc.md` … `d65-cel-osiagniecia.md`.
5. Struktura kodu (trzy moduły Gradle):
   - **`:sim`** – `sim/src/main/kotlin/com/centurywar/`: symulacja bitwy (czysty Kotlin/JVM, bez `android.*`, **deterministyczna**). W `game/`: `Battle.kt`, `Ai.kt`, `AiPersonality.kt`, `Defs.kt` (balans), `Modes.kt` (kampania, tryby, `BattleConfig`, `Bonuses`, `Daily`, `Weekly`, `Rng`), `Orders.kt`, `EndlessCards.kt`, `Research.kt`, `BattleController.kt` (jedyna droga komend gracza, nagrywa log), `Replay.kt`, `ReplayRecord.kt`, `ReplayPlayer.kt`, `BattleSnapshot.kt` (zapis przerwanej bitwy, format binarny z wersją), `SaveGuard.kt`, `SimVersion.kt`; w `meta/`: `ScoreWire.kt`, `ScoreQueue.kt`. Testy: `sim/src/test/kotlin/...`. Ten sam kod kompiluje serwer.
   - **`:server`** – `server/`: backend rankingu (Ktor + SQLite; `Api.kt`, `Models.kt`, `ReplayVerifier.kt`, `Security.kt`, `NickModeration.kt`, `Retention.kt`, `AdminCli.kt` …), weryfikuje powtórki przez `Replay.verify`; opis API i podpisu HMAC w `server/README.md`. Testy: `server/src/test/kotlin/...`.
   - **`:app`** – `app/src/main/java/com/centurywar/`:
     - `CenturyWarApp.kt`, `MainActivity.kt`,
     - `engine/`: pętla gry (`GameView`, `FramePacer`), `Game`, `Screen`, `Ui`, `Icons`, `L` (**teksty w 10 językach**), `Quality`/`QualityGovernor`, `ThermalWatch`, `Sound`/`SoundSynth`/`SfxMix`, `Music`/`RecordedMusic`/`MusicBreath`, `GameAccessibility`/`SystemAccessibility`, `Haptics`, `StartupTimings`,
     - `game/`: renderer `BattleRenderer.kt`, `BaseFlashCache.kt`, `BitmapCacheBudget.kt` + rysunki w `game/art/`,
     - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `ProfileBackup`, `Achievements`, `Missions`, `NextGoal`, `WelcomeBack`, `LostBattle`, `StageNovelty`, `PlaytestLog`, `TesterSurvey`, `TesterReportReminder`, `OnlineLeaderboards`, `OnlineAchievements`, `PlayGamesLeaderboards`, `ScoreClient` (ranking globalny), `ReplayStore`, `ReplayImport`, `ReplayShareProvider`, `BattleHistory`, `SkirmishSetup`, `WhatsNewCatalog`, `BannerCatalog`, `PendingDeletions`,
     - `crash/`: raporty zawieszeń i Raport dla testera (`CrashStore`, `ExitReasons`, `PlaytestReportText` …),
     - `notifications/`: przypomnienie o wyzwaniu dnia (`DailyReminders`, `DailyReminderLogic`, `DailyReminderTexts` …),
     - `testloop/`: pętla testowa Google Play (`TestLoopActivity`, osobny proces `:testloop`),
     - `screens/`: menu, kampania, tryby, Rozkazy (`OrdersScreen`), badania, misje, osiągnięcia, rankingi, statystyki, powtórki, ustawienia, kodeks, „Co nowego”, bitwa (`BattleScreen` i jego części `BattleTopBar`, `BattleBottomHud`, `BattleResultsPanel`, `BattleUpgradePanel`, `BarrageAiming`, `BattleSfx`, `BattleAlerts`, `MilestoneRibbon`, `EvolveBanner` …), `TutorialOverlay`, `HintOverlay`, `Chrome`, `MenuBg`.
   - Testy aplikacji: `app/src/test/java/com/centurywar/...` (Robolectric). Testy skryptów: `tests/*.py`. Narzędzia: `tools/`. Kategorie testów (fast/screenshots/balance): `gradle/test-categories.properties`, opis w `docs/TEST_SPEED.md`.
   - Dokumenty: `docs/BALANCE.md`, `docs/RELEASE.md`, `docs/OWNER_CHECKLIST.md`, `docs/GLOSSARY.md`, `docs/PERFORMANCE.md`, `docs/store/` (karty sklepu, Data safety, polityka prywatności, plan monetyzacji), `docs/reports/` (raporty dni i audytów), `docs/research/` (R1–R14).

## Środowisko (obowiązkowe przy zmianach w kodzie lub `L.kt`)
Kod, którego nie skompilowałeś i nie przetestowałeś, **nie jest gotowy**. Zlecenia czysto raportowe i dokumentowe nie wymagają budowania, ale każdy wniosek o zachowaniu gry opieraj na przeczytanym kodzie (najlepiej potwierdzonym testem). Jeśli w Twoim środowisku nie ma Android SDK, zainstaluj go:
```bash
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk
curl -sSfL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-36" "build-tools;36.0.0" "platform-tools"
cd <repo> && echo "sdk.dir=$HOME/android-sdk" > local.properties
```
Jeśli Maven Central zwraca 429, przekieruj go na mirror `https://maven-central.storage-download.googleapis.com/maven2/` skryptem init Gradle (`~/.gradle/init.d/`), a obraz Robolectric (`org.robolectric:android-all-instrumented`) pobierz z tego mirrora do `~/.m2/repository`. Gdy `dl.google.com` jest zablokowane, zobacz obejście w `tools/offline-build/`. Zlecenia tylko dla `:sim` i `:server` działają bez Android SDK z `-PserverOnly=true`.
**Jeśli mimo to nie możesz uruchomić Gradle, a zlecenie zmienia kod lub `L.kt`, napisz to na początku raportu wielkimi literami i nie twierdź, że testy przechodzą.**

Pułapki z poprzednich tur:
- Testy w `app/src/test` kompilują się z `android.jar`: nie ma tam `com.sun.*`, `java.lang.management`, `jdk.jfr`.
- `Fighter.alive` to właściwość tylko do odczytu (`active && dying < 0f`).
- Zapis profilu działa w tle: w testach po `save()` wołaj `Profile.awaitHintWrites()` przed odczytem pliku.
- W tekstach Kotlin `%1$s` pisz jako `%1\$s`.
- Raporty balansu (`BalanceReport`, `EndlessBalanceReportTest`) uruchamiaj naprawdę i wklejaj liczby; nie oddawaj kryterium „niezweryfikowane”.
- Nowa klasa testowa trafia do kategorii wg nazwy (`gradle/test-categories.properties`): nazwa z `Balance` → balance, z `ScreenshotTest` → screenshots, reszta → fast. Nie zmieniaj tego pliku.

## Praca równoległa
To zlecenie jest częścią paczki **172–201** (30 zleceń puszczanych równolegle; kolejność i grupy w `docs/tasks/PACZKA-172-201.md`). Jednocześnie koordynator i jego agenci pracują na `main` nad: drugą partią **Rozkazów sztabu** (`Orders*`, `OrdersScreen`, PROTOCOL 0.8.13, part211), **paskiem celu w menu** (`MenuScreen`, `NextGoal`), **osiągnięciami i Play Games** (`Achievements*`, `OnlineAchievements`, `PlayGames*`, `docs/store/play_games_*`, `docs/store/pgs_import/`), **dźwiękiem z d63** (`Sound`, `SoundSynth`, `SfxMix`, `SfxLayers`, `BattleSfx`, `PendingSfx`), a zewnętrzne modele nad researchem **170/171** (nowi wrogowie i osobowości AI, warianty epok). **Tych obszarów nie ruszasz w żadnym zleceniu tej paczki.** Trzymaj się plików wymienionych w swoim zakresie; jeśli poprawka wymaga pliku spoza zakresu — opisz ją w raporcie zamiast ją robić.

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek** (wyjątek: `play-services-games-v2`). Nie dodawaj zależności (także w `tools/`: tylko biblioteka standardowa Pythona 3 i `pytest` do testów).
- **Teksty UI:** tabela w `engine/L.kt` jest podzielona na funkcje `part0()`…`partN()` (limit 64 KB bajtkodu jednej metody JVM). Zajęte są części do **part211** (part210–211 — koordynator). **W tej paczce każde zlecenie, które zmienia `L.kt`, ma własny, zarezerwowany numer części** (podany w zleceniu, od **part212** wzwyż) — używasz go tylko wtedy, gdy musisz dodać nowy klucz: nowa funkcja `private fun partNNN()` plus jedna linia `putAll(partNNN())` na końcu listy wywołań (liczba `putAll(part` = liczba `private fun part`). Zmiana treści istniejących wpisów nie wymaga nowej części. Zlecenia bez rezerwacji **nie zmieniają `L.kt`**. Format `"klucz" to t("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")` — **wszystkie 10 języków** w kolejności `L.CODES`. Test `LTest` sprawdza komplet języków, zgodność placeholderów (`%d`, `%s`, `%1$s`) i brak zdublowanych kluczy.
- **CI (decyzja właściciela — limit minut):** workflowy w `.github/workflows/` uruchamiają się tylko przy tagu `v*`, przy PR do `main` i ręcznie (`workflow_dispatch`). **Nie dodawaj workflowów ani wyzwalaczy przy `push`.** Nowy workflow w tej paczce — wyłącznie `workflow_dispatch`.
- **Niewidomi (decyzja właściciela):** **nic nowego specjalnie dla niewidomych.** Istniejące wsparcie TalkBack zostaje, ale nie proponuj nowych funkcji dla niewidomych i nie obiecuj w sklepie, w dokumentach dla graczy ani w marketingu, że gra jest dla osób niewidomych.
- **Nie zmieniaj** zatwierdzonego układu `compactTop` (górny pasek bitwy) ani sposobu celowania ostrzału (`BarrageAiming`) i nie proponuj ich przeróbek.
- **`Daily.RELEASE_DAY` (2027-12-01) zostaje bez zmian** — decyduje o tym tylko właściciel.
- **Powiadomienia:** prośba o zgodę na powiadomienia i same powiadomienia **nie pojawiają się na ekranie wyników bitwy** (decyzja właściciela) — nie przenoś ich tam i nie proponuj tego.
- **Symulacja musi zostać deterministyczna:** w `sim/` żadnego `System.currentTimeMillis()`/`nanoTime()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng`. **W tej paczce nikt nie zmienia `sim/src/main`** (przebieg symulacji, balans, `SimVersion.PROTOCOL`) — robi to wyłącznie koordynator. Zlecenia balansu są tylko-raport.
- **Oryginalność:** nie kopiuj nazw, haseł, opisów, mechanik ani układów z innych gier; nie wymieniaj innych gier z nazwy (także jako porównania „jak w …”).
- **Grafika i UI:** płaski cartoon z grubym konturem, kolor drużyny dominujący (niebieski gracz, czerwony wróg); **bez emoji**.
- **Zrzuty-wzorce:** nie aktualizuj wzorców zrzutów (`updateScreenshots`) — jeśli Twoja zmiana zmienia obraz, opisz w raporcie, które wzorce się zmienią; zatwierdza je koordynator.
- Dokumenty i komentarze po polsku (chyba że zlecenie wymaga tekstów w innych językach), zwięźle.
- **Bez identyfikatorów modeli AI** w commitach, komentarzach i plikach.

## Budowanie i testy (obowiązkowo, gdy zmieniasz kod lub `L.kt`)
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # jeśli trzeba
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*LTest*' --console=plain --no-configuration-cache
./gradlew fastTest --console=plain --no-configuration-cache          # szybkie testy app/sim/server
```
- Testy Robolectric pobierają ~200 MB przy pierwszym uruchomieniu; przy błędach 429 ponawiaj z odstępem albo użyj mirrora.
- Testy skryptów: `python3 -m pytest tests/` (gdy zmieniasz `tools/` lub dokumenty sprawdzane przez `tests/`).
- Karty sklepu sprawdza `StoreGraphicsTest.listingLimits` (tytuł ≤ 30, krótki opis ≤ 80, pełny opis ≤ 4000 znaków) oraz `tools/check_store_listings.py`.
- Konkretne komendy dla Twojego zlecenia są w sekcji „Testy” poniżej — wklej ich wynik do raportu.

## Git
- Commituj małymi krokami, wiadomości po polsku (pierwsza linia ≤ 72 znaki).
- Wypchnij **wyłącznie na swoją gałąź** (`git push -u origin <TWOJA_GAŁĄŹ>`). Nie pushuj na `main` ani na gałęzie `claude/*`/`integ/*`, nie rób force-push.
- **Nie otwieraj Pull Requesta** (PR do `main` uruchamia płatne CI). Koordynator sam przejrzy Twoją gałąź i ją scali.

## Raport końcowy (plik z raportem + ostatnia wiadomość sesji)
- Plik raportu: ścieżka podana w zleceniu (`docs/reports/gpt-NNN-*.md`), w ostatnim commicie.
- W raporcie i w ostatniej wiadomości: nazwa wypchniętej gałęzi i hash ostatniego commita; co zrobiono; lista zmienionych i nowych plików; wyniki budowania i testów (wklej końcówkę logu), jeśli zmieniano kod, `L.kt` lub `tools/`; czego nie zrobiono i dlaczego, ryzyka.

---

# Prompt zlecenia

## Cel
Przejrzyj `docs/store/listing_uk.md` okiem rodzimego użytkownika języka ukraińskiego: naturalność, poprawność, spójność nazw z grą (`L.kt`, kolumna `uk`), limity Play (30/80/4000), brak obietnic dla niewidomych i porównań z innymi grami. Popraw kartę.

## Pliki (zakres)
- `docs/store/listing_uk.md`
- `docs/reports/gpt-196-listing-uk.md` (raport)

## Czego NIE ruszać
- inne karty, `docs/store/marketing/`, `L.kt` (niespójności z grą — lista w raporcie)
- Wspólne dla paczki: obszary koordynatora i 170/171 z „Pracy równoległej” (`Orders*`, `OrdersScreen`, `MenuScreen`, `NextGoal`, `Achievements*`, `OnlineAchievements`, `PlayGames*`, `Sound`/`SoundSynth`/`SfxMix`/`SfxLayers`/`BattleSfx`), cały `sim/src/main` i `SimVersion.PROTOCOL`, `compactTop`, `BarrageAiming`, `Daily.RELEASE_DAY`, wzorce zrzutów, `gradle/test-categories.properties`; bez PR i bez force-push.

## Gotowe, gdy…
- poprawiona karta; tabela zmian w raporcie (przed → po → dlaczego);
- `tools/check_store_listings.py` i `StoreGraphicsTest.listingLimits` zielone.
- raport `docs/reports/gpt-196-listing-uk.md` jest w ostatnim commicie gałęzi `gpt/196-listing-uk` i zawiera sekcje z „Raportu końcowego”.

## Testy
```bash
python3 tools/check_store_listings.py
./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest.listingLimits*' --console=plain --no-configuration-cache   # jeśli masz SDK
```
Wklej końcówkę wyniku każdej komendy do raportu. Jeśli zmieniasz kod lub `L.kt`, dodatkowo `./gradlew :app:assembleDebug` i `./gradlew fastTest` (prompt ogólny).

## Raport
`docs/reports/gpt-196-listing-uk.md`: data, hash commita `main`, od którego zaczynasz; czy uruchamiano Gradle (jeśli nie — WIELKIMI LITERAMI); co zrobiono, lista plików, wyniki testów, czego nie zrobiono, ryzyka, propozycje dalszych zleceń (krótkie tytuły i pliki).

## Czego nie robić
- Nie wychodź poza pliki z zakresu; nie otwieraj PR; nie pushuj poza swoją gałąź.
- Nie proponuj zmian `compactTop`, celowania ostrzału, `Daily.RELEASE_DAY`, nowych funkcji dla niewidomych ani przenoszenia powiadomień na ekran wyników.
