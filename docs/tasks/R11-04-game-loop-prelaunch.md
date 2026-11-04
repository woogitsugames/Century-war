# R11-04: Pętla testowa gry (Game Loop) dla raportu przedpremierowego Play i Test Lab

**Gałąź:** `claude/r11-04-petla-testowa` z `origin/integ/tura10`.
**Teksty:** raczej brak (pętla nie pokazuje nowych napisów). Gdyby były potrzebne, tylko `part199()`
w `engine/L.kt` (10 języków). **Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`.
Źródło: `docs/research/R11-backlog.md`, TOP 4. Commity po polsku, wypychaj często.

## Cel

Raport przedpremierowy Google Play uruchamia każdą wersję wysłaną na tor testowy na kilku
prawdziwych telefonach i zgłasza awarie, ANR, problemy z wydajnością i dostępnością. Pomoc Play
pisze wprost, że dla gier rysowanych na własnym płótnie trzeba dostarczyć pętlę gry. Bez niej
robot klika losowo po `SurfaceView` i do bitwy prawie nie dociera. Pętla korzysta ze standardu
Firebase Test Lab: intencja `com.google.intent.action.TEST_LOOP`, numer scenariusza w intencji,
plik wyników pod adresem z intencji.

Po zmianie: każda wersja testowa przechodzi na urządzeniach Google 4 scenariusze, w tym
3 minuty prawdziwej bitwy AI kontra AI przy ×3 na tanim telefonie. Wyniki (klatki, jakość,
pamięć) widać w raporcie i w pliku JSON. Awarie i przycięcia na tanich telefonach znajdziemy
przed testerami zamkniętymi, a nie z ich ocen.

Źródła: [Pomoc Play: raport przedpremierowy](https://support.google.com/googleplay/android-developer/answer/9842757),
[Firebase Test Lab: Game Loop](https://firebase.google.com/docs/test-lab/android/game-loop).
Przed scaleniem sprawdź w Play Console (Testowanie → Raport przedpremierowy → Ustawienia),
jak dziś wskazuje się pętlę gry, i opisz to w `docs/RELEASE.md`.

## Stan w kodzie

- `AndroidManifest.xml`: `MainActivity` z filtrami MAIN/LAUNCHER i VIEW (pliki `.cwr`).
- `MainActivity.kt`: `onCreate` (ok. 112) czyta `replayUri(intent)`, wątek startu tworzy
  `Profile(File(root, "profile.json")).load()`, `PlaytestLog`, `SnapshotStore(File(root, "battle.snap"))`,
  `CrashReports.pendingFatal`, `Quality.detect`; `onNewIntent` (356).
- `engine/Game.kt`: ekrany (`setScreen`, `push`), `frameMetrics`, regulator jakości
  (`QualityGovernor`), `fps`, `reportSimSteps`.
- `engine/FrameMetrics.kt`: okno klatek, `p95Ms()`.
- `screens/BattleScreen.kt`: konstruktor z `BattleConfig`; tempo (`speed` 1/3/5).
- `sim/.../Battle.kt`: `playerAi` (AI za gracza; używa go `BalanceReport` i testy).
- `crash/PlaytestReportText.kt`, `meta/PlaytestLog.kt`: format raportu testera (wzorzec pól).
- `StartupTimings` (`CWStartup`): fazy zimnego startu.

## Zmiany

### 1. Manifest
```xml
<intent-filter>
    <action android:name="com.google.intent.action.TEST_LOOP" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/javascript" />
</intent-filter>
<meta-data android:name="com.google.test.loops" android:value="4" />
```
Opcjonalnie etykiety `com.google.test.loops.*` zgodnie z dokumentacją Test Lab. Filtr w tej
samej `MainActivity` (najprostsze) albo w osobnej `TestLoopActivity` z
`android:excludeFromRecents="true"`. Wybierz wariant, który nie zmienia zachowania zwykłego
startu, i uzasadnij wybór w raporcie.

### 2. `testloop/GameLoopRunner.kt` (nowy pakiet)
- Wykrycie: `intent.action == "com.google.intent.action.TEST_LOOP"`, scenariusz
  `intent.getIntExtra("scenario", 1)`, wynik do `intent.data` przez
  `contentResolver.openFileDescriptor(uri, "w")` (brak URI = tylko logcat `CWTestLoop`).
- **Izolacja (najważniejsze):** osobny katalog `cacheDir/testloop/` dla profilu, dziennika testu
  i zrzutu bitwy (`Profile(File(dir, "profile.json"))`, `SnapshotStore(File(dir, "battle.snap"))`),
  czyszczony na starcie pętli. Żadnego zapisu do `filesDir/profile.json`, `history.json`,
  `battle.snap` ani powtórek. Bez logowania Play Games, bez wysyłki wyników na serwer
  (`ScoreClient`), bez planowania powiadomień, bez raportów awarii do kolejki gracza
  (awarie i tak widzi raport Google). Flaga `Game.testLoop = true` blokuje te ścieżki w jednym miejscu.
- Scenariusze (każdy kończy się `finish()` po zapisaniu wyników; limit czasu z zapasem,
  np. 4 min, potem wynik „timeout”):
  1. **Start i menu:** zimny start do pierwszej klatki menu, 20 s w menu (animowane tło),
     wejście i wyjście z Kampanii, Badań, Kodeksu, Ustawień. Pomiar: fazy `StartupTimings`,
     p50/p95 klatki na każdym ekranie.
  2. **Bitwa środkowa:** etap 8 (`Campaign.config`, stały seed), `battle.playerAi = Ai(battle, 0)`,
     tempo ×3, 3 min albo do końca bitwy, potem ekran wyników. Pomiar: p50/p95 klatki,
     najniższy stopień jakości `QualityGovernor`, liczba odrzuconych kroków symulacji,
     pamięć (`Debug.getPss` lub `ActivityManager.MemoryInfo`) na starcie i końcu.
  3. **Endless późny:** Endless od zaawansowanej epoki (`startEra` jak dla fali 20–30, stały seed)
     z AI gracza, ×3, 3 min, z bossem (jeśli łatwo ustawić). Pomiar jak w 2.
  4. **Obrót i wznowienie:** bitwa ×1 30 s → `onPause`/`onResume` (zapis zrzutu do katalogu
     pętli) → wczytanie zrzutu i 30 s gry. Pomiar: czy zrzut się wczytał, czas wczytania.
- Wynik JSON (mały, stały format, wersja pola `v`): `scenario`, `result` (`ok`/`timeout`/`error`),
  `versionName`, `protocol`, `device` (`Build.MODEL`, SDK), `quality`, `frames` (p50, p95, max),
  `qualityMin`, `discardedSteps`, `pssStartKb`/`pssEndKb`, `startupMs` (scenariusz 1),
  `battleTimeS`, `winner`. Bez danych osobowych.
- Pętla nie może zmienić **zwykłego** startu: bez intencji TEST_LOOP ścieżka `onCreate`
  identyczna (test).

### 3. Dokumentacja
- `docs/RELEASE.md`: sekcja „Raport przedpremierowy i pętla gry”: gdzie w Play Console
  włączyć pętlę i liczbę scenariuszy, jak czytać wyniki, jak uruchomić lokalnie
  (`adb shell am start -a com.google.intent.action.TEST_LOOP -n <pakiet>/.MainActivity -t application/javascript --ei scenario 2`)
  i w Test Lab (`gcloud firebase test android run --type game-loop --scenario-numbers 1,2,3,4 ...`,
  jeśli właściciel ma projekt Firebase — **nie wymagaj go**).
- `docs/PERFORMANCE.md`: progi do porównania (te same co testy `LowEndScreensPerformanceTest`)
  i miejsce na wyniki z telefonów Google.
- `docs/store/` / Data safety: bez zmian (nic nie jest wysyłane przez grę).

## Kroki

1. `GameLoopRunner` z izolowanym profilem + scenariusz 1 + test Robolectric (intencja z
   `scenario` i plikiem wyników w katalogu tymczasowym).
2. Scenariusze 2 i 3 (AI gracza, ×3, pomiary).
3. Scenariusz 4.
4. Manifest, R8 (sprawdź `assembleRelease`/próbę R8: klasa pętli i jej wejście nie mogą zostać
   usunięte), dokumentacja, pełne testy, wypchnij.

## Testy do dopisania

- `GameLoopRunnerTest` (Robolectric): dla każdego scenariusza wynik JSON z `result = ok`
  (czasy skrócone przez parametr testowy, np. 5 s zamiast 3 min), `finish()` wywołane.
- Izolacja: przed pętlą zapisz w `filesDir` profil z 500 medalami i zrzut bitwy; po pętli
  oba pliki bajt w bajt bez zmian, brak nowych plików w `filesDir` (poza dopuszczonymi
  przez system), `ScoreClient` i PGS niewywołane.
- Zwykły start (intencja MAIN) nie tworzy katalogu `testloop` i nie ustawia `Game.testLoop`.
- Złośliwa intencja (TEST_LOOP z nieistniejącym scenariuszem, z URI bez prawa zapisu):
  pętla kończy się bez awarii i bez zapisu poza katalogiem pętli.
- `StartupTest`, `LifecycleProfile160Test`, `BattleSettlement156Test` zielone.

## Kryteria odbioru

- `adb shell am start ... TEST_LOOP --ei scenario N` dla N = 1..4 kończy się samodzielnie
  w ≤ 4 min i zostawia poprawny JSON. Prawdziwy profil gracza bez zmian.
- Zwykły start, wznawianie bitwy, import powtórek i profilu działają jak dotąd.
- Bez nowych bibliotek, bez nowych uprawnień w manifeście, Data safety bez zmian.
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Filtr intencji jest eksportowany, więc każda aplikacja na telefonie może uruchomić pętlę.
  Dlatego izolacja jest twarda: nic trwałego, nic do sieci, a wynik trafia tylko pod URI z intencji.
- Na telefonach Google gra startuje z pustym profilem, a samouczek nie może zablokować
  scenariusza. Pętla tworzy profil z `tutorialDone = true` w swoim katalogu.
- Muzyka i dźwięk w pętli: zostaw włączone (to też obciążenie do zmierzenia), ale bez
  przejmowania fokusu audio na stałe.

## Czego nie ruszać

- Zwykła ścieżka startu, profil, zrzuty, powtórki, serwer, PROTOCOL.
- Zatwierdzony pasek `compactTop`, celowanie ostrzału.
