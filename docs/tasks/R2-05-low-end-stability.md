# R2-05: Stabilność na tanich telefonach: pamięć, przyczyny zamknięć, termika

**Gałąź:** `claude/r2-05-low-end-stability` z `origin/integ/tura10`. **Teksty:** jeśli potrzebne,
tylko nowa `part183()` w `engine/L.kt` (10 języków); raporty techniczne pozostają bez tłumaczeń.
**Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`.
Źródło: `docs/research/R1-backlog.md`, TOP 8 (a–c) i sekcja 4. **Punkt d (fokus audio) jest
wyłączony z tego zlecenia**, bo czeka na decyzję właściciela. Commity po polsku, wypychaj często.

## Cel

1. **Pamięć:** gra oddaje pamięć, gdy system o to prosi (`onTrimMemory`). Dzięki temu rzadziej
   jest zabijana w tle i szybciej wraca.
2. **Diagnostyka:** przy kolejnym uruchomieniu zapisujemy lokalnie przyczynę poprzedniego
   zakończenia procesu (ANR, brak pamięci, błąd natywny) z `ApplicationExitInfo` (API 30+).
   Takie zdarzenia są dziś niewidoczne poza Android vitals.
3. **Termika:** regulator jakości dostaje sygnał o nagrzewaniu, zanim telefon zacznie dławić
   procesor, co ma znaczenie w długim Endless ×5.

Bez nowych bibliotek, tylko API platformy. Bez wysyłki sieciowej, więc Data safety się nie zmienia.

## Stan w kodzie

- `CenturyWarApp.kt` (`onCreate`: `CrashReports.install`, PGS). `MainActivity`: `onCreate` (ok. 98;
  wątek I/O przy starcie, `CrashReports.pendingFatal(app)` ok. 133, `offerCrashReport` ok. 146),
  `onResume` (350), `onPause` (371: muzyka, zapis profilu z limitem 300 ms), `onDestroy` (418).
  Brak `onTrimMemory` w całym kodzie.
- `crash/CrashReports.kt`: `install`, `snapshot`, `save(fatal, exception)`, `pendingFatal`,
  `markOffered` (SharedPreferences `crash_report_offer`), `sharingText`. `crash/CrashStore.kt`:
  `write(fatal, report)`, `rotate()`, `newest(fatalOnly)`, `MAX_CHARS`, do 10 plików.
- `engine/QualityGovernor.kt`: `record(cpuMs, seconds, automatic, fpsCap, maximum, frameIntervalMs, refreshHz)`,
  stopnie `reduction`/`gpuReduction`; wołane z `Game.monitor(dt)` (`engine/Game.kt` ok. 594–600).
  Testy: `QualityGovernorTest`.
- Cache: `screens/CampaignScreen.kt` `regionPictures` + `rebuildPictures()` (ok. 450; rysowanie
  pomija `null`, ok. 332), `game/BaseFlashCache.kt` (`release()` ok. 62, w `BattleRenderer` ok. 176),
  `game/art/Backdrops.kt` (cache tła `cachedEra`…), `screens/EraIntroOverlay.kt` (Picture),
  `engine/RecordedMixer.kt` (`Group.release()`, `releaseAll()`), `engine/Sound.kt` (`release()`).

## Zmiany

### A. `onTrimMemory`
1. `MainActivity.onTrimMemory(level)`: `game.post { game.trimMemory(level) }`.
   Od Androida 14 system dostarcza praktycznie tylko `TRIM_MEMORY_UI_HIDDEN` (20) i
   `TRIM_MEMORY_BACKGROUND` (40). Obsłuż `level >= 20` (lekko) i `>= 40` (mocniej). Starsze
   poziomy `RUNNING_*` traktuj jak 20.
2. `Screen.onTrimMemory(level: Int)` (domyślnie pusto) w `engine/Screen.kt`. `Game.trimMemory`
   woła ją dla bieżącego ekranu i ekranów na stosie.
3. Zwalniane:
   - `CampaignScreen`: `regionPictures.fill(null)`, `pictureWidth = -1`. Przy następnym
     `layout()`/`render()` odbuduj, żeby `rebuildPictures()` było wołane także z `render`, gdy
     brak obrazów, bo inaczej po powrocie mapa będzie pusta.
   - `BattleRenderer`: `baseFlashCache.release()` (odtwarza się sam przy kolejnym błysku;
     sprawdzić).
   - `Backdrops`: unieważnij cache (`cachedEra = -1` itd.).
   - `EraIntroOverlay`: zwolnij Picture, jeśli nieaktywny.
   - Przy `>= 40` w tle: `RecordedMixer` zwalnia odtwarzacze, które nie grają (zachowanie
     wznowienia muzyki bez zmian; test `MusicLifecycle160Test`).
   - **Nie** zwalniaj `SoundPool` ani stanu bitwy.

### B. `ApplicationExitInfo` (API 30+)
1. Nowy `crash/ExitReasons.kt`:
   `fun collect(context: Context, store: CrashStore, prefs: SharedPreferences)`. Wołany na wątku
   I/O przy starcie, obok `pendingFatal`.
   - `ActivityManager.getHistoricalProcessExitReasons(packageName, 0, 5)`. Bierz tylko wpisy
     nowsze niż zapisany `last_exit_ts` w `crash_report_offer`.
   - Zapisuj raport dla: `REASON_ANR`, `REASON_CRASH_NATIVE`, `REASON_LOW_MEMORY`,
     `REASON_EXCESSIVE_RESOURCE_USAGE`, `REASON_INITIALIZATION_FAILURE`. Pomijaj
     `REASON_USER_REQUESTED`, `REASON_EXIT_SELF`, `REASON_CRASH` (Java łapie już
     `UncaughtExceptionHandler`). `REASON_SIGNALED` tylko z `importance == FOREGROUND`.
   - Treść: nagłówek jak `CrashReports.snapshot` (wersja, model, Android) + `reason`,
     `description`, `importance`, `pss`/`rss`, `timestamp`. Dla ANR dołącz początek
     `traceInputStream` (tylko wątek `main` i wątek gry, maks. 8 000 znaków, ucięcie przez
     `CrashStore.MAX_CHARS`).
   - `CrashStore.write(fatal = reason == ANR || reason == CRASH_NATIVE, report)`. Dzięki temu
     istniejące pytanie „wyślij raport” po awarii (`pendingFatal` → `offerCrashReport`) obejmie
     też ANR, bez nowego UI.
2. Na API 26–29 nic nie rób (`Build.VERSION.SDK_INT >= 30`).
3. `docs/store/data_safety.md`: jedno zdanie, że lokalne raporty obejmują też przyczyny
   zakończenia procesu (nadal bez automatycznej wysyłki). Sprawdź zgodność z polityką prywatności.

### C. Termika (API 29+/30+)
1. `MainActivity`: `PowerManager.addThermalStatusListener(executor, listener)` (API 29+) w
   `onResume`, `removeThermalStatusListener` w `onPause`. Wartość do `@Volatile var thermalStatus`
   w `Game`. Na API 30+ dodatkowo co 10 s w bitwie `getThermalHeadroom(10)` (wątek UI albo
   pomocniczy, **nie** pętla gry; wywołanie jest kosztowne i ograniczane przez system).
2. `QualityGovernor.record(..., thermal: Int = 0)` (parametr z wartością domyślną, więc
   istniejące testy się kompilują). Przy `thermal >= THERMAL_STATUS_MODERATE` (2) albo headroom
   ≥ 0,85 obniż jeden stopień **CPU** po 3 s (jak `gpuSlowFor`). Powrót jakości tylko gdy
   `thermal <= LIGHT` i spełnione obecne warunki (10 s zapasu). Dopisz to do `docs/PERFORMANCE.md`.
3. Nakładka FPS (`showFps`) pokazuje `T2` przy statusie termicznym (krótko, bez nowych tekstów).

## Testy do dopisania

- `engine/QualityGovernorTest`: termika `MODERATE` przez 3 s obniża stopień przy zdrowym CPU;
  `NONE` nie zmienia dotychczasowych wyników (istniejące przypadki bez zmian).
- `crash/ExitReasonsTest` (Robolectric `@Config(sdk = [30+])`, `ShadowActivityManager.addApplicationExitInfo`):
  ANR zapisany jako fatal z wycinkiem śladu; `USER_REQUESTED` pominięty; drugi start nie
  duplikuje (znacznik `last_exit_ts`); API 29 nic nie robi.
- Test `onTrimMemory`: kampania po `trimMemory(20)` rysuje się poprawnie po `render` (zrzut
  identyczny z bazowym albo asercja, że `regionPictures` są odbudowane); bitwa po `trimMemory(40)`
  dalej rysuje błysk bazy.
- `LowEndScreensPerformanceTest`, `AllocationTest`: bez nowych alokacji w pętli.
- `MusicLifecycle160Test`, `RecordedMusicTest`: muzyka wraca po `trimMemory(40)` i `onResume`.

## Kryteria odbioru

- `adb shell am send-trim-memory com.centurywar.game RUNNING_LOW` (Android 13 i niżej) albo
  `BACKGROUND` w trakcie gry nie powoduje błędów ani pustych ekranów (próba na telefonie
  właściciela dopisana do `docs/RELEASE.md`, sekcja prób na urządzeniach).
- Wymuszony ANR w buildzie debug (np. ukryta opcja w nakładce FPS albo test) skutkuje
  pytaniem o raport przy następnym starcie.
- Pełne testy zielone. PROTOCOL, złote powtórki i `BattleSnapshot.VERSION` bez zmian.

## Ryzyka

- Odbudowa `Picture` po powrocie może dać krótkie przycięcie klatki. Akceptowalne raz po powrocie z tła.
- Ślad ANR może zawierać nazwy klas innych bibliotek. Bez danych gracza, ale sprawdzić,
  czy nie ma tam ścieżek z nazwą konta (raport tylko lokalny i ręcznie udostępniany).
- Zachowanie termiki różni się między producentami: `getThermalHeadroom` może zwracać `NaN`
  (wtedy ignorować).

## Czego nie ruszać

- **Fokus audio** (`engine/Music.kt`, `AudioFocusRequest` `AUDIOFOCUS_GAIN`), bo to decyzja właściciela.
- Progi `QualityGovernor` dla CPU/GPU (4 s / 3 s / 10 s), poziomy LOW/MED/HIGH, `compactTop`.
- Balans, `:sim`, PROTOCOL, formaty zapisu.
