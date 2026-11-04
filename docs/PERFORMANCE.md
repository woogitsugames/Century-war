# Wydajność Century War (zlecenia 37 i 41)

## Budżet klatki

- Ekran 60 Hz: **16,67 ms CPU na update i render łącznie**. Przy 90/120 Hz celem regulatora pozostaje stabilne tempo około 60 FPS, **nie** pełne 90/120 FPS. Okres celu = 1000 / min(odświeżanie ekranu, cel FPS).
- Limit 30 FPS: budżet CPU i okres celu **33,33 ms**. Normalne 30 FPS (w tym oczekiwanie na vsync i sen pętli) nie uruchamia redukcji.
- Obserwacje: średnie update/render z kołowego okna **120 klatek**; p95 sumy obu faz z prealokowanej tablicy (liczony podczas odświeżania nakładki). Drugie okno 120 próbek przechowuje rzeczywisty odstęp między startami klatek i czas `lockHardwareCanvas/lockCanvas…unlockCanvasAndPost`.
- **CPU-bound:** po **4 s** CPU powyżej 103% budżetu odejmowany jest jeden stopień jakości.
- **GPU/kompozytor-bound:** po **co najmniej 3 s** średni odstęp klatek > **1,35 × okres celu**, przy CPU ≤80% budżetu, odejmowany jest jeden stopień. Pomiar czasu `lock…post` pomaga diagnozować oczekiwanie na GPU/kompozytor, ale nie jest odczytem czasu wykonania poleceń bezpośrednio na GPU.
- **Powrót jakości:** po **10 s** CPU ≤70% budżetu i odstępie ≤110% okresu celu przywracany jest jeden stopień. Przy braku pomiaru odstępu zachowane jest dotychczasowe zachowanie CPU-only. Dla obu rodzajów przeciążenia minimalny odstęp zmian wynosi **3 s**; ręczny wybór jakości wyłącza automat i resetuje redukcje.
- **CPU:** najpierw mniej cząstek, chmur, paralaksy i cieni, następnie skala bufora −0,05 na stopień. **GPU:** od pierwszego stopnia skala bufora −0,05 i wyłączone cienie, a dopiero po wykorzystaniu zakresu skalowania redukowane są dalsze efekty. Oba rodzaje stopni mogą się sumować; skala nigdy nie spada poniżej **0,60**. Podstawowe poziomy LOW/MED/HIGH pozostają bez zmian.

## Odczyt nakładki na telefonie

W Ustawieniach włącz `FPS`. Nakładka na dole pokazuje:

- `FPS` — rzeczywista średnia liczba klatek/s (łącznie z oczekiwaniem),
- `U` / `R` — CPU update i render w ms, `GPU/post` — czas od pozyskania Canvas do opublikowania klatki (zawiera też CPU render), `p95` — 95. percentyl sumy CPU,
- `Q3-2` — jakość bazowa 3, łącznie dwa stopnie redukcji (CPU i/lub GPU),
- `95%` — rzeczywista skala bufora, `p160` — maksymalna liczba cząstek,
- `steps` — kroki symulacji ostatniej klatki, `lost` — odrzucone kroki przy limicie 20.

Przy zbyt dużym koszcie symulacji przycisk prędkości pokazuje np. `×5 → ×3`. Jest to **wyłącznie tempo odtwarzania**, nie mnożnik statystyk lub stan bitwy. Krok symulacji pozostaje 1/60 s. Przy długim zawieszeniu ograniczenie 20 kroków w klatce nadal działa, ale liczba odrzuconych kroków jest jawnie mierzona.

## Testy i scenariusze

Uruchom:

```bash
PERF_REPORT=true ./gradlew :app:testDebugUnitTest --tests '*SimPerformanceTest*' --tests '*AllocationTest*' --tests '*BattleIndexTest*' --tests '*QualityGovernorTest*' --tests '*FrameMetricsTest*' --console=plain --no-configuration-cache
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*Replay*' --tests '*Snapshot*' --tests '*LTest*' --console=plain
./gradlew :server:test -PserverOnly=true --console=plain
```

`SimPerformanceTest` porównuje ścieżkę referencyjną (pełne skany tablicy) z indeksem żywych jednostek na tej samej VM: kampania etap 36 z AI po obu stronach, Endless fala 40 ze wstępnie wypełnionymi armiami, potyczka ze stanem zegara w 30. minucie. Minimalny wymóg 600 kroków/s jest stabilnym zabezpieczeniem CI (×5 przy 60 FPS wymaga 300 kroków/s). **Wynik to throughput JVM, a nie FPS urządzenia**.

`BattleIndexTest` porównuje binarny zrzut dwóch silników w kolejnych momentach, w tym kolejność walki, AI i RNG. Nowe tablice indeksów są cache'em i nie należą do formatu zapisu. `AllocationTest` używa `ThreadMXBean`; po rozgrzaniu JIT mierzy 10 000 kroków gęstej bitwy, z marginesem 8192 B na incydentalną deoptymalizację. Nie mierzy inicjalizacji ani tworzenia ofert kart (zdarzeń, nie cyklicznej ścieżki). Nie zakładamy, że pomiar alokacji z Robolectric jest wiarygodny: emuluje Androida, a skia/shadows alokują poza logiką gry.

## Pomiary na urządzeniu

1. Podłącz tani telefon (2–3 GB RAM); ustaw tryb auto i włącz `FPS`.
2. Wypełnij armię w Endless po fali 40, uruchom ×5 i obserwuj `p95`, `steps`, `lost` przez co najmniej 30 sekund; powtórz po rozgrzaniu urządzenia i w oszczędzaniu energii.
3. Dla GPU-bound obserwuj przez ponad 3 s niski czas `U + R` przy długich odstępach między klatkami; `GPU/post` pomaga wskazać blokowanie na powierzchni. Na ekranie 120 Hz stabilne 70 FPS nie powinno uruchamiać redukcji. Zweryfikuj osobno limit 30 FPS.
4. W nowym terminalu użyj `adb shell dumpsys gfxinfo com.centurywar.game reset`, a następnie po ~30 s `adb shell dumpsys gfxinfo com.centurywar.game framestats`. SurfaceView może nie wykazać wszystkich wewnętrznych klatek; porównuj z nakładką CPU i odczuciem płynności.
5. Weryfikuj `dumpsys meminfo com.centurywar.game`, temperaturę i throttling. Pomiar p95 z JVM i `GPU/post` nie są równoznaczne z rzeczywistym czasem GPU ani opóźnieniem prezentacji.

Zrzuty nakładki: `app/build/screenshots/phone720_performance_overlay.png` oraz `foldOpen_performance_overlay.png` po wykonaniu `ScreenshotTest.performanceOverlay`. Wyniki kroki/s przed/po są emitowane wyłącznie przy `PERF_REPORT=true` i są zależne od wydajności CI. Do PR należy przepisać dane z logów rzeczywistego uruchomienia, a nie wartości orientacyjne.


## Zlecenie 77 — oprawa menu, kampanii, kodeksu i audio

### Budżety dla taniego telefonu (2–3 GB RAM, 4 słabe rdzenie)

Wartości są **celami pomiaru na urządzeniu**, nie gwarancją, że test JVM na
maszynie CI uzyska takie same czasy. Zegar CPU obejmuje razem `Screen.update()`
i `Screen.render()`, lecz nie czas GPU i kompozytora. Przy 60 FPS całkowity
budżet klatki to 16,67 ms; potrzebny jest zapas na obsługę wejścia i systemu.

| Scenariusz, po rozgrzaniu | CPU średnia | CPU p95 | Alokacje Java (600 klatek) |
|---|---:|---:|---:|
| Menu LOW, bez dialogu | ≤ 6 ms | ≤ 10 ms | < 16 KiB |
| Menu HIGH (regresja wyglądu) | raport | raport | < 16 KiB |
| Kampania LOW, przewijanie | ≤ 8 ms | ≤ 12 ms | < 16 KiB |
| Kodeks LOW, przewijanie | ≤ 8 ms | ≤ 12 ms | < 16 KiB |
| Pauza bitwy LOW | ≤ 10 ms | ≤ 14 ms | < 16 KiB |
| Dymek kontekstowy LOW | ≤ 2 ms | ≤ 4 ms | < 16 KiB |

Test `LowEndScreensPerformanceTest` rozgrzewa 300 klatek i mierzy kolejne 600,
wypisuje średnią i p95 czasu wywołań oraz różnicę licznika alokacji JVM.
Używa refleksji dla `com.sun.management.ThreadMXBean`, ponieważ `android.jar`
nie zawiera tego API. Testy uruchamia się przez Robolectric NATIVE; dlatego
**wynik alokacji i CPU w JVM nie jest FPS ani profilem GPU na Androidzie**.
Limit alokacji jest asercją regresyjną, a czasy pozostają raportowane, bez
sztywnego progu zależnego od obciążenia współdzielonej maszyny CI.

### Ścieżka muzyczna

`Music` wylicza muzykę na oddzielnym wątku `music-stream`, z priorytetem tła.
`MusicThreadPerformanceTest` uruchamia syntezę na osobnym wątku JVM z buforem
2048 próbek mono / 22 050 Hz (92,88 ms audio na bufor). Po 150 buforach
rozgrzewających rejestruje 600 czasów `renderPcm()` i wypisuje średnią/p95.
Asercje: średni czas syntezy < 46,44 ms (50% bufora), p95 < 92,88 ms.
Test nie uwzględnia `AudioTrack.write()`, opóźnień harmonogramisty ani
zakłóceń przez sterowniki audio; te należy przetestować na tanim telefonie.

### Automatyczna adaptacja

`QualityGovernor` już analizuje wspólny czas CPU (`update + render`) i odstęp
między klatkami, więc obejmuje obciążenie nowych ekranów bez odrębnego licznika.
Nowe właściwości `Quality.menuDetail` i `Quality.menuAnimated` stosują
redukcję także do tła i postaci menu, kampanii oraz kodeksu. LOW to
nieruchome, uproszczone twierdze, jedna postać na stronę i mniej warstw;
MED ogranicza najdroższe detale, HIGH zachowuje poprzedni wygląd.
Kolejne stopnie ograniczenia CPU/GPU przełączają dodatkowe efekty nawet wtedy,
gdy bazowy profil jakości jest ustawiony automatycznie na HIGH.

Obrazy `Picture` regionów kampanii są nagrywane ponownie dopiero po zmianie
geometrii, orientacji lub skali DP (a nie przy samym powrocie do ekranu).
Modalny opis wznowienia ma zawinięte teksty przygotowane w `layout()`,
zamiast dzielenia tekstu na ciągi znaków przy każdej klatce. Gradienty
tła już wcześniej były budowane przy zmianie rozmiaru — nie przenosimy ich
na gorącą ścieżkę renderowania.

### Termika i brak pamięci (R2-05)

- **Termika:** `ThermalWatch` (MainActivity, od `onResume` do `onPause`) przekazuje do
  `Game.thermalStatus` status z `PowerManager.addThermalStatusListener` (API 29+), a na
  API 30+ co 10 s w bitwie `getThermalHeadroom(10)` do `Game.thermalHeadroom` (wątek UI,
  nigdy pętla gry; `NaN` ignorujemy). `QualityGovernor.record(..., thermal)` traktuje status
  `MODERATE` (2) lub wyżej, albo zapas ≥ 0,85, jak sygnał nagrzewania: po **3 s** obniża jeden
  stopień **CPU** (`reduction`, bez skali bufora). Powrót jakości tylko przy statusie
  ≤ `LIGHT` (1) i dotychczasowych 10 s zapasu. Progi CPU/GPU (4 s / 3 s / 10 s) bez zmian.
  **R6-02 M3 (decyzja właściciela):** termika daje **najwyżej 2 stopnie**
  (`THERMAL_MAX_STEPS`, licznik `thermalSteps`), drugi dopiero po **15 s** dalszego
  nagrzania, i **nigdy nie obniża skali bufora**: stopień termiczny tylko wtedy, gdy
  `reduction - gpuReduction < Quality.EFFECT_STEPS` (zostały efekty do wyłączenia). Licznik
  zeruje się przy każdym powrocie jakości i w `reset()`; dalsze stopnie dają już tylko
  rzeczywiste przeciążenie CPU/GPU.
  Nakładka FPS dopisuje `T<status>` w dolnym wierszu, gdy status > 0.
- **`onTrimMemory`:** `Game.trimMemory` (UI_HIDDEN 20 i RUNNING_* jak 20, BACKGROUND 40)
  woła `Screen.onTrimMemory` dla bieżącego ekranu i stosu. Kampania zwalnia obrazy epok
  (odbudowa przy pierwszym `render`), bitwa bitmapy błysku i warstwy tła `Backdrops`,
  zapowiedź epoki swoje `Picture`, gdy jest nieaktywna. Od 40 wątek nagrań zwalnia
  odtwarzacze, które nie grają. Stan bitwy i `SoundPool` zostają. Gdy pętla stoi (gra w tle),
  zwalnianie wykonuje się od razu na UI, jak migawka w `onPause`. Odbudowa po powrocie
  może raz przyciąć klatkę. **R10-04 L4:** kampania nagrywa najwyżej 2 obrazy epok na
  klatkę (najpierw widoczne, potem pozostałe), więc powrót z tła nie nagrywa 9 naraz.
- **Tryb gry Androida (R10-04):** `res/xml/game_mode_config.xml` (API 33+) zgłasza obsługę
  trybów baterii i wydajności bez skalowania i bez narzucania FPS przez system. MainActivity
  czyta `GameManager.gameMode` (API 31+) przy każdym wznowieniu i przekazuje go do
  `Game.applyGameMode`; w trybie baterii efektywny limit to 30 FPS (także budżet
  `QualityGovernor`), a jakość najwyżej średnia, bez zmiany `profile.fpsCap` i `profile.quality`.
  Po powrocie do trybu standardowego lub wydajności wracają ustawienia gracza; raport testera
  ma wiersz `Game mode`.

### Testy lokalne i pomiar na urządzeniu

```bash
PERF_REPORT=true ./gradlew :app:testDebugUnitTest \
  --tests '*LowEndScreensPerformanceTest*' \
  --tests '*MusicThreadPerformanceTest*' \
  --tests '*MenuQualityTest*' \
  --console=plain --no-configuration-cache
./gradlew :app:assembleDebug --console=plain
```

Na fizycznym telefonie: przejdź co najmniej 30 sekund przez menu, przewijanie
kampanii i kodeksu, pauzę i dymek; odczytaj `U/R/p95`, FPS, `GPU/post`,
zużycie RAM i temperaturę w LOW i automacie. Podczas odtwarzania muzyki
porównaj p95 z wyłączoną muzyką oraz sprawdź trzaski i underruny.
Zrzuty ekranu w HIGH i LOW zweryfikuj przy 720p oraz na Foldzie.


## Zlecenie 101 — pomiar rozruchu i budżet bitmap

### Zimny start (pierwsza klatka menu)

W debug APK jednorazowa linia `CWStartup` pojawia się **po pomyślnym
`unlockCanvasAndPost()` pierwszej klatki `MenuScreen`**, nie przy samym
`setContentView()`. Początek pomiaru to utworzenie `CenturyWarApp`, tuż
przed `Application.onCreate()`. Nie obejmuje ładowania procesu/zależności
przez system przed stworzeniem `Application`; tę część mierzy Android.

Log pokazuje całkowity czas i koszty: `Application` (raporty awarii, Play
Games SDK), ekran startowy, profil, snapshot, raport awarii, wykrywanie klasy
RAM/CPU, tabelę 10 języków, konstrukcję `Game`, uruchomienie ładowania
historii, Play Games, muzykę i montaż `SurfaceView`. Poszczególne koszty
dotyczą różnych wątków; ich suma **nie musi** równać się całemu czasowi.

Wykonanie na fizycznym urządzeniu z 2–3 GB RAM:

```bash
adb shell am force-stop com.centurywar.game
adb logcat -c
adb shell am start -W -n com.centurywar.game/com.centurywar.MainActivity
adb logcat -d -s CWStartup:I
```

Powtórz 5–10 razy po `force-stop` (pierwszy start po instalacji osobno),
podaj medianę i p95. `am start -W` jest pomiarem systemowego uruchamiania
Activity, a `CWStartup` obejmuje dodatkowo pierwszą opublikowaną ramkę gry.
Nie traktuj pomiaru JVM/Robolectric jako rzeczywistego zimnego startu telefonu.

**Przeniesione koszty:** profil, snapshot, wykrywanie RAM/CPU i inicjalizacja
pełnej tabeli `L` są w wątku `startup-io`. Tekst ekranu wczytywania nie
tworzy całej tabeli na UI. `SoundPool`, synteza i prewarm SFX startują dopiero
po pierwszej klatce menu (`audio-init` + `sfx-gen`), a muzyka / odświeżanie
przypomnień po jej publikacji. Pierwszy bardzo szybki dotyk może jeszcze nie
mieć gotowego dźwięku, ale gra i sterowanie działają niezależnie od audio.

### Pamięć cache rozbłysków

`ActivityManager.memoryClass` (limit sterty aplikacji, a nie całkowity RAM)
ustala udział **maksymalnie 1/32 klasy pamięci**. Dodatkowe pułapy:
LOW 2 MiB, MED 4 MiB, HIGH 8 MiB. Cache `BaseFlashCache` nie alokuje nic
przy wejściu w bitwę: bitmapa ARGB_8888 (460×580, 1 067 200 B) powstaje
dopiero podczas rozbłysku. Na LOW obie bazy współdzielą jedną bitmapę, na
HIGH przy wystarczającej stercie istnieją dwie. Zmiana jakości z HIGH na LOW
zwalnia nadmiarową bitmapę; przy limicie mniejszym niż jedna bitmapa efekt
rozbłysku jest pomijany i korpus bazy nadal jest rysowany z `Picture`.

Przykładowe maksima:

| memoryClass | LOW: budżet | Liczba bitmap LOW | HIGH: liczba bitmap |
|---|---:|---:|---:|
| 32 MiB | 1 MiB | 0 | 0 |
| 64 MiB | 2 MiB | 1 | 1 |
| 128 MiB | 2 MiB | 1 | 2 |
| 256 MiB | 2 MiB | 1 | 2 |

`BitmapCacheBudgetTest` sprawdza limity oraz rzeczywistą liczbę
przydzielonych bitmap, przełączenie LOW i brak przydziału przy małej stercie.
Wygląd na HIGH nie został zmieniony.

### Test budżetu klatki z 72 jednostkami

`BattleCrowdPerformance101Test` mierzy pełne `BattleScreen.update()` +
`render()` na `Canvas(Bitmap)` w Robolectric NATIVE i profilu LOW. Po
120 klatkach rozgrzewki wykonuje 240 pomiarów w scenariuszu 36 jednostek
i 72 jednostek (maksymalna pula). Raportuje medianę, p95 i iloraz median;
asercja regresyjna to **ratio < 8×** w tej samej JVM. Jest to kontrola
nadmiernego wzrostu kosztu wraz z liczbą jednostek, **nie** gwarancja
16,67 ms ani odczyt czasu GPU na telefonie.

```bash
./gradlew :app:assembleDebug --console=plain --no-configuration-cache
./gradlew :app:testDebugUnitTest \
  --tests '*StartupTimingsTest*' \
  --tests '*BitmapCacheBudgetTest*' \
  --tests '*BattleCrowdPerformance101Test*' \
  --tests '*BaseFlashCache90Test*' \
  --tests '*StartupTest*' \
  --tests '*LTest*' --console=plain --no-configuration-cache
```

Wyniki liczbowe startupu i GPU trzeba zebrać na fizycznym telefonie; w
repozytorium nie zakładamy, że pomiar hosta jest pomiarem telefonu.


## Fala 6 — zlecenie 152: dowódcy, wieże, Endless 55

### Warunki pomiarów

Ten sam scenariusz w `Wave6PerformanceFixture`: po jednym dowódcy na stronę
(aura i Impuls), 12 vs 12 żywych jednostek, cztery wieże poziomu III na każdej
stronie, naprzemienne epoki wież dające rozprysk i podpalenie, Endless fala 55,
720p (1600×720), LOW / HIGH. Test CPU rozgrzewa 120 klatek, mierzy 240
kolejnych; mediana to element nr 120, p95 nr 227.
Pomiary JVM pochodzą z Ubuntu GitHub Actions, Robolectric NATIVE, Android SDK 35
(emulowany) przy kompilacji dla SDK 36, Java 17. **Nie są pomiarem GPU telefonu.**

Kod bazowy: `85e90b6`, kod po zmianach `29ae3666`. Obie wersje uruchomiono
na tym samym runnerze w jednym **zakończonym pomyślnie** przebiegu [Performance 152, #41503404584](https://github.com/woogitsugames/Century-war/actions/runs/41503404584)
(artefakt `performance-152-logs`).

| Jakość | Metryka | Przed: mediana/p95 (ms) | Po: mediana/p95 (ms) |
|---|---|---:|---:|
| LOW | `Battle.step()` — sama symulacja | 0,003 / 0,009 | 0,002 / 0,008 |
| LOW | `BattleScreen.update()` | 0,037 / 0,092 | 0,018 / 0,039 |
| LOW | `BattleScreen.render()` | 14,486 / 16,341 | 13,762 / 14,491 |
| HIGH | `Battle.step()` — sama symulacja | 0,002 / 0,004 | 0,002 / 0,004 |
| HIGH | `BattleScreen.update()` | 0,015 / 0,040 | 0,015 / 0,035 |
| HIGH | `BattleScreen.render()` | 16,085 / 17,012 | 16,177 / 17,157 |

Pomiary wykonano kolejno na jednym runnerze, ale zmienność JVM/JIT i obciążenie
CI wykluczają interpretowanie różnic rzędu setnych milisekundy jako pewnego
przyspieszenia lub spowolnienia. Dodatkowy scenariusz 72 jednostek uzyskał
`ratio 72/36 = 0,89×` (mediany: 17,408 i 19,526 ms); test kontroluje
złożoność, nie czas GPU.

### Alokacje i cykl życia pamięci

W końcowym, zielonym pomiarze 600 przeplatanych `update + draw` alokowało
łącznie **688 B** zarówno w LOW, jak i HIGH, a samo powtórzone `draw` **48 B**
w obu profilach. Pierwszy wariant testu,
wykonujący 600 samych `update()` bez `draw()`, przydzielił 28 672 B i nie
spełnił progu 16 KiB — taki przebieg nie odwzorowuje rzeczywistej pętli gry.
Test końcowy mierzy więc przeplatane klatki i niezależnie draw, z limitem
4096 B na 600 iteracji, i uruchamia zarówno LOW, jak i HIGH.
Nie utożsamiać tego progu tolerancji JVM z matematycznym zerem alokacji.

Przy 20 przejściach menu ↔ bitwa w dwóch grupach po 10, z GC między pomiarami,
test na commicie `29ae3666` uzyskał:

| Jakość | Sterta po 10 (B) | Sterta po 20 (B) | Przyrost (B) | Zachowane stare ekrany |
|---|---:|---:|---:|---:|
| LOW | 77 029 496 | 77 041 848 | 12 352 | 0 |
| HIGH | 77 081 968 | 77 083 024 | 1 056 | 0 |

`BaseFlashCache.release()` recykluje bitmapy błysku bazy przy `onPause()`
ekranu bitwy, niezależnie od momentu GC. Test dodatkowo sprawdza, że cache
realnie został utworzony (do 2 MiB przy memoryClass 64 MiB) i po opuszczeniu
bitwy zgłasza 0 B. Nie zmieniono danych, reguł, balansu, formatu powtórki,
`SimVersion.PROTOCOL` ani wyglądu.

### Weryfikacja

```bash
./gradlew :app:assembleDebug --console=plain --no-configuration-cache
PERF_REPORT=true ./gradlew :app:testDebugUnitTest \
  --tests '*BattleCrowdPerformance101Test*' \
  --tests '*BattleScreenAllocationTest*' \
  --tests '*LowEndScreensPerformanceTest*' \
  --tests '*HudDigits152Test*' \
  --tests '*HudDigits152RenderTest*' \
  --tests '*ReplayTest*' \
  --tests '*ScreenshotTest*' --console=plain --no-configuration-cache
```

**Zweryfikowane:** `:app:assembleDebug`, testy wydajności, `ReplayTest`,
`HudDigits152Test`, `HudDigits152RenderTest` oraz testy HUD dowódcy —
wszystkie zakończone `BUILD SUCCESSFUL` w przebiegu #41503404584.
Log końcowy testów: `BUILD SUCCESSFUL in 52s`, APK: `BUILD SUCCESSFUL in 42s`.

**Ograniczenie:** JVM/Robolectric nie mierzy GPU, temperatury ani PSS na
fizycznym urządzeniu 2–3 GB RAM. Osobna walidacja na sprzęcie docelowym
(720p oraz Fold) jest nadal potrzebna.
