# R2-03: Dostępność z ustawień systemu i wolne tempo ×0,5

**Gałąź:** `claude/r2-03-system-a11y` z `origin/integ/tura10`. **Teksty:** tylko nowa `part181()`
w `engine/L.kt` (10 języków). **Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`.
Źródło: `docs/research/R1-backlog.md`, TOP 3 i sekcja 3. Commity po polsku, wypychaj często.

## Cel

1. Przy **pierwszym uruchomieniu** rozmiar tekstu gry wynika z systemowej skali czcionki.
2. Gdy w systemie wyłączono animacje, gra od początku ogranicza wstrząsy i błyski.
3. Nowa opcja **tempa ×0,5** w Dostępności (cykl ×½ → ×1 → ×3 → ×5), proponowana, gdy działa TalkBack.

Ręczny wybór gracza zawsze ma pierwszeństwo i nie jest nadpisywany przy kolejnych uruchomieniach.

## Stan w kodzie

- `meta/Profile.kt` ok. 262–292: `textScale` (100/115/130, setter zaokrągla), `reduceShake`,
  `reduceFlash`, `screenShake`, `leftHandedHud`. Zapis `toJson` ok. 519–524, odczyt ok. 992–997,
  `resetSettings`/`reset` ok. 1063. `hasExistingSave` ustawia `load()` (ok. 1090), więc pozwala
  rozpoznać świeżą instalację.
- `engine/Game.kt`: `init` (ok. 83) i `applyLoadedProfile()` wołają `applyAccessibility()`
  (ok. 373: `Palette.select`, `ui.textScale = profile.textScale / 100f`, `layout()`).
- `MainActivity.onConfigurationChanged` (ok. 900): `fontScale` jest w `configChanges`, ale
  wartość nie jest czytana.
- `screens/AccessibilityScreen.kt`: przyciski `sizes` (30–32), `shake` (41), `flash` (42),
  `leftHud` (43); `onClick` ok. 228–238 woła `g.applyAccessibility()`.
- Tempo: `BattleScreen.speed: Int` (1/3/5), przełączanie ok. 893
  (`SPEED, PAUSE_SPEED -> speed = when (speed) { 1 -> 3; 3 -> 5; else -> 1 }`),
  `effectiveSpeed = min(speed.toFloat(), allowedSpeed...)` ok. 596, etykiety `speedLabels`
  (ok. 744, indeks = `speed`), HUD `BattleBottomHud.refreshButtons` ok. 219 i 244, dymek
  `tip53_speed` ok. 801. Tempo zmienia tylko odtwarzanie: krok `Battle.STEP` stały, komendy
  nagrywane według kroku, więc powtórki i ranking są niezależne od tempa.

## Zmiany

1. **Profile:** `var textScaleManual = false`, `var motionManual = false`,
   `var slowSpeedOption = false`. Klucze opcjonalne (`optBoolean`, nie w `mandatory`). Wysyłać
   do chmury jak inne ustawienia, czyli nie dodawać ich do `remove()` w `cloudJson`.
   `AccessibilityScreen.onClick` ustawia `textScaleManual = true` przy 30–32 i
   `motionManual = true` przy 41/42. Reset ustawień zeruje flagi.
2. **Odczyt systemu (wątek UI), nowy `engine/SystemAccessibility.kt`:**
   ```kotlin
   internal class SystemA11y(val fontScale: Float, val animationsOff: Boolean, val touchExploration: Boolean)
   internal object SystemAccessibility { fun read(context: Context): SystemA11y }
   ```
   - `fontScale = context.resources.configuration.fontScale`,
   - `animationsOff = !ValueAnimator.areAnimatorsEnabled()` (API 26+), dodatkowo
     `Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f`,
   - `touchExploration = AccessibilityManager.isTouchExplorationEnabled`.
   Wywołać w `MainActivity.onCreate` (po wczytaniu profilu) i w `onConfigurationChanged`,
   a wynik przekazać przez `game.post { game.applySystemAccessibility(snapshot) }`.
3. **`Game.applySystemAccessibility(s)`** (wątek gry):
   - jeśli `!profile.textScaleManual`: `fontScale ≥ 1.27 → 130`, `≥ 1.12 → 115`, inaczej 100;
     przy zmianie `applyAccessibility()`;
   - jeśli `!profile.motionManual && s.animationsOff`: `reduceShake = true`, `reduceFlash = true`;
   - `touchExplorationActive = s.touchExploration` (pole w `Game`, dla bitwy). Bez zapisu
     profilu w każdej klatce, zapis tylko przy faktycznej zmianie.
   - Dotyczy też istniejących profili, które nigdy nie zmieniały tych opcji. To celowe, bo
     wartości domyślne tylko zgadywały ustawienia systemu.
4. **Tempo ×0,5:**
   - Wartość `speed = 0` oznacza ×0,5. Funkcja `speedFactor(speed) = if (speed == 0) 0.5f else speed.toFloat()`
     zastępuje `speed.toFloat()` w ok. 596 i 631. `speedLabels[0] = "×½"`, a `limitedSpeedLabels`
     sprawdzić dla indeksu 0.
   - Cykl przy `profile.slowSpeedOption`: `0 → 1 → 3 → 5 → 0`, bez opcji bez zmian.
   - Opcja w `AccessibilityScreen` (nowy przycisk, id 44, wiersz jak `leftHud`). Gdy
     `g.touchExplorationActive` i opcja jeszcze nie była ustawiona ręcznie, bitwa startuje
     z `speed = 0` i opcją widoczną w dymku `tip53_speed`.
   - Nie zapisywać tempa w zrzucie bitwy, jeśli dziś nie jest zapisywane (sprawdzić
     `BattleSnapshot` i odczyt). Format zrzutu się nie zmienia, więc `BattleSnapshot.VERSION`
     zostaje 19.
5. **Teksty `part181()`:** `a11y181_slow`, `a11y181_slow_d`, `a11y181_from_system`
   (podpis „Ustawione według systemu”, `RowNote` pod rozmiarem tekstu, gdy `!textScaleManual`).

## Testy do dopisania

- `engine/SystemAccessibilityTest` (Robolectric, `@Config(fontScale = 1.3f)` albo
  `RuntimeEnvironment.setFontScale`): świeży profil → 130; profil z `textScaleManual` → bez zmian;
  `ANIMATOR_DURATION_SCALE = 0` → `reduceShake`/`reduceFlash` na `true`, chyba że `motionManual`.
- `meta/ProfileTest`: nowe klucze wczytywane z wartościami domyślnymi ze starego JSON;
  `toCloudJson` zawiera `slowSpeedOption`.
- `screens/BattleScreen`-owy test tempa: przy opcji cykl ½→1→3→5, `acc` rośnie o połowę;
  bez opcji cykl jak dziś. Złote powtórki bez zmian (`:sim:test`).
- Zrzuty: w `PrereleasePolishTest` sprawdzić, czy jest przebieg z `textScale = 130`. Jeśli nie,
  dodać jeden dla HUD i Dostępności, bez nowych wzorców w `regression100`.
- `GameAccessibility164Test`: nowy przycisk ma opis i stan zaznaczenia.

## Kryteria odbioru

- Telefon z czcionką systemową „duża” przy pierwszym uruchomieniu ma 115% lub 130% bez wchodzenia w ustawienia.
- Ręczna zmiana w Dostępności jest trwała po restarcie i przy zmianie czcionki systemowej.
- Tempo ×½ działa w kampanii, potyczce, Endless i wyzwaniu dnia. Powtórki są zgodne bajt w bajt.
- Pełne testy zielone, bez zmian PROTOCOL i złotych powtórek.

## Ryzyka

- Tekst 130% przy pierwszym uruchomieniu odsłoni ucięte napisy na zewnętrznym Foldzie, czyli na
  ekranach, których zrzuty były robione głównie przy 100%. Przejrzyj zrzuty `pre10_foldOuter_*`
  przy 130%.
- `ANIMATOR_DURATION_SCALE` bywa 0 na telefonach testowych z opcjami programisty. To spodziewane.

## Czego nie ruszać

- Zatwierdzony `compactTop`, układ HUD (`BattleBottomHud.layout`), tryb leworęczny.
- Balans, `:sim`, PROTOCOL, kopie zapasowe (`res/xml/*_rules.xml`).
- Palety (`Palette.kt`): bez nowych kolorów w tym zadaniu.
