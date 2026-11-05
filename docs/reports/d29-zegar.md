# Dzień 29: deterministyczny zegar powitania po przerwie

Gałąź `claude/dzien-29-zegar` (baza `origin/integ/tura10`). Środowisko bez sieci do
dl.google.com: **nic nie było kompilowane ani uruchamiane**. Koordynator kompiluje i testuje.

## Przyczyna niestabilności `WelcomeBack193Test`

1. **Dwa zegary w jednym przebiegu menu.** `Game.clock` (powitanie) test ustawiał dopiero
   po konstruktorze `Game`, a reszta ścieżki menu brała czas systemowy:
   - `Game.init` → `checkDailyLogin()` (`System.currentTimeMillis()`): seria logowań, misje,
     `missionState.lastSeenDay`;
   - `MenuScreen.update` → `g.settleLostBattle()` z domyślnym `Daily.dayNumber()` (prawdziwy
     dzień, a test buduje `BattleMeta` z `now - 6 * day`);
   - `MenuScreen.refreshMissionBadge`, `refreshGoal` (`NextGoals.pick`) i podpis „wyzwanie
     wolne” (`Daily.dayNumber()`).
   Do tego test brał `now = System.currentTimeMillis()`, więc wynik zależał też od daty
   przebiegu (zasady dnia, cel, misje). Przejście przez północ UTC między wyliczeniem
   `today` w teście a którymkolwiek z tych odczytów dawało dwa różne dni.
2. **Globalne ostrzeżenie zapisu (najbardziej prawdopodobna przyczyna „tylko w pełnym
   przebiegu”).** `MenuScreen.update` zaczyna od
   `if (SaveGuard.takeWarning()) g.showNotice(L["save154_problem"], …)`. `SaveGuard` to
   `object` w module `sim` ze stanem na proces. Testy działają w 4 forkach z
   `forkEvery = 50`, więc to, która klasa testów wcześniej w tym samym JVM zgłosiła
   `SaveGuard.report(...)` (np. odczyt uszkodzonego profilu/historii/powtórki bez
   `resetWarningForTests()`), zmienia się między przebiegami. Pierwszy test klasy, który
   wchodzi do menu, dostaje wtedy dodatkowy baner, a
   `noWelcomeAfterTwoDaysOrBeforeTutorial` sprawdza `noticeTexts().isEmpty()`.

## Zmiany

Kod gry (zachowanie bez zmian: w grze zegar to zawsze `System.currentTimeMillis()`):

- `engine/Game.kt`: `clock: () -> Long` jest teraz 4. parametrem konstruktora
  (`internal var`, domyślnie `{ System.currentTimeMillis() }`), więc `init` też go używa.
  Usunięta stara deklaracja pola. `checkDailyLogin(nowMillis = clock())`,
  `settleLostBattle(today = Daily.dayNumber(clock()))`, `checkWelcomeBack` bez zmian.
  Wywołania `Game(host, profile, quality)` (MainActivity, TestLoopActivity, ~75 testów)
  kompilują się bez zmian dzięki wartości domyślnej.
- `screens/MenuScreen.kt`: `refreshMissionBadge`, `refreshGoal` i `dailyFree` biorą
  `g.clock()` zamiast czasu systemowego. Ankieta testerów (`PlaytestLog`) zostaje na
  czasie systemowym.
- `screens/MissionsScreen.kt`: `clock` domyślnie `{ g.clock() }` (dotąd czas systemowy).
  `StoreGraphicsTest` dalej ustawia `screen.clock` sam.

Testy:

- `WelcomeBack193Test`: stałe `now = 1_793_793_600_000L` (4.11.2026 12:00 UTC, dzień
  20761), zegar podany w konstruktorze `Game(host, profile, Quality.LOW, clock = { now })`;
  `SaveGuard.resetWarningForTests()` w `@Before` i `@After`. Zasady dnia mają zawsze ≥ 2
  modyfikatory (`Daily.generatedConfig`), więc baner „Dziś w wyzwaniu dnia” jest dla
  każdego dnia.
- `RewardFeedback90Test` (oba testy misji) i `RecordsUxTest.menuShowsUnclaimedMissionCountAndRecordsEntry`:
  `g.clock = { now }` z tym samym `now`, którym test ustawia stan misji (tanie poprawki
  wyścigu z północą UTC).

## Inne testy z prawdziwą datą (bez zmian)

| Test | Użycie | Ryzyko |
| --- | --- | --- |
| `screens/LostBattle188Test` (`rankedDailyAttemptComesBackOnce`, `corruptSnapshotGivesNothing`) | kilka razy `Daily.dayNumber()`; `BattleScreen` sam bierze `Daily.dayNumber()` i `System.currentTimeMillis()` (`LostBattle.meta`) | wyścig z północą UTC; naprawa wymaga zegara w `BattleScreen`, nie jest tania |
| `meta/LostBattleTest` (`weeklySiegeLossHasNoTierBonus`, `dailyRankedGivesNoMedalsButUnrankedDoes`) | konfiguracja dnia/tygodnia z prawdziwej daty | asercje ogólne; wynik zależy od daty, ale nie od północy |
| `screens/BattleScreenTest:350`, `BattleSummaryScreenshotTest:139` | `Weekly.weekNumber(Daily.dayNumber())` | tydzień z daty przebiegu; zmienne zrzuty |
| `screens/ScreenshotTestMenus` (105, 224, 235–238) | misje i tablica tygodnia z prawdziwej daty | zrzuty zależne od daty |
| `screens/ScreenshotTestLayout` (241, 285, 567–570) | dwa odczyty `utcDay` / misje | zrzuty; wyścig z północą tylko kosmetyczny |
| `screens/HardCampaign189ScreenshotTest` (39, 176) | `utcWeek` i `NextGoals.pick` z czasem systemowym; menu teraz z `g.clock()` (też systemowy) | cel z daty przebiegu; przy północy niedzielnej UTC możliwa rozbieżność tygodnia |
| `screens/Content176ScreenshotTest:77`, `Localization120ScreenshotTest:149`, `ScreenshotTestFold:466`, `engine/LTest:641` | misje z czasem systemowym | zrzuty / teksty; niskie ryzyko |
| `engine/RecordedMusicTest` | terminy oczekiwania | nie dotyczy dat |

Dalszy krok (osobno): zegar w `BattleScreen` (`Daily.dayNumber()`, `LostBattle.meta`,
`finishedAt`) brany z `g.clock()`, wtedy `LostBattle188Test` można przypiąć do stałej daty.

## Ryzyka kompilacji

- Parametr konstruktora `internal var clock` w publicznej klasie i wartości domyślne
  publicznych metod (`checkDailyLogin`, `settleLostBattle`) odwołujące się do niej:
  dozwolone (ograniczenie dotyczy tylko publicznych funkcji `inline`).
- Przecinek końcowy po ostatnim parametrze konstruktora: wymaga Kotlin ≥ 1.4.
- Nazwany argument `clock = { now }` w teście: zgodny z nazwą parametru.
- `MissionsScreen`: lambda `{ g.clock() }` w inicjalizatorze właściwości odwołuje się do
  `Screen.g`, który jest już ustawiony (konstruktor klasy bazowej).
- `SaveGuard` (`com.centurywar.game`, moduł `sim`) jest już importowany w innych testach
  `app` tą samą ścieżką.
