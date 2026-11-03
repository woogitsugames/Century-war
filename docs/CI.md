# CI Android: Century War

## Zlecenie 162: bramki wydania i pamięć CI

Na tagu `v*` job `release` czeka na zielone `build`, `fast-tests`,
`screenshot-tests` i `balance-tests`. Każdy wykonuje checkout
tego samego SHA wskazanego przez tag (zdarzenie GitHub Actions).
Błąd lub pominięcie jednej bramki blokuje podpisany APK/AAB.
Bieżący problem balansu A156-13 może nadal blokować wydanie:
nie wolno usuwać testów lub asercji, aby to obejść.

### Zmienne GitHub Actions

| Typ | Nazwa | Przeznaczenie |
|---|---|---|
| Secret | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Podpis upload |
| Secret | `CW_HMAC_KEY` | Konfiguracja podpisu żądań rankingu |
| Variable | `CW_SERVER_URL` | HTTPS adres serwera |
| Variable | `CW_PRIVACY_URL` | HTTPS adres opublikowanej polityki |

Zmienne `CW_*` trafiają jawnie do kroku kompilacji release.
Weryfikator zgłasza wyłącznie nazwy brakujących ustawień, bez wartości.
Nadal wymagane są wypełnione dokumenty wydawcy, polityka prywatności,
ID Play Games, prawidłowa data wydania i numer wersji.
Zastępcze `example` i `REPLACE_ME` pozostają odrzucane.
**Uwaga:** obecny `CW_HMAC_KEY` jest osadzany w aplikacji (`BuildConfig`);
sekret Actions nie chroni go przed odczytem z APK. Bezpieczeństwo
rankingu wymaga osobnego przeglądu modelu uwierzytelniania.

### Graf, pamięć i kompletność testów

`:app:test` jest przepinane na `testDebugUnitTest` po utworzeniu
wariantów AGP. `fast-tests` sprawdza `:app:test --dry-run` i odrzuca
`testReleaseUnitTest` oraz `releaseChecklist`. Testy buildSrc
uwzględniają 71 wymaganych kluczy Play Games.

Robolectric (noc 9): `maxHeapSize=768m`, okresowy GC (`-XX:G1PeriodicGCInterval=2000`),
`forkEvery=50`, domyślnie 4 procesy (nie więcej niż rdzeni, `-PtestForks=N`).
Szczegóły i pomiary: [TEST_SPEED.md](TEST_SPEED.md), sekcja „Noc 9”.
Klasy `PrereleasePolish*` należą do `screenshots`, nie do `fast`.
Audyt 156 potwierdził zaliczone **osobne** kategorie przy 8 GiB,
jednym forku i takiej izolacji (fast: 8 min 22 s;
screenshots: 9 min 48 s). **To nie jest aktualny pomiar
pełnego `:app:testDebugUnitTest`.**

```bash
# Pełny pomiar pod limitem 8 GiB:
./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache --max-workers=1 -PtestForks=1

# Pełny pomiar na hoście 16 GiB (domyślnie 4 procesy):
./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache
```

### Zrzuty PNG (A156-27)

`app/build/screenshots/` jest outputem zadania testowego Gradle.
Przy `FROM-CACHE` PNG muszą być odtworzone razem z raportami.
Job `screenshot-tests` weryfikuje 30 plików (15 scen × telefon i Fold),
usuwa wygenerowany katalog, wymaga ponownego przebiegu
`:app:testDebugUnitTest FROM-CACHE`, a następnie sprawdza te same PNG.
Brak obrazu, niepoprawny nagłówek albo brak cache kończy job błędem.
Log powtórzenia znajduje się w artefakcie `cw-tests-screenshots-results`.

```bash
python3 scripts/verify_ci_screenshots.py app/build/screenshots
python3 -m unittest discover -s tests -p 'test_*.py' -v
./gradlew -p buildSrc test --console=plain --no-configuration-cache
```

Regresję bramek zabezpieczają `tests/test_ci_release_gates.py`
i `tests/test_verify_ci_screenshots.py`. `updateScreenshots`
pomija cache, żeby faktycznie aktualizować wzorce.

**Weryfikacja:** przed publikacją odczytaj wyniki pełnego
Gradle i jobów GitHub Actions dla wskazanego SHA; analiza
samego YAML nie jest potwierdzeniem przejścia testów.

---


Workflow: [`.github/workflows/android.yml`](../.github/workflows/android.yml).

## Szybka kontrola lokalna

Po pierwszym pobraniu SDK 36 i zależności uruchom:

```bash
./gradlew quickCheck --console=plain --no-configuration-cache
# To samo z modułu: ./gradlew :app:quickCheck
```

`quickCheck` buduje APK debug i wykonuje reprezentatywne testy symulacji
(reguły, deterministyczność, snapshot, replay, AI, karty, wyzwanie dnia),
`LTest` oraz wybrane Robolectric (`ProfileTest`, `BattleScreenTest`).
Nie generuje setek PNG. Cel: **poniżej 5 min przy rozgrzanym cache**;
pierwsze uruchomienie z pobieraniem SDK/Robolectric może potrwać dłużej.
Pełny lokalny zestaw nie jest zastępowany tym zadaniem:

```bash
./gradlew :app:testDebugUnitTest :server:test --console=plain --no-configuration-cache
```

Własność `-PtestForks=1` ogranicza testy do jednego procesu JVM.
Domyślnie są maksymalnie cztery procesy testowe (nie więcej niż rdzeni)
po 768 MiB sterty z okresowym GC; każdy zajmuje do ok. 2 GB RSS.
Na maszynie z 8 GB RAM użyj `-PtestForks=2` (lub `1`). `quickCheck` filtruje
`testDebugUnitTest` **tylko wtedy, gdy uruchomiono zadanie quickCheck**;
nie należy łączyć obu nazw zadań w jednej komendzie, jeżeli potrzebny
jest pełny przebieg.

## Równoległe CI

Aktualny workflow uruchamia `build` (APK, buildSrc i skrypty Python),
`fast-tests` (app/sim/server), `screenshot-tests` (Robolectric,
30 PNG i cache), `balance-tests` (raporty balansu), `test-timings`
(zbiorcze czasy) oraz `lint` (nieblokujący). Pierwsze cztery joby
są także bramkami wydania na tagu. Kategorie są rozłączne,
a wzorce sprawdza `scripts/ci_test_categories.py`.

## Pomiar czasów i punkt odniesienia

Skrypt odczytuje `TEST-*.xml` (także z katalogów wielu shardów):

```bash
python3 scripts/ci_test_timings.py app/build/test-results/testDebugUnitTest --top 20
python3 scripts/ci_test_timings.py ci-test-artifacts --top 20 --output ci-test-timings.md
```

**Pomiar bazowy:** raport `robolectric-test-reports` z workflow Android CI
nr `41291246736`, 1 listopada 2026. W archiwum było **140 przypadków
w 17 klasach**, więc nie jest to pomiar wszystkich testów w repozytorium.
`ScreenshotTest`: 22 testy i **983,91 s** (16 min 24 s). Top 20
zarejestrowanych metod.

W archiwum występuje 17 klas (pozostałych trzech pozycji top 20 nie
można zmierzyć z tego przebiegu). Najwolniejsze klasy:

| Klasa | Czas |
|---|---:|
| `ScreenshotTest` | 983,91 s |
| `BattleScreenTest` | 14,36 s |
| `AiPersonalityProfileTest` | 6,92 s |
| `AppIconScreenshotTest` | 5,11 s |
| `BackLocaleTest` | 3,02 s |
| `ArtGalleryTest` | 2,51 s |
| `RendererStressTest` | 2,43 s |
| `ProfileCloudTest` | 0,67 s |
| `ProfileTest` | 0,58 s |
| `BackdropsTest` | 0,31 s |
| `MissionsTest` | 0,30 s |
| `StartupTest` | 0,29 s |
| `BattleHistoryTest` | 0,27 s |
| `SkirmishSetupTest` | 0,14 s |
| `HintOverlayTest` | 0,13 s |
| `ScreenTransitionScreenshotTest` | 0,13 s |
| `AchievementPersistenceTest` | 0,09 s |

Top 20 najwolniejszych metod:

| Metoda | Czas |
|---|---:|
| `ScreenshotTest.screens` | 549,59 s |
| `ScreenshotTest.tutorial` | 304,23 s |
| `ScreenshotTest.menus` | 64,50 s |
| `ScreenshotTest.campaignJourneyMap` | 16,30 s |
| `ScreenshotTest.accessibility` | 13,63 s |
| `BattleScreenTest.firstBattleLongPressHintUsesCompatibleProfileBit` | 10,32 s |
| `ScreenshotTest.codexAllCategories` | 7,73 s |
| `AiPersonalityProfileTest.measureAndReport` | 6,92 s |
| `ScreenshotTest.endlessBossVariants` | 6,18 s |
| `ScreenshotTest.contextualHints` | 3,71 s |
| `ScreenshotTest.battleHistoryScreens` | 3,69 s |
| `BackLocaleTest.activityConfigurationRefreshesAutoLocaleButPreservesManualSelection` | 2,98 s |
| `AppIconScreenshotTest.launcherSizesAndStoreExport` | 2,94 s |
| `ScreenshotTest.weatherScreens` | 2,93 s |
| `RendererStressTest.simultaneousBarragesStayWithinParticleBudget` | 2,43 s |
| `ScreenshotTest.variantScreens` | 2,31 s |
| `AppIconScreenshotTest.legacyThemeDisplaysBrandedWindow` | 2,06 s |
| `BattleScreenTest.longPressDoesNotClickAndShortTapStillWorks` | 1,76 s |
| `ScreenshotTest.replays` | 1,66 s |
| `ScreenshotTest.playGamesMenus` | 1,59 s |

Pierwotny job `build-and-test` na gałęzi bazowej jest już czerwony:
`scripts/check_license_inventory.py` zgłasza brakujące wpisy serwera
w `docs/LICENSES.md`. To osobny problem **sprzed niniejszej zmiany**.
Nowy job `build` nadal traktuje ten audyt jako blokujący, ale wykonuje
kompilację i zachowuje APK przed kontrolą licencji; testy biegną niezależnie.

## Keystore do podpisywania release

Wygeneruj nowy klucz upload do Google Play i przechowuj plik oraz hasla poza repozytorium. Przy pierwszym publikowaniu wybierz trwale wartosci — zmiana klucza upload bedzie wymagac procedury w Google Play Console.

```bash
keytool -genkeypair -v \
  -keystore century-war-upload.jks \
  -alias century-war-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Komenda interaktywnie poprosi o haslo keystore i haslo klucza (moga byc takie same). Wygeneruj zawartosc sekretu `KEYSTORE_BASE64` z pliku `.jks`:

```bash
base64 -w 0 century-war-upload.jks > century-war-upload.b64   # Linux
# Na macOS: base64 < century-war-upload.jks | tr -d '\n'
```

W repozytorium GitHub otworz **Settings > Secrets and variables > Actions > New repository secret** i dodaj cztery sekrety:

| Sekret | Wartosc |
|---|---|
| `KEYSTORE_BASE64` | Cala zawartosc zakodowanego Base64 pliku `.jks` (bez nowych linii) |
| `KEYSTORE_PASSWORD` | Haslo do keystore |
| `KEY_ALIAS` | Alias klucza, np. `century-war-upload` |
| `KEY_PASSWORD` | Haslo do klucza |

Nie umieszczaj keystore, zakodowanego Base64 ani hasel w commitach, artefaktach lub logach. Zachowaj kopie zapasowa `.jks` i danych logowania w bezpiecznym menedzerze sekretow. GitHub Actions odtwarza plik tylko w katalogu tymczasowym runnera; nie zapisuje go w artefaktach.

## Release po tagu

Push taga `v*` (np. `v0.1.0`) uruchamia joby `build`, `fast-tests`,
`screenshot-tests`, `balance-tests`, a po ich sukcesie `release`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Przed tagowaniem upewnij sie, ze commit zawiera workflow i przechodzi CI. Job **przerywa prace**, jesli brakuje choc jednego z czterech sekretow; nie wolno publikowac buildow produkcyjnych podpisanych kluczem debug. Po udanym buildzie dostepne sa artefakty `century-war-release-apk` i `century-war-release-aab`. To sa artefakty GitHub Actions, a nie automatyczna publikacja w Google Play.

## Lokalny release

Bez czterech zmiennych srodowiskowych `:app:assembleRelease` i `:app:bundleRelease` kończą się błędem; nie ma już automatycznego podpisu debug. Do lokalnego builda produkcyjnego ustaw wszystkie cztery zmienne (najlepiej w menedżerze sekretów, poza repo):

| Zmienna Gradle | Wartosc |
|---|---|
| `RELEASE_KEYSTORE_FILE` | Bezwzgledna sciezka do pliku `.jks` |
| `RELEASE_STORE_PASSWORD` | Haslo keystore |
| `RELEASE_KEY_ALIAS` | Alias klucza |
| `RELEASE_KEY_PASSWORD` | Haslo klucza |

Przy częściowo ustawionych zmiennych konfiguracja Gradle zwróci błąd. Plik debug.keystore i alias androiddebugkey nie są akceptowane jako produkcyjny podpis. Testowy podpis debug wymaga jednocześnie flagi `-PciTestDebugSigning=true`, zdarzenia pull_request / workflow_dispatch i oznaczenia `CW_RELEASE_SMOKE=true` w GitHub Actions. Flaga nie działa podczas publikacji taga. Dane wydawcy uzupełnia się w [OWNER_INPUT.md](store/OWNER_INPUT.md), a gotowe polityki generuje zadanie `:app:renderStorePolicies`.

## Diagnostyka

- Kod buduje sie na JDK 17 i Android SDK z `platforms;android-36` oraz `build-tools;36.0.0`.
- Testy symulacji, reguł, zrzutów bitwy i lokalizacji dzialaja w JVM; nie wymagaja emulatora. Testy Robolectric (SDK 35) pobieraja przy pierwszym uruchomieniu duze artefakty; kolejne korzystaja z cache.
- Artefakty z jobow debug/lint/robolectric sa przechowywane przez 14 dni, a release przez 90 dni.
- Raport z nieudanego kroku oraz diagnostyka `lint` znajduja sie na stronie uruchomienia workflow w zakladce **Actions**.
