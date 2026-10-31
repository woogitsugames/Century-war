# Zlecenie 33: Pogoda i pora dnia na polu bitwy

**Gałąź robocza:** `gpt/33-weather` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Pogoda i pora dnia zmieniają wygląd pola bitwy i w niewielkim, symetrycznym stopniu jego zasady. Bitwy na tym samym etapie wyglądają i grają się różnie, a gracz ma nowy czynnik do uwzględnienia (np. wieże słabsze w nocy, łucznicy pod wiatr). Wszystko deterministyczne i weryfikowalne przez serwer.

## Stan obecny (sprawdzone w kodzie)
- `sim/.../Modes.kt`: `BattleConfig` (bez pogody), `Modifier` (10 modyfikatorów, m.in. `FOG_OF_WAR`), `Stage(index, nameKey, startEra, maxEra, aiPower, modifiers)`, `Campaign.config(...)`, `Daily.config(day, bonuses)`. **`Daily.config` losuje modyfikatory z `Modifier.values()`**: dopisanie pogody jako nowego `Modifier` zmieniłoby wyzwanie dnia dla bieżącego i przyszłych dni, więc pogoda musi być osobnym polem.
- `Daily.ERA_EXPANSION_DAY` pokazuje wzór: zmiany losowania dnia tylko od ustalonej daty w przyszłości.
- `sim/.../Battle.kt`: zasięg jednostki `Defs.unitRange(def, tier)` w `updateUnit` (~l. 833), zasięg wież `td.range` w `turrets()` (~l. 791), ruch `Defs.unitSpeed(def, tier) * s.speedMul * rally`, podpalenie `burnDps/burnLeft`. Modyfikatory stosowane w `init` przez pola `Side` (`speedMul`, `dmgMul`, …).
- `sim/.../BattleSnapshot.kt`: `writeConfig/readConfig` (pola konfiguracji), `VERSION = 7`.
- Serwer (`server/.../ReplayVerifier.kt`) buduje konfigurację przez `Campaign.config` i `Daily.config`, a Endless/potyczkę przez `BattleConfig(mode, seed, 1f)`: pogoda musi wynikać z tych funkcji albo z seeda wewnątrz symulacji, inaczej serwer odrzuci wynik.
- Renderer: `BattleRenderer.drawSky/drawBackdrop/drawGround` (kolory z `EraSpec`), `drawFog` dla mgły wojny, cząstki w prealokowanych tablicach (`PMAX = 320`, `Quality.maxParticles`).
- Brak ikon pogody w `engine/Icons.kt`. Etap pokazuje modyfikatory w `CampaignScreen` (~l. 231), wyzwanie dnia w `ModeScreen`.

## Zakres
1. **Model** (`:sim`): `enum class Weather(val key) { CLEAR, RAIN, WIND, NIGHT }` (dopisywanie tylko na końcu), pole `BattleConfig.weather = CLEAR`, `Stage.weather`. Propozycja efektów (symetryczne, małe; możesz przestroić z uzasadnieniem):
   - RAIN: zasięg jednostek dystansowych i wież ×0,9, ruch ×0,92, podpalenie ×0,5,
   - WIND: pociski strony z wiatrem zasięg ×1,1, pod wiatr ×0,9; kierunek zmienia się co 75 s jako czysta funkcja `(seed, czas)` (bez nowego stanu i bez losowań z `Battle.rng`),
   - NIGHT: zasięg wież ×0,85, zasięg dystansowych ×0,95.
   Mnożniki liczone bez alokacji (pola `Side` ustawiane w `init` albo funkcja `rangeMul(side, projectile)`). `CLEAR` musi dawać bit w bit dzisiejszy przebieg (test porównujący z zapisanym wynikiem/`BattleSnapshot` sprzed zmiany).
2. **Przydział:**
   - kampania: pogoda części etapów (np. co 3. od etapu 5, cykl RAIN/WIND/NIGHT), zapisana w `Campaign.stages`; ponów `BalanceReport` dla zmienionych etapów i dopisz wynik do `docs/BALANCE.md`,
   - wyzwanie dnia: od nowej stałej `Daily.WEATHER_DAY` (data w przyszłości, jak `ERA_EXPANSION_DAY`) pogoda z **osobnego** `Rng(day * … + stała)`, bez zmiany istniejących losowań; dni wcześniejsze = CLEAR (test: konfiguracja dzisiejszego i najbliższych dni identyczna jak przed zmianą),
   - Endless: pogoda zmienia się razem z epoką wroga (co 6 fal) jako funkcja `(seed, era)` wewnątrz `director`,
   - potyczka: CLEAR (wybór pogody da zlecenie 35).
3. **Zapis:** `weather` w `BattleSnapshot.writeConfig/readConfig`, podbij `BattleSnapshot.VERSION`, przypadek w `BattleSnapshotTest` (wznowienie w deszczu i przy zmianie wiatru). Format `Replay` bez zmian; jeśli scalone zlecenie 23, podbij `CW_GAME_VERSION` (zmienione etapy kampanii) i dodaj test serwera, że wynik z pogodą przechodzi weryfikację.
4. **Wygląd** (`BattleRenderer`, osobna warstwa `drawWeather`): deszcz (smugi w prealokowanych tablicach, liczba zależna od `Quality`), wiatr (dryf chmur i cząstek, łopocące flagi baz w stronę wiatru, wskaźnik kierunku przy minimapie), noc (przyciemnione niebo i tło, jaśniejsze strefy przy bazach i błyskach wież, paski HP i kontury nadal czytelne). Bez nowych `Paint`/`Shader` na klatkę; gradienty nieba cache'owane per (epoka, pogoda). Nie zmieniaj rysowania pocisków i trafień (zlecenia 25–26).
5. **UI:** ikony `RAIN`, `WIND`, `MOON` w `Icons`; pogoda w informacji o etapie (`CampaignScreen`), w `ModeScreen` (dzień), na górnym pasku bitwy (ikona + strzałka wiatru), wpisy w `CodexScreen` z opisem efektów.
6. **AI:** bez nowych bonusów; AI korzysta z tych samych zasięgów (sprawdź, czy `Ai.kt` nie liczy zasięgów osobno).
7. **Teksty** (10 języków): nazwy i opisy pogody, podpowiedź HUD.

## Kryteria akceptacji
- `WeatherTest`: CLEAR identyczne z dzisiejszym przebiegiem; każdy typ zmienia odpowiedni zasięg/ruch; wiatr zmienia kierunek zgodnie z funkcją; determinizm (dwa przebiegi, ten sam wynik); wyzwanie dnia przed `WEATHER_DAY` niezmienione.
- `BattleSnapshotTest`, `ReplayTest`, `deterministicForSameSeed`, `BattleSimTest` przechodzą; `:server:test` przechodzi.
- Odsetek wygranych w etapach z pogodą w `BalanceReport` w granicach ±10 p.p. od etapów sąsiednich (tabela w PR).
- `ScreenshotTest`: bitwa w każdej pogodzie (różne epoki) i informacja o etapie na phone720, phone20x9, foldOpen, foldPortrait; noc czytelna na 720p (LOW).
- Zero alokacji na klatkę (renderer i symulacja); `LTest` przechodzi.

## Pliki
`sim/.../Modes.kt`, `Battle.kt`, `BattleSnapshot.kt`, ewentualnie `Ai.kt`; `app/.../game/BattleRenderer.kt`, `engine/Icons.kt`, `screens/CampaignScreen.kt`, `screens/ModeScreen.kt`, `screens/BattleScreen.kt` (pasek górny), `screens/CodexScreen.kt`, `engine/L.kt`, `docs/BALANCE.md`; testy `app/src/test/java/com/centurywar/game/WeatherTest.kt` (nowy), `BattleSnapshotTest.kt`, `screens/ScreenshotTest.kt`, ewentualnie `server/src/test/...`.
