# Zlecenie 37: Wydajność: budżet klatki, auto-jakość, testy kroków/s i alokacji

**Gałąź robocza:** `gpt/37-performance-budget` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Gra działa płynnie na tanich telefonach (2–3 GB RAM, 4 słabe rdzenie) także w najcięższych momentach (Endless fala 40+, ×5, pełne armie, ostrzały). Mierzymy czas symulacji i rysowania, pilnujemy budżetu klatki automatycznie i mamy testy, które łapią regresje wydajności i alokacje.

## Stan obecny (sprawdzone w kodzie)
- `engine/GameView.kt`: pętla `loop()` liczy `dt` z `System.nanoTime()`, opcjonalny limit FPS przez `Thread.sleep`.
- `engine/Game.kt` `monitor(dt)`: średnia czasu **między klatkami** (`frameAvg`, zawiera vsync i uśpienie), auto-jakość tylko w dół (`profile.quality == 0`, `fpsCap != 30`, > 22 ms przez 4 s → `setQuality(level - 1)`), nigdy nie wraca w górę. Brak pomiaru czasu CPU `update` i `render` osobno.
- `Game.frame`: przy `profile.showFps` co klatkę `drawText("${fps.toInt()} fps  q…")` – alokacja w każdej klatce.
- `engine/Quality.kt`: `LOW/MED/HIGH` → `baseScale` 0,75/0,9/1, `maxParticles` 70/160/320, `parallaxLayers`, `clouds`, `shadows`; `detect()` z RAM i rdzeni; `renderScale` z limitem 3,9 MPx.
- `screens/BattleScreen.update`: do 20 kroków symulacji na klatkę (`steps < 20`), po przekroczeniu `acc = 0` (gra zwalnia bez informacji).
- Alokacje w pętli rysowania znalezione przy przeglądzie: `BattleRenderer.consumeFx` – `goldStrings.getOrPut(v)` na `HashMap<Int, String>` (boxing `Int` > 127 przy każdym zdarzeniu złota), `L.f("rank", …)` przy awansie; `RankingsScreen.render` formatuje statystyki co klatkę. Sprawdź resztę ekranów.
- `sim/.../Battle.kt`: `MAX_UNITS = 72`, `MAX_PROJ = 160`, pętle po wszystkich jednostkach w ruchu (`updateUnit`: sprawdzanie blokady O(n²)), `frontUnit` w pętli.
- Jest `app/src/main/baseline-prof.txt` (zlecenie 11). Brak testu wydajności i testu alokacji.

## Zakres
1. **Pomiar:** w `Game.frame` czas CPU `update` i `render` (nanoTime, średnie kroczące i 95. percentyl z okna 120 klatek w prealokowanej tablicy), liczba kroków symulacji na klatkę i „zgubione” kroki (gdy limit 20). Nakładka FPS (`showFps`) pokazuje: FPS, ms update/render, p95, jakość, skala, cząstki – teksty z cache (odświeżane 2× na sekundę, bez alokacji).
2. **Budżet klatki i auto-jakość:** decyzje na podstawie czasu CPU (update + render) względem budżetu ekranu (16,6 ms przy 60 Hz; przy 90/120 Hz nie obniżaj jakości tylko dlatego, że nie ma 120 FPS). Stopniowanie w kilku krokach zamiast trzech poziomów: najpierw cząstki i chmury, potem paralaksa i cienie, potem skala bufora (`renderScale` w krokach 0,05 do minimum 0,6). Powrót w górę po 10 s z zapasem ≥ 30% budżetu (histereza, maks. jedna zmiana na 3 s). Ustawienie ręczne jakości blokuje automat jak dziś.
3. **Symulacja przy ×3/×5:** gdy kroki nie mieszczą się w budżecie, zamiast cichego `acc = 0` obniż efektywną prędkość i pokaż ją na przycisku prędkości (np. „×5 → ×3”), wróć, gdy jest zapas. Bez wpływu na wynik bitwy (symulacja deterministyczna, zmienia się tylko tempo).
4. **Usuń alokacje na klatkę** w ścieżce bitwy i menu (min. przypadki wyżej; `goldStrings` → tablica/`SparseArray`-podobna struktura bez boxingu albo zaokrąglanie do kubełków z gotowymi stringami).
5. **Optymalizacje symulacji** bez zmiany wyników (bit w bit): np. lista żywych jednostek per strona utrzymywana przy spawn/śmierci, cache `frontUnit` raz na krok, wczesne wyjścia. Każda zmiana potwierdzona testem identyczności (`BattleSnapshot.encode` po N krokach przed i po – zapisz oczekiwany hash w teście).
6. **Testy:**
   - `SimPerformanceTest` (JVM): kroki/s dla scenariuszy: kampania etap 36 obie strony AI, Endless fala 40 z pełną pulą, potyczka 30 min; raport w PR; asercja minimalna z dużym zapasem (np. ≥ 20 000 kroków/s na CI), żeby test nie był niestabilny; uruchamiany zawsze, raport szczegółowy za zmienną `PERF_REPORT=true`,
   - `AllocationTest` (JVM, `com.sun.management.ThreadMXBean.getThreadAllocatedBytes`): 10 000 kroków `Battle.step()` po rozgrzaniu = 0 B (tolerancja na JIT ≤ kilka KB, uzasadnij); to samo dla `BattleScreen.update` w Robolectric, jeśli wiarygodne (inaczej opisz),
   - `QualityGovernorTest`: logika auto-jakości na sztucznych czasach klatek (spadek, histereza, powrót, blokada przy ręcznym ustawieniu).
7. **Baseline Profile:** dopisz do `baseline-prof.txt` gorące metody nowej ścieżki (jeśli zmieniasz nazwy klas/metod).
8. Instrukcja w `docs/PERFORMANCE.md`: jak mierzyć na telefonie (nakładka, `adb shell dumpsys gfxinfo`), budżety, wyniki testów.

## Kryteria akceptacji
- Wyniki bitew identyczne przed i po (testy identyczności, `BattleSimTest`, `ReplayTest`, `BattleSnapshotTest`, `deterministicForSameSeed`).
- `AllocationTest` i `SimPerformanceTest` przechodzą; tabela kroków/s przed/po w PR.
- Auto-jakość nie oscyluje (test), nie obniża jakości na ekranach 120 Hz przy 60+ FPS, wraca w górę.
- `ScreenshotTest`: nakładka wydajności na phone720 i foldOpen; wygląd przy domyślnej jakości bez zmian.
- `LTest` przechodzi (jeśli dochodzą teksty, np. etykieta zwolnionej prędkości).

## Pliki
`app/.../engine/Game.kt`, `engine/GameView.kt`, `engine/Quality.kt`, `game/BattleRenderer.kt`, `screens/BattleScreen.kt` (prędkość), `screens/RankingsScreen.kt` i inne ekrany z alokacjami, `sim/.../Battle.kt` (optymalizacje bez zmiany wyników), `app/src/main/baseline-prof.txt`, `docs/PERFORMANCE.md` (nowy); testy `app/src/test/java/com/centurywar/game/SimPerformanceTest.kt`, `AllocationTest.kt`, `engine/QualityGovernorTest.kt` (nowe).
