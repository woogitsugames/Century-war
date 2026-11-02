# NAJNOWSZE (2.11.2026 ok. 20:30 UTC)
- `integ/tura10` = main + odtwarzacz nagranej muzyki (agent, pełne testy app zielone na jego gałęzi) + 21 utworów OGG w `app/src/main/assets/music/` (21 MB, APK 25 MB). Szybkie testy (Music*, BattleScreenTest, Startup) zielone; **pełnych testów po scaleniu muzyki nie uruchomiono — uruchom je i wypchnij integ/tura10 na main + bold-sagan**. APK z muzyką wysłane właścicielowi do odsłuchu (czekamy na uwagi: głośność, pętle, przenikanie).
- Uwaga z raportu agenta: gra przejmuje fokus audio (zatrzymuje Spotify itp.); pętle MediaPlayer mają krótki „oddech” na złączeniu.

# Century War: stan 2.11.2026 ok. 19:45 UTC (tura 10, późny wieczór)

- `main` = `claude/bold-sagan-vwz5gm` = `integ/tura10` = 5081cd7b (+ ten wpis): tura 10 w całości — szlif przed premierą 1 i 2, doktryny (PROTOCOL 0.8.2), strojenie etapów 16–18/34–36, GPT 146–149, 152 (wydajność), 153 (grafiki sklepu), 154 (odporność zapisu). Pełne testy zielone. APK wysłane.
- **Muzyka (w toku):**
  - Oryginały od właściciela jako FLAC na osobnej gałęzi **`music-src`** (`originals/*.flac`, README z listą). **Komplet 21/21** (181 MB FLAC). Nowe pliki od właściciela: od razu FLAC na `music-src` (`ffmpeg -i X.wav -c:a flac -compression_level 8 originals/X.flac`).
  - Agent „Nagrana muzyka” buduje odtwarzacz (`RecordedMusic`, `RecordedMixer`, `MusicCatalog` w `engine/`, skrypt `scripts/convert_music.sh`, pliki `app/src/main/assets/music/*.ogg`, przenikanie calm↔battle wg intensywności, fallback do syntezy). Gałąź docelowa `claude/tura10-muzyka`; kopia niedokończonej pracy: `claude/wip-muzyka` (333acb1d: zmiany w Music.kt, MainActivity, BattleScreen, build.gradle.kts, nowe RecordedMusic/RecordedMixer/MusicCatalog + RecordedMusicTest, docs/music/README.md, LICENSE_NOTES.md; skrypt convert_music.sh jeszcze nie). Jeśli przepadł — zlecić ponownie wg `docs/tasks/142-recorded-music.md` + ustaleń wyżej.
  - Po odtwarzaczu: skonwertuj FLAC z `music-src` skryptem do `assets/music/`, commit na main, APK do odsłuchu.
- **GPT w toku:** 155 (tłumaczenia part145/150/151), 156 (audyt przed premierą — sam raport `docs/reports/audit-156.md`; przełożyć właścicielowi na decyzje klikalne), 157 (grafiki sklepu: zwycięstwo na wykresie, realny czas bitwy). Sprawdzaj `origin/gpt/15[5-7]-*`, scalaj po 10 min bez zmian, usuwaj jednorazowe workflowy GPT (`.github/workflows/*-15x.yml`).
- Testy: `forkEvery = 40` (OOM pamięci natywnej zrzutów). Pełne ok. 10–15 min.

# Century War: stan po turze 10 (2.11.2026, popołudnie)

- `main` = `claude/bold-sagan-vwz5gm` = `integ/tura10`: tura 9 (125 dowódca, 136 tłumaczenia, przegląd UX dowódcy i wież), fala 5 GPT (146 poprawki UI, 147 menu w pionie, 148 kontrola R8, 149 serwer), pomiar balansu z dowódcą, strojenie etapów 16–18 i 34–36 (PROTOCOL 0.8.1). Pełne testy zielone (ok. 9–11 min). APK wysłane.
- Testy: `forkEvery = 40` w `app/build.gradle.kts` — wcześniej jeden proces testów rósł do ~10 GB pamięci natywnej (OOM 137).
- Decyzje właściciela (tura 10): etapy 16–18 i 34–36 lżejsze (zrobione); etap 36 zostaje (76% dla gracza z pełnymi badaniami bez dowódcy); DoctrineBalance82 na 100 seedach, pasmo 40–65%; menu-lista tylko gdy wysokość > 1,3× szerokości lub < 520 dp.
- Otwarte: PLUNDER 63,5% / ARSENAL 42% w lustrzanym teście doktryn (brzegi pasma); Endless fala 60 = 7/100 (blisko dolnego celu 5%); 142 muzyka czeka na pliki Suno.
- Kolejne pomysły: wydanie testowe (release na telefonie, testy zamknięte wg `docs/RELEASE.md`), wdrożenie serwera wg `docs/SERVER_DEPLOY.md`.

# Century War: przekazanie koordynacji (2.11.2026, ok. 11:00 UTC, tura 9)

Plik dla nowej sesji Claude Code. Przeczytaj w całości przed działaniem. Starsza historia (tury 6–8) jest niżej w sekcji „Archiwum”.

## Rola i zasady
- Koordynator projektu **Century War**: oryginalna strategia na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek poza Play Games, grafika rysowana kodem). Repo `woogitsugames/century-war`.
- Właściciel nie jest programistą: odpowiadaj po polsku, zwięźle, tłumacz skutki. Decyzje dawaj w formie klikalnej (AskUserQuestion).
- Commity po polsku z liniami atrybucji sesji. Bez PR. Bez identyfikatorów modeli w repo.
- Gałęzie: `main` = `claude/bold-sagan-vwz5gm` (baza zleceń GPT). Pracuj na `integ/turaN`, po zielonych pełnych testach wypychaj na oba. Właściciel pozwala scalać wszystko do main bez pytania.
- Agenci: **najwyżej 1 dodatkowy agent opus naraz** (limit konta). Agentom każ uruchamiać Gradle przez `flock /tmp/claude-0/gradle.lock ./gradlew ...` (dwa Gradle naraz = OOM). Długie komendy tylko Bash `run_in_background`.
- Po większych scaleniach wysyłaj APK (`app/build/outputs/apk/debug/app-debug.apk`, skopiuj do scratchpadu i SendUserFile). Właściciel testuje na telefonie i Galaxy Fold (zewnętrzny 904x2316, wewnętrzny 2184x1968).

## Środowisko (nowy kontener)
1. Android SDK: jak w sekcji archiwalnej (cmdline-tools → `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`; `local.properties` z `sdk.dir`).
2. Init Gradle `~/.gradle/init.d/mirror.gradle`: mirror GCS `https://maven-central.storage-download.googleapis.com/maven2/` — podmiana URL repo.maven.apache.org we wszystkich repozytoriach ORAZ w `beforeSettings` dodanie mirrora na początek `pluginManagement.repositories` + `gradlePluginPortal()` (inaczej buildSrc dostaje 429). Nie dodawaj repozytoriów w `allprojects` (FAIL_ON_PROJECT_REPOS).
3. Robolectric: ręcznie pobierz `android-all-instrumented` 15-robolectric-13954326-i7, 9-robolectric-4913185-2-i7, 10-robolectric-5803371-i7 z mirrora do `~/.m2/repository/org/robolectric/android-all-instrumented/<wersja>/`.
4. Pełne testy: `./gradlew :app:testDebugUnitTest :sim:test :server:test --continue --console=plain` — teraz **ok. 8–11 min**. Narzędzia scalania: `docs/coord-tools/` (res.py, mergeauto.sh, fixL.py). Po każdym scaleniu `L.kt`: liczba `putAll(part` = liczba `private fun part` (teraz 49).
5. `-PscreenReason=...` przy `updateScreenshots` podawaj bez polskich znaków (inaczej krzaki w UPDATES.md).

## Stan na moment przekazania
- `main` = `claude/bold-sagan-vwz5gm` = **969b0414** (tura 9 bez dowódcy): GPT 135 (sklep, „Co nowego”), 140 (stopnie wież 1–3), przeglądy UX nowego gracza (143, 144), naprawa „Porzuć” w oknie wznowienia, uczciwe elity i Zryw wroga, PROTOCOL 0.7.0. Pełne testy zielone. APK wysłane.
- **`integ/tura9` = 17da424a**: main + scalona paczka GPT **125 (dowódca)** + PROTOCOL **0.8.0** + rozszerzone zlecenie 136. **NIE na main — 12 czerwonych testów:**
  - `HintOverlayTest` (5) i `ScreenshotTestTranslation78Test` (de/tr/uk): podpowiedzi w bitwie się nie pojawiają („expected:<0>/<19> but was:<-1>”) — 125 zmienił `HintOverlay`.
  - `LeftHandedHudTest.widerHud119KeepsExistingRows` / `twoRowActionsStayAtLeftOnMediumWidth`: siódmy przycisk (dowódca) zmienia układ akcji w `BattleBottomHud`. Przed odblokowaniem przycisk ma być ukryty i układ identyczny jak przed 125.
  - `ScreenshotTest.regression100`: `phone720_reg100_battle_hud` różni się o 1,2% (sprawdź, czy przycisk dowódcy nie pojawia się przed etapem 8).
  - `DoctrineBalance82Test`: ECONOMY 70%, ARSENAL 37,5%, PLUNDER 62,5% (cel 40–60%) — AI używa dowódcy samodzielnie w potyczce.
  - Agent naprawczy został zatrzymany w trakcie; jego niedokończone zmiany (Profile.kt, HintOverlayTest, tymczasowy TmpHudProbeTest do usunięcia) są w `claude/wip-tura9-fix125` (97c26198, na integ/tura9 17da424a) — przejrzyj i dokończ albo zacznij od nowa. **Pierwsze zadanie nowej sesji: naprawić te testy, pełne testy, wypchnąć integ/tura9 na main + bold-sagan, APK dla właściciela.**
- **GPT:** fala 4 (**136** przegląd tłumaczeń, zakres part124–part144) — zip dany właścicielowi z prośbą, by wysłał DOPIERO gdy 125 będzie na main. Daj mu znać po wypchnięciu. Następnie sprawdzaj `origin/gpt/136-*`, przejrzyj, usuń tymczasowe workflowy i zrzuty z `docs/reports`, scal.
- **142 (muzyka Suno):** opisy w `docs/music/SUNO_PROMPTS.md`; zlecenie `docs/tasks/142-recorded-music.md` czeka na pliki w `music-src/`.
- Zaplanowane zadanie „Century War: nocne wznawianie pracy” (trig_01AzV7aUMNZTRAk489MEmYEL, co godzinę o :33) wznawia STARĄ sesję — w nowej sesji wyłącz je albo utwórz własne (właściciel o nie prosił).

## Decyzje właściciela (obowiązujące)
- Uczciwe AI: w kampanii/potyczce wróg ma elity i Zryw tylko gdy gracz je zbadał; limit armii wroga 12; ranking kampanii tylko z poziomu Normalny.
- Zatwierdzony układ paska bitwy `compactTop` i celowanie ostrzału (przeciągnij-puść; celownik omija własnych żołnierzy) — nie zmieniać bez zgody.
- Powiadomienia nie pokazują się na wynikach bitwy (tylko w menu).
- Dowódca (125) — tak; stopnie wież (140) — tak; muzyka Suno — opisy dla wszystkich epok.
- `Daily.RELEASE_DAY` = 2027-12-01, nie zmieniać bez decyzji.

## Otwarte do decyzji właściciela
- Etapy 28–36: gracz zbierający medale tylko z kampanii wygrywa tam 10–54% (szczegóły w `docs/BALANCE.md`, „Tura 8 — uczciwe elity wroga”).
- Pion telefonu w menu (podzielony ekran): podpisy kafelków ok. 8 sp; propozycja układu jednej kolumny w raporcie UX 2 (manifest wymusza `sensorLandscape`, więc dotyczy tylko podzielonego ekranu).

## Kolejne pomysły (gdy nic nie czeka)
- Przegląd UX po dowódcy i wieżach (jak `NewPlayerJourneyTest`), test balansu kampanii z dowódcą dla profilu „nowy gracz”, wydanie testowe (R8 release na telefonie, Play Console testy zamknięte), wdrożenie serwera rankingu (`docs/SERVER_DEPLOY.md`).

---

# Archiwum

# Century War: przekazanie koordynacji (1.11.2026, wieczór, tura 6)

Plik dla nowej sesji Claude Code (inne konto). Przeczytaj w całości przed działaniem.

## Rola i zasady

Jesteś koordynatorem projektu **Century War**: oryginalna strategia na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek poza Play Games, grafika rysowana kodem). Repo: `woogitsugames/century-war`.

- Gałąź główna `main`. Ten sam commit wypychaj także na `claude/bold-sagan-vwz5gm` (baza zleceń GPT) i na gałąź swojej sesji.
- Użytkownik nie jest programistą: odpowiadaj po polsku, zwięźle, tłumacz skutki. Commity po polsku z liniami atrybucji sesji. Bez PR, chyba że poprosi. Bez identyfikatorów modeli w repo.
- Po większych scaleniach wysyłaj APK (`app/build/outputs/apk/debug/app-debug.apk`), użytkownik testuje na telefonie i Galaxy Fold (ekran zewnętrzny i wewnętrzny).
- Agenci: tylko w liczbie, na którą użytkownik wyraźnie się zgodzi w Twojej sesji.
- **Pamięć kontenera:** dwa równoległe Gradle z testami = OOM (exit 137). Agentom każ uruchamiać Gradle przez `flock /tmp/claude-0/gradle.lock ./gradlew ...`. Długie komendy tylko przez Bash `run_in_background` (procesy z `&` giną po turze).

## Przygotowanie środowiska (w kontenerze nie ma Android SDK)

```bash
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk
curl -sSfL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-36" "build-tools;36.0.0" "platform-tools" > sdk.log
cd /workspace/Century-war && echo "sdk.dir=$HOME/android-sdk" > local.properties
```

Maven Central odpowiada 429. Dodaj (poza repo) skrypt init Gradle, który przekierowuje go na mirror GCS:

```bash
mkdir -p ~/.gradle/init.d && cat > ~/.gradle/init.d/mirror.gradle <<'EOF'
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def fix = { RepositoryHandler repos ->
    repos.all { r ->
        if (r instanceof MavenArtifactRepository && r.url.toString().startsWith('https://repo.maven.apache.org')) {
            r.url = mirror
        }
    }
}
settingsEvaluated { s ->
    fix(s.pluginManagement.repositories)
    fix(s.dependencyResolutionManagement.repositories)
}
allprojects { fix(repositories); buildscript { fix(repositories) } }
EOF
```

Robolectric pobiera obraz Androida (~200 MB) sam i też dostaje 429. Pobierz go ręcznie:

```bash
d=~/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7; mkdir -p $d && cd $d
for e in jar pom; do curl -sSfL -o android-all-instrumented-15-robolectric-13954326-i7.$e https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7/android-all-instrumented-15-robolectric-13954326-i7.$e; done
```

Emulatora nie ma (brak KVM). Wygląd sprawdzasz zrzutami z testów (`app/build/screenshots/*.png`, czytaj je narzędziem Read).

## Komendy

```bash
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest :sim:test :server:test --continue --console=plain   # pełne: ok. 60 min, ZAWSZE Bash run_in_background
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Profile*' --tests '*Startup*' --console=plain   # szybkie
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest.menus*' --console=plain --no-configuration-cache   # zrzuty 720p, 20:9, Fold poziom i pion
./gradlew :app:testDebugUnitTest --tests '*ArtGalleryTest*' --console=plain   # galeria jednostek, baz i pasek animacji
BALANCE_RUN=true BALANCE_RUNS=8 BALANCE_PROFILE=target ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.campaignReport*'   # balans, wyniki w app/build/balance/
```

`--rerun` przy balansie jest konieczne: Gradle nie widzi zmiennych środowiskowych jako wejścia testu.


Dodatkowe obrazy Robolectric (pobierz tak samo jak wyżej, z mirrora GCS): `android-all-instrumented` w wersjach `9-robolectric-4913185-2-i7` i `10-robolectric-5803371-i7`. Testy Robolectric wymagają `unitTests.isIncludeAndroidResources = true` (już ustawione).

## Narzędzia koordynatora

Skrypty w `docs/coord-tools/` (skopiuj do `/tmp/claude-0/tools/` i popraw ścieżkę `cd` w `mergeauto.sh` na swój worktree):
- `mergeauto.sh N`: scala `origin/gpt/N-*`, konflikty w `L.kt` i `android.yml` rozwiązuje jako „oba”, wypisuje pozostałe konflikty.
- `res.py PLIK K ours|theirs|both`: rozwiązuje K-ty blok konfliktu.
- `addfn.py PLIK GAŁĄŹ FUNKCJA`: pozostałe konflikty jako „ours” i dopisuje funkcję testową z gałęzi (dla dwóch nowych testów w tym samym miejscu, np. `ScreenshotTest.kt`).
- `fixL.py`: naprawia gubione nawiasy w `L.kt` po scaleniach.

**Pułapka `L.kt`:** tabela tekstów to `val table by lazy { HashMap().apply { putAll(part0()) … } }`, a każda paczka dopisuje `private fun partNN()` + `putAll(partNN())`. Po scaleniu „oba” linie `putAll` często lądują za nawiasem `}` albo w środku tablicy innej funkcji. Sprawdzaj: liczba `putAll(part` = liczba `private fun part`, a kompilacja przechodzi.

## Stan na moment przekazania

- `main` = `claude/bold-sagan-vwz5gm` = `487a82b` (tura 5 scalona, testy zielone, APK wysłane; dodane zlecenia 92–117).
- **Gałąź integracyjna tury 6: `claude/peaceful-ride-0azzze`** (i kopia `integ/tura6`). Zawiera scalenie paczek GPT 92–114 + poprawki 5 agentów. **NIE jest jeszcze w pełni przetestowana i NIE jest na `main`.**
  - Scalone: 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114.
  - **Nie scalone:** 115 (podział `BattleScreen.kt`: odrzucony, bo koliduje z 104/110; zlecić od nowa na nowej bazie), 116 (przegląd tłumaczeń: wnioski już naniesione w `L.kt`, gałęzi nie scalać), 117 (grafiki sklepu: przegląd OK, scalić na końcu).
- Poprawki po przeglądach (zrobione przez agentów w worktree, wypchnięte w tej gałęzi):
  - serwer: limit kroków weryfikacji = `Replay.MAX_STEPS`, zwrot dziennego limitu przy 503, rotacja kopii ratunkowych, `SimVersion.PROTOCOL` = 0.5.0; usunięto 22 jednorazowe workflowy GPT; `release-check.yml` z syntetycznymi ID Play Games także dla bazy; `scripts/ci_test_shards.py` obsługuje wiele klas w pliku; kategorie testów (`gradle/test-categories.properties`).
  - teksty: ujednolicone nazwy Ostrzału/Zrywu/wieżyczek, „Endless” przetłumaczone, nowe testy w `LTest`.
  - start i profil (107 import kopii, 106 „Co nowego” kluczowane wersją, 101 start audio po pierwszej klatce, 114 przywrócona kopia zapasowa Androida dla profilu, 105 historia), bitwa/dźwięk (110 HUD leworęczny bez kolizji z zatwierdzonym paskiem, 104 wykres, 102/103 dźwięk i muzyka), balans (97 RNG AI, 109 tygodnie, pomiar kampanii i Endless po scaleniu) — **sprawdź raporty w sekcji „Wyniki agentów” niżej; jeśli czegoś brakuje, dokończ.**
- Zatwierdzony przez użytkownika układ paska bitwy na wąskich ekranach (`compactTop` w `BattleScreen.layout`): minimapa w środku drugiego rzędu, liczebność armii z lewej, pogoda z prawej, obie wyśrodkowane na wysokości minimapy; Ewolucja i pauza w pierwszym rzędzie (48 dp). Nie zmieniać bez zgody.
- Celowanie ostrzału: przeciągnięcie przesuwa znacznik, puszczenie palca od razu odpala (zatwierdzone).

## Co dalej (kolejno)

1. Sklonuj/przełącz na `claude/peaceful-ride-0azzze`, przygotuj środowisko, `./gradlew :app:compileDebugUnitTestKotlin :server:compileTestKotlin :sim:compileTestKotlin`.
2. Pełne testy w tle: `./gradlew :app:testDebugUnitTest :sim:test :server:test --continue --console=plain`. Napraw błędy. Uwaga: `./gradlew test` uruchamia też release i pada na `releaseChecklist` (brak danych właściciela) — to oczekiwane, CI używa `testDebugUnitTest`.
3. **Wzorce zrzutów (paczka 100):** po wszystkich zmianach UI wzorce w `app/src/test/resources/screens/` będą nieaktualne. Przed regeneracją: w `ScreenshotTest.regression100` przed `CampaignScreen` oznacz zapowiedzi epok jako widziane (inaczej oba zrzuty kampanii pokazują nakładkę epoki), zaostrz `MAX_CHANGED_FRACTION` do 0.001, usuń maskę `battle_hud`, dodaj `require(selected.isNotEmpty())`. Potem `./gradlew :app:updateScreenshots -PscreenReason="Tura 6: ..." --no-configuration-cache` i obejrzyj PNG.
4. Scal `origin/gpt/117-store-screenshots-3` (uwaga z przeglądu: `StoreGraphicsTest.drawGameplay` `check(screen.onBack())` zawsze true — lepiej sprawdzić stan `paused`).
5. Złote powtórki symulacji: jeśli `GoldenReplayTest` pada po zmianach balansu, `./gradlew :sim:regenerateGolden -PserverOnly=true -PgoldenReason="..." --no-configuration-cache` (zadanie kończy się błędem configuration cache, ale pliki zapisuje; sprawdź wpis na końcu `docs/BALANCE.md` pod kątem krzaków w kodowaniu).
6. Zielone testy → wypchnij na `main`, `claude/bold-sagan-vwz5gm` i swoją gałąź; wyślij APK; krótko opisz użytkownikowi zmiany (wykres po bitwie, „Co nowego”, kopia profilu, HUD dla leworęcznych, daltonizm, nowe karty Endless, nowe wyzwania, dźwięk i muzyka).
7. **Paczki dla GPT po wypchnięciu nowej bazy** (użytkownik o nie prosi):
   - powtórka 115: podział `BattleScreen.kt` (czysto mechaniczny, zrzuty identyczne),
   - dolny pasek bitwy na zewnętrznym ekranie Folda (904×2316, gęstość ~2,7): przyciski ok. 27 dp, cel 48 dp,
   - przegląd tłumaczeń nowych ekranów (wykres po bitwie, kopia profilu, „Co nowego”, Play Games) + zrzuty najdłuższych języków,
   - pomiar czasu weryfikacji serwera dla powtórki 432 000 kroków (limit 15 s),
   - ewentualne poprawki balansu z raportu agenta balansu.
   Format zleceń: jak `docs/tasks/92-*.md` (wspólny prompt + „Prompt zlecenia”), dopisz falę w `docs/tasks/DISPATCH.md`, daj użytkownikowi zip.

## Decyzje właściciela (`docs/store/OWNER_INPUT.md`)

- Data premiery: nieustalona (`Daily.RELEASE_DAY` = 2027-12-01, nie zmieniać bez decyzji; przed premierą ustawić na faktyczny dzień, nigdy w przeszłości).
- Wydawca: firma/JDG (konto organizacji, D-U-N-S). Politykę prywatności i dane wydawcy ogarnia inna osoba.
- Play Games: włączone na premierę — potrzebne ID projektu, 4 rankingów i osiągnięć w `docs/store/play_games_ids.properties` (paczka 92, instrukcja w `docs/RELEASE.md`).

## Przed wydaniem (poza kodem)

Wersja release (R8) sprawdzona na telefonie, Play Games z prawdziwymi ID, wdrożenie serwera rankingu (hosting, https, sekrety; `docs/SERVER_DEPLOY.md`), testy zamknięte w Play Console na kilku tanich/starszych telefonach.

## Wyniki agentów (tura 6)

- **Serwer + CI:** zrobione wszystko; `:server:test` 62/62, testy Pythona 12/12, `ci_test_shards.py verify` OK. Nie zmierzono czasu weryfikacji 432 000 kroków. Testy `:app`/`:sim` po podbiciu PROTOCOL nieuruchomione (zmieniono też `ReleaseConfigTest`).
- **Teksty:** zrobione; `LTest*` zielone (18 + 3).
- **Start i profil:** zrobione 8/8; testy grupy zielone. Czerwony `ProfileTest.standardPalettePreservesLegacyColors` (oczekuje `Ui.BTN_GREEN = 0xFF409646`) — skutek palety z 111, do rozstrzygnięcia (zaktualizować test albo przywrócić kolor w palecie standardowej). Do dopisania: `docs/RELEASE.md` sekcja „Języki i backup” (kopia obejmuje `profile.json` i `history.json`, wymóg szyfrowania).
- **Bitwa i dźwięk:** zrobione 5/5; testy grupy zielone (BattleScreenTest 22, Music* 25, LeftHandedHud*, BattleSummary*, Replay*, Stats). Uwagi:
  - `ScreenshotTest.foldAudit98` i `layoutCollisions` padają: „campaign 904x2316 pl: kolizja przycisków 3 i 4” (ekran kampanii na zewnętrznym ekranie Folda) — do naprawy w `CampaignScreen`.
  - Na zrzucie wyników powiadomienie „Daily streak” zasłania zakładki wyników — sprawdzić `Game` (powiadomienia mają być wstrzymane, gdy `screen.blocksNotices`).
  - Muzyka: podniesiono głośność warstw starcia (test `changingIntensityAddsLayersSmoothly` padał już po scaleniu) — dobrze, by użytkownik odsłuchał.
  - Tryb leworęczny na 720x1280: nazwa epoki wroga się nie mieści i nie jest rysowana; w trybie domyślnym na 720x1280 Ewolucja nachodzi na pauzę (stan sprzed zmian).
- **Balans: PRZERWANY (brak czasu).** Agent zdążył (w tej gałęzi): poprawki z punktów 97 (RNG AI przed warunkiem, preferencje dla kart 108), 109 (tygodnie, komentarze, test), 95 (asercje raportu, uczciwa tabela) — **sprawdź `git log -p` i testy `:sim:test` oraz `app game/**`, bo nie wiadomo, czy wszystko zostało przez niego przetestowane.** Pomiar po scaleniu 96+97+108+109 wykazał (wstępnie): Endless fala 60 zbyt łatwa (wcześniej 4/20 seedów), kampania etap 16 ok. 40% wygranych (za trudny), etap 23 ok. 90% (za łatwy). Agent testował strojenie, które **cofnięto jako niezmierzone**:
  - `Battle.kt` skalowanie fal Endless: `hpMul` nachylenie po fali 48 z 0,06 na 0,07 albo dodatkowy mnożnik `dmgMul` `(1 + max(0, wave-48)*0.05)`;
  - `Modes.kt` `stageRamp`: etap 16 0,97→0,94, etap 23 0,92→0,966.
  Do zrobienia: pełny pomiar (Endless 100 seedów, kampania 36 etapów × 20 seedów), strojenie, wpis w `docs/BALANCE.md` (sekcja „Tura 6 – pomiar po scaleniu”), regeneracja złotych powtórek, jeśli się zmienią.

## Decyzje właściciela (tura 7, 1.11.2026)
- Dowódca na polu bitwy (zlecenie 125): TAK, w fali 2.
- Poziomy trudności kampanii (126): do rankingu kampanii liczy się tylko poziom Normalny. Przy przeglądzie 126 sprawdzić, że wyniki z Łatwego/Trudnego nie trafiają do rankingu.
- Banery powiadomień nie pokazują się na wynikach bitwy, tylko w menu (`BattleScreen.blocksNotices` zawsze true) — zostaje.

## Stan na 1.11.2026 ok. 21:45 UTC (tura 8 w toku)

- `main` = `claude/bold-sagan-vwz5gm` = `integ/tura7` (paczki 118–122, testy zielone).
- **`integ/tura8`**: scalona fala 1 GPT (123, 124, 126–134) + poprawki koordynatora (L.kt, osiągnięcie `campaign_hard` w Play Games, testy 66 osiągnięć, Robolectric w Mission130/Achievement130, BackLocaleTest). NIE na `main`.
- Pozostałe czerwone testy po scaleniu: sim `GoldenReplayTest`, `AiPersonalityProfileTest` (ECONOMIST 87,5% wygranych — 127 za silny), `ChallengeRules109Test` (archiwalna powtórka nie weryfikuje się po zmianie AI), app `DoctrineBalance82Test` (VETERAN 67,5%), `ScreenshotTest.regression100/foldAudit98/foldCoverOverlays123/campaignJourneyMap`.
- Pracowali dwaj agenci (worktree; kopie zapasowe commitów: `claude/wip-tura8-balans`, `claude/wip-tura8-ui` — mogą być niekompletne):
  - **balans:** przegląd 126/127, pomiary (126 i 127 nie mają wyników w BALANCE.md), naprawa testów sim + DoctrineBalance82, regeneracja złotych powtórek;
  - **UI:** 4 testy zrzutów + zgłoszenia właściciela: (A) złote przyciski — ciemny tekst z ciemnym obrysem w `Ui.textO` jest rozmazany, usunąć obrys/cień dla ciemnego tekstu; (B) dymek podpowiedzi w bitwie ma być nad HUD, na pasie ziemi, nie zasłaniać przycisków; (C) półprzezroczysty baner „Możesz awansować!” z „Awansuj”/„Czekaj”, raz na epokę, bez pauzy, przez BattleController, teksty `part139()`; (D) w panelu pauzy (i innych) przyciski nachodzą na wewnętrzny obrys — odstęp 12–16 dp + test geometryczny.
  Jeśli praca agentów przepadła — zlecić ponownie wg powyższego.
- Potem: pełne testy, push na `main` + `claude/bold-sagan-vwz5gm`, APK dla właściciela, zip fali 2 (140 stopnie wież + 135 sklep), następnie fala 3 (125 dowódca), fala 4 (136 tłumaczenia).
- Środowisko: init Gradle musi dodać mirror GCS na początek `pluginManagement` + `gradlePluginPortal()` (buildSrc); pełne testy app trwają teraz ok. 7 min (`forkEvery=0` z 131).
- Pytanie otwarte do właściciela: muzyka z Suno (zaproponowano próbkę: menu + jedna epoka w wersji spokojnej i bojowej, ścieżki stem lub dwie wersje; licencja płatnego planu).

## Decyzje właściciela (1.11.2026, wieczór) i tryb nocny
- Tura 8 po zielonych testach: wolno wypchnąć na `main` i `claude/bold-sagan-vwz5gm` bez pytania.
- Ostrzał: celownik gracza omija własną linię (zlecone agentowi UI jako G). Symulacja i tak nie rani swoich (`BarrageFriendlyFireTest`).
- Limit armii wroga zostaje 12.
- Elity: w kampanii/potyczce wróg ma elity tylko gdy gracz (zlecone agentowi balansu); gra ma tłumaczyć, jak odblokować elity (agent UI, F, `part141()`); baner awansu (C, `part139()`).
- Muzyka: opisy Suno dla wszystkich epok w `docs/music/SUNO_PROMPTS.md`; zlecenie 142 (wgranie) czeka na pliki w `music-src/`.
- Stopnie wież: zlecenie 140 (fala 2 razem z 135), dowódca 125 w fali 3, tłumaczenia 136 w fali 4.
- Zaplanowane zadanie „Century War: nocne wznawianie pracy” co godzinę wznawia tę sesję (wyłączyć rano, gdy niepotrzebne).

## Stan na 2.11.2026 ok. 03:30 UTC — tura 8 NA MAIN
- `main` = `claude/bold-sagan-vwz5gm` = `integ/tura8` (4f4d3314): fala 1 GPT (123, 124, 126–134) + poprawki UI A–G + balans (uczciwe elity, 126/127, stageRamp) + PROTOCOL 0.6.0. Pełne testy zielone (8,5 min). APK wysłane.
- Zip fali 2 wysłany właścicielowi: 140 (stopnie wież) + 135 (sklep, „Co nowego”). Potem fala 3: 125 dowódca, fala 4: 136 tłumaczenia. 142 (muzyka) czeka na pliki Suno.
- Otwarte do decyzji właściciela: Zryw (r_rally) ma tę samą asymetrię co elity; etapy 28–36 trudne dla gracza zbierającego medale tylko z kampanii; dymek Doktryny elit w menu częściowo zasłania Wyzwanie dnia i Potyczkę.

## Stan na 2.11.2026 ok. 09:10 UTC
- `main` = `claude/bold-sagan-vwz5gm` = `integ/tura9` (6da61261): Zryw wroga tylko gdy gracz go zbadał (kampania/potyczka), dymek podpowiedzi poza bitwą omija przyciski (górny/dolny pas na gęstym menu), paczka GPT 135. Pełne testy zielone.
- Właściciel: wolno używać 1 dodatkowego agenta i scalać wszystko do main. Pracuje agent „Przegląd UX nowego gracza” (worktree z integ/tura9; nie rusza sim, BattleBottomHud, BattleUpgradePanel; teksty part143).
- Czekamy na GPT 140 (stopnie wież). Po scaleniu: zip fali 3 (125 dowódca).
