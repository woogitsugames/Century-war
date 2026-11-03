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


## Zlecenie 162: nowsza konfiguracja pamięci

Historyczne `forkEvery=0` z zadania 131 zostało zastąpione przez
`forkEvery=1` i `maxHeapSize=384m` na zadaniu testowym aplikacji (później kompromis
`forkEvery=12`, 768 MiB — sekcja „Kompromis forkEvery” niżej).
`PrereleasePolishTest` należy teraz do kategorii `screenshots`.
Dla 8 GiB stosuj `-PtestForks=1`, dla 16 GiB `-PtestForks=2`.
Pełne wyniki należy zmierzyć na obecnym SHA; nie zastępują
ich wcześniejsze przebiegi pojedynczych kategorii.
Zasady wydania na tagu i cache PNG: [CI.md](CI.md).

## Kompromis forkEvery (3.11.2026, decyzja właściciela)

> **Nieaktualne od nocy 9** — obecna konfiguracja i pomiary: sekcja „Noc 9” niżej.

`forkEvery=1` (każda klasa testowa Androida w nowym procesie JVM) był bezpieczny pamięciowo,
ale pełny zestaw trwał ok. **16 min**. Obecnie `app/build.gradle.kts` wymienia proces
testowy **co 12 klas** przy stercie **768 MiB** i dwóch równoległych procesach.

| Ustawienie | Pełne `:app:testDebugUnitTest :sim:test :server:test` | Szczyt zajętej pamięci kontenera | OOM (137) |
|---|---:|---:|---|
| `forkEvery=1`, 384 MiB | ok. 16 min (pomiar koordynatora) | — | nie |
| `forkEvery=6`, 640 MiB | 13 min 14 s (testy `:sim`/`:server` z cache) | ok. 10,3 GB | nie |
| `forkEvery=12`, 768 MiB | **13 min 0 s** z kompilacją od zera; **13 min 2 s** samo ponowne wykonanie testów (`--rerun`) | ok. 11,7–12,3 GB | nie |
| `forkEvery=20`, 768 MiB | 12 min 31 s (`--rerun`) | ok. 13,1 GB (zostaje 3 GB) | nie |

Pomiar: 4 CPU, 15 GB RAM, `free -m` co 10 s; zajętość obejmuje także demony Gradle/Kotlin.
Wybrano **12**: rzadsza wymiana procesu daje tylko ok. 30 s, a zużywa ok. 1 GB więcej pamięci.
Celu 11–12 min nie da się osiągnąć samym `forkEvery` — wąskim gardłem jest nierówny podział klas
między dwa procesy (Gradle przydziela klasy po kolei, bez równoważenia czasu).
Najdłuższe klasy (czas sumaryczny ok. 17 min dzielony na dwa procesy): `ScreenshotTest` ok. 5,6 min,
`PrereleasePolishTest` i `DoctrineBalance82Test` po ok. 2 min, `BattleScreenTest` ok. 1,4 min —
dalsze skracanie wymaga podzielenia tych klas (zwłaszcza `ScreenshotTest`), nie zmiany `forkEvery`.

Nadpisanie bez edycji pliku: `-PtestForkEvery=N` (np. `-PtestForkEvery=1` do izolacji
podejrzanego testu, `-PtestForks=1 -PtestForkEvery=4` na komputerze z 8 GiB).

## Noc 9: równy podział na procesy i okresowy GC (3.11.2026)

**Wynik:** pełne `:app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue`
na 4 CPU / 15 GB: **11 min 38 s → 5 min 54 s** (końcowy przebieg na commicie z tą sekcją,
626 + 277 + 76 testów, 0 błędów), szczyt zajętej pamięci kontenera **10,5 GB → 8,3 GB**
(w tym ok. 4 GB demonów Gradle/Kotlin), największy proces testowy **7,7 GB → 1,3 GB RSS**.
Żaden test nie został usunięty ani osłabiony; liczba metod rośnie (617 → 626), bo podzielone
pętle są osobnymi metodami.

### Przyczyny wolnego przebiegu

1. **Gradle nie równoważy czasu.** `maxParallelForks` rozdaje klasy procesom po kolei
   (round robin) w kolejności skanowania katalogów (pliki przed podkatalogami, alfabetycznie).
   Przed zmianą proces 0 dostał 663 s pracy (m.in. `ScreenshotTest` 230 s, `FirstHourJourneyTest`
   124 s, `PrereleasePolishTest` 79 s, `DoctrineBalance82Test` 77 s), a proces 1 — 186 s:
   od 193. sekundy pracował tylko jeden proces. Sprawdzone na XML (`timestamp`, `time`)
   symulacją przydziału — zero kolizji w obrębie procesu.
2. **Natywne bitmapy Robolectric zwalnia dopiero GC obiektu Javy.** Obiekty Javy są małe, sterta
   768 MiB prawie się nie zapełnia, więc GC prawie nie działa, a pamięć natywna rośnie
   (`PrereleasePolish*` na Foldzie: ok. 75 MB/s, proces do **7,7 GB RSS**). To był powód
   `forkEvery=12` i ryzyka OOM.

### Zmiany

- **Czwórki sąsiednich klas.** Ciężka praca jest w czterech klasach o sąsiednich nazwach;
  przy 1, 2 lub 4 procesach każdy dostaje równą część, niezależnie od reszty listy:
  - `ScreenshotTest` → `ScreenshotTestBattle`, `ScreenshotTestFold`, `ScreenshotTestLayout`,
    `ScreenshotTestMenus` (podział mechaniczny, wspólne narzędzia w abstrakcyjnej
    `ScreenshotTestBase`; metody, nazwy PNG i wzorce `src/test/resources/screens/` bez zmian;
    `regression100` jest w `ScreenshotTestMenus`, `updateScreenshots` filtruje
    `com.centurywar.screens.ScreenshotTestMenus.regression100`);
  - `FirstHourJourneyTest` → baza `FirstHourJourneyBase` i klasy `FirstHourJourneyTest`
    (telefon, pl + przerwany samouczek), `…OddTest` (telefon, en), `…FoldTest`, `…FoldOddTest`
    (Fold zewnętrzny); `CW_TEXT_LANGS` dzieli się na języki z parzystych i nieparzystych pozycji;
    dziennik `fh_audit_<urządzenie>_<języki>.txt` zamiast jednego `fh_audit.txt`;
  - `PrereleasePolishTest` → baza `PrereleasePolishBase`, po jednym urządzeniu na klasę
    (`PrereleasePolishTest` = 720p + dwa krótkie testy, `…Phone20x9Test`, `…FoldOuterTest`,
    `…FoldInnerTest`); raporty `pre10_audit_<urządzenie>.txt`; kategoria `*PrereleasePolish*`;
  - `DoctrineBalance82Test` → baza `DoctrineBalance82Base`, doktryny `index % 4`
    (`DoctrineBalance82Test`, `…Mod1Test`, `…Mod2Test`, `…Mod3Test`); te same seedy i progi,
    workflow `sim-balance-82.yml` filtruje `*DoctrineBalance82*`.
- **Okresowy GC** w procesach testowych aplikacji: `-XX:+UseG1GC -XX:G1PeriodicGCInterval=2000`
  (cykl G1, gdy przez 2 s nie było GC). `PrereleasePolishFoldTest` osobno: 3,0 GB → 0,77 GB RSS,
  czas bez zmian (79 s oba przebiegi).
- **`forkEvery=50`** (bezpiecznik; dziś ok. 35 klas na proces, więc bez wymiany) i
  **domyślnie 4 procesy** (`maxParallelForks`, nie więcej niż rdzeni; `-PtestForks=N`, 1–4).
  Na komputerze z 8 GiB: `-PtestForks=2`.

### Pomiary (4 CPU, 15 GB, `free -m` i RSS procesów „Gradle Test Executor” co 5 s)

| Przebieg | Procesy | forkEvery | GC | Czas ścienny | Szczyt zajętej pamięci | Największy proces |
|---|---:|---:|---|---:|---:|---:|
| przed zmianą (z kompilacją testów z cache) | 2 | 12 | — | **698 s** | 10,5 GB | — |
| pary klas | 2 | 12 | — | 500 s¹ | 11,9 GB | 7,7 GB |
| pary klas | 2 | 12 | okresowy | 480 s | 5,1 GB | 1,2 GB |
| pary klas | 2 | bez wymiany | okresowy | 427 s | 6,3 GB | 1,8 GB |
| pary klas | 4 | bez wymiany | okresowy | 363 s | 8,1 GB | 2,0 GB |
| czwórki klas | 3 | bez wymiany | okresowy | 373 s | 7,4 GB | 1,8 GB |
| czwórki klas | 4 | bez wymiany | okresowy | 335 s¹ | 8,0 GB | 1,4 GB |
| **końcowa konfiguracja** | 4 | 50 | okresowy | **354 s** | 8,3 GB | 1,3 GB |

¹ z kompilacją zmienionych testów (ok. 40–60 s). „Bez wymiany”: `-PtestForkEvery=1000`.
Zajętość obejmuje demony Gradle i Kotlin (ok. 2–3,4 GB przed startem testów).
Przy czterech procesach sumaryczny czas metod rośnie o ok. 30% (rywalizacja o 4 rdzenie),
ale czas ścienny i tak spada. Trzy procesy są gorsze od czterech: czwórki klas
nie dzielą się równo na trzy.

**Dysk:** każdy proces Robolectric rozpakowuje ok. 203 MB natywnego środowiska do
`/tmp/robolectric-nativeruntime*`, a jeden przebieg zapisuje w lokalnym build cache
ok. 840 MB (katalog `app/build/screenshots` jest wyjściem zadania). Przy prawie pełnym dysku
testy padają na `FileSystemException`/`UnsatisfiedLinkError` — to nie są błędy testów.

### Jak sprawdzić równowagę po dodaniu ciężkiej klasy

Kolejność przydziału to posortowane nazwy (pliki przed podkatalogami pakietu), a proces to
`indeks % liczba procesów`. Nowy test trwający ponad ok. 30 s warto od razu rozbić na cztery
sąsiednie klasy (wspólna abstrakcyjna baza, bez `@Test` w bazie), jak powyżej.
`python3 scripts/ci_test_timings.py app/build/test-results/testDebugUnitTest --top 20`
pokazuje najdłuższe klasy.

### Seedy w testach ścieżek

`FirstHourJourneyTest` uruchamia potyczkę i Endless przyciskiem z UI, a gra bierze wtedy
seed z `System.nanoTime()` (`ModeScreen`, `SkirmishSetupScreen`). We wszystkich pełnych
przebiegach tej nocy (2–4 procesy, poza jednym przerwanym przez pełny dysk) wszystkie klasy `FirstHourJourney*` przeszły, a asercje
ścieżki nie zależą od wyniku tych bitew (Endless kończy się poddaniem po 15 min). Podmiana
ekranu bitwy na inny seed po dotknięciu przycisku zmieniłaby przebieg (zapowiedź epoki,
zapis bitwy), więc seedy zostały bez zmian; niestabilności nie zaobserwowano.

