# R6: przegląd kodu po scaleniach D5, R2-03, R2-04, R2-05 (3.11.2026)

Zakres: `git diff 13f98b7..origin/integ/tura10 -- app/src/main` (921cd50; 32 pliki, +2242/−98).
Najwięcej zmian: `engine/GameAccessibility.kt`, `screens/BattleAccessibility.kt`,
`meta/PlaytestLog.kt`, `crash/PlaytestReportText.kt`, `crash/ExitReasons.kt`, `engine/Game.kt`,
`MainActivity.kt`, `engine/QualityGovernor.kt`, `engine/ThermalWatch.kt`, `engine/Music.kt`.
Przegląd czytaniem kodu, bez uruchamiania testów (Gradle zajęty przez agenta dnia 9).
Wiersze według 921cd50.

**Wynik: 0 problemów wysokich, 6 średnich, 9 niskich.** Nie znalazłem utraty danych,
podwójnego rozliczenia, wyścigu z ryzykiem awarii ani alokacji w pętli rysowania bez TalkBack.
Wątki są pilnowane starannie:
- migawki drzewa dostępności są niezmienne;
- akcje wracają przez `game.post` z kontrolą `stillValid`;
- `onTrimMemory` działa na UI tylko przy zatrzymanej pętli, jak migawka w `onPause`;
- `PlaytestLog` jest `@Synchronized`, a zapis idzie przez osobny wątek;
- dziennik testu jest rozliczany w `settleBattle`, czyli raz.

## Średnie

### R6-M1. Zabicia w tle z braku pamięci zapychają dziennik awarii
- **Gdzie:** `crash/ExitReasons.kt:40` (`REASON_LOW_MEMORY -> true` bez warunku na `importance`);
  `crash/CrashStore.kt` (`MAX_LOGS = 10`, `rotate()`, `newest()`).
- **Scenariusz:** na tanim telefonie system regularnie zabija procesy z pamięci podręcznej
  (`importance` = CACHED). Każde takie zabicie po powrocie do gry zapisuje raport (do 5 na start).
  Po kilku dniach 10-elementowy dziennik zawiera same „LOW_MEMORY” i rotacja usuwa prawdziwe
  raporty awarii. „Zgłoś problem” (`sharingText` → `newest()`) wysyła wtedy najświeższy
  raport, czyli zwykłe zabicie w tle, a nie awarię.
- **Poprawka:** `REASON_LOW_MEMORY` (i `EXCESSIVE_RESOURCE_USAGE`) tylko dla
  `importance <= IMPORTANCE_VISIBLE`, jak `SIGNALED`. Opcjonalnie raporty zakończeń w osobnej
  kwocie (np. najwyżej 3), żeby nie wypierały awarii Javy.

### R6-M2. Stare ANR po aktualizacji wywołują pytanie „gra się zawiesiła”
- **Gdzie:** `crash/ExitReasons.kt:71–80` (`last = prefs.getLong(LAST_EXIT_TS, 0L)`) +
  `MainActivity.kt:144–158` (`pendingFatal` → `offerCrashReport`).
- **Scenariusz:** tester aktualizuje grę do wersji z R2-05. Historia `ApplicationExitInfo`
  przetrwa aktualizację, a znacznika jeszcze nie ma (0). Pierwsze uruchomienie zapisuje jako
  „fatal” każdy ANR sprzed tygodni (do 5 wpisów) i od razu pyta o wysłanie raportu awarii. Tester
  zgłasza problem, którego nie było w tej sesji.
- **Poprawka:** przy braku klucza `LAST_EXIT_TS` tylko ustawić znacznik na najnowszy wpis
  (bez raportów) albo brać wpisy nowsze niż `PackageInfo.lastUpdateTime`. Dopisać do raportu
  czas zdarzenia jako pierwszy wiersz (już jest „Czas”). Plik ma `lastModified` = chwila zapisu,
  więc kolejność w `newest()` może być myląca.

### R6-M3. Termika obniża jakość aż do minimum, krok co 3 s
- **Gdzie:** `engine/QualityGovernor.kt:70–74` (`thermalFor >= 3f` → `reduction++`) i `changed()`
  (90; zeruje `thermalFor`). `Game.monitor` przekazuje `thermalSignal(status, headroom)`,
  a `headroom >= 0.85` znaczy MODERATE.
- **Scenariusz:** w długim Endless status MODERATE (albo zapas ≥ 0,85) trzyma się minutami.
  Co 3 s kolejny stopień, więc po ok. 30 s gra spada do najniższej jakości i skali bufora 0,60,
  choć CPU i GPU mają zapas. Powrót wymaga statusu ≤ LIGHT i 10 s zapasu. Na wielu telefonach
  MODERATE trwa do końca sesji, więc gra wygląda gorzej niż trzeba.
- **Poprawka:** limit stopni termicznych (np. najwyżej 2 ponad stopnie z CPU/GPU) albo odstęp
  ≥ 15 s między kolejnymi stopniami termicznymi. Termika nie powinna obniżać skali bufora,
  tylko efekty (dziś po wyczerpaniu efektów `reduction` przechodzi na skalę). Test w `QualityGovernorTest`.

### R6-M4. Przy TalkBack i ograniczonym tempie drzewo przebudowuje się co klatkę
- **Gdzie:** `screens/BattleAccessibility.kt:113` (klucz przycisku tempa zawiera
  `effectiveSpeed.toInt()`), `engine/GameAccessibility.kt:471` (różny klucz oznacza przebudowę bez limitu
  1 s), 373 (`TYPE_WINDOW_CONTENT_CHANGED` dla całego widoku).
- **Scenariusz:** gracz z TalkBack przełącza na ×3/×5 na słabym telefonie. `effectiveSpeed`
  waha się co klatkę wokół granicy (np. 4,9/5,0 → `toInt` 4/5). Klucz się zmienia, więc za
  każdym razem następuje przebudowa strukturalna i zdarzenie zmiany całego okna. Skutek:
  alokacje (`Node`, `String`) i zalew zdarzeń do TalkBack, który przerywa czytanie i dławi telefon,
  który i tak już nie nadąża.
- **Poprawka:** z klucza usunąć `effectiveSpeed` (zostaje sam `speed`). Napis „×5 → ×3” trafia
  do `stateDescription` przy przebudowie tekstów co 1 s.

### R6-M5. Raport testera ujawnia użycie TalkBack bez wzmianki w opisie
- **Gdzie:** `crash/PlaytestReportText.kt:74, 177` (`TalkBack: yes/no`),
  opis `pt182_desc` w `engine/L.kt:182` („…wydajność i ustawienia. Bez nicku…”).
- **Scenariusz:** informacja, że gracz używa czytnika ekranu, pozwala wnioskować o
  niepełnosprawności (dane szczególnej kategorii, art. 9 RODO). Gracz wysyła raport ręcznie,
  ale opis nie mówi, że zawiera on ustawienia dostępności, więc zgoda nie jest świadoma.
  Wydawca dostaje taką informację e-mailem.
- **Poprawka:** dopisać w `pt182_desc` (10 języków) „w tym ustawienia dostępności (np. czy
  działa czytnik ekranu)” albo usunąć wiersz TalkBack i zostawić tylko „tempo ×½”. Dopisać zdanie
  w polityce prywatności przy raporcie testera.

### R6-M6. Muzyka gry milknie na całą sesję po krótkim dźwięku z innej aplikacji
- **Gdzie:** `engine/Music.kt:77` (`yieldToOther = !focusHeld && otherAppPlaying()` tylko w `resume()`)
  i 171 (`AudioManager.isMusicActive`).
- **Scenariusz:**
  (a) gracz wraca do gry z odtwarzaczem na pauzie, ale `isMusicActive` jeszcze jest `true`
  (wygaszanie, powiadomienie dźwiękowe, film w innej aplikacji); gra milknie do następnego
  wyjścia i powrotu;
  (b) gracz zatrzymuje Spotify w trakcie gry, a muzyka gry nie wraca;
  (c) `isMusicActive` liczy też strumień muzyki **tej** aplikacji, więc dźwięk efektu
  `SoundPool` w chwili `resume()` może wyciszyć muzykę gry.
- **Poprawka:** sprawdzać `isMusicActive` z opóźnieniem ok. 1 s po `resume()` i ponawiać co
  kilka sekund, dopóki `yieldToOther` (lekki timer na wątku muzyki). Zdecydować wcześniej niż
  odtwarzacze gry ruszą (dziś kolejność w `resume()` jest dobra). Decyzja właściciela
  („nie przerywać muzyki gracza”) zostaje.

## Niskie

| # | Gdzie | Problem | Propozycja |
|---|---|---|---|
| R6-L1 | `MainActivity.kt:144` → `ExitReasons.anrThreads` | Czytanie śladu ANR (skan do 2 mln znaków) na wątku startu opóźnia pierwszy ekran po ANR | Przenieść `collectExitReasons` za pierwszą klatkę (wątek `crash-io`), a pytanie o raport pokazać przy następnym starcie albo po zebraniu **Zrobione w R10-04.** |
| R6-L2 | `MainActivity.kt:379` / `Game.applySystemAccessibility` | Włączenie TalkBack skrótem (klawisze głośności) w trakcie gry nie jest wykrywane, bo odczyt jest tylko w `onResume`/`onConfigurationChanged`. Tempo ×½ i opcja nie włączą się do powrotu z tła | `AccessibilityManager.addTouchExplorationStateChangeListener` w `onResume`, usunąć w `onPause` **Zrobione w R10-04.** |
| R6-L3 | `engine/Game.kt:427` | Automatyczne `reduceShake/reduceFlash` nie wracają, gdy gracz ponownie włączy animacje w systemie | Zapamiętać „ustawione automatycznie” i cofać, gdy `!animationsOff && !motionManual` **Zrobione w R10-04.** |
| R6-L4 | `screens/CampaignScreen.kt:327` | Po `onTrimMemory` wszystkie 9 obrazów epok nagrywa się w jednej klatce, więc przy powrocie z tła jest jednorazowe przycięcie | Nagrywać tylko widoczne regiony, resztę leniwie **Zrobione w R10-04.** |
| R6-L5 | `engine/Game.kt:706–712` | Okna wydajności 30 s liczą też czas pauzy i ekran wyników (`screen is BattleScreen`), co zaniża p95 i redukcje w raporcie | Liczyć tylko, gdy `!paused && !finished` |
| R6-L6 | `engine/GameAccessibility.kt:270–282` (`tap`) | Gdy żaden z punktów próbnych nie trafia w przycisk, kliknięcie z czytnika nic nie robi, a `performAction` zwróciło już `true` | Wynik `tap` do logu debug; w ostateczności `screen.onClick(button)` **Zrobione w R10-04.** |
| R6-L7 | `crash/ExitReasons.kt:80` | Raport zakończenia procesu ma `lastModified` = chwila zapisu, nie chwila zdarzenia, więc `newest()` może wybrać stary ANR zamiast świeższej awarii | `file.setLastModified(info.timestamp)` po zapisie |
| R6-L8 | `meta/PlaytestLog.kt:286` | Dziennik testu zapisuje się tylko w `onPause`. Awaria w trakcie sesji gubi jej liczniki (sesje, porażki), czyli dokładnie te sesje, które testera interesują | Dodatkowy `saveAsync()` po rozliczeniu bitwy (`battleSettled`) |
| R6-L9 | `engine/Music.kt:171` | `isMusicActive` może rzucić `SecurityException` na niektórych ROM-ach; jest złapany jako `RuntimeException`, więc OK. Uwaga tylko do testu na urządzeniu | — |

## Sprawdzone bez uwag

- **Wznawianie bitwy:** tempo nie wchodzi do zrzutu (`speed` tylko w UI). Wznowiona bitwa
  startuje w ×½ przy TalkBack. `BattleSnapshot.VERSION` bez zmian. Dziennik testu jest
  rozliczany w `settleRewards` wewnątrz `settleBattle` (raz).
- **Zapis profilu:** nowe flagi `textScaleManual`, `motionManual`, `slowSpeedOption`,
  `slowSpeedManual` są opcjonalne (`optBoolean`/`has`), bez wpisu w `mandatory`. Reset je zeruje,
  a migracja starego profilu jest zgodna z decyzją właściciela. `applySystemAccessibility` zapisuje
  profil tylko przy zmianie. Profil z chmury/importu dostaje ustawienia systemu ponownie
  (`applyLoadedProfile`).
- **Wątki w dostępności:** `tree` volatile i niezmienne. Lista węzłów nie jest modyfikowana po
  publikacji. Akcje niestandardowe sprawdzają ekran i rodzaj drzewa. Ostrzał z akcji idzie przez
  `fireAimedBarrage(snapBarrageTarget(x))`, więc komenda jest ta sama co po przeciągnięciu.
- **Alokacje:** bez usługi dostępności `refreshIfEnabled` wraca od razu. Porównanie struktury
  (`planMatches`) nie alokuje. Dziennik testu w pętli (`perfSample` co 30 s) nie alokuje.
  `ThermalWatch` działa na wątku UI.
- **`onTrimMemory`:** `game` i `view` inicjowane w jednym wywołaniu `finishStartup` na wątku
  UI, więc brak wyścigu z `lateinit`. Gdy pętla stoi, zwalnianie idzie od razu na UI (jak migawka
  w `onPause`). `EraIntroOverlay.trimMemory` nie rusza aktywnej zapowiedzi.
- **Prywatność raportu testera** (poza M5): brak nicku, UUID, sekretu i konta PGS. Daty tylko
  jako dni UTC, model telefonu jak w raporcie awarii, 6000 znaków.
- **L.kt:** `putAll(part` = `private fun part` = 62.

Zlecenie poprawek M1–M6: `docs/tasks/R6-02-fixes.md`.
