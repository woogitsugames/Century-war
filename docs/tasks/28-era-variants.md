# Zlecenie 28: Warianty epok (od nowa, zamiast 18)

**Gałąź robocza:** `gpt/28-era-variants` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Przy awansie do wybranych epok gracz wybiera jeden z dwóch wariantów (inny skład jednostek, wieża, ostrzał i wygląd). To zlecenie **zastępuje odrzuconą gałąź `gpt/18-era-branches`** i zlecenie 18: zrób to od nowa na obecnym kodzie (9 epok, `EraSpec`), tak żeby powtórki, zapis bitwy, wyzwanie dnia i kampania pozostały spójne.

## Stan obecny (sprawdzone w kodzie)
- `sim/src/main/kotlin/com/centurywar/game/Defs.kt`:
  - `class EraSpec(nameKey, unitNames, weapons, heavyBody, eliteBody, rangedProj, heavyProj, eliteProj, turretName, turretProj, abilityKey, barrageProj, evolveXp, art: EraArt, terrain, palette, accent, fastTurret)`; `Defs.specs` ma 9 wpisów, `ERA_COUNT = specs.size` (indeksy: 0 plemienna, 1 brąz, 2 żelazo, 3 feudalna, 4 proch, **5 para (`EraArt.STEAM`)**, 6 stal, **7 krzem (`EraArt.SILICON`)**, 8 orbitalna).
  - `enum class EraArt { TRIBAL, BRONZE, IRON, FEUDAL, POWDER, STEAM, STEEL, SILICON, ORBITAL }`; `artPreset(era)` mapuje styl na indeks palet postaci (żelazo i feudalna → 2).
  - `eras = Array(ERA_COUNT) { buildEra(it) }`, `allUnits` z `id = era * 4 + rola` (`UnitDef.id` zapisywany w `BattleSnapshot`, l. ~100 i ~251, oraz w kolejce `Side.queue`), `Defs.unit(era, role)`, wieża `Defs.eras[era].turret`, ostrzał `Defs.spec(era).barrageProj`.
- Renderer i grafika wybierają wygląd z **indeksu epoki**: `Defs.spec(def.era).art` (np. `BattleRenderer.drawBody`, `unitWidth/unitHeight`), `Soldiers.draw` (`Defs.artPreset(epoch)`, l. ~84), `Bases.kt` (`flagX/flagTop`, `artPreset` l. 154, 404, 422, 446, 483), cache baz `basePics[side * ERA_COUNT + era]`, niebo/tło z `b.player.era`, `Music.kt` (l. ~54), wieżyczki `bases.turret(s.turretEra[i], ...)`.
- `Battle.kt`: `Side.era`, `turretEra[]`; `evolve()` (l. ~346) → `offerDoctrines()` (losuje z `rng`, gracz dostaje `pendingChoice`); `step()` czeka, dopóki `pendingChoice`/`pendingCards` nie są null; AI ma osobne strumienie `rng.nextAiInt(side, …)` (`Modes.kt`, `Rng`), a decyzje w `Ai.kt` używają `Defs.unit(me.era, role)` (8 miejsc).
- `BattleController.kt`: jedyna droga komend gracza (`canAct` sprawdza `pendingChoice`/`pendingCards`; `evolve()` dla `playerAi` od razu zapisuje wybór doktryny; `choosePendingDoctrine/choosePendingCard`). `Replay.kt`: `VERSION = 3`, `enum Kind { …, BARRAGE_AT, CARD, UNIT_UPGRADE }` (kolejność = format), `validate()` sprawdza zakresy argumentów. `BattleSnapshot.VERSION = 7`, modyfikatory zapisywane jako maska po `ordinal`.
- `Modes.kt`:
  - `Daily.config(day)`: `mods` losowane z `Modifier.values()` (**dodanie wartości do `Modifier` zmienia `all.size`, czyli wszystkie dotychczasowe dni**), potem `rolledStart` z gałęzią `historical = day < ERA_EXPANSION_DAY` (`ERA_EXPANSION_DAY = 21153L`, 1.12.2027, data zastępcza do czasu wydania). Test `BattleSimTest.dailyEraSelectionChangesOnlyFromNextUtcDay`.
  - `Campaign.stages`: `ERA_COUNT * 4` etapów, gwiazdki w profilu po indeksie etapu (migracja gwiazdek była potrzebna przy wstawieniu żelaza – tu ma jej nie być). Serwer (`server/.../ReplayVerifier.kt`) odtwarza konfigurację przez `Daily.config(day, Bonuses())` i `Campaign.config(...)`.
- `app/src/test/java/com/centurywar/game/BalanceReport.kt`: raport na żądanie (`BALANCE_RUN=true`), `campaignReport()` i `mirrorReport()`.
- **Odrzucona gałąź `origin/gpt/18-era-branches`** (przejrzyj: `git diff $(git merge-base origin/gpt/18-era-branches origin/claude/bold-sagan-vwz5gm) origin/gpt/18-era-branches`, plik `app/.../game/EraBranches.kt`). **Czego nie powtarzać:**
  1. `typealias EraSpec = EraDef` i osobny obiekt `EraBranches` z równoległymi tablicami – koliduje z klasą `EraSpec`.
  2. Sztywne indeksy epok: `available(era) = era == 4 || era == 6`, `allUnits` z „+16” i `if (off < 8) 4 else 6`, etapy kampanii 16/17/24/25, `Profile.steamPreference` po numerze epoki, `BranchArt` z `def.era == 4`. Po dodaniu żelaza wszystko wskazuje złe epoki.
  3. Zmiana `Daily.config` dla **wszystkich** dni (`rng.nextInt(3)` → `nextInt(7)`, filtrowana lista modyfikatorów, dodatkowe losowania w środku) – zmieniała dzisiejsze i historyczne wyzwania.
  4. Wybór wariantu poza `BattleController`/`Replay` (`battle.chooseEraVariant()` wołane z UI, automatyczny wybór w `Battle.step()` dla `playerAi`) – serwer nie może tego zweryfikować.
  5. Wybór AI z głównego `rng.nextInt(2)` (zmienia losowość całej bitwy) i heurystyki zależne od chwilowego stanu bez osobnego strumienia.
  6. Nowe wartości `Modifier.FORCE_*` (przesuwają pulę losowania wyzwania dnia).
  7. Nazwy „Tesla”, „Diesel”, „Cyber”, „Bio” (marka, nazwisko, ogólniki) i wygląd wariantu jako „naklejki” na tę samą sylwetkę.
  8. Osobny plik CI `.github/workflows/era-branches-ci.yml` i osobny raport poza `docs/BALANCE.md`.

## Zakres
1. **Model danych (`Defs.kt`):**
   - `class EraVariant(val key: String, val nameKey: String, val descKey: String, val art: EraArt, …)` z nadpisaniami: jednostki ról (co najmniej ciężka i elita własne; pozostałe role mogą zostać z bazy, ze zmienionymi statystykami), wieża, ostrzał (`barrageProj`, liczba, rozprysk), opcjonalnie paleta/teren.
   - `EraSpec.variants: Array<EraVariant> = emptyArray()`; na start dwie pary: dla epoki ze stylem `STEAM` i dla `SILICON` (wyszukiwane po `EraArt`, nigdy po indeksie). Proponowane nazwy (własne, bez marek i nazwisk): para – **„Spalinowa”** (ciężka, pancerna, wolna) / **„Galwaniczna”** (wyładowania łańcuchowe, krucha); krzem – **„Rojowa”** (tanie drony, liczne) / **„Biotechniczna”** (regeneracja, droga). Możesz zaproponować lepsze, ale bez nawiązań do innych gier.
   - Nowe style dopisz **na końcu** `EraArt` (np. `COMBUSTION`, `GALVANIC`, `SWARM`, `BIOTECH`); `artPreset` przyjmuje `EraArt` (zostaw przeciążenie po epoce).
   - `UnitDef` dostaje `variant: Int = -1` i `art: EraArt`; identyfikatory jednostek bazowych bez zmian (`era * 4 + rola`), jednostki wariantów dopisywane za nimi w kolejności `specs`/`variants`, wyliczane w pętli (bez stałych typu 16). `Defs.unit(era, role, variant = -1)`, `Defs.turret(era, variant)`, `Defs.barrage(era, variant)`, `Defs.eraOf(art)`.
2. **Symulacja (`Battle.kt`):** `Side.eraVariant` (−1 = brak), `Side.turretVariant[]`; po `evolve()` (i na starcie bitwy, gdy `startEra` ma warianty) strona wybiera wariant **przed** doktryną: gracz – `pendingVariant` blokuje `step()` jak `pendingChoice`; AI – od razu. Kupno/ulepszenie/sprzedaż wieży, kolejka, `canQueue`, ostrzał używają wariantu (`side.unit(role)`). Mechaniki specjalne (łańcuch, regeneracja) deterministyczne, bez alokacji, O(n) na trafienie; ich stan w `BattleSnapshot`. Epoki bez wariantów działają bit w bit jak dziś (te same losowania `rng`).
3. **Komendy i powtórki:** `Replay.Kind.VARIANT` dopisane **na końcu** enuma, `BattleController.variant(index)` (z `canAct` uwzględniającym `pendingVariant`), `choosePendingVariant()` dla `playerAi` (zapis do logu jak doktryna, z rezerwą miejsca w `evolve()`), `Replay.verify`/`validate` (zakres argumentu), **`Replay.VERSION` → 4**. Opisz w PR potrzebę podbicia `CW_GAME_VERSION` na serwerze (`server/README.md`).
4. **AI:** wybór deterministyczny przez `rng.nextAiInt(side, …)` z wagami zależnymi od `AiPersonality` (np. `SIEGER`/`ENGINEER` częściej ciężki wariant) i ewentualnie od wariantu przeciwnika, jeśli już wybrany. Zero losowań z głównego `rng`. `Ai.kt`: zamień `Defs.unit(me.era, role)` na wersję z wariantem.
5. **Zapis bitwy:** `eraVariant`, `turretVariant[]`, `pendingVariant`, stan mechanik w `BattleSnapshot`; **`VERSION` → 8**; przypadki w `BattleSnapshotTest` (zrzut w trakcie wyboru wariantu i po nim, z jednostkami wariantu w kolejce i na polu, wynik identyczny).
6. **Wyzwanie dnia:** nowa stała `Daily.VARIANTS_DAY` (nie wcześniej niż `ERA_EXPANSION_DAY`, z komentarzem jak przy `ERA_EXPANSION_DAY`). Dla `day < VARIANTS_DAY` konfiguracja identyczna jak dziś. Wymuszony wariant (opcjonalnie) jako nowe pole `BattleConfig` (np. `forcedVariants: Map<EraArt, Int>` lub prosta para pól), losowane **po** wszystkich obecnych losowaniach i tylko od `VARIANTS_DAY`; **nie** dodawaj wartości do `Modifier`. Test: kopia obecnego algorytmu w teście (`legacyConfig`) porównana z `Daily.config` dla każdego dnia od 20000 do `VARIANTS_DAY - 1` (start, max, moc AI, modyfikatory).
7. **Kampania:** liczba i indeksy etapów bez zmian, bez migracji gwiazdek. Opcjonalnie `Stage.forcedVariant` na istniejących etapach epok z wariantami (np. etap 2 i 3 danej epoki), wyznaczonych przez `Defs.eraOf(EraArt.STEAM)`, nie stałą liczbę. Domyślnie gracz wybiera sam.
8. **UI:** modal wyboru wariantu w `BattleScreen` (styl jak wybór doktryny: dwie karty, nazwa, krótki opis stylu gry, cztery ikony jednostek przez `drawUnitIcon`), komenda przez `commands.variant(i)`; samouczek nie trafia na warianty. Kodeks (`CodexScreen`) pokazuje oba warianty epoki (przełącznik na karcie epoki). Panel wież pokazuje wieżę wariantu. Układ działa od 720p po Folda w pionie i poziomie.
9. **Grafika:** wygląd po kluczu `EraArt` z `UnitDef.art`/wariantu strony (nie z `def.era`): `drawBody`, `unitWidth/Height`, `Soldiers`, `Machines`, `Bases` (cache baz indeksowany stylem, nie epoką), wieżyczki, niebo/tło, `Music` (wariant gra motyw epoki bazowej). Każdy wariant ma rozpoznawalną sylwetkę ciężkiej i elitarnej jednostki w obecnym stylu (płaskie kształty, gruby kontur, kolor drużyny dominuje). Galeria w `ArtGalleryTest` (`gallery_units_*`, `gallery_bases_*`) z wariantami.
10. **Teksty:** nazwy i opisy wariantów, nazwy jednostek z trzema stopniami (`nameKey`, `_t2`, `_t3` – patrz `UnitDef.tierNameKeys`), wieże, ostrzały, opisy w Kodeksie – w `L.kt`, 10 języków.
11. **Balans:** w `BalanceReport` nowa metoda (np. `variantReport()`, tylko przy `BALANCE_RUN=true`): dla każdej epoki z wariantami pojedynki lustrzane wariant A vs B (te same bonusy, zamiana stron, ≥ 200 seedów) oraz A i B przeciw AI z wariantem losowym na etapach kampanii danej epoki. Wymóg: **różnica odsetka wygranych między wariantami ≤ 10 pp**, a etapy kampanii nie wychodzą poza progi z `docs/BALANCE.md`. Wyniki dopisz do `docs/BALANCE.md`.

## Kryteria akceptacji
- Bitwy w epokach bez wariantów i dotychczasowe testy (`BattleSimTest`, `BattleRulesTest`, `CounterBattleSimTest`, `EndlessCardsTest`, `AiPersonalityTest`) przechodzą; `ReplayTest` i `BattleSnapshotTest` przechodzą po podbiciu wersji, z nowymi przypadkami (powtórka z wyborem wariantu gracza i `playerAi` zweryfikowana przez `Replay.verify`).
- `Daily.config` dla dni przed `VARIANTS_DAY` bez zmian (test porównawczy); indeksy etapów i gwiazdki bez zmian.
- Żadnej stałej z numerem epoki w kodzie wariantów (sprawdzenie grepem w PR).
- Raport balansu: różnica wygranych wariantów ≤ 10 pp w obu epokach.
- Zrzuty: modal wyboru (720p, Fold pion/poziom, pl i en), Kodeks z wariantami, galeria jednostek i baz.
- LTest przechodzi (10 języków).

## Pliki
`sim/src/main/kotlin/com/centurywar/game/Defs.kt`, `Battle.kt`, `Ai.kt`, `BattleController.kt`, `Replay.kt`, `BattleSnapshot.kt`, `Modes.kt`; `app/.../game/BattleRenderer.kt`, `art/Soldiers.kt`, `art/Machines.kt`, `art/Bases.kt`, `engine/Music.kt`, `engine/L.kt`, `screens/BattleScreen.kt`, `screens/CodexScreen.kt`; testy `BattleSnapshotTest`, `ReplayTest`, `BattleSimTest`, `BalanceReport`, `ArtGalleryTest`, `ScreenshotTest`; `docs/BALANCE.md`, `server/README.md` (notka o wersji).
