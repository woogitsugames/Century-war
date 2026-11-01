# Podział testów (zlecenie 99)

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

**Punkt odniesienia przed zmianą:** około **60 minut** pełnego zestawu,
według treści zlecenia, bez bezpośredniego pomiaru tej gałęzi.
**Wynik po zmianie:** wymaga uruchomienia GitHub Actions. Nie należy traktować
celu 10 minut jako potwierdzonego wyniku. Aby porównać uczciwie,
mierz czas Gradle na tym samym runnerze/cachu i porównaj osobno
szybką ścieżkę oraz całkowity czas ukończenia trzech równoległych jobów.

## Izolacja testów

Nie połączono instancji `Game` pomiędzy testami: korzystają one z mutowalnego
stanu profilu, języka i bitwy. Bez benchmarku nie można potwierdzić, że współdzielenie
przyspieszy wykonanie bez wprowadzenia zależności między testami.
Reużycie procesu JVM oraz zasobów Robolectric zapewniają oryginalne zadania AGP.
