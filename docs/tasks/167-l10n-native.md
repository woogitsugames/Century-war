# Zlecenie 167: Natywny przegląd tłumaczeń 10 języków w L.kt

> **Repozytorium GitHub: `woogitsugames/century-war`** (https://github.com/woogitsugames/century-war).
> **Gałąź bazowa: `main`** — z niej utwórz swoją gałąź i wypchnij ją; koordynator scali (bez PR).

**Gałąź robocza:** `gpt/167-l10n-native` (utwórz z `origin/main`)

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
Przegląd **wszystkich tekstów gry** w `app/src/main/java/com/centurywar/engine/L.kt` oczami rodzimego użytkownika każdego z 10 języków (pl, en, de, es, fr, it, pt, uk, nl, tr) i poprawa tego, co brzmi nienaturalnie, niespójnie lub błędnie. Wynik: poprawki w `L.kt` + raport `docs/reports/l10n-167.md`, wypchnięte na gałąź `gpt/167-l10n-native`.

## Przeczytaj najpierw
- `docs/GLOSSARY.md` — kanoniczne nazwy pojęć w każdym języku (epoki, jednostki, tryby, zdolności, badania). Trzymaj się ich; jeśli uważasz, że nazwa w glosariuszu jest zła, nie zmieniaj jej w grze — opisz propozycję w raporcie.
- `docs/reports/d42-tlumaczenia.md`, `docs/reports/d53-jezyki.md`, `docs/research/R7-translations.md` — wcześniejsze przeglądy i ustalenia (nie cofaj ich poprawek).
- `app/src/test/java/com/centurywar/engine/LTest.kt` — testy języków (placeholdery, liczba mnoga, długość etykiet przycisków ≤ 18 znaków, terminologia, francuskie „vous”).

## Zasady językowe (obowiązujące)
- **Rejestr:** pl, de, es, it, uk, nl, tr — forma „ty” (du / tú / tu / ти / jij / sen); **fr — „vous”** w całej grze (także osiągnięcia i komunikaty); **pt — portugalski europejski (pt-PT) z formą „tu”** (np. „podes”, „tens”, „toca”), bez brazylijskich form („você”, gerundium zamiast „a + bezokolicznik” itp.); en — amerykańska pisownia.
- Francuska typografia: odstęp przed `:`, `!`, `?`, `;`, typograficzne apostrofy.
- Ton: krótko, jasno, przyjaźnie, jak w grze mobilnej; bez dosłownych kalk z polskiego ani angielskiego.
- Spójność: ta sama rzecz zawsze tą samą nazwą (patrz glosariusz), te same czasowniki na tych samych przyciskach w całej grze.

## Zakres zmian (ściśle)
- Wolno zmieniać **wyłącznie treść istniejących wpisów** (tekst w danym języku).
- **Nie zmieniaj kluczy**, kolejności języków, liczby wpisów, placeholderów (`%d`, `%s`, `%1$s`, `%%` — w Kotlinie `%1\$s`) ani separatorów form mnogich (`|`) i ich liczby. Nie dodawaj i nie usuwaj kluczy (wyjątek: zarezerwowana `part207` tylko w razie absolutnej konieczności — opisz w raporcie dlaczego; domyślnie nie używaj).
- Nie zmieniaj wartości liczbowych w tekstach (np. „+30 medali”) — muszą zgadzać się z kodem.
- Etykiety przycisków nie mogą się wydłużyć ponad limit z `LTest` (≤ 18 znaków); gdy poprawka wydłuża tekst wyświetlany w ciasnym miejscu, wybierz krótszy wariant.
- Polski jest językiem źródłowym — poprawiaj w nim tylko oczywiste błędy (literówki, gramatyka).
- Nie zmieniaj innych plików poza `L.kt` i raportem. Jeśli w innych plikach (np. `meta/Profile.kt`) zauważysz teksty w 10 językach z błędami — tylko opisz je w raporcie.

## Raport (`docs/reports/l10n-167.md`)
- Podsumowanie dla właściciela (po polsku, 5–8 zdań): ile zmian w każdym języku, najważniejsze problemy.
- Tabela zmian: klucz, język, było, jest, powód (np. „kalka”, „rejestr”, „termin wg glosariusza”, „literówka”).
- Lista „do decyzji” — rzeczy, których nie zmieniłeś (np. propozycje zmiany glosariusza, teksty, które wymagają kontekstu z gry).
- Wyniki `LTest` i `assembleDebug` (końcówka logu). **Bez zielonego `LTest` gałąź nie jest gotowa.**

## Czego nie robić
- Nie zmieniaj kluczy, placeholderów, liczby form mnogich, kodu ani testów (nie „naprawiaj” testów pod swoje teksty).
- Nie dodawaj tekstów o funkcjach dla niewidomych.
- Nie otwieraj PR.
