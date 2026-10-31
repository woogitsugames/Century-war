# Zlecenie 23: Integracja klienta z serwerem rankingu globalnego

**Gałąź robocza:** `gpt/23-global-ranking-client` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Gra (opcjonalnie, za zgodą gracza) wysyła zweryfikowalne wyniki do własnego serwera rankingu z `server/` i pokazuje ranking globalny. Funkcja **domyślnie wyłączona**.

## Stan obecny (sprawdzone w kodzie)
- Serwer: `server/src/main/kotlin/com/centurywar/server/` – `Api.kt` (`POST /v1/score`, `GET /v1/top`, `GET /v1/around`), `Security.kt` (HMAC-SHA256 z `timestamp\nnonce\nMETHOD\n/uri?query\nbody`, nagłówki `X-CW-Timestamp`, `X-CW-Nonce`, `X-CW-Signature`), `Models.kt` (`ScoreSubmission`: `board`, `player` UUID v4, `nick`, `gameVersion`, `day`, `seed`, `stage`, `replay`, `score`), `ReplayVerifier.kt`. Pełny opis w `server/README.md`.
- `ReplayVerifier.verify` buduje konfigurację **bez badań**: `Daily.config(day, Bonuses())`, `BattleConfig(Mode.ENDLESS, seed, 1f)`, `BattleConfig(Mode.SKIRMISH, seed, 1f)`, `Campaign.config(stages[stage], Bonuses(), seed)`.
- Klient buduje bitwy **z badaniami**: `ModeScreen.kt` (`BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = pr.bonuses())`, potyczka z `aiPower()` = `SKIRMISH_POWER[profile.skirmishDiff]`), `CampaignScreen.kt` (`Campaign.config(s, g.profile.bonuses(), System.nanoTime(), …tutorial)`). `Daily.config` normalizuje bonusy (`normalizedBonuses`), więc **wyzwanie dnia już dziś przejdzie weryfikację**; endless, potyczka (inna siła AI) i kampania (badania, samouczek) – nie.
- Badania: definicje `Profile.research` (13 pozycji, `max` poziomów) i wzór `Profile.bonuses()` są w `:app` (`meta/Profile.kt`), więc serwer ich nie zna.
- Log: `BattleController.log()` → `Replay.encode(log)`; kontroler jest w `BattleSnapshot`, więc wznowiona bitwa ma ciągły log (sprawdź testem).
- Wysyłka wyników dziś: `BattleScreen.kt` ~l. 495–520 (`pr.submit(...)` lokalnie + `g.online.submit(board, score)` do Play Games). Interfejs `meta/OnlineLeaderboards.kt`.
- Aplikacja nie ma klienta HTTP ani identyfikatora gracza (UUID) w `Profile`.

## Decyzja projektowa (opisz w PR i w `docs/replay-integration.md`)
Wybierz i uzasadnij jedną z dróg; rekomendowana jest **B**:
- **A. Bitwy rankingowe bez badań:** osobny przełącznik „Rankingowa” w `ModeScreen` (endless/potyczka) i w kampanii; tylko takie bitwy idą na serwer. Prosto, uczciwie, ale gracz nie korzysta z postępu.
- **B. Przesyłanie poziomów badań:** przenieś definicje badań i wzór `bonuses()` do `:sim` (np. `sim/.../Research.kt`: `object Research { val defs; fun bonuses(levels: IntArray): Bonuses }`), `Profile` używa tej samej funkcji (bez zmiany formatu JSON profilu i bez zmiany wartości – test porównawczy). `ScoreSubmission` dostaje `research: List<Int>` (13 liczb w ustalonej kolejności) oraz `difficulty: Int?` (potyczka) i `tutorial: Boolean` (tylko `campaign_0`). Serwer waliduje zakresy (`0..max`, długość listy, `difficulty in 0..3`), buduje `Bonuses` tą samą funkcją, zapisuje sumę poziomów przy wyniku. Rankingi mogą (opcja) rozróżniać „bez badań” / „z badaniami” – zaproponuj.
Każda zmiana schematu = podbij `CW_GAME_VERSION`/wersję API; serwer odrzuca stare zgłoszenia czytelnym błędem.

## Zakres
1. **`:sim`/`:server`** wg wybranej drogi, z testami w `server/src/test/...`: poprawna powtórka endless/potyczki/kampanii z badaniami przechodzi; poziom spoza zakresu → `400`; zmienione poziomy przy tej samej powtórce → `422`.
2. **Klient HTTP** (`app/.../meta/ScoreClient.kt` albo `online/`): `HttpsURLConnection`, bez bibliotek, **na osobnym wątku** (jeden wątek roboczy, kolejka), nigdy na wątku gry ani UI. Timeouty 10 s, ponowienia z wykładniczym odstępem, kolejka niewysłanych zgłoszeń zapisana w pliku (przeżywa restart, maks. ~20 pozycji, odrzucane po 7 dniach). Podpis HMAC dokładnie jak w `server/README.md` (wspólny test wektora podpisu klient ↔ `Security.kt`).
3. **Konfiguracja:** adres serwera i klucz HMAC z `local.properties`/zmiennych CI do `BuildConfig` (dziś projekt go nie generuje: włącz `buildFeatures { buildConfig = true }` w `app/build.gradle.kts`; pole puste = funkcja ukryta w UI; sprawdź `ReleaseConfigTest`). Klucz nie trafia do repo. Opisz w PR, że klucz w APK nie jest sekretem (README serwera).
4. **Profil:** `playerId` (losowe UUID v4, tworzone przy pierwszym włączeniu funkcji), `globalRanking: Boolean = false`; zgodność wstecz, test w `ProfileTest`. Reset postępu nie zmienia `playerId`; dodaj osobną akcję „Nowy identyfikator rankingu”. Nick wysyłany po walidacji jak na serwerze (`validNick`).
5. **Ustawienia** (`screens/SettingsScreen.kt`): wiersz „Ranking globalny: Wył./Wł.”; pierwsze włączenie pokazuje krótkie wyjaśnienie, co jest wysyłane (pseudonim, losowy identyfikator, wynik, zapis komend bitwy), z linkiem do polityki prywatności. Teksty w 10 językach.
6. **Wysyłka** w `BattleScreen` po zakończeniu bitwy (tam, gdzie dziś `g.online.submit`): tylko gdy funkcja włączona i bitwa kwalifikuje się (np. wyzwanie dnia tylko podejście rankingowe `rankedDaily`; bitwy z samouczkiem – wg decyzji). Kodowanie powtórki i wysyłka poza wątkiem gry; ekran wyniku nie czeka na sieć. Status (wysłano / w kolejce / odrzucono) widoczny dyskretnie na ekranie wyniku lub w Rankingach.
7. **Rankingi** (`screens/RankingsScreen.kt`): zakładka „Globalne” z `GET /v1/top` i `GET /v1/around` (dzień/tydzień/całość), cache w pamięci, stan ładowania i błędu, działa offline (komunikat). Bez blokowania wątku gry.
8. **Testy:** `ReplayTest`/nowy test: powtórka bitwy klienta (z badaniami, po zapisie i wznowieniu przez `BattleSnapshot`) przechodzi `ReplayVerifier`; test podpisu; test kolejki (przepełnienie, wygasanie); test `ProfileTest` nowych pól; `ScreenshotTest` ustawień i zakładki globalnej.
9. Dokumentacja: `server/README.md` (nowe pola), `docs/replay-integration.md`; w PR lista zmian do `docs/store/*` (polityka i Data safety – nie edytuj, jeśli równolegle trwa zlecenie 20, tylko wypisz).

## Kryteria akceptacji
- Domyślnie nic nie jest wysyłane; po wyłączeniu funkcji kolejka jest czyszczona.
- Wynik z prawdziwej bitwy klienta (endless, potyczka, kampania, daily) przechodzi weryfikację serwera w teście JVM bez sieci (klient → JSON → `ReplayVerifier`).
- Brak I/O sieci i dysku na wątku gry; brak nowych bibliotek; aplikacja działa bez serwera i bez internetu.
- `./gradlew :server:test -PserverOnly=true`, `:app:testDebugUnitTest` (lista z promptu ogólnego) i `ProfileTest` przechodzą.

## Pliki
`sim/.../` (nowy `Research.kt` przy drodze B), `server/src/main/kotlin/com/centurywar/server/{Models,ReplayVerifier,Api}.kt` + testy, `app/build.gradle.kts` (pola `BuildConfig`), `app/.../meta/Profile.kt`, nowy klient w `app/.../meta/`, `screens/SettingsScreen.kt`, `screens/RankingsScreen.kt`, `screens/BattleScreen.kt`, `engine/L.kt`, `server/README.md`, `docs/replay-integration.md`.
