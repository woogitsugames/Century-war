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
