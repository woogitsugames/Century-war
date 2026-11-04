# D39 — audyt kodu zmian z 3–4.11.2026

Zakres: `git log --since=2026-11-03 origin/integ/tura10` (104 commity) oraz
`git diff 5fc6f46a origin/integ/tura10 -- app/src/main sim/src/main` (83 pliki, +7549/−346).
Szukane były tylko prawdziwe błędy. Każde podejrzenie sprawdzono, czytając kod: jest
scenariusz „wejście → zły wynik” albo podejrzenie zostało odrzucone. Wyniki bitew i `:sim`
bez zmian.

Gradle nie działa (sieć blokuje dl.google.com), więc testów aplikacji (Robolectric) nie
uruchomiono. Sygnatury sprawdzone grepem. `:sim` nie zmieniał się, dlatego jego testów nie
uruchamiano ponownie.

## Potwierdzone i naprawione

### 1. Wyciek wątku dziennika testu (`PlaytestLog`) przy każdym ponownym otwarciu gry

- `app/src/main/java/com/centurywar/MainActivity.kt:556` (poprawka), `meta/PlaytestLog.kt:115` (executor), `:368` (`close()`).
- Scenariusz: gracz wychodzi z gry przyciskiem Wstecz (`finish` → `onDestroy`), a proces
  zostaje w pamięci. Po ponownym otwarciu `onCreate` tworzy nowy `PlaytestLog` z nowym
  `Executors.newSingleThreadExecutor`. Stary wątek `century-playtest` nigdy się nie kończy,
  bo `close()` nie był nigdzie wołany. `BattleHistory` z tym samym wzorcem jest zamykana
  w `onDestroy`. Skutek: każde wyjście i powrót zostawia jeden bezczynny wątek i obiekt
  dziennika.
- Poprawka: `game.playtest?.close()` w `onDestroy`, obok `history.close()`. `shutdown()`
  kończy zapisy zleconych w `onPause`. Później `saveAsync` i `awaitWrites` łapią
  `RejectedExecutionException` (już były w `try`). Do testów doszły `PlaytestLog.closed`
  i `MainActivity.playtestLog`.
- Test: `app/src/test/java/com/centurywar/PlaytestClose39Test.kt` (Robolectric: `destroy`
  zamyka wątek; zapis zlecony przed `close()` trafia na dysk; wywołania po `close()` nie
  rzucają wyjątku).

## Potwierdzone, do decyzji

### 2. Rankingowe wyzwanie dnia utracone przy aktualizacji, a gra otwarta następnego dnia UTC: brak jakiejkolwiek rekompensaty

- `meta/LostBattle.kt:36` (rankingowe = 0 medali) i `:65` (zwrot próby tylko dla `dailyDay == today`).
- Scenariusz: gracz zaczyna rankingowe wyzwanie dnia D. W trakcie bitwy aplikacja się
  aktualizuje, a gracz otwiera ją dnia D+1. `consolation = 0`, `dailyDay != today`, więc
  nie dostaje medali ani próby. Widzi tylko baner „bitwa przerwana”. Rozliczenie zgadza się
  z opisem („dzisiejsze … odzyskuje próbę”), ale za inne tryby rekompensata jest zawsze.
  Do decyzji: np. medale jak za trening (`5 * effort`), gdy próby nie da się już zwrócić.
  Kodu nie zmieniałem, bo zmiana dotyczy nagród.

### 3. (Poza zakresem dat) wątek `snapshot-io` z `SnapshotStore` też nie jest zamykany

- `sim/.../BattleSnapshot.kt:614`: executor tworzony leniwie przy pierwszym zapisie lub
  usunięciu. Ten sam wzorzec co w pkt 1, ale kod jest sprzed 3.11 i należy do `:sim`, więc go
  nie ruszałem. Wyciek następuje tylko wtedy, gdy w danej Activity był zapis zrzutu.

## Odrzucone (sprawdzone, bez błędu albo bez pewnego scenariusza)

| # | Podejrzenie | Miejsce | Dlaczego odrzucone |
|---|---|---|---|
| 4 | Podwójna rekompensata za bitwę utraconą przy aktualizacji | `meta/LostBattle.kt:60`, `sim/.../BattleSnapshot.kt:786` | Idzie przez `Profile.settleBattle` (identyfikator bitwy). Gdy proces zginie po zapisie profilu, przy starcie `meta.battleId == lastSettledBattle` usuwa pliki. `settle` zwraca null i `settleLostBattle` usuwa pliki bez banera. |
| 5 | Plik `.meta` z poprzedniej bitwy opisuje nowy zrzut | `BattleSnapshot.kt` `writeAtomic`/`loadBattle` | Opis jest zapisywany po zrzucie, a przy odczycie dopasowywany po `storedBattleId`. Gdy identyfikatory się nie zgadzają, rekompensaty nie ma (wybór ostrożny). |
| 6 | Druga premia za powrót po synchronizacji z chmurą lub imporcie | `meta/Profile.kt:731` | `lastPlayDay` i `welcomeBackDay` biorą maksimum z wartości lokalnej i chmurowej. Cofnięty zegar (`today <= lastPlayDay`) nie zmienia niczego. |
| 7 | Migracja `lastPlayDay` w starym profilu | `Profile.kt:1207` | Bierze `missionState.lastSeenDay` z chwili wczytania, czyli sprzed odświeżenia misji przy starcie. Wartość spoza 0..MAX_DAY daje −1 (bez premii). |
| 8 | Seria porażek i krok samouczka trafiają do chmury lub importu | `Profile.kt` `cloudJson`/`fromJson` | Usuwane z JSON-a chmury, a przy `fromJson` przywracane z wartości lokalnej. |
| 9 | Pętla testowa dotyka prawdziwego profilu lub plików gracza | `testloop/TestLoopActivity.kt:101,131`, `CenturyWarApp.kt:22` | Profil, zrzut i kopie są tylko w `cacheDir/testloop`. W procesie `:testloop` nie powstaje `CrashStore` (`store == null`, brak zapisu). `Game.testLoop` blokuje ranking, wysyłkę i Play Games. Metody `Host` (przypomnienia, udostępnianie) to puste domyślne. URI wyników sprawdza `TestLoopIntent`. |
| 10 | Wspólny cache `cacheDir/sfx` dla obu procesów | `engine/Sound.kt:227` | Wyścig możliwy tylko przy jednoczesnym działaniu gry i pętli testowej z pustym cache. Na urządzeniu raportu przedpremierowego to się nie zdarza. Brak pewnego scenariusza. |
| 11 | Gra uznaje własną, jeszcze wybrzmiewającą muzykę za cudzą i milknie (`yieldToOther`) | `engine/Music.kt:103` | `pause()` oddaje fokus i zatrzymuje odtwarzacze przed `resume()`. Nie potwierdziłem, że `isMusicActive` zwraca wtedy true. Nawet gdyby tak było, sprawdzenie w tle przywraca muzykę po 2 odczytach ciszy (ok. 3,5 s). Bez pewnego scenariusza. |
| 12 | Oddech muzyki (`MusicBreath`): wątki | `engine/MusicBreath.kt` | Wszystkie metody `@Synchronized`. Każdy egzemplarz ma jednego pisarza (wątek syntezy albo nagrań), `dt` jest ograniczone, a NaN odfiltrowany. |
| 13 | Wznowiony samouczek: krok w profilu wyprzedza zrzut | `screens/TutorialOverlay.kt:159` | Krok wraca od początku, czeka na swój warunek i ma limit czasu. Wcześniejsze kroki się nie powtarzają, więc samouczek się nie blokuje. Krok postawy przy armii w „Trzymaj” jest obsłużony (`stanceHeld`). |
| 14 | `AchievementBattleTracker.pulseCd` za krótka tablica | `meta/AchievementBattleTracker.kt:51` | `battle.units` to tablica o stałym rozmiarze `MAX_UNITS`. |
| 15 | R8: nazwy enumów w nowych formatach | `BattleSnapshot.kt:69` (`Mode` po nazwie), `Missions.kt` (nowe `MissionType`) | Oba enumy mają `-keep enum` w `proguard-rules.pro`. W nowym kodzie brak innej refleksji i serializacji refleksyjnej. Nazwy ekranów są stałymi `crashName`. |
| 16 | Uszkodzony `playtest.json` / pola ankiety | `meta/PlaytestLog.kt:159` | Wyjątek przy odczycie oznacza start od zera. Pola ankiety są sprawdzane (zakresy, ocena 0 przy „answered” → „skipped”). Raport używa `getOrElse`, a złą datę łapie `try`. |
| 17 | Uszkodzone nowe pola profilu | `Profile.kt` `restore` | Same `opt*` z `coerceIn` lub sprawdzeniem zakresu, więc nie ma wyjątków. |
| 18 | `DailyReminderGoal` nadpisuje profil | `notifications/DailyReminderGoal.kt:21` | `fromJson`, `restore` i `NextGoals.pick` nie wołają `save()`. Obiekt wskazuje osobny plik. |
| 19 | Zapisy `PlaytestLog.saveAsync` z dwóch wątków w złej kolejności | `MainActivity.onPause`, `BattleScreen.settleRewards` | Zadanie z `onPause` biegnie na wątku gry albo na UI tylko wtedy, gdy pętla stoi. Nigdy równolegle z wątkiem gry. |
| 20 | Tempo ×½ (`speed = 0`) jako indeks lub mnożnik | `BattleScreen.kt` | Wszystkie użycia przez `speedFactor` albo z osobną gałęzią dla 0. Napisy mają indeks 0 („×½”). Symulacja bez zmian. |
