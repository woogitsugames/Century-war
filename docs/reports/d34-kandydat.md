# Dzień 34: gałąź-kandydat do scalenia (d29 + d28 + d31 + d32)

Gałąź `claude/dzien-34-kandydat`, baza `origin/integ/tura10`. Scalone po kolei (`git merge --no-edit`):

1. `origin/claude/dzien-29-zegar` — bez konfliktów,
2. `origin/claude/dzien-28-nowosc` — bez konfliktów,
3. `origin/claude/dzien-31-od-premiery` — konflikt w `engine/L.kt`,
4. `origin/claude/dzien-32-wydanie-testowe` — bez konfliktów (także `docs/RELEASE.md`).

Gradle tu nie działa (sieć blokuje dl.google.com), więc **nic nie było kompilowane**. Przegląd
zrobiłem czytając cały diff względem `origin/integ/tura10` (Kotlin, `buildSrc`, `app/build.gradle.kts`,
`android.yml`) i sprawdzając każdy użyty symbol grepem w repo.

## Konflikty i ich rozwiązanie

| Plik | Konflikt | Rozwiązanie |
|---|---|---|
| `app/src/main/java/com/centurywar/engine/L.kt` | d28 i d31 dopisały po `putAll(part200())` własne `putAll(...)` i nową część zaraz za blokiem `init` | Zostały obie: `putAll(part201())` i `putAll(part203())` w `init`, potem `part203()` i `part201()`, potem `part200()` bez zmian. Liczba `putAll(part` = liczba `private fun part` = **77**. Zbiory nazw są równe, nie ma zduplikowanych kluczy |
| `engine/Game.kt` | brak (d29 zmienił konstruktor i `clock`, d31 dodał `preReleaseNotesEnabled` w innym miejscu) | git scalił sam; wynik sprawdzony |
| `docs/RELEASE.md` | brak (zmieniała go tylko gałąź d32) | — |

## Sprawdzone ryzyka kompilacji (z raportów d28–d32 i z diffu)

Wszystkie wypadły **poprawnie**. Nie znalazłem błędu kompilacji, więc kod Kotlin jest bez zmian.

- **`Game(…, clock)`** (d29): parametr `internal var clock: () -> Long = { System.currentTimeMillis() }`
  z przecinkiem końcowym. Wartości domyślne publicznych `checkDailyLogin(nowMillis = clock())` i
  `settleLostBattle(today = Daily.dayNumber(clock()))` mogą odwoływać się do składowej `internal`,
  bo funkcje nie są `inline`. `Daily.dayNumber(nowMillis: Long)` istnieje.
  - Wywołania w `MainActivity`, `TestLoopActivity` i wszystkich testach mają 3 argumenty
    pozycyjne. Nazwany `clock = { now }` jest tylko w `WelcomeBack193Test` i zgadza się z nazwą
    parametru.
  - Gałęzie d28, d31 i d32 nie tworzą nowych obiektów `Game`. `StoreGraphicsTest` (d31) ma
    3 argumenty.
  - `g.clock = …` w `RecordsUxTest` i `RewardFeedback90Test` działa, bo testy z tego samego
    modułu widzą składowe `internal`.
- **`MissionsScreen.clock = { g.clock() }`**: `Screen.g` to `val` z konstruktora klasy bazowej.
  Jawny odbiorca `g.` nie myli się z własnym polem `clock` ekranu.
- **`SaveGuard.resetWarningForTests()`** jest publiczny (`com.centurywar.game`, moduł `sim`). Te
  same wywołania są już w innych testach `app`.
- **`StageNovelty`** (d28): wszystkie użyte symbole istnieją z właściwymi sygnaturami:
  - `Campaign.stages`, `Stage(index, nameKey, startEra, maxEra, aiPower, modifiers, weather = CLEAR)`,
    `Modifier.key`, `Weather.key`/`CLEAR`,
  - `Defs.eras[].units[].role/nameKey`, `Defs.eraKeys`, `Defs.ERA_COUNT`, `Role.HEAVY`,
  - `L.f(key, vararg Any)`, `L.lang: Int`, `L.setCode`, `L.CODES`,
  - `Campaign.config(stage, Bonuses(), seed)`, `Battle(cfg).commanderAvailable`.

  Widoczność jest spójna: `internal` obiekt, enum i klasa, a `private class Cached` tylko w
  prywatnym polu. `L.kt` importuje tylko `java.util.Locale`, więc zwykły JUnit bez Robolectric
  działa. Przeliczyłem kampanię skryptem: nowości mają etapy 3–8, 11, 15, 19, 23, 27 i 31,
  największa przerwa to 4 etapy, epoki 1..8 pojawiają się po kolei. To zgadza się z asercjami
  `StageNoveltyTest`.
- **`CampaignScreen`** (d28):
  - w zasięgu są `sin`, `Color`, `time`, `locked`, `r`, `CROWN_DX`/`CROWN_OUT_DP`,
    `ui.textScale`, `ui.textO`, `ui.measure(…, isBold)` i `drawHardLine(c, line, left, cy, size)`;
  - `sin(time * 3f + i)` to Float + Int, czyli Float;
  - nowe pola `noveltyInfoRect`, `noveltyShown` (`private set`) i `noveltyShownCache` są
    zadeklarowane jako składowe klasy.
- **`PreReleaseRules`, `AchievementsScreen`, `ModeScreen`** (d31):
  - istnieją `AchievementDef.metric/hidden/threshold`, `AchievementMetric.DAILY_EVENTS/WEEKLY_RULESETS`,
    `RowNote.ellipsize` (`internal` w `companion`, ten sam moduł), `Ui.textO(…, align)`,
    `Paint` (import w `ModeScreen`), `ModeScreen.day`/`panel` i `pw` w `layout()`;
  - wcześniej żaden z tych ekranów nie miał `companion object`, więc nowe
    `private companion object` nie są duplikatami;
  - w `Game.kt` jest już import `BuildConfig`;
  - `Weekly.ruleSet(week)`, `Weekly.weekNumber`, `Daily.config(day, bonuses).dailyEvent` i
    `Weekly.firstRuleSetWeek` istnieją; cztery oznaczone osiągnięcia nie są ukryte.
- **`ReleaseChecklist.ciSkipProblem`** (d32): przyjmuje `parseSkips(): Set<String>` i
  `validReleaseSigning: Boolean` (zdefiniowane przed użyciem, linia 85). `GradleException` jest
  domyślnie importowany w skryptach `.kts`. Test korzysta z `allowedSkips: Set<String>` i ma
  potrzebne importy `assert*`. Reguła nie dotyczy `release-check.yml`, bo ten działa przy
  `pull_request` lub ręcznie, z podpisem debug, więc `uploadSigned = false`. Zadanie `release`
  (tag) nie używa `-PskipCheck`.

## Znalezione błędy i poprawki

**Błędów kompilacji ani niezgodności sygnatur między gałęziami nie znalazłem.** Kod pozostaje
w wersji ze scalenia. Jedyna poprawka to rozwiązanie konfliktu w `L.kt`.

Jest za to jedna pewna **niezgodność testów**, której nie da się naprawić bez uruchomienia testów
(opis poniżej, punkt 1).

## Pozostałe ryzyka — do sprawdzenia przy pierwszej kompilacji

1. **Wzorce regresji ekranów (`ScreenshotTestMenus.regression100`) — prawie na pewno nie przejdą
   po d28.**
   - Wzorce `phone720_reg100_campaign`, `phone720_reg100_campaign_info`, `foldOpen_reg100_campaign`
     i `foldOpen_reg100_campaign_info` są zrobione bez iskry nowości.
   - Test ma `stars[0] = 3` i `stars[1] = 2`, więc etapy 3–8 są nowe i niewygrane: na mapie
     pojawia się złota iskra.
   - Panel `campaign_info` pokazuje etap 3, który ma nowość. Gwiazdki mają teraz 26 dp zamiast
     30 dp, a w wierszu jest napis „Nowość: …”.
   - Raport d28 tego nie wymienia. Po obejrzeniu zrzutów trzeba uruchomić
     `./gradlew :app:updateScreenshots -PscreenReason='R11-06 nowość na etapie'`, dopisać wpis
     do `app/src/test/resources/screens/UPDATES.md` i zatwierdzić PNG.
   - Wzorzec `achievements` nie powinien się zmienić, bo oznaczone osiągnięcia mają indeksy
     59–63 i przy `scroll = 0` są poza kadrem. Wzorców wyzwania dnia nie ma.
2. **Dopiski d31 w testach zrzutów i audytach.**
   - W debug `TESTER_SURVEY = true`, a data przebiegu jest przed `RELEASE_DAY`, więc
     `PrereleasePolishBase` (4 urządzenia) pokazuje linię pod „Graj” w wyzwaniu dnia i tygodnia.
     Dla `ModeScreen` działa twarda reguła braku nakładania tekstów (`require10`).
   - Linia stoi w pustym pasie 24 dp, ale na świeżym profilu wyświetla się podpowiedź
     `HintOverlay.DAILY_MENU` zaczepiona o `start.r`. Trzeba sprawdzić, czy jej tekst nie
     nachodzi na linię.
   - Jeśli nachodzi, w tych testach trzeba ustawić `g.preReleaseNotesEnabled = false` (jak w
     `StoreGraphicsTest`).
   - Przy skali tekstu 130% zostają ok. 2 dp zapasu do przycisku.
3. **Panel etapu na 720p, tryb Trudny.**
   - `drawStarRow` odejmuje miejsce na nowość, nawet gdy na końcu jej nie pokaże. Dodatkowo
     `rowTextWidth` liczy pełną szerokość nowości, więc przy długiej nowości napis korony
     prawie zawsze spada do 11 sp.
   - Asercje `HardCampaign189ScreenshotTest.checkPanel` powinny przejść (≥ 11 sp, w panelu,
     nad „Trudność”), ale nikt tego nie widział (de, tr).
4. **Dwa zegary po d29 + d31.**
   - `AchievementsScreen.today` i `ModeScreen.day` biorą `Daily.dayNumber()` (czas
     systemowy), a nie `g.clock()`.
   - Na wynik testów to nie wpływa, bo zegar gry w testach i tak jest systemowy, z wyjątkiem
     `WelcomeBack193Test`, który nie otwiera tych ekranów.
   - Jeśli zegar ma być wszędzie jeden, to drobna zmiana na później.
5. **Konfiguracja Gradle (d32):**
   - uruchomić `./gradlew -p buildSrc test` i `./gradlew :app:help`;
   - sprawdzić, że krok „Potwierdź blokadę release bez klucza upload” w `release-check.yml`
     nadal kończy się błędem podpisu;
   - zadanie `test-release` w `android.yml` uruchomić dopiero po wgraniu sekretów.
6. **`WelcomeBack193Test`** ma stałą datę 4.11.2026 (dzień 20761, przed premierą). Gdyby kiedyś
   testowano powitanie z zasadami po premierze, datę trzeba będzie przesunąć.

## Weryfikacja w tym środowisku

- `python3 -m pytest tests/ -q`: pytest nie jest zainstalowany. Zamiast tego
  `python3 -m unittest discover -s tests -p 'test_*.py'`: **55 testów OK** (w tym
  `test_ci_release_gates.py` z d32).
- Liczba części `L.kt`: 77 `putAll(part` = 77 `private fun part`, zbiory nazw równe, bez
  zduplikowanych kluczy.
