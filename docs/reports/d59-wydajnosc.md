# D59 — wydajność i bateria

Gałąź `claude/dzien-59-wydajnosc` (baza `origin/main` z 5.11.2026). Decyzja właściciela:
wydajność i bateria, bez zmiany wyników bitew i wyglądu.

## Co już było zrobione (punkt wyjścia)

- **152** (`audit-156` §10): `HudDigits152` zamiast Stringów złota i zegara, `Wave6PerformanceFixture`
  (fala 55, dowódcy, 8 wież III st.), budżet alokacji bitwy < 4 KB / 600 klatek, zwalnianie cache błysków.
- **d43**: `Game.nowMillis()` zamiast zegara-lambdy w menu (pudełko `Long` = 16 B na wywołanie).
- **R2-05 / R10-04**: `ThermalWatch` i `QualityGovernor`, tryb gry baterii (30 FPS, jakość ≤ MED),
  zwalnianie pamięci w `onTrimMemory`.
- **77**: `LowEndScreensPerformanceTest` (menu, kampania, Kodeks, pauza, dymek), `MusicThreadPerformanceTest`.
- Symulacja: `AllocationTest` (0 alokacji w `Battle.step`), `SimPerformanceTest`.

Dotychczasowe testy bitwy zamrażały AI i oddziały (`hold`, `thinkTimer = MAX`). Nowe pomiary
prowadzą naturalną bitwę z AI po obu stronach.

## Metoda

- Robolectric (`GraphicsMode.NATIVE`, SDK 35), JDK 21, bufor 1600×720 px (rysowanie programowe,
  więc koszt wypełniania pikseli jest przesadzony względem GPU telefonu, ale proporcje
  overdraw zostają). Alokacje: `ThreadMXBean.getThreadAllocatedBytes` wątku testu.
- Rozgrzewka 600 klatek, pomiar 1200 klatek (bitwy) lub 300 + 600 (ekrany).
- Przyczyny: JFR (`ExecutionSample` + `NativeMethodSample`; zdarzenia TLAB przy `-XX:TLABSize=2k`).
- Wygląd: tymczasowy test sum CRC32 pikseli pełnej klatki bitwy (9 epok × warianty × 4 pogody,
  LOW/MED/HIGH, poziomo i pionowo, 3 pozycje kamery, wstrząs 0,6–1,0, AI po obu stronach):
  **2088 klatek, przed i po zmianach identyczne bit w bit**; dwa przebiegi przed zmianami też
  identyczne (determinizm). Test był w commicie 197b8f3c, potem usunięty (7 min).

## Pomiary przed / po (JVM, 1600×720)

| Scena | Przed: avg / p95 [ms] | Po: avg / p95 [ms] | Przed: alokacje | Po: alokacje |
|---|---|---|---|---|
| Kampania etap 34, LOW, AI×2 | 13,01 / 16,25 | 11,68 / 14,37 | 2,6 KB / 1200 kl. | 2,6 KB / 1200 kl. |
| Kampania etap 36, LOW, AI×2 | 12,28 / 14,72 | 11,37 / 13,59 | 2,3 KB / 1200 kl. | 2,3 KB / 1200 kl. |
| Kampania etap 36, HIGH, AI×2 | 14,89 / 18,96 | 13,42 / 16,46 | 2,3 KB / 1200 kl. | 2,3 KB / 1200 kl. |
| Endless fala 52+, LOW, AI×2 | 15,52 / 18,94 | 14,01 / 16,25 | 111 KB / 1200 kl.¹ | 111 KB / 1200 kl.¹ |
| Endless fala 52+, HIGH, AI×2 | 17,50 / 21,32 | 16,58 / 20,96 | 3,7 KB / 1200 kl. | 3,6 KB / 1200 kl. |
| Rekordy → Osiągnięcia, HIGH | 29,49 / 35,41 | 28,82 / 34,39 | **31,6 KB / 600 kl.** | **48 B / 600 kl.** |
| Rekordy → Statystyki / Rankingi / Powtórki | 28,0–29,1 | 27,5–28,5 | ≤ 2 KB | ≤ 0,4 KB |
| Kodeks, HIGH | 30,45 / 35,77 | 30,36 / 35,10 | 48 B | 48 B |
| Mapa kampanii z iskrami, bezczynna, HIGH | 33,72 / 45,45 | 33,35 / 41,78 | 0,7–1,3 MB² | 0,7 MB² |
| Mapa kampanii z iskrami, przewijana | 34,05 / 47,32 | 32,20 / 41,12 | 48 B | 48 B |
| Menu, HIGH | 26,27 / 31,82 | 26,08 / 30,92 | 776 B | 776 B |

Bitwy późnych epok: **−7…−10 % czasu klatki** (średnia), p95 −2…−14 %. Ekrany poza bitwą:
zmiana w granicach szumu (nic tam nie zmieniano poza alokacjami Osiągnięć).

¹ Endless: w stanie ustalonym 48 B / 600 klatek (okna po 300 klatek: 368, 368, **31 856 + 86 664**,
48, 48, 48 B). Skok przypada na zmianę fali (nowe napisy fali i oddziały, jednorazowo) i na
pierwsze wywołania metod natywnych Robolectric. Test budżetu bierze najlepsze z 3 okien.
² Mapa bezczynna: JFR nie pokazuje w stosach tych alokacji ani jednej ramki gry — to generowanie
uchwytów metod (`InvokerBytecodeGenerator`, ASM) przez Robolectric przy pierwszych wywołaniach
natywnego Canvas. Przewijana mapa (te same rysunki) alokuje 48 B. Nie jest to koszt telefonu.

Praca w tle (po `onPause`): pętla gry stoi (`GameView.stopThread`), `ThermalWatch.stop` usuwa
odpytywanie, `Music.pause` kończy wątki `music-stream` i `music-recorded` i oddaje fokus audio,
wątki zapisu (profil, zrzut, historia, dziennik) czekają na kolejkę bez limitu. **Jedyny wyjątek:
wątek `sfx-gen` budził się co 4 s przez cały czas życia procesu, także w tle** — poprawione.

## Najważniejsze źródła kosztu i poprawki

1. **Niebo bitwy pod ziemią (overdraw).** `Backdrops.drawSky` malował gradient na całą
   wysokość, a od linii ziemi zakrywał go w całości nieprzezroczysty grunt. W profilu JFR to był
   największy pojedynczy koszt klatki bitwy (31–41 % próbek). Teraz gradient kończy się 8 dp pod
   ziemią (zapas na wstrząs kamery ≤ 4 dp w pionie i wygładzanie). Obraz identyczny (CRC).
2. **Kafle krajobrazu poza ekranem.** `drawLayer` rysował zawsze o jeden kafel więcej z każdej
   strony (3 warstwy × 2 obrazy `Picture` na klatkę, całkiem poza ekranem). Teraz tylko kafle
   przecinające ekran z marginesem 8 dp na wstrząs (7 dp). Treść kafla mieści się w [0, 640] —
   potwierdzone sumami CRC dla wszystkich epok i wariantów. Licznik `lastDrawnTiles`.
3. **Cząstki poza ekranem.** Iskry, dym i pierścienie walki poza kadrem były rysowane (wywołania
   JNI i rekordy listy wyświetlania). Teraz tylko się poruszają i gasną; rysowanie pomijane, gdy
   cała cząstka (promień ≤ 1,6·rozmiar + 4k) leży poza ekranem z zapasem 8 dp. Licznik
   `culledParticles`. Fizyka cząstek bez zmian (tylko wygląd, nie symulacja).
4. **Alokacje Rekordy → Osiągnięcia (31,6 KB / 600 klatek → 48 B).** Ekran co klatkę liczy
   postęp: `Modifier.values()` i `Doctrine.values()` kopiowały tablice, `sumOf`/`count` po liście
   badań tworzyły iteratory, a `g.clock()` pakował `Long`. Maski liczone raz, pętle po indeksie,
   `g.nowMillis()` (także licznik wyzwania tygodnia w `ModeScreen.update`).
5. **Budzenie w tle.** `Sound.worker` czekał `wait(4000)` w pętli: 15 wybudzeń procesu na minutę
   przez cały czas działania, także gdy gra była w tle. `prewarm()` i `release()` budzą wątek pod
   tym samym zamkiem po zmianie stanu, więc teraz `wait()` bez limitu (bez utraty sygnału).

Symulacja (`:sim`) nie była zmieniana: `PROTOCOL` bez zmian, złote powtórki i wyniki bitew bez zmian.

## Testy budżetu

- `LateEraPerformanceTest` (kategoria fast):
  - naturalna bitwa kampanii etap 36 i Endless fala 52 (AI × 2, rozgrzewka 1800 klatek):
    < 16 KB / 600 klatek (wynik: 1,3–1,5 KB i 48 B),
  - `landscapeDrawsOnlyVisibleTiles`: kafle ≤ warstwy × (⌈(szer. + 2·margines)/640⌉ + 1), LOW i HIGH,
    4 pozycje kamery,
  - `offscreenParticlesAreSkipped`: kamera przy bazie, walka w środku pasa → `culledParticles > 0`,
  - `soundGeneratorSleepsWithoutPeriodicWakeups`: bezczynny `sfx-gen` w stanie `WAITING`, nie `TIMED_WAITING`.
- `LowEndScreensPerformanceTest.achievementsLow`: < 16 KB / 600 klatek (wynik ok. 3 KB).

## Weryfikacja

WYNIKI_WERYFIKACJI

## Czego nie zmieniano (rekomendacje)

- Ekrany poza bitwą kosztują na JVM więcej niż bitwa (26–34 ms przy 1600×720): animowane tło
  menu (`MenuBg`: niebo, trzy warstwy gór, cień) plus przyciemnienie `Chrome.dim` na cały ekran,
  pod panelami. Zmniejszenie wymagałoby zmiany wyglądu albo przycinania pod zaokrąglonymi
  panelami (na GPU złożone przycięcie może kosztować więcej) — nie ruszano.
- Ekrany statyczne (Kodeks, Rekordy) rysują pełną klatkę w tempie ekranu (60–120 Hz). Limit
  60 FPS poza bitwą na ekranach 90/120 Hz zmniejszyłby pracę o połowę, ale to decyzja produktowa
  (płynność przewijania) — do decyzji właściciela.
- `music-recorded` tyka co 40 ms także wtedy, gdy grę prowadzi synteza (celowe: przenikania
  i przerywniki). Tylko w pierwszym planie.
- Pomiary JVM nie zastępują profilu GPU i temperatury na tanim telefonie.
