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
