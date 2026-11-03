# R6-02: Poprawki z przeglądu R6 (6 problemów średnich)

**Gałąź:** `claude/r6-02-poprawki` z `origin/integ/tura10` (po scaleniu dnia 9).
**Teksty:** tylko nowa `part186()` w `engine/L.kt` (10 języków), potrzebna do M5.
**Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`. Podstawa:
`docs/research/R6-review.md` (opisy, scenariusze, wiersze według 921cd50). Commity po polsku,
osobny commit na każdy punkt, wypychaj często.

## Cel

Usunąć sześć problemów średnich z przeglądu kodu po D5/R2-03/R2-04/R2-05, zanim gra trafi
do testerów. Problemy niskie (R6-L1…L9) są opcjonalne: zrób L5, L7 i L8, jeśli są tanie, a
resztę zostaw.

## Zmiany

### M1: zabicia w tle nie zapychają dziennika awarii
- `crash/ExitReasons.kt`, `worthReporting()` (wiersz ok. 37–48):
  `REASON_LOW_MEMORY` i `REASON_EXCESSIVE_RESOURCE_USAGE` tylko przy
  `importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE` (jak `SIGNALED`).
- `crash/CrashStore.kt`: raporty zakończeń z osobnym prefiksem (`exit-…`, dopisać do `FILE_NAME`
  i `read()`), najwyżej **3** pliki `exit-` (rotacja w `write`). `newest()` dla
  „Zgłoś problem” preferuje `fatal-`/`caught-`, a `exit-` tylko gdy nie ma innych. ANR i błąd
  natywny dalej mają trafiać do pytania po awarii (`pendingFatal`): ustal, czy `exit-` ANR liczy
  się jako fatal (np. `exit-fatal-…`), i napisz to w KDoc.
- Testy: `crash/ExitReasonsTest` (LOW_MEMORY w tle pominięte, na pierwszym planie zapisane),
  `CrashReportsTest`/`CrashStoreTest` (5 zakończeń nie wypiera 2 awarii Javy; „Zgłoś problem”
  wybiera awarię Javy).

### M2: stare ANR po aktualizacji nie pytają o raport
- `crash/ExitReasons.kt`, `collect30()` (ok. 71–88): gdy w `prefs` **nie ma** klucza
  `LAST_EXIT_TS`, tylko zapisz najnowszy `timestamp` i zwróć 0 (pierwsze uruchomienie po
  aktualizacji albo instalacji). Dodatkowo pomijaj wpisy starsze niż
  `PackageManager.getPackageInfo(...).lastUpdateTime` (zabezpieczenie po wyczyszczeniu danych).
- Po zapisie raportu `file.setLastModified(info.timestamp)` (to też R6-L7), żeby kolejność
  `newest()` odpowiadała czasowi zdarzenia.
- Testy: brak klucza oznacza 0 raportów i ustawiony znacznik; drugi start zapisuje nowe ANR;
  wpis sprzed `lastUpdateTime` jest pominięty (Robolectric `ShadowActivityManager`,
  `ShadowPackageManager`).

### M3: termika nie spycha jakości do minimum
- `engine/QualityGovernor.kt`, `record()` (ok. 52–80):
  - osobny licznik `thermalSteps` (zerowany w `reset()` i przy powrocie jakości);
  - termika dodaje stopień tylko gdy `thermalSteps < THERMAL_MAX_STEPS` (= 2);
  - kolejny stopień termiczny dopiero po ≥ 15 s od poprzedniego (`thermalFor >= 15f` po
    pierwszym stopniu, 3 s dla pierwszego).
  - Stopnie termiczne nie mogą obniżać skali bufora. Jeśli zmiana `reduction` przy
    wyczerpanych efektach przechodzi na skalę (`Quality`), termika przy tej granicy nie
    dokłada stopnia.
- `docs/PERFORMANCE.md`: opis zasady (najwyżej 2 stopnie z termiki, co 15 s, bez skali bufora).
- Testy `QualityGovernorTest`: MODERATE przez 120 s przy zdrowym CPU daje dokładnie 2 stopnie;
  powrót po ochłodzeniu (≤ LIGHT, 10 s zapasu); istniejące przypadki CPU/GPU bez zmian.

### M4: TalkBack bez przebudowy drzewa co klatkę przy ograniczonym tempie
- `screens/BattleAccessibility.kt:113`: klucz `BattleScreen.SPEED -> s.speed`
  (bez `effectiveSpeed`). Napis z ograniczeniem („×5 → ×3”) zostaje w `state()` (wiersz 218,
  `b.label`) i odświeża się z limitem 1 s jak inne teksty.
- Test w `engine/BattleAccessibilityTest` (albo w teście D5): przy `speed = 5` i
  `effectiveSpeed` zmienianym co klatkę między 4,6 a 5,0 przez 600 klatek przyrost `revision`
  wynosi ≤ 2 na sekundę zegara testowego (`setAccessibilityClock`).

### M5: raport testera mówi wprost o ustawieniach dostępności
- `engine/L.kt`: nowa `part186()` z kluczem `pt186_desc` (10 języków; zastępuje użycie
  `pt182_desc` w `MainActivity.sharePlaytestReport`). Treść jak `pt182_desc` plus zdanie
  „Zawiera też ustawienia dostępności, np. czy działa czytnik ekranu.” Starego klucza nie
  usuwaj (inne miejsca, testy).
- `docs/store/privacy_policy_pl.md` i `_en.md` (jeśli opisują raport testera; jeśli nie, dopisz
  krótki akapit przy raporcie problemu): raport ręczny, zawiera ustawienia dostępności, gra nic
  nie wysyła sama.
- **Do decyzji koordynatora/właściciela:** zamiast opisu można usunąć wiersz „TalkBack” z
  raportu (`crash/PlaytestReportText.kt:177`) i zostawić tylko „tempo ×½”. Domyślnie: opis.
- Testy: `PlaytestReportTextTest` bez zmian; `LTest` dla `pt186_desc`.

### M6: muzyka gry wraca, gdy cudza muzyka ucichnie
- `engine/Music.kt` (`resume()` ok. 70–78, `sync()` ok. 190, `otherAppPlaying()` 171):
  - w `resume()` ustaw `yieldToOther` jak dziś, ale gdy `true`, sprawdzaj ponownie co 3 s
    (na istniejącym wątku sterującym muzyką albo `Handler` wątku UI; **nie** w pętli gry), dopóki
    gra jest na pierwszym planie;
  - gdy `isMusicActive` jest `false` przez 2 kolejne odczyty, `yieldToOther = false` i `sync()`;
  - pierwszy odczyt ok. 500 ms po `resume()` (wygaszanie innej aplikacji), a do tego czasu
    muzyka gry nie startuje. Odczyt w chwili, gdy gra już gra, nie może wyciszyć własnej
    muzyki: sprawdzaj tylko przy `!focusHeld`.
- Decyzja właściciela („nie przerywać muzyki gracza”) zostaje: gdy cudza muzyka gra, gra jej
  nie zatrzymuje.
- Testy: `MusicAudioFocusTest`/`MusicLifecycle160Test` (`ShadowAudioManager.setIsMusicActive`):
  inna muzyka przy wejściu oznacza ciszę bez fokusu; po zatrzymaniu innej muzyki gra wraca w ≤ 6 s;
  pauza i wznowienie nie zostawiają pracującego timera.

## Kryteria odbioru

- Pełne testy zielone:
  `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.
- Bez zmian `:sim`, PROTOCOL, formatów zapisu, `compactTop` i balansu.
- Krótki raport `docs/reports/r6-02-poprawki.md`: co zmieniono w każdym punkcie i które
  problemy niskie zrobiono.

## Ryzyka

- M1/M2: zmiana prefiksów plików w `crash/` nie może zgubić istniejących raportów (`read()`
  przyjmuje stare i nowe nazwy).
- M6: timer muzyki nie może przeżyć `pause()`/`release()` (wyciek wątku/Handlera).
- M3: nie zmieniaj progów CPU/GPU (4 s / 3 s / 10 s).

## Czego nie ruszać

- `GameAccessibility` poza kluczem tempa (D5 jest zatwierdzony), `PlaytestLog` (poza
  ewentualnym L8: `saveAsync()` po `battleSettled`), `SystemAccessibility`.
