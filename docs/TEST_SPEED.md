# Podział testów (zlecenia 99 i 131)

Wszystkie testy istnieją nadal. Zadania wybierają rozłączne zestawy metod
z **oryginalnych** zadań Gradle (`:app:testDebugUnitTest`, `:sim:test`, `:server:test`).
Dzięki temu nie tworzymy osobnej konfiguracji Robolectric i nie pomijamy zasobów Androida.

| Zadanie | Uruchamiane testy |
|---|---|
| `./gradlew fastTest` | Testy Android debug, symulacji i serwera poza ciężkimi kategoriami |
| `./gradlew screenshotTest` | `*ScreenshotTest*`, galerie grafiki, tła, renderowanie i testy ekranów low-end |
| `./gradlew balanceTest` | `*Balance*`, wskazane raporty AI (`measureAndReport`, `tenSeedAiWaveReport`), długie pojedynki kampanii, Endless i lustrzane doktryny |
| `./gradlew :app:testDebugUnitTest` | Cały zestaw debug bez filtrów |
| `./gradlew test` | Zestawy standardowe modułów; test release Android jest pomijany |

Wzorce kategorii są utrzymywane **wyłącznie** w
[`gradle/test-categories.properties`](../gradle/test-categories.properties).
Wszystko, co nie pasuje do `screenshots` lub `balance`, należy do `fast`.
Wzorce mogą wskazywać pojedynczą metodę (`*BattleSimTest.campaignStagesFinish`),
aby zachować szybkie asercje w tej samej klasie.
Narzędzie `python3 scripts/ci_test_categories.py verify` sprawdza podział
wszystkich odkrytych metod JUnit w trzech modułach.

## Przykłady

```bash
# Codzienna ścieżka (< 10 minut po rozgrzaniu cache - cel, nie pomiar)
./gradlew fastTest --console=plain --no-configuration-cache

# Zrzuty i testy graficzne na formaty ekranów
./gradlew screenshotTest --console=plain --no-configuration-cache -PtestForks=1

# Długie pomiary - domyślnie BalanceReport używa 20 seedów
BALANCE_RUN=true ./gradlew balanceTest --console=plain --no-configuration-cache

# Test wariantu release uruchamiaj osobno, po przygotowaniu podpisu i checklisty
./gradlew :app:releaseChecklist
```

Nie łącz `fastTest`, `screenshotTest` i `balanceTest` w jednym wywołaniu:
opierają się na tej samej nazwie zadania Gradle i różnią filtrem.
Można je uruchamiać **równolegle w osobnych procesach/runnerach**.
Samo `./gradlew :app:test` uruchamia tylko `testDebugUnitTest`, a nie
`testReleaseUnitTest` ani związanych z wydaniem zależności.

## CI i pomiary

`.github/workflows/android.yml` uruchamia trzy niezależne joby: `fast-tests`,
`screenshot-tests` i `balance-tests`. Pozostały także osobne zadania budowania,
lint i podpisanego wydania na tagu. Job `balance-tests` ogranicza liczbę seedów
raportu do **2**, ale uruchamia pełne testy innych ciężkich kategorii.
Pełny raport kampanii z **20 seedami** jest dostępny przez `balanceTest`
lub dedykowany workflow `campaign-balance.yml`.

Każdy job zapisuje czas **rzeczywistego wywołania Gradle** w sekundach
(`ci-test-*.duration`) i XML JUnit. `test-timings` dodaje do GitHub Actions
zbiorczy raport i listę najwolniejszych testów.

**Historyczny punkt odniesienia (zlecenie 99):** około **60 minut** pełnego zestawu,
według treści ówczesnego zlecenia, bez bezpośredniego pomiaru tej gałęzi.
**Wynik po zmianie:** wymaga uruchomienia GitHub Actions. Nie należy traktować
celu 10 minut jako potwierdzonego wyniku. Aby porównać uczciwie,
mierz czas Gradle na tym samym runnerze/cachu i porównaj osobno
szybką ścieżkę oraz całkowity czas ukończenia trzech równoległych jobów.

## Izolacja testów

Nie połączono instancji `Game` pomiędzy testami: korzystają one z mutowalnego
stanu profilu, języka i bitwy. Bez benchmarku nie można potwierdzić, że współdzielenie
przyspieszy wykonanie bez wprowadzenia zależności między testami.
Reużycie procesu JVM oraz zasobów Robolectric zapewniają oryginalne zadania AGP.

## Zlecenie 131: pomiar pełnego zestawu i redukcja rasteryzacji

**Cel:** poniżej 25 min dla `./gradlew :app:testDebugUnitTest :sim:test :server:test`
na 4 CPU / 15 GB RAM, bez wyłączania testów i zmiany asercji.

| Przebieg | Czas ścienny | Źródło |
|---|---:|---|
| Przed zmianami | ok. **47 min** | podany w zleceniu pomiar kontenera koordynatora; brak zachowanego logu |
| Po zmianach | **niezmierzony** | workflow `test-speed.yml`; wymaga zakończonego uruchomienia na porównywalnym sprzęcie |

### 20 najwolniejszych klas przed zmianą — dostępne XML

Artefakty GitHub Actions z **1.11.2026**:
`robolectric-test-reports` (run `41265121701`, SHA `f21df06`),
`sim-jvm-test-reports` (run `41338537587`, SHA `4fb015f`) oraz
`server-test-reports` (run `41338537571`, SHA `4fb015f`).
To **różne uruchomienia, częściowo różne commity**: ich czasów nie wolno
sumować jako czasu ściennego jednego przebiegu. XML zawierają 63 klasy i
412 uruchomień metod; nie reprezentują kompletnego aktualnego zestawu.

| # | Moduł | Klasa | Metody | Czas XML (s) |
|---:|---|---|---:|---:|
| 1 | `app` | `com.centurywar.screens.ScreenshotTest` | 22 | 983.91 |
| 2 | `app` | `com.centurywar.screens.BattleScreenTest` | 17 | 14.36 |
| 3 | `sim` | `com.centurywar.game.AiPersonalityProfileTest` | 1 | 10.99 |
| 4 | `sim` | `com.centurywar.game.BattleRobustnessTest` | 9 | 8.36 |
| 5 | `app` | `com.centurywar.game.AiPersonalityProfileTest` | 1 | 6.92 |
| 6 | `server` | `com.centurywar.server.EndlessBalanceReportTest` | 2 | 6.78 |
| 7 | `sim` | `com.centurywar.game.Doctrine69BattleSimTest` | 2 | 5.13 |
| 8 | `app` | `com.centurywar.AppIconScreenshotTest` | 4 | 5.11 |
| 9 | `app` | `com.centurywar.BackLocaleTest` | 2 | 3.02 |
| 10 | `app` | `com.centurywar.game.ArtGalleryTest` | 8 | 2.51 |
| 11 | `sim` | `com.centurywar.game.BattleSimTest` | 10 | 2.44 |
| 12 | `app` | `com.centurywar.game.RendererStressTest` | 1 | 2.43 |
| 13 | `sim` | `com.centurywar.game.BattleSnapshotTest` | 34 | 2.25 |
| 14 | `sim` | `com.centurywar.game.AiPersonalityTest` | 13 | 1.02 |
| 15 | `app` | `com.centurywar.meta.ProfileCloudTest` | 8 | 0.67 |
| 16 | `server` | `com.centurywar.server.ServerAbuseTest` | 7 | 0.66 |
| 17 | `app` | `com.centurywar.meta.ProfileTest` | 26 | 0.58 |
| 18 | `server` | `com.centurywar.server.ApiTest` | 6 | 0.58 |
| 19 | `sim` | `com.centurywar.game.BattleIndexTest` | 3 | 0.52 |
| 20 | `sim` | `com.centurywar.game.ReplayTest` | 16 | 0.36 |

W `ScreenshotTest` metody `screens` (**549,59 s**), `tutorial`
(**304,23 s**) i `menus` (**64,50 s**) dominują w dostępnym profilu.
`screens` odtwarza po 4200 klatek w każdej z 12 bitew, ale do zapisania
PNG potrzebna jest tylko końcowa klatka. W samouczku większość klatek
służy oczekiwaniu na kolejne warunki, a nie zapisowi obrazu.

### Dokonane optymalizacje

- `shot()` wykonuje **tyle samo aktualizacji** `Game.frame` i identyczne
  `dt`, ale raster pełnej rozdzielczości jest tworzony tylko w ostatniej
  klatce. Wcześniejsze trafiają do bitmapy 4×4 px.
- `screens` nadal wykonuje 4200 klatek na bitwę (4199 na bitmapie 4×4 px
  i ostatnią w docelowej rozdzielczości); każda bitmapa jest zwalniana.
  `tutorial` renderuje klatki oczekiwania w małym buforze, a nadal zapisuje
  pełne PNG i wykonuje wszystkie kontrole UI.
- Test `intermediateFramesProduceTheSameBattleState` porównuje czas bitwy,
  złoto i liczbę zabójstw po 90 pełnych i 90 małych renderach.
- `forkEvery = 0` umożliwia ponowne użycie procesu testowego Robolectric;
  limit wynosi maks. 2 równoległe forki (`-PtestForks=1` na słabym RAM,
  `-PtestForks=2` na 15 GB). **Nie uruchamiać dwóch Gradle równolegle**
  przy 15 GB: można wyczerpać pamięć mimo limitu forków.
- `ScreenshotGoldenTest` należy do `screenshots`, a
  `LowEndScreensPerformanceTest` do sharda `visual`; żaden test nie jest
  pominięty, asercje i progi regresji są niezmienione.
- `regression100` zachowuje swój własny przebieg z `dt=0` i porównanie
  kompletu **30** wzorców PNG, bez zmian tolerancji.

### Pomiary po zmianie

```bash
python3 scripts/ci_test_shards.py verify
python3 scripts/ci_test_categories.py verify
python3 -m unittest discover -s tests -p 'test_*.py' -v
/usr/bin/time -f 'FULL_TEST_WALL=%e' \
  ./gradlew :app:testDebugUnitTest :sim:test :server:test \
  --console=plain --no-configuration-cache -PtestForks=2
python3 scripts/ci_test_timings.py \
  app/build/test-results/testDebugUnitTest \
  sim/build/test-results/test \
  server/build/test-results/test --top 20
```

Nowy `.github/workflows/test-speed.yml` uruchamia pełny zestaw jednym
poleceniem na jednym runnerze i zapisuje czas ścienny, XML, log oraz top 20
do artefaktu `cw-full-suite-speed-results`. Czas z XML to suma metod,
a nie czas ścienny Gradle (część testów biegnie równolegle). **Nie wolno
wpisywać wyniku „po” na podstawie założeń**: wymaga prawdziwego pomiaru
na tym samym CPU, RAM, JDK, cache i zestawie testów.
