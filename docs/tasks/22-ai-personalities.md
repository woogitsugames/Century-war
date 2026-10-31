# Zlecenie 22: Wyraźne różnice osobowości AI z miarami

**Gałąź robocza:** `gpt/22-ai-personalities` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Pięć osobowości AI (`AiPersonality`) ma grać wyraźnie inaczej i dać się rozpoznać po przebiegu bitwy. Dziś ECONOMIST, SIEGER i BALANCED dają podobne wyniki (`docs/HANDOFF.md`, sekcja ryzyk).

## Stan obecny (sprawdzone w kodzie)
- `sim/.../AiPersonality.kt`: enum z polami `meleeWeight/rangedWeight/heavyWeight/eliteWeight`, **które nigdzie nie są używane**.
- `sim/.../Ai.kt`: różnice to drobne progi: ECONOMIST czeka do 7 s z awansem (`update`), szansa elity w `pickRole` (35/48/32/32/35), SIEGER: ostrzał przy `danger >= 2` (`useAbilities`) i 70% elity w ostatniej epoce, ENGINEER: wieże od 28 s (`manageTurrets`), ASSAULT: Zryw od 3 jednostek, ranking doktryn `doctrineRank`. Wspólny trzon `trainUnits`/`pickRole`/`savingForNextEra` jest identyczny.
- Test: `app/src/test/java/com/centurywar/game/AiPersonalityTest.kt` (skład armii różny dla ≥3 osobowości, determinizm, mgła).
- Zasada: osobowość zmienia **decyzje**, nie statystyki ani przychód (komentarz w `Ai.kt` i `AiPersonality.kt`).

## Zakres
1. **Parametry zamiast `when`:** przenieś zachowanie do pól enuma (np. `eliteChance`, `turretDelay`, `turretBudget` (udział złota na wieże), `evolveDelay`, `saveForEraBias`, `armoryBias`, `barrageDanger`, `rallyMin`, `holdThreshold`, `pushArmySize` (od ilu jednostek przestaje bronić i naciera)), a wagi ról faktycznie użyj w `pickRole` (losowanie ważone po kontrach) albo je usuń. Kod `Ai.kt` ma czytać parametry; `when(personality)` zostaje tylko tam, gdzie zachowanie jest jakościowo inne.
2. **Wyraźne profile** (propozycja, możesz ją uzasadnić inaczej):
   - ASSAULT: wczesna presja, tanie jednostki wręcz, szybki Zryw, późny awans epoki,
   - ENGINEER: wieże i ulepszenia wież priorytetem, postawa obronna, ciężkie jednostki,
   - ECONOMIST: oszczędza na awans epoki (najszybszy średni awans), mało wież, armia minimalna do obrony, potem duża fala elity; Zbrojownia późno,
   - SIEGER: gromadzi armię (nie wysyła pojedynczo), naciera falą od `pushArmySize`, ostrzał na bazę/front wroga, ciężkie i elita,
   - BALANCED: środek wszystkich parametrów (punkt odniesienia).
3. Bez nowych źródeł losowości (tylko `b.rng.nextAiInt/nextAiFloat(me.index, …)`), bez bonusów do statystyk. Jeśli dodajesz pole stanu do `Ai` (np. licznik zebranej fali), dopisz je do `BattleSnapshot` (podbij `VERSION`) i do `BattleSnapshotTest`. AI gracza w testach nadal wydaje rozkazy przez `BattleController` (powtórki).
4. **Miary różnic** – nowy test (np. `AiPersonalityProfileTest`), dla każdej osobowości jako przeciwnik BALANCED-gracza, na ≥20 seedach, era 0→maxEra 3 i 2→2, liczy średnie:
   - czas pierwszego awansu epoki, liczba awansów,
   - udział ról w `spawnedByRole`,
   - liczba zbudowanych wież (`turretsBuilt`), ostrzałów (`barragesUsed`), Zrywów (`ralliesUsed`),
   - średnia liczba żywych jednostek (próbkowana co 5 s), złoto niewydane średnio,
   - czas bitwy i odsetek wygranych.
   Wypisz tabelę (println) i dodaj asercje kierunkowe, np.: ECONOMIST ma najwcześniejszy średni awans; ENGINEER najwięcej wież; ASSAULT najwięcej jednostek wręcz w pierwszych 90 s; SIEGER największą średnią armię przy natarciu; każda para osobowości różni się o ≥15% w co najmniej dwóch miarach. Odsetek wygranych każdej osobowości przeciw BALANCED w przedziale 30–70% (żadna nie jest bezużyteczna ani zdecydowanie najlepsza).
5. Zachowaj obecne testy `AiPersonalityTest` (dopuszczalna zmiana progów z uzasadnieniem) i sprawdź, że `BattleSimTest` (krzywa kampanii) nie przesuwa się o więcej niż ~10 p.p. odsetka wygranych; jeśli tak, opisz wpływ w PR (strojenie balansu to zlecenie 04).
6. Jeśli na gałęzi bazowej są już ulepszenia jednostek (zlecenie 16), uwzględnij je w parametrach (np. ECONOMIST i ENGINEER kupują ulepszenia, ASSAULT rzadko).

## Kryteria akceptacji
- Tabela miar w PR (przed i po zmianie) pokazuje wyraźne profile; asercje kierunkowe przechodzą.
- Determinizm: `deterministicForEachPersonality`, `deterministicForSameSeed`, `ReplayTest`, `BattleSnapshotTest` przechodzą.
- Brak alokacji w `Ai.update` (żadnych list/lambd tworzonych co myślenie).
- `./gradlew :server:test -PserverOnly=true` przechodzi (serwer kompiluje `:sim`).

## Pliki
`sim/src/main/kotlin/com/centurywar/game/AiPersonality.kt`, `Ai.kt`, ewentualnie `BattleSnapshot.kt`; testy `app/src/test/java/com/centurywar/game/AiPersonalityTest.kt` + nowy test profili, `BattleSnapshotTest.kt`.
