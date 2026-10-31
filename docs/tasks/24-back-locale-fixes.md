# Zlecenie 24: Poprawki z przeglądu: configChanges locale i predictive back

**Gałąź robocza:** `gpt/24-back-locale-fixes` (utwórz z `origin/claude/bold-sagan-vwz5gm`)

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
Dwie poprawki z przeglądu kodu (`docs/HANDOFF.md`, ryzyka): zmiana języka systemu nie może restartować gry w trakcie bitwy, a gest Wstecz w menu głównym ma pokazywać systemową animację predictive back.

## Stan obecny (sprawdzone w kodzie)
- `app/src/main/AndroidManifest.xml`, `MainActivity`: `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|navigation|density|uiMode|fontScale"` – brak `locale|layoutDirection`. Aplikacja ma `android:localeConfig="@xml/locales_config"` (języki aplikacji w ustawieniach Androida 13+), więc zmiana języka systemu lub języka aplikacji restartuje Activity (bitwa przechodzi przez zrzut `BattleSnapshot` i pytanie o wznowienie zamiast trwać dalej).
- `MainActivity.kt` nie nadpisuje `onConfigurationChanged`. Język gry: `Game.applyLanguage()` → `L.setCode(profile.lang)`; `profile.lang == ""` oznacza język systemu (`L.systemLang()`). Ustawienia (`SettingsScreen.kt`, wiersz `lang`) zmieniają `p.lang` i wołają `g.applyLanguage()`.
- `android:enableOnBackInvokedCallback="true"`; w `MainActivity.onCreate` (API 33+) zawsze zarejestrowany `OnBackInvokedCallback` z `PRIORITY_DEFAULT` → `game.requestBack()`. `Game.back()`: `screen.onBack()`, potem `pop()`, a w `MenuScreen` bez obsługi → `host.exit()` → `finish()`. Ponieważ callback jest zawsze zarejestrowany, system nie pokaże animacji powrotu do ekranu głównego (predictive back) w menu głównym. `MenuScreen.onBack()` obsługuje tylko zamknięcie oferty wznowienia (`g.resumeOffer`).

## Zakres
1. **configChanges:** dopisz `locale|layoutDirection` w manifeście. W `MainActivity.onConfigurationChanged` (na wątku UI) zleć przez `game.post { … }`: gdy `profile.lang` jest puste – `applyLanguage()` i odświeżenie tekstów bieżącego ekranu (tak jak po zmianie języka w Ustawieniach; sprawdź, jak `SettingsScreen` odświeża etykiety, i jeśli trzeba dodaj w `Game` metodę typu `onLanguageChanged()` wołającą `layout()` bieżącego ekranu i ekranów na stosie). Teksty cache'owane w ekranach (np. `MissionsScreen`, `BattleScreen` – `waveText`, etykiety przycisków) muszą się odświeżyć. Kierunek układu się nie zmienia (`supportsRtl="false"`, brak języków RTL) – dopisz to w komentarzu.
2. Opcjonalnie, jeśli proste: synchronizacja z językiem aplikacji Androida 13+ (`LocaleManager.applicationLocales`) – wybór w Ustawieniach gry ustawia też język aplikacji w systemie i odwrotnie. Jeśli tego nie robisz, opisz w PR.
3. **Predictive back:**
   - `Game` udostępnia stan „Wstecz zostanie obsłużony w grze” (`wantsBack`): true, gdy jest stos ekranów, bieżący ekran nie jest `MenuScreen`, albo `MenuScreen` ma otwarte okno (oferta wznowienia lub inne modalne elementy). Wartość liczona na wątku gry, publikowana do `MainActivity` tylko przy zmianie (np. `@Volatile` + `runOnUiThread`), bez alokacji na klatkę.
   - `MainActivity` (API 33+) rejestruje callback, gdy `wantsBack == true`, i wyrejestrowuje, gdy `false` – wtedy system sam zamyka Activity z animacją powrotu do pulpitu. Zapis profilu i zrzut bitwy nadal przez `onPause` (sprawdź, że wyjście bez `game.requestBack()` niczego nie gubi).
   - Android < 13: zachowaj `onBackPressed` jak dziś.
   - Nie rejestruj animacji wewnątrz gry (`OnBackAnimationCallback`) – poza zakresem, chyba że trywialne.
4. **Testy:**
   - Robolectric: `Game` w `MenuScreen` bez oferty wznowienia → `wantsBack == false`; z ofertą → `true`; po wejściu w Ustawienia → `true`; po powrocie → `false`.
   - Robolectric: zmiana `Locale` + wywołanie ścieżki z `onConfigurationChanged` przy `profile.lang == ""` zmienia `L.lang` i etykiety bieżącego ekranu; przy ustawionym języku w profilu – bez zmian.
   - `ReleaseConfigTest`: asercja, że `configChanges` zawiera `locale` i `layoutDirection`.

## Kryteria akceptacji
- Zmiana języka systemu w trakcie bitwy nie restartuje Activity (bitwa trwa, bez pytania o wznowienie), teksty zmieniają się, gdy profil ma język „Auto”.
- Na API 33+ w menu głównym gest Wstecz daje systemową animację wyjścia; na innych ekranach Wstecz działa jak dziś (pauza w bitwie, zamknięcie okien, powrót).
- Testy z pkt 4 i lista z promptu ogólnego przechodzą.

## Pliki
`app/src/main/AndroidManifest.xml`, `app/src/main/java/com/centurywar/MainActivity.kt`, `engine/Game.kt`, `screens/MenuScreen.kt` (stan modalny), ewentualnie ekrany z cache tekstów; testy w `app/src/test/java/com/centurywar/` (`ReleaseConfigTest.kt` + nowy test Robolectric).

> **Uwaga koordynatora (31.10.2026):** `MainActivity` ma już `onConfigurationChanged` (ustawia orientację: telefony tylko poziom, ekrany od 600 dp dowolna). Odświeżanie tekstów dopisz w tej samej metodzie, nie zastępuj jej.
