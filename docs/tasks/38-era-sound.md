# Zlecenie 38: Dźwięki epok i miks z muzyką

**Gałąź robocza:** `gpt/38-era-sound` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

---

# Prompt ogólny (wspólny dla wszystkich zleceń)

Pracujesz nad grą **Century War**, oryginalną strategią na Androida typu „wojna na jednym pasie przez epoki”. Nazwy, jednostki, grafika i mechaniki są własne: **nie kopiuj niczego z innych gier i nie umieszczaj w grze ani materiałach nawiązań do innych gier**.

**Repozytorium:** `woogitsugames/century-war` (GitHub). **Gałąź bazowa:** `claude/bold-sagan-vwz5gm`.

## Zanim zaczniesz
1. Sklonuj repo i utwórz swoją gałąź z **najnowszej** gałęzi bazowej: `git fetch origin claude/bold-sagan-vwz5gm && git checkout -b <TWOJA_GAŁĄŹ> origin/claude/bold-sagan-vwz5gm`. Nazwa gałęzi jest podana w zleceniu.
2. Przeczytaj `README.md`, `docs/GDD.md` (projekt gry, plan rozwoju) i `docs/HANDOFF.md` (stan i ryzyka).
3. Struktura kodu (trzy moduły Gradle):
   - **`:sim`** – `sim/src/main/kotlin/com/centurywar/game/`: symulacja bitwy (czysty Kotlin/JVM, bez `android.*`, **deterministyczna**): `Battle.kt`, `Ai.kt`, `AiPersonality.kt`, `Defs.kt` (balans), `Modes.kt` (kampania, tryby, `BattleConfig`, `Bonuses`, `Daily`, `Rng`), `EndlessCards.kt`, `BattleController.kt` (jedyna droga komend gracza, nagrywa log), `Replay.kt` (kodowanie i weryfikacja powtórek), `BattleSnapshot.kt` (zapis i wznawianie przerwanej bitwy, format binarny z wersją). Ten sam kod kompiluje serwer.
   - **`:server`** – `server/`: backend rankingu (Ktor + SQLite), weryfikuje powtórki przez `Replay.verify`; opis API i podpisu HMAC w `server/README.md`.
   - **`:app`** – `app/src/main/java/com/centurywar/`:
     - `game/`: renderer `BattleRenderer.kt` + rysunki w `game/art/`,
     - `engine/`: pętla gry (`GameView`), `Game`, `Screen`, `Ui`, `Icons`, `L` (**teksty w 10 językach**), `Quality`, `Sound`, `Music`,
     - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `Achievements`, `Missions`, `MissionTexts`, `OnlineLeaderboards`, `PlayGamesLeaderboards`,
     - `notifications/`: przypomnienie o wyzwaniu dnia,
     - `screens/`: menu, kampania, tryby (`ModeScreen`), badania, rankingi, ustawienia, kodeks, misje, osiągnięcia, bitwa (`BattleScreen`, HUD), `TutorialOverlay`, `Chrome`, `MenuBg`,
     - `MainActivity.kt`.
   - Testy symulacji i aplikacji są w `app/src/test/java/com/centurywar/...` (także dla kodu z `:sim`), testy serwera w `server/src/test/kotlin/...`.

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek** (wyjątek: `play-services-games-v2`). Dodaj zależność tylko wtedy, gdy zlecenie wprost na to pozwala.
- Wolno edytować wszystkie pliki potrzebne do zlecenia. Zmiany w plikach współdzielonych (`Battle.kt`, `Ai.kt`, `BattleScreen.kt`, `L.kt`, `Profile.kt`, `MainActivity.kt`) rób możliwie małe i addytywne, bo inne zlecenia pracują równolegle.
- **Teksty UI:** dodawaj do `engine/L.kt` w formacie `"klucz" to t("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")`, czyli **wszystkie 10 języków** (kolejność `L.CODES`). Etykiety przycisków krótkie. Test `LTest` sprawdza komplet języków, zgodność placeholderów (`%d`, `%s`) i brak zdublowanych kluczy, więc musi przechodzić.
- **Symulacja musi zostać deterministyczna:** w `sim/` nie używaj `System.currentTimeMillis()`/`nanoTime()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng` (`Rng`, osobne strumienie AI). Testy `deterministicForSameSeed` i `ReplayTest` muszą przechodzić.
- **Komendy gracza** idą wyłącznie przez `BattleController` (inaczej nie trafią do powtórki i serwer odrzuci wynik).
- **Zapis bitwy:** jeśli dodajesz pole stanu do `Battle`/`Side`/`Fighter`/`Projectile`/`Ai`/`BattleConfig`/`BattleController`, dopisz je do `BattleSnapshot.kt` (zapis i odczyt), **podbij `BattleSnapshot.VERSION`** i dodaj przypadek do `BattleSnapshotTest`, który sprawdza, że wznowiona bitwa daje identyczny wynik. Zmiana formatu powtórki = podbij `Replay.VERSION`.
- **Zapis profilu:** nowe pola w `Profile` muszą być kompatybilne wstecz (brak pola w starym JSON = wartość domyślna) i przycinane do sensownych zakresów przy odczycie (patrz `ProfileTest`).
- **Grafika:** rysowana kodem w stylu płaskiego cartoonu z grubym ciemnym konturem. Kolor drużyny jest dominujący (niebieski gracz, czerwony wróg). UI używa `Ui.panel`/`Ui.button`/wstęg i ikon z `Icons`; **nie używaj emoji** (nie wszędzie się renderują).
- **Wydajność:** gra ma płynnie chodzić na tanich telefonach (2–3 GB RAM). W pętli gry i renderowania **zero alokacji** (pule, prealokowane tablice, cache stringów), żadnego I/O ani sieci na wątku gry. Ekrany muszą działać od 720p po rozłożonego Folda (~2184×1968, prawie kwadrat); respektuj `g.insetL/T/R/B` i `ui.dp`.
- Komentarze w kodzie po polsku, zwięzłe. Styl jak w istniejącym kodzie.
- **Bez identyfikatorów modeli AI** w commitach, komentarzach i plikach.

## Budowanie i testy (obowiązkowo przed pushem)
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # jeśli trzeba
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Counter*' --tests '*Endless*' --tests '*AiPersonality*' --console=plain
./gradlew :server:test -PserverOnly=true --console=plain   # gdy zmieniasz :sim lub :server
```
- Testy Robolectric (`ProfileTest`, `BattleScreenTest`, `ScreenshotTest`, `ArtGalleryTest`) pobierają ~200 MB przy pierwszym uruchomieniu. Przy błędach 429 z Maven Central ponawiaj z odstępem albo pobierz artefakt z `https://maven-central.storage-download.googleapis.com/maven2`. Dodaj `--no-configuration-cache`.
- Przy zmianach wyglądu uruchom `--tests '*ScreenshotTest*'` (~2 min). PNG lądują w `app/build/screenshots/`: obejrzyj je (720p i Fold) i dołącz najważniejsze do PR.
- Jeśli dodajesz logikę, **dodaj testy JUnit**.

## Git
- Commituj małymi krokami, wiadomości po polsku (pierwsza linia ≤ 72 znaki).
- Wypchnij **wyłącznie na swoją gałąź** (`git push -u origin <TWOJA_GAŁĄŹ>`). Nie pushuj na `claude/bold-sagan-vwz5gm` ani `main`, nie rób force-push.
- Otwórz Pull Request z Twojej gałęzi **do** `claude/bold-sagan-vwz5gm`, z opisem zmian i zrzutami ekranu, jeśli zmieniasz wygląd.

## Raport końcowy (w opisie PR)
- co zrobiono, lista zmienionych i nowych plików,
- wyniki budowania i testów (wklej końcówkę logu),
- czego nie zrobiono i dlaczego, ryzyka.

---

# Prompt zlecenia
## Cel
Każda epoka brzmi inaczej: syntezowane warianty dźwięków trafień, strzałów, śmierci, wież i ostrzałów dla epok, osobna głośność efektów, a muzyka ustępuje ważnym zdarzeniom (ducking). Bez plików audio, nadal synteza w kodzie.

## Stan obecny (sprawdzone w kodzie)
- `engine/Sound.kt`: 12 dźwięków (`HIT, SHOT, ARROW, EXPLOSION, DEATH, LASER, EVOLVE, COIN, CLICK, VICTORY, DEFEAT, THUMP`), synteza w `synth(id)` do WAV w `cacheDir/sfx/s<i>_v<VERSION>.wav` (generowane w tle przy starcie, `VERSION = 1`), `SoundPool` z `setMaxStreams(10)`, `play(id, vol, minGapMs, pitch)` z ograniczeniem spamu `lastPlay[id]`, `volume = 0.8f`.
- `screens/BattleScreen.playSounds()` (~l. 290): mapowanie zdarzeń `FxBuffer` na dźwięki, głośność od odległości od środka kamery; pociski wg `Proj` (`BEAM/PLASMA` → LASER, `BULLET/SHELL/BALL/ROCKET` → SHOT, reszta → ARROW). Epoka nie wpływa na dźwięk (poza wysokością przy ostrzale).
- `Game.sfx(id, vol, gap, pitch)` gra tylko przy `profile.sound` (wł./wył., brak głośności efektów). Muzyka: `engine/Music.kt` (`setEra`, `setIntensity`, `setVolume`), synteza `engine/MusicSynth.kt` (22 050 Hz), `Profile.musicVolume`, suwak w `SettingsScreen`.
- `FxBuffer` (`sim/.../Battle.kt`): `HIT, DEATH, EXPLOSION, MUZZLE, BASE_HIT, EVOLVE, GOLD, BARRAGE` z polami `x, h, side, value`; brak informacji o epoce/typie jednostki przy `HIT`/`DEATH` (tylko `value` = rozmiar przy DEATH).
- UI: `CLICK` przy przyciskach; brak dźwięków nagród, odblokowań, kart.

## Zakres
1. **Banki dźwięków per styl epoki** (`EraArt`: TRIBAL…ORBITAL; pogrupuj, np. 4 rodziny: plemienne/brąz/żelazo, średniowiecze, proch/para, stal/krzem/orbita): warianty HIT (drewno/brąz/stal/energia), DEATH (człowiek/zwierzę/maszyna), SHOT (proca/łuk/kusza, muszkiet/armata, karabin/działo, laser/plazma), turret fire, BARRAGE per epoka (głazy, strzały ogniste, salwa armat, rakiety, orbitalny promień), EVOLVE w skali zależnej od epoki. Każdy wariant w 2 odmianach (lekka zmiana parametrów) albo z losową wysokością ±5% w `play`, żeby nie brzmiało powtarzalnie. Generowanie leniwe w tle, cache WAV z podbitą `VERSION`, limit czasu generowania i pamięci (podaj łączny rozmiar w KB).
2. **Informacja w zdarzeniach:** jeśli dźwięk potrzebuje epoki/rodzaju ciała, dopisz ją do `FxBuffer` **bez zmiany symulacji** (np. pole `era` wypełniane przy `fx.add`; `FxBuffer` nie jest w `BattleSnapshot` i nie wpływa na wynik). Uzgodnij z zleceniami 25–26, które też korzystają z `FxBuffer` – zmiana addytywna.
3. **Mapowanie** w `BattleScreen.playSounds()` (albo we wspólnej klasie `BattleSfx`, jeśli scalone 29): wariant wg epoki/ciała/pocisku; priorytety głosów (eksplozje i bazy ważniejsze niż trafienia), limit jednoczesnych trafień na klatkę, panorama lewo/prawo wg pozycji na ekranie (`SoundPool.play(left, right)`).
4. **UI i meta:** dźwięki: nagroda medali (rosnące „liczenie”), odblokowanie osiągnięcia, wybór karty Endless wg rzadkości, zakup badania, gwiazdki w wynikach, nowa epoka w menu kampanii. Krótkie, ciche, spójne.
5. **Miks:** `Profile.sfxVolume` (0..1, domyślnie 0,8; kompatybilne wstecz, stare `sound=false` = wyciszone) z suwakiem w `SettingsScreen` obok muzyki (jak `musicVolume`; uzgodnij układ wierszy ze zleceniami 31/32); `Music.duck(amount, seconds)` – płynne ściszenie muzyki przy awansie epoki, ostrzale, bossie, zwycięstwie/porażce (obwiednia w `MusicSynth`/`Music`, bez alokacji w wątku audio); limiter/ograniczenie sumy głośności w krótkim oknie.
6. **Bez zmian w symulacji** (poza addytywnym polem w `FxBuffer`); `:server` nie korzysta z dźwięku.
7. **Teksty** (10 języków): „Efekty”, ewentualnie opisy w ustawieniach.

## Kryteria akceptacji
- `SoundSynthTest` (JVM, wydziel czystą syntezę z `Sound` jak `MusicSynth`): każdy wariant ma długość w limicie, brak przesterowania (|x| ≤ 1), brak NaN, warianty jednej rodziny różnią się widmowo (np. energia w pasmach), łączny rozmiar WAV w limicie; determinizm generowania.
- `MusicSynthTest`: ducking zmniejsza głośność i wraca płynnie (bez trzasków: maks. skok próbki).
- `ProfileTest`: `sfxVolume` przycinane, domyślne dla starego JSON.
- Zero alokacji w `playSounds()` i w wątku muzyki; brak I/O na wątku gry (generowanie w tle). `LTest`, `ReplayTest`, `deterministicForSameSeed` przechodzą.
- `ScreenshotTest`: ustawienia z suwakiem efektów na phone720 i foldPortrait.
- W PR krótkie nagrania lub opis odsłuchu wariantów (np. WAV wygenerowane testem w `app/build/sfx-preview/`).

## Pliki
`app/.../engine/Sound.kt`, `engine/SoundSynth.kt` (nowy, czysta synteza), `engine/Music.kt`, `engine/MusicSynth.kt`, `engine/Game.kt` (`sfx`, głośność), `screens/BattleScreen.kt` (`playSounds`) lub `screens/BattleSfx.kt`, `screens/SettingsScreen.kt`, `screens/ResearchScreen.kt`, `screens/AchievementsScreen.kt`/`engine/Game.kt` (dźwięk odblokowania), `meta/Profile.kt`, `sim/.../Battle.kt` (tylko pole w `FxBuffer`), `engine/L.kt`; testy `app/src/test/java/com/centurywar/engine/SoundSynthTest.kt` (nowy), `MusicSynthTest.kt`, `meta/ProfileTest.kt`, `screens/ScreenshotTest.kt`.
