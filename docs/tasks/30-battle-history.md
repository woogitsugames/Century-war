# Zlecenie 30: Historia bitew i ekran statystyk

**Gałąź robocza:** `gpt/30-battle-history` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Gracz widzi historię ostatnich bitew i swoje statystyki: ile wygrywa w każdym trybie, jak gra (role, doktryny, wieże, ostrzały), z kim przegrywa i jak się poprawia. Daje powód do kolejnej bitwy i podpowiada, co zmienić.

## Stan obecny (sprawdzone w kodzie)
- `meta/Profile.kt`: tylko łączne liczniki `battles`, `wins`, `totalKills`, `playSeconds`, `bestWave` (aktualizowane w `Achievements.onBattleEnd`), tablice wyników `boards` i `achievementCounters`. Brak historii pojedynczych bitw i podziału na tryby.
- `RankingsScreen.kt` (~l. 124) rysuje jedną linię statystyk, formatując string w każdej klatce (`"${L["stats"]}: …"`, `"%.1f".format`), czyli alokuje w pętli rysowania.
- Po bitwie dostępne są liczniki strony (`sim/.../Battle.kt`, klasa `Side`): `kills`, `spawnedByRole`, `goldEarned`, `turretsBuilt`, `barragesUsed`, `ralliesUsed`, `erasAdvanced`, `elitesLost`, `baseDamageTaken`, `doctrines`, `cardStacks`, `era`, `rank`; z bitwy: `time`, `wave`, `bossesDefeated`, `stars()`, `score()`, `winner`; osobowość przeciwnika `battle.ai?.personality`.
- Koniec bitwy: `BattleScreen.onFinished()` (~l. 546), gdzie liczone są nagroda, `newRecord`, `tipKey` (powód porażki).
- Menu: `MenuScreen.kt` ma w prawym górnym rogu ikony `achievements`, `codex`, `settings`; brak ikony wykresu w `engine/Icons.kt`.
- Profil jest synchronizowany z chmurą (Play Games), więc nie może puchnąć.

## Zakres
1. **Zapis historii** (`app/.../meta/BattleHistory.kt`): osobny plik `filesDir/history.json` (nie w profilu i nie w chmurze), do 100 ostatnich wpisów, zapis atomowy w tle. Wpis: data (ms), tryb, etap, wynik (wygrana/porażka/poddanie), gwiazdki, punkty, czas, zabici/straceni, epoka końcowa i awanse, `spawnedByRole[4]`, maska doktryn, karty Endless (indeksy z `cardStacks`), wieże, ostrzały, Zrywy, złoto zarobione, obrażenia bazy, fala i bossowie (Endless), siła AI i osobowość przeciwnika, modyfikatory, nagroda w medalach, `tipKey`, opcjonalnie id powtórki (gdy scalone 29). Odczyt odporny na uszkodzenia (zły wpis pomijany), zakresy przycinane.
2. **Liczniki trwałe w `Profile`** (kompatybilne wstecz, małe): bitwy i zwycięstwa per tryb, najdłuższa seria zwycięstw i bieżąca seria, suma czasu per tryb. Brak pola w starym JSON = 0.
3. **Wpis po bitwie** w `onFinished()` (jedna alokacja na koniec bitwy, I/O w tle).
4. **`screens/StatsScreen.kt`** (dwie zakładki):
   - *Statystyki:* zwycięstwa/bitwy i odsetek per tryb, serie, łączny czas, ulubiona rola (udział w szkoleniu), najczęstsza doktryna, najlepsze wyniki (z `boards`), wykres słupkowy ostatnich 14 dni (bitwy i zwycięstwa) i wykres wyniku z ostatnich 20 bitew Endless; odsetek wygranych z każdą osobowością AI,
   - *Historia:* przewijana lista wpisów (ikona trybu, wynik, czas, data względna „dziś/wczoraj/N dni temu”), po dotknięciu szczegóły wpisu (wszystkie liczby, doktryny ikonami, podpowiedź `tipKey` przy porażce, przycisk „Powtórka”, gdy jest).
   - Wykresy rysowane kodem w stylu UI (`Ui.panel`, gruby kontur), bez alokacji w `render` (dane i stringi liczone przy wejściu na ekran i przy zmianie zakładki).
5. **Wejście:** nowa ikona w rzędzie ikon menu (dodaj `Icons.CHART`) i przycisk w `RankingsScreen` w miejscu dzisiejszej linii statystyk; napraw formatowanie w każdej klatce (cache stringa).
6. **Teksty** (10 języków): nagłówki, nazwy statystyk, „dziś/wczoraj/%d dni temu” (z formami liczby mnogiej, jeśli scalone 19, inaczej forma neutralna „%d d temu”).

## Kryteria akceptacji
- `BattleHistoryTest`: zapis i odczyt, limit 100, odporność na uszkodzony plik i brakujące pola, przycinanie zakresów; agregaty (odsetek, serie, ulubiona rola) na danych testowych.
- `ProfileTest`: stary profil bez nowych pól wczytuje się z zerami; nowe pola przetrwają `toJson/fromJson` i chmurę (`ProfileCloudTest`).
- Zero alokacji w `StatsScreen.render` i w poprawionym `RankingsScreen.render`; brak I/O na wątku gry.
- `ScreenshotTest`: obie zakładki, szczegóły wpisu i pusty stan (nowy gracz) na phone720, phone20x9, foldOpen, foldPortrait; zrzuty w PR.
- `LTest` przechodzi; symulacja nietknięta (bez zmian w `:sim`).

## Pliki
`app/.../meta/BattleHistory.kt` (nowy), `meta/Profile.kt`, `screens/StatsScreen.kt` (nowy), `screens/BattleScreen.kt` (`onFinished`), `screens/MenuScreen.kt`, `screens/RankingsScreen.kt`, `engine/Icons.kt`, `engine/L.kt`, `engine/Game.kt`/`MainActivity.kt` (inicjalizacja pliku historii); testy `app/src/test/java/com/centurywar/meta/BattleHistoryTest.kt`, `ProfileTest.kt`, `screens/ScreenshotTest.kt`.
