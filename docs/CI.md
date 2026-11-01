# CI Android: Century War

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
Domyślnie są maksymalnie dwa procesy testowe po 1 GiB sterty,
`forkEvery=15` klas. Dzięki temu testy nie zajmują niekontrolowanie
pamięci na maszynach z 8–16 GB RAM. `quickCheck` filtruje
`testDebugUnitTest` **tylko wtedy, gdy uruchomiono zadanie quickCheck**;
nie należy łączyć obu nazw zadań w jednej komendzie, jeżeli potrzebny
jest pełny przebieg.

## Równoległe CI

Workflow: [`.github/workflows/android.yml`](../.github/workflows/android.yml).
Każdy push/PR uruchamia niezależne joby, bez uzależniania testów od audytu licencji:

| Job | Zakres |
|---|---|
| `build` | Debug APK, `buildSrc:test`, zależności i kontrola licencji |
| `tests (jvm)` | Wszystkie testy JVM (w tym deterministyczna symulacja) |
| `tests (robolectric)` | Logika z Robolectric, bez ciężkiego renderowania |
| `tests (screen-phone720)` | `ScreenshotTest.screens` i `tutorial`, format 1600×720 |
| `tests (screen-phone20x9)` | Te same metody, format 2400×1080 |
| `tests (screen-foldOpen)` | Te same metody, rozłożony Fold 2184×1968 |
| `tests (screens-other)` | Pozostałe metody `ScreenshotTest`, galeria, ikony, renderer |
| `server-tests` | Pełne `:server:test` wraz z weryfikacją powtórek |
| `lint` | Android Lint, informacyjnie (nieblokujący) |
| `test-timings` | Łączy XML z jobów i publikuje top 20 klas/metod |

Joby testów mają limit 20 min, a celem jest **cały przebieg CI poniżej
20 min czasu ściennego**. Nie jest to gwarantowany wynik dla zimnego cache:
pobranie Gradle, Robolectric i SDK może podnieść czas. Rzeczywiste czasy
jobów należy sprawdzać w zakładce Actions; raport JUnit liczy *czas metod*,
który sumuje się między równoległymi shardami.

`scripts/ci_test_shards.py` odczytuje bieżące pliki `app/src/test`
i generuje rozłączne selektory Gradle. Polecenie:

```bash
python3 scripts/ci_test_shards.py verify
python3 scripts/ci_test_shards.py args jvm
```

sprawdza pokrycie wszystkich wykrytych metod `@Test` i odrzuca nieznane
selektory lub duplikaty. `ScreenshotTest.screens` i `tutorial` działają
na wszystkich trzech formatach przy zwykłym lokalnym uruchomieniu.
W CI filtr `CW_SCREENSHOT_DEVICE=phone720|phone20x9|foldOpen` wybiera
jeden format na job. **Nie pomijamy żadnego formatu ani metody**.
Raporty balansu (`BALANCE_RUN=true`) i generator sklepu
(`STORE_GRAPHICS=true`) pozostają na żądanie, jak poprzednio.

Każdy job zachowuje osobny artefakt `cw-tests-*-results`, shardy obrazów
również PNG, a `build` — `century-war-debug-apk`.
Gradle wykorzystuje `gradle/actions/setup-gradle@v4`, Robolectric ma
dodatkowy cache `~/.m2/repository/org/robolectric` (w tym
`android-all-instrumented`). `server.yml` nadal sprawdza także
budowanie obrazu Docker po zmianach w `server/` albo `sim/`.

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
zarejestrowanych metod:

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

Push taga `v*` (np. `v0.1.0`) uruchamia wylacznie job `release`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Przed tagowaniem upewnij sie, ze commit zawiera workflow i przechodzi CI. Job **przerywa prace**, jesli brakuje choc jednego z czterech sekretow; nie wolno publikowac buildow produkcyjnych podpisanych kluczem debug. Po udanym buildzie dostepne sa artefakty `century-war-release-apk` i `century-war-release-aab`. To sa artefakty GitHub Actions, a nie automatyczna publikacja w Google Play.

## Lokalny release

Bez zmiennych srodowiskowych `:app:assembleRelease` i `:app:bundleRelease` nadal korzystaja z debug signingConfig, zeby nie popsuc dotychczasowego lokalnego procesu. Aby lokalnie sprawdzic podpis produkcyjny, ustaw wszystkie cztery zmienne (najlepiej w menedzerze sekretow, bez zapisywania ich w repo):

| Zmienna Gradle | Wartosc |
|---|---|
| `RELEASE_KEYSTORE_FILE` | Bezwzgledna sciezka do pliku `.jks` |
| `RELEASE_STORE_PASSWORD` | Haslo keystore |
| `RELEASE_KEY_ALIAS` | Alias klucza |
| `RELEASE_KEY_PASSWORD` | Haslo klucza |

Przy czesciowo ustawionych zmiennych konfiguracja Gradle zwroci blad zamiast niejawnie uzyc klucza debug.

## Diagnostyka

- Kod buduje sie na JDK 17 i Android SDK z `platforms;android-36` oraz `build-tools;36.0.0`.
- Testy symulacji, reguł, zrzutów bitwy i lokalizacji dzialaja w JVM; nie wymagaja emulatora. Testy Robolectric (SDK 35) pobieraja przy pierwszym uruchomieniu duze artefakty; kolejne korzystaja z cache.
- Artefakty z jobow debug/lint/robolectric sa przechowywane przez 14 dni, a release przez 90 dni.
- Raport z nieudanego kroku oraz diagnostyka `lint` znajduja sie na stronie uruchomienia workflow w zakladce **Actions**.
