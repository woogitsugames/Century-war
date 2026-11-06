# Zlecenie 166: Niezależny audyt kodu (raport, bez zmian w kodzie)

> **Repozytorium GitHub: `woogitsugames/century-war`** (https://github.com/woogitsugames/century-war).
> **Gałąź bazowa: `main`** — z niej utwórz swoją gałąź i wypchnij ją; koordynator scali (bez PR).

**Gałąź robocza:** `gpt/166-code-audit` (utwórz z `origin/main`)

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
Niezależny, dokładny **audyt kodu** Century War przed pierwszym wydaniem dla testerów zamkniętych. **To zlecenie jest wyłącznie raportem — nie zmieniaj żadnego pliku poza raportem** (bez poprawek kodu, testów, dokumentów, `L.kt`). Wynik: jeden plik `docs/reports/audit-166.md` wypchnięty na gałąź `gpt/166-code-audit`.

## Czego nie powtarzać
Przeczytaj najpierw poprzednie audyty i przeglądy: `docs/reports/audit-156.md` (audyt przed premierą, znaleziska A156-xx), `docs/reports/d39-audyt.md`, `docs/reports/d49-przeglad.md` oraz zlecenia, które je naprawiały (`docs/tasks/154-robustness.md`, `160-lifecycle-profile.md` … `165-privacy-docs.md`). **Nie zgłaszaj ponownie znalezisk już opisanych** — chyba że sprawdziłeś w aktualnym kodzie, że poprawka jest niepełna albo wprowadziła nowy błąd; wtedy podaj ID poprzedniego znaleziska i wyjaśnij, czego brakuje.

## Zakres (czytaj kod, nie tylko dokumenty)
1. **Zapisy i odczyty:** `meta/Profile.kt` (zapis JSON w tle, zapis atomowy, kopia/`ProfileBackup`, `PendingDeletions`), `meta/PlaytestLog.kt` i `crash/` (Raport dla testera, `CrashStore`), `sim/.../BattleSnapshot.kt` i `SaveGuard.kt` (wznawianie przerwanej bitwy, `LostBattle` — utracona bitwa po aktualizacji), `ReplayRecord`/`ReplayStore`/`ReplayImport`, `BattleHistory`. Co się stanie przy zabiciu procesu w połowie zapisu, braku miejsca, starym formacie, zapisie z nowszej wersji, równoległym zapisie z dwóch wątków.
2. **Rozliczenia nagród:** medale, gwiazdki, korony, zwrot próby i medale za przerwane rankingowe wyzwanie dnia, powitanie po przerwie (`WelcomeBack`), misje, osiągnięcia (lokalne i `OnlineAchievements`), kamienie milowe Endless, nagrody tygodnia. Szukaj podwójnego naliczenia (np. obrót ekranu/powrót z tła na ekranie wyników, ponowne wejście do ekranu, wznowienie ze snapshotu), utraty nagrody (zabicie procesu między naliczeniem a zapisem) i przepełnień liczników.
3. **Wątki:** zapis w tle, dźwięk (`Sound`, `SoundSynth`, `PendingSfx`), muzyka (`Music`, `RecordedMusic`, `RecordedMixer`), `ThermalWatch`, `QualityGovernor`, `ScoreClient`/`ScoreQueue`, Play Games. Wyścigi danych, zakleszczenia, dostęp do stanu gry spoza wątku gry, wycieki wątków/odbiorników.
4. **Cykl życia Activity:** `MainActivity`, `CenturyWarApp`, `GameView` — pauza, obrót, podzielony ekran, zmiana rozmiaru (Fold: zewnętrzny/wewnętrzny ekran), powrót z tła, zabicie procesu i odtworzenie, zmiana języka i ustawień systemowych w trakcie, `TestLoopActivity` w procesie `:testloop`.
5. **R8 / wydanie:** reguły `app/proguard-rules.pro` (lub odpowiednik), refleksja, serializacja, klasy wywoływane z manifestu, `tools/r8-sim-smoke`, `tests/test_verify_release_*.py` — czy w buildzie release coś może się wysypać, a w debug działa.
6. **Odporność na uszkodzone dane:** uszkodzony/ucięty/ogromny JSON profilu, snapshot z błędnymi długościami, powtórka z zaimportowanego pliku (`ReplayImport`, `ReplayShareProvider`), wartości spoza zakresu (ujemne medale, nieznane ordinal enumów, NaN), brak plików, pliki z innej wersji gry.

## Format raportu (`docs/reports/audit-166.md`)
- Na początku: data, hash commita `main`, który audytowałeś, oraz informacja, czy uruchamiałeś Gradle/testy (jeśli nie — wielkimi literami).
- Podsumowanie dla właściciela-nieprogramisty (5–10 zdań, po polsku): co jest najgroźniejsze i czy blokuje testy zamknięte.
- Tabela znalezisk: **ID** (`A166-01` …), **ważność** (Krytyczna / Wysoka / Średnia / Niska), obszar, **plik:linia** (aktualne numery linii), opis, **scenariusz odtworzenia** (konkretne kroki albo test), proponowana poprawka (opis, nie kod do wklejenia).
- Oznacz wyraźnie „podejrzenie”, jeśli czegoś nie potwierdziłeś w kodzie; nie zgłaszaj rzeczy niesprawdzonych jako pewnych.
- Sekcja „Sprawdzone, bez zastrzeżeń” — krótka lista obszarów, które przejrzałeś i są w porządku (żeby nie sprawdzać ich znowu).
- Na końcu: proponowane zlecenia naprawcze (krótkie tytuły, zakres, pliki), pogrupowane w paczki o rozłącznych plikach.

## Czego nie robić
- Nie zmieniaj kodu, testów, balansu, `L.kt`, workflowów ani innych dokumentów — tylko `docs/reports/audit-166.md`.
- Nie proponuj zmian `compactTop`, celowania ostrzału, `Daily.RELEASE_DAY` ani nowych funkcji dla niewidomych.
- Nie otwieraj PR.
