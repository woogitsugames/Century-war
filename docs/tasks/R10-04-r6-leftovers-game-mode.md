# R10-04: Domknięcie przeglądu R6 (L1–L4, L6) i tryb gry Androida (oszczędzanie baterii)

**Gałąź:** `claude/r10-04-r6-tryb-gry` z `origin/integ/tura10`. **Teksty:** zarezerwowana `part191()`
w `engine/L.kt`. Użyj jej tylko wtedy, gdy naprawdę potrzebny jest nowy napis (10 języków;
`putAll(part191())`; liczba `putAll(part` = liczba `private fun part`). Raport testera jest
po angielsku (stałe etykiety), więc nie potrzebuje tłumaczeń. **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Źródło: `docs/research/R6-review.md` (tabela
„Niskie”), `docs/reports/r6-02-poprawki.md` (zrobione M1–M6, L7, L8; **L5 pominięte celowo,
nie ruszaj**), `docs/research/R1-backlog.md` §4.2 (Game Mode API). Commity po polsku, wypychaj często.

## Cel

Pięć drobnych, sprawdzonych w przeglądzie usterek i jedno ułatwienie dla tanich telefonów.
Dla gracza:
- szybszy pierwszy ekran po zawieszeniu gry;
- TalkBack włączony skrótem w trakcie gry od razu daje tempo ×½ i czytelny HUD;
- „mniej wstrząsów i błysków” wraca do ustawień gracza, gdy znów włączy animacje w telefonie;
- mapa kampanii nie przycina się po powrocie z tła;
- kliknięcie z czytnika ekranu nigdy nie przepada;
- w trybie oszczędzania baterii (Ustawienia gry w Androidzie / Game Dashboard) gra sama
  ogranicza się do 30 FPS i niższej jakości.

## Zmiany

### L1. Odczyt śladu ANR poza ścieżką pierwszego ekranu
- Dziś `MainActivity` (wątek startu, ok. 144) woła `CrashReports.collectExitReasons(app)` przed
  `runOnUiThread { finishStartup(...) }`. `ExitReasons.collect` czyta ślad ANR
  (`getTraceInputStream`, skan do 2 mln znaków), więc pierwszy ekran czeka.
- Zmiana: przenieś `collectExitReasons` **po** `finishStartup`, na wątek `crash-io` (ten sam
  wykonawca, którego używa `CrashStore`, albo nowy wątek demona o niskim priorytecie). Pytanie
  o raport (`pendingFatal` → `offerCrashReport`) pokaż, gdy zbieranie się skończy, przez
  `runOnUiThread`, tylko jeśli aktywność żyje i **nie trwa bitwa** (`game.current !is BattleScreen`).
  W przeciwnym razie przy następnym `MenuScreen`. Pytanie o awarię Javy z poprzedniej sesji
  (`pendingFatal` bez ANR) zostaje jak dziś.
- `StartupTimings.Phase.CRASH` ma mierzyć tylko szybką część.

### L2. TalkBack przełączony w trakcie gry
- `MainActivity.resumeGame()` woła `postSystemAccessibility()` tylko przy wznowieniu, a
  `onConfigurationChanged` przy zmianie konfiguracji. Dodaj
  `AccessibilityManager.addTouchExplorationStateChangeListener` w `onResume` i
  `removeTouchExplorationStateChangeListener` w `onPause`. Listener woła `postSystemAccessibility()`.
  Podobnie `addAccessibilityStateChangeListener`, jeśli odczyt `touchExploration` od niego zależy.
- `Game.applySystemAccessibility` (ok. 419) już przelicza `touchExplorationActive`. Bitwa w toku:
  tempo ×½ wchodzi tylko wtedy, gdy gracz nie wybrał tempa ręcznie w tej bitwie. Sprawdź
  `slowSpeedEnabled()` i to, jak `BattleScreen` ustawia `speed` na starcie, a jeśli trzeba,
  dodaj `BattleScreen.onSystemAccessibilityChanged()`.

### L3. Automatyczne ograniczenie ruchu wraca razem z animacjami systemu
- `Game.applySystemAccessibility` (ok. 427) włącza `reduceShake`/`reduceFlash`, gdy
  `animationsOff && !motionManual`, ale nigdy ich nie wyłącza.
- Dodaj do `Profile` pole `motionAuto` (opcjonalne, `optBoolean`, **bez** wpisu w `mandatory`,
  zerowane w resecie obok `motionManual` ok. 1125). Ustawiaj je przy automatycznym włączeniu.
  Gdy `!s.animationsOff && motionAuto && !motionManual`, przywróć `reduceShake = false`,
  `reduceFlash = false` i `motionAuto = false`. Ręczna zmiana w Dostępności czyści `motionAuto`
  (tam, gdzie dziś ustawia się `motionManual = true`). Zapis profilu tylko przy zmianie.
- Profil z chmury i import (`applyLoadedProfile`): pole jest lokalne, więc nie wysyłaj go do chmury
  (sprawdź `toCloudJson`). Jeśli dziś trafia tam `motionManual`, zrób tak samo.

### L4. Mapa kampanii po `onTrimMemory`
- `CampaignScreen.onTrimMemory` zeruje `regionPictures` i `pictureWidth = -1`. `render` (ok. 336)
  woła wtedy `rebuildPictures()`, który nagrywa wszystkie 9 epok w jednej klatce.
- Zmiana: `rebuildPictures()` tylko ustala geometrię (`pictureWidth`), a w pętli rysowania
  (ok. 337–348) brakujący `regionPictures[e]` dla **widocznej** epoki jest nagrywany na miejscu
  (najwyżej 2 epoki na klatkę, reszta w następnych klatkach). Wydziel `recordRegion(e)` z
  obecnego `rebuildPictures`. Wygląd bez zmian.

### L6. Kliknięcie z czytnika nie przepada
- `GameAccessibility.tap(screen, button)` (ok. 270): gdy żaden punkt próbny nie trafi w przycisk
  (`firstHit(...) !== button` dla wszystkich `TAP_FX/TAP_FY`), dziś nic się nie dzieje, a
  `performAction` zwraca `true`. Dodaj ostateczność: `screen.onClick(button)` na wątku gry (tak
  samo jak dzisiejsze `tapAt`, przez `game.post` z kontrolą `stillValid`). Zapisz to też do
  dziennika debug (`Log.d`, tylko w buildzie debug), żeby wykryć przycisk zasłonięty innym.
- `tap` ma zwracać `Boolean`. `performAction` zwraca `false` tylko wtedy, gdy przycisk zniknął
  albo jest wyłączony.

### Tryb gry Androida (`GameManager`, R1 §4.2)
- Nowy `app/src/main/res/xml/game_mode_config.xml`:
  ```xml
  <game-mode-config xmlns:android="http://schemas.android.com/apk/res/android"
      android:supportsBatteryGameMode="true" android:supportsPerformanceGameMode="true"
      android:allowGameDownscaling="false" android:allowGameFpsOverride="false" />
  ```
  Wpis w manifeście w `<application>`:
  `<meta-data android:name="android.game_mode_config" android:resource="@xml/game_mode_config" />`
  (API 33+; na starszych bez skutku). Sprawdź `appCategory="game"` (już jest).
- Odczyt (API 31+): `getSystemService(GameManager::class.java).gameMode` w `resumeGame()`
  (tryb może się zmienić, gdy gra jest w tle). Przekaż go do `Game` przez `game.post`.
- `Game`: przy `GAME_MODE_BATTERY` efektywny limit klatek = 30 (`applyFrameRate(30)`, bez zmiany
  `profile.fpsCap`) i jakość nie wyższa niż średnia (`QualityGovernor` / `effectiveQualityLevel`;
  ręczny wybór jakości w Ustawieniach ma pierwszeństwo tylko w górę do średniej). Przy
  `GAME_MODE_PERFORMANCE` i `STANDARD` bez zmian. Po powrocie do trybu standardowego wraca
  `profile.fpsCap`.
- Ustawienia: wiersz FPS pokazuje dziś wybór gracza. Przy trybie baterii możesz dodać dopisek
  „(oszczędzanie baterii)”. Wtedy użyj `part191` z kluczem `gm191_battery`. Jeśli tekstu nie
  dodajesz, `part191` zostaje niewykorzystana.
- Raport testera (`crash/PlaytestReportText.kt`): wiersz `Game mode: standard|battery|performance|unsupported`.
  Sprawdź limit 6000 znaków (`PlaytestReportTextTest`).

## Kroki

1. L6 i L4 (najmniejsze, osobne commity).
2. L3 (profil + test).
3. L2 (listener + test).
4. L1 (przeniesienie zbierania + test kolejności).
5. Tryb gry (+ manifest, raport testera).
6. Dopisz w `docs/PERFORMANCE.md` 3 zdania o trybie gry, a w `docs/research/R6-review.md` przy
   L1–L4 i L6 „zrobione w R10-04”.

## Testy do dopisania

- `GameAccessibility`/`BattleAccessibilityTest`: przycisk przykryty innym przyciskiem w całym
  obszarze dotyku → akcja z czytnika i tak wywołuje `onClick`. Przycisk niewidoczny → `false`.
- `CampaignScreen` (Robolectric): po `onTrimMemory` pierwsza klatka nagrywa ≤ 2 obrazy epok,
  a po kilku klatkach z przewijaniem mapa wygląda jak przed (porównanie zrzutu z tolerancją
  `ScreenshotTest`).
- `SystemAccessibilityTest`: animacje off → `reduceShake`/`reduceFlash` = true, `motionAuto`;
  animacje on → oba false. Ręczna zmiana → `motionManual`, a późniejsze animacje on nic nie
  zmieniają. Odczyt starego profilu bez `motionAuto`.
- Listener TalkBack: symulowana zmiana stanu → `touchExplorationActive` i tempo ×½ w następnej
  bitwie (Robolectric `ShadowAccessibilityManager`).
- `MainActivity`/start (`StartupTimings` albo test jednostkowy z udawanym `ExitReasons`): pierwszy
  ekran powstaje przed zakończeniem zbierania. Pytanie o raport nie pojawia się nad bitwą.
- Tryb gry: test czystej funkcji `effectiveFps(profileCap, gameMode)` i `effectiveQuality(...)`
  oraz wiersz w raporcie testera.
- Pełny zestaw: `PlaytestReportTextTest`, `CrashReports*Test`, `ExitReasons*Test`,
  `QualityGovernorTest`, `LowEndScreensPerformanceTest` zielone.

## Kryteria odbioru

- L1–L4 i L6 zachowują się jak opisano. L5 nietknięte.
- Tryb baterii: 30 FPS i jakość ≤ średnia. Po wyjściu z trybu ustawienia gracza wracają.
- Brak nowych alokacji w pętli klatki. Start bez regresji czasu (`StartupTimings`).
- Data safety i polityka prywatności bez zmian (tryb gry jest lokalny, raport testera wysyła
  gracz ręcznie, jak dotąd).
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- `MainActivity.kt` zmienia też R10-01 (start, `lostBattle`). Zmiany są w innych miejscach, scalaj
  po kolei.
- Robolectric może nie mieć cieni `GameManager`. Logikę trzymaj w czystych funkcjach, a odczyt
  systemu w jednym miejscu z `try/catch` i `Build.VERSION.SDK_INT`.

## Czego nie ruszać

- L5 (okna wydajności w pauzie): pominięte w R6-02 celowo.
- Progi `QualityGovernor` dla CPU/GPU i termiki (R6-02 M3), fokus audio i muzyka (M6).
- Zatwierdzony pasek bitwy `compactTop`, celowanie ostrzału, `:sim`, PROTOCOL.
