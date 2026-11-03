# R2-01: „Następny cel” na wynikach bitwy i w menu

**Gałąź:** `claude/r2-01-next-goal` z `origin/integ/tura10`. **Teksty:** tylko nowa `part179()` w `engine/L.kt`
(10 języków: pl, en, de, es, fr, it, pt, uk, nl, tr). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Źródło: `docs/research/R1-backlog.md`, TOP 5 i R1 w sekcji 2.3.
Commity po polsku, wypychaj często.

## Cel

Po każdej bitwie i w menu gracz widzi jedną linię z najbliższym konkretnym celem. Dotknięcie
linii prowadzi prosto do celu. Gracz ma wiedzieć, po co zagrać jeszcze raz, a sesja kończy się
powodem do powrotu.

## Logika (czysta, bez Androida): nowy `app/src/main/java/com/centurywar/meta/NextGoal.kt`

```kotlin
internal enum class NextGoalKind { MISSIONS, RESEARCH, STAGE, WEEKLY_TIER, NONE }
internal class NextGoal(val kind: NextGoalKind, val text: String, val arg: Int = -1)
internal object NextGoals { fun pick(p: Profile, nowMillis: Long, context: Mode?): NextGoal }
```

Priorytet, czyli pierwszy pasujący warunek:
1. **MISSIONS:** `Missions.pendingClaims(p.missionState) > 0` (dzień i tydzień zgodne z
   `Missions.utcDay/utcWeek`, jak w `MenuScreen.refreshMissionBadge`). Tekst: „Odbierz nagrody
   misji: %d”.
2. **RESEARCH:** najtańsze badanie z `Research.defs`, dla którego `p.canBuy(def)`. Elitę
   obsługuje już `EliteUnlock`; jeśli `EliteUnlock.affordable(p)`, wybierz ją jako pierwszą.
   Tekst: „Stać cię na badanie: %s (%d medali)”, nazwa z `L[def.key]`.
3. **STAGE:** `p.unlockedStage()` < liczby etapów i etap nie ma gwiazdki. Tekst:
   „Następny etap %d: %s”, gdzie drugi parametr to pierwsza nowość etapu: modyfikator
   (`L[mod.key]`), pogoda (`L[stage.weather.key]`, jeśli ≠ CLEAR) albo zakres epok
   (`L.f("eras_range", ...)`). Dane z `Campaign.stages[i]` (`sim/.../Modes.kt`).
4. **WEEKLY_TIER:** tylko gdy `context == Mode.ENDLESS` (wyniki Oblężenia tygodnia) albo
   kampania jest ukończona. Najbliższy niezdobyty próg `Weekly.threshold(i)` z maski
   `p.weeklyClaimedFor(week)`. Tekst: „Oblężenie tygodnia: %d fal do %s”.
5. **NONE:** pusty tekst.

Zasady: `pick` nie alokuje w każdej klatce. Wołać przy rozliczeniu bitwy, przy `layout()`,
zmianie języka i co 10 s w menu (ten sam licznik co `missionBadgePoll`). Teksty budować przez
`L.f`/`L.plural` (jest `medals_n`).

## Wyniki bitwy: `screens/BattleResultsPanel.kt`

- `refreshResultTexts()`: dziś `tipText` ma pierwszeństwo „dlaczego przegrałem” (`why_lost`),
  potem elita (`elite141_afford`). **Nie zmieniaj tej kolejności.** Gdy `tipText` jest pusty
  (wygrana albo brak powodu), wstaw `NextGoals.pick(...)`.
  `prepareResultTipLines()` zawinie tekst jak dotąd.
- Zapamiętaj `resultGoalKind` w `BattleScreen` (pole obok `tipKey`). Dotknięcie w obszarze linii
  wskazówki (`drawResults`, ok. wiersza 309 `drawWrappedLines(... resultTipLines ...)`) działa
  tylko dla `kind != NONE`. Nie dodawaj nowego przycisku w rzędzie Od nowa / Powtórka / Dalej,
  bo jego układ jest dopasowany do Folda (noc 3 i noc 8). Dodaj niewidoczny `Button` nad wierszem
  wskazówki z celem dotyku ≥ 48 dp przez `hitSlop` i opisem TalkBack równym tekstowi celu.
- Akcja: MISSIONS → `MissionsScreen`, RESEARCH → `ResearchScreen`, STAGE → `CampaignScreen`,
  WEEKLY_TIER → `ModeScreen(g, Mode.DAILY, true)`. Zachowanie przycisku Dalej
  (`nextScreen()`, `BattleScreen.kt` ok. 958) bez zmian.

## Menu: `screens/MenuScreen.kt`

- Kafel Kampanii ma dziś `campaign.sub` = „gwiazdki/108” albo `first_goal` przed samouczkiem
  (`update()`, ok. 405–412, budowane po zmianie `subKey`). **Przed samouczkiem zostaje
  `first_goal`.** Po samouczku dodaj **jedną linię celu pod kaflami** w pasie, który nie koliduje
  z `noticeTop..noticeBottom` (tam są banery powiadomień). W układzie pionowym (`portraitMenu`)
  i na zewnętrznym Foldzie linia może się nie mieścić. Wtedy jej nie pokazuj, zamiast zmniejszać
  tekst poniżej 11 sp.
- Gdy cel to MISSIONS, nie dubluj czerwonej plakietki misji. Wybierz następny w kolejności cel.
- TalkBack: `accessibilityLabel` dla nowego przycisku (menu ma węzły z `GameAccessibility`,
  rodzaj `BUTTONS`).

## Kroki

1. `NextGoal.kt` + testy jednostkowe.
2. Wyniki bitwy (wygrana bez wskazówki → cel).
3. Menu.
4. `part179()` (+ `putAll(part179())`; liczba `putAll(part` = liczba `private fun part`).
5. Zrzuty i testy pierwszej godziny.

## Testy do dopisania

- `app/src/test/java/com/centurywar/meta/NextGoalTest.kt`: kolejność priorytetów, brak celu
  po ukończeniu wszystkiego, poprawne badanie najtańsze, etap z pogodą i modyfikatorem, próg
  tygodnia z maską.
- Rozszerzyć `screens/FirstHourJourneyBase.kt`: po każdej **wygranej** wynik ma niepusty cel,
  który nie nachodzi na przyciski wyników ani na baner. Dotknięcie celu otwiera właściwy ekran,
  Wstecz wraca do menu (asercje „brak martwych końców” już są).
- `PrereleasePolishTest` (+ warianty Fold/20x9): napis celu ≥ 8,5 dp, nieucięty, w pl/en/de.
- `LTest`: nowe klucze mają 10 tłumaczeń.

## Kryteria odbioru

- Wygrana pokazuje cel, porażka nadal pokazuje „dlaczego przegrałem”.
- Cel prowadzi w 1 dotknięciu do ekranu celu. Na telefonie 720p, 20:9 i obu ekranach Folda nic
  nie nachodzi na siebie (zrzuty `pre10_*`, `fh_*`).
- Bez alokacji w pętli menu (istniejący `LowEndScreensPerformanceTest` < 16 KiB / 600 klatek).
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Kolizje z dymkami `HintOverlay` (np. `FIRST_BATTLE` na mapie) i banerem osiągnięć. Test pierwszej
  godziny sprawdza najwyżej jedną nakładkę naraz.
- Mało miejsca na zewnętrznym Foldzie. Lepiej ukryć linię niż ściskać tekst.

## Czego nie ruszać

- Zatwierdzony układ paska bitwy `compactTop`, rząd przycisków wyników, kolejność „dlaczego przegrałem”.
- Balans, `:sim`, PROTOCOL, nagrody. To zadanie tylko z UI.
- Powiadomienia nadal nie pokazują się na wynikach bitwy (decyzja właściciela). Cel to wiersz
  panelu wyników, nie baner.
