# D43 — gałąź integracyjna i pierwsza kompilacja `:app` poza Gradle

Gałąź `claude/dzien-43-integracja`, baza `origin/claude/dzien-34-kandydat`.
Gradle, AGP i Android SDK nadal nie działają, bo sieć blokuje dl.google.com. Proxy nie było obchodzone.
Kod `:app` z kilku gałęzi nigdy nie był kompilowany. Tutaj pierwszy raz skompilowałem całość
(`sim` + `app/src/main` + `app/src/test`) i uruchomiłem testy Robolectric.
Wszystkie narzędzia są w `/tmp/claude-0/d43/`. W repo ich nie ma.

## 1. Scalenia

Gałęzie scalałem po kolei przez `git merge --no-edit`:

| # | Gałąź | Wynik |
|---|---|---|
| 1 | `origin/integ/tura10` | bez konfliktów (tylko `COORDINATOR_HANDOVER.md`) |
| 2 | `origin/claude/dzien-35-etap-19` | bez konfliktów |
| 3 | `origin/claude/dzien-41-drobne` (zawiera 36 i 39) | bez konfliktów |
| 4 | `origin/claude/dzien-42-tlumaczenia` | bez konfliktów |
| 5 | `origin/claude/dzien-38-serwer` | bez konfliktów |
| 6 | `origin/claude/dzien-37-balans-19-36` | bez konfliktów (`BALANCE.md` scalił się automatycznie) |
| 7 | `origin/claude/dzien-45-lejek` (d44 playtest_merge, R13, d45) | bez konfliktów |
| 8 | `origin/integ/tura10` (nowy commit) | bez konfliktów |

`dzien-40` nie jest scalony, bo czeka na decyzję właściciela.

Kontrole po scaleniu:
- **`L.kt`:** 77 wywołań `putAll(partN())` i 77 funkcji `private fun partN`. Oba zbiory są takie same
  i nie ma duplikatów.
- **`SimVersion.PROTOCOL`:** `"0.8.9"`.

## 2. Kompilacja `:app` poza Gradle

**Wynik: 0 błędów kompilacji** w `sim/src/main`, `app/src/main` i `app/src/test` (stan po scaleniu 8).
Na gałęzi nie trzeba było niczego poprawiać, więc nie ma commitów z poprawkami.

Środowisko:
- **Kompilator:** Kotlin **2.0.21** z `/opt/gradle-8.14.3/lib`. W tym katalogu nie ma wersji 2.2.20,
  której używa projekt. Kod nie korzysta z funkcji języka nowszych niż 2.0.
- **Android:** `org.robolectric:android-all:16-robolectric-13921718` (API 36, jak `compileSdk`),
  z Maven Central przez mirror, z weryfikacją sha1. Jar `android-all-instrumented` nie nadaje się do
  kompilacji: klasy dziedziczące z `Activity` muszą wtedy implementować `$$robo$getData`.
- **Play Games:** 14 zaślepek w Javie, tylko dla symboli używanych w `PlayGamesLeaderboards.kt` i
  `CenturyWarApp.kt`. Obejmują `PlayGames`, `PlayGamesSdk`, `*Client`, `Task`/`OnCompleteListener`,
  `Snapshot*` i `LeaderboardVariant`.
- **`R`:** prawdziwe `R.java` z **aapt2** (pakiet Ubuntu `aapt`, wersja 2.19-debian). Pakiet pobrałem
  przez `apt-get download` z archive.ubuntu.com i rozpakowałem w `/tmp`, bez instalacji w systemie.
  - Zasoby: `app/src/main/res` oraz `games_ids.xml` w wariancie debug. Plik jest wygenerowany jak
    w `PlayGamesIdsGenerator` przy `REPLACE_ME`, czyli `play_games_configured=false`.
  - aapt2 2.19 nie czyta `resources.arsc` z API 35/36, więc zasoby linkowałem z `android-all` API 34.
    Publiczne ID atrybutów się nie zmieniają.
- **`BuildConfig`:** ręcznie, z polami jak w debug: `CW_SERVER_URL`, `CW_HMAC_KEY`, `CW_PRIVACY_URL`
  i `PRIVACY_URL` puste, `TESTER_SURVEY=true`, `VERSION_NAME=0.1.0-dev`, `VERSION_CODE=1`.
- **Manifest:** odtworzyłem scalanie AGP. Dodałem `package`, podstawiłem `${applicationId}` i usunąłem
  `<provider tools:node="remove">`.
- **Testy:** junit 4.13.2 i robolectric 4.16 z Maven Central.
  - `androidx.test:monitor` jest tylko na Google Maven. Zastąpiłem go 15 minimalnymi klasami, których
    Robolectric używa w `AndroidTestEnvironment` i `RoboMonitoringInstrumentation`.
  - Testy JVM bez Robolectric dostają odpowiednik „mockable android.jar” z AGP
    (`unitTests.isReturnDefaultValues = true`). Wygenerowałem go przez ASM z `android-all` 36.
  - JVM 21 uruchamiam z `--add-opens java.base/java.io` i `java.base/java.lang`.

## 3. Uruchomione testy

### Testy wskazane w zleceniu: 29 testów, 28 OK, 1 błąd

| Klasa | Wynik |
|---|---|
| `StageNoveltyTest` | OK |
| `PreReleaseRulesTest` | OK |
| `WelcomeBack193Test` | OK (6) |
| `PlaytestClose39Test` | OK |
| `GameClockScreens41Test` | OK |
| `CodexFoldOuterLabelsTest` | **5/6**, błąd `recordsTabsReadableOnFoldOuter` (opis niżej) |

### Pełny zestaw „fastTest”: 750 testów, 749 OK, 1 błąd

Zestaw obejmuje 136 klas z `app/src/test`. Wykluczenia są takie jak w
`gradle/test-categories.properties` (screenshots, balance). Pominąłem w całości 4 klasy, które mają
wykluczenia na poziomie metod: `AiPersonalityProfileTest`, `EndlessBossTest`,
`Doctrine69BattleSimTest` i `BattleSimTest`. Testy szły w paczkach po 25 klas na jedną JVM.
Jedyny błąd to ten sam co wyżej.

Nowe testy z d44/d45 przechodzą: `PlaytestLogTest` i `PlaytestReportTextTest`.

### Pozostałe moduły

- **`:sim`:** 318 testów OK (skrypt jak w d35/d41).
- **`tests/test_playtest_merge.py`:** 14 testów OK (`unittest`).

### Błąd `CodexFoldOuterLabelsTest.recordsTabsReadableOnFoldOuter`

```
tr 130% 'İstatistikler' 9.8 sp
```

Na zewnętrznym Foldzie, po turecku i przy tekście 130%, zakładka „İstatistikler” w siatce 2 × 2
(`RecordsTabs`, przycisk rysowany w 12 sp i zmniejszany do szerokości) ma 9,8 sp. Wymagane jest ≥ 10.

To nie jest skutek integracji. Ten sam błąd jest na samej `origin/claude/dzien-41-drobne`: zbudowałem
jej drzewo osobno (`git archive`) i uruchomiłem test. Test powstał w d36/d41 i nigdy wcześniej nie
był uruchamiany. Nie poprawiałem go, bo to decyzja o układzie, nie błąd kompilacji. Możliwe kierunki:
- pełna szerokość wiersza dla zakładek w siatce przy dłuższych nazwach,
- mniejsze marginesy w `Ui.button` w trybie siatki,
- krótsza nazwa w TR, np. „İstatistik”.

### Błędy, które wynikały tylko ze środowiska

Usunąłem je ustawieniami środowiska, bez zmian w repo:
- `ReplayShareProviderTest.onlyReadOnlyReplayFilesAreExposed` i `FoldZoomTest.beforeAfterShots` rzucały
  `InaccessibleObjectException` (`FileDescriptor.fd`). Pomogło `--add-opens java.base/java.io`.
- `RecordedMusicTest` (2 testy) wpadał na `UnsatisfiedLinkError` w `Process.setThreadPriority` przy
  prawdziwym `android-all` na ścieżce klas. Pomógł mockable jar (jak w AGP).

## 4. Czego nie sprawdziłem

- **Gradle/AGP:** konfiguracja `build.gradle.kts` i `buildSrc`, manifest merger, R8/ProGuard,
  `releaseChecklist`, zasoby generowane przez `GeneratePlayGamesIdsTask` (odtworzone ręcznie),
  lint (m.in. NewApi), budowa APK/AAB.
- **API ukryte:** kompilacja szła na implementacyjnym `android-all`, który zawiera API `@hide`.
  Użycie ukrytego API nie dałoby więc błędu, a z `android.jar` z SDK by go dało.
- **Play Games:** zaślepki dają zgodność tylko tam, gdzie dobrze odwzorowują sygnatury (typy
  zwracane, nullowalność). Prawdziwa biblioteka `play-services-games-v2:22.1.0` może się różnić
  w szczegółach.
- **Kotlin 2.2.20:** różnice wobec 2.0.21, np. nowe ostrzeżenia traktowane jako błędy.
- **Testy zrzutów ekranu** (`screenshots`, około 30 wzorców PNG) i długie testy balansu: zajęłyby kilka
  godzin. Renderowanie fontów poza Gradle też może się różnić.
- **Moduł `:server`** (zmiany z d38): poza tą kompilacją.

## 5. Odtworzenie

Skrypty są w `/tmp/claude-0/d43/tools/` i giną po restarcie kontenera:
- `build_app.sh <repo>` — kompilacja,
- `run_tests.sh <repo> <klasy…>` — wybrane testy,
- `run_fast.sh <repo>` — cały zestaw „fastTest”,
- `build_res.py`, `mkr.py`, `mkstubs.py`, `mkmonitor.py`, `MockableJar.java`, `get_aapt.sh` — kroki pomocnicze.
