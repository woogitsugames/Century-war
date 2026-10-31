# Wydajność Century War (zlecenie 37)

## Budżet klatki

- Ekran 60 Hz: **16,67 ms CPU na update i render łącznie**. Na 90/120 Hz nie zakładamy, że telefon musi utrzymać 90/120 FPS: punktem odniesienia regulatora pozostaje 60 FPS.
- Limit 30 FPS: budżet 33,33 ms. Sam odstęp między klatkami (vsync i uśpienie) nie uruchamia redukcji.
- Obserwacje: średnie update/render z kołowego okna **120 klatek**; p95 sumy obu faz z prealokowanej tablicy (liczony podczas odświeżania nakładki).
- Po **4 s** CPU powyżej 103% budżetu regulator odejmuje jeden stopień; po **10 s** poniżej 70% budżetu przywraca jeden stopień. Minimalny odstęp zmian: **3 s**. Ręczny wybór jakości wyłącza automat.
- Kolejność degradacji: liczba cząstek, chmury, paralaksa, cienie, następnie skala bufora po 0,05 do 0,60. Dotychczasowe trzy poziomy LOW/MED/HIGH pozostają podstawą jakości.

## Odczyt nakładki na telefonie

W Ustawieniach włącz `FPS`. Nakładka na dole pokazuje:

- `FPS` — rzeczywista średnia liczba klatek/s (łącznie z oczekiwaniem),
- `U` / `R` — CPU update i render w ms, `p95` — 95. percentyl ich sumy,
- `Q3-2` — jakość bazowa 3, redukcja automatyczna o dwa stopnie,
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
3. W nowym terminalu użyj `adb shell dumpsys gfxinfo com.centurywar.game reset`, a następnie po ~30 s `adb shell dumpsys gfxinfo com.centurywar.game framestats`. SurfaceView może nie wykazać wszystkich wewnętrznych klatek; porównuj z nakładką CPU i odczuciem płynności.
4. Weryfikuj `dumpsys meminfo com.centurywar.game`, temperaturę i throttling. Pomiar p95 z JVM nie jest równoznaczny z rzeczywistym czasem GPU ani opóźnieniem prezentacji.

Zrzuty nakładki: `app/build/screenshots/phone720_performance_overlay.png` oraz `foldOpen_performance_overlay.png` po wykonaniu `ScreenshotTest.performanceOverlay`. Wyniki kroki/s przed/po są emitowane wyłącznie przy `PERF_REPORT=true` i są zależne od wydajności CI. Do PR należy przepisać dane z logów rzeczywistego uruchomienia, a nie wartości orientacyjne.
