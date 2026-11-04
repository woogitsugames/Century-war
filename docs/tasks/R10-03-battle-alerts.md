# R10-03: Ostrzeżenia w bitwie — sygnały dźwiękowe i znak „baza atakowana” przy krawędzi

**Gałąź:** `claude/r10-03-ostrzezenia` z `origin/integ/tura10`. **Teksty:** tylko nowa `part190()`
w `engine/L.kt` (10 języków; `putAll(part190())`; liczba `putAll(part` = liczba `private fun part`).
**Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`. Źródło:
`docs/research/R10-backlog.md`, TOP 3. Commity po polsku, wypychaj często.

## Cel

Gracz, który patrzy na front, ma usłyszeć i zobaczyć, że dzieje się coś ważnego:
1. baza gracza spada poniżej 50% i poniżej 25% HP;
2. wróg wchodzi w nową epokę;
3. Ostrzał albo Zryw jest znów gotowy po odnowieniu;
4. zaczyna się presja oblężnicza (`battle.time` przekracza `battle.siegeStart`; nie w Endless).

Dziś te zdarzenia słyszy tylko osoba z TalkBack (`GameAccessibility`, ok. 700–760, klucze
`a178_*`). Uderzenie w bazę (`FxBuffer.BASE_HIT` → `Sound.THUMP` w `BattleSfx`, ok. 170) cichnie
z odległością kamery. Na polu nie ma znaku, że baza obrywa poza kadrem.

## Stan w kodzie

- `screens/BattleSfx.kt`: `BattleSfx.play(g, battle, camX, worldVisible)` (wołane z
  `BattleScreen` ok. 710), limity liczby dźwięków na klatkę, `g.sfx(id, vol, gap, pitch, pan, priority)`.
- `engine/SoundSynth.kt`: `VERSION = 3` (cache wyrenderowanych WAV), `COUNT = 56`, `render(id, variant)`,
  `duration(id)`. `engine/Sound.kt`: stałe `Sound.X = SoundSynth.X`.
- `engine/GameAccessibility.kt` 765: `baseBand(hp, max)` (0, 1 = ≤50%, 2 = ≤25%). Logika
  „gotowe po odnowieniu” jest w 733–739 (`evUsed`, `b.abilityReady(p, i)`).
- `sim/.../Battle.kt`: `siegeStart` (300), `abilityReady(side, i)` (888), `player.era`, `enemy.era`,
  `player.baseHp/baseMaxHp`, `endless`.
- `game/BattleRenderer.kt`: `sx(x)` (prywatne, 246), `camX`, `worldVisible`, `drawBase`
  (419; baza gracza przy `s.baseFront`), `fieldTop`, `groundY`.
- `screens/BattleScreen.kt`: `render` (1664), `fieldRect`, dotyk na polu (przeciąganie kamery,
  celowanie `BarrageAiming`), minimapa `mini` (przesuwanie kamery ok. 1152), samouczek `tut`,
  dymki `HintOverlay`.
- Ustawienia: `profile.sound`, `sfxVolume`, `reduceFlash` (Dostępność).

## Zmiany

### 1. Czysty detektor `screens/BattleAlerts.kt`
```kotlin
internal class BattleAlerts {
    /** Zwraca maskę bitów zdarzeń tej klatki; bez alokacji. */
    fun step(b: Battle, nowSim: Float): Int
    var baseHitAt: Float   // czas symulacji ostatniego trafienia bazy gracza (≥ 2,5% maks. HP w 1 s)
    companion object { const val BASE_HALF = 1; const val BASE_LOW = 2; const val ENEMY_ERA = 4;
                       const val ABILITY_READY = 8; const val SIEGE = 16 }
}
```
- Progi bazy jak w `baseBand`. Każdy próg sygnalizuj **raz w dół**. Ponowny sygnał tylko wtedy,
  gdy baza wróci powyżej progu + 10% (naprawa w Endless).
- `ABILITY_READY` tylko po faktycznym użyciu i odnowieniu (jak `evUsed`), nie na starcie bitwy.
  Ostrzał i Zryw razem najwyżej raz na 8 s symulacji.
- Stan inicjowany przy pierwszym `step` i po wznowieniu ze zrzutu (bez fałszywego „baza < 50%”
  po wczytaniu). Detektor nie wchodzi do zrzutu bitwy.
- `GameAccessibility` **zostaje bez zmian** (osobne liczniki, inne zadanie). Wspólną funkcję
  `baseBand` możesz przenieść do `BattleAlerts.companion` i wołać z obu miejsc. Zachowanie
  TalkBack ma się nie zmienić (`BattleAccessibilityTest`).

### 2. Dźwięki (`SoundSynth` / `Sound`)
- Trzy nowe syntezowane dźwięki **na końcu** listy: `ALERT_BASE` (krótki, niski róg alarmowy,
  ok. 0,5 s), `ALERT_ENEMY_ERA` (dwa tony w dół, 0,4 s), `ALERT_READY` (cichy, jasny „dzyń”,
  0,2 s). `COUNT` 56→59, `VERSION` 3→4 (cache WAV odbuduje się raz). Sprawdź `totalWavBytes()`
  i czas generowania (`MAX_GENERATION_BURST_MS`) w istniejących testach dźwięku.
- `BattleSfx` (albo `BattleScreen` obok `BattleSfx.play`): bit → `g.sfx(...)` z `priority = 3`
  dla bazy i epoki wroga, `priority = 1` i `vol 0,45` dla gotowości. Odstęp `gap` ≥ 4000 ms dla
  alarmu bazy. `SIEGE` używa istniejącego `Sound.BOSS` cicho (vol 0,5) albo `ALERT_ENEMY_ERA`,
  wybierz na słuch i opisz w raporcie. Muzyka: `g.music?.duck(0.5f, 0.6f)` tylko przy `BASE_LOW`.
- Żadnych dźwięków, gdy `!profile.sound`. Głośność jak efekty (`sfxVolume`). Nie graj w powtórkach
  (`ReplayScreen`) ani w scenie menu (`MenuBattleScene`).

### 3. Znak „baza atakowana” przy krawędzi pola
- Warunek: `nowSim - baseHitAt < 2,5 s` **i** baza gracza poza kadrem (`sx(baseFront) < fieldRect.left`;
  udostępnij w `BattleRenderer` funkcję `playerBaseOffscreen(): Boolean` albo `screenX(world)`).
- Rysunek: przy lewej krawędzi pola, na wysokości `groundY - 60 dp`, czerwona plakietka 44×44 dp:
  `Icons.SHIELD` lub `Icons.TOWER` + strzałka w lewo; obok aktualne HP bazy w %, gdy ≤ 50%.
  Przy `reduceFlash` bez pulsowania (stała przezroczystość), w przeciwnym razie łagodny puls 2 Hz
  (alfa 160–255). Tryb leworęczny: baza gracza nadal po lewej, więc znak też po lewej.
  Sprawdź, czy przyciski HUD leworęcznego nie zasłaniają pola w tym miejscu (`LeftHandedHudTest`).
- Dotyk znaku (Button z `hitSlop` do 48 dp, pierwszy w `buttons` tylko wtedy, gdy widoczny)
  ustawia kamerę na bazę: `r.camX = -120f` (lewy kraniec, jak przy starcie). Wyłącz automatyczną
  kamerę tak samo jak przy ręcznym przewinięciu. **Nie** może przechwytywać przeciągania przy
  celowaniu ostrzału (`aimingBarrage`), więc w trakcie celowania znak jest ukryty i nieaktywny.
- Pierwszeństwo nakładek: znak ukryty, gdy aktywny jest krok samouczka (`tut?.showing`), dymek
  `HintOverlay`, wybór karty, doktryny lub wariantu, zapowiedź epoki albo pauza.
- TalkBack: opis przycisku `alert190_base_a11y` („Baza atakowana, %d%%. Dotknij, by pokazać
  bazę”). Węzeł przycisku dodaj do drzewa HUD tylko wtedy, gdy znak jest widoczny. Klucz
  struktury zmienia się wtedy najwyżej co 2,5 s (por. R6-M4).

### 4. Teksty `part190()`
`alert190_base_a11y`. Jeśli dodasz opcję w Ustawieniach (decyzja właściciela nr 4 w backlogu),
to także `alert190_setting` i `alert190_setting_d`. **Domyślnie bez nowej opcji.**

## Kroki

1. `BattleAlerts` + testy jednostkowe (bez Androida).
2. Dźwięki w `SoundSynth`, podpięcie w `BattleSfx`.
3. Znak przy krawędzi + dotyk + priorytety nakładek.
4. `part190()`, zrzuty, odsłuch (wyrenderuj 3 nowe WAV do `app/build/sfx-preview/` istniejącym
   narzędziem lub testem, jeśli jest; jeśli nie, krótki test zapisujący WAV, poza `src/main`).

## Testy do dopisania

- `BattleAlertsTest`: sekwencja HP 100→49→24→60 (Endless, naprawa) daje HALF, LOW, a potem nic,
  dopóki HP nie przekroczy 60/35; wróg awansuje 2 razy → 2 zdarzenia; Ostrzał użyty i odnowiony
  → 1 zdarzenie, nie na starcie; presja raz i nigdy w Endless; po „wznowieniu” (nowy detektor
  na bitwie z HP 30%) brak fałszywego zdarzenia.
- Alokacje: `step()` i rysowanie znaku bez alokacji (wzór: istniejące testy alokacji bitwy,
  `LowEndScreensPerformanceTest`).
- Zrzuty `alerts190_*` (telefon 720p, zewnętrzny Fold, układ leworęczny; pl, en, de): znak widoczny,
  nie nachodzi na HUD, minimapę ani pasek górny; ukryty przy kroku samouczka i przy dymku.
- Dotyk: znak przesuwa kamerę do bazy. W trakcie celowania ostrzału przeciągnięcie przez miejsce
  znaku celuje jak dotąd (`BarrageAiming*`).
- Dźwięk: istniejące testy `Sound*`/`SoundSynth*` po podbiciu `VERSION`, długość nowych próbek
  ≤ 0,6 s, brak przesterowania (szczyt ≤ 0,95).
- `FirstHourJourneyTest` i `BattleAccessibilityTest` bez zmian (najwyżej jedna nakładka naraz;
  TalkBack bez zmian).

## Kryteria odbioru

- W bitwie testowej (etap 8, AI) alarm bazy pada najwyżej 2 razy, gotowość najwyżej co 8 s,
  a sygnały nie dublują się z `Sound.BOSS` w tej samej sekundzie.
- Znak pojawia się tylko, gdy baza obrywa i jest poza kadrem, i znika po 2,5 s spokoju.
- Brak regresji zrzutów `regression100` (znak nie pojawia się w tamtych scenach).
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Za dużo dźwięków przy ×3/×5: licz odstępy w czasie symulacji, nie w czasie rzeczywistym, a przy
  ×5 pomijaj `ABILITY_READY`.
- Kolizja znaku z dymkami i samouczkiem: reguła „jedna nakładka naraz” z `FirstHourJourneyBase`.

## Czego nie ruszać

- `GameAccessibility` (komunikaty TalkBack), `:sim`, PROTOCOL, balans.
- Zatwierdzony pasek bitwy `compactTop` i celowanie ostrzału (przeciągnij-puść; celownik omija
  własnych żołnierzy).
- Głośności muzyki i istniejących efektów (decyzja właściciela po odsłuchu), fokus audio.
