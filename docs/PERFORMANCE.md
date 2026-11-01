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
