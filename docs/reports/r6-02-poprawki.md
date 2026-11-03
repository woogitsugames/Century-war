# R6-02: poprawki z przeglądu R6

Gałąź `claude/dzien-10-poprawki` (z `origin/integ/tura10`). Podstawa: `docs/research/R6-review.md`,
zlecenie `docs/tasks/R6-02-fixes.md`.

## M1: zakończenia procesu w tle nie zapychają dziennika
- `ExitReasons.worthReporting`: `LOW_MEMORY` i `EXCESSIVE_RESOURCE_USAGE` tylko przy ważności
  procesu `<= IMPORTANCE_VISIBLE`.
- `CrashStore`: raporty zakończeń jako `exit-…` (zwykłe) i `exit-fatal-…` (ANR, błąd natywny),
  osobny limit 3 plików; awarie Javy (`fatal-`, `caught-`) nadal do 10. `read()` przyjmuje stare
  i nowe nazwy.
- `exit-fatal-` liczy się jak awaria: `newest(fatalOnly = true)` (pytanie po starcie) i
  „Zgłoś problem” go biorą. Zwykłe `exit-` trafia do „Zgłoś problem” tylko, gdy nie ma innych
  raportów.

## M2: stare ANR po aktualizacji nie pytają o raport
- Brak klucza `last_exit_ts` (instalacja, aktualizacja do tej wersji): tylko zapis najnowszego
  znacznika (lub 0), zero raportów.
- Wpisy starsze niż `PackageInfo.lastUpdateTime` są pomijane.

## M3: termika
- `QualityGovernor`: licznik `thermalSteps`, najwyżej 2 stopnie (`THERMAL_MAX_STEPS`), pierwszy
  po 3 s, drugi po 15 s (`THERMAL_NEXT_S`); stopień termiczny tylko gdy
  `reduction - gpuReduction < Quality.EFFECT_STEPS` (bez skali bufora). Zerowany w `reset()` i
  przy powrocie jakości. Progi CPU/GPU bez zmian. Opis w `docs/PERFORMANCE.md`.

## M4: TalkBack i ograniczone tempo
- `BattleAccessibility.key` dla `SPEED` = `s.speed`; napis „×5 → ×3” odświeża się przez stan
  z limitem 1 s.

## M5: okno raportu testera
- `part186()` z `pt186_desc` (10 języków, „TalkBack” bez tłumaczenia), użyte w
  `MainActivity.sharePlaytestReport`. `pt182_desc` zostaje. Wiersz TalkBack w raporcie zostaje.
- Polityka prywatności PL/EN: raport testera zawiera ustawienia dostępności (w tym TalkBack);
  limity raportów (10 + 3) i pomijanie zabić w tle.

## M6: muzyka wraca po cudzej muzyce
- `Music.resume()`: gdy gra ustępuje (`yieldToOther`), `Handler` wątku UI sprawdza
  `isMusicActive` po 500 ms, potem co 3 s; 2 kolejne odczyty ciszy przywracają muzykę gry.
  Odczyty tylko bez fokusu (własna muzyka nie wycisza gry); `pause()`/`release()` zdejmują timer.

## Problemy niskie
- **L7** zrobione (z M2): `lastModified` raportu = czas zdarzenia.
- **L8** zrobione: `PlaytestLog.saveAsync()` po `battleSettled`.
- **L5** pominięte: zmiana łamie założenia testu `playtestSamplingInBattleDoesNotAllocate`
  (próbkuje na pauzie) i nie jest tania.
