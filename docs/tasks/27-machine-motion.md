# Zlecenie 27: Płynny ruch maszyn i zwierząt

**Gałąź robocza:** `gpt/27-machine-motion` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Maszyny i zwierzęta mają poruszać się tak płynnie jak piechota po ostatniej poprawce: płynny chód koni, słoni, mamutów i mechów z wygaszaniem kroku, koła i gąsienice obracające się zgodnie z prędkością, odrzut i kołysanie dział i czołgów przy strzale oraz ciągły cykl ataku machin (przygotowanie w trakcie odnowienia).

## Stan obecny (sprawdzone w kodzie)
- Piechota (`app/.../game/art/Soldiers.kt`) ma już płynność: `var stride` (0..1, l. ~25) miesza fazę kroku z postawą stojącą w `leg()` (l. ~215–230), a `var windup` (l. ~30) daje przygotowanie ataku w trakcie odnowienia. Ustawia je `BattleRenderer.drawUnit` (l. ~429–437): `men.stride = mix(prevStride, stride)`, `men.windup = ((cool - u.cd - 0.25) / (cool - 0.25))` gdy jednostka stoi i ma odnowienie.
- `art/Machines.kt` **nie używa `stride` ani `windup`** (poza jeźdźcami rysowanymi przez `men.draw`):
  - `horseLeg` (l. ~81), `eleLeg` (l. ~179), `mechLeg` (l. ~520): `moving = walk != 0f`, więc nogi skaczą w jednej klatce między pełnym krokiem a postawą; `eleLeg` nie podnosi stopy (brak `lift`); stojący mech ma sztywne ±0,16 rad.
  - Renderer podaje `walk = 0` przy `stride <= 0.01` (`drawUnit`, l. ~430), więc przy zatrzymaniu **koła wracają skokowo do kąta 0, a ogniwa gąsienic przeskakują** (`wheel(..., -walk * k)`, `tracks()` z `(walk * 6f) % step`, `roadWheel` z `walk * 2f`).
  - Obrót kół nie wynika z prędkości: `u.anim` rośnie o `v * dt * 0.09` (`Battle.updateUnit`, l. ~877), a kąt koła to `walk * stała` bez uwzględnienia promienia i `def.size` (np. armata: `-walk * 0.8` przy `r = 16`, `size = 1.75` daje ok. 0,072 rad na jednostkę świata zamiast ~0,036 dla toczenia bez poślizgu; `roadWheel` ok. 2× za szybko).
  - Odrzut przy strzale: `cannon` `rec = atk * 7f`, `heavyTank` `atk * 6f`, `ironclad` `atk * 5f`, `apc`, mechy `atk * 4..5f` – tylko liniowe przesunięcie lufy, bez cofnięcia i kołysania kadłuba; `trebuchet` ramię tylko w trakcie `atk` (spoczynek −28°), `scorpion` cięciwa tylko przy `atk`.
  - `hover` (l. ~636) i pulsowanie mechów liczone z globalnego `time`, więc wszystkie jednostki danego typu bujają się w identycznej fazie.
- Jednostki (indeks epoki: 0 plemienna … 8 orbitalna): koń `Defs.unit(3, HEAVY)` (rycerz), `unit(4, HEAVY)`; rydwan `unit(1, HEAVY)`; słoń bojowy `unit(1, ELITE)`; mamut/tusker `unit(0, ELITE)`; skorpion `unit(2, ELITE)`; trebusz `unit(3, ELITE)`; armata `unit(4, ELITE)`; kroczący mech parowy `unit(5, HEAVY)`; pancernik lądowy `unit(5, ELITE)`; półgąsienicowy `unit(6, HEAVY)`; ciężki czołg `unit(6, ELITE)`; transporter `unit(7, HEAVY)`; mech `unit(7, ELITE)`; poduszkowiec `unit(8, HEAVY)`; tytan `unit(8, ELITE)`. Rozmiary: `HEAVY` 1,3, `ELITE` 1,75 (`Defs.buildEra`).
- Galeria: `ArtGalleryTest.animationStrips` (l. 80–104) – 4 wiersze piechoty, 16 klatek (0–5 marsz i wygaszanie kroku, 6–11 odnowienie, 12–15 atak) przez `BattleRenderer.drawUnitPose(..., walk, stride, atk, windup)`, który dziś ustawia tylko `men.stride/men.windup`.

## Zakres
1. **Wspólny stan animacji:** w `Machines` pola `stride` (0..1), `windup` (−1 lub 0..1) i `phase` (indywidualne przesunięcie fazy, np. z `u.serial`), ustawiane w `BattleRenderer.drawUnit` i `drawUnitPose` tak jak dla `Soldiers` (oba moduły z tych samych wartości). Bez nowych pól w `:sim` (wystarczą `anim`, `prevAnim`, `stride`, `prevStride`, `cd`, `attackAnim`).
2. **Ciągła faza ruchu:** renderer przekazuje do maszyn ciągłą, interpolowaną fazę (`mix(prevAnim, anim)`) także przy `stride = 0`, a o tym, czy nogi idą, decyduje `stride` (nie `walk != 0`). Koła i gąsienice nie mogą przeskakiwać po zatrzymaniu.
3. **Chód zwierząt i mechów:**
   - `horseLeg`: cykl czteronożny (przesunięcie faz przód/tył i lewa/prawa jak w stępie/kłusie), podnoszenie kopyta, lekkie kołysanie tułowia i łba w rytm kroku; wygaszanie do postawy stojącej przez `stride`. To samo dla rydwanu (koń + koła).
   - `eleLeg` (słoń, mamut): wolny, ciężki krok z podnoszeniem stopy i ugięciem, kołysanie tułowia i trąby, uszy/sierść opcjonalnie.
   - `mechLeg`: krok z fazą podparcia (stopa „przyklejona” do ziemi, bez ślizgania – jeśli to trudne, przynajmniej bez widocznego ślizgu przy typowej prędkości), lekkie opadanie kadłuba przy stąpnięciu; płynne przejście do postawy.
4. **Koła i gąsienice zgodne z prędkością:** kąt = przebyta droga / (promień × `def.size`) (z `anim / 0.09` = droga w świecie, z uwzględnieniem kierunku strony), przesunięcie ogniw gąsienicy = droga / `def.size`. Ujednolić `wheel`, `roadWheel`, `tracks` i koła transportera. Opisz w PR przyjętą skalę.
5. **Strzał i kołysanie:** odrzut z krzywą (szybkie cofnięcie, wolny powrót) dla lufy oraz kadłuba (`cannon`: laweta podskakuje i cofa się, `heavyTank`/`ironclad`/`apc`: pochylenie kadłuba o 1–3° wokół tylnego koła i powrót z lekkim przeregulowaniem; mechy: krok w tył / ugięcie). W ruchu lekkie kołysanie kadłuba pojazdu zależne od fazy (nierówności terenu), wygaszane przez `stride`.
6. **Cykl ataku machin (`windup`):** trebusz – ramię płynnie wraca do pozycji naciągu w trakcie odnowienia (bez skoku na −28°); skorpion – cięciwa się napina; armata – lekkie uniesienie lufy przed strzałem; zwierzęta bojowe – wzniesienie łba/trąby przed uderzeniem. Wartość `windup` liczona jak dla piechoty.
7. **Faza indywidualna:** bujanie `hover` i pulsowanie mechów z przesunięciem fazy na jednostkę, żeby grupa nie ruszała się synchronicznie.
8. **Jakość:** na LOW (`!p.hi`) można pominąć kołysanie kadłuba i drobne detale ruchu, ale nie wygaszanie kroku ani obrót kół. Zero alokacji.
9. **Galeria:** rozszerz `animationStrips` o wiersze: koń z jeźdźcem, rydwan, mamut, słoń, trebusz, armata, mech parowy, ciężki czołg, mech, poduszkowiec (te same 16 klatek: marsz, wygaszanie, odnowienie, atak; ewentualnie zwiększ liczbę klatek). Rozważ podział na dwa pliki (`gallery_animation.png` piechota, `gallery_animation_machines.png`), jeśli obraz robi się za duży.

## Kryteria akceptacji
- Brak skoków nóg, kół i gąsienic przy ruszaniu i zatrzymaniu (widoczne w galerii w klatkach 0–6 każdego wiersza).
- Koła nie „ślizgają się” wizualnie: obrót odpowiada przesunięciu (opis i ewentualne zdjęcie w PR).
- `ReplayTest`, `BattleSnapshotTest`, `deterministicForSameSeed` przechodzą bez zmian; brak zmian w `:sim`.
- Wygląd spójny z obecnym (płaskie kształty, ciemny kontur, kolor drużyny dominuje); zrzuty `ScreenshotTest` 720p i Fold bez regresji.
- Zero alokacji w rysowaniu.

## Pliki
`app/src/main/java/com/centurywar/game/art/Machines.kt`, `art/Soldiers.kt` (tylko jeśli wspólne pomocnicze krzywe), `app/.../game/BattleRenderer.kt` (`drawUnit`, `drawUnitPose`, `drawBody`), `app/src/test/java/com/centurywar/game/ArtGalleryTest.kt`.
