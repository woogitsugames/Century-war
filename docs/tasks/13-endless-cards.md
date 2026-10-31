# Zlecenie 13: Oblężenie bez końca 2.0: karty roguelite i bossowie

**Gałąź robocza:** `gpt/13-endless-cards` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Tryb bez końca ma być powodem do grania godzinami i walki o ranking.

## Zakres (`game/Battle.kt` director, nowe pliki `game/EndlessCards.kt`, testy)
1. Co 5 fal zamiast samych doktryn: wybór 1 z 3 **kart** z puli ≥ 25 (doktryny + nowe: „+1 slot wieży”, „darmowa jednostka elitarna co falę”, „wieże podpalają (DoT)”, „ostrzał co 30 s automatycznie”, „+50% złota, −20% HP bazy”, „klonowanie: 10% szans na kopię wyszkolonej jednostki” itd.). Karty mają rzadkość, a wylosowanie jest deterministyczne (`Battle.rng`).
2. Co 10 fal **boss**: wzmocniona jednostka elitarna z mnożnikiem HP i specjalną zdolnością (np. odrzut, aura szybkości dla sojuszników). Nagroda: dodatkowa karta.
3. Fale tematyczne (np. „rój strzelców”, „pancerna kolumna”) zapowiadane wcześniej (pole w `Battle` z nazwą następnej fali, do pokazania w HUD).
4. Wynik do rankingu: zostaw kompatybilne `Battle.score()` dla trybu, ale uwzględnij pokonanych bossów.
5. Teksty kart w `engine/L.kt` (10 języków). HUD: uogólnij wybór doktryny w `BattleScreen` (`pendingChoice`) na karty z ikonami i rzadkością; zapowiedź następnej fali i pasek HP bossa.
6. Testy: karty się aplikują, boss się pojawia, determinizm, bitwa AI vs fale kończy się w rozsądnym czasie (test „endlessEventuallyEnds”).
