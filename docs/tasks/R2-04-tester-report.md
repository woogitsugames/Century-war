# R2-04: Lokalny dziennik gry i „Raport dla testera”

**Gałąź:** `claude/r2-04-tester-report` z `origin/integ/tura10`. **Teksty:** tylko nowa `part182()`
w `engine/L.kt` (10 języków). **Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`.
Źródło: `docs/research/R1-backlog.md`, TOP 2. Commity po polsku, wypychaj często.

## Cel

Tester jednym przyciskiem udostępnia (sam wybiera aplikację, np. e-mail) krótki raport: jak
daleko doszedł, ile grał, jakie tryby, jak działała gra na jego telefonie. Gra **niczego nie
wysyła sama**: bez sieci i bez nowego SDK, więc Data safety się nie zmienia. To ta sama zasada co
w obecnym „Zgłoś problem” (`O grze → Zgłoś problem`). Raport daje dane do wniosku o dostęp do
produkcji. Google może kazać testować dalej przy „insufficient tester engagement”
(`docs/RELEASE.md`, sekcja „Formalności Play 2026–2027”).

## Stan w kodzie (wykorzystaj, nie duplikuj)

- `meta/Profile.kt`: `battles`, `wins`, `playSeconds`, `tutorialDone`, `stars[]`/`totalStars()`,
  `unlockedStage()`, `bestWave`, `recordBattle(mode, won, seconds)` (ok. 308: `modeBattles[]`,
  `modeWins[]`, `modeSeconds[]`, `winStreak`, `bestWinStreak`), `missionState.loginStreak`,
  `campaignDifficulty`, ustawienia dostępności i jakości.
- `meta/BattleHistory.kt` (`history.json`, ostatnie bitwy: `mode`, `stage`, `result`, `seconds`,
  `era`, `aiPower`, `modifiers`, …).
- `crash/CrashReports.kt`: `snapshot(...)` (wersja, model, Android, ekran, zdarzenia),
  `sharingText(...)`; `crash/CrashReportText.kt` buduje tekst; `crash/CrashStore.kt` (`MAX_CHARS`).
- Udostępnianie: `MainActivity.shareProblemReport()` / `sendProblemReport()` (ok. 750–776:
  `AlertDialog`, potem `ACTION_SEND` z `createChooser` na osobnym wątku). Interfejs
  `engine/Game.kt` → `Host.shareProblemReport()` (ok. 41).
- Wydajność: `Game.frameMetrics` (`FrameMetrics.p95Ms()`, `totalDroppedSteps`),
  `governor.reduction`/`gpuReduction`, `quality.maxReductionSteps`, `effectiveQualityLevel()`.
- `AboutScreen.kt`: przyciski 1–5, `3 -> g.host.shareProblemReport()`.

## Zmiany

1. **Nowy `meta/PlaytestLog.kt`**: liczniki, których dziś nie ma, w osobnym pliku
   `files/playtest.json` (**nie** w `profile.json`, poza regułami kopii `res/xml/*_rules.xml`,
   poza `toCloudJson`):
   - `firstDay`, `lastDay` (UTC, `Missions.utcDay`), `daysPlayed` (liczba różnych dni),
     `sessions` (start gry lub powrót po > 30 min w tle),
   - `tutorialSeconds` (czas do `tutorialDone`), `firstWinSeconds`,
   - `maxLossStreak` w kampanii, `surrenders`,
   - wydajność na sesję: max `governor.reduction`, najgorsze `p95Ms` z okien po 30 s bitwy,
     `droppedSteps`; trzymać tylko 5 ostatnich sesji,
   - `screensVisited` jako bitmaska znanych ekranów (Kodeks, Badania, Misje, Powtórki,
     Dostępność, Potyczka, Endless, Dzień, Tydzień).
   Zapis zbiorczy: przy `onPause` razem z profilem (ten sam limit czasu w `MainActivity.onPause`)
   albo przez istniejący wątek zapisu. **Bez I/O w pętli gry** (StrictMode w debug to wyłapie).
   Uszkodzony plik oznacza start od zera, bez błędu i bez `SaveGuard.report`.
2. **Zbieranie:** `Game.setScreen`/`push` (bitmaska ekranów), `BattleScreen.settleRewards`
   (porażki i kapitulacje, czas do pierwszej wygranej; wewnątrz `settleBattle`, więc rozliczane raz),
   pętla gry co 30 s w bitwie (p95, redukcje; bez alokacji, tylko pola prymitywne).
3. **Tekst raportu**, nowy `crash/PlaytestReportText.kt`, wzorem `CrashReportText.build`: zwykły
   tekst, najpierw nagłówek z `CrashReports` (wersja, model, Android), potem sekcje „Postęp”,
   „Czas gry”, „Tryby”, „Wydajność”, „Ustawienia” (jakość, FPS, paleta, tekst, leworęczny,
   TalkBack). **Bez nicku, bez UUID i sekretu rankingu, bez nazwy konta Play Games.**
   Maks. 6 000 znaków. Tytuły sekcji i etykiety po angielsku i bez tłumaczeń, bo czyta je zespół.
   Opis w oknie dialogowym jest w języku gracza.
4. **UI:** `AboutScreen`: nowy przycisk 6 „Raport dla testera” obok „Zgłoś problem”.
   `Host.sharePlaytestReport()` + implementacja w `MainActivity` jak `sendProblemReport`
   (okno z opisem zawartości → `ACTION_SEND`). Opis TalkBack.
5. **Teksty `part182()`:** `pt182_button`, `pt182_desc` (co zawiera raport i że nic nie jest
   wysyłane automatycznie), `pt182_subject`, `pt182_share_title`.
6. **Dokumenty:** `docs/RELEASE.md`, sekcja „Testy zamknięte w Play Console”, punkt 6: poproś
   testerów o wysłanie raportu po 3. i po 10. dniu. Dopisz akapit, że raport jest lokalny.
   `docs/store/data_safety.md`: jedno zdanie w tabeli przepływów (lokalny, ręczne udostępnienie,
   jak raporty błędów).

## Testy do dopisania

- `meta/PlaytestLogTest`: dni i sesje (zmiana dnia UTC, powrót po 31 min = nowa sesja, po 5 min
  ta sama), uszkodzony plik oznacza zera, limit 5 sesji wydajności, odczyt/zapis w katalogu tymczasowym.
- `crash/PlaytestReportTextTest`: brak nicku (ustaw nietypowy nick i sprawdź, że go nie ma),
  brak `playerId`/`playerSecret`, długość ≤ 6 000, wszystkie sekcje obecne.
- Test kopii zapasowej: `res/xml/backup_rules.xml` i `data_extraction_rules.xml` nadal obejmują
  tylko `profile.json` i `history.json` (jeśli test już istnieje, rozszerz o `playtest.json` jako wykluczony).
- `GameAccessibility164Test` albo `FirstHourJourneyTest`: nowy przycisk w O grze ma opis TalkBack;
  Wstecz działa.
- `LowEndScreensPerformanceTest`/`AllocationTest`: pętla bitwy bez dodatkowych alokacji.

## Kryteria odbioru

- Po kilku bitwach przycisk otwiera systemowe okno udostępniania z czytelnym tekstem; anulowanie
  nic nie wysyła.
- Plik `playtest.json` nie trafia do kopii Androida ani do Saved Games.
- `data_safety.md` i polityka prywatności są dalej zgodne z kodem (raport lokalny, ręczny).
- Pełne testy zielone.

## Ryzyka

- Prywatność: każde nowe pole w raporcie sprawdzić pod kątem danych osobowych. Model telefonu
  jest już w raporcie błędu, więc to nic nowego.
- Zapis przy `onPause` ma limit 300 ms. Jeśli nie zdąży, liczniki z tej sesji przepadają. To
  dopuszczalne, ale nie wolno blokować UI.

## Czego nie ruszać

- `CrashReports` (format raportów błędów, pytanie po awarii), reguły kopii zapasowej, `ScoreClient`.
- Balans, `:sim`, PROTOCOL, `compactTop`.
- Nie dodawać żadnej biblioteki analitycznej ani wysyłki sieciowej.
