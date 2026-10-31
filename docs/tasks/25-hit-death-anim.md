# Zlecenie 25: Animacja trafień i śmierci

**Gałąź robocza:** `gpt/25-hit-death-anim` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Trafienie i śmierć jednostki mają być czytelne i „ważyć”: trafiona jednostka krótko się odchyla, a ginąca upada w sposób zależny od typu ciała (człowiek się przewraca, zwierzę osuwa się na bok, maszyna zostaje dymiącym wrakiem). Do tego kurz i odpryski. Wszystko czysto wizualnie, bez wpływu na wynik bitwy.

## Stan obecny (sprawdzone w kodzie)
- `app/src/main/java/com/centurywar/game/BattleRenderer.kt`, `drawUnit` (ok. l. 408–455):
  - śmierć: `alpha = max(0f, 1f - u.dying / 0.8f)` i `c.rotate(-min(80f, u.dying * 200f))` wokół punktu stóp, **dla każdego typu ciała tak samo** (czołg, mech i koń przewracają się jak człowiek, liniowo, bez wyhamowania),
  - trafienie: tylko `pen.flash = u.flash > 0f` (`art/Pen.kt`, `c()`: miesza kolor w 78% z bielą). Brak odrzutu ani zgięcia.
- `sim/.../Battle.kt`:
  - `damageUnit` (ok. l. 965–995) ustawia `u.flash = 0.12f` przy **każdych** obrażeniach, także przy podpaleniu, które `updateUnit` (ok. l. 810–815) zadaje w każdym kroku, więc płonąca jednostka świeci cały czas. `flash` jest zapisywany w `BattleSnapshot` (l. 102 i 254), czyli należy do stanu logiki: nie zmieniaj jego znaczenia.
  - `FxBuffer.HIT` powstaje tylko przy ciosie wręcz w jeden cel (l. ~849) i pocisku bez rozprysku (l. ~939); `x` = cel, `h = 40 * size`, `side` = ofiara, `value = 0`. Rozprysk (`splashDamage`) daje tylko `EXPLOSION` (`side` = atakujący, `value = 0.6 + radius / 100`), podpalenie nic.
  - `FxBuffer.DEATH` (l. ~993): `x`, `h = 0`, `side` = ofiara, `value = def.size`; bez typu ciała.
  - Umieranie trwa w symulacji dokładnie 0,8 s (`updateUnit`, l. ~805–807: `dying > 0.8f` → `active = false`), potem `Fighter` wraca do puli. `spawnFighter` bierze pierwszy wolny obiekt z puli, więc **zmiana czasu umierania zmienia kolejność jednostek i wyniki powtórek**. Nie wolno jej ruszać.
  - Wzorzec pól tylko-wizualnych: `Fighter.prevX/prevAnim/prevAttack/stride/prevStride` (l. 25–33), poza `BattleSnapshot`, zerowane w `spawnFighter` (l. ~750). `Battle.stepCount` jest publiczny.
- `consumeFx` (l. 568–608): `HIT` → 3 żółte kwadraciki, `DEATH` → 6 szarych kłębów dymu (`ptype 3`). Cząsteczki: struktura tablic, `PMAX = 320`, limit `Quality.maxParticles` (70/160/320 dla LOW/MED/HIGH).
- Typy ciał: `Body.HUMAN/BEAST/SIEGE/CANNON/VEHICLE/MECH/HOVER` (`Defs.kt`); `drawBody` (l. 457) rozdziela je na `Soldiers.draw` i `Machines.beast/scorpion/trebuchet/cannon/vehicle/mech/hover`.
- Galeria: `app/src/test/java/com/centurywar/game/ArtGalleryTest.kt`, `animationStrips` (l. 80–104) przez `BattleRenderer.drawUnitPose(c, def, cx, bottom, height, team, walk, stride, atk, windup)` → `app/build/screenshots/gallery_animation.png`.

## Zakres
1. **Dane o trafieniu (minimalne pole tylko-wizualne):** w `Fighter` dodaj `var hitStep = -1` (krok ostatniego bezpośredniego trafienia) i `var hitPow = 0f` (0..1, np. `min(1f, obrażenia / maxHp * 4f)`). Ustawiaj je w `damageUnit` tylko przy trafieniu bezpośrednim (cios, pocisk, rozprysk, odrzut bossa), **nie** przy podpaleniu: np. parametr domyślny `direct: Boolean = true`, a ścieżka `burn` przekazuje `false`. Zeruj w `spawnFighter`. Pól nie zapisuj w `BattleSnapshot`, nie czytaj ich w logice. Komentarz jak przy `prevX`. Jeśli znajdziesz prostszy sposób bez zmian w `:sim`, opisz go w PR.
2. **Reakcja na trafienie** w `drawUnit`: wiek trafienia `age = (b.stepCount - u.hitStep + lerp) * Battle.STEP`; przez ok. 0,18 s odchylenie od strony atakującego (obrót wokół stóp o kilka stopni i przesunięcie 1–3 jednostek lokalnych, z wygaszaniem, skala z `hitPow`). Ludzie: zgięcie tułowia/głowy w tył (np. pole `Soldiers.recoil` 0..1 jak `stride`/`windup`); zwierzęta: szarpnięcie łbem; maszyny: drgnięcie kadłuba bez obrotu. Błysk `pen.flash` zostaje, ale przy podpaleniu (bez świeżego `hitStep`) ma być słabszy: np. pulsujący pomarańczowy odcień zamiast bieli (pole w `Pen`, np. `tint`/`tintAmt`, zamiast drugiego wybielenia).
3. **Śmierć zależna od ciała** (oś czasu 0..0,8 s z `u.dying`, interpolowana: `dying + lerp * STEP`, krzywe z wyhamowaniem zamiast liniowych):
   - `HUMAN`: upadek w tył (od wroga) do ok. 85–90° z małym odbiciem, ugięte nogi (ustaw `stride = 0`, `windup = -1`), broń może wypaść jako cząsteczka; przezroczystość dopiero w ostatnich ~0,25 s.
   - `BEAST`: zwierzę osuwa się (przesunięcie w dół + przechył ok. 15–25°), nogi przestają się ruszać; jeździec (rysowany w `Machines.rider`) spada razem z nim lub odchyla się mocniej.
   - `SIEGE`, `CANNON`: przechył i zapadnięcie konstrukcji, drzazgi (brązowe cząsteczki `ptype 0`).
   - `VEHICLE`, `MECH`, `HOVER`: bez przewracania. Krótki błysk wybuchu na starcie, przyciemnienie do zwęglonych barw (np. `Pen.char` 0..1 mieszający kolor z ciemnym szarym), mech przyklęka/opada do przodu, pojazd unoszący się opada na ziemię.
4. **Wraki i kurz (tylko renderer):** symulacja usuwa jednostkę po 0,8 s, więc wrak maszyny trwający dłużej trzymaj we własnej puli renderera (struktura tablic, np. `WMAX = 12`: x, strona, `def.id`, wiek). Wykrycie śmierci bez zmian w `FxBuffer`: renderer ma `IntArray(Battle.MAX_UNITS)` z numerem `serial` ostatnio widzianej śmierci w danym slocie puli; gdy `u.dying >= 0` i `serial` nowy, dodaj wrak. Wrak: zaciemniona sylwetka (ta sama funkcja rysująca z `char = 1`, z cache w `Picture` na `def.id`, jeśli się opłaca) albo uproszczona kupka blach, z której co jakiś czas leci dym (`ptype 3`) i znika po ok. 2,5 s (LOW: 1 s albo bez wraku, tylko dym). Ludzie i zwierzęta: kłąb kurzu przy upadku (płaskie elipsy przy ziemi), bez pozostawiania ciał.
5. **Odpryski przy trafieniu:** zamiast żółtych kwadracików zależnie od celu: iskry i odłamki metalu dla maszyn, kurz/drzazgi dla drewna, krótki pył dla ludzi i zwierząt. Kierunek od atakującego (`side` ofiary jest w `FxBuffer.side`). Liczbę cząsteczek dziel przez `Quality` (LOW ok. połowa).
6. **Galeria:** rozszerz `animationStrips` (albo dodaj `deathStrips` → `gallery_death.png`) o wiersze: człowiek wręcz, człowiek dystans, koń z jeźdźcem, słoń/mamut, armata, czołg, mech; klatki: trafienie (3–4 fazy) i śmierć (6–8 faz). Do tego potrzebny będzie publiczny wariant `drawUnitPose` z parametrami `hitAge` i `dying` (domyślnie wyłączone, żeby obecne wywołania się nie zmieniły).

## Kryteria akceptacji
- `ReplayTest`, `BattleSnapshotTest` i `deterministicForSameSeed` przechodzą **bez zmian w testach**; `BattleSnapshot.VERSION` i `Replay.VERSION` bez zmian.
- Nowe pola w `Fighter` nie są czytane przez logikę symulacji (sprawdź grepem i opisz w PR).
- Zero alokacji w `render()` (bez `new`, lambd z przechwyceniem, boxingu); limit cząsteczek respektowany na LOW.
- `gallery_death.png` (lub rozszerzony `gallery_animation.png`) pokazuje różną śmierć dla człowieka, zwierzęcia i maszyny; zrzuty `ScreenshotTest` bitwy na 720p i Foldzie bez artefaktów (wraki nie zasłaniają HUD, znikają).
- Płonąca jednostka nie jest stale biała.

## Pliki
`app/src/main/java/com/centurywar/game/BattleRenderer.kt`, `app/.../game/art/Soldiers.kt`, `art/Machines.kt`, `art/Pen.kt`, `sim/src/main/kotlin/com/centurywar/game/Battle.kt` (tylko pola `hitStep`/`hitPow` i parametr `direct`), `app/src/test/java/com/centurywar/game/ArtGalleryTest.kt`.
