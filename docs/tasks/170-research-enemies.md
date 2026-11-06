# Zlecenie 170: Research: nowi wrogowie i osobowości AI (raport, bez zmian w kodzie)

> **Repozytorium GitHub: `woogitsugames/century-war`** (https://github.com/woogitsugames/century-war).
> **Gałąź bazowa: `main`** — z niej utwórz swoją gałąź i wypchnij ją; koordynator scali (bez PR).

**Gałąź robocza:** `gpt/170-research-enemies` (utwórz z `origin/main`)

---

# Prompt ogólny (wspólny dla wszystkich zleceń)

Pracujesz nad grą **Century War**, oryginalną strategią na Androida typu „wojna na jednym pasie przez epoki”. Nazwy, jednostki, grafika i mechaniki są własne: **nie kopiuj niczego z innych gier i nie umieszczaj w grze ani w materiałach (także marketingowych) nawiązań do innych gier, ich nazw ani porównań z nimi**.

**Repozytorium:** `woogitsugames/century-war` (GitHub). **Gałąź bazowa:** `main`.

## Zanim zaczniesz
1. Sklonuj repo i utwórz swoją gałąź z **najnowszej** gałęzi bazowej: `git fetch origin main && git checkout -b <TWOJA_GAŁĄŹ> origin/main`. Nazwa gałęzi jest podana w zleceniu.
2. Przeczytaj `README.md`, `century-war.md` (aktualny stan projektu i decyzje właściciela), górę `docs/COORDINATOR_HANDOVER.md` (najnowsze wpisy) i `docs/GDD.md` (projekt gry). `docs/HANDOFF.md` jest starszy (31.10) — tylko dla kontekstu.
3. Stan wersji (sprawdź w kodzie, mogły się zmienić): `SimVersion.PROTOCOL` = **0.8.11**, `BattleSnapshot.VERSION` = 21, `Replay.VERSION` = 7 (`MIN_VERSION` = 5), 79 osiągnięć / 84 ID Play Games, 9 epok, 36 etapów kampanii, 10 języków.
4. Struktura kodu (trzy moduły Gradle):
   - **`:sim`** – `sim/src/main/kotlin/com/centurywar/`: symulacja bitwy (czysty Kotlin/JVM, bez `android.*`, **deterministyczna**). W `game/`: `Battle.kt`, `Ai.kt`, `AiPersonality.kt`, `Defs.kt` (balans), `Modes.kt` (kampania, tryby, `BattleConfig`, `Bonuses`, `Daily`, `Weekly`, `Rng`), `EndlessCards.kt`, `Research.kt`, `BattleController.kt` (jedyna droga komend gracza, nagrywa log), `Replay.kt`, `ReplayRecord.kt`, `ReplayPlayer.kt`, `BattleSnapshot.kt` (zapis przerwanej bitwy, format binarny z wersją), `SaveGuard.kt`, `SimVersion.kt`; w `meta/`: `ScoreWire.kt`, `ScoreQueue.kt`. Testy: `sim/src/test/kotlin/...`. Ten sam kod kompiluje serwer.
   - **`:server`** – `server/`: backend rankingu (Ktor + SQLite; `Api.kt`, `ReplayVerifier.kt`, `Security.kt`, `NickModeration.kt`, `Retention.kt` …), weryfikuje powtórki przez `Replay.verify`; opis API i podpisu HMAC w `server/README.md`. Testy: `server/src/test/kotlin/...`.
   - **`:app`** – `app/src/main/java/com/centurywar/`:
     - `CenturyWarApp.kt`, `MainActivity.kt`,
     - `engine/`: pętla gry (`GameView`), `Game`, `Screen`, `Ui`, `Icons`, `L` (**teksty w 10 językach**), `Quality`/`QualityGovernor`, `ThermalWatch`, `Sound`/`SoundSynth`/`SfxMix`, `Music`/`RecordedMusic`/`MusicBreath`, `GameAccessibility`/`SystemAccessibility`, `Haptics`, `StartupTimings`,
     - `game/`: renderer `BattleRenderer.kt`, `BaseFlashCache.kt`, `BitmapCacheBudget.kt` + rysunki w `game/art/`,
     - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `ProfileBackup`, `Achievements`, `Missions`, `NextGoal`, `WelcomeBack`, `LostBattle`, `StageNovelty`, `PlaytestLog`, `TesterSurvey`, `TesterReportReminder`, `OnlineLeaderboards`, `OnlineAchievements`, `PlayGamesLeaderboards`, `ScoreClient` (ranking globalny), `ReplayStore`, `ReplayImport`, `ReplayShareProvider`, `BattleHistory`, `SkirmishSetup`, `WhatsNewCatalog`, `BannerCatalog`, `PendingDeletions`,
     - `crash/`: raporty zawieszeń i Raport dla testera (`CrashStore`, `ExitReasons`, `PlaytestReportText` …),
     - `notifications/`: przypomnienie o wyzwaniu dnia,
     - `testloop/`: pętla testowa Google Play (`TestLoopActivity`, osobny proces `:testloop`),
     - `screens/`: menu, kampania, tryby, badania, misje, osiągnięcia, rankingi, statystyki, powtórki, ustawienia, kodeks, „Co nowego”, bitwa (`BattleScreen` i jego części `BattleTopBar`, `BattleBottomHud`, `BattleResultsPanel`, `BattleUpgradePanel`, `BarrageAiming`, `BattleSfx`, `BattleAlerts` …), `TutorialOverlay`, `HintOverlay`, `Chrome`, `MenuBg`.
   - Testy aplikacji: `app/src/test/java/com/centurywar/...` (Robolectric). Testy skryptów: `tests/*.py`. Narzędzia: `tools/`.
   - Dokumenty: `docs/BALANCE.md`, `docs/RELEASE.md`, `docs/OWNER_CHECKLIST.md`, `docs/store/` (karty sklepu, Data safety, polityka prywatności, plan monetyzacji), `docs/reports/` (raporty dni i audytów), `docs/research/` (R1–R13).

## Środowisko (obowiązkowe przy zmianach w kodzie lub `L.kt`)
Kod, którego nie skompilowałeś i nie przetestowałeś, **nie jest gotowy**. Zlecenia czysto raportowe i dokumentowe nie wymagają budowania, ale każdy wniosek o zachowaniu gry opieraj na przeczytanym kodzie (najlepiej potwierdzonym testem). Jeśli w Twoim środowisku nie ma Android SDK, zainstaluj go:
```bash
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk
curl -sSfL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-36" "build-tools;36.0.0" "platform-tools"
cd <repo> && echo "sdk.dir=$HOME/android-sdk" > local.properties
```
Jeśli Maven Central zwraca 429, przekieruj go na mirror `https://maven-central.storage-download.googleapis.com/maven2/` skryptem init Gradle (`~/.gradle/init.d/`), a obraz Robolectric (`org.robolectric:android-all-instrumented`) pobierz z tego mirrora do `~/.m2/repository`. Gdy `dl.google.com` jest zablokowane, zobacz obejście w `tools/offline-build/`.
**Jeśli mimo to nie możesz uruchomić Gradle, a zlecenie zmienia kod lub `L.kt`, napisz to na początku raportu wielkimi literami i nie twierdź, że testy przechodzą.**

Pułapki z poprzednich tur:
- Testy w `app/src/test` kompilują się z `android.jar`: nie ma tam `com.sun.*`, `java.lang.management`, `jdk.jfr`.
- `Fighter.alive` to właściwość tylko do odczytu (`active && dying < 0f`).
- Zapis profilu działa w tle: w testach po `save()` wołaj `Profile.awaitHintWrites()` przed odczytem pliku.
- W tekstach Kotlin `%1$s` pisz jako `%1\$s`.
- Raporty balansu (`BalanceReport`) uruchamiaj naprawdę i wklejaj liczby; nie oddawaj kryterium „niezweryfikowane”.

## Praca równoległa
To zlecenie jest wykonywane równolegle z innymi (166–169, paczka zleceń zewnętrznych; jednocześnie koordynator i jego agenci pracują na `main`). Trzymaj się plików wymienionych w zakresie. Zlecenia 166, 168 i 169 **nie zmieniają kodu ani `L.kt`**; tylko 167 zmienia `L.kt` (wyłącznie treść istniejących wpisów).

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek** (wyjątek: `play-services-games-v2`). Nie dodawaj zależności.
- **Teksty UI:** tabela w `engine/L.kt` jest podzielona na funkcje `part0()`…`partN()` (limit 64 KB bajtkodu jednej metody JVM). Zajęte są części do **part206**; **następna wolna: part207**. Nowe klucze — tylko gdy zlecenie wprost na to pozwala — idą do nowej funkcji o **zarezerwowanym** numerze plus jedna linia `putAll(partNNN())` na końcu listy wywołań (liczba `putAll(part` = liczba `private fun part`). Rezerwacja w tej paczce: **part207 → zlecenie 167** (tylko gdyby nowy klucz był absolutnie konieczny; domyślnie 167 nie dodaje kluczy). Zlecenia 166, 168 i 169 nie dodają tekstów do `L.kt`. Format `"klucz" to t("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")` — **wszystkie 10 języków** w kolejności `L.CODES`. Test `LTest` sprawdza komplet języków, zgodność placeholderów (`%d`, `%s`, `%1$s`) i brak zdublowanych kluczy.
- **CI (decyzja właściciela — limit minut):** workflowy w `.github/workflows/` uruchamiają się tylko przy tagu `v*`, przy PR do `main` i ręcznie (`workflow_dispatch`). **Nie dodawaj workflowów ani wyzwalaczy przy `push`.**
- **Niewidomi (decyzja właściciela):** **nic nowego specjalnie dla niewidomych.** Istniejące wsparcie TalkBack zostaje, ale nie proponuj nowych funkcji dla niewidomych i nie obiecuj w sklepie ani w marketingu, że gra jest dla osób niewidomych.
- **Nie zmieniaj** zatwierdzonego układu `compactTop` (górny pasek bitwy) ani sposobu celowania ostrzału (`BarrageAiming`) i nie proponuj ich przeróbek.
- **`Daily.RELEASE_DAY` (2027-12-01) zostaje bez zmian** — decyduje o tym tylko właściciel.
- **Symulacja musi zostać deterministyczna:** w `sim/` żadnego `System.currentTimeMillis()`/`nanoTime()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng`. Zmiana przebiegu symulacji wymaga podbicia `SimVersion.PROTOCOL` — robi to wyłącznie koordynator.
- **Oryginalność:** nie kopiuj nazw, haseł, opisów, mechanik ani układów z innych gier; nie wymieniaj innych gier z nazwy (także jako porównania „jak w …”).
- **Grafika i UI:** płaski cartoon z grubym konturem, kolor drużyny dominujący (niebieski gracz, czerwony wróg); **bez emoji**.
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

## Git
- Commituj małymi krokami, wiadomości po polsku (pierwsza linia ≤ 72 znaki).
- Wypchnij **wyłącznie na swoją gałąź** (`git push -u origin <TWOJA_GAŁĄŹ>`). Nie pushuj na `main` ani na gałęzie `claude/*`/`integ/*`, nie rób force-push.
- **Nie otwieraj Pull Requesta** (PR do `main` uruchamia płatne CI). Koordynator sam przejrzy Twoją gałąź i ją scali.

## Raport końcowy (w ostatniej wiadomości sesji i w ostatnim commicie)
- nazwa wypchniętej gałęzi i hash ostatniego commita,
- co zrobiono, lista zmienionych i nowych plików,
- wyniki budowania i testów (wklej końcówkę logu), jeśli zmieniano kod lub `L.kt`,
- czego nie zrobiono i dlaczego, ryzyka.

---

# Prompt zlecenia

## Cel
Zaproponuj **6–8 nowych osobowości/stylów gry przeciwnika AI** (oraz ewentualnie 2–3 nowe typy wrogich jednostek specjalnych lub bossów Endless), które sprawią, że kolejne bitwy w kampanii, Potyczce i Rozkazach sztabu będą się wyraźnie różnić.

## Najpierw przeczytaj
`sim/src/main/kotlin/com/centurywar/game/Ai.kt`, `AiPersonality.kt` (obecne osobowości i ich miary), zlecenie 22 (`docs/tasks/22-ai-personalities.md`) i jego wynik, `Defs.kt` (role jednostek), `EndlessCards.kt` i bossowie Endless, `docs/BALANCE.md` (sekcje o AI), `docs/reports/d62-rozkazy.md` (tryb Rozkazy — `enemyAllowed`).

## Wymagania dla propozycji
- Każda osobowość musi być **rozpoznawalna dla gracza w pierwszych 60–90 s bitwy** (co widać na polu) i mieć jasną kontrę.
- Deterministyczna (ta sama `Rng`), bez dostępu do informacji ukrytych przed graczem.
- Opisz miary, którymi test potwierdzi różnicę (jak w zleceniu 22: udział ról, tempo ataków, użycie ostrzału, wieże).
- Wskaż, które etapy kampanii lub rozkazy najlepiej do niej pasują.

## Wynik
`docs/research/R170-wrogowie.md`. Wypchnij gałąź `gpt/170-research-enemies`.
**Charakter zlecenia: research, tylko dokument.** Nie zmieniaj kodu gry. Wynik to jeden plik raportu (ścieżka niżej) wypchnięty na Twoją gałąź. Pisz po polsku, konkretnie, z odwołaniami do plików i stałych w repo (`plik:linia`). Oryginalność: propozycje nie mogą kopiować mechanik, nazw ani wyglądu konkretnych istniejących gier i nie mogą się do nich odwoływać.

**Format każdej propozycji:** nazwa robocza (oryginalna), opis dla gracza w 2–3 zdaniach, jak działa w symulacji (które pola/klasy w `:sim` trzeba dodać lub zmienić, czy zmienia PROTOCOL i zapis bitwy), jak się z nią gra i jak ją kontrować, gdzie w grze się pojawia (kampania/potyczka/Endless/wyzwania/rozkazy), koszt (S/M/L), ryzyka dla balansu, jak ją zmierzyć na symulacji (konkretny test/raport w stylu `BalanceReport`, `DoctrineBalance82`, `OrdersBalanceReport`), czytelność na małym ekranie (telefon 720p, zewnętrzny ekran Folda 904×2316 px).

Na końcu: ranking TOP 3 z uzasadnieniem i 3 pytania do właściciela (nie-programisty) prostym językiem z rekomendacją.
