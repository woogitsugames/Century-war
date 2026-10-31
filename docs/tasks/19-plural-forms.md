# Zlecenie 19: Liczba mnoga w tekstach (formy liczebników w 10 językach)

**Gałąź robocza:** `gpt/19-plural-forms` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Teksty z liczbami muszą być gramatyczne we wszystkich 10 językach. Dziś `L.f("ms_wins", 1)` daje „Win 1 battles”, a po polsku „Rozegraj 2 bitew”, „Dziś: +1 medali”.

## Stan obecny (sprawdzone w kodzie)
- `engine/L.kt`: `fun f(key, vararg args)` = `String.format(Locale.ROOT, get(key), *args)`; jedna forma na język.
- Klucze z liczebnikiem przy rzeczowniku: wszystkie `ms_*` z `%d` (m.in. `ms_battles`, `ms_wins`, `ms_kills`, `ms_units`, `ms_elites`, `ms_ranged`, `ms_heavy`, `ms_evolutions`, `ms_ranks`, `ms_doctrines`, `ms_modifier_wins`, `ms_research`, `ms_campaign_wins`, `ms_skirmish_wins`, `ms_daily_battles`, `ms_endless_battles`, `ms_healthy_wins`, `ms_stars`, `ms_armory`, `ms_turrets`, `ms_barrages`, `ms_rallies`, `ms_minutes`, `ms_score`, `ms_three_stars`, `ms_gold`, `ms_hard_wins`, `ms_login_paid`).
- Wywołania: `meta/MissionTexts.kt` (`label()`), `screens/MissionsScreen.kt` (`ms_login_day`, `ms_login_paid`), `engine/Game.kt` (powiadomienie o serii logowań), `screens/BattleScreen.kt` (`"+$reward ${L["medals"]}"` w podsumowaniu bitwy), `screens/MenuScreen.kt` (`"${pr.medals} ${L["medals"]}"`).
- Klucze bez rzeczownika po liczbie (`stage_n`, `wave`, `level`, `slot`, `rank`, `cx_era_n`, `tut_step`, `achievement_completed`, `ms_login_day`, `ai_power`) **nie wymagają** form. Sprawdź jednak każdy klucz z `%d` i `%s` (np. `cx_evolve` z liczbą jako `%s`) i wypisz w PR, które zostały uznane za neutralne.

## Zakres
1. **API** w `engine/L.kt`:
   - `fun pluralIndex(langIdx: Int, n: Long): Int` z regułami:
     - `pl`: 1 → 0; n%10 w 2..4 i n%100 nie w 12..14 → 1; reszta → 2 (`1 bitwa`, `2 bitwy`, `5 bitew`, `22 bitwy`, `12 bitew`),
     - `uk`: n%10==1 i n%100!=11 → 0; n%10 w 2..4 i n%100 nie w 12..14 → 1; reszta → 2 (`1 битва`, `21 битва`, `3 битви`, `11 битв`),
     - `fr`: 0 i 1 → 0, reszta → 1,
     - `en`, `de`, `es`, `it`, `pt`, `nl`: 1 → 0, reszta → 1,
     - `tr`: zawsze 0 (rzeczownik po liczebniku bez liczby mnogiej).
   - Formy w jednym wpisie tabeli, oddzielone `|` (np. pl `"Wygraj %d bitwę|Wygraj %d bitwy|Wygraj %d bitew"`, en `"Win %d battle|Win %d battles"`, tr jedna forma). Format `t(...)` i kolejność kolumn bez zmian.
   - `fun plural(key: String, n: Long, vararg args: Any): String`: wybiera formę (gdy form jest mniej niż indeks, bierze ostatnią), potem formatuje jak `f`. Zwykłe `L[key]`/`L.f` dla klucza z `|` nie mogą zwracać surowego `|` (np. bierz ostatnią formę albo dodaj asercję w teście, że takie klucze są używane tylko przez `plural`).
   - Przy argumentach innych niż liczebnik (`ms_modifier_wins` ma `%s` i `%d`) liczba decydująca o formie jest jawnym parametrem `n`.
2. **Przegląd tekstów:** zamień wszystkie klucze z punktu „Stan obecny” na formy. Zwróć uwagę na dopełniacz po polsku i ukraińsku (`Pokonaj 1 wroga / 2 wrogów / 5 wrogów`, `Zdobądź 1 gwiazdkę / 2 nowe gwiazdki / 5 nowych gwiazdek`), `razy/raz` (`Użyj ostrzału 1 raz / 2 razy`), `minutę/minuty/minut`. Teksty mają brzmieć naturalnie; nie zmieniaj sensu.
3. **Medale:** dodaj klucz `medals_n` (np. „%d medal|%d medale|%d medali”) i użyj go w `BattleScreen` (podsumowanie nagrody, z cache stringa, nie w każdej klatce), `MenuScreen` (podtytuł Badań), `ms_login_paid` i w powiadomieniu w `Game.kt`. Etykieta `medals` (nagłówek) zostaje.
4. Wywołania w `MissionTexts.label()`, `MissionsScreen`, `Game.kt` przełącz na `L.plural`. Formatowanie nie może trafić do pętli rysowania (tylko przy zmianie wartości, jak dziś).
5. **Testy** w `app/src/test/java/com/centurywar/engine/LTest.kt`:
   - tabela reguł: pl/uk dla 0, 1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 101, 111, 112; fr 0/1/2; en 1/2; tr 1/5,
   - każdy klucz z `|` ma właściwą liczbę form dla języka (pl/uk 3, tr 1, pozostałe 2; dopuszczalnie mniej, jeśli formy są identyczne, ale wtedy test to wypisuje),
   - zgodność placeholderów **w każdej formie** (rozszerz istniejący test placeholderów o rozbicie po `|`),
   - przykładowe asercje: `plural("ms_wins", 1)` po polsku = „Wygraj 1 bitwę”, `2` = „Wygraj 2 bitwy”, `5` = „Wygraj 5 bitew”; po angielsku „Win 1 battle”.
6. Uaktualnij komentarz na górze `L.kt` (opis formatu `|`).

## Kryteria akceptacji
- Żaden widoczny tekst nie zawiera `|`; `LTest` przechodzi z nowymi testami.
- Zrzuty `ScreenshotTest` (misje, podsumowanie bitwy) w pl i en pokazują poprawne formy dla 1, 2 i 5.
- Brak alokacji w pętli rysowania (formatowanie tylko przy zmianie wartości).
- W PR lista wszystkich przejrzanych kluczy z `%d`/`%s` z decyzją: formy / neutralny.

## Pliki
`app/src/main/java/com/centurywar/engine/L.kt`, `meta/MissionTexts.kt`, `screens/MissionsScreen.kt`, `screens/BattleScreen.kt`, `screens/MenuScreen.kt`, `engine/Game.kt`, `app/src/test/java/com/centurywar/engine/LTest.kt`.
