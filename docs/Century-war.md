# Century War: przekazanie pracy (31.10.2026, wieczór)

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
- Wersje: `Replay.VERSION = 3`, `BattleSnapshot.VERSION = 7`, profil `CURRENT_VERSION = 3`. Nowy stan bitwy musi trafić do `BattleSnapshot` (podbij VERSION). Teksty w `engine/L.kt` w 10 językach (test `LTest`).
- Pola `prevX/prevAnim/prevAttack/stride` w `Fighter` i `prevX/prevH` w `Projectile` są tylko wizualne (interpolacja renderera). Nie używaj ich w logice.

## Co zrobiono w tej sesji

Scalone do `main` (każde z buildem i pełnymi testami):
- `claude/int-meta` (osiągnięcia, misje, powiadomienia, Play Games), `claude/int-infra` (CI, wydanie, sklep, serwer; symulacja wydzielona do `:sim`), `claude/int-audio-tests` (muzyka), gpt/14 (odporny profil), gpt/16 (ulepszenia jednostek), gpt/17 (Epoka żelaza: 9 epok, 36 etapów, migracja gwiazdek), gpt/04 (tylko blokada oblężnicza i narzędzie balansu; liczby zmierzone od nowa, `docs/BALANCE.md`).
- Naprawiony crash przy starcie (`StartupTest`), Fold: dowolna orientacja od 600 dp.
- Płynniejszy ruch: interpolacja między krokami (90/120 Hz), wygaszanie kroku, ciągły cykl ataku.
- `Daily.ERA_EXPANSION_DAY = 21153` (1.12.2027): data wydania nieznana. **Przed wydaniem ustawić na faktyczny dzień wydania (nigdy w przeszłości).**
- gpt/18 odrzucone (zastąpione zleceniem 28).

## Co teraz: zlecenia GPT 19–38

Użytkownik wysłał do GPT **pierwszą falę: 19, 20, 24, 27**. Plan fal i kolejność scalania: `docs/tasks/DISPATCH.md`, zależności: `docs/tasks/README.md`.

Gdy użytkownik napisze, że GPT skończył (albo na początku sesji):
1. `git fetch origin && git ls-remote origin 'refs/heads/gpt/*'` i porównaj z poprzednim stanem (stare gałęzie 01–18 są już scalone lub odrzucone).
2. Każdą nową gałąź przejrzyj krytycznie: determinizm, zero alokacji na klatkę, `BattleSnapshot`/`Replay` VERSION, 10 języków, zrzuty 720p..Fold (poziom i pion), brak emoji i nawiązań do innych gier. Sprawdź, czy zlecenie zostało wykonane w całości.
3. Scalaj po kolei według `DISPATCH.md` (`git merge --no-ff`), rozwiązuj konflikty, po każdym scaleniu pełne testy. Napraw, co trzeba.
4. Wypchnij na `main`, `claude/bold-sagan-vwz5gm` i gałąź sesji; wyślij APK; powiedz użytkownikowi, którą falę wysłać dalej.
5. Po zmianach symulacji (28, 33, 34, 36) powtórz pomiar balansu i zaktualizuj `docs/BALANCE.md`.

## Otwarte sprawy i ryzyka

- Materiały sklepu i polityka prywatności są nieaktualne po Play Games (zlecenie 20). `docs/store/data_safety.md` ma ostrzeżenie.
- Play Games wymaga konfiguracji w Play Console (`docs/PLAY_GAMES_SETUP.md`, `app/src/main/res/values/games_ids.xml` ma `REPLACE_ME`). Własny upload key: `docs/CI.md`.
- Serwer: plansze `campaign_8..31` trzeba przenumerować o +4 przy pierwszym wdrożeniu; serwer liczy bitwy bez bonusów z badań (zlecenie 23 to rozwiązuje).
- Balans: AI z mgłą wojny jest słabe; weteran z 80% badań wygrywa niektóre późne etapy w 1–2 min; przy 8 seedach rozrzut ±15 pp (przed wydaniem 20 seedów).
- Dalsze błędy znalezione przy pisaniu zleceń są opisane w samych zleceniach (np. płonąca jednostka ciągle błyska, brak zapisu powtórek w aplikacji, alokacje na klatkę w HUD, auto-jakość tylko w dół).
- Test na prawdziwych telefonach robi użytkownik (Galaxy Fold, telefon). Pytaj o wrażenia po każdym APK.
