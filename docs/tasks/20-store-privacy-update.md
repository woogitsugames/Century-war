# Zlecenie 20: Aktualizacja materiałów sklepu i prywatności po Play Games i powiadomieniach

**Gałąź robocza:** `gpt/20-store-privacy-update` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Materiały do Google Play i polityki prywatności mają opisywać aplikację taką, jaka jest po scaleniu Google Play Games (gpt/01) i powiadomień (gpt/15). Dziś twierdzą „offline, bez usług sieciowych”, „integracja z Google Play Games nie jest aktywna”, a formularz Data safety odpowiada „Nie zbiera / nie udostępnia”.

## Stan faktyczny (sprawdź w kodzie i potwierdź w PR)
- `app/src/main/AndroidManifest.xml`: uprawnienia `VIBRATE`, `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; meta-data `com.google.android.gms.games.APP_ID`; `PlayGamesInitProvider` usunięty (`tools:node="remove"`), więc PGS startuje tylko, gdy `CenturyWarApp.playGamesReady`.
- `app/build.gradle.kts`: `play-services-games-v2` (sprawdź też zależności transytywne: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, oraz scalony manifest release `app/build/intermediates/merged_manifests/...` – czy nie dochodzą `AD_ID`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` itp.).
- `meta/PlayGamesLeaderboards.kt`, `meta/OnlineAchievements.kt`, `screens/PlayGamesScreen.kt`, `screens/SettingsScreen.kt`: logowanie do Play Games, wysyłanie wyników, osiągnięć i zapisu w chmurze (`Profile` jako snapshot). Ustal: kiedy następuje logowanie (automatycznie przy starcie czy po akcji gracza), co dokładnie trafia do Google (tablice, osiągnięcia, treść zapisu w chmurze: profil z pseudonimem, postępem, badaniami).
- `notifications/*`: lokalne powiadomienie o wyzwaniu dnia (AlarmManager, bez serwera push), zgoda `POST_NOTIFICATIONS`, ponowne planowanie po restarcie (`RECEIVE_BOOT_COMPLETED`). Brak FCM.
- Serwer rankingu (`server/`) **nie jest jeszcze używany przez aplikację** (zlecenie 23). Nie opisuj go jako aktywnego; zostaw w sekcji „planowane” i dopisz, co się zmieni po włączeniu.

## Zakres
1. `docs/store/data_safety.md`: usuń ramkę „Do aktualizacji…”, przepisz „Potwierdzone w kodzie” i tabelę odpowiedzi dla wersji z PGS. Rozdziel: dane przetwarzane przez Google Play Games jako dostawcę usługi (identyfikator gracza, wyniki, osiągnięcia, zapis gry) vs dane wydawcy. Dla każdej kategorii Play Console (np. „Identyfikatory urządzenia lub inne”, „Aktywność w aplikacji / Inne treści generowane przez użytkownika”, „Informacje o aplikacji i jej działaniu”) podaj odpowiedź, cel, czy opcjonalne, szyfrowanie w transmisji, możliwość usunięcia, z uzasadnieniem i linkiem do aktualnej dokumentacji Google o deklarowaniu danych SDK Play Games. Gdzie odpowiedź zależy od decyzji wydawcy albo od dokumentacji Google, oznacz **[DO DECYZJI]** zamiast zgadywać.
2. `docs/store/privacy_policy_pl.md` i `privacy_policy_en.md` (treść równoważna): nowa data obowiązywania, sekcje: Google Play Games (opcjonalne logowanie, co jest przesyłane, link do polityki Google, jak się wylogować i usunąć dane gry z Play Games), powiadomienia (lokalne, wyłączane w Ustawieniach gry i systemu, bez danych wysyłanych na serwer), uprawnienia z wyjaśnieniem każdego, kopia zapasowa Androida (bez zmian). Usuń zdania „wersja bez usług sieciowych”, „integracja nie jest aktywna”. Zachowaj placeholdery `[UZUPEŁNIJ_…]`.
3. `docs/store/listing_pl.md` i `listing_en.md`: krótki i pełny opis bez fałszywego „offline / bez internetu”. Zgodnie z prawdą: rozgrywka działa bez internetu; opcjonalnie rankingi i osiągnięcia Google Play Games oraz zapis w chmurze; przypomnienie o wyzwaniu dnia. Krótki opis ≤ 80 znaków, tytuł ≤ 30 znaków (policz i podaj liczby w PR). Zaktualizuj „Kontrolę przed publikacją” (rankingi lokalne i globalne przez Play Games). Bez nawiązań do innych gier i bez obietnic funkcji niewdrożonych (reklamy, zakupy, multiplayer).
4. `docs/store/content_rating.md`: odpowiedzi IARC dla interakcji użytkowników po dodaniu Play Games (publiczne pseudonimy na tablicach wyników Google; brak czatu i wymiany treści) i dla „udostępniania lokalizacji” (nie). Dopisz, kiedy ankietę powtórzyć (po zleceniu 23).
5. Jeśli w aplikacji nie ma linku do polityki prywatności (sprawdź `SettingsScreen.kt`), opisz to w `data_safety.md` jako otwarty punkt. **Nie zmieniaj kodu** w tym zleceniu.

## Kryteria akceptacji
- Żaden plik w `docs/store/` nie twierdzi, że aplikacja nie ma usług sieciowych ani że Play Games jest nieaktywne; `grep -ri "offline\|bez usług sieciowych\|no network" docs/store` zwraca tylko zdania prawdziwe (np. „rozgrywka działa offline”).
- Wersje PL i EN polityki i listingu są merytorycznie identyczne.
- Każde twierdzenie o danych ma pokrycie w kodzie (w PR tabela: twierdzenie → plik/linia).
- Limity znaków Play Console spełnione; brak emoji i nazw innych gier.

## Pliki
`docs/store/data_safety.md`, `privacy_policy_pl.md`, `privacy_policy_en.md`, `listing_pl.md`, `listing_en.md`, `content_rating.md` (tylko dokumentacja; kod czytasz, nie edytujesz).
