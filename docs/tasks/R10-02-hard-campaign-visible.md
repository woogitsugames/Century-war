# R10-02: Trudna kampania widoczna — korony na mapie, liczniki i „Następny cel”

**Gałąź:** `claude/r10-02-trudna-kampania` z `origin/integ/tura10`. **Teksty:** tylko nowa
`part189()` w `engine/L.kt` (10 języków; `putAll(part189())`; liczba `putAll(part` = liczba
`private fun part`). **Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`.
Źródło: `docs/research/R10-backlog.md`, TOP 2. Commity po polsku, wypychaj często.

## Cel

Po przejściu kampanii gracz ma widzieć następną drabinkę: wygraj każdy etap na Trudnym
(36 koron). Dziś profil zapisuje te wygrane (`hardStageWins`), ale pokazuje je tylko pasek
osiągnięcia `campaign_hard`. Mapa, panel etapu, kafel w menu i „Następny cel” ich nie znają.

## Stan w kodzie

- `meta/Profile.kt`: `private var hardStageWins` (133), `hardStagesCleared()` (161),
  `recordHardVictory(index)` (162). Zapis, odczyt i scalanie z chmurą obsługują już ok. 529, 865, 1011.
  Reset jest w 1242.
- `screens/BattleScreen.kt` ok. 1355: `if (won && !cfg.tutorial && difficulty == HARD) pr.recordHardVictory(stage)`.
- `screens/CampaignScreen.kt`:
  - `drawNode(c, t, unlocked)` (ok. 410–460). Rogi węzła są zajęte: pogoda w prawym górnym
    (`wx = x + r*0.94, wy = y - r*0.82`), flaga w lewym górnym (co 4. etap), błyskawica
    modyfikatora w lewym dolnym (`x - r*0.94, y + r*0.68`), gwiazdki pod węzłem. **Wolny jest
    prawy dolny róg.**
  - `drawRegionHeading(c, era, x, y)` (ok. 386): medalion, nazwa epoki, gwiazdka i „earned/12”.
  - `drawInfo(c, s)` (ok. 617): gwiazdki 30 dp na środku, wiersze zakresu epok, siły, pogody
    i modyfikatorów. Przyciski poziomu trudności `difficultyButtons` są nad „Start”.
  - Obrazy epok są nagrywane w `regionPictures` (`Picture`). **Korony rysuj poza nagraniem**,
    jak gwiazdki, bo stan się zmienia.
- `screens/MenuScreen.kt` ok. 449–455: `campaign.sub` = „gwiazdki/108”, przebudowa przy zmianie
  `subKey`, `campaign.subIcon = Icons.STAR`.
- `meta/NextGoal.kt`: `NextGoalKind { MISSIONS, RESEARCH, STAGE, WEEKLY_TIER, NONE }`, `pick(...)`.
  Po ukończeniu kampanii jedynym celem jest dziś próg Oblężenia tygodnia, a gdy wszystkie progi
  są zdobyte, `NONE`.
- Akcje celu: `MenuScreen.onClick` (`GOAL -> when (goalKind)`) i analogicznie wyniki bitwy
  (`BattleResultsPanel` / `BattleScreen`, R2-01).
- Ikona `Icons.CROWN` istnieje (używa jej Wódz w HUD).

## Zmiany

### Profil
- `fun hardWon(index: Int): Boolean` (publiczny odczyt bitu) i `fun hardWonInEra(era: Int): Int`
  (0–4). Bez zmian formatu zapisu.

### Mapa kampanii (`CampaignScreen`)
- `drawNode`: gdy `hardWon(i)`, mała korona w prawym dolnym rogu: kółko tła jak przy pogodzie
  (`Ui.OUTLINE` + złote wypełnienie 10 dp), `Icons.CROWN` 16 dp w punkcie `(x + r*0.94, y + r*0.68)`.
  Rysuj na węźle także po odblokowaniu następnego etapu. Bez animacji, bez alokacji.
- `drawRegionHeading`: gdy w epoce jest co najmniej 1 korona albo kampania ma wszystkie gwiazdki,
  obok licznika gwiazdek dopisz koronę i „X/4” (gotowe napisy w tablicy, jak `starLabels`).
  Kolor `Ui.GOLD` przy 4/4. Sprawdź szerokość na zewnętrznym Foldzie (`portraitMap`,
  `viewportW - 110 dp`): gdy się nie mieści, korona i licznik idą do drugiego wiersza pod gwiazdkami,
  a nazwa epoki się nie zmniejsza.
- `drawInfo`: pod dużymi gwiazdkami jeden wiersz z koroną: `hard189_won` („Wygrany na Trudnym”)
  albo, gdy wybrany jest Trudny i etap nie ma korony, `hard189_todo` („Wygraj na Trudnym, by
  zdobyć koronę”). Gdy wybrany jest Łatwy lub Normalny i korony nie ma, wiersza nie pokazuj.
  Wiersz nie może przesunąć chipów modyfikatorów pod napis „Trudność” na 720p (`row` 25 dp,
  test `PrereleasePolish*`). Gdy brakuje miejsca, połącz go z wierszem gwiazdek (korona obok
  gwiazdek).
- TalkBack: `CampaignScreen.accessibilityLabel` (ok. 724) ma dziś tylko gałąź `close`. Dodaj
  gałąź dla kafli etapów: `super.accessibilityLabel(b)` + `hard189_a11y` („korona Trudnego”),
  gdy `hardWon(i)`. Napis dopisuj raz przy zmianie stanu, nie w każdej klatce.

### Menu
- Gdy wszystkie etapy mają gwiazdkę (`p.stars.all { it > 0 }`), `campaign.sub` = „gwiazdki/108 ·
  korony/36”, a druga ikona to korona. Dopisz `hardStagesCleared()` do `subKey`. Przed tym
  momentem bez zmian.

### „Następny cel”
- Nowy `NextGoalKind.HARD_STAGE`, dodawany **na końcu** enuma. W `pick` po `WEEKLY_TIER`,
  czyli gdy próg tygodnia nie jest celem: jeśli kampania ma wszystkie gwiazdki, wybierz pierwszy
  etap bez korony. Tekst `hard189_goal`: „Trudny: zdobądź koronę na etapie %d (%s)”, gdzie
  drugi parametr to `stageNovelty(i)`. `arg` = indeks etapu.
- Akcja (menu i wyniki): `CampaignScreen` z zaznaczonym etapem `arg` i wybranym poziomem Trudny.
  Jeśli `CampaignScreen` nie ma jeszcze wejścia „otwórz na etapie”, dodaj parametr konstruktora
  `focus: Int = -1`. Ustaw `selected` i przewiń mapę tak, jak robi to `autoScroll` do bieżącego
  etapu. Poziomu trudności w profilu **nie zmieniaj** bez dotknięcia gracza: wystarczy obwódka
  wokół przycisku Trudny, jak `drawEasySuggestion`.

### Teksty `part189()`
`hard189_won`, `hard189_todo`, `hard189_goal`, `hard189_a11y`, `hard189_crowns` („Korony”, do
TalkBack licznika). 10 języków. Nazwę poziomu bierz z istniejącego `campaign126_hard`.

## Kroki

1. Profil: `hardWon`, `hardWonInEra` + test.
2. Mapa: węzeł, nagłówek, panel.
3. Menu: `campaign.sub`.
4. `NextGoal.HARD_STAGE` + akcje + wejście `CampaignScreen(focus)`.
5. `part189()`, zrzuty.

## Testy do dopisania

- `NextGoalTest`: `HARD_STAGE` pojawia się tylko po komplecie gwiazdek i po zdobyciu progów
  tygodnia; wybiera pierwszy etap bez korony; przy 36 koronach daje `NONE`.
- `ProfileTest`: `hardWon` i `hardWonInEra` po `recordHardVictory`, po odczycie JSON i po scaleniu
  z chmurą (bit z obu stron zostaje).
- Nowy test zrzutów `HardCampaign189ScreenshotTest` (telefon 1600×720, 2400×1080, zewnętrzny Fold
  904×2316, wewnętrzny 2184×1968; pl, en, de): profil z kompletem gwiazdek i 5 koronami (w tym
  pełna epoka 1). Asercje: korona nie nachodzi na gwiazdki, błyskawicę ani pogodę; licznik
  w nagłówku nieucięty, ≥ 11 sp; wiersz w panelu nie nachodzi na chipy ani „Trudność”.
- Akcja celu: dotknięcie celu w menu otwiera mapę z panelem etapu i obwódką Trudnego,
  a Wstecz wraca do menu (asercje „brak martwych końców” z `FirstHourJourneyBase`).
- `ScreenshotTest.regression100` (`phone720_reg100_campaign`, `foldOpen_reg100_campaign`) bez zmian,
  bo tamte profile nie mają koron. Jeśli wzorzec się zmieni, sprawdź przyczynę.
- `LTest`: nowe klucze mają 10 tłumaczeń.

## Kryteria odbioru

- Wygrana etapu na Trudnym od razu daje koronę na mapie i w panelu. Licznik w nagłówku i w menu
  się zgadza. Cel po kampanii prowadzi w 1 dotknięciu do panelu etapu.
- Na 4 ekranach nic nie nachodzi na siebie, a najmniejszy napis ma ≥ 11 sp.
- Bez alokacji w `render()` mapy (`LowEndScreensPerformanceTest`).
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Ciasno na zewnętrznym Foldzie (nagłówek epoki w pionie). Lepiej przenieść licznik do drugiego
  wiersza niż zmniejszać nazwę epoki.
- `NextGoalKind` jest używany w `when` w kilku miejscach. Dopisz gałąź wszędzie, bo kompilator
  pilnuje tylko `when` bez `else`.

## Czego nie ruszać

- Balans poziomów (`CampaignDifficulty`), nagrody, ranking (liczy tylko Normalny), `:sim`, PROTOCOL.
- Kolejność celów R2-01 (misje → badanie → etap → próg tygodnia). Nowy cel jest **ostatni**.
- Zatwierdzony pasek bitwy `compactTop`, celowanie, rząd przycisków wyników.
- Osiągnięcie `campaign_hard` i ID Play Games (R6-01/R9 w toku).
