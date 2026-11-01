# Century War: przekazanie pracy (31.10.2026, noc — po scaleniu zleceń 19–38)

Ten plik jest dla nowej sesji Claude Code. Przeczytaj go w całości, zanim cokolwiek zrobisz.

## Rola i zasady

Jesteś koordynatorem projektu **Century War**: oryginalna strategia na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek poza Play Games, grafika rysowana kodem na Canvas). Repo: `woogitsugames/century-war`.

- **Gałąź główna: `main`**. Zawsze wypychaj ten sam commit także na `claude/bold-sagan-vwz5gm` (domyślna gałąź na GitHubie, zleceniodawcy GPT bazują na niej). Gałąź sesji (podana przez system) też aktualizuj tym samym commitem.
- Odpowiadaj użytkownikowi po polsku, zwięźle. Użytkownik nie jest programistą: tłumacz skutki, nie szczegóły.
- Commity po polsku, z liniami atrybucji sesji. Nie twórz PR bez prośby.
- Przy każdej zmianie: build + testy (niżej). Po większych scaleniach wysyłaj użytkownikowi APK (`SendUserFile`, `app/build/outputs/apk/debug/app-debug.apk`), testuje na telefonie i na Galaxy Fold.
- Możesz używać do 2 agentów Opus równolegle (zgoda użytkownika).

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
./gradlew :app:assembleDebug :app:testDebugUnitTest :server:test --console=plain   # pełne: ok. 17 min, puszczaj w tle
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Profile*' --tests '*Startup*' --console=plain   # szybkie
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest.menus*' --console=plain --no-configuration-cache   # zrzuty 720p, 20:9, Fold poziom i pion
./gradlew :app:testDebugUnitTest --tests '*ArtGalleryTest*' --console=plain   # galeria jednostek, baz i pasek animacji
BALANCE_RUN=true BALANCE_RUNS=8 BALANCE_PROFILE=target ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.campaignReport*'   # balans, wyniki w app/build/balance/
```

`--rerun` przy balansie jest konieczne: Gradle nie widzi zmiennych środowiskowych jako wejścia testu.

## Architektura (najważniejsze)

- `:sim` (`sim/src/main/kotlin/com/centurywar/game/`): cała symulacja bitwy, czysty Kotlin/JVM, wspólna z serwerem. Deterministyczna (bez zegara, Random spoza seeda, kolejności HashMap), zero alokacji na klatkę, potęgi przez `StrictMath`.
- `:app`: Android (ekrany `screens/`, silnik `engine/`, grafika `game/art/`, renderer `game/BattleRenderer.kt`, profil `meta/Profile.kt`). Testy symulacji też są w `app/src/test`.
- `:server`: ranking globalny (Ktor + SQLite), weryfikuje powtórki przez `Replay.verify` z `:sim`. Niewdrożony.
- Komendy gracza idą wyłącznie przez `BattleController` (zapis do `Replay`).
- Wersje: `Replay.VERSION = 4`, `BattleSnapshot.VERSION = 11`, profil `CURRENT_VERSION = 3` (nowe pola profilu są opcjonalne z domyślnymi), wersja gry dla serwera rankingu `0.3.0` (`ScoreClient.GAME_VERSION`, `CW_GAME_VERSION`). Nowy stan bitwy musi trafić do `BattleSnapshot` (podbij VERSION). Teksty w `engine/L.kt` w 10 językach (test `LTest`); tabela jest podzielona na funkcje `partN()` (limit 64 KB metody JVM) — nowe klucze dopisuj do ostatniej części albo dodaj następną.
- Pola `prevX/prevAnim/prevAttack/stride` w `Fighter` i `prevX/prevH` w `Projectile` są tylko wizualne (interpolacja renderera). Nie używaj ich w logice.

## Co zrobiono (sesja 31.10 wieczór/noc)

Scalone do `main` wszystkie zlecenia GPT 19–38 (kolejność: 19, 20, 24, 27, 21, 22, 25, 26, 29, 31, 28, 30, 32, 23, 33, 38, 34, 36, 35, 37), z rozwiązaniem konfliktów i poprawkami:
- 25+26+31: typ cząstki 4 (pył) i nowy 5 (fala uderzeniowa); wstrząs ekranu: ustawienie 0/1/2 i `reduceShake`; błyski przez `allowFlash`.
- 26+38: błysk wieży tylko z `FxBuffer.MUZZLE` z symulacji (usunięte `detectTurretFire`).
- 29+38: dźwięki epok przeniesione do wspólnego `screens/BattleSfx` (bitwa i odtwarzacz powtórek), z obsługą wariantów epok.
- 22: test profili osobowości grał 720 s i liczył nierozstrzygnięte jako porażki; teraz do rozstrzygnięcia, dostrojeni Inżynier i Ekonomista.
- 23+35+36: potyczka własna nie trafia do rankingu globalnego; oblężenie tygodnia wysyłane jako tablica `weekly` (badania zerowe); poprawki walidacji serwera i kolejki wyników.
- 32: naprawiony crash dymka podpowiedzi przy małym miejscu (`coerceIn` z pustym zakresem), zapis podpowiedzi bez wyścigu wątków.
- 37: test alokacji nie kompilował się u GPT; źródłem ~384 B/krok było `StrictMath.pow` (JDK alokuje) w `Defs.income/costScale` — teraz tablice. Ogranicznik tempa ×3/×5 przeniesiony do obecnego `BattleScreen`. Część drobnych optymalizacji UI z 37 odpadła przy konfliktach (ekrany przebudowane przez 29/30/36).
- Ustawienia: id przycisków 13–18 (wstrząs, dostępność, podpowiedzi, ranking globalny, nowa tożsamość, suwak efektów).
- Pomiar balansu kampanii po fali: `docs/BALANCE.md` (0 patów; etapy 34–36 bardzo trudne dla gracza-AI).

## Co teraz

1. Warianty epok (28) zbalansowane (STEAM 0,5 pp, SILICON 4 pp przy 400 pojedynkach; `docs/BALANCE.md`). Po zmianach wariantów warto powtórzyć pomiar kampanii.
2. Zebrać wrażenia użytkownika z APK (telefon, Galaxy Fold) i poprawiać.
3. Zlecenia 19–67 są zamknięte i scalone (tura 3: 39–52, tura 4: 53–67). Nowe zlecenia GPT pisać według wzoru w `docs/tasks/` (każde zlecenie dodaje teksty we własnej funkcji `partNN()` w `L.kt`).
4. Testy Robolectric widzą zasoby aplikacji (`isIncludeAndroidResources`). Zapis profilu jest w tle — w testach po `save()` wołaj `Profile.awaitHintWrites()`.
5. Długie komendy (pełne testy ok. 45 min) uruchamiaj narzędziem Bash z `run_in_background`, nie przez `&` — procesy z `&` giną po zakończeniu tury.

## Otwarte sprawy i ryzyka

- Materiały sklepu zaktualizowane w 20, ale `docs/store/monetization_plan.md` i `screenshots_plan.md` wciąż piszą „offline”.
- Ranking: tytuł „Rankingi” nachodzi na przycisk „Nazwa dowódcy” na Foldzie w pionie; etykiety zakładek ucięte.
- Auto-jakość (37) patrzy tylko na czas CPU; telefon ograniczony GPU nie obniży jakości.
- `Daily.WEATHER_DAY` i `VARIANTS_DAY` = `ERA_EXPANSION_DAY` — przed wydaniem ustawić na dzień wydania.
- Play Games wymaga konfiguracji w Play Console (`docs/PLAY_GAMES_SETUP.md`, `app/src/main/res/values/games_ids.xml` ma `REPLACE_ME`). Własny upload key: `docs/CI.md`.
- Serwer: plansze `campaign_8..31` trzeba przenumerować o +4 przy pierwszym wdrożeniu; serwer liczy bitwy bez bonusów z badań (zlecenie 23 to rozwiązuje).
- Balans: AI z mgłą wojny jest słabe; weteran z 80% badań wygrywa niektóre późne etapy w 1–2 min; przy 8 seedach rozrzut ±15 pp (przed wydaniem 20 seedów).
- Dalsze błędy znalezione przy pisaniu zleceń są opisane w samych zleceniach (np. płonąca jednostka ciągle błyska, brak zapisu powtórek w aplikacji, alokacje na klatkę w HUD, auto-jakość tylko w dół).
- Test na prawdziwych telefonach robi użytkownik (Galaxy Fold, telefon). Pytaj o wrażenia po każdym APK.
