# R2-02: Pomoc po porażkach w kampanii

**Gałąź:** `claude/r2-02-help-after-losses` z `origin/integ/tura10`. **Teksty:** tylko nowa `part180()`
w `engine/L.kt` (10 języków). **Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`.
Źródło: `docs/research/R1-backlog.md`, TOP 6. Najlepiej po R2-01, bo oba korzystają z wiersza
wskazówki na wynikach. Commity po polsku, wypychaj często.

## Cel

Gracz, który przegrał ten sam etap kampanii **dwa razy z rzędu**, dostaje na wynikach konkretną
propozycję: poziom **Łatwy** albo badanie, na które go stać. Przy trzeciej porażce z rzędu dymek
na mapie kampanii przypomina, że poziom trudności można zmienić. Dziś etapy 11–18 dla gracza
z małą liczbą badań kończą się wygraną w 37–50% (`docs/BALANCE.md`), a seria porażek to typowy
moment, w którym gracz odchodzi.

## Stan w kodzie

- Rozliczenie: `BattleScreen.settleRewards(pr, won)` (`screens/BattleScreen.kt` ok. 1167–1290),
  gałąź `Mode.CAMPAIGN`. Etap: `cfg.stageIndex`. Poziom: `Campaign.difficulty(cfg)`.
  Samouczek: `cfg.tutorial`. Kapitulacja: `surrendered`.
- Wskazówka na wynikach: `tipKey` (`tip_siege`, `tip_cap`, …) → `BattleResultsPanel.refreshResultTexts()`
  składa `tipText` (`why_lost` + powód; `defeatReasons` z `collectDefeatReasons()`).
- Poziom trudności: `Profile.campaignDifficulty` (`meta/Profile.kt:132`, zapis `toJson` ok. 492,
  odczyt ok. 815/962). Wybór w oknie etapu: `CampaignScreen.difficultyButtons` (ok. 210–225),
  moc AI ok. 617, start bitwy ok. 668.
- Ranking kampanii liczy się tylko na Normalnym (`settleRewards`: `difficulty == NORMAL`).
- Dymki: `HintOverlay` (stałe `FIRST_BATTLE = 20`, … `COMMANDER = 24`); ekran mapy używa
  `hints.showScreen(HintOverlay.FIRST_BATTLE, ...)` (`CampaignScreen.kt` ok. 315).

## Zmiany

1. **Licznik porażek** w `Profile`: `var campaignLossStreakStage = -1`, `var campaignLossStreak = 0`.
   - W `settleRewards`, gałąź `CAMPAIGN`, poza samouczkiem: porażka na tym samym etapie →
     `streak++`, na innym → `stage = idx, streak = 1`. Wygrana na tym etapie → reset.
     **Kapitulacja liczy się jak porażka tylko po ≥ 60 s bitwy** (`battle.time`), żeby nie
     nabijać licznika szybkim „Porzuć”.
   - Zapis w `toJson()` jako klucze opcjonalne (`optInt`, **nie** dodawać do listy `mandatory`
     w `restore`). Dopisać do `cloudJson` → `remove(...)` (stan lokalny, nie do chmury), jak
     `lastSettledBattle` (`Profile.kt` ok. 601–608). `reset()` (ok. 1180) zeruje licznik.
   - Licznik zmienia się wewnątrz `pr.settleBattle(...)`, więc rozlicza się raz (A156-10).
2. **Wynik po 2. porażce** (`streak >= 2`, etap ≥ 2, poziom ≠ EASY): `tipText` dostaje
   **przed** powodem porażki jedno zdanie: „Ten etap sprawia kłopot. Spróbuj na poziomie
   Łatwy (bez rankingu)” albo, gdy gracza stać na badanie (`Profile.canBuy`; najtańsze
   z `Research.defs`, elita przez `EliteUnlock`), „Stać cię na badanie: %s”. Badanie ma
   pierwszeństwo, bo nie wyłącza rankingu. Całość mieści się w istniejących `resultTipLines`
   (`prepareResultTipLines`), ograniczonych do dostępnej wysokości.
3. **Szybka zmiana poziomu:** po dotknięciu Dalej przy aktywnej propozycji Łatwego
   `CampaignScreen` otwiera okno tego etapu (`selected = Campaign.stages[idx]`) z wyróżnionym
   przyciskiem EASY (`highlight`/obwódka). **Nie zmieniaj poziomu samodzielnie**, gracz wybiera.
   Dodaj do `CampaignScreen` parametr konstruktora z wartością domyślną, żeby nie zmieniać
   innych wywołań.
4. **Dymek po 3. porażce:** nowa stała `HintOverlay.DIFFICULTY = 25` (sprawdź, czy wolna), pokazywana
   raz (`HintSettings`, jak inne dymki), celuje w przycisk EASY w oknie etapu.
5. Teksty w `part180()`: `help180_easy`, `help180_research`, `help180_hint_title`, `help180_hint`
   (10 języków, zwięźle, jak `why_lost`).

## Testy do dopisania

- `meta/ProfileTest` (albo nowy `CampaignLossStreakTest`): seria 2× porażka → 2; wygrana → 0;
  inny etap → 1; kapitulacja < 60 s nie liczy się; samouczek nie liczy się; licznik nie trafia
  do `toCloudJson()`; stary profil bez kluczy wczytuje się (wartości domyślne).
- Rozliczenie raz: ponowne `settleBattle` tej samej bitwy nie zwiększa licznika (wzór:
  `FirstHourJourneyTest.tutorialInterruptedResumedSettlesOnce`).
- Test ekranu: po 2 porażkach na etapie 11 wynik zawiera tekst propozycji. Dalej otwiera okno
  etapu 11 z wyróżnionym EASY. Napisy na 720p i zewnętrznym Foldzie nieucięte
  (płótno testowe z `PrereleasePolishBase`).
- `LTest`: 10 tłumaczeń nowych kluczy.

## Kryteria odbioru

- Propozycja pojawia się dokładnie od 2. porażki z rzędu i znika po wygranej.
- Wybór Łatwego jest zawsze decyzją gracza, a tekst mówi o braku rankingu.
- Medale, gwiazdki, ranking i powtórki są bez zmian (złote powtórki nietknięte).
- Pełne testy zielone.

## Ryzyka

- Nadmiar tekstu na wynikach na zewnętrznym Foldzie. Przy braku miejsca pokaż tylko propozycję,
  bez drugiego powodu porażki.
- Gracz może odebrać propozycję jako protekcjonalną. Ton neutralny, bez „za trudne dla ciebie”.

## Czego nie ruszać

- Balans, moc AI, nagrody, `CampaignDifficulty` (mnożniki), `:sim`, PROTOCOL.
- Zasada „ranking kampanii tylko z Normalnego” i uczciwe AI.
- Zatwierdzony `compactTop` i rząd przycisków wyników.
