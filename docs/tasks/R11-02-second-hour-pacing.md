# R11-02: Druga godzina bez klifu — krótsze etapy 8–12

**Warunek:** decyzja właściciela (R11, pytanie 1). Domyślnie wariant (a): etapy 8–12.
Przy wariancie (b) zakres to 8–18, a pomiar obejmuje wszystkie te etapy.
**Gałąź:** `claude/r11-02-druga-godzina` z `origin/integ/tura10`.
**Teksty:** raczej brak. Gdyby były potrzebne, tylko `part197()` w `engine/L.kt` (10 języków).
**Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`. Długie pomiary przez
Bash `run_in_background`. Źródło: `docs/research/R11-backlog.md`, TOP 2. Commity po polsku,
wypychaj często.

## Cel

Dzień 1 skrócił etapy 2–7: presja oblężnicza rusza po 4 min zamiast 8 i rośnie o 80%/min
zamiast 20%/min. Mediana spadła z 13,4 do 7,7 min. Etap 8, pierwszy z dowódcą, wraca do
starych zasad: **mediana 13,4 min, 66% wygranych, oczekiwany czas przejścia 19,3 min**
(`docs/BALANCE.md`, tabela „Pomiar przed/po, 100 seedów na etap, Normalny”). Etapy 8–18 trwają
12–14 min. Tester po godzinie gry dostaje dowódcę, po czym każda bitwa trwa nagle dwa razy dłużej.

Cel: etapy 8–12 z medianą **9–10 min**, wygrane w paśmie ±5 pp względem dzisiejszych,
płynne przejście 7 → 8 → 12 → 13. Ostatnia skrócona bitwa nie może być wyraźnie krótsza od
etapu 13 tylko dlatego, że 13 ma stare zasady. Jeśli tak wyjdzie, rozważ stopniowanie (niżej).

## Stan w kodzie

- `sim/.../Modes.kt`, `object Campaign`:
  - `EARLY_SIEGE_START = 240f`, `EARLY_SIEGE_RAMP = 0.8f`, `EARLY_SIEGE_STAGES = 1..6`;
  - `earlySiege(cfg)`, `siegeStart(cfg)`, `siegeRamp(cfg)`. Poza zakresem obowiązuje
    `Battle.SIEGE_START = 480f` i `Battle.SIEGE_RAMP = 0.2f` (`Battle.kt` ok. 237);
  - `stageRamp(i)`: korekty siły AI dla etapów (`11 -> 0.97f`, `12 -> 0.828f`).
- `sim/.../Battle.kt`: `val siegeStart`/`siegeRamp` (ok. 300) z `Campaign`. `commanderAvailable`:
  kampania od `stageIndex >= 7` (etap 8).
- UI czyta `battle.siegeStart` (`BattleScreen` `tip_siege`, `BattleAlerts`, `HintOverlay.SIEGE`),
  więc samo się dostosuje.
- `sim/.../SimVersion.kt`: `PROTOCOL = "0.8.6"`.
- Pomiar: `app/src/test/java/com/centurywar/game/BalanceReport.kt` (`BALANCE_RUN=true`,
  `BALANCE_RUNS`, `BALANCE_PROFILE=target|medale`, `BALANCE_STAGE_FROM/TO`; kolumny `expected`
  i `stars`), `tools/check_campaign_balance.py`. Przebieg pierwszej godziny:
  `FirstHourJourneyTest` / `FirstHourJourneyBase`.
- Złote powtórki: `sim/src/test/resources/golden/` (`campaign-opening`, `campaign-night`, …).
  Regeneracja: `ORG_GRADLE_PROJECT_goldenReason=... ./gradlew :sim:regenerateGolden -PserverOnly=true --no-configuration-cache`.

## Zmiany

### 1. Zasady presji etapów 8–12 (`Campaign`)
- Uogólnij `earlySiege` na tabelę, bez zmiany etapów 2–7:
  ```kotlin
  /** R11-02: (start s, przyrost /min) dla indeksów etapów; reszta = Battle.SIEGE_*. */
  fun siegeStart(cfg): Float = when { early(cfg) -> 240f; second(cfg) -> SECOND_SIEGE_START; else -> Battle.SIEGE_START }
  ```
  `SECOND_SIEGE_STAGES = 7..11` (indeksy). Punkt startowy pomiaru: `300f` i `0.5f`/min.
  Samouczek, Endless, potyczka i wyzwania dnia/tygodnia bez zmian. Każdy poziom trudności
  (Łatwy/Normalny/Trudny) na tych samych zasadach, jak w dniu 1.
- Jeśli etap 12 wyjdzie wyraźnie krótszy od 13: stopniowanie, np. 8–10 `300/0.5`,
  11–12 `360/0.4`. Wybierz najprostszy wariant spełniający kryteria i opisz go w BALANCE.md.
- Korekty `stageRamp` dla 8–12 tylko wtedy, gdy wygrane wyjdą poza pasmo ±5 pp
  (dzień 1 potrzebował jednej korekty, etap 7: 0,95). Najmniejsza korekta, która wraca do pasma.

### 2. Wersja zasad i powtórki
- `SimVersion.PROTOCOL` 0.8.6 → **0.8.7**. Dopisz do `Replay`/serwera listę przyjmowanych formatów
  tak jak przy 0.8.5 (dzień 1). Sprawdź `SimVersion` i testy serwera.
- Regeneracja złotych powtórek z powodem „R11-02: krótsza presja etapów 8-12”. Jeśli żadna
  złota powtórka nie dotyczy etapów 8–12, wynik powinien zostać bez zmian. Wpisz to do raportu
  jako kontrolę determinizmu.
- `BattleSnapshot.defsFingerprint`: zasady presji nie są częścią odcisku (dzień 1), więc
  przerwana bitwa wznawia się po aktualizacji na nowych zasadach. Potwierdź testem. Jeśli
  odcisk jednak się zmieni, działa ścieżka R10-01 (baner + rekompensata). Opisz, co zaszło.

### 3. Pomiar (100 seedów, Normalny)
- `BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target BALANCE_STAGE_FROM=7 BALANCE_STAGE_TO=14`
  i to samo z `BALANCE_PROFILE=medale`, przed i po. Zasady trybu bez `BALANCE_COMMANDER`
  i z `BALANCE_COMMANDER=on`, bo etap 8 wprowadza dowódcę.
- Tabela jak w dniu 1: wygrane, mediana, oczekiwany czas, ★ wygranych, medale. Etapy 7, 13 i 14
  służą jako kontrola (identyczne przed i po).
- Model „drugiej godziny”: suma oczekiwanych czasów 8–12 przed i po plus przerwy 1,5 min.
  Ile etapów mieści się w 60 min po dowódcy?
- Jeden przebieg `FirstHourJourneyTest` przedłużony do etapu 12 (zmienna środowiskowa albo
  osobny test `SecondHourJourneyTest`, wyłączony domyślnie): czasy bitew, porażki, dymki
  i zapowiedzi bez kolizji.

## Kroki

1. Pomiar „przed” (tło, ok. 30–60 min).
2. Zmiana zasad + test jednostkowy `Campaign.siegeStart/Ramp` (8–12 nowe, 2–7 i 13+ bez zmian,
   samouczek i tryby bez zmian).
3. Pomiar „po”, ewentualnie stopniowanie albo korekta `stageRamp`, pomiar ponownie.
4. PROTOCOL, złote powtórki, testy serwera.
5. `docs/BALANCE.md` (nowa sekcja „R11-02 — druga godzina”), pełne testy, wypchnij.

## Testy do dopisania

- `sim`: `SecondHourSiegeTest`: dla każdego poziomu trudności `siegeStart`/`siegeRamp` etapów
  1–36 zgodne z tabelą; tryby inne niż kampania i samouczek bez zmian.
- `sim`: determinizm etapu 13: ten sam seed, ten sam wynik i czas przed i po (powtórka kontrolna).
- Wznowienie zrzutu bitwy etapu 9 zapisanego przed zmianą: wczytuje się i dalej działa
  (albo ścieżka R10-01, patrz punkt 2).
- Istniejące: `FirstHourJourney*`, `DoctrineBalance82Test`, testy serwera z PROTOCOL.

## Kryteria odbioru

- Etapy 8–12: mediana 9–10 min (profil docelowy, 100 seedów), wygrane ±5 pp względem „przed”,
  0 patów, najkrótsza mediana ≥ 4 min (walidator).
- Etapy 2–7 i 13–36 identyczne przed i po.
- Gwiazdki i medale za etapy 8–12 nie spadają o więcej niż 10% względem „przed”. Krótsza
  presja zostawia mniej HP bazy, więc sprawdź ★ i, jeśli trzeba, skoryguj tak jak w etapie 7.
- PROTOCOL 0.8.7, złote powtórki aktualne, serwer przyjmuje nowe powtórki.
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Dowódca (etap 8) ma mniej czasu na pokazanie się. Sprawdź w pomiarze z `BALANCE_COMMANDER=on`,
  czy różnica „z dowódcą / bez” zostaje podobna. Jeśli zniknie, wolniejsza presja tylko dla etapu 8.
- Zmiana PROTOCOL unieważnia powtórki rankingowe kampanii zrobione przed aktualizacją.
  Najlepiej scalić **przed** pierwszym AAB dla testerów zamkniętych.
- Krótsze bitwy zmieniają tempo zdobywania medali, a więc badań, przed etapami 19+ (`stageRamp`
  strojone pod ok. 40% badań). Zanotuj medale/h przed i po.

## Czego nie ruszać

- Etapy 2–7 (dzień 1), samouczek, Endless, potyczka, wyzwania dnia i tygodnia.
- Jednostki, AI, koszty, HP baz.
- Zatwierdzony pasek `compactTop`, celowanie ostrzału.
