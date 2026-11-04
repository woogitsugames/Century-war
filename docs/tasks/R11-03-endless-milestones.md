# R11-03: Kamienie milowe w trakcie Endless i Oblężenia tygodnia

**Gałąź:** `claude/r11-03-kamienie-endless` z `origin/integ/tura10`.
**Teksty:** tylko nowa `part198()` w `engine/L.kt` (10 języków: pl, en, de, es, fr, it, pt, uk, nl, tr;
`putAll(part198())`; liczba `putAll(part` = liczba `private fun part`). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Źródło: `docs/research/R11-backlog.md`, TOP 3.
Commity po polsku, wypychaj często.

## Cel

Bieg Endless trwa 30–60 min (mediana porażki: fala 54). Progi Oblężenia tygodnia (Brąz 10,
Srebro 20, Złoto 30), rekord fali (`Profile.bestWave`) i medale gracz poznaje dopiero na
ekranie wyników (`BattleScreen.settleRewards`). Po fali 30 jedynymi wydarzeniami są bossowie
co 10 fal. Gracz nie wie, że Złoto ma już w kieszeni, ani że do rekordu brakuje mu 3 fal.

Po zmianie w bitwie Endless (zwykłym i tygodniowym):
- przy przekroczeniu progu tygodnia: jednorazowa wstęga „Srebro zdobyte! +60 medali po bitwie”,
  krótki sygnał i komunikat TalkBack;
- przy pobiciu rekordu fali: wstęga „Nowy rekord: fala 38!” (raz na bieg, także przy każdej
  kolejnej fali nie powtarzamy);
- od fali `rekord − 5` wiersz „Następna fala…” pod paskiem ma dopisek „· do rekordu: 3”;
- w menu pauzy wiersz „Rekord: fala 41 · Brąz ✓ · Srebro ✓ · Złoto: fala 30” (Oblężenie
  tygodnia) albo „Rekord: fala 41” (zwykłe Endless).

## Stan w kodzie

- `screens/BattleTopBar.kt` ok. 330–375: dla Endless pasek bossa (tytuł, opis, HP) i wiersz
  `nextThemeText` („Następna fala: …”) pod `topBar.bottom`. Numer fali w samym pasku
  (`waveText`, ok. 312) należy do zatwierdzonego układu **`compactTop`, nie ruszać**.
- `screens/BattleScreen.kt`: `settleRewards` (ok. 1476–1492) liczy `Weekly.threshold(i)`,
  `pr.claimWeekly`, `pr.bestWave`; menu pauzy `refreshPauseTexts` (ok. 1634) i rysowanie
  informacji (`pauseInfoX`/`pauseInfoW`, ok. 1692 i 1838).
- `screens/BattleAlerts.kt` (R10-03): czysta klasa zdarzeń, maska bitów, czas symulacji,
  bez alokacji. `screens/BattleSfx.kt`: `playAlerts(g, mask, fast, bossNow)` i dźwięki
  `Sound.ALERT_*` (prewarm).
- `engine/GameAccessibility.kt` ok. 730–746: `announce(text, prio, now)` dla zdarzeń bitwy
  (np. `a178_boss`).
- `meta/Profile.kt`: `bestWave` (236), `weeklyClaimedFor(week)` (552), `claimWeekly`;
  `sim/.../Modes.kt` `Weekly.threshold(i)`, `Weekly.medalReward(i)`, `TIER_COUNT`.
- Wznowienie bitwy (`BattleScreen` z `restored`): stan UI nie jest w zrzucie.

## Zmiany

### 1. Logika (`screens/EndlessMilestones.kt`, nowy plik, czysta klasa)
```kotlin
internal class EndlessMilestones(bestWaveAtStart: Int, claimedMaskAtStart: Int, weekly: Boolean) {
    /** Zwraca zdarzenie tej klatki (NONE / TIER_0..2 / RECORD); bez alokacji. */
    fun step(wave: Int): Int
    val wavesToRecord: Int   // > 0 tylko, gdy rekord istnieje i wave >= best - 5
}
```
- Próg tygodnia ogłaszamy tylko, gdy nie był zaliczony przed bitwą (`weeklyClaimedFor(cfg.weekly)`
  na starcie). Zwykłe Endless nie ma progów.
- Rekord: tylko gdy `bestWaveAtStart > 0` (pierwszy bieg nie ma czego bić) i `wave > bestWaveAtStart`.
  Raz na bieg.
- Wznowienie ze zrzutu: inicjalizacja z bieżącą falą jako „już widzianą”, bez ogłaszania
  progów sprzed wznowienia (ten sam wzorzec co `BattleAlerts.ready`).
- Liczy po zmianie `battle.wave`, nie co klatkę. Nie zmienia nagród: medale dalej daje tylko
  `settleRewards`.

### 2. Wstęga i dopisek (`BattleTopBar`/`BattleScreen`)
- Wstęga 3 s (wjazd 0,25 s, zanik 0,5 s), złota, `ui.ribbon` jak w innych miejscach, **pod**
  wierszem „Następna fala” (pod paskiem bossa, gdy jest boss). Nie zasłania przycisków
  i dymków (`HintOverlay`, `EvolveBanner`); przy otwartym panelu wież/ulepszeń czeka w kolejce.
  Najwyżej jedna wstęga naraz, kolejka 2.
- Dopisek `· do rekordu: N` w `nextThemeText` (przeliczany przy zmianie fali, jak dziś).
  Tekst w `ui.fit` jak dotąd; na zewnętrznym Foldzie dopisek może skrócić się do „· rekord −3”.
- Teksty wstęgi budowane raz przy zdarzeniu, nie co klatkę.

### 3. Dźwięk i TalkBack
- Dźwięk: istniejący `Sound.ALERT_READY` z wyższym tonem albo dźwięk gwiazdki/osiągnięcia,
  priorytet 2, odstęp ≥ 2 s. Bez nowych dźwięków syntezy (`SoundSynth.VERSION` bez zmian).
  Głośność jak efekty (decyzja właściciela przy R10-03).
- TalkBack: `announce(L.f("ms198_tier_a11y", tier), PRIO_NORMAL, now)` i rekord, w tym samym
  bloku co `a178_boss` (osobne liczniki, bez podwójnych komunikatów).

### 4. Menu pauzy
- Jeden wiersz informacji w panelu pauzy Endless (tam, gdzie fala, czas i zabici), przeliczany
  w `refreshPauseTexts` tylko po zmianie. Na zewnętrznym Foldzie mieści się w `pauseInfoW`
  (`ui.fit`, min. 10 sp). Gdy się nie mieści, dwa wiersze.

### 5. Teksty `part198()`
- `ms198_tier` „%1$s zdobyte! +%2$s po bitwie” (nazwa progu z istniejących `weekly_bronze`/
  `weekly_silver`/`weekly_gold`, jeśli pasują gramatycznie; inaczej własne `ms198_bronze` itd.),
  `ms198_record` „Nowy rekord: fala %1$d!”, `ms198_to_record` „do rekordu: %1$d”,
  `ms198_pause_record` „Rekord: fala %1$d”, `ms198_pause_tier_next` „%1$s: fala %2$d”,
  `ms198_tier_a11y`, `ms198_record_a11y`. Liczby fal przez `L.plural`, gdzie potrzebne.

## Kroki

1. `EndlessMilestones` + testy jednostkowe.
2. Wstęga, dopisek, dźwięk, TalkBack.
3. Menu pauzy.
4. `part198()`, zrzuty (telefon 720p, 2400×1080, zewnętrzny Fold; pl, en, de), pełne testy.

## Testy do dopisania

- `EndlessMilestonesTest` (JVM): progi raz; progi zaliczone przed bitwą nie są ogłaszane;
  zwykłe Endless bez progów; rekord raz, tylko gdy `best > 0`; `wavesToRecord` od `best − 5`;
  wznowienie od fali 25 nie ogłasza Brązu ani Srebra.
- Robolectric `EndlessMilestonesScreenTest`: tygodniowe Oblężenie od fali 9 (stan bitwy
  ustawiony jak w testach bossów), przejście na falę 10 → wstęga widoczna, nie nachodzi na
  pasek bossa, przyciski ani `nextThemeText` (prostokąty), znika po ≤ 4 s; nagrody na wynikach
  bez zmian (te same medale co przed zmianą).
- Zrzuty `r11_03_{phone720,foldOuter}_{pl,en,de}_{tier,record,pause}.png`.
- `BattleAccessibilityTest`: komunikat progu pojawia się raz.
- Alokacje bitwy (`LowEndScreensPerformanceTest`, testy alokacji HUD): bez nowych alokacji
  w klatce poza chwilą zdarzenia.
- `LTest`: 10 tłumaczeń.

## Kryteria odbioru

- W Endless i Oblężeniu tygodnia gracz widzi i słyszy zdobycie progu oraz pobicie rekordu
  w chwili, gdy to się dzieje, a w pauzie widzi swój rekord i najbliższy próg.
- Medale, rekord i rankingi liczone jak dotąd (wyłącznie `settleRewards`); zrzuty i powtórki
  bez zmian, `:sim` nietknięty, PROTOCOL bez zmian.
- Pasek górny (`compactTop`) i jego zrzuty bez zmian.
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Kolizja wstęgi z paskiem bossa (fale 10, 20, 30 to jednocześnie bossowie i progi!). Wstęga
  musi stać pod opisem bossa albo poczekać 3 s. Sprawdź oba przypadki na zewnętrznym Foldzie.
- Wstęga nie może przypominać powiadomienia na wynikach (zasada właściciela): po końcu bitwy
  kolejka jest czyszczona.

## Czego nie ruszać

- Nagrody, progi i `:sim` (`Weekly`, `Battle`), format zrzutu i powtórek.
- Pasek `compactTop` i numer fali w nim, celowanie ostrzału.
- Sygnały R10-03 (tylko dopisanie nowego zdarzenia, bez zmiany istniejących).
