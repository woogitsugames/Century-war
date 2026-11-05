# Dzień 49 — przegląd kodu d48 (Raport v2, przypomnienie, LostBattle)

Zakres: `git diff 964b202d origin/integ/tura10 -- app/src/main` (10 plików, +357/−10) oraz
`docs/reports/d48-raport-v2.md`. Gałąź: `claude/dzien-49-przeglad` (z `integ/tura10`).

## Znalezisko 1 (naprawione): wyścig zapisu `playtest.json`

**Co:** `PlaytestLog.saveAsync()` bierze migawkę (`toJson()`, zsynchronizowane) i dopiero potem,
poza blokadą, wrzuca zapis do jednowątkowej kolejki. Do d47 zapis wołał w praktyce jeden wątek
(gra, albo UI w `onPause`, gdy pętla stoi). D48 dodał drugi: `crash-io`
(`MainActivity.countPlaytestCrashes()` → `countCrashes()` → `saveAsync()`), z priorytetem
`MIN_PRIORITY`, działający równolegle z pętlą gry.

**Scenariusz:** wersja testowa, 3. dzień testu, w dzienniku awarii jest nowy ANR.
1. `crash-io`: `countCrashes` zwraca `true`, `toJson()` = stan S1 (bez znacznika przypomnienia);
   wątek o najniższym priorytecie zostaje wywłaszczony przed `writer.execute`.
2. Wątek gry w menu: `reminderShown(REMINDER_DAY3)`, `saveAsync()` → migawka S2 (z ANR i znacznikiem)
   trafia do kolejki pierwsza.
3. `crash-io` wznawia się i kolejkuje S1. Plik kończy jako S1: znacznik przypomnienia utracony
   (baner z 3. dnia pokaże się ponownie po restarcie), a gdyby w międzyczasie zapisała się odpowiedź
   ankiety albo rozliczenie etapu — one też.

Danych nie psuje (zapis atomowy `SaveGuard.atomicWrite`), ale cofa stan pliku do starszej migawki.

**Poprawka (1 linia):** `@Synchronized` na `saveAsync()` — migawka i kolejkowanie pod tą samą
blokadą co mutacje, więc kolejność w kolejce = kolejność migawek. `execute` nie blokuje, więc
blokada jest krótka. Test: `meta/PlaytestLogSaveOrderTest` (2 wątki × 200 zapisów, 20 rund;
plik musi równać się `toJson()`, znacznik przypomnienia i 200 zdarzeń awarii po ponownym odczycie).
Test jest stresowy: bez poprawki wykrywa wyścig z dużym, nie stuprocentowym prawdopodobieństwem;
z poprawką jest deterministycznie zielony (ostatnia migawka w kolejce powstaje po ostatniej mutacji
obu wątków).

## Sprawdzone bez błędów

- **CrashStore / crash-io vs UI:** `fatalEvents()` jest `@Synchronized` jak reszta `CrashStore`,
  czyta tylko 1 KB nagłówka. `countPlaytestCrashes` startuje po `finishStartup` (gra i
  `playtestStarted` → `firstDay` już ustawione; start wątku daje happens-before). `countCrashes`
  zsynchronizowane. ANR rozpoznawany po `": ANR ("` — zgodne z `ExitReasons` (`Przyczyna: ANR (2)`).
  Awaria Javy nie jest liczona podwójnie: `REASON_CRASH` nie tworzy `exit-fatal-`.
- **Uszkodzone dane:** nowe pola opcjonalne i walidowane (kod regexem, liczniki przycięte,
  `reminders` maskowane, `dayBits` odtwarzane dla starego dziennika). Brak zmiany `VERSION`.
- **Przypomnienie w produkcji / nad bitwą:** warunek `g.testerSurveyEnabled`
  (`BuildConfig.TESTER_SURVEY`, w release tylko z `-PtesterSurvey=true`). `Game` aktualizuje tylko
  bieżący ekran (`screen?.update`), więc `MenuScreen.updateReminder` nie działa nad bitwą ani
  wynikami; dodatkowo blokady `resumeOffer`, `lostBattle`, ankiety, dymków i powiadomień.
- **Kod testera w profilu/chmurze:** kod żyje tylko w `playtest.json` i w tekście raportu.
  `backup_rules.xml` i `data_extraction_rules.xml` mają wyłącznie `include` dla `profile.json`
  i `history.json`, więc `playtest.json` nie trafia do kopii. Brak odwołań w `ProfileBackup`,
  `ScoreClient`, powtórkach.
- **LostBattle podwójna wypłata:** całe rozliczenie w `profile.settleBattle(battleId)` (raz na
  identyfikator); nowa gałąź `dailyDay in 0 until today` wyklucza się z gałęzią zwrotu próby
  (`dailyDay == today`). Medale dodawane raz, po wyborze gałęzi.
- **Alokacje w klatce menu:** `updateReminder` bez alokacji (porównania, `TesterSurvey.due`,
  `Missions.utcDay`); tekst banera (`L.f`) tylko w `layoutReminder`; `drawReminder` używa
  `ui.fit(String)`/`textO` bez alokacji. Poza wersją testową wychodzi na pierwszym warunku.

## Uwagi (bez zmian, niski priorytet)

- `ensureTesterCode(random = SecureRandom())`: domyślny argument tworzy `SecureRandom` przy każdym
  wywołaniu, także gdy kod już jest (start i każdy raport; nie w klatce). Kosmetyka.
- Kod wylosowany przy starcie zapisuje się dopiero przy najbliższym zapisie dziennika; gdy proces
  zginie przed nim, następny start wylosuje inny kod. Rzadkie (zapis w `onPause`).
- Znacznik przypomnienia zapisuje się w chwili pokazania; wyjście z menu przed dotknięciem
  „zużywa” próg — zgodnie z opisem d48.

## Testy

Obejście `tools/offline-build/` (`CW_OFFLINE_DIR=/tmp/claude-0/d49`, zależności podlinkowane z d48):
WYNIK_TESTOW
