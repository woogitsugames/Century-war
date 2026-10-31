# Zlecenie 26: Pociski, ostrzały, eksplozje i potrząsanie ekranem

**Gałąź robocza:** `gpt/26-projectile-fx` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Pociski i ostrzały mają być widowiskowe, ale czytelne i tanie: smugi strzał i kul, dym z luf i spalin, eksplozje z falą uderzeniową, efektowny ostrzał specjalny z widocznym miejscem upadku oraz potrząsanie ekranem z limitem i opcją w Ustawieniach. Bez wpływu na symulację.

## Stan obecny (sprawdzone w kodzie)
- `app/src/main/java/com/centurywar/game/BattleRenderer.kt`:
  - `drawProj` (ok. l. 527–564): pozycja interpolowana (`prevX/prevH` + `lerp`), kąt z `atan2(-p.vh, p.vx)`. „Smugi” tylko stałe w kodzie: `BULLET` linia 16 j., `SHELL` biała kreska, `ROCKET` poświata, `BEAM` linia 52 j. `STONE`, `BOULDER`, `JAVELIN`, `ARROW`, `BOLT`, `BALL`, `PLASMA` bez smugi.
  - `render` (l. 132–160) rysuje tylko pociski z `p.delay <= 0f`, więc pociski ostrzału czekające na start są niewidoczne.
  - `consumeFx` (l. 568–608): `EXPLOSION` = pierścień (`ptype 1`) + błysk (`ptype 2`) + 4 (LOW) lub 9 odłamków + połowa tego dymu; `MUZZLE` = jeden błysk 0,07 s (`value` = typ pocisku, nieużywane); `BARRAGE` = tylko `shake += 0.4`.
  - Potrząsanie: `shake` maleje o `3/s`, `shakeX = sin(time * 70f) * shake * 6f * dp`, tylko w poziomie, doliczane w `sx()`. Progi: eksplozja `value > 1.2` → `+0.6` (max 1,5), `> 0.9` → `+0.15` (max 1). Ostrzał (16 wybuchów po `value = 1.2`) daje więc serię małych wstrząsów, a końcowy wybuch bazy (`value = 3`) duży. **Brak opcji w Ustawieniach** (`SettingsScreen.kt`, `localRows/onlineRows` l. 34–35; jest tylko `vibration`).
  - Dym bazy epoki pary (`drawBase`, l. 373): `(time * 10f).toInt() % 6 == 0` w każdej klatce, więc przy 120 Hz dymi 2× mocniej niż przy 60 Hz.
  - Cząsteczki: struktura tablic, `PMAX = 320`, typy 0 kropka / 1 pierścień / 2 błysk / 3 dym; limit `Quality.maxParticles` (70/160/320), nowe cząsteczki po przekroczeniu są po prostu odrzucane.
- `art/Machines.kt`: spaliny i para to poświaty liczone z `time` (`ironclad` l. ~378, `steamWalker` l. ~542), nie cząsteczki; armaty i pojazdy mają `muzzle()` (l. ~365) przy `atk > 0.72`.
- `sim/.../Battle.kt`:
  - `Projectile`: `x, h, vx, vh, g, t, dur, tx, delay, type, era`, `prevX/prevH` (tylko renderer). Tor: `p.vh -= g * dt; p.h += vh * dt`, więc pozycję sprzed `τ` sekund da się policzyć analitycznie: `x - vx·τ`, `h - vh·τ - ½·g·τ²` (dla `τ ≤ t`).
  - `fireBarrage` (l. ~543–583): 16 pocisków `Defs.spec(era).barrageProj`, start z `h = 950` i `tx - dir * 260`, lot prosty (`g = 0`) 0,85 s, `delay = i * 0.11 + rnd * 0.1`, cel `p.tx` znany od startu, rozprysk 60. Ten sam kod dla ostrzału celowanego (`BARRAGE_AT`) i karty `AUTO_BARRAGE`. Na koniec jedno `FxBuffer.BARRAGE` przy bazie.
  - Wieże (strzał wieży, l. ~794): `turretFire[i] = 0.15f` i `launch(...)`, **bez** `FxBuffer.MUZZLE`.
- `engine/Quality.kt`: `maxParticles`, `parallaxLayers`, `clouds`, `shadows`; `meta/Profile.kt`: nowe klucze nie mogą trafić na listę `mandatory` w `restore` (l. ~306), bo stare profile przestałyby się wczytywać.
- Zlecenie 21 (celowany ostrzał) przelicza dotyk na świat wzorem `worldX = r.camX + (x - shake) / r.k` i dodaje w `BattleRenderer` funkcję typu `worldX(screenX)`.

## Zakres
1. **Smugi pocisków** bez historii i alokacji: w `drawProj` licz punkty wstecz analitycznie z `vx/vh/g` (τ ograniczone do `p.t + lerp * STEP`, żeby smuga nie wychodziła przed lufę); 3–6 odcinków z zanikającą przezroczystością i grubością (jeden `Path` lub kilka `drawLine`). Charakter wg typu: strzała/oszczep/bełt – cienka jasna smuga; kula (`BULLET`) – żółty smugacz; kamień, głaz, kula armatnia – szare kłębki pyłu co kilka klatek (cząsteczki z akumulatorem czasu, nie z licznika klatek); `SHELL` – dymna smuga; `ROCKET` – smuga dymu + płomień; `PLASMA`/`BEAM` – poświata. Jeśli wolisz bufor pozycji (`prevX/prevH`), trzymaj go w renderze jako prealokowane `FloatArray(Battle.MAX_PROJ * N)` z resetem przy `prevX.isNaN()`. LOW: najwyżej jedna linia, bez kłębków.
2. **Dym z luf i spalin:** `MUZZLE` wg `value` (typ pocisku): proch (`BULLET` w epokach prochu i pary, `BALL`) – biało-szary kłąb dryfujący z wiatrem; `SHELL`/`ROCKET` – większy kłąb i podmuch w tył; energia – sam błysk. Wieże: wykryj w rendererze nowy strzał po wzroście `s.turretFire[i]` (prealokowana tablica poprzednich wartości) i dodaj błysk/dym z lufy wieżyczki. Spaliny pojazdów i mechów oraz para z kominów: emisja cząsteczek z akumulatora czasu (niezależna od FPS), częstsza w ruchu; popraw też emisję dymu bazy pary (l. 373).
3. **Eksplozje:** błysk, fala uderzeniowa (spłaszczony pierścień przy ziemi + szybki pierścień w powietrzu), odłamki, kurz przy ziemi i krótki ślad wypalenia na gruncie (pula renderera, np. 16 śladów, znikają po ~3 s; LOW bez śladów). Wygląd zależny od epoki (`Defs.spec(era).art`): kamień/drewno – kurz i kamienie, proch i później – ogień i dym, `ORBITAL` – energetyczny pierścień. Skala z `value`. Opcjonalnie lekki odrzut cząsteczek kurzu od fali.
4. **Ostrzał specjalny:**
   - znacznik miejsca upadku dla każdego pocisku ostrzału (płaski cień/okrąg przy `p.tx`, rosnący i ciemniejący wraz z `p.t / p.dur`; dla pocisków jeszcze czekających (`delay > 0`) – słaby znacznik ostatnie ~0,3 s przed startem),
   - widoczny nalot z góry: smugi z punktu `h = 950`, w `ORBITAL` pionowe wiązki od górnej krawędzi ekranu,
   - efekt rzucenia przy bazie na `FxBuffer.BARRAGE` (błysk/salwa przy bazie, krótki komunikat nie jest potrzebny).
5. **Potrząsanie ekranem:** model „trauma” (amplituda ~ `trauma²`, sumowanie z limitem 1, zanik ~1,5/s), przesunięcie w obu osiach (pion mniejszy) z płynnego szumu liczonego z `time` (bez losowania w klatce), maksimum w `dp` (np. 7 dp poziomo, 4 dp pionowo) niezależnie od rozdzielczości. Źródła: duże eksplozje, ostrzał (jeden wstrząs na rzucenie + małe na impakty z limitem), zniszczenie bazy. Zachowaj spójność przeliczania ekran→świat: dodaj/uzupełnij publiczną funkcję `worldX(screenX)` w `BattleRenderer`, która uwzględnia przesunięcie (tak, żeby zlecenie 21 mogło z niej korzystać).
6. **Ustawienie:** `Profile.screenShake` (Int: 0 wyłączone, 1 słabe, 2 pełne; domyślnie 2; odczyt `optInt` z przycięciem do 0..2, **nie** dodawaj do listy `mandatory`), wiersz w `SettingsScreen` (przełącznik cykliczny jak `quality`, ikona z `Icons`), klucze `screen_shake`, `shake_off`/`shake_low`/`shake_full` w `L.kt` w 10 językach. Test w `ProfileTest` (stary JSON bez klucza → 2, wartość spoza zakresu przycięta).
7. **Budżet cząsteczek:** dodaj priorytet przy pełnej puli (dym i kurz odrzucane najpierw, błyski i odłamki wybuchów mają pierwszeństwo, np. zastąpienie najstarszego dymu). Jeśli na HIGH potrzeba więcej niż 320, zwiększ `PMAX`/`Quality.maxParticles` z uzasadnieniem. LOW: mniej cząsteczek, bez śladów wypalenia i smug z kłębków.
8. **Testy i galeria:** w `ArtGalleryTest` nowy obraz `gallery_projectiles.png` (każdy typ `Proj` w locie ze smugą, eksplozja w 4 fazach, znaczniki ostrzału) dla HIGH i LOW; test Robolectric (np. w `BattleScreenTest` lub nowy `RendererStressTest`), który puszcza bitwę z wielokrotnym ostrzałem obu stron przez ~600 klatek i sprawdza, że liczba cząsteczek nie przekracza limitu i nic nie rzuca wyjątku; zrzut `ScreenshotTest` bitwy w trakcie ostrzału (720p i Fold).

## Kryteria akceptacji
- `ReplayTest`, `BattleSnapshotTest`, `deterministicForSameSeed` przechodzą bez zmian; brak zmian w `:sim` (jeśli jednak potrzebne, to tylko pola tylko-wizualne opisane w PR).
- Zero alokacji w pętli rysowania; emisja dymu nie zależy od liczby klatek na sekundę.
- Przy `screenShake = 0` obraz się nie trzęsie; przy pełnym wstrząs nie przekracza zadanego limitu w `dp` nawet przy kilku ostrzałach naraz.
- Na LOW liczba cząsteczek ≤ `Quality.maxParticles`, a zrzuty LOW są czytelne (pociski widoczne, bez „mgły” z dymu).
- Zrzuty i galeria w PR.

## Pliki
`app/src/main/java/com/centurywar/game/BattleRenderer.kt`, `app/.../game/art/Machines.kt`, ewentualnie `art/Bases.kt` (pozycja lufy wieżyczki), `engine/Quality.kt`, `meta/Profile.kt`, `screens/SettingsScreen.kt`, `engine/L.kt`; testy `app/src/test/java/com/centurywar/game/ArtGalleryTest.kt`, `meta/ProfileTest.kt`, `screens/ScreenshotTest.kt`, nowy test obciążeniowy renderera.
