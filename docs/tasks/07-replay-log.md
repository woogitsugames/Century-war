# Zlecenie 07: Zapis komend gracza i odtwarzanie bitwy (anty-cheat)

**Gałąź robocza:** `gpt/07-replay-log` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

---

# Prompt ogólny (wspólny dla wszystkich zleceń)

Pracujesz nad grą **Century War**, oryginalną strategią na Androida typu „wojna na jednym pasie przez epoki” (gatunek jak Age of War, ale własne nazwy, jednostki, grafika i mechaniki: **nie kopiuj niczego z Age of War ani innych gier**).

**Repozytorium:** `woogitsugames/century-war` (GitHub). **Gałąź bazowa:** `claude/bold-sagan-vwz5gm`.

## Zanim zaczniesz
1. Sklonuj repo i utwórz swoją gałąź z **najnowszej** gałęzi bazowej: `git fetch origin claude/bold-sagan-vwz5gm && git checkout -b <TWOJA_GAŁĄŹ> origin/claude/bold-sagan-vwz5gm`. Nazwa gałęzi jest podana w zleceniu.
2. Przeczytaj `README.md` i `docs/GDD.md` (projekt gry, zawartość, technologia, plan rozwoju).
3. Struktura kodu w `app/src/main/java/com/centurywar/`:
   - `game/`: symulacja bitwy (czysty Kotlin, **deterministyczna**): `Battle.kt`, `Ai.kt`, `Defs.kt` (balans), `Modes.kt` (kampania, tryby, `BattleConfig`, `Rng`); `BattleSnapshot.kt` (zapis i wznawianie przerwanej bitwy, format binarny z wersją); renderer `BattleRenderer.kt` + rysunki w `game/art/` (`Pen`, `Soldiers`, `Machines`, `Bases`),
   - `engine/`: pętla gry (`GameView`), `Game`, `Screen`, `Ui` (przyciski, panele, wstęgi, odznaki), `Icons` (ikony wektorowe), `L` (**teksty w 10 językach**), `Quality`, `Sound`,
   - `meta/`: `Profile` (zapis JSON, badania, rankingi lokalne), `OnlineLeaderboards`,
   - `screens/`: menu, kampania (mapa), badania, rankingi, ustawienia, kodeks, bitwa (HUD), `TutorialOverlay` (samouczek), `Chrome` (wspólny nagłówek ekranów), `MenuBg`,
   - `MainActivity.kt`.

## Twarde zasady
- **Kotlin, Android SDK 36** (compileSdk/targetSdk 36, minSdk 26). Projekt celowo **nie używa AndroidX ani zewnętrznych bibliotek**. Dodaj zależność tylko wtedy, gdy zlecenie wprost na to pozwala.
- Wolno edytować wszystkie pliki potrzebne do zlecenia. Zmiany w plikach współdzielonych (`Battle.kt`, `BattleScreen.kt`, `L.kt`, `Profile.kt`, `MainActivity.kt`) rób możliwie małe i addytywne, bo inne zlecenia pracują równolegle.
- **Teksty UI:** dodawaj do `engine/L.kt` w formacie `"klucz" to t("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")`, czyli **wszystkie 10 języków** (kolejność `L.CODES`). Etykiety przycisków krótkie. Test `LTest` sprawdza komplet języków, zgodność placeholderów (`%d`, `%s`) i brak zdublowanych kluczy, więc musi przechodzić.
- **Symulacja musi zostać deterministyczna:** w `game/` nie używaj `System.currentTimeMillis()`, `Math.random()`, `kotlin.random` ani iteracji po `HashMap`/`HashSet`, gdzie liczy się kolejność. Losowość tylko przez `Battle.rng` (`Rng`). Test `deterministicForSameSeed` musi przechodzić.
- **Zapis bitwy:** jeśli dodajesz pole stanu do `Battle`/`Side`/`Fighter`/`Projectile`/`Ai`/`BattleConfig`, dopisz je do `BattleSnapshot.kt` (zapis i odczyt), **podbij `BattleSnapshot.VERSION`** i dodaj przypadek do `BattleSnapshotTest`, który sprawdza, że wznowiona bitwa daje identyczny wynik.
- **Zapis profilu:** nowe pola w `Profile` muszą być kompatybilne wstecz (brak pola w starym JSON = wartość domyślna) i przycinane do sensownych zakresów przy odczycie (patrz `ProfileTest`).
- **Grafika:** rysowana kodem w stylu płaskiego cartoonu z grubym ciemnym konturem. Kolor drużyny jest dominujący (niebieski gracz, czerwony wróg). UI używa `Ui.panel`/`Ui.button`/wstęg i ikon z `Icons`; **nie używaj emoji** (nie wszędzie się renderują).
- **Wydajność:** gra ma płynnie chodzić na tanich telefonach (2–3 GB RAM). W pętli gry i renderowania **zero alokacji** (pule, prealokowane tablice, cache stringów), żadnego I/O na wątku gry w każdej klatce. Ekrany muszą działać od 720p po rozłożonego Folda (~2184×1968, prawie kwadrat); respektuj `g.insetL/T/R/B` i `ui.dp`.
- Komentarze w kodzie po polsku, zwięzłe. Styl jak w istniejącym kodzie.
- **Bez identyfikatorów modeli AI** w commitach, komentarzach i plikach.

## Budowanie i testy (obowiązkowo przed pushem)
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # jeśli trzeba
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --console=plain
```
- Testy Robolectric (`ProfileTest`, `BattleScreenTest`, `ScreenshotTest`, `ArtGalleryTest`) pobierają ~200 MB przy pierwszym uruchomieniu. Przy błędach 429 z Maven Central ponawiaj z odstępem albo pobierz artefakt z `https://maven-central.storage-download.googleapis.com/maven2`. Dodaj `--no-configuration-cache`.
- Przy zmianach wyglądu uruchom `--tests '*ScreenshotTest.menus*'` (~2 min) albo `ArtGalleryTest` (wszystkie jednostki i bazy). PNG lądują w `app/build/screenshots/`: obejrzyj je i dołącz najważniejsze do PR.
- Jeśli dodajesz logikę, **dodaj testy JUnit** w `app/src/test/java/...`.

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
Wynik rankingowy musi dać się zweryfikować: seed plus lista komend gracza odtwarza bitwę 1:1.

## Zakres
1. `game/Replay.kt`: rekord komendy (numer kroku symulacji, typ: queue(role), cancel, evolve, buyTurret(slot), upgradeTurret(slot), sellTurret(slot), armory, barrage, rally, stance(hold), doctrine(index)). Kompaktowa serializacja (np. varint + Base64) oraz wersja formatu.
2. W `game/Battle.kt` dodaj warstwę `BattleController`/`commands` (nowy plik), przez którą przechodzą komendy gracza i która je loguje z bieżącym numerem kroku (`Battle` ma licznik kroków; dodaj go, jeśli brak). Istniejące metody zostają; dodaj wariant przez kontroler.
3. `Replay.verify(cfg, log): Result(score, winner, steps)`: odtwarza bitwę bez renderowania (z `playerAi = null`, AI przeciwnika takie samo) i zwraca wynik.
4. Testy: bitwa sterowana przez `Ai` po stronie gracza, ale **logowana jako komendy**, a potem odtworzona z logu → identyczny wynik, czas, liczba zabitych. Także odporność na zmanipulowany log (komenda w przyszłym kroku, nielegalna komenda → odrzucenie).
5. Podepnij kontroler w `screens/BattleScreen.kt` i `screens/TutorialOverlay.kt` (wszystkie komendy gracza idą przez kontroler). Log komend zapisuj też w `BattleSnapshot` (wznowiona bitwa kontynuuje log).
6. Uwaga z przeglądu kodu: `Battle.time` jest sumą floatów (dryf na długich bitwach): rozważ licznik kroków jako źródło prawdy. `kotlin.math.pow` → `Math.pow` nie jest gwarantowanie identyczne na różnych urządzeniach: dla weryfikacji serwerowej użyj `StrictMath.pow` w `Defs`/`Battle` (sprawdź wpływ na balans testami).

## Uwaga
Determinizm musi obejmować kolejność wykonywania komend względem `step()`: komendy z kroku N wykonuj przed `step()` numer N.
