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

Na zewnętrznym Foldzie, po turecku i przy tekście 130%, zakładka „İstatistikler” ma 9,8 sp.
Wymagane jest ≥ 10.

To nie jest skutek integracji. Ten sam błąd jest na samej `origin/claude/dzien-41-drobne`: zbudowałem
jej drzewo osobno (`git archive`) i uruchomiłem test. Test powstał w d36/d41 i nigdy wcześniej nie
był uruchamiany.

**Poprawione w drugim etapie (pkt 6.1).** Mój pierwotny opis „w siatce 2 × 2” był błędny: zakładki
stały w jednym rzędzie.

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
- **Testy zrzutów ekranu:** uruchomione w drugim etapie (pkt 6.3).
- **Długie testy balansu:** nieuruchomione.
- **Moduł `:server`** (zmiany z d38): poza tą kompilacją.

## 5. Odtworzenie

Skrypty są w repo, w `tools/offline-build/` (pkt 6.2). Ich README opisuje zależności i użycie.

## 6. Drugi etap

### 6.1. `recordsTabsReadableOnFoldOuter`: poprawione

**Przyczyna.** Przy tureckim i tekście 130% zakładki Rekordów nie przechodziły w siatkę 2 × 2, tylko
stały w jednym rzędzie z ikoną i podpisem pod nią:
- Warunek siatki w `RecordsTabs.layout()` sprawdzał, czy podpis mieści się w 10 sp.
  „İstatistikler” mieścił się na styk: 65,3 dp przy dostępnych 66,5 dp.
- `Ui.fit` startuje jednak od 12 × 0,86 = 10,32 sp. Szerokość tekstu nie skaluje się liniowo
  (hinting), więc wynik nie mieścił się, a pętla zmniejszała rozmiar krokami po 4 %.
- Skutek: 9,79 sp.

**Poprawka** (`RecordsTabs.kt`, jedna linia warunku). Siatka wchodzi, gdy podpis nie mieści się
z zapasem jednego kroku, czyli w `10 / 0,96` sp. Dotyczy to tylko wąskiego pionowego ekranu
(`narrow`, czyli zewnętrznego Folda). Telefony i rozłożony Fold są bez zmian, a próg testu też.

**Wynik.**
- `CodexFoldOuterLabelsTest`: 6/6.
- Cały „fastTest”: **750/750**.
- Zrzut `d43_foldOuter_tr130_rekordy.png` pokazuje siatkę 2 × 2 z pełnymi nazwami.

Pierwsza próba, czyli węższe marginesy siatki, niczego nie zmieniła, bo siatka w ogóle nie
była używana. Wycofałem ją przed commitem.

### 6.2. `tools/offline-build/`

Skrypty są przeniesione do repo, bez jarów i binarek:
- `setup.sh`, `build_app.sh`, `run_tests.sh`, `run_fast.sh`, `run_shots.sh`, `build_sim.sh`,
- `build_res.py`, `mkr.py`, `mkstubs.py`, `mkmonitor.py`, `MockableJar.java`,
- `pom.xml`, `settings.xml`,
- `README.md` po polsku: obejście na czas blokady, co i skąd pobiera, czym różni się od AGP.

Katalog roboczy ustawia `$CW_OFFLINE_DIR` (domyślnie `/tmp/cw-offline`). Sprawdziłem, że kopie z
repo kompilują projekt i uruchamiają testy.

### 6.3. Testy zrzutów ekranu

Testy działają z natywną grafiką Robolectric (`nativeruntime` 4.16 z Maven Central). Uruchomiłem 36
klas z kategorii `screenshots`, każdą w osobnej JVM: **34 klasy OK**, 2 z błędami.

**`ScreenshotTestMenus.regression100`.** Test zatrzymuje się na pierwszej różnicy. Uruchomiłem go
więc dodatkowo z lokalną, niezatwierdzoną zmianą, która zbiera wszystkie różnice. Wynik: 26 z 30
wzorców się zgadza, 4 się różnią:

| Wzorzec | Zmienione piksele | Przyczyna (na podstawie pliku diff) |
|---|---|---|
| `phone720_reg100_campaign` | 0,128 % (limit 0,1 %) | nowe iskry „Nowość” przy nierozegranych etapach (R11-06) |
| `foldOpen_reg100_campaign` | 0,125 % | to samo |
| `phone720_reg100_campaign_info` | 0,681 % | linia „✦ Nowość: Epoka brązu (Rydwan), Mgła wojny” obok gwiazdek w panelu etapu 3 |
| `foldOpen_reg100_campaign_info` | 0,684 % | to samo |

Wszystkie 4 różnice to zamierzone zmiany z R11-06, a wzorce są starsze. Wzorców nie aktualizowałem.
Pliki diff są w `/tmp/claude-0/d43/shots/diffs/`.

**`LowEndScreensPerformanceTest.menuLow` i `menuHigh`.** Menu alokuje 30,4 i 30,5 KB na 600 klatek
przy limicie 16 KB. Ten sam wynik jest na bazie `dzien-34-kandydat`, więc to nie skutek integracji.
Może to zależeć od środowiska (JDK 21, Robolectric poza Gradle) albo od daty, bo menu ma zegar.
Nie sprawdziłem tego dalej. Pozostałe ekrany w tym teście mieszczą się w limicie.

**Uwaga z przeglądu zrzutów: wyzwanie dnia.** Linia „Wersja testowa: pełne zasady wyzwań od
premiery” leży na złotej ramce dolnej krawędzi panelu, więc wygląda jak przekreślona:
- `ModeScreen`: linia bazowa jest na `panel.bottom − 8 dp`, czyli na wysokości wewnętrznej ramki,
- widać to na phone720, foldOpen i foldOuter.

Nie poprawiałem tego, bo to poza zakresem zlecenia.

**Zrzuty** (2427 PNG) są w `/tmp/claude-0/d43/shots/`. Obejmują testy z kategorii oraz zrzuty
z lokalnego testu `D43ShotsTest` (spoza repo), zrobione dla scen, o które prosił koordynator.
