# Balans kampanii

Stan: 31.10.2026, po scaleniu mechanik 05/06/07/13/16/17 (9 epok, 36 etapów). Zastępuje raport z gpt/04, który był zmierzony na starej grze (32 etapy, bez kontr, kart, ulepszeń i Epoki żelaza).

## Co zmieniono

- **HP baz ×3 poza Endless** (`Battle.BALANCE_BASE_HP_MUL`). Bitwy gracza z badaniami kończyły się w ok. 1 minutę.
- **Blokada oblężnicza** (z gpt/04): od 8. minuty obie bazy tracą `0,32% HP/s × presja` proporcjonalnie do tego, jak daleko przeciwnik trzyma front (`Battle.SIEGE_ATTRITION`). Wcześniej etapy 1–2 bez badań w 2/3 bitew kończyły się patem po 30 minutach; teraz 0 patów.
- **Siła AI w drugiej połowie kampanii** (`Campaign.stageRamp`): etapy 1–18 bez zmian, 19–27 rosną do ×1,35, 28–36 do ×2,2 (aiPower 3,7 na etapie 36). Badania dają dużą przewagę, więc bez tego średni gracz wygrywał późną kampanię w 1–2 minuty.
- **Skarbiec: +20 złota na start za poziom** (było +40). Weteran zaczynał z prawie trzykrotnie większym złotem niż wróg i wygrywał pierwszym szturmem.
- Z gpt/04 **nie wzięto**: ręcznej tabeli `powerByStage` (32 pozycje, do 6,9), HP baz ×4 z członem `(era/7)^3`, wyłączenia wież w etapach 1–6 (wycinało lekcję wież z samouczka).

## Metodyka

`BalanceReport` (tylko na żądanie):

```bash
BALANCE_RUN=true BALANCE_RUNS=8 BALANCE_PROFILE=target ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.campaignReport*'
```

- Obie strony sterowane przez `Ai` (wynik to dolne oszacowanie gracza-człowieka). Limit 30 min, timeout = pat.
- Profile badań: nowy 0%, początkujący 10% (z elitą i Zrywem), średni 40%, weteran 80% poziomów.
- Profil docelowy etapu: 1–6 nowy, 7–18 początkujący, 19–27 średni, 28–36 weteran.
- Zmienne: `BALANCE_STAGES="1,7,13"`, `BALANCE_PROFILE` (`target` albo nazwa), `BALANCE_POWER_SCALE`, `BALANCE_STRICT=true` (progi), `BALANCE_TAG`, `BALANCE_COMMANDER=on|off` i profile `medale`/`medale-elita` (tura 9). Wyniki: `app/build/balance/`.
- `--rerun` jest potrzebne: Gradle nie traktuje zmiennych środowiskowych jako wejścia testu.

## Wyniki (profil docelowy, 8 seedów na etap, przed zmianą Skarbca)

| Etap | Profil | AI | Epoki | Wygrane | Mediana [min] | Timeout | Śr. epoka |
|---:|---|---:|---|---:|---:|---:|---:|
| 1 | nowy | 0.72 | 0–0 | 100.00% | 13.09 | 0.00% | 0.00 |
| 2 | nowy | 0.77 | 0–0 | 87.50% | 13.08 | 0.00% | 0.00 |
| 3 | nowy | 0.83 | 0–1 | 100.00% | 12.92 | 0.00% | 1.00 |
| 4 | nowy | 0.88 | 0–1 | 100.00% | 12.66 | 0.00% | 1.00 |
| 5 | nowy | 0.82 | 1–1 | 100.00% | 12.11 | 0.00% | 1.00 |
| 6 | nowy | 0.87 | 1–1 | 100.00% | 12.08 | 0.00% | 1.00 |
| 7 | poczatkujacy | 0.93 | 1–2 | 87.50% | 10.80 | 0.00% | 2.00 |
| 8 | poczatkujacy | 0.98 | 0–2 | 100.00% | 11.15 | 0.00% | 2.00 |
| 9 | poczatkujacy | 0.92 | 2–2 | 100.00% | 9.22 | 0.00% | 2.00 |
| 10 | poczatkujacy | 0.97 | 2–2 | 87.50% | 11.11 | 0.00% | 2.00 |
| 11 | poczatkujacy | 1.03 | 2–3 | 50.00% | 12.27 | 0.00% | 3.00 |
| 12 | poczatkujacy | 1.08 | 0–3 | 50.00% | 11.59 | 0.00% | 3.00 |
| 13 | poczatkujacy | 1.02 | 3–3 | 50.00% | 9.70 | 0.00% | 3.00 |
| 14 | poczatkujacy | 1.07 | 3–3 | 50.00% | 12.29 | 0.00% | 3.00 |
| 15 | poczatkujacy | 1.13 | 3–4 | 62.50% | 9.65 | 0.00% | 4.00 |
| 16 | poczatkujacy | 1.18 | 1–4 | 37.50% | 13.13 | 0.00% | 4.00 |
| 17 | poczatkujacy | 1.12 | 4–4 | 50.00% | 11.25 | 0.00% | 4.00 |
| 18 | poczatkujacy | 1.17 | 4–4 | 50.00% | 10.66 | 0.00% | 4.00 |
| 19 | sredni | 1.27 | 4–5 | 100.00% | 6.94 | 0.00% | 4.88 |
| 20 | sredni | 1.38 | 2–5 | 75.00% | 9.86 | 0.00% | 5.00 |
| 21 | sredni | 1.36 | 5–5 | 87.50% | 8.03 | 0.00% | 5.00 |
| 22 | sredni | 1.47 | 5–5 | 100.00% | 7.52 | 0.00% | 5.00 |
| 23 | sredni | 1.58 | 5–6 | 87.50% | 13.00 | 0.00% | 6.00 |
| 24 | sredni | 1.70 | 3–6 | 75.00% | 10.71 | 0.00% | 6.00 |
| 25 | sredni | 1.67 | 6–6 | 62.50% | 9.30 | 0.00% | 6.00 |
| 26 | sredni | 1.80 | 6–6 | 75.00% | 9.84 | 0.00% | 6.00 |
| 27 | sredni | 1.92 | 6–7 | 62.50% | 8.07 | 0.00% | 6.75 |
| 28 | weteran | 2.14 | 4–7 | 100.00% | 1.51 | 0.00% | 4.88 |
| 29 | weteran | 2.18 | 7–7 | 100.00% | 1.32 | 0.00% | 7.00 |
| 30 | weteran | 2.40 | 7–7 | 100.00% | 1.42 | 0.00% | 7.00 |
| 31 | weteran | 2.63 | 7–8 | 87.50% | 6.71 | 0.00% | 7.75 |
| 32 | weteran | 2.88 | 5–8 | 37.50% | 11.17 | 0.00% | 7.19 |
| 33 | weteran | 2.90 | 8–8 | 75.00% | 3.07 | 0.00% | 8.00 |
| 34 | weteran | 3.15 | 8–8 | 100.00% | 6.24 | 0.00% | 8.00 |
| 35 | weteran | 3.42 | 8–8 | 75.00% | 1.53 | 0.00% | 8.00 |
| 36 | weteran | 3.69 | 6–8 | 25.00% | 9.61 | 0.00% | 7.50 |

Po zmianie Skarbca (weteran, 8 seedów): etap 28: 100%, 2,0 min; 29: 100%, 5,3 min; 30: 88%, 9,3 min; 33: 88%, 1,3 min; 35: 88%, 5,7 min.

Przed strojeniem (6 seedów): nowy gracz 1–2: 33% wygranych i 67% patów; od etapu 7 nowy gracz 0%; średni i weteran wygrywali prawie każdy etap, zwykle w 1–1,5 min.

## Znane słabości

- **Mgła wojny** (np. etap 30) osłabia AI przeciwnika: nie widzi armii gracza i szybko przegrywa.
- Weteran z 80% badań nadal wygrywa niektóre późne etapy w 1–2 minuty (28, 33). Uznane za akceptowalne: to gracz z prawie pełnymi badaniami.
- Etapy 11–18 dla gracza z 10% badań: ok. 50% wygranych. W praktyce gracz ma wtedy zwykle więcej badań.
- Wyniki przy 8 seedach mają rozrzut ±15 pp; przed wydaniem warto puścić 20 seedów.

## Warianty epok – procedura i stan pomiarów (zlecenie 28)

Dwie pary wariantów epok odnajdywane są po `EraArt.STEAM` i `EraArt.SILICON`.
Lustrzane pojedynki obejmują **200 seedów dla każdej epoki, z zamianą stron**
(400 pojedynków na epokę); porównuje się odsetki zwycięstw wariantów A i B.
Dodatkowy pomiar porównuje dwa warianty z AI wybierającą wariant
na dwóch już istniejących etapach kampanii danej epoki. Kryterium A/B:
**różnica zwycięstw nie większa niż 10 punktów procentowych** i brak patów.

```bash
BALANCE_RUN=true BALANCE_VARIANT_RUNS=200 ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.variantReport*' --console=plain --no-configuration-cache
```

Plik wynikowy: `app/build/balance/variants.md`. Obie strony pojedynku grają
AI o osobowości `BALANCED` (wcześniej przeciwnik losował osobowość z seeda),
pozostałe ustawienia stron są identyczne; zamiana stron znosi przewagę gracza/wroga.

**Pomiar (31.10.2026, 200 seedów × 2 strony):**

| Epoka | Wariant A | Wariant B | Wygrane A | Wygrane B | Paty | Różnica [pp] |
|---|---|---|---:|---:|---:|---:|
| STEAM | spalinowy | galwaniczny | 201 | 199 | 0 | 0,5 |
| SILICON | rój | biotech | 208 | 192 | 0 | 4,0 |

Przed strojeniem: STEAM 340:60 (70 pp), SILICON ok. 76:24. Zmiany mnożników
(kolejność ról: piechota, strzelec, ciężka, elita):

- spalinowy: koszt ciężkiej/elity 1,10/1,12 → 1,11/1,13; HP 1,25/1,30 → 1,14/1,16;
  obrażenia wieży 1,13 → 1,08,
- galwaniczny: koszt ciężkiej/elity 1,10/1,12 → 1,06/1,08; HP 0,82/0,80 → 0,96/0,95;
  obrażenia ciężkiej 1,08 → 1,12; obrażenia wieży 0,90 → 0,95,
- biotech: koszt 1,16/1,14/1,20/1,24 → 1,08/1,06/1,11/1,15; HP 1,12/1,08/1,20/1,25 →
  1,16/1,12/1,22/1,26; obrażenia 0,98/0,98/1,01/1,02 → 1,00/1,00/1,03/1,04;
  obrażenia wieży 1,04 → 1,07,
- rój bez zmian.

Kampania z wariantem wybranym na starcie (bez badań, tylko elita i zryw; N = 200,
zero timeoutów): etap 21 — 9,5% / 11,5%, etap 22 — 6,0% / 5,0%,
etap 29 — 2,0% / 1,0%, etap 30 — 0,5% / 1,0% (wariant A / B). Niskie odsetki
wynikają z braku badań, różnice między wariantami mieszczą się w szumie.

## A156-13 — wyrównanie wariantów STEAM i SILICON (02.11.2026)

Wyniki z audytu 156 odtworzono na aktualnym kodzie. Każda próba obejmuje
200 deterministycznych seedów × zamiana stron, czyli **400 pojedynków**
bez badań (poza elitą i Zrywem), z AI obu stron `BALANCED` i limitem 30 min.
Mierzono dokładnie konfigurację i logikę `BalanceReport.variantDuel` (tryb
`SKIRMISH`), bez luzowania pierwotnej asercji progu 10 pp.

| Epoka | Przed: A / B / paty / różnica | Po: A / B / paty / różnica |
|---|---|---|
| STEAM | 165 / 235 / 0 / **17,50 pp** | **194 / 206 / 0 / 3,00 pp** |
| SILICON | 178 / 222 / 0 / **11,00 pp** | **204 / 196 / 0 / 2,00 pp** |

**Przyczyna i dobór parametrów:**
- **STEAM: spalinowy (A) przeciw galwanicznemu (B).** Spalinowy ma
  więcej HP, lecz droższe ciężkie jednostki. Przy obu stronach sterowanych
  AI prędkość ciężkiej/elity istotnie zmienia kolejność docierania na front.
  Próba ich przyspieszenia z `0.78/0.74` do `0.88/0.82` spowodowała
  pogorszenie wyniku **132:268 (34 pp)**. Drobne spowolnienie do
  **`0.75/0.72`** umożliwia lepsze współdziałanie z resztą armii
  (hipoteza dotycząca formacji; wynik 194:206 jest zmierzony).
  Galwaniczny zachowuje przewagę mobilności, spalinowy przewagę HP.
- **SILICON: rój (A) przeciw biotech (B).** Wariant roju ma być tani i
  szybki, a biotech cięższy i wytrzymalszy. Zwiększenie HP ciężkiej roju
  z `0.78` do `0.83` nie pomogło (**176:224, 12 pp**). Przy niezmienionych
  HP i obrażeniach obniżono jedynie **mnożnik kosztu ciężkiej roju
  `0.86 → 0.83`**, uzyskując 204:196.
- Żadna inna epoka ani podstawowa jednostka nie ma zmienionych statystyk;
  AI, kampania, Endless, doktryny, wersje zapisu i protokołu bez zmian.
  `VariantTuning163Test` zabezpiecza obie zmienione relacje ekonomii
  i szybkości; pierwotny test `BalanceReport.variantReport` pozostaje
  niezmodyfikowany.

**Weryfikacja:** lokalnie wykonano po 400 pojedynków przed i po korekcie,
przez kod `Battle` i `Ai` zgodny z raportem, kompilowany jako czysty JVM.
Wynik wcześniejszego wariantu STEAM (132:268) był identyczny z GitHub
Actions, co sprawdza zgodność reprodukcji. Raport CI `BALANCE_RUN=true`
musi dodatkowo potwierdzić obie epoki oraz próby na etapach kampanii.

```bash
BALANCE_RUN=true BALANCE_VARIANT_RUNS=200 ./gradlew :app:testDebugUnitTest \
  --rerun --tests '*BalanceReport.variantReport*' --console=plain --no-configuration-cache
./gradlew :sim:test --tests '*VariantTuning163Test*' --tests '*GoldenReplayTest*' \
  --console=plain --no-configuration-cache
```

`GoldenReplayTest` w przebiegu przed ostatecznym strojeniem zgłaszał
rozbieżność już na `campaign-opening` (epoka 0), której statystyki nie
były zmieniane. Zgodnie ze zleceniem koordynator osobno podbije
`SimVersion.PROTOCOL` i rozstrzygnie aktualizację złotych wzorców;
to zadanie ich nie regeneruje.

## Pogoda — zlecenie 33 (31.10.2026)

Od etapu 5 co trzy etapy włącza się pogoda, w cyklu **deszcz → wiatr → noc**:
5 (deszcz), 8 (wiatr), 11 (noc), 14 (deszcz), 17 (wiatr), 20 (noc),
23 (deszcz), 26 (wiatr), 29 (noc), 32 (deszcz), 35 (wiatr).
Pozostałe etapy mają `CLEAR`. Modyfikatory kampanii i wcześniejsze losowania
wyzwania dnia nie ulegają zmianie.

**Metodyka porównania:** nowy `BalanceReport.weatherImpactReport` uruchamia
dla każdego zmienionego etapu tę samą liczbę seedów i ten sam docelowy profil
badań czterokrotnie: etap z pogodą, identyczny etap z `CLEAR`, poprzedni i
następny etap. Zapisuje `weather-impact.csv` i `weather-impact.md`.
Różnicę względem identycznego etapu `CLEAR` należy oddzielić od
różnic między sąsiadującymi etapami (z inną siłą AI i modyfikatorami).

```bash
BALANCE_RUN=true BALANCE_RUNS=20 BALANCE_PROFILE=target ./gradlew :app:testDebugUnitTest --rerun-tasks --tests '*BalanceReport.weatherImpactReport*' --console=plain
```

**Status pomiarów:** niezweryfikowane w tym środowisku — brak lokalnego SDK
Android i możliwości uruchomienia Gradle. Nie przypisujemy pogodowym etapom
fikcyjnych procentów zwycięstw. Warunek ±10 p.p. względem sąsiadów wymaga
porównania wyników nowego raportu; historyczny raport powyżej miał już
różnice większe niż 10 p.p. pomiędzy niektórymi sąsiadami bez pogody.


## Pomiar po fali zleceń 19–38 (31.10.2026, wieczór)

Po scaleniu wariantów epok (28), pogody (33), bossów Endless (34), oblężenia tygodnia (36) i strojenia osobowości AI (22). Ta sama komenda, profil docelowy, 8 seedów na etap (rozrzut ok. ±15 pp; przed wydaniem 20 seedów).

Wnioski:
- 0 patów (timeout 0% na każdym etapie).
- Weteran nie wygrywa już późnych etapów w 1–2 minuty (mediana 4–11 min) — wcześniejsze ryzyko zniknęło, głównie dzięki silniejszym osobowościom AI i wariantom epok.
- Etapy 34–36 są dla gracza sterowanego przez AI bardzo trudne (25% / 12,5% / 0%). To dolne oszacowanie gracza-człowieka, ale przed wydaniem warto sprawdzić 20 seedami i ewentualnie złagodzić koniec `Campaign.stageRamp`.
- Etapy 12 i 16 (25%) odstają od sąsiadów, tak jak w poprzednim pomiarze (zakres epok 0–3 / 1–4).

| Etap | Profil | AI | Epoki | Wygrane | Mediana [min] | Timeout | Śr. epoka |
|---:|---|---:|---|---:|---:|---:|---:|
| 1 | nowy | 0.72 | 0–0 | 100.00% | 13.91 | 0.00% | 0.00 |
| 2 | nowy | 0.77 | 0–0 | 87.50% | 13.81 | 0.00% | 0.00 |
| 3 | nowy | 0.83 | 0–1 | 100.00% | 13.54 | 0.00% | 1.00 |
| 4 | nowy | 0.88 | 0–1 | 87.50% | 13.60 | 0.00% | 1.00 |
| 5 | nowy | 0.82 | 1–1 | 100.00% | 13.61 | 0.00% | 1.00 |
| 6 | nowy | 0.87 | 1–1 | 87.50% | 13.73 | 0.00% | 1.00 |
| 7 | poczatkujacy | 0.93 | 1–2 | 62.50% | 10.51 | 0.00% | 2.00 |
| 8 | poczatkujacy | 0.98 | 0–2 | 75.00% | 12.78 | 0.00% | 2.00 |
| 9 | poczatkujacy | 0.92 | 2–2 | 62.50% | 11.83 | 0.00% | 2.00 |
| 10 | poczatkujacy | 0.97 | 2–2 | 75.00% | 9.57 | 0.00% | 2.00 |
| 11 | poczatkujacy | 1.03 | 2–3 | 87.50% | 11.26 | 0.00% | 3.00 |
| 12 | poczatkujacy | 1.08 | 0–3 | 25.00% | 10.02 | 0.00% | 3.00 |
| 13 | poczatkujacy | 1.02 | 3–3 | 50.00% | 7.57 | 0.00% | 3.00 |
| 14 | poczatkujacy | 1.07 | 3–3 | 50.00% | 11.16 | 0.00% | 3.00 |
| 15 | poczatkujacy | 1.13 | 3–4 | 75.00% | 9.63 | 0.00% | 3.81 |
| 16 | poczatkujacy | 1.18 | 1–4 | 25.00% | 13.92 | 0.00% | 4.00 |
| 17 | poczatkujacy | 1.12 | 4–4 | 62.50% | 11.62 | 0.00% | 4.00 |
| 18 | poczatkujacy | 1.17 | 4–4 | 50.00% | 8.97 | 0.00% | 4.00 |
| 19 | sredni | 1.27 | 4–5 | 75.00% | 6.91 | 0.00% | 4.88 |
| 20 | sredni | 1.38 | 2–5 | 50.00% | 11.74 | 0.00% | 5.00 |
| 21 | sredni | 1.36 | 5–5 | 87.50% | 8.99 | 0.00% | 5.00 |
| 22 | sredni | 1.47 | 5–5 | 100.00% | 7.35 | 0.00% | 5.00 |
| 23 | sredni | 1.58 | 5–6 | 37.50% | 13.15 | 0.00% | 6.00 |
| 24 | sredni | 1.70 | 3–6 | 75.00% | 11.42 | 0.00% | 6.00 |
| 25 | sredni | 1.67 | 6–6 | 50.00% | 7.63 | 0.00% | 6.00 |
| 26 | sredni | 1.80 | 6–6 | 75.00% | 6.75 | 0.00% | 6.00 |
| 27 | sredni | 1.92 | 6–7 | 50.00% | 12.34 | 0.00% | 7.00 |
| 28 | weteran | 2.14 | 4–7 | 62.50% | 10.17 | 0.00% | 6.19 |
| 29 | weteran | 2.18 | 7–7 | 75.00% | 3.95 | 0.00% | 7.00 |
| 30 | weteran | 2.40 | 7–7 | 75.00% | 4.95 | 0.00% | 7.00 |
| 31 | weteran | 2.63 | 7–8 | 37.50% | 10.05 | 0.00% | 8.00 |
| 32 | weteran | 2.88 | 5–8 | 50.00% | 9.93 | 0.00% | 7.38 |
| 33 | weteran | 2.90 | 8–8 | 50.00% | 7.31 | 0.00% | 8.00 |
| 34 | weteran | 3.15 | 8–8 | 25.00% | 8.39 | 0.00% | 8.00 |
| 35 | weteran | 3.42 | 8–8 | 12.50% | 8.53 | 0.00% | 8.00 |
| 36 | weteran | 3.69 | 6–8 | 0.00% | 11.46 | 0.00% | 8.00 |

## Zlecenie 42 — wyrównanie końca kampanii (31.10.2026)

Korekty dotyczą wyłącznie danych etapów w `Campaign.stageRamp` i zakresu epok
etapów 12 i 16. Nie zmieniono statystyk jednostek, algorytmu AI, mechaniki
bitwy, rankingu ani zapisanych gwiazdek gracza.

**Pomiar bazowy i pomiar po zmianach na 20 seedach:** oba uruchomiono jako
oddzielne przebiegi `Balans kampanii (20 seedow)` na GitHub Actions
(6 grup po 6 etapów; `BALANCE_PROFILE=target`, `BALANCE_RUNS=20`).
Przebieg bazowy: [41241002877](https://github.com/woogitsugames/Century-war/actions/runs/41241002877).
Przebieg po korekcie parametrów:
[41241102953](https://github.com/woogitsugames/Century-war/actions/runs/41241102953).
**W chwili sporządzenia tej sekcji oba przebiegi były w kolejce GitHub Actions,
więc brak zweryfikowanych wyników na 20 seedach i tabeli wygranych przed/po.**
Poniżej zapisano rzeczywiście zmienione parametry, nie symulowane wyniki.

| Etap | AI przed | AI po | Epoki przed → po | Wygrane przed (historyczne 8 seedów) |
|---:|---:|---:|---|---:|
| 11 | 1,03 | 1,06 | 2–3 → 2–3 | 87,5% |
| 12 | 1,08 | 1,04 | 0–3 → 1–3 | 25,0% |
| 16 | 1,18 | 1,15 | 1–4 → 2–4 | 25,0% |
| 21 | 1,36 | 1,47 | 5–5 → 5–5 | 87,5% |
| 22 | 1,47 | 1,66 | 5–5 → 5–5 | 100,0% |
| 23 | 1,58 | 1,46 | 5–6 → 5–6 | 37,5% |
| 29 | 2,18 | 2,24 | 7–7 → 7–7 | 75,0% |
| 30 | 2,40 | 2,45 | 7–7 → 7–7 | 75,0% |
| 31 | 2,63 | 2,55 | 7–8 → 7–8 | 37,5% |
| 34 | 3,15 | 3,00 | 8–8 → 8–8 | 25,0% |
| 35 | 3,42 | 3,01 | 8–8 → 8–8 | 12,5% |
| 36 | 3,69 | 2,99 | 6–8 → 6–8 | 0,0% |

Pozostałe 24 etapy mają niezmienioną siłę AI i zakres epok. Wyniki historyczne
przed zmianą pochodzą wyłącznie z tabeli w poprzedniej sekcji i **nie zastępują**
wymaganego pomiaru 20-seedowego.

**Wymagane sprawdzenie wyników po zakończeniu przebiegów:** etapy 1–6
≥85% zwycięstw; 7–27: 45–85%; 28–33: 40–75%; 34–36: 25–55%.
Odchylenie wewnętrznego etapu od średniej dwóch sąsiadów ≤25 p.p.,
0 timeoutów i mediana czasu ≥4 min od etapu 7. Jeśli którykolwiek warunek
nie będzie spełniony, należy jeszcze dostroić wskazane etapy i powtórzyć pomiar
na tych samych seedach. Nie oznaczać zlecenia jako zakończonego bez tych danych.

## Endless — izolacja tematów fal i pomiar weterana (zlecenie 46)

**Weryfikacja wyników:** pomiar automatyczny został zlecony do GitHub Actions,
ale liczby w poniższej tabeli wymagają zakończenia przebiegu CI. Do tego czasu
nie traktuj pól „oczekuje na CI” jako wyników testów.

- Profil: 80% z 110 poziomów badań, obliczone tym samym algorytmem co
  `BalanceReport` (odblokowania jednopoziomowe najpierw, reszta rozdzielona
  proporcjonalnie). `Research.bonuses` w module `:sim` przelicza poziomy
  na mnożniki. Gracz jest prowadzony przez `Ai(b, 0)`.
- Seedy: **1001–1020** (20 bitew), startowe zasady zwykłego `Mode.ENDLESS`,
  maksymalnie 90 000 kroków na seed lub do fali 60.
- `ENDLESS_SURVIVE_N` oznacza liczbę bitew, które **dotarły do fali N
  z żywą bazą gracza**; test zatrzymuje się na początku fali 60.
  `ENDLESS_FAILURES` to liczba porażek przed falą 60.
- Wynik i tabela są drukowane przez
  `server/src/test/kotlin/com/centurywar/server/EndlessBalanceReportTest.kt`:

```bash
./gradlew :server:test -PserverOnly=true --tests '*EndlessBalanceReportTest*' --console=plain --no-configuration-cache --rerun-tasks
```

| Fala | Dotarło z żywą bazą (20 seedów) |
|---:|---:|
| 20 | oczekuje na CI |
| 30 | oczekuje na CI |
| 40 | oczekuje na CI |
| 50 | oczekuje na CI |
| 60 | oczekuje na CI |

Średnia osiągnięta fala (`ENDLESS_MEAN`): **oczekuje na CI**.
Liczba porażek i mediana fali porażki:
**oczekuje na CI** (`ENDLESS_FAILURES`, `ENDLESS_DEFEAT_WAVE_MEDIAN`).

Porównanie na CI wykorzystuje `git merge-base HEAD origin/main` i uruchamia
ten sam test na bazowej oraz zmienionej symulacji (zachowując jednakowe seedy
i badania). Nie korzysta ze stałego commita. Obecny próg regresji to ±15%
średniej osiągniętej fali.

**Uwagi:** zmiana kolejności lub efektów fal modyfikuje symulację i
historyczne powtórki. Zgodnie ze zleceniem wersji rankingu nie zmieniono
— wymaga to działania koordynatora.


## Pomiar po zleceniu 42 (1.11.2026, 20 seedów, koordynator)

Zlecenie 42 nie zawierało pomiaru; ten przebieg go uzupełnia (profil docelowy, 20 seedów na etap). 0 patów. Koniec kampanii jest teraz wygrywalny (etapy 34–36: 50% / 55% / 35%, wcześniej 25% / 12,5% / 0%). Do poprawy w przyszłości: etap 29 (90%, mediana 2,6 min — za łatwy i za krótki) i etap 11 (35%, nieco poniżej celu 45%).

| Etap | Profil | AI | Epoki | Wygrane | Mediana [min] | Timeout | Śr. epoka |
|---:|---|---:|---|---:|---:|---:|---:|
| 1 | nowy | 0.72 | 0–0 | 100.00% | 13.92 | 0.00% | 0.00 |
| 2 | nowy | 0.77 | 0–0 | 80.00% | 13.82 | 0.00% | 0.00 |
| 3 | nowy | 0.83 | 0–1 | 90.00% | 13.73 | 0.00% | 1.00 |
| 4 | nowy | 0.88 | 0–1 | 90.00% | 13.49 | 0.00% | 1.00 |
| 5 | nowy | 0.82 | 1–1 | 95.00% | 13.14 | 0.00% | 1.00 |
| 6 | nowy | 0.87 | 1–1 | 90.00% | 13.59 | 0.00% | 1.00 |
| 7 | poczatkujacy | 0.93 | 1–2 | 60.00% | 10.65 | 0.00% | 2.00 |
| 8 | poczatkujacy | 0.98 | 0–2 | 85.00% | 12.21 | 0.00% | 2.00 |
| 9 | poczatkujacy | 0.92 | 2–2 | 65.00% | 11.61 | 0.00% | 2.00 |
| 10 | poczatkujacy | 0.97 | 2–2 | 60.00% | 11.63 | 0.00% | 2.00 |
| 11 | poczatkujacy | 1.06 | 2–3 | 35.00% | 10.29 | 0.00% | 3.00 |
| 12 | poczatkujacy | 1.04 | 1–3 | 65.00% | 10.24 | 0.00% | 3.00 |
| 13 | poczatkujacy | 1.02 | 3–3 | 70.00% | 8.20 | 0.00% | 3.00 |
| 14 | poczatkujacy | 1.07 | 3–3 | 55.00% | 11.00 | 0.00% | 3.00 |
| 15 | poczatkujacy | 1.13 | 3–4 | 60.00% | 11.41 | 0.00% | 3.93 |
| 16 | poczatkujacy | 1.15 | 2–4 | 50.00% | 12.66 | 0.00% | 4.00 |
| 17 | poczatkujacy | 1.12 | 4–4 | 65.00% | 11.37 | 0.00% | 4.00 |
| 18 | poczatkujacy | 1.17 | 4–4 | 55.00% | 9.10 | 0.00% | 4.00 |
| 19 | sredni | 1.27 | 4–5 | 85.00% | 8.28 | 0.00% | 4.82 |
| 20 | sredni | 1.38 | 2–5 | 75.00% | 12.12 | 0.00% | 5.00 |
| 21 | sredni | 1.47 | 5–5 | 70.00% | 9.19 | 0.00% | 5.00 |
| 22 | sredni | 1.66 | 5–5 | 70.00% | 9.43 | 0.00% | 5.00 |
| 23 | sredni | 1.46 | 5–6 | 70.00% | 12.09 | 0.00% | 5.90 |
| 24 | sredni | 1.70 | 3–6 | 55.00% | 10.80 | 0.00% | 6.00 |
| 25 | sredni | 1.67 | 6–6 | 60.00% | 7.63 | 0.00% | 6.00 |
| 26 | sredni | 1.80 | 6–6 | 65.00% | 6.85 | 0.00% | 6.00 |
| 27 | sredni | 1.92 | 6–7 | 55.00% | 10.24 | 0.00% | 6.95 |
| 28 | weteran | 2.14 | 4–7 | 60.00% | 11.05 | 0.00% | 6.40 |
| 29 | weteran | 2.24 | 7–7 | 90.00% | 2.57 | 0.00% | 7.00 |
| 30 | weteran | 2.45 | 7–7 | 65.00% | 4.72 | 0.00% | 7.00 |
| 31 | weteran | 2.55 | 7–8 | 55.00% | 8.78 | 0.00% | 7.75 |
| 32 | weteran | 2.88 | 5–8 | 60.00% | 9.93 | 0.00% | 7.40 |
| 33 | weteran | 2.90 | 8–8 | 55.00% | 7.45 | 0.00% | 8.00 |
| 34 | weteran | 3.00 | 8–8 | 50.00% | 9.05 | 0.00% | 8.00 |
| 35 | weteran | 3.01 | 8–8 | 55.00% | 9.01 | 0.00% | 8.00 |
| 36 | weteran | 2.99 | 6–8 | 35.00% | 12.97 | 0.00% | 7.75 |

## Korekta etapów 11 i 29 (1.11.2026, noc, 20 seedów)

`Campaign.stageRamp`: etap 11 ×1,03 → ×0,97, etap 29 ×1,03 → ×1,12.

| Etap | Profil | AI | Wygrane przed | Wygrane po | Mediana po [min] |
|---:|---|---:|---:|---:|---:|
| 10 | początkujący | 0.97 | 60% | 60% | 11.6 |
| 11 | początkujący | 1.00 | 35% | 70% | 10.3 |
| 12 | początkujący | 1.04 | 65% | 65% | 10.2 |
| 28 | weteran | 2.14 | 60% | 60% | 11.1 |
| 29 | weteran | 2.44 | 90% (2,6 min) | 75% | 4.4 |
| 30 | weteran | 2.45 | 65% | 65% | 4.7 |

## Doktryny 69 — pomiar lustrzanych walk AI (1.11.2026)

Źródło: `Doctrine69BattleSimTest.mirroredAiMatchesRemainBelowSixtyPercent`, wykonane w [GitHub Actions](https://github.com/woogitsugames/Century-war/actions/runs/41291857294). Dla każdej doktryny wykonano **20 seedów × dwie strony = 40 bitew**. Jeden dowódca otrzymywał badaną doktrynę, drugi walczył bez niej; obaj byli sterowani przez AI `BALANCED`, z tym samym złotem, epoką (2), siłą i modyfikatorami `FRAGILE_BASES`, `RICH_START`. Bitwy ograniczono do 600 s czasu symulacji. Odsetek zwycięstw liczony jest wśród zakończonych meczów; test wymaga przynajmniej 24 rozstrzygnięć i maksimum 60% wygranych.

| Doktryna | Seedy | Mecze | Wygrane | Przegrane | Nierozstrzygnięte | Skuteczność |
|---|---:|---:|---:|---:|---:|---:|
| Sztandar (`BANNER`) | 20 | 40 | 17 | 23 | 0 | **42,5%** |
| Zaopatrzenie (`SUPPLY`) | 20 | 40 | 16 | 24 | 0 | **40,0%** |
| Mistrzowie oblężeń (`SIEGE_MASTERS`) | 20 | 40 | 20 | 20 | 0 | **50,0%** |
| Czujność (`VIGILANCE`) | 20 | 40 | 22 | 18 | 0 | **55,0%** |

**Wynik:** wszystkie cztery doktryny mieszczą się w limicie 60% przy 20 seedach i zamianie stron; żadna bitwa nie zakończyła się timeoutem. To jest test krótkiego scenariusza porównawczego, nie pełne oszacowanie balansu całej kampanii.
## Zlecenie 71 — osiem nowych kart Endless (1.11.2026)

Karty dopisano po dotychczasowych identyfikatorach, bez zmiany ich kolejności.
Dwa nowe efekty bojowe: spowalnianie przez wieże (2 s, −15% ruchu za stos,
maksymalnie −45%) i premia +30% obrażeń jednostek przy HP bazy poniżej 25%.
Pozostałe efekty: synergie ekonomii, ruchu, liczebności, wież i napraw.
Naprawy są ograniczone do 30% bazowego HP na falę.

**Powtarzalny pomiar A/B:** `EndlessBalanceReportTest.veteranTwentySeeds`
z `server/src/test/kotlin/com/centurywar/server/` uruchamiany na obu
wersjach przez `.github/workflows/endless-balance.yml`.
Ustawienia: seedy **1001–1020**, 80% badań weterana, kontrola przez
`Ai`, maksymalnie fala 60 lub 90 000 kroków. Punktem odniesienia jest
`merge-base` względem **`claude/bold-sagan-vwz5gm`**, a nie `main`.
Wynik testu to `ENDLESS_MEAN` = średnia osiągnięta fala (fala porażki,
lub 60 dla ocalałych).

**Warunek akceptacji:** wzrost średniej względem bazy najwyżej **+10%**
`(nowa / bazowa − 1) × 100`. Ulepszono regułę GitHub Actions, aby
zwracała błąd, gdy przekroczony zostanie ten próg.

**Wyniki uruchomionego pomiaru A/B:** [GitHub Actions — 20 seedów](https://github.com/woogitsugames/Century-war/actions/runs/41291746749)
(pomiar 1.11.2026, baza `0893892872e964f97e9c51c34ecfda29911763e1`).

| Wskaźnik | Baza | Nowe karty |
|---|---:|---:|
| Średnia osiągnięta fala (`ENDLESS_MEAN`) | 53,05 | 52,20 |
| Różnica względem bazy | — | **−1,60%** |
| Porażki przed falą 60 | 10/20 | 15/20 |
| Mediana fali porażki | 54 | 53 |
| Żywa baza przy fali 20 | 20/20 | 20/20 |
| Żywa baza przy fali 30 | 19/20 | 20/20 |
| Żywa baza przy fali 40 | 16/20 | 18/20 |
| Żywa baza przy fali 50 | 16/20 | 15/20 |
| Żywa baza przy fali 60 | 10/20 | 5/20 |

**Wniosek:** wymagany limit wzrostu średniej o maksymalnie +10% spełniono.
Równocześnie większa liczba porażek przed falą 60 (15 zamiast 10)
wskazuje na zmianę rozkładu przeżywalności. Kryterium średniej nie
gwarantuje identycznego poziomu trudności w późniejszych falach;
przed wydaniem warto sprawdzić większą próbę seedów.

**Zgodność:** zmiana symulacji wymaga od koordynatora aktualizacji wersji
rankingu w aplikacji i serwerze. `BattleSnapshot.VERSION` podniesiono do 13
z uwagi na `Fighter.slowLeft`. Dotychczasowe snapshoty v12 są odrzucane.

## Złote powtórki symulacji (zlecenie 91)

Dziesięć scenariuszy w `sim/src/test/resources/golden/` przypina konfigurację (w tym bonusy,
modyfikatory, pogodę, kartę tygodnia i wydarzenie dnia), przyjęte komendy kontrolera,
wynik, zwycięzcę, falę, licznik kroków i SHA-256 całego końcowego
`BattleSnapshot.encode`. Odtworzenie przechodzi też przez `Replay.verify`
(weryfikator serwera) i `ReplayPlayer`; różnica choćby jednego bajtu stanu jest błędem testu.

Bez Android SDK: `./gradlew :sim:test -PserverOnly=true`.

**Świadoma aktualizacja po zmianie balansu:**
`./gradlew :sim:regenerateGolden -PserverOnly=true -PgoldenReason="konkretna przyczyna zmiany"`.
Zadanie nadpisuje wyłącznie pliki golden i dopisuje uzasadnienie z datą na końcu
`docs/BALANCE.md`. Przed scaleniem sprawdź diff JSON, zmianę wyników i wpływ na
powtórki/ranking; nie wywołuj regeneracji automatycznie w zwykłym CI.

- 2026-11-01 — regeneracja złotych powtórek: utworzenie 10 wzorców na podstawie
  bazowego silnika (Replay.VERSION=5, BattleSnapshot.VERSION=13), bez zmiany balansu.
  Zweryfikowane przez `:sim:regenerateGolden` w CI.
## Zlecenie 82 — limity odnowienia, eliminacja pustych ofert i malejące stosy (1.11.2026)

**Reguły:**
- `abilityCdMul` nie spada poniżej **0,35** (badania, karty, doktryny i zdarzenie dnia; również przy nałożeniu odnowienia).
- Karty, których ograniczony efekt osiągnął limit, przestają być losowane: klonowanie (0,6), elity na falę (4), leczenie na falę (0,3), limit armii (72), autoostrzał (15 s), sloty wież, odnowienie przy 0,35 i leczenie pełnej bazy. `BANNER` wymaga Zrywu, `VIGILANCE` bez mgły wzmacnia tylko zasięg wież.
- Trwałe efekty kart otrzymują pełny pierwszy stos; kolejne mają odpowiednio **60%, 40%, 30%, 20%, 15%, 10%, 5%** siły pojedynczej premii. Po ośmiu stosach karta nie pojawia się ponownie. Skumulowana *addytywna* siła premii wynosi maksymalnie 2,8 jednostki pierwszego stosu, a mnożniki nadal składają się multiplikatywnie.
- `SUPPLY`: natychmiastowe 15 złota w skali epoki (po scaleniu z innymi paczkami obniżone z 25, by lustrzany test doktryn mieścił się w 40–60%) i dotychczasowe +30% złota z zabójstw przez 60 s.
- W zwykłych pojedynkach doktryny `BULWARK`, `FURY` i `PLUNDER` mają osłabione wartości (odpowiednio 5%/3%, 8% i 8%; od tury 10 `PLUNDER` 6%) po audycie lustrzanym. **Karty Endless zachowują pierwszą historyczną premię** (20%/10%, 12% i 35%); ich kolejne stosy są wygaszane według powyższej tabeli. Opisy ofert rozróżniają te tryby. Opis w dziesięciu językach uwzględnia obie nagrody.

**Pomiar referencyjny Endless A/B na wcześniejszej rewizji (przed końcową korektą doktryn i złota SUPPLY; wynik nie jest pomiarem bieżącego HEAD):** [GitHub Actions #41309004679](https://github.com/woogitsugames/Century-war/actions/runs/41309004679), zadanie `EndlessBalanceReportTest.veteranTwentySeeds`. Seedy **1001–1020**, badania weterana 80%, sterowanie `Ai`, maksymalnie 90 000 kroków albo fala 60. Przebieg bazowy i zmodyfikowany wykonano w tej samej akcji, z osobnego worktree dla bazy. Fala 60 w tych danych oznacza również ucięcie pomiaru, niekoniecznie porażkę.

| Metryka (20 seedów) | Przed (gałąź bazowa) | Po (pierwsza wersja zlecenia 82) |
|---|---:|---:|
| Średnia osiągnięta fala (`ENDLESS_MEAN`, z uciętymi na 60) | 50,05 | **50,10** |
| Średnia fala porażki (wyłącznie przegrane) | 48,29 (17 porażek) | **50,10 (20 porażek)** |
| Zmiana średniej | — | **+0,05 fali (+0,10%)** |
| Mediana fali porażki (tylko zakończone porażką) | 53 | **54** |
| Porażki przed falą 60 | 17/20 | **20/20** |
| Przetrwanie fali 20 | 20/20 | 20/20 |
| Przetrwanie fali 30 | 20/20 | 19/20 |
| Przetrwanie fali 40 | 16/20 | 16/20 |
| Przetrwanie fali 50 | 13/20 | 15/20 |
| Przetrwanie fali 60 | 3/20 | **0/20** |

**Rozkład osiągniętych fal przed/po (ten sam seed):**

| Seed | Przed | Po | Seed | Przed | Po |
|---|---:|---:|---|---:|---:|
| 1001 | 60* | 54 | 1011 | 55 | 55 |
| 1002 | 41 | 54 | 1012 | 60* | 35 |
| 1003 | 53 | 54 | 1013 | 46 | 56 |
| 1004 | 53 | 56 | 1014 | 36 | 53 |
| 1005 | 54 | 56 | 1015 | 58 | 57 |
| 1006 | 53 | 54 | 1016 | 53 | 23 |
| 1007 | 53 | 38 | 1017 | 58 | 36 |
| 1008 | 38 | 57 | 1018 | 45 | 44 |
| 1009 | 35 | 54 | 1019 | 34 | 54 |
| 1010 | 60* | 57 | 1020 | 56 | 55 |

`*` — osiągnięto limit pomiaru (fala 60), baza nie została pokonana.

**Drugi pomiar doktryn (przed dostrojeniem do 5%/3%, 8% i 25 złota):** [CI #41309274812](https://github.com/woogitsugames/Century-war/actions/runs/41309274812). Z 13 doktryn 11 mieściło się w przedziale 40–60% na seedach 82_000–82_019. Poza przedziałem: `BULWARK` 26/39 = 66,7% i `PLUNDER` 25/40 = 62,5%. Na seedach 69_000–69_019 `SUPPLY` nadal osiągnęło 28/40 = 70%. `FURY` spadło do 23/40 = 57,5%. Ponowny pomiar bieżącego HEAD jest wymagany.

**Pierwszy test lustrzany doktryn** ([CI #41309004688](https://github.com/woogitsugames/Century-war/actions/runs/41309004688), 20 seedów × 2 strony, buff kontra brak buffa) wykrył zbyt wysoką skuteczność czterech doktryn: `BULWARK` 29/39 (74,4%, jeden remis), `FURY` 26/40 (65%), `PLUNDER` 34/40 (85%) i `SUPPLY` 32/40 (80%). Pozostałych dziewięć doktryn było w paśmie 40–60%. Testy 69 wykryły `SUPPLY` 29/40 (72,5%) na osobnych seedach. Na tej podstawie osłabiono zwykłe doktryny i zmniejszono natychmiastową premię `SUPPLY`, zachowując pierwsze karty Endless. **Wyniki po tych korektach trzeba zmierzyć ponownie**; nie wpisuje się niezweryfikowanych wartości jako końcowych.

**Wniosek dla pomiaru przed końcowym strojeniem:** średnia fala praktycznie się nie zmieniła (+0,10%, poniżej limitu +10% w CI), ale ogon wyników się zmienił: w nowych regułach żaden z 20 seedów nie dotarł do fali 60, podczas gdy poprzednio trzy. To nie dowodzi równego balansu późnych fal; wymaga ponownej oceny przy większej liczbie seedów i sterowaniu człowieka. Regresję mechaniki pokrywają `SimBalanceCaps82Test`, `DoctrineBalance82Test`, testy snapshotów i powtórek. Test AI obejmuje 13 doktryn × 20 seedów × dwie strony, z progiem 40–60%; szczegółowe wyniki należy odczytać z `sim-balance-82.yml` (nie należy zakładać, że wszystkie mieszczą się w przedziale bez odczytania wyniku).

- 2026-11-01 — regeneracja złotych powtórek: scalenie paczek 80–91 (limity 82, natychmiastowe złoto Zaopatrzenia obniżone do 15).

- 2026-11-01 — regeneracja złotych powtórek: Nowe karty i motywy Endless, format zrzutu v14.
## Zlecenie 96 — ponowny pomiar kampanii po limitach 82 (1.11.2026)

Ponownie zmierzono **36 etapów × 20 seedów** z profilem badań docelowym dla etapu,
po zmianach odnowienia, stosowania kart i doktryn w zleceniu 82 oraz po obniżeniu
natychmiastowego złota `SUPPLY` do 15. Nie zmieniono `Defs.kt`, globalnej
symulacji ani algorytmu AI. Seedy pozostają identyczne dla porównania:
`246813579 + stage.index * 10007 + trial * 997` (`trial = 0..19`),
sterowanie `Ai` po obu stronach, maksimum 30 minut.

**Źródła rzeczywistych pomiarów:**
- [Baza po 82, przed 96 — pełne 36 etapów](https://github.com/woogitsugames/Century-war/actions/runs/41323297221).
- [Drugi pełny pomiar po korekcie etapów 3, 12 i 24](https://github.com/woogitsugames/Century-war/actions/runs/41323558033).
- [Strojenie mocy AI — 7 mnożników, po 20 seedów](https://github.com/woogitsugames/Century-war/actions/runs/41323715034):
  etap 2 przy skali 0,90; etap 19 przy 1,10; etap 27 przy 0,90.
- [Pełna kontrola parametrów docelowych](https://github.com/woogitsugames/Century-war/actions/runs/41325254365)
  uruchomiona po wpisaniu wybranych mnożników bezpośrednio do `Modes.kt`.

**Pasmo akceptacji** (`tools/check_campaign_balance.py`): etapy 1–6:
85–100% wygranych; 7–27: 45–85%; 28–33: 40–75%; 34–36: 25–55%.
Dodatkowo: zero timeoutów, mediana od etapu 7 co najmniej 4 minuty,
odchylenie wewnętrznego etapu od średniej sąsiadów najwyżej 25 p.p.

### Tabela przed/po (N=20 na etap)

W kolumnie „Po” trzy etapy (2, 19, 27) wykorzystują pomiar celowanego
mnożnika `BALANCE_POWER_SCALE`; dla etapów 3, 12 i 24 użyto drugiego
pełnego pomiaru. Dla pozostałych 30 etapów nie zmieniano żadnych parametrów,
więc wyniki są identyczne z przebiegiem bazowym. Finalny, niezależny
przebieg pełnych 36 etapów weryfikuje równoważność parametrów
przeniesionych do `Campaign.stageRamp` (różnice zaokrągleń Float).

| Etap | Profil | AI przed | Zakres epok | Wygrane przed | Wygrane po | Mediana po [min] | Timeouty po |
|---:|---|---:|---|---:|---:|---:|---:|
| 1 | nowy | 0.72 | 0–0 | 100% | **100%** | 13.91 | 0 |
| 2 | nowy | 0.77 | 0–0 | 80% | **100%** | 13.72 | 0 |
| 3 | nowy | 0.83 | 0–1 | 80% | **90%** | 13.72 | 0 |
| 4 | nowy | 0.88 | 0–1 | 90% | **90%** | 13.63 | 0 |
| 5 | nowy | 0.82 | 1–1 | 95% | **95%** | 13.45 | 0 |
| 6 | nowy | 0.87 | 1–1 | 90% | **90%** | 13.53 | 0 |
| 7 | poczatkujacy | 0.93 | 1–2 | 60% | **60%** | 11.35 | 0 |
| 8 | poczatkujacy | 0.98 | 0–2 | 65% | **65%** | 12.33 | 0 |
| 9 | poczatkujacy | 0.92 | 2–2 | 65% | **65%** | 10.46 | 0 |
| 10 | poczatkujacy | 0.97 | 2–2 | 70% | **70%** | 11.47 | 0 |
| 11 | poczatkujacy | 1.00 | 2–3 | 60% | **60%** | 11.44 | 0 |
| 12 | poczatkujacy | 1.04 | 1–3 | 40% | **75%** | 10.47 | 0 |
| 13 | poczatkujacy | 1.02 | 3–3 | 70% | **70%** | 8.20 | 0 |
| 14 | poczatkujacy | 1.07 | 3–3 | 60% | **60%** | 12.12 | 0 |
| 15 | poczatkujacy | 1.13 | 3–4 | 55% | **55%** | 11.71 | 0 |
| 16 | poczatkujacy | 1.15 | 2–4 | 45% | **45%** | 13.67 | 0 |
| 17 | poczatkujacy | 1.12 | 4–4 | 65% | **65%** | 11.67 | 0 |
| 18 | poczatkujacy | 1.17 | 4–4 | 65% | **65%** | 8.80 | 0 |
| 19 | sredni | 1.27 | 4–5 | 100% | **70%** | 7.90 | 0 |
| 20 | sredni | 1.38 | 2–5 | 80% | **80%** | 13.34 | 0 |
| 21 | sredni | 1.47 | 5–5 | 75% | **75%** | 9.35 | 0 |
| 22 | sredni | 1.66 | 5–5 | 55% | **55%** | 9.53 | 0 |
| 23 | sredni | 1.46 | 5–6 | 75% | **75%** | 12.25 | 0 |
| 24 | sredni | 1.70 | 3–6 | 40% | **70%** | 12.66 | 0 |
| 25 | sredni | 1.67 | 6–6 | 50% | **50%** | 7.74 | 0 |
| 26 | sredni | 1.80 | 6–6 | 75% | **75%** | 6.41 | 0 |
| 27 | sredni | 1.92 | 6–7 | 40% | **70%** | 11.21 | 0 |
| 28 | weteran | 2.14 | 4–7 | 55% | **55%** | 12.55 | 0 |
| 29 | weteran | 2.44 | 7–7 | 75% | **75%** | 4.51 | 0 |
| 30 | weteran | 2.45 | 7–7 | 65% | **65%** | 4.72 | 0 |
| 31 | weteran | 2.55 | 7–8 | 50% | **50%** | 8.97 | 0 |
| 32 | weteran | 2.88 | 5–8 | 60% | **60%** | 10.54 | 0 |
| 33 | weteran | 2.90 | 8–8 | 55% | **55%** | 7.09 | 0 |
| 34 | weteran | 3.00 | 8–8 | 55% | **55%** | 7.65 | 0 |
| 35 | weteran | 3.01 | 8–8 | 55% | **55%** | 7.08 | 0 |
| 36 | weteran | 2.99 | 6–8 | 40% | **40%** | 11.81 | 0 |

### Zmiany tylko w parametrach etapów

| Etap | Współczynnik `stageRamp` przed | Po | Wygrane przed → po |
|---:|---:|---:|---|
| 2 | 1,000 | 0,837 | 80% → 100% |
| 3 | 1,000 | 0,900 | 80% → 90% |
| 12 | 0,960 | 0,900 | 40% → 75% |
| 19 | 1,000 | 1,265 | 100% → 70% |
| 24 | 1,000 | 0,940 | 40% → 70% |
| 27 | 1,000 | 0,846 | 40% → 70% |

Pozostałych 30 etapów nie zmieniono. Analiza porównawcza powyższych
pomiarów: **36/36** w docelowym paśmie, **0 timeoutów** i brak
przekroczenia progu 25 p.p. względem sąsiadów; najkrótsza mediana po
etapie 6 to ponad 4 minuty. Etap 2 wykazywał odpowiedź nieliniową:
sam spadek korekty do 0,93 pogorszył wynik z 80% do 75%, podczas gdy
mnożnik wybrany w teście skali 0,90 dał 100%. Nie zastępuje to
odrębnej walidacji pełnego końcowego przebiegu.

**Powtórki:** wzorce kampanii w `GoldenFixtures.kt` korzystają z etapów
1 i 29. Żaden z nich nie został zmieniony, więc regeneracja wzorców
nie jest wymagana. Testy `:sim:test`, `:app:assembleDebug` i testy
aplikacji w [CI JVM i Android](https://github.com/woogitsugames/Century-war/actions/runs/41323558058)
przeszły na wcześniejszej wersji korekt; aktualne testy końcowe
obejmują ponownie te same ścieżki.

## Zlecenie 95 — Endless: weteran, 100 seedów, fale 40–60 (1.11.2026)

**Wynik:** bieżące skalowanie fal i bossów już spełnia wszystkie trzy kryteria
akceptacji na większej próbie. Nie osłabiano późnych fal tylko dlatego,
że wcześniejsza próba 20 seedów (zlecenie 82) nie miała zwycięskiego przebiegu.
Zapobiega to nieuzasadnionemu ułatwieniu gry i zmianie historycznych powtórek.

**Warunki pomiaru:** `EndlessBalanceReportTest.veteranHundredSeeds`, seedy
**1001–1100**, `BattleConfig(Mode.ENDLESS, seed, 1f)`, dokładnie 80% poziomów
badań (jak w pomiarze 20-seedowym), gracz sterowany przez `Ai(b, 0)`,
limit 90 000 kroków oraz przerwanie na początku fali 60 z żywą bazą.
Wpis „60” jest **prawostronnie ucięty**: nie oznacza zwycięstwa nad falą 60
ani pokonania bossa tej fali. Mediana liczona jest **wyłącznie z porażek**.
Porażka podczas fali progowej nie liczy się do jej przeżycia.

Uwaga (tura 6, koordynator): kolumna „Przed” nie pochodzi z osobnego
przebiegu – audyt 95 nie zmienił symulacji, więc wartości zostały
**przepisane z kolumny „Po”**, a nie zmierzone niezależnie na `487a82b`.

| Metryka | Przed (kopia „Po”, bez osobnego pomiaru) | Po audycie (bez zmiany symulacji) | Cel |
|---|---:|---:|---:|
| Przetrwanie fali 20 | 100/100 | **100/100** | co najmniej 95/100 |
| Przetrwanie fali 30 | 94/100 | **94/100** | pomiar |
| Przetrwanie fali 40 | 75/100 | **75/100** | pomiar |
| Przetrwanie fali 50 | 69/100 | **69/100** | pomiar |
| Dotarcie żywą bazą do fali 60 | 7/100 | **7/100** | 5–15/100 |
| Mediana fali porażki | 54 | **54** | 45–55 |
| Średnia osiągnięta fala (ucięcie na 60) | 49,15 | **49,15** | pomiar |
| Porażki przed falą 60 | 93/100 | **93/100** | pomiar |
| Timeouty | 0/100 | **0/100** | 0 |

**Rozkład porażek — 100 seedów:**

| Fala porażki | Liczba seedów |
|---|---:|
| 0–19 | 0 |
| 20–29 | 6 |
| 30–39 | 18 |
| 40–44 | 4 |
| 45–49 | 3 |
| 50–54 | 36 |
| 55–59 | 26 |
| 60 (żywa baza, pomiar ucięty) | 7 |

Pomiar uruchomiono w
[GitHub Actions #41323340840](https://github.com/woogitsugames/Century-war/actions/runs/41323340840)
(test na zmodyfikowanym drzewie przeszedł; weryfikacja porównania A/B zakończyła
się błędem technicznym, gdy drugie uruchomienie Gradle użyło cache i nie
wypisało ponownie `ENDLESS_MEAN`). Workflow poprawiono przez
`--rerun-tasks` i dodano porównanie średnich z 100 seedów.

**Ochrona przed regresją:** test 100-seedowy twardo sprawdza przeżycie fali 20
(≥95/100) i szeroki zakres mediany porażek (38–60); cele strojenia (fala 60:
5–15/100, mediana 45–55) są tylko raportowane (`ENDLESS100_TARGET_*`), bo
kolejne zlecenia (97, 108) je przesuwają (korekta w turze 6). Nie zmieniono kart, doktryn,
wartości skalowania, pól stanu, `BattleSnapshot.VERSION` ani `Replay.VERSION`.

**Zależność:** podczas pomiaru zlecenie 108 (nowe karty i motywy fal) nie
było scalone z `claude/bold-sagan-vwz5gm`. Po jego scaleniu należy
**ponownie uruchomić 100 seedów**; wcześniejszego wyniku nie wolno uważać
za pomiar po zleceniu 108.


## Tura 6 – pomiar po scaleniu (1.11.2026)

Pomiar po scaleniu paczek 95, 96, 97, 108 i 109 oraz poprawek z przeglądu
(RNG AI losowany przed warunkiem oferty, preferencje osobowości dla kart 108,
rotacja tygodni). Warunki identyczne z wcześniejszymi sekcjami:
kampania `BalanceReport.campaignReport` (profil docelowy, seedy
`246813579 + etap * 10007 + próba * 997`), Endless
`EndlessBalanceReportTest` (weteran 80% badań, seedy 1001–1100, ucięcie na fali 60).
Dodatkowo, do strojenia, użyto tymczasowej równoległej kopii obu pomiarów
(niecommitowanej): dawała wyniki identyczne co do bitwy z oficjalnymi testami,
więc można było sprawdzić 100 seedów na etap oraz kontrolną próbę Endless 2001–2100.

**Uwaga o stanie wyjściowym:** w gałęzi integracyjnej zostało niezmierzone
nachylenie `hpMul` po fali 48 = 0,07 (miało zostać cofnięte). Pomiar pokazał, że
nie zmienia ono niczego istotnego (fala 60: 16/100 przy 0,06 i przy 0,07),
więc przywrócono 0,06.

### Endless (100 seedów, weteran)

| Metryka | Przed (po scaleniu, `hpMul` 0,06) | Po | Cel |
|---|---:|---:|---:|
| Przetrwanie fali 20 | 100/100 | **100/100** | ≥ 95/100 |
| Przetrwanie fali 30 | 95/100 | **95/100** | pomiar |
| Przetrwanie fali 40 | 83/100 | **83/100** | pomiar |
| Przetrwanie fali 50 | 77/100 | **77/100** | pomiar |
| Dotarcie żywą bazą do fali 60 | 16/100 | **11/100** | 5–15/100 |
| Mediana fali porażki | 54 | **54** | 45–55 |
| Średnia fala (ucięcie na 60) | 51,15 | **50,55** | pomiar |
| Próba kontrolna 2001–2100: fala 60 | 17/100 | **8/100** | 5–15/100 |
| 20 seedów 1001–1020: fala 60 | 4/20 | **3/20** | pomiar |
| Timeouty | 0 | **0** | 0 |

Rozkład porażek po zmianie (przedziały jak w sekcji zlecenia 95:
0–19, 20–29, 30–39, 40–44, 45–49, 50–54, 55–59, 60): `0, 5, 12, 3, 2, 47, 20, 11`
(przed: `0, 5, 12, 3, 1, 34, 29, 16`).

**Dlaczego obrażenia, a nie zdrowie:** seedy, które dochodzą do fali 60, to te,
w których bot gracza osiąga ostatnią epokę razem z falami; jego baza nie traci
wtedy ani punktu życia aż do fali 60. Zdrowie fal prawie nie ma na nie wpływu
(seedy 1001–1100, fala 60): nachylenie `hpMul` 0,07 → 16, 0,10 → 16, 0,25 → 13.
Dodatkowy mnożnik obrażeń po fali 48 (wariant z poprzedniej próby) też był za słaby:
0,03 → 15, 0,05 → 16, 0,08 → 15. Skuteczne okazało się wcześniejsze wejście:
od fali 45 +5%/falę → 11 (kontrola 8), od 45 +8%/falę → 8, od 41 +3%/falę → 12
(kontrola 6), od 41 +5%/falę → 8, od 41 +10%/falę → 2. Wybrano najmniejszą zmianę,
która w obu próbach daje środek pasma i nie rusza fal do 44.

**Zmiana (`Battle.kt`, dyrektor Endless):**
`e.dmgMul` dostaje dodatkowy czynnik `(1 + max(0, wave − 44) × 0,05)`
(fala 50: ×1,30, fala 55: ×1,55, fala 59: ×1,75). Fale 1–44 bez zmian,
`hpMul` jak przed turą 6 (nachylenie 0,06 po fali 48).

### Kampania (36 etapów × 20 seedów)

Zmieniono tylko korekty `Campaign.stageRamp` dwóch etapów; pozostałe 34 etapy
dają wyniki identyczne bitwa w bitwę (te same seedy, te same parametry).

| Etap | Profil | `stageRamp` przed → po | AI przed → po | Wygrane 20 seedów przed → po | Wygrane 100 seedów przed → po | Mediana po [min] |
|---:|---|---|---|---:|---:|---:|
| 16 | poczatkujacy | 0,97 → **0,93** | 1,15 → 1,10 | 40% → **60%** | 52% → **61%** | 13,36 |
| 23 | sredni | 0,92 → **0,96** | 1,46 → 1,52 | 90% → **60%** | 65% → **65%** | 12,40 |

Sąsiedzi na 100 seedach (bez zmian): etap 14: 57%, 15: 59%, 17: 54%, 18: 56%,
21: 70%, 22: 55%, 24: 52%, 25: 47%.

**Interpretacja:** wyniki pojedynczego etapu są bardzo czułe na drobne zmiany
siły AI (1–2% potrafi przestawić 20-seedowy wynik o 20–35 p.p.). Na 100 seedach
etap 23 ma ok. 62–67% wygranych dla korekt 0,92–0,97, więc wynik 90% na 20 seedach
był odstającą próbą, a nie rzeczywistą łatwością; korekta 0,96 jest kosmetyczna
i przesuwa referencyjną próbę 20 seedów do pasma i do poziomu sąsiadów (55%/65%).
Etap 16 był faktycznie najtrudniejszy w swoim bloku (52% na 100 seedach wobec
54–59% u sąsiadów); korekta 0,93 podnosi go do 61%.

Pełna tabela po zmianie (20 seedów, profil docelowy): etapy 1–6: 90–100%;
7–27: 50–75%; 28–33: 50–75%; 34–36: 45–55%. **36/36** w paśmie
`tools/check_campaign_balance.py`, 0 timeoutów, najkrótsza mediana od etapu 7:
4,51 min (etap 29), największe odchylenie od średniej sąsiadów 20 p.p. (etap 25).

Pozostałe wiersze (identyczne przed i po zmianie): 1: 100%, 2: 100%, 3: 90%, 4: 90%, 5: 95%, 6: 90%,
7: 65%, 8: 65%, 9: 65%, 10: 70%, 11: 65%, 12: 75%, 13: 70%, 14: 60%, 15: 60%,
17: 65%, 18: 65%, 19: 60%, 20: 65%, 21: 75%, 22: 55%, 24: 65%, 25: 50%,
26: 75%, 27: 65%, 28: 55%, 29: 75%, 30: 65%, 31: 50%, 32: 65%, 33: 55%,
34: 55%, 35: 55%, 36: 45%.

**Powtórki:** złote powtórki (`GoldenReplayTest`) przechodzą bez regeneracji:
wzorce kampanii używają etapów 1 i 29, a wzorce Endless/tygodnia nie dochodzą do fali 45.

**Testy:** poprawiono dwa testy nieaktualne po 108 (`Doctrine69SnapshotTest`
oczekiwał `BattleSnapshot.VERSION` 13 zamiast 14; `EndlessExpansion71Test`
liczył wszystkie karty za 34. pozycją zamiast ośmiu kart zlecenia 71).

- 2026-11-01 — regeneracja złotych powtórek: 122 ARMORED_FOE w Endless.

## Zlecenie 122 — trwałość modyfikatorów w Endless i oblężeniu tygodnia (1.11.2026)

**Przyczyna:** dyrektor nadpisywał mnożniki `e.hpMul` i `e.dmgMul` przy
przejściu do następnej fali. Efekty `ARMORED_FOE` (+30% HP) i `GLASS_CANNON`
(−30% HP, +50% obrażeń) przestawały działać; `VETERANS` (+6% dwa razy)
był już uwzględniony. Dyrektor wylicza teraz z zapisanej konfiguracji
`cfg.modifiers` bazowe mnożniki obu parametrów, bez nowych pól stanu.
Skalowanie fal i zwykłe Endless bez tych modyfikatorów pozostały identyczne.

**Regresja:** `EndlessModifier122Test` porównuje statystyki rzeczywiście
stworzonych jednostek na fali 10, wszystkie trzy modyfikatory i ich kombinację,
historyczny tydzień 2000 oraz identyczne binarnie snapshoty po odtworzeniu
bitwy między falami 9–12. Bez zmiany `BattleSnapshot.VERSION` i `Replay.VERSION`.

### Pomiar zwykłego Endless: 100 seedów weterana (1001–1100)

Uruchomiono w GitHub Actions
[porównanie przed i po](https://github.com/woogitsugames/Century-war/actions/runs/41341087387),
`EndlessBalanceReportTest`, AI gracza z 80% badań, limit fala 60.
Bez modyfikatorów wynik nie zmienia się ani o jedną falę.

| Metryka | Przed | Po | Cel |
|---|---:|---:|---|
| Przetrwanie fali 20 | 100/100 | **100/100** | ≥95/100 |
| Przetrwanie fali 30 | 95/100 | **95/100** | raport |
| Przetrwanie fali 40 | 83/100 | **83/100** | raport |
| Przetrwanie fali 50 | 77/100 | **77/100** | raport |
| Żywa baza przy fali 60 | 11/100 | **11/100** | 5–15/100 |
| Mediana fali porażki | 54 | **54** | 45–55 |
| Średnia fala (ucięcie na 60) | 50,55 | **50,55** | bez regresji |
| Średnia fala 20 seedów | 51,15 | **51,15** | bez regresji |
| Timeouty | 0 | **0** | 0 |

### Oblężenie tygodnia: pomiar parowany

`EndlessBalanceReportTest.weeklyModifierPairs122` mierzy po 8 pierwszych
tygodni po premierze z każdym modyfikatorem. Dla każdego porównuje tę samą
konfigurację (seed, start/max epoki, karta, drugi modyfikator) po usunięciu
tylko badanego modyfikatora. Gracz sterowany AI, bez badań (reguły tygodnia),
próba ucięta na fali 60.

| Modyfikator | Śr. fala z efektem | Śr. fala bez efektu | Mediana z / bez | Fala 20 z / bez |
|---|---:|---:|---|---|
| `ARMORED_FOE` | 9,625 | 10,625 | 9 / 11 | 0/8 / 0/8 |
| `GLASS_CANNON` | 10,000 | 10,125 | 10,5 / 9,5 | 0/8 / 0/8 |
| `VETERANS` | 13,125 | 13,250 | 12,5 / 12,5 | 1/8 / 1/8 |

Wszystkie próby ukończyły się przed limitem kroków; nikt w tej krótkiej próbie
nie dotarł do fali 60. Próba 8 tygodni na modyfikator nie uzasadnia
statystycznego wniosku o jego globalnej trudności: tygodnie mają
różne epoki/karty i wcześnie kończą się dla AI bez badań.
O zgodności mechaniki decydują testy dokładnych mnożników HP/obrażeń.

**Złota powtórka:** zmienił się snapshot i liczba zabójstw przeciwnika
w `weekly-start-card.json` (tydzień 3024, `GLASS_CANNON`).
Wygenerowano aktualny wzorzec przez `:sim:regenerateGolden`
z `-PgoldenReason="122 ARMORED_FOE w Endless"`.
Pozostałe wzorce bez zmian. Nie zmieniano kampanii,
`Daily.RELEASE_DAY` ani wersji protokołów; wersję rankingu aktualizuje koordynator.

## Zlecenie 127 — wyraźniejsze osobowości AI (1.11.2026)

Zmiany obejmują wyłącznie **decyzje** botów: bez zmian statystyk, kosztów,
mnożników trudności i formatów zapisu. Nie dodano żadnych pól stanu ani nowych
źródeł losowości; wybór ról nadal używa `Rng.nextAiInt`.

- **ECONOMIST:** opóźnia wyprawę od 105. sekundy, zbiera co najmniej 7 żołnierzy,
  a przy braku zagrożenia przeznacza więcej nadwyżek na ulepszenia; rzadziej buduje wieże.
- **SIEGER:** rozpoczyna inwestycje w wieże od 18. sekundy, utrzymuje większą falę
  i kieruje ostrzał na najdalej wysuniętą widoczną jednostkę w legalnym zasięgu.
- **BALANCED:** zwiększona waga doboru kontr na obserwowane role przeciwnika.

**Istotne ograniczenie:** celowany ostrzał ma zasięg 220–1370 jednostek od
bazy rzucającego. Nie może bezpośrednio uderzyć w bazę przeciwnika z drugiego
końca pasa; nie rozszerzono zasięgu zdolności tylko dla jednego bota.

### Powtarzalne pomiary A/B

`AiPersonality127ReportTest.compareThreeStylesOnFiftySeeds` mierzy trzy style
na **50 tych samych seedach** (50021 + 9973 × indeks), w potyczce epok 2–3
z modyfikatorami RICH_START i FRAGILE_BASES. Graczem kieruje BALANCED,
przeciwnik ma wymuszoną osobowość. Zapis CSV obejmuje rozkład czterech ról,
czas pierwszego ataku i pierwszego skutecznego trafienia gracza, pierwszą wieżę i ewolucję,
wieże/ostrzały/zrywy/ulepszenia na mecz oraz czas spędzony w obronie.
Niezaobserwowane zdarzenia mają pustą średnią („-”) i osobny licznik prób.

`BalanceReport.campaignReport`: **36 etapów × 20 seedów** na profil docelowy
danego etapu, identyczne seedy na kodzie bazowym i zmienionym.
Wykres A/B jest budowany przez `.github/workflows/ai-personalities-127.yml`
(oddzielne wykonania bazy i propozycji). Kryterium zmiany **średniej**
częstości zwycięstw gracza: ±5 punktów procentowych, przy pełnej tabeli
odchyleń poszczególnych etapów. Różnice pojedynczych etapów również należy
przeanalizować, ponieważ 1 wygrana z 20 odpowiada 5 p.p.

Wyniki liczbowe należy przenieść z ukończonego przebiegu CI do opisu PR.
Do czasu ukończenia pomiarów nie zakładamy, że limit ±5 p.p. jest spełniony.

### Pomiar tury 8 (2.11.2026)

Metoda: `BalanceReport.campaignReport` (profil docelowy, seedy
`246813579 + etap × 10007 + próba × 997`), liczony szybką równoległą kopią
(niecommitowaną, wyniki identyczne bitwa w bitwę; przy 100 seedach pierwsze 20 to
oficjalna próba). Baza „przed 127” = obecny kod z `Ai.kt` i `AiPersonality.kt`
sprzed scalenia `e04c1517` (inne paczki nie ruszały tych plików; Normalny 126 daje
identyczne `aiPower`). Na 20 seedach baza odtwarza tabelę z tury 6 co do etapu.

**Wynik scalonej paczki 127 (bez poprawek):** średnia 65,7% → 66,5% (100 seedów),
na 20 seedach 67,8% → 67,8%. Pojedyncze etapy na 20 seedach skakały o 20–30 p.p.
(1: 100 → 75%, 18: 65 → 40%, 24: 65 → 35%), na 100 seedach poza ±5 p.p. o więcej niż
szum (σ różnicy ≈ 7 p.p.) zostały: etap 17 (+16), 18 (−9), 12 (−8), 27 i 33 (+11).

**Błędy wykryte w testach po 127:**

- `AiPersonalityProfileTest`: Ekonomista wygrywał 87,5% (próg 30–70%, 20 seedów × 2 scenariusze).
  Już przed 127 miał na 100 seedach 67,5% przeciw BALANCED (blisko progu), a 127 dodało
  mu podwójną inwestycję w ulepszenia (`upgradeReserve` 0,75 i ×0,65 bez zagrożenia)
  oraz większą falę. Próby na 100 seedach pokazały, że rozmiar fali działa najsilniej
  (fala 8: 77%, 7: ok. 70%, 6 i 5: 64–74% zależnie od reszty parametrów); wpływ rezerwy
  ulepszeń mieści się w szumie. Wybrano wariant z wynikiem poniżej stanu sprzed 127 (64%).
- `DoctrineBalance82Test`: VETERAN 67,5% (próg 40–60%) przez silniejsze kontry BALANCED (+55/+45).
- `GoldenReplayTest` i `ChallengeRules109Test` (archiwalna powtórka dnia): każda zmiana
  decyzji AI zmienia przebieg nagrań — to oczekiwane, nie błąd; wzorce zregenerowano.
  Uwaga dla rankingu: stare powtórki dnia nie przejdą weryfikacji nowym AI (wersję
  rankingu/`SimVersion` podnosi koordynator).

**Korekty tury 8 (tylko decyzje AI, bez statystyk):**

| Parametr | 127 | Tura 8 |
|---|---:|---:|
| ECONOMIST `pushArmySize` | 7 | **5** |
| ECONOMIST `upgradeReserve` | 0,75 | **0,90** |
| ECONOMIST mnożnik rezerwy bez zagrożenia | 0,65 | **0,80** |
| BALANCED waga kontry dystans/ciężcy | +55 | **+45** |
| BALANCED waga ciężkich na wręcz | +45 | **+35** |
| BALANCED `evolveDelay` | 3 s | **6 s** |

Zachowane cechy 127: Ekonomista wstrzymuje natarcie od 105 s już w przedostatniej
epoce, Oblężnik buduje wieże od 18 s i celuje ostrzałem w najdalszą jednostkę,
BALANCED mocniej (niż przed 127) reaguje kontrami. Sonda 100 seedów (osobowość przeciw
BALANCED): ASSAULT 61%, ENGINEER 45%, ECONOMIST 64%, SIEGER 56%, BALANCED 49%.
Opóźnienie awansu BALANCED przywraca kolejność „Ekonomista awansuje najwcześniej”
(114,2 s wobec 115,3 s), którą osłabienie kontr odwróciło.

**Kampania po turze 8 (Normalny, profil docelowy):** zawiera też uczciwe elity wroga
(sekcja niżej) i korekty `stageRamp`; etapy 1–6 zmieniły się głównie przez elity.

| Etap | Przed 127 (100) | Po 127, scalone (100) | Tura 8 (100) | Tura 8 (20) | Δ |
|---:|---:|---:|---:|---:|---:|
| 1 | 89% | 86% | 90% | 85% | +1 |
| 2 | 92% | 88% | 81% | 90% | -11 |
| 3 | 91% | 94% | 90% | 100% | -1 |
| 4 | 85% | 80% | 91% | 95% | +6 |
| 5 | 91% | 96% | 92% | 100% | +1 |
| 6 | 93% | 91% | 89% | 90% | -4 |
| 7 | 72% | 67% | 72% | 80% | +0 |
| 8 | 67% | 66% | 67% | 65% | +0 |
| 9 | 64% | 69% | 75% | 80% | +11 |
| 10 | 71% | 70% | 71% | 75% | +0 |
| 11 | 60% | 64% | 64% | 60% | +4 |
| 12 | 73% | 65% | 70% | 70% | -3 |
| 13 | 66% | 64% | 67% | 65% | +1 |
| 14 | 57% | 63% | 61% | 60% | +4 |
| 15 | 59% | 56% | 55% | 60% | -4 |
| 16 | 61% | 59% | 62% | 65% | +1 |
| 17 | 54% | 70% | 59% | 60% | +5 |
| 18 | 56% | 47% | 53% | 60% | -3 |
| 19 | 64% | 59% | 61% | 60% | -3 |
| 20 | 74% | 70% | 71% | 80% | -3 |
| 21 | 70% | 74% | 71% | 65% | +1 |
| 22 | 55% | 55% | 58% | 60% | +3 |
| 23 | 65% | 67% | 64% | 65% | -1 |
| 24 | 52% | 47% | 51% | 45% | -1 |
| 25 | 47% | 54% | 59% | 70% | +12 |
| 26 | 68% | 66% | 65% | 65% | -3 |
| 27 | 58% | 69% | 64% | 70% | +6 |
| 28 | 57% | 61% | 60% | 70% | +3 |
| 29 | 71% | 76% | 73% | 70% | +2 |
| 30 | 58% | 63% | 66% | 70% | +8 |
| 31 | 52% | 50% | 47% | 50% | -5 |
| 32 | 68% | 61% | 65% | 65% | -3 |
| 33 | 60% | 71% | 68% | 60% | +8 |
| 34 | 52% | 56% | 57% | 55% | +5 |
| 35 | 60% | 62% | 62% | 55% | +2 |
| 36 | 34% | 39% | 38% | 35% | +4 |
| śr. | **65,7%** | **66,5%** | **66,9%** | **68,6%** | |

Na 20 seedach **36/36** etapów mieści się w pasmach `tools/check_campaign_balance.py`
(1–6: 85–100%, 7–27: 45–85%, 28–33: 40–75%, 34–36: 25–55%), 0 timeoutów.
Względem bazy przed 127 na 100 seedach poza ±5 p.p. zostają etapy 2 (−11; 81%, nadal
w paśmie na 20 seedach), 9 (+11) i 25 (+12, celowo: etap był najtrudniejszy w bloku, 47%).
Korekty `stageRamp` (mnożnik korekty przed → po): 1: 1 → 0,799; 2: 0,837 → 0,708;
3: 0,90 → 0,81; 4: 1 → 0,80; 5: 1 → 0,80; 6: 1 → 0,8245; 12: 0,90 → 0,828;
18: 1 → 0,92; 25: 1 → 0,97; 29: 1,12 → 1,053; 30: 1,02 → 0,989; 32: 1 → 0,97; 35: 0,88 → 0,9152 (20 seedów: 60% → 55%, w paśmie).

## Poziomy trudności kampanii — zlecenie 126 (1.11.2026)

- **Łatwy:** siła AI ×0,80, medale ×0,75 (wynik zaokrąglany w dół).
- **Normalny:** siła AI ×1,00, medale ×1,00; jedyny poziom kampanii do rankingów lokalnych, Play Games i serwera.
- **Trudny:** siła AI ×1,20, medale ×1,30; ukończenie każdego z 36 etapów zapisuje postęp do osiągnięcia „Mistrz trudnej kampanii” (600 medali).
- **Gwiazdki:** wspólny postęp odblokowuje etapy i osiągnięcia podstawowe. Osobna tablica `normalStars` rejestruje wyłącznie wyniki z Normalnego i stanowi źródło rankingu. Stare profile migrują gwiazdki do `normalStars`.
- **Powtórki:** wybrana trudność jest już zawarta w `BattleConfig.aiPower` zapisanym przez `BattleSnapshot.writeConfig` do lokalnego `ReplayRecord`; nie trzeba zmieniać wersji `Replay` (aktualna: 5). Serwer odtwarza tylko Normalny na podstawie etapu i seeda; klient nie wysyła wyników z innych poziomów. Samouczek nie daje wyniku rankingowego ani postępu do osiągnięcia Trudnego.
- **Balans:** `BalanceReport.campaignDifficultyReport` mierzy 36 etapów × 20 identycznych seedów × trzy poziomy = **2160 bitew AI kontra AI**; profil badań dobierany zależnie od etapu. Maksimum 30 min symulacji, źródło `app/build/balance/campaign-difficulty-126.csv` oraz `.md`.
- **Uruchomienie:** `BALANCE_RUN=true BALANCE_RUNS=20 ./gradlew :app:testDebugUnitTest --rerun-tasks --tests '*BalanceReport.campaignDifficultyReport*' --console=plain --no-configuration-cache`. Można dzielić na grupy przez `BALANCE_STAGES="1,2,3,4,5,6"`.

**Wyniki (tura 8, 2.11.2026):** zmierzone po wszystkich zmianach tury 8 (osobowości,
uczciwe elity, `stageRamp`), metoda jak w sekcji 127; 20 seedów = oficjalna próba
raportu, 100 seedów = kontrola szumu. Mnożniki **bez zmian** (×0,80 / ×1,00 / ×1,20).

| Etap | Łatwy (100) | Normalny (100) | Trudny (100) | Łatwy (20) | Normalny (20) | Trudny (20) |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 93% | 90% | 76% | 95% | 85% | 90% |
| 2 | 96% | 81% | 76% | 95% | 90% | 75% |
| 3 | 96% | 90% | 77% | 100% | 100% | 65% |
| 4 | 97% | 91% | 71% | 95% | 95% | 65% |
| 5 | 99% | 92% | 83% | 100% | 100% | 95% |
| 6 | 97% | 89% | 69% | 90% | 90% | 65% |
| 7 | 85% | 72% | 45% | 80% | 80% | 30% |
| 8 | 90% | 67% | 46% | 90% | 65% | 50% |
| 9 | 86% | 75% | 44% | 80% | 80% | 55% |
| 10 | 87% | 71% | 53% | 90% | 75% | 55% |
| 11 | 78% | 64% | 40% | 90% | 60% | 35% |
| 12 | 80% | 70% | 55% | 85% | 70% | 55% |
| 13 | 79% | 67% | 43% | 85% | 65% | 45% |
| 14 | 77% | 61% | 32% | 80% | 60% | 55% |
| 15 | 74% | 55% | 41% | 55% | 60% | 45% |
| 16 | 80% | 62% | 38% | 85% | 65% | 45% |
| 17 | 81% | 59% | 29% | 80% | 60% | 35% |
| 18 | 77% | 53% | 29% | 85% | 60% | 25% |
| 19 | 86% | 61% | 35% | 85% | 60% | 45% |
| 20 | 90% | 71% | 49% | 95% | 80% | 55% |
| 21 | 85% | 71% | 47% | 80% | 65% | 50% |
| 22 | 72% | 58% | 43% | 70% | 60% | 40% |
| 23 | 79% | 64% | 33% | 85% | 65% | 35% |
| 24 | 79% | 51% | 31% | 80% | 45% | 20% |
| 25 | 78% | 59% | 35% | 70% | 70% | 30% |
| 26 | 92% | 65% | 51% | 90% | 65% | 35% |
| 27 | 73% | 64% | 44% | 75% | 70% | 60% |
| 28 | 87% | 60% | 51% | 90% | 70% | 55% |
| 29 | 89% | 73% | 62% | 95% | 70% | 70% |
| 30 | 85% | 66% | 47% | 90% | 70% | 50% |
| 31 | 83% | 47% | 41% | 80% | 50% | 35% |
| 32 | 86% | 65% | 42% | 85% | 65% | 50% |
| 33 | 79% | 68% | 55% | 80% | 60% | 35% |
| 34 | 68% | 57% | 37% | 60% | 55% | 30% |
| 35 | 76% | 62% | 48% | 65% | 55% | 50% |
| 36 | 56% | 38% | 17% | 55% | 35% | 5% |
| śr. | **83,2%** | **66,9%** | **47,6%** | **83,1%** | **68,6%** | **48,2%** |

- **Łatwy:** średnio +16,3 p.p. względem Normalnego (100 seedów; 20 seedów: +14,5 p.p.),
  najsłabszy etap 36: 56%. Mieści się w zamierzonym +15–25 p.p.; na etapach 1–6
  różnica jest mała, bo Normalny daje tam już ~90%.
- **Trudny:** średnio −19,3 p.p. (47,6%), każdy etap wygrywalny: minimum 17% (etap 36)
  na 100 seedach; na 20 seedach etap 36 = 1/20 (5%), co przy 17% na 100 seedach jest szumem.
  Etapy 17–18: 29%. Nie zmieniano ×1,20 — mniejszy mnożnik zbliżyłby Trudny do Normalnego
  w środku kampanii, a koniec kampanii i tak pozostaje najtrudniejszy na każdym poziomie.

**Przegląd kodu 126 (tura 8):** rankingi lokalne, Play Games i serwer przyjmują tylko
Normalny (`ScoreClient`, `BattleScreen`, `ReplayVerifier` liczy własny config Normalnego).
Poprawki: (1) `BattleScreen` traktował nierozpoznaną siłę AI kampanii (`Campaign.difficulty`
= null) jako Normalny, więc trafiała do gwiazdek rankingowych i tablic — teraz tylko
jawny `NORMAL`, medale ×1; (2) samouczek (etap 1 przed `tutorialDone`) uruchamiany jest
zawsze na Normalnym, niezależnie od wybranego poziomu (Trudny dawał samouczkowi ×0,96 siły).
Migracja profilu (`normalStars` z `stars`, brak pola → Normalny) bez zastrzeżeń.

## Tura 8 — uczciwe elity wroga (2.11.2026)

**Problem (zgłoszenie właściciela):** elity gracza wymagają badania „Doktryna elit”
(`r_elite`, 120 medali), a `Side.eliteUnlocked` wroga było zawsze `true`. Gracz bez
badania trafiał na słonie i inne elity, których sam nie mógł wystawić. `BalanceReport`
tego nie widział: profil docelowy od etapu 7 (10% badań) zawsze ma `r_elite`.

**Zmiana (`Battle.kt`):** w kampanii i potyczce `e.eliteUnlocked = p.eliteUnlocked`.
Endless, wyzwanie dnia/tygodnia (w tym MIRROR_ARMY) bez zmian: dzień i tydzień normalizują
badania gracza (elity zawsze dostępne), Endless ma własne karty elit. AI bez elit już
wcześniej pomijało je w ulepszeniach i przy losowaniu roli (waga 0), więc złoto idzie na
pozostałe role i wieże; kolejka nie może się zablokować. `EliteFairnessTest`: kampania bez
badania → wróg nigdy nie kolejkuje ani nie szkoli ELITE; z badaniem → szkoli; potyczka
podąża za badaniem, Endless i dzień zachowują elity.
`r_rally` (Zryw) ma tę samą asymetrię — poza zakresem tej zmiany, do decyzji.

**Pomiar (Normalny, 100 seedów, seedy jak w raporcie):**

- „Bez elit” = profil docelowy bez `r_elite` (etapy 1–6 i tak go nie mają).
- „Medale” = realistyczny nowy gracz: medale wyłącznie z kampanii, pierwsza wygrana
  każdego etapu na 2 gwiazdki (`20 + 6i + 2·5 + 2·12`, i = indeks etapu od 0), od etapu 7 jedna porażka
  przed wygraną (`5 + 2i + 10`), wydawane zawsze na najtańszy następny poziom badań;
  wariant „z elitą” kupuje `r_elite` jako pierwsze. Daje to ok. 414 medali przed etapem 7
  (9 poziomów), 1236 przed 13. (22), 2346 przed 19. (33), 4551 przed 28. (48) i
  6381 przed 34. (56 poziomów) — mniej niż profile docelowe 0,4/0,8 (44/88 poziomów),
  bo pomija misje, logowanie i Endless.

| Etap | Bez elit, przed (100) | Bez elit, po (100) | Medale bez elit, przed (100) | Medale bez elit, po (100) | Medale z elitą, po (100) |
|---:|---:|---:|---:|---:|---:|
| 1 | 82% | 90% | 82% | 90% | 90% |
| 2 | 90% | 81% | 83% | 93% | 93% |
| 3 | 90% | 90% | 95% | 86% | 86% |
| 4 | 77% | 91% | 86% | 83% | 83% |
| 5 | 95% | 92% | 91% | 92% | 97% |
| 6 | 90% | 89% | 99% | 95% | 79% |
| 7 | 34% | 85% | 21% | 67% | 57% |
| 8 | 35% | 67% | 38% | 63% | 55% |
| 9 | 25% | 74% | 24% | 77% | 70% |
| 10 | 14% | 69% | 24% | 68% | 71% |
| 11 | 28% | 64% | 30% | 71% | 58% |
| 12 | 13% | 85% | 25% | 83% | 78% |
| 13 | 13% | 66% | 11% | 80% | 73% |
| 14 | 9% | 60% | 24% | 75% | 76% |
| 15 | 12% | 49% | 26% | 73% | 69% |
| 16 | 31% | 53% | 31% | 74% | 74% |
| 17 | 11% | 57% | 22% | 76% | 78% |
| 18 | 13% | 56% | 17% | 78% | 79% |
| 19 | 22% | 60% | 18% | 44% | 52% |
| 20 | 66% | 89% | 58% | 78% | 67% |
| 21 | 31% | 66% | 31% | 67% | 75% |
| 22 | 22% | 44% | 16% | 39% | 71% |
| 23 | 25% | 59% | 34% | 63% | 73% |
| 24 | 26% | 63% | 44% | 69% | 63% |
| 25 | 11% | 55% | 18% | 65% | 77% |
| 26 | 15% | 60% | 19% | 68% | 70% |
| 27 | 19% | 55% | 35% | 57% | 67% |
| 28 | 42% | 69% | 20% | 36% | 40% |
| 29 | 19% | 55% | 0% | 20% | 54% |
| 30 | 15% | 50% | 0% | 26% | 38% |
| 31 | 24% | 53% | 4% | 19% | 30% |
| 32 | 14% | 66% | 2% | 21% | 41% |
| 33 | 16% | 65% | 1% | 21% | 42% |
| 34 | 4% | 37% | 1% | 10% | 26% |
| 35 | 5% | 31% | 0% | 11% | 40% |
| 36 | 6% | 45% | 8% | 20% | 21% |
| śr. | **31,8%** | **65,0%** | **31,6%** | **59,9%** | **64,2%** |

**Wnioski:**

- Przed poprawką gracz bez elit miał od etapu 7 ścianę: 9–35% na etapach 7–19,
  średnio 31,8%. Po poprawce ten sam profil ma średnio 65,0%, minimum 31% (etap 35) i 37% (etap 34);
  etapy 7–27 ≥ 40%.
- Elity wroga we wczesnych epokach osłabiały AI (oszczędzało na drogą jednostkę), więc
  po ich wyłączeniu etapy 1–6 profilu „nowy” spadły do 55–82%. Stąd obniżone korekty
  `stageRamp` etapów 1–6 (sekcja 127, tabela tury 8): z powrotem 81–92% na 100 seedach.
- Realistyczny profil medalowy: etapy 1–27 ≥ 39% (najsłabsze 19: 44%, 22: 39%), więc bez
  ścian we wczesnej i środkowej kampanii. Etapy 28–36 (strojone pod weterana z 80% badań)
  dają mu 10–36% bez elit i 21–54% z elitą — tam postęp wymaga dodatkowych medali
  (misje, Endless, powtórki) lub poziomu Łatwy. Kosztu `r_elite` nie zmieniano:
  bariera nie wynikała z ceny, tylko z asymetrii.

- 2026-11-02 — regeneracja złotych powtórek: Tura 8 balans: osobowości 127, uczciwe elity wroga, stageRamp etapów 1-6/12/18/25/29/30/32.



## 140 — stopnie rozbudowy wież

Wieża ma stopnie 1–3. Kolejne poziomy kosztują odpowiednio 60% i 90% ceny
wieży w jej obecnej epoce; awans wieży do nowej epoki zachowuje stopień.
Każdy poziom dodaje 35% obrażeń oraz 35% szybkości strzelania względem
wartości bazowej. Trzeci dodaje rozprysk w parzystych epokach lub podpalenie
w nieparzystych. Sprzedaż zwraca połowę ceny podstawowej wieży, bez zwrotu
kosztu jej stopni.

Porównanie wykonano na bazie `a08c7987` i po zmianie, 36 etapów × 20 tych
samych seedów, profil badań `target`, bot przeciw AI, do 30 minut bitwy.
Przed: 494/720 zwycięstw (68,61%); po: 470/720 (65,28%), różnica −3,33 p.p.
łącznie. Na dziewięciu pojedynczych etapach różnica przekracza 5 p.p.; przy
20 seedach jedno zwycięstwo odpowiada 5 p.p., więc wyniki etapów są małymi próbami.
Bot wydał łącznie na kupno i ulepszenia wież 3 034 891 złota przed i 5 050 121
po, odpowiednio średnio 4 215 i 7 014 na bitwę. Pomiar wydatków wykonano
w oddzielnych tymczasowych kopiach z licznikiem niewpływającym na decyzje
symulacji; liczba zwycięstw w tych przebiegach była identyczna. Szczegóły
etapów: `docs/balance-140-before.csv` i `docs/balance-140-after.csv`.

Polecenie pomiaru zwycięstw: `BALANCE_RUN=true BALANCE_RUNS=20 BALANCE_PROFILE=target
./gradlew :app:testDebugUnitTest --tests '*BalanceReport.campaignReport*'
--no-configuration-cache`.


Test `EndlessBalanceReportTest.veteranHundredSeeds`: seedy 1001–1100,
weteran 80% badań; falę 60 osiągnięto w **13/100** przypadków (cel 5–15),
mediana fali porażki 54, średnia osiągnięta fala 52,33. W trybie Endless AI
zaczyna rozważać stopnie wież od fali 55; gracz może kupować je wcześniej.
Pełny zestaw testów serwera i symulacji zakończył się powodzeniem.

- 2026-11-02 — regeneracja złotych powtórek: 140 stopnie wiez.


## Zlecenie 125 — dowódca na polu bitwy

**Projekt i zakres:** [docs/tasks/125-commander-design.md](tasks/125-commander-design.md).
Od etapu 8 kampanii, w potyczce, Endless i nowych wyzwaniach dnia obie strony mają dostęp do dowódcy za 2× koszt jednostki ciężkiej. Limit 1 aktywnego dowódcy na stronę, 180% HP i 115% obrażeń elity, aura +10% obrażeń pobliskich oddziałów w promieniu 230, Impuls co 20 s, odnowienie 90 s po śmierci (45 s w specjalnym dniu). Bez zmian istniejących jednostek i ich cen. AI kupuje dowódcę z własnego złota; w Endless z budżetu fali. Replay v5/v6 zachowują oryginalne reguły dzięki wyłączeniu dowódcy.

**Weryfikacja przed/po:** `.github/workflows/campaign-balance.yml` wykonuje 36 etapów po 20 seedów (6 podgrup) z `BALANCE_PROFILE=target`, a `.github/workflows/endless-balance.yml` porównuje bazę PR i bieżący kod z raportu `EndlessBalanceReportTest` dla 20 i 100 seedów. Wyniki muszą pochodzić z faktycznych przebiegów CI; ten wpis **nie stanowi potwierdzenia testów ani liczb balansu**. Linki do przebiegów i rzeczywiste pomiary należy dopisać do opisu PR.

- 2026-11-02 — regeneracja złotych powtórek: Dowodca w polu bitwy: nowy Replay v7, Snapshot v16 i uczciwe AI.

- 2026-11-02 — regeneracja złotych powtórek: Reaktywne AI kampanii dowodcy bez zmiany starych wynikow.


### Zlecenie 125 — pomiary A/B wykonane 2.11.2026

**Kampania, dokładna para bazowa/nowa:** baza `claude/bold-sagan-vwz5gm`
(SHA `0da3a5c026ef70c3d2107872f9becd677d669159`) vs `gpt/125-commander`
(po zmianie AI na reaktywne), ten sam `BalanceReport.campaignReport`,
profil docelowy, **36 etapów × 20 seedów = 720 bitew na wersję**.

- **Baza: 471/720 (65,42%)**, dowódca: **471/720 (65,42%)**, różnica **0,00 p.p.**
- **36/36 etapów bez różnicy** w liczbie zwycięstw, medianie minut i odsetku patów; łącznie 0 patów. Cel braku regresji osiągnięty.
- **Walidator progów nadal sygnalizuje 3 naruszenia istniejące także w bazie**: etap 1 = 75% wobec limitu 85–100%; etap 8 = 50%, odchylenie 27,5 p.p. od sąsiednich etapów (limit 25); etap 29 = mediana 2,75 min (minimum 4 min). **Nie zmieniano balansu istniejących jednostek ani etapów w ramach 125.**
- Pomiar bazy: [Actions 41426714372](https://github.com/woogitsugames/Century-war/actions/runs/41426714372).
- Pomiar zmiany: [Actions 41426131917](https://github.com/woogitsugames/Century-war/actions/runs/41426131917).

**Endless**, porównanie tego samego `EndlessBalanceReportTest` ze stanem bazowym:
- 20 seedów: **51,80 → 50,70**, zmiana **−2,12%**.
- 100 seedów: **52,33 → 50,50**, zmiana **−3,50%**.
- [Actions 41426312995](https://github.com/woogitsugames/Century-war/actions/runs/41426312995), wykonanie zakończone powodzeniem.

Wobec bazy nie zaszła zmiana przebiegu testowych walk kampanii, ponieważ
AI kampanijne reaguje na przywołanie dowódcy przez przeciwnika, lecz go samo
nie inicjuje. Regeneracja 10 złotych powtórek v7/Snapshot v16 wykonana przez
`./gradlew :sim:regenerateGolden` na GitHub Actions
([przebieg](https://github.com/woogitsugames/Century-war/actions/runs/41426305006)).

### Tura 9 — doktryny z dowódcą (DoctrineBalance82, 2.11.2026)

Lustrzany pojedynek doktryn (epoka 3, kruche bazy, bogaty start, AI BALANCED po obu stronach),
seedy 82000–82099 z zamianą stron (200 bitew na doktrynę). Procent wygranych strony z doktryną:

| Doktryna | bez dowódcy | z dowódcą |
|---|---|---|
| ECONOMY | 54% | 57% |
| SIEGE | 50% | 50% |
| BULWARK | 59% | 58% |
| SWIFT | 49% | 47% |
| VETERAN | 66% | 57% |
| FURY | 55% | 59% |
| ARSENAL | 47% | 42% |
| TACTICS | 56% | 53% |
| PLUNDER | 58% | 63,5% |
| BANNER | 51% | 46% |
| SUPPLY | 49% | 57% |
| SIEGE_MASTERS | 50% | 50% |
| VIGILANCE | 50% | 52% |

Wcześniejszy test na 20 seedach był zbyt szumny (ECONOMY 70% przy 20 seedach, 57% przy 100).
Test używa teraz 100 seedów i pasma 40–65%. Bez zmian w regułach gry; PLUNDER (63,5%) i ARSENAL (42%)
są na brzegach pasma — kandydaci do strojenia, jeśli pomiar kampanii to potwierdzi.

## Tura 9 — kampania z dowódcą (2.11.2026)

**Pytanie:** czy dowódca (125) trywializuje etapy 8–36, gdy gracz używa go rozsądnie,
i co zmienia na etapach 28–36 dla gracza zbierającego medale tylko z kampanii.
Uwaga do opisu 125 wyżej: dowódca bazuje na bieżącej jednostce **ciężkiej** (180% HP,
115% obrażeń), zgodnie z projektem — nie na elicie.

**Narzędzie (`BalanceReport`, bez zmiany domyślnego zachowania):**

- `BALANCE_COMMANDER=on` — AI gracza przywołuje dowódcę samo, gdy jest dostępny i stać je
  (ta sama reguła co w potyczce: zagrożenie, ≥2 żywe jednostki lub ≥35 s bitwy);
  `off` — nigdy; brak zmiennej — zasady trybu (w kampanii tylko w odpowiedzi na dowódcę
  wroga, czyli praktycznie nikt go nie przywołuje). AI wroga w kampanii pozostaje
  reaktywne: odpowiada własnym dowódcą, gdy gracz ma aktywnego.
  Przełącznik to pole `Ai.commanderOverride` (domyślnie `null`, nie trafia do snapshotów ani
  powtórek; test w `Commander125Test`).
- `BALANCE_PROFILE=medale` / `medale-elita` — realistyczny gracz z tury 8 (medale tylko
  z kampanii, najtańszy następny poziom badań; `medale-elita` kupuje `r_elite` najpierw).
  Uwaga: „medale” kupuje elitę samo ok. etapu 19, więc od 19 oba profile są identyczne.
- Raport CSV ma kolumny `player_commander_battles` i `enemy_commander_battles`.
- Endless: `BalanceReport.endlessCommanderReport` (weteran 80%, seedy 1001–1100, do fali 60),
  warianty `bez` (reguły bez dowódcy), `wrog` (tylko wróg), `z` (obie strony).

Polecenie: `BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target BALANCE_STAGE_FROM=8
BALANCE_COMMANDER=on ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.campaignReport*'`.

### Kampania, 100 seedów na etap (Normalny)

„Bez” = obecne zachowanie (nikt nie przywołuje), „z dowódcą” = AI gracza przywołuje go
rozsądnie. Etapy 1–7 są identyczne (dowódca od etapu 8). Gracz miał dowódcę w 97% bitew
(2818/2900), wróg odpowiadał w 94% (2735/2900); 0 patów w obu wariantach.

| Etap | Profil docelowy: bez | z dowódcą | Δ [pp] | Mediana bez/z [min] | Medale: bez | z dowódcą | Δ [pp] |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 8 | 67% | 68% | +1 | 13,3 / 13,6 | — | — | — |
| 9 | 75% | 73% | −2 | 11,8 / 12,1 | — | — | — |
| 10 | 67% | 68% | +1 | 12,9 / 13,1 | — | — | — |
| 11 | 62% | 54% | −8 | 11,9 / 12,2 | — | — | — |
| 12 | 69% | 72% | +3 | 11,6 / 11,8 | — | — | — |
| 13 | 67% | 60% | −7 | 9,3 / 9,6 | — | — | — |
| 14 | 57% | 56% | −1 | 12,2 / 13,0 | — | — | — |
| 15 | 53% | 61% | +8 | 12,1 / 13,3 | — | — | — |
| 16 | 67% | 51% | −16 | 13,6 / 13,8 | — | — | — |
| 17 | 58% | 48% | −10 | 12,4 / 13,0 | — | — | — |
| 18 | 58% | 50% | −8 | 11,6 / 12,1 | — | — | — |
| 19 | 65% | 62% | −3 | 9,7 / 10,1 | 50% | 53% | +3 |
| 20 | 68% | 81% | +13 | 13,2 / 13,2 | 64% | 70% | +6 |
| 21 | 68% | 70% | +2 | 10,2 / 10,2 | 71% | 73% | +2 |
| 22 | 59% | 64% | +5 | 9,0 / 8,7 | 71% | 57% | −14 |
| 23 | 57% | 63% | +6 | 13,2 / 13,1 | 71% | 68% | −3 |
| 24 | 56% | 60% | +4 | 13,3 / 13,4 | 63% | 61% | −2 |
| 25 | 60% | 58% | −2 | 10,4 / 10,6 | 84% | 79% | −5 |
| 26 | 64% | 68% | +4 | 9,3 / 8,8 | 68% | 65% | −3 |
| 27 | 65% | 61% | −4 | 12,4 / 12,4 | 68% | 68% | +0 |
| 28 | 71% | 66% | −5 | 13,2 / 13,3 | 38% | 35% | −3 |
| 29 | 75% | 76% | +1 | 3,8 / 1,9 | 48% | 45% | −3 |
| 30 | 65% | 62% | −3 | 9,7 / 7,7 | 42% | 46% | +4 |
| 31 | 48% | 76% | +28 | 10,9 / 7,5 | 36% | 39% | +3 |
| 32 | 63% | 68% | +5 | 11,7 / 11,3 | 47% | 36% | −11 |
| 33 | 59% | 60% | +1 | 6,6 / 7,6 | 37% | 37% | +0 |
| 34 | 55% | 53% | −2 | 8,6 / 8,4 | 31% | 23% | −8 |
| 35 | 56% | 53% | −3 | 7,1 / 6,7 | 40% | 29% | −11 |
| 36 | 38% | 47% | +9 | 12,9 / 12,7 | 29% | 24% | −5 |
| **8–18** | 63,6% | 60,1% | −3,5 | | — | — | — |
| **19–27** | 62,4% | 65,2% | +2,8 | | 67,8% | 66,0% | −1,8 |
| **28–36** | 58,9% | 62,3% | +3,4 | | 38,7% | 34,9% | −3,8 |
| **8–36** | 61,8% | 62,4% | +0,6 | | — | — | — |
Kontrola na 20 seedach (36 etapów, ten sam układ): profil docelowy 8–36 59,7% → 64,7%,
28–36 56,7% → 63,3%; medale 28–36 41,7% → 32,8%; medale-elita 8–18 72,3% → 76,4%.
Przy 20 seedach jedna wygrana to 5 pp, więc kierunki są te same, ale skala różnic jest
szumem — rozstrzyga tabela 100-seedowa.

### Endless (weteran, 100 seedów, do fali 60)

| Wariant | Śr. fala | Mediana porażki | Fala 40 | Fala 50 | Fala 60 |
|---|---:|---:|---:|---:|---:|
| bez dowódcy | 52,33 | 54 | 89 | 85 | 13 |
| dowódca tylko u wroga | 50,64 | 53 | 84 | 76 | 8 |
| dowódca obu stron (stan gry) | 50,50 | 53 | 82 | 79 | 7 |

Wynik zgodny z pomiarem 125 (52,33 → 50,50). Dowódca wroga z budżetu fali kosztuje gracza
ok. 1,7 fali; dowódca gracza tego nie odrabia (AI gracza miało go w 100/100 biegów).
Cele Endless (fala 60: 5–15%, mediana 45–55) nadal spełnione, ale fala 60 jest blisko dolnej
granicy (7/100).

### Wnioski

- **Dowódca nie trywializuje kampanii.** Profil docelowy 8–36: 61,8% → 62,4% (+0,6 pp);
  wszystkie etapy 47–81% (cel ≥45% od etapu 19, ≥55% dla początkującego — etapy 16–18
  spadają do 48–51%, czyli poniżej progu 55%). Ani jeden etap nie przekracza 81%.
- Na początku (8–18, gracz z 10% badań) dowódca jest **raczej obciążeniem** (−3,5 pp):
  2× cena ciężkiej jednostki to duży wydatek przy niskim dochodzie, a wróg z mnożnikiem
  dochodu odpowiada własnym dowódcą. Później (19–36) daje +3 pp.
- Odstające: etap 31 (+28 pp, 48% → 76%; gracz wygrywa przed awansem wroga do epoki 8,
  średnia epoka 7,80 → 7,55) i 20 (+13 pp); etapy 16–17 (−10…−16 pp).
- **Etapy 28–36 dla gracza „z medali” nie poprawiają się — lekko pogarszają:** 38,7% →
  34,9% (−3,8 pp), najsłabsze 34–36: 23–29%. Dowódca nie rozwiązuje znanego problemu
  końca kampanii; reaktywny dowódca wroga przy aiPower 2,2–3,7 jest silniejszy niż
  dowódca gracza.
- Etap 29 ma z dowódcą medianę 1,9 min (było 3,8; problem istniał przed 125 — próg 4 min).

**Strojenie: nie wykonano.** Wyniki mieszczą się w celach albo są na granicy szumu, a żadna
mała zmiana parametru dowódcy nie celuje w problem 28–36 (to nie dowódca go tworzy).
Reguły symulacji bez zmian, `SimVersion.PROTOCOL` pozostaje 0.8.0, złote powtórki bez
regeneracji.

**Do decyzji właściciela:**

1. Etapy 28–36 dla gracza „z medali” (35–39%, 34–36 poniżej 30%): obniżyć `stageRamp`
   ostatnich etapów (np. 34–36 o ~10%) **albo** zostawić jako ścianę, którą przechodzi się
   z medalami z misji/Endless lub na poziomie Łatwy. Dowódca tu nie pomaga.
2. Etapy 16–18 z dowódcą 48–51% (bez 58–67%): ewentualnie dowódca tańszy na wczesnych
   etapach (np. koszt 1,5× ciężkiej zamiast 2×) — zmiana reguł, wymaga nowej wersji
   protokołu i regeneracji powtórek; przy 100 seedach efekt jest jednak umiarkowany.
3. Etap 31 (+28 pp) i mediana etapu 29: obserwować; jeśli gracze faktycznie używają dowódcy,
   rozważyć `stageRamp` etapu 31 lub `maxEra`.
4. Endless: fala 60 spadła z 13 do 7/100 — w celu, ale po kolejnej zmianie utrudniającej
   może wypaść poniżej 5.

## Tura 10 — lżejsze etapy 16–18 i 34–36 (2.11.2026)

Decyzja właściciela po pomiarze tury 9: etapy 34–36 lżejsze dla gracza z medalami tylko
z kampanii (cel 40–50% z dowódcą, profil docelowy ≤ ok. 75%), etapy 16–18 lżejsze dla
profilu docelowego z dowódcą (cel 55–70%). Zasady dowódcy, AI, Endless, potyczka,
wyzwania i pozostałe etapy bez zmian.

**Zmiana:** tylko korekty `Campaign.stageRamp` (`sim/.../Modes.kt`). Ten mnożnik trafia
wyłącznie do `Campaign.stages[i].aiPower`, czyli do kampanii (Endless, potyczka, wyzwania
dnia i oblężenie tygodnia mają własne `aiPower`). Siła AI na poziomie Normalnym:

| Etap | Korekta przed → po | Zmiana | aiPower przed → po |
|---:|---:|---:|---:|
| 16 | 0,93 → 0,86 | −7,5% | 1,10 → 1,02 |
| 17 | 1 → 0,92 | −8% | 1,12 → 1,03 |
| 18 | 0,92 → 0,85 | −7,6% | 1,08 → 1,00 |
| 34 | 0,95 → 0,727 | −23,5% | 3,00 → 2,29 |
| 35 | 0,9152 → 0,778 | −15% | 3,13 → 2,66 |
| 36 | 0,81 → 0,6075 | −25% | 2,99 → 2,24 |

Obniżka o 10% na 34–36 nie wystarczyła: medale z dowódcą 36% / 38% / 38% (cel 40–50%).
Odpowiedź gracza „z medali” na aiPower jest płaska do ok. −15%, potem rośnie skokowo
(etap 34: −15% → 37%, −22% → 40%, −23,5% → 45%, −25% → 53%), więc korekty 34–36 są
większe niż wstępne 10% i dobrane osobno dla każdego etapu.

`SimVersion.PROTOCOL` 0.8.0 → 0.8.1 (zmiana przebiegu bitew kampanii). Złote powtórki
(kampania: etapy 1 i 29) nie zmieniają wyników; regeneracja zapisana niżej.

### Pomiar, 100 seedów na etap, Normalny

„Z dowódcą” = `BALANCE_COMMANDER=on` (AI gracza przywołuje go rozsądnie — ta sama kolumna
co w tabeli tury 9, do niej odnoszą się cele); „zasady trybu” = zmienna nieustawiona
(gracz przywołuje dowódcę tylko w odpowiedzi na dowódcę wroga, czyli praktycznie nigdy).
Etapy 15, 19 i 33 nie zmieniły się (kontrola determinizmu: wyniki identyczne jak w turze 9).

| Etap | Docelowy z dowódcą: przed → po | Docelowy, zasady trybu: przed → po | Medale z dowódcą: przed → po | Medale, zasady trybu: przed → po |
|---:|---:|---:|---:|---:|
| 15 | 61% → 61% | 53% → 53% | — | — |
| 16 | 51% → **63%** | 67% → 65% | — | — |
| 17 | 48% → **64%** | 58% → 65% | — | — |
| 18 | 50% → **65%** | 58% → 63% | — | — |
| 19 | 62% → 62% | 65% → 65% | 53% | 50% |
| 32 | 68% | 63% | 36% → 36% | 47% |
| 33 | 60% → 60% | 59% → 59% | 37% → 37% | 37% → 37% |
| 34 | 53% → 62% | 55% → 70% | 23% → **45%** | 31% → 55% |
| 35 | 53% → 66% | 56% → 67% | 29% → **46%** | 40% → 50% |
| 36 | 47% → 62% | 38% → 76% | 24% → **46%** | 29% → 49% |

Mediany czasu bitwy po zmianie (z dowódcą, docelowy): 16–18 12,4–13,8 min, 34–36
5,0–12,9 min (etap 35 krótki jak wcześniej, 6,7 → 5,0 min).

### Wnioski

- Cele spełnione z dowódcą: 16–18 63–65% (≥55%, ≤70%), 34–36 docelowy 62–66% (≤75%),
  medale 45–46% (40–50%).
- Krzywa 15–19 (z dowódcą): 61 → 63 → 64 → 65 → 62% — łagodne wypłaszczenie zamiast dołka
  48–51%; między kolejnymi etapami najwyżej +2 pp łatwiej (w kampanii zdarzały się
  skoki do +19 pp, np. 19 → 20).
- Krzywa 32–36 dla „medali”: 36 → 37 → 45 → 46 → 46%. Skok 33 → 34 (+8 pp) jest mniejszy niż
  dotychczasowe skoki łatwości (np. 19 → 20 +19 pp, 30 → 31 +14 pp profilu docelowego).
- Uwaga: bez dowódcy po stronie gracza (zasady trybu) profil docelowy na etapie 36 ma 76%
  (wcześniej 38%), czyli ostatni etap przestaje być wyraźnie najtrudniejszy dla gracza
  z pełnymi badaniami. Gracz „z medali” nadal ma tam ok. 50%.

- 2026-11-02 — regeneracja złotych powtórek: Tura 10: lzejsze etapy 16-18 i 34-36.

- 2026-11-02 — regeneracja złotych powtórek: Tura 10: doktryny Grabież i Arsenał.

## Tura 10 — wyrównanie Grabieży i Arsenału (2.11.2026)

**Decyzja właściciela:** lekko wyrównać dwie doktryny z brzegów pasma testu lustrzanego
(PLUNDER 63,5%, ARSENAL 42%); cel obu ok. 47–58%.

**Zmiany reguł (`Battle.applyDoctrine`):**

| Doktryna | Przed | Po | Zakres |
|---|---|---|---|
| PLUNDER (Grabież) | +8% złota za zabójstwa | **+6%** | kampania, potyczka, dzienne itd.; karta Endless bez zmian (+35%) |
| ARSENAL (Arsenał) | +1 slot wieży, −20% kosztu wież | +1 slot, **−35%** kosztu wież | wszystkie tryby, także karta Endless |

ARSENAL zmieniono również w Endless, bo karta Endless „Arsenał” korzysta z tego samego opisu
`d_arsenal_d` (osobny opis wymagałby zmiany ekranu bitwy). Opisy `d_plunder_d` i `d_arsenal_d`
poprawione w 10 językach, klucze bez zmian. `SimVersion.PROTOCOL` 0.8.1 → 0.8.2; złote powtórki
zregenerowane bez zmian wyników (żadna nie używa tych doktryn).

**Siatka strojenia (DoctrineBalance82, 200 bitew na wartość):** PLUNDER 0,08/0,06/0,05/0,04 →
63,5/58/51/55%; ARSENAL koszt −20/−30/−35/−40/−50% → 42/56,5/52,5/48/48%; dodatkowe +10–15%
obrażeń wież przy ARSENAL nie pomagało (−20% i +15%: 42,5%). Pierwszy wybór 0,05 i −35%
(51% / 52,5%) łamał `AiPersonalityProfileTest` (sim): osobowość SIEGER, która czasem bierze
Grabież, spadała do 27,5% wygranych przy progu 30% (przy 0,08 i −35%: 32,5%; 0,04: 27,5%;
0,05 z −30%: 25%). Ostatecznie **+6% i −35%**: SIEGER 30% (test zielony), PLUNDER 58% —
na górnej granicy celu 47–58%. SIEGER był na granicy progu już przed zmianą.

**DoctrineBalance82 (lustrzany, seedy 82000–82099 z zamianą stron, z dowódcą):**

| Doktryna | Przed | Po |
|---|---:|---:|
| ECONOMY | 57,5% | 57,5% |
| SIEGE | 50,5% | 50,5% |
| BULWARK | 58,5% | 58,5% |
| SWIFT | 47,5% | 47,5% |
| VETERAN | 57,5% | 57,5% |
| FURY | 59,5% | 59,5% |
| ARSENAL | 42% | **52,5%** |
| TACTICS | 53,5% | 53,5% |
| PLUNDER | 63,5% | **58%** |
| BANNER | 46,5% | 46,5% |
| SUPPLY | 57,5% | 57,5% |
| SIEGE_MASTERS | 50% | 50% |
| VIGILANCE | 52,5% | 52,5% |

Pozostałe doktryny są deterministycznie identyczne (zmiana dotyczy tylko strony z PLUNDER/ARSENAL).
Rozrzut po zmianie: 46,5–59,5% (wcześniej 42–63,5%).

**Endless (`BalanceReport.endlessCommanderReport`, weteran, 100 seedów, dowódca obu stron;
zmienia go tylko ARSENAL −35%, Grabież w Endless bez zmian):**
śr. fala 50,50 → 50,85, fala 40 82 → 84/100, fala 50 79 → 80/100, **fala 60 7 → 8/100**
(cel 5–15/100 zachowany).

**Kampania (`BalanceReport.campaignReport`, profil docelowy, AI kampanii wybiera doktryny wg osobowości;
„po” = ostateczne +6% / −35%):**

| Etap | Profil | N | Przed | Po |
|---:|---|---:|---:|---:|
| 6 | nowy | 40 | 100% | 100% |
| 12 | początkujący | 40 | 70% | 70% |
| 18 | początkujący | 40 | 62,5% | 62,5% |
| 24 | średni | 100 | 56% | 58% |
| 30 | weteran | 40 | 60% | 60% |
| 34 | weteran | 100 | 70% | 70% |
| 35 | weteran | 100 | 67% | 67% |
| 36 | weteran | 100 | 76% | 74% |

Bez wyraźnych zmian w kampanii (różnice ±2 bitwy na 100; mediany czasu ±0,5 min).

- 2026-11-02 — regeneracja złotych powtórek: Tura 10: warianty Pary i Krzemu (163).
- 2026-11-03 — regeneracja złotych powtórek: format zrzutu bitwy v17 (identyfikator rozliczenia, A156-10). Zmieniły się wyłącznie skróty `snapshotSha256`; wyniki, logi i stany symulacji bez zmian.

- 2026-11-03 — regeneracja złotych powtórek: Tura 10: snapshot v17 po scaleniu z 163.
## Zlecenie 170 — nowa zawartość (3.11.2026)

Nowości bez zmian kampanii: wydarzenia dnia `SIEGE_WORKS` (wieżyczki −40% kosztu, +25% szybkostrzelności), `FORCED_MARCH` (+25% prędkości, +20% szkolenia), `SPOILS_OF_WAR` (+75% złota za zabicie) — wszystkie symetryczne dla obu stron, losowane dopiero od `Daily.EVENTS_V2_DAY` = `RELEASE_DAY + 8` (wcześniejsze dni, w tym przypięta powtórka `RELEASE_DAY + 3`, losują z zamrożonej szóstki). Karty Endless `QUARTERMASTER` (zwykła, +20% XP) i `MASTER_MASONS` (niepospolita, −12% kosztu wieżyczek i ich rozbudowy; tylko ze slotami wież).

**Endless (`BalanceReport.endlessCommanderReport`, weteran, 100 seedów 1001–1100, wariant `z`):**

| | Śr. fala | Mediana porażki | Fala 40 | Fala 50 | Fala 60 |
|---|---:|---:|---:|---:|---:|
| main przed 170 | 50,85 | 53 | 84/100 | 80/100 | 8/100 |
| po 170 | 51,38 | 53 | 87/100 | 82/100 | 5/100 |

`EndlessBalanceReportTest` (serwer): `TARGET_MEDIAN=OK`, `TARGET_SURVIVE_60=OK` (5/100, dolna granica celu 5–15). Nowe karty zmieniają losowanie ofert (inne karty w kolejnych falach), dlatego bot częściej dochodzi do fal 40–50, a rzadziej do 60 — wynik w celu, ale na granicy; przy kolejnej zmianie utrudniającej Endless trzeba to zmierzyć ponownie.

Złote powtórki: nowe pozycje enumu `EndlessCard` wydłużają zapis `cardStacks` w migawce, więc `GoldenReplayTest` wymaga regeneracji (zrobi to koordynator razem z podbiciem `SimVersion.PROTOCOL`).

- 2026-11-03 — regeneracja złotych powtórek: Tura 10: nowa zawartosc 170.

**Noc 4 — wydarzenie dnia zgodne z modyfikatorami (od `EVENTS_V2_DAY`).** Jedna lista sprzeczności `Daily.EVENT_CONFLICTS`: `SIEGE_WORKS` × `NO_TURRETS`/`EXPENSIVE_TOWERS`, `FORCED_MARCH` × `FAST_UNITS`, `FORTIFIED_BASES` × `FRAGILE_BASES` (×2 i ×0,5 zdrowia bazy), `MARKSMEN` × `RANGED_HORDE` („tylko strzelcy” czyni hordę pustą). Pierwsze losowanie bez zmian; przy kolizji drugie losowanie z tego samego strumienia (`Rng(day * 131071 + 907)`) spośród wydarzeń zgodnych. Modyfikatory, epoki, siła AI i pogoda bez zmian; zmienia się tylko wydarzenie (a przez nie epoka startu przy `GOLD_RUSH` oraz siła 100% i styl dowódcy przy `MIRROR_ARMY`). Dni przed `EVENTS_V2_DAY` (w tym przypięta powtórka `RELEASE_DAY + 3`) identyczne — odcisk dni `RELEASE_DAY − 30 … EVENTS_V2_DAY − 1` w `DailyEventConflictsTest`. Bez innych par: `GOLD_RUSH` z `DOUBLE_INCOME`/`RICH_START` i `MIRROR_ARMY` z `NO_TURRETS` się sumują, ale sobie nie przeczą.

Zmienione dni w pierwszych 3 latach od `EVENTS_V2_DAY` (21 z 273 dni z wydarzeniem; przed poprawką → po):

| Dzień | Od premiery | Modyfikatory | Przed | Po |
|---|---:|---|---|---|
| 21164 | +11 | DOUBLE_INCOME, NO_TURRETS | SIEGE_WORKS | FORTIFIED_BASES |
| 21196 | +43 | FRAGILE_BASES, GLASS_CANNON | FORTIFIED_BASES | GOLD_RUSH |
| 21304 | +151 | RANGED_HORDE, EXPENSIVE_TOWERS | SIEGE_WORKS | FORCED_MARCH |
| 21308 | +155 | FRAGILE_BASES, FOG_OF_WAR | FORTIFIED_BASES | SPOILS_OF_WAR |
| 21320 | +167 | FRAGILE_BASES, NO_TURRETS | FORTIFIED_BASES | MARKSMEN |
| 21372 | +219 | RANGED_HORDE, ARMORED_FOE | MARKSMEN | SPOILS_OF_WAR |
| 21376 | +223 | FRAGILE_BASES, GLASS_CANNON | FORTIFIED_BASES | MIRROR_ARMY |
| 21428 | +275 | RANGED_HORDE, SHORT_DAY | MARKSMEN | SIEGE_WORKS |
| 21756 | +603 | NO_TURRETS, RANGED_HORDE | SIEGE_WORKS | RAPID_ABILITIES |
| 21768 | +615 | FAST_UNITS, ARMORED_FOE | FORCED_MARCH | FREE_BARRAGE |
| 21800 | +647 | NO_TURRETS, MERCENARIES | SIEGE_WORKS | FREE_BARRAGE |
| 21924 | +771 | NO_TURRETS, SHORT_DAY | SIEGE_WORKS | RAPID_ABILITIES |
| 22036 | +883 | RANGED_HORDE, RICH_START | MARKSMEN | SIEGE_WORKS |
| 22040 | +887 | FRAGILE_BASES, NO_TURRETS | FORTIFIED_BASES | RAPID_ABILITIES |
| 22060 | +907 | FAST_UNITS, EXPENSIVE_TOWERS | SIEGE_WORKS | FORTIFIED_BASES |
| 22084 | +931 | FRAGILE_BASES, VETERANS | FORTIFIED_BASES | RAPID_ABILITIES |
| 22092 | +939 | RANGED_HORDE, ARMORED_FOE | MARKSMEN | GOLD_RUSH |
| 22152 | +999 | FRAGILE_BASES, SLOW_XP | FORTIFIED_BASES | FREE_BARRAGE |
| 22172 | +1019 | NO_TURRETS, RICH_START | SIEGE_WORKS | SPOILS_OF_WAR |
| 22188 | +1035 | DOUBLE_INCOME, FRAGILE_BASES | FORTIFIED_BASES | GOLD_RUSH |
| 22228 | +1075 | NO_TURRETS, ARMORED_FOE | SIEGE_WORKS | MIRROR_ARMY |

Wszystkie zmienione dni są po premierze (`RELEASE_DAY` to jeszcze data zastępcza), więc nikt ich nie grał. Konfiguracja dnia to dane wejściowe powtórki (serwer odtwarza ją z numeru dnia i porównuje wydarzenie), więc klient i serwer muszą mieć ten sam algorytm — podbicie `SimVersion.PROTOCOL` zostawiam koordynatorowi (zob. raport nocy 4).

## Dzień 1 — krótsze etapy 2–7 (3.11.2026)

**Decyzja właściciela:** etapy kampanii 2–7 mają trwać ok. 7–9 min mediany zamiast 12–14,
żeby nowy gracz zobaczył dowódcę (etap 8) po ok. 50–60 min gry razem z samouczkiem
(raport `docs/reports/noc-5-pierwsza-godzina.md`: 87 min). Etap 1 i etapy ≥ 8 bez zmian.

**Zmiana:** w etapach 2–7 (indeksy 1–6, każdy poziom trudności) presja oblężnicza
startuje po **4 minutach** zamiast 8 i rośnie o **80%/min** zamiast 20%/min
(`Campaign.siegeStart` / `Campaign.siegeRamp`, `Battle.siegeStart` / `Battle.siegeRamp`).
Blokada baz (`SIEGE_ATTRITION`) i premia do obrażeń baz korzystają z tej samej presji.
Dodatkowo etap 7 ma korektę `stageRamp` **0,95** (aiPower 0,87 → 0,83): krótsza blokada
zostawia zwycięzcy mniej HP bazy, więc gwiazdek i medali przed etapem 7 jest mniej
(niżej), a w przejściu „pierwszej godziny” etap 7 przegrywał się w połowie podejść.
Endless, potyczka, wyzwania dnia/tygodnia, samouczek, AI i pozostałe `aiPower` bez zmian.
`SimVersion.PROTOCOL` 0.8.4 → 0.8.5. Złote powtórki (kampania: etapy 1 i 29) nie zmieniły
wyników; `BattleSnapshot.defsFingerprint` bez zmian, więc przerwana bitwa wznawia się po
aktualizacji (na nowych zasadach presji).

**Dlaczego ten mechanizm.** Wszystkie bitwy etapów 2–7 w pomiarze kończą się przez blokadę:
mediana 13,2–13,6 min, czyli ok. 5,5 min po starcie presji. Mniejsze HP baz **nie skraca**
bitwy (HP ×0,5 przy presji od 8. min: mediana 13,0–13,6 min, bez zmiany), bo blokada zabiera
ułamek HP. Szybszy przyrost złota zmieniłby całe tempo bitwy (epoki, armia, zakupy), a sama
presja działa symetrycznie i dopiero w drugiej połowie bitwy. Sam wcześniejszy start ma
granicę: presja od 3. minuty przy +20%/min daje 8,7–9,0 min, od 2. minuty 7,7–8,4 min,
dlatego start 4 min z szybszym przyrostem (otwarcie bitwy zostaje nietknięte).

Przegląd wariantów (profil docelowy, 100 seedów, etapy 2–7, mediana / wygrane):

| Start presji | Przyrost | HP baz | Mediana [min] | Wygrane 2–6 | Etap 7 |
|---:|---:|---:|---:|---:|---:|
| 480 s (przed) | 20%/min | ×1 | 13,2–13,6 | 86–99% | 69% |
| 480 s | 20%/min | ×0,5 | 13,0–13,6 | 86–97% | 70% |
| 300 s | 20%/min | ×0,6 | 10,5–10,8 | 85–92% | 72% |
| 180 s | 20%/min | ×1 | 8,7–9,0 | 86–91% | 76% |
| 180 s | 60%/min | ×1 | 7,0–7,3 | 76–91% | 74% |
| 150 s | 50%/min | ×1 | 6,8–7,2 | 77–86% | 70% |
| 210 s | 60%/min | ×1 | 7,5–7,7 | 81–89% | 69% |
| **240 s** | **80%/min** | ×1 | **7,6–7,8** | **83–88%** | **67%** |
| 270 s | 100%/min | ×1 | 7,8–8,0 | 82–87% | 69% |

### Pomiar przed/po, 100 seedów na etap, Normalny

`BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target BALANCE_STAGE_FROM=1
BALANCE_STAGE_TO=9` (i `BALANCE_PROFILE=medale`). Zasady trybu (bez `BALANCE_COMMANDER`).
Nowe kolumny w `BalanceReport`: `expected` — oczekiwany czas przejścia etapu przy
powtórkach do skutku (średnia wygrana + (1 − p)/p średnich porażek) i `stars` — średnia
gwiazdek wygranych bitew. „Po” obejmuje korektę etapu 7.

| Etap | Profil docelowy | Wygrane przed → po | Mediana [min] przed → po | Oczekiwany czas [min] przed → po | ★ wygranych przed → po | Medale: wygrane przed → po | Medale: mediana przed → po | Medale: ★ przed → po |
|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | nowy | 86% → 86% | 13,8 → 13,8 | 15,9 → 15,9 | 1,37 → 1,37 | 86% → 86% | 13,8 → 13,8 | 1,37 → 1,37 |
| 2 | nowy | 89% → 88% | 13,6 → **7,8** | 15,2 → 8,8 | 1,48 → 1,22 | 92% → 87% | 13,6 → 7,8 | 1,47 → 1,14 |
| 3 | nowy | 92% → 88% | 13,6 → **7,8** | 14,7 → 8,8 | 1,48 → 1,17 | 94% → 82% | 13,7 → 7,8 | 1,33 → 1,23 |
| 4 | nowy | 86% → 83% | 13,5 → **7,7** | 15,6 → 9,2 | 1,52 → 1,39 | 91% → 86% | 13,3 → 7,7 | 1,63 → 1,33 |
| 5 | nowy | 92% → 86% | 13,3 → **7,6** | 14,3 → 8,8 | 1,71 → 1,34 | 95% → 93% | 12,9 → 7,7 | 1,94 → 1,37 |
| 6 | nowy | 99% → 87% | 13,2 → **7,7** | 12,5 → 8,7 | 1,86 → 1,34 | 99% → 94% | 12,8 → 7,5 | 2,05 → 1,51 |
| 7 | początkujący | 69% → 75% | 13,2 → **7,8** | 18,2 → 10,2 | 1,86 → 1,25 | 76% → 75% | 14,1 → 7,9 | 1,11 → 1,07 |
| 8 | początkujący | 66% → 66% | 13,4 → 13,4 | 19,3 → 19,3 | 1,65 → 1,65 | 67% → 67% | 14,0 → 14,0 | 1,12 → 1,12 |
| 9 | początkujący | 75% → 75% | 11,8 → 11,8 | 14,7 → 14,7 | 2,17 → 2,17 | 76% → 76% | 13,9 → 13,9 | 1,33 → 1,33 |
| **2–7** | | **87,8% → 84,5%** | **13,4 → 7,7** | **Σ 90,5 → 54,4** | **1,65 → 1,29** | **91,2% → 86,2%** | **13,4 → 7,7** | **1,59 → 1,28** |

Etapy 1, 8 i 9 są identyczne (kontrola). 0 patów przed i po. Etap 7 bez korekty 0,95:
67% (docelowy) / 66% (medale); z korektą 0,92 / 0,88: 70% / 80% (docelowy) — przy 100
seedach to szum ±5 pp, wybrano najmniejszą korektę, która wraca do poziomu sprzed zmiany.

### Czas do dowódcy (etap 8)

- **Model z pomiaru** (samouczek + etap 1 = 8,7 min i ok. 1,5 min przerw między bitwami
  z przejścia nocy 5, plus oczekiwany czas etapów 2–7 z porażkami na poziomie AI):
  przed 8,7 + 1,5 + 90,5 ≈ **101 min**, po 8,7 + 1,5 + 54,4 ≈ **65 min**
  (profil „medale”: 98 → 64 min). Bez porażek: 87 → ok. **56 min** (6 × 7,7 min).
- **Przejście „pierwszej godziny”** (`FirstHourJourneyTest`, 4 przejścia, seedy z zegara,
  do 3 podejść): przed (noc 5) **87 min**; po zmianie Wódz w HUD po **56, 58, 65 i 72 min**
  (średnio 63 min). Bitwy 2–7 trwały 7,2–8,0 min; porażki tylko na etapie 7 (0, 0, 1 i 2
  podejścia). Wygrane miały głównie 1–2 ★. Ten sam test przed korektą etapu 7: 55, 65,
  73 i 82 min (6 porażek, z tego 4 na etapie 7).

### Wnioski

- Mediana etapów 2–7: 13,2–13,6 → **7,6–7,8 min** (cel 7–9).
- Wygrane w paśmie celów: etapy 2–6 profilu „nowy” 83–88% (próg ≥ 70%), etap 7
  „początkujący” 75% (próg ≥ 55%). Średnio −3,3 pp na 2–7 (medale −5,0 pp); największy
  spadek na etapie 6 (99% → 87%), który wcześniej był wyraźnie łatwiejszy od sąsiadów.
  Krzywa 1 → 9: 86 → 88 → 88 → 83 → 86 → 87 → 75 → 66 → 75% (wcześniej
  86 → 89 → 92 → 86 → 92 → 99 → 69 → 66 → 75%): etap 7 dalej jest progiem przed etapem 8.
- **Skutek uboczny: mniej gwiazdek.** Gwiazdki liczą HP bazy gracza na końcu, a szybsza
  blokada zabiera je też zwycięzcy: średnio 1,65 → 1,29 ★ na wygraną (−0,36). To ok. −6 medali
  na etap przy pierwszej wygranej (5/★ + 12 za nową ★), czyli ok. −35 medali do etapu 8 —
  stąd korekta etapu 7. Trzy gwiazdki na etapach 2–7 są trudniejsze; do decyzji, czy to
  akceptowalne (alternatywa: łagodniejszy przyrost presji kosztem dłuższych bitew).
- **Do koordynatora (UI, poza zakresem):** `HintOverlay` (dymek SIEGE) i `BattleScreen`
  (`tip_siege`) używają `Battle.SIEGE_START`, a teksty `hint_siege`/`tip_siege` w `L.kt`
  mówią o 8 minutach. W etapach 2–7 pasek presji (`siegePressure()`) rośnie od 4. minuty,
  a dymek/wskazówka pojawią się dopiero po 8 min (o ile bitwa jeszcze trwa). Warto przejść
  na `battle.siegeStart` i tekst bez liczby minut.

- 2026-11-03 — regeneracja złotych powtórek: Dzien 1: krotsze etapy 2-7.

## Dzień 2 — gwiazdki bez przyspieszonej presji (3.11.2026)

**Decyzja właściciela:** po dniu 1 wcześniejsza presja oblężnicza w etapach 2–7 zabierała HP
bazy także zwycięzcy, więc gwiazdek było mniej (1,65 → 1,29 ★ na wygraną). Gwiazdki mają
wrócić do poziomu sprzed dnia 1, bez zmiany przebiegu bitwy.

**Zmiana:** nowy licznik `Side.siegeDamageTaken` zbiera HP bazy utracone przez presję
**szybszą niż standardowa**: przy każdej porcji blokady (`SIEGE_ATTRITION`) i obrażeń bazy
dolicza część `1 − presja_std / presja`, gdzie `presja_std = 1 + (t − siegeStart)/60 × 0,2`.
`Battle.stars()` liczy HP gracza jako `baseHp + 0,6 × siegeDamageTaken`, najwyżej pełne HP
(`Battle.SIEGE_STAR_FORGIVENESS = 0,6`). Progi gwiazdek (75% / 40%) bez zmian. Reguła jest
ta sama we wszystkich etapach i poziomach trudności: przy standardowej presji (etap 1, etapy
od 8., inne tryby) licznik zostaje 0, więc ich gwiazdki są identyczne jak dotąd.
`score()`, zwycięzca i czas bitwy bez zmian (stary wynik wygranych w pomiarze identyczny).
`BattleSnapshot.VERSION` 18 → 19 (nowe pole na końcu strony; zrzuty v16–v18 wczytują się
z licznikiem 0). **`SimVersion.PROTOCOL` bez zmian (0.8.5)**: gwiazdki nie wchodzą do
wyniku weryfikowanego przez serwer (ranking etapów `campaign_N` używa `score()` z HP bazy
i czasu); tablica „Kampania — gwiazdki” (lokalna i Play Games) to suma gwiazdek profilu,
bez powtórki — złotych powtórek nie regenerowano.

**Dlaczego 0,6.** Warianty zmierzone na tych samych 100 seedach (profil docelowy, etapy 2–7,
średnia ★ wygranych): pominięcie całej presji (blokada + premia) — **3,00** (każda wygrana
3 ★, bo bitwy rozstrzyga blokada); pominięcie nadwyżki ponad presję ×1 — 2,20 i zmiana
także etapów 1, 8, 9 (1,37 → 2,05 na etapie 1); nadwyżka ponad standardową presję ×1,0 —
2,07 (prawie zawsze 2 ★); ×0,5 — 1,56; **×0,6 — 1,66**; ×0,7 — 1,79.

### Pomiar przed/po, 100 seedów na etap, Normalny

`BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target|medale BALANCE_STAGE_FROM=1
BALANCE_STAGE_TO=9`. Wygrane, mediany i oczekiwane czasy identyczne jak po dniu 1 (bitwy się
nie zmieniły). „Przed dniem 1” z tabeli dnia 1.

| Etap | ★ docelowy: przed dniem 1 → dzień 1 → dzień 2 | ★ „medale”: przed dniem 1 → dzień 1 → dzień 2 |
|---:|---:|---:|
| 1 | 1,37 → 1,37 → 1,37 | 1,37 → 1,37 → 1,37 |
| 2 | 1,48 → 1,22 → **1,57** | 1,47 → 1,14 → **1,53** |
| 3 | 1,48 → 1,17 → **1,60** | 1,33 → 1,23 → **1,63** |
| 4 | 1,52 → 1,39 → **1,67** | 1,63 → 1,33 → **1,77** |
| 5 | 1,71 → 1,34 → **1,81** | 1,94 → 1,37 → **1,71** |
| 6 | 1,86 → 1,34 → **1,77** | 2,05 → 1,51 → **1,88** |
| 7 | 1,86 → 1,25 → **1,55** | 1,11 → 1,07 → **1,39** |
| 8 | 1,65 → 1,65 → 1,65 | 1,12 → 1,12 → 1,12 |
| 9 | 2,17 → 2,17 → 2,17 | 1,33 → 1,33 → 1,33 |
| **2–7** | **1,65 → 1,29 → 1,66** | **1,59 → 1,28 → 1,65** |
| **2–9** | **1,72 → 1,44 → 1,72** | **1,50 → 1,26 → 1,55** |

**Medale.** Nagroda za wygraną etapu to `20 + 6·etap + 5·★ + 12·nowe ★`, więc pierwsza
wygrana daje 17 medali za każdą gwiazdkę. Średnio +2,26 ★ łącznie na etapach 2–7 (docelowy;
„medale” +2,27) = **ok. +38 medali do etapu 8** przy pierwszych wygranych (dzień 1 zabrał
ok. 35), przy powtórkach +5/★. Wygrane, czasy i przebieg kampanii bez zmian.


- 2026-11-03 — regeneracja złotych powtórek: Dzien 2: gwiazdki bez presji.
- 2026-11-03 — zawartość 176 (dowódca i stopnie wież): sześć osiągnięć (40, 140, 170, 200, 180,
  160 medali; łącznie 890 jednorazowo, w skali istniejących 15–900) i cztery typy misji
  (COMMANDER_SUMMONS, COMMANDER_WINS, TURRET_TIERS, TOP_TIER_WINS, waga 2, nagrody wg wzoru
  misji). Liczone po bitwie z obserwacji `AchievementBattleTracker` i logu komend gracza; bez
  zmian w `sim/`, PROTOCOL i złotych powtórkach. Misje dowódcy losują się dopiero od etapu 8,
  misje stopni wież od etapu 3, więc nowy gracz ma tę samą rotację co wcześniej.
- 2026-11-03 — R6-01 (osiągnięcia pierwszej godziny): `first_evolve` 10, `first_turret` 10,
  `first_research` 10, `stages_3` 20 medali (razem 50 jednorazowo); przy 196–356 medalach
  zdobytych w pierwszej godzinie (noc 5) to ok. +10–15% medali na start, bez strojenia nagród.


## D6 — Dzień 6: karty 184, zestawy tygodnia, wydarzenia dnia (3.11.2026)

Nowe karty Endless (Saperzy, Mur tarcz, Skarbiec wojenny, Zakład), trzy zestawy Oblężenia
tygodnia (Nocna straż, Żołd i łupy, Kruchy bastion) i dwa symetryczne wydarzenia dnia
(Dzień ciężkiej piechoty, Pojedynek wież) od `Daily.EVENTS_V3_DAY` = premiera + 15.
`SimVersion.PROTOCOL` 0.8.5 → **0.8.6**, `BattleSnapshot.VERSION` 19 → **20**,
`Replay.VERSION` bez zmian (`RANKED_REPLAY_FORMATS` = `{7}`).

Pomiar: „przed” = `origin/integ/tura10` (caba585) w osobnym worktree, „po” = ten PR
(commit 0a50d55 + strojenie poniżej). Endless: weteran 80% badań, `EndlessBalanceReportTest`.

### Endless, 100 seedów (cel: fala 60 = 5–15/100, mediana porażek 45–55, fala 20 ≥ 95)

| Metryka | 1001–1100 przed | 1001–1100 po | 2001–2100 przed | 2001–2100 po |
|---|---:|---:|---:|---:|
| Fala 60 (żywa baza) | 3/100 | **9/100** | 10/100 | **6/100** |
| Mediana fali porażki | 54 | **53** | 54 | **53** |
| Fala 20 | 100/100 | 100/100 | 100/100 | 100/100 |
| Fala 40 / 50 | 82 / 80 | 85 / 79 | 88 / 87 | 85 / 77 |
| Średnia fala | 50,68 | 51,22 | 52,12 | 50,96 |
| Średni wynik | 20 704 | 21 064 | 21 465 | 20 952 |
| Rozkład (<20, 20–29, 30–39, 40–44, 45–49, 50–54, 55–59, 60) | 0,0,18,2,0,57,20,3 | 0,1,13,4,3,51,19,9 | 0,1,11,1,0,64,13,10 | 0,1,13,4,3,56,17,6 |

Przed zmianą obie próby różniły się o 7 punktów na fali 60 (3 i 10), więc to szum próby;
po zmianie obie są w paśmie 5–15. Krzywej fal nie ruszano.

### Zakład (WAGER) — wpływ na wynik

`wagerScoreEffect184`: ten sam seed z Zakładem dokładanym przy pierwszej ofercie (fala 5)
i bez niego. AI sama bierze Zakład rzadko (waga −30 poza profilem szturmowym): 3/100 i 5/100
przebiegów.

| Premia za falę | Próba | Śr. wynik bez → z Zakładem | Zmiana | Śr. fala bez → z | Fala 60 bez → z |
|---|---|---:|---:|---:|---:|
| ×1,25 (312 pkt) | 1001–1100 | 21 105 → 23 423 | **+11,0%** | 51,22 → 49,84 | 9 → 7 |
| ×1,15 (287 pkt) | 1001–1100 | 21 064 → 22 177 | +5,3% | 51,22 → 49,84 | 9 → 7 |
| ×1,15 (287 pkt) | 2001–2100 | 20 952 → 21 874 | +4,4% | 50,96 → 49,24 | 6 → 4 |

**Decyzja:** ×1,25 przekraczało próg +10%, więc zgodnie ze zleceniem premia to **×1,15**
(`EndlessCards.WAGER_WAVE_SCORE` = 287). Kara (HP fal ×1,1) kosztuje ok. 1,5 fali i 2 przebiegi
do fali 60 na 100, więc Zakład to realne ryzyko za ok. +5% wyniku.

### Oblężenie tygodnia: zestawy na tych samych 8 tygodniach

`weeklyRuleSets184`: każdy zestaw (para modyfikatorów + karta) na tych samych 8 seedach
i epokach (losowe tygodnie od `firstRuleSetWeek() + 1`), bez badań (zasady tygodnia).

| Zestaw | Mediana fali przed | po |
|---|---:|---:|
| day109_gold | 12,0 | 12,5 |
| day109_march | 11,0 | 10,0 |
| day109_rain | 10,5 | 11,0 |
| day109_veterans | 12,0 | 10,5 |
| day109_hired | 12,0 | 11,0 |
| day109_iron | 9,5 | 8,5 |
| **week184_night** (Mgła + Opancerzony wróg, Czujność) | — | **12,0** |
| **week184_mercs** (Najemnicy + Podwójny dochód) z Grabieżą | — | 41,5 (4/8 do fali 60) |
| **week184_mercs** z Marszem bojowym | — | **12,5** |
| **week184_bastion** (Ciężkie wieże + Kruche bazy, Pancerz piechoty) | — | **10,0** |

Pasmo istniejących zestawów: 8,5–12,5. „Żołd i łupy” z Grabieżą wychodził daleko poza pasmo
(złoto za zabójstwa + podwójny dochód + najemnicy). Próba kart dla tej pary: Grabież 41,5,
Krwawe złoto 60, Zbieracz 14, Musztra 13, Marsz bojowy 12,5 (dla porównania Najemnicy + Wolne
XP z Grabieżą 10,5). **Decyzja:** karta tygodnia → **Marsz bojowy** (modyfikatory bez zmian).
Różnice zestawów 109 między „przed” i „po” to wpływ nowych kart na oferty (te same seedy).

### Wydarzenia dnia: lustrzane bitwy AI vs AI

`BALANCE_RUN=true BALANCE_DAILY_EVENT=<nazwa> ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.mirrorReport*'`
(20 seedów × 3 epoki × 2 przebiegi z zamienionymi osobowościami stron = 120 bitew).

| Wydarzenie | Wygrane strony 0 | Średni czas [min] | Zmiana czasu | Pat |
|---|---:|---:|---:|---:|
| brak | 56/120 (46,7%) | 13,11 | — | 0 |
| HEAVY_DAY | 63/120 (52,5%) | 12,39 | −5,5% | 0 |
| TOWER_DUEL | 59/120 (49,2%) | 13,14 | +0,2% | 0 |

Obie w paśmie 45–55% i bez wydłużenia bitew o > 20%.

### Regresja

Kampania, potyczka i dni sprzed `EVENTS_V3_DAY`: złote powtórki mają ten sam wynik, zwycięzcę,
liczbę kroków i zabójstwa; zmienia się tylko skrót zrzutu (`snapshotSha256`), bo zrzut v20
ma 4 dodatkowe stosy kart. Dni od premiery do `EVENTS_V3_DAY` mają te same wydarzenia
(test `Content184Test.daysBeforeEventsV3KeepTheirEvents` z listą z HEAD sprzed zmiany).
Karty nie występują w kampanii, więc jej balans się nie zmienia.
Po regeneracji: we wszystkich 10 złotych powtórkach (także Endless i tydzień) wynik, zwycięzca,
fala, kroki, zabójstwa i czas są identyczne; zmienił się wyłącznie `snapshotSha256` (format v20).
Powtórki Endless/tygodnia kończą się przed pierwszą ofertą kart z nowymi pozycjami.

- 2026-11-03 — regeneracja złotych powtórek: D6 184: nowe karty Endless, zestawy tygodnia, wydarzenia dnia, zrzut v20.

## R11-02 — druga godzina: krótsze etapy 8–12 (4.11.2026)

**Decyzja właściciela:** etapy 8–12 (pierwsze z dowódcą) mają trwać ok. 9–10 min mediany
zamiast 12–14. Po dniu 1 etapy 2–7 trwały 7,7 min, a etap 8 wracał do 13,4 min.

**Zmiana (wzorzec dnia 1, łagodniej):** w etapach 8–12 (indeksy 7–11, każdy poziom
trudności, bez samouczka) presja oblężnicza startuje po **5 minutach** (300 s) zamiast 8
i rośnie o **50%/min** zamiast 20%/min (`Campaign.SECOND_SIEGE_*`, `Campaign.siegeStart` /
`siegeRamp`). Etapy 2–7, 1 i 13+ oraz inne tryby bez zmian. Stopniowanie 8–10 / 11–12 nie
było potrzebne: etap 13 (stare zasady) ma u profilu docelowego medianę 9,3 min, czyli tyle
co nowe 8–12.

**Gwiazdki bez kary za presję:** reguła dnia 2 (`siegeDamageTaken`) liczy tylko nadwyżkę
ponad +20%/min od wcześniejszego startu, a współczynnik 0,6 zostawiał w etapach 8–12 spadek
★ o 6–20% (profil docelowy: 8: 1,65 → 1,46, 10: 2,01 → 1,60, 12: 2,09 → 1,71). Dlatego
w 8–12 zwrot wynosi **1,0** (`Campaign.siegeStarForgiveness`, `Battle.siegeStarForgiveness`);
w etapach 2–7 dalej 0,6, poza nimi licznik jest 0. Gwiazdki nie wchodzą do `score()`.

**Korekta:** etap 12 `stageRamp` 0,828 → **0,811** (−2%): bez niej 70% → 63% (poza pasmem
±5 pp). Przegląd `BALANCE_POWER_SCALE` dla etapu 12: 0,98 → 71%, 0,96 → 70%, 0,94 → 74%;
wybrano najmniejszą.

`SimVersion.PROTOCOL` 0.8.6 → **0.8.7** (`RANKED_REPLAY_FORMATS` bez zmian, `Replay.VERSION`
7 bez zmian). `BattleSnapshot.defsFingerprint` nie obejmuje zasad presji kampanii, więc
przerwana bitwa etapu 8–12 wznawia się po aktualizacji na nowych zasadach (test
`SecondHourControlTest`), bez ścieżki R10-01.

### Pomiar przed/po, 100 seedów na etap, Normalny

`BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target|medale BALANCE_STAGE_FROM=7
BALANCE_STAGE_TO=14`, zasady trybu (bez `BALANCE_COMMANDER`). „Po” z korektą etapu 12.

| Etap | Wygrane przed → po | Mediana [min] przed → po | Oczekiwany [min] przed → po | ★ przed → po | Medale: wygrane | Medale: mediana | Medale: ★ |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 7 | 75% → 75% | 7,8 → 7,8 | 10,2 → 10,2 | 1,55 → 1,55 | 75% → 75% | 7,9 → 7,9 | 1,39 → 1,39 |
| 8 | 66% → 69% | 13,4 → **9,5** | 19,3 → 13,5 | 1,65 → 1,77 | 67% → 72% | 14,0 → 9,6 | 1,12 → 1,50 |
| 9 | 75% → 70% | 11,8 → **9,0** | 14,7 → 11,8 | 2,17 → 2,29 | 76% → 73% | 13,9 → 9,5 | 1,33 → 1,70 |
| 10 | 67% → 72% | 12,9 → **9,4** | 18,2 → 12,4 | 2,01 → 1,88 | 75% → 71% | 13,9 → 9,5 | 1,19 → 1,79 |
| 11 | 61% → 65% | 12,1 → **9,0** | 19,4 → 13,5 | 2,02 → 2,03 | 76% → 75% | 13,6 → 9,4 | 1,49 → 1,83 |
| 12 | 70% → 67% | 11,8 → **9,1** | 16,4 → 13,1 | 2,09 → 1,93 | 84% → 87% | 13,9 → 9,4 | 1,27 → 1,76 |
| 13 | 67% → 67% | 9,3 → 9,3 | 13,3 → 13,3 | 2,51 → 2,51 | 76% → 76% | 13,9 → 13,9 | 1,29 → 1,29 |
| 14 | 57% → 57% | 12,2 → 12,2 | 20,5 → 20,5 | 2,04 → 2,04 | 75% → 75% | 13,9 → 13,9 | 1,27 → 1,27 |
| **8–12** | **67,8% → 68,6%** | **11,8–13,4 → 9,0–9,5** | **Σ 87,9 → 64,3** | **1,99 → 1,98** | **75,6% → 75,6%** | **13,6–14,0 → 9,4–9,6** | **1,28 → 1,72** |

Etapy 7, 13 i 14 identyczne (kontrola). 0 patów. Najkrótsza mediana 9,0 min (próg ≥ 4).
Najgorszy spadek ★ profilu docelowego: etap 12 −7,7%, etap 10 −6,5% (próg 10%).
Profil „medale” ma ★ wyraźnie wyżej (gwiazdki nie są trudniejsze; łatwiejsze o ok. +0,44 ★).

**Z dowódcą (`BALANCE_COMMANDER=on`, profil docelowy):** wygrane 8–12 przed 67/73/68/52/71%,
po 69/69/61/57/72%; mediany 13,7/12,1/13,1/12,2/11,8 → 9,5/9,1/9,3/9,1/9,3 min; ★ 1,51/2,10/
1,81/1,96/2,11 → 1,72/2,13/2,00/2,04/1,92. Różnica „z dowódcą / zasady trybu” zostaje
w granicach szumu 100 seedów (przed −9…+1 pp, po −11…+5 pp; etap 10 po −11 pp, etap 11
przed −9 pp), dowódca obu stron występuje w 96–100% bitew.

### Druga godzina (model)

Suma oczekiwanych czasów 8–12 plus 1,5 min przerwy po każdej bitwie: profil docelowy
87,9 + 7,5 = **95 min → 64,3 + 7,5 = 72 min**; „medale” 91,4 + 7,5 = 99 min → 62,8 + 7,5 =
70 min. W 60 min po dowódcy mieści się ok. 3,2 etapu przed i ok. 4,2 etapu po (8–11
w 57 min). Medale za wygrane etapów 8–12 przychodzą ok. 1,35× szybciej (te same nagrody
w krótszym czasie, plus więcej ★), więc badania przed etapem 19 rosną szybciej; do
obserwacji przy pomiarze etapów 19+.

- 2026-11-04 — regeneracja złotych powtórek: R11-02: krotsza presja etapow 8-12.

## Etapy 13–18 — krótsze bitwy po R11-02 (4.11.2026)

**Decyzja właściciela (4.11):** po R11-02 etapy 8–12 trwają ok. 9–9,5 min, ale etapy 13–18
wracają do starej presji (profil „medale” ok. 13,4–14 min). Cel: mediana 13–18 ok. 9,5–11 min
dla profilu docelowego i „medale”, wygrane ±5 pp, ★ bez spadku > 10% na etap.

### Pomiar „przed” (PROTOCOL 0.8.7), 100 seedów, Normalny

`BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target|medale BALANCE_STAGE_FROM=7
BALANCE_STAGE_TO=20 BALANCE_STAGES=7,12,13,14,15,16,17,18,19,20`.

| Etap | Docelowy: wygrane / mediana / oczek. / ★ | Medale: wygrane / mediana / oczek. / ★ | Docelowy z dowódcą: wygrane / mediana / ★ |
|---:|---:|---:|---:|
| 7 | 75% / 7,8 / 10,2 / 1,55 | 75% / 7,9 / 10,4 / 1,39 | — |
| 12 | 67% / 9,1 / 13,1 / 1,93 | 87% / 9,4 / 10,8 / 1,76 | 72% / 9,3 / 1,92 |
| 13 | 67% / 9,3 / 13,3 / 2,51 | 76% / 13,9 / 17,9 / 1,29 | 60% / 9,6 / 2,47 |
| 14 | 57% / 12,2 / 20,5 / 2,04 | 75% / 13,9 / 18,4 / 1,27 | 56% / 13,0 / 1,80 |
| 15 | 49% / 12,2 / 22,8 / 1,96 | 83% / 14,0 / 16,7 / 1,14 | 58% / 13,3 / 1,79 |
| 16 | 65% / 13,7 / 20,4 / 1,32 | 83% / 13,8 / 16,6 / 1,16 | 62% / 13,8 / 1,35 |
| 17 | 65% / 12,6 / 18,5 / 1,89 | 87% / 13,6 / 15,6 / 1,41 | 64% / 12,8 / 1,77 |
| 18 | 63% / 11,8 / 17,8 / 2,16 | 84% / 13,4 / 15,9 / 1,70 | 65% / 12,4 / 2,05 |
| 19 | 60% / 10,1 / 15,9 / 2,53 | 49% / 13,5 / 26,6 / 1,51 | — |
| 20 | 77% / 13,2 / 16,7 / 1,65 | 72% / 13,4 / 18,0 / 1,60 | — |

### Warianty (100 seedów, Normalny; mediana [min] / wygrane / ★)

Wariant A: etapy 13–18 presja od 360 s, +40%/min, zwrot gwiazdek 1,0, bez korekt `stageRamp`.

| Etap | Docelowy A | Medale A | Docelowy z dowódcą A |
|---:|---:|---:|---:|
| 13 | 8,2 / 67% / 2,51 | 10,8 / 78% / 1,62 | 8,4 / 64% / 2,27 |
| 14 | 10,3 / 57% / 1,89 | 10,8 / 73% / 1,48 | 10,6 / 56% / 1,71 |
| 15 | 10,4 / 53% / 1,85 | 10,8 / 75% / 1,44 | 10,7 / 55% / 1,85 |
| 16 | 10,8 / 63% / 1,56 | 10,8 / 95% / 1,45 | 10,9 / 59% / 1,49 |
| 17 | 10,2 / 63% / 1,92 | 10,6 / 83% / 1,78 | 10,5 / 65% / 1,86 |
| 18 | 10,0 / 60% / 2,17 | 10,4 / 81% / 1,93 | 10,2 / 62% / 2,08 |

Wariant B: od 390 s, +35%/min (bez dowódcy).

| Etap | Docelowy B | Medale B |
|---:|---:|---:|
| 13 | 8,5 / 67% / 2,49 | 11,5 / 78% / 1,49 |
| 14 | 10,8 / 59% / 1,86 | 11,5 / 74% / 1,39 |
| 15 | 10,9 / 51% / 2,00 | 11,6 / 80% / 1,33 |
| 16 | 11,5 / 61% / 1,49 | 11,5 / 94% / 1,35 |
| 17 | 10,7 / 64% / 1,95 | 11,3 / 85% / 1,68 |
| 18 | 10,5 / 59% / 2,17 | 11,2 / 81% / 1,83 |

B daje medianę „medali” 11,2–11,6 min (powyżej 11) i nie usuwa skoku wygranych „medali”
na etapie 16 (83% → 94–95% w obu wariantach), więc dalej wariant A.

Przegląd `BALANCE_POWER_SCALE` przy wariancie A (wygrane: docelowy / docelowy z dowódcą /
medale; „przed” = stan PROTOCOL 0.8.7):

| Etap | Przed | A bez korekty | ×0,98 | ×0,96 | ×1,02 | ×1,04 |
|---:|---:|---:|---:|---:|---:|---:|
| 15 | 49 / 58 / 83% | 53 / 55 / 75% | 56 / 63 / 73% | **54 / 60 / 80%** | — | — |
| 16 | 65 / 62 / 83% | 63 / 59 / 95% | — | — | **60 / 65 / 85%** | 66 / 56 / 81% |

Wybrane: etap 15 ×0,96 (`stageRamp` 1 → 0,96), etap 16 ×1,02 (0,86 → 0,877, nadal lżej niż
przed turą 10: 0,93).

Kontrola z korektą w kodzie (`stageRamp` 15 → 0,96, 16 → 0,877): etap 15 jak przy ×0,96
(54 / 60 / 80%), etap 16 inaczej niż przy ×1,02 (65 / 59 / **92%**; zaokrąglenie aiPower
zmienia przebieg seedów). Dalszy przegląd samej korekty etapu 16 niżej.

Przegląd korekty etapu 16 w kodzie (docelowy / z dowódcą / medale): 0,87 → 65 / 63 / 85%,
0,877 → 65 / 59 / 92%, 0,88 → 56 / 60 / 87%, 0,885 → 61 / 59 / 89%, 0,89 → 61 / 54 / 86%.
Odpowiedź jest w granicach szumu 100 seedów; wybrano 0,87 (najmniejsza zmiana, wszystkie
trzy w paśmie ±5 pp).

### Zmiana

- Etapy 13–18 (indeksy 12–17, każdy poziom trudności, bez samouczka): presja oblężnicza od
  **360 s** (6 min) zamiast 480 s, przyrost **+40%/min** zamiast +20%/min
  (`Campaign.THIRD_SIEGE_*`). Łagodniej niż 8–12 (300 s, +50%/min), więc bez skoku po 12.
- Zwrot gwiazdek z przyspieszonej presji 1,0 jak w 8–12 (`Campaign.siegeStarForgiveness`).
- `stageRamp`: etap 15 1 → **0,96**, etap 16 0,86 → **0,87**. Etapy 17 i 18 bez zmian
  (0,92 / 0,85, strojenie tury 10).
- `SimVersion.PROTOCOL` 0.8.7 → **0.8.8** (`RANKED_REPLAY_FORMATS` i `Replay.VERSION` bez
  zmian). Odcisk `BattleSnapshot.defsFingerprint` nie obejmuje presji kampanii, więc przerwana
  bitwa 13–18 wznawia się na nowych zasadach (jak w R11-02). Kontrola determinizmu przeniesiona
  z etapu 13 na etap 19 (`SecondHourControlTest`, wynik zapisany przy 0.8.7).

### Pomiar przed/po, 100 seedów na etap, Normalny

Mediana [min] / wygrane / ★ wygranych. „Po” = stan w kodzie (etap 16: przegląd 0,87).

| Etap | Docelowy przed → po | Medale przed → po | Docelowy z dowódcą przed → po |
|---:|---:|---:|---:|
| 7 | 7,8 / 75% / 1,55 → identycznie | 7,9 / 75% / 1,39 → identycznie | — |
| 12 | 9,1 / 67% / 1,93 → identycznie | 9,4 / 87% / 1,76 → identycznie | 9,3 / 72% / 1,92 → identycznie |
| 13 | 9,3 / 67% / 2,51 → **8,2** / 67% / 2,51 | 13,9 / 76% / 1,29 → **10,8** / 78% / 1,62 | 9,6 / 60% / 2,47 → 8,4 / 64% / 2,27 |
| 14 | 12,2 / 57% / 2,04 → **10,3** / 57% / 1,89 | 13,9 / 75% / 1,27 → **10,8** / 73% / 1,48 | 13,0 / 56% / 1,80 → 10,6 / 56% / 1,71 |
| 15 | 12,2 / 49% / 1,96 → **10,6** / 54% / 1,98 | 14,0 / 83% / 1,14 → **10,8** / 80% / 1,51 | 13,3 / 58% / 1,79 → 10,5 / 60% / 1,95 |
| 16 | 13,7 / 65% / 1,32 → **10,9** / 65% / 1,49 | 13,8 / 83% / 1,16 → **10,8** / 85% / 1,47 | 13,8 / 62% / 1,35 → 10,9 / 63% / 1,43 |
| 17 | 12,6 / 65% / 1,89 → **10,2** / 63% / 1,92 | 13,6 / 87% / 1,41 → **10,6** / 83% / 1,78 | 12,8 / 64% / 1,77 → 10,5 / 65% / 1,86 |
| 18 | 11,8 / 63% / 2,16 → **10,0** / 60% / 2,17 | 13,4 / 84% / 1,70 → **10,4** / 81% / 1,93 | 12,4 / 65% / 2,05 → 10,2 / 62% / 2,08 |
| 19 | 10,1 / 60% / 2,53 → identycznie | 13,5 / 49% / 1,51 → identycznie | — |
| 20 | 13,2 / 77% / 1,65 → identycznie | 13,4 / 72% / 1,60 → identycznie | — |
| **13–18** | **9,3–13,7 → 8,2–10,9**, Σ oczek. 113,4 → 93,4 min | **13,4–14,0 → 10,4–10,8**, Σ 101,2 → 80,2 min | Σ oczek. 116,1 → 94,2 min |

Etapy 7, 12, 19 i 20 identyczne (kontrola). 0 patów. Wygrane: docelowy −3…+5 pp, medale
−4…+2 pp, z dowódcą −3…+4 pp. ★ docelowego: najgorzej etap 14 −7,4% (próg 10%), z dowódcą
etap 13 −8,1%, etap 14 −5,0%; „medale” +13…+33%. Strojenie tury 10 (16–18 z dowódcą
55–70%) zachowane: 63 / 65 / 62%.

Etap 13 profilu docelowego (8,2 min) jest krótszy niż cel 9,5 min: już przed zmianą był
najkrótszy z 13–18 (9,3 min, stare zasady), a każda wcześniejsza presja go skraca.
Mieści się między etapem 7 (7,8) a 12 (9,1), więc nie robi skoku; pozostałe 13–18 mają
10,0–10,9 min, a „medale” 10,4–10,8 min. Wariant B (390 s, +35%/min) dawał etap 13
8,5 min, ale „medale” 11,2–11,6 min.

Przebieg kampanii po dowódcy (profil docelowy, suma oczekiwanych czasów + 1,5 min przerwy):
etapy 8–18 po R11-02 64,3 + 113,4 + 16,5 = 194 min → **174 min** (−20 min); „medale”
62,8 + 101,2 + 16,5 = 181 min → **160 min**.

Złote powtórki po regeneracji bez zmian (kampania: etapy 1 i 29, żadna nie dotyczy 13–18;
kontrola determinizmu).

- 2026-11-04 — regeneracja złotych powtórek: Etapy 13-18.

## Etap 19 — łagodniejszy klif 18 → 19 (dzień 35, 4.11.2026)

**Decyzja właściciela:** lekko złagodzić etap 19 mnożnikiem 0,94 (pomiar wstępny i kryterium
„ściany”: `docs/reports/d33-pomiar-19.md`; raport zmiany: `docs/reports/d35-etap-19.md`).

### Zmiana

- `Campaign.stageRamp`: etap 19 (`i + 1 == 19`, indeks 18) **1,265 → 1,1891** (= 1,265 × 0,94).
  `aiPower` etapu 19: 1,611728 → **1,5150243**, bitowo to samo co `BALANCE_POWER_SCALE=0.94`
  z `BALANCE_POWER_SCALE_STAGES=19` w pomiarze d33 (M3), więc wyniki M3 0,94 powtarzają się
  co do bitwy. (1,189 dałoby 1,5148969 — inna wartość Float, inne bitwy.)
- `SimVersion.PROTOCOL` 0.8.8 → **0.8.9** (`RANKED_REPLAY_FORMATS` i `Replay.VERSION` bez zmian).
- Kontrola determinizmu w `SecondHourControlTest` przeniesiona z etapu 19 na **etap 21**
  (seed 91021, wynik zapisany przy 0.8.8; po zmianie identyczny).
- Złote powtórki po regeneracji bez zmian (kampania: etapy 1 i 29, żadna nie dotyczy etapu 19).

### Pomiar przed/po, 100 seedów na etap, Normalny, etapy 16–24

Stan z kodu (nie mnożnikiem), `:sim` i `BalanceReport` skompilowane poza Gradle (jak w d33).
Wygrane / mediana [min] / oczekiwany czas [min] / ★ wygranych; w nawiasie poziomy badań.
Etapy 16–18 i 20–24 identyczne co do bitwy przed i po we wszystkich profilach (kontrola);
w `medale-czas` etap 20+ liczy czas etapu 19 z przebiegu, ale poziomy badań się nie zmieniły.

| Profil | Etap 18 | Etap 19 przed (0.8.8) | Etap 19 po (0.8.9) | Etap 20 |
|---|---:|---:|---:|---:|
| medale | 81% / 10,4 / 12,9 / 1,93 (31) | 49% / 13,5 / 26,6 / 1,51 (33) | **65%** / 13,4 / **19,7** / 1,68 | 72% / 13,4 / 18,0 / 1,60 (35) |
| medale-czas, teraz | 90% / 7,8 / 8,1 / 2,68 (37) | 71% / 9,8 / 13,3 / 2,54 (39) | **76%** / 10,1 / **12,1** / 2,37 | 79% / 13,2 / 16,1 / 1,66 (40) |
| docelowy | 60% / 9,9 / 15,5 / 2,17 | 60% / 10,1 / 15,9 / 2,53 | **72%** / 9,1 / **12,0** / 2,67 | 77% / 13,2 / 16,7 / 1,65 |
| docelowy z dowódcą | 62% / 10,2 / 15,3 / 2,08 | 61% / 10,1 / 15,4 / 2,51 | **73%** / 9,6 / **12,2** / 2,63 | 74% / 13,3 / 16,9 / 1,89 |

| Profil | Spadek wygranych 18 → 19 przed → po | Iloraz oczek. 19/18 przed → po | Ocena (kryterium d33) przed → po |
|---|---:|---:|---|
| medale | −32 → **−16 pp** | 2,06× → **1,53×** | ściana → ostrzegawcza (spadek > 15 pp) |
| medale-czas, teraz | −19 → **−14 pp** | 1,63× → **1,49×** | ostrzegawcza → **płynnie** |
| docelowy | 0 → +12 pp | 1,03× → 0,78× | płynnie |
| docelowy z dowódcą | −1 → +11 pp | 1,01× → 0,80× | płynnie |

Etapy 21–24 (bez zmian): medale 70 / 64 / 69 / 74%, medale-czas 88 / 68 / 67 / 59%,
docelowy 72 / 64 / 52 / 63%, z dowódcą 68 / 68 / 53 / 57%.

Cele z d33: „medale” na 19 ≥ 60% — **65%** ✓; iloraz `medale-czas` ≤ 1,6× — **1,49×** ✓;
docelowy na 19 ≤ 75% — **72%** ✓, z dowódcą **73%** ✓. Uwaga: profil docelowy jest teraz na
etapie 19 **łatwiejszy niż na 18** (72% vs 60%, oczek. 12,0 vs 15,5 min). To skutek przeskoku
profilu z 10% na 40% badań na etapie 19, a nie realnego gracza (profile medalowe mają +2 poziomy
na etap); warunek R12 „docelowy nie łatwiejszy niż na 18” jest więc formalnie niespełniony,
w granicy 75%. Przebieg „medale-czas”: suma oczekiwanych czasów 16–24 −1,2 min, „medale” −6,9 min.

- 2026-11-04 — regeneracja złotych powtórek: Etap 19: stageRamp 1,265 -> 1,1891 (x0,94), PROTOCOL 0.8.9; powtórki bez zmian.

## D37 — przegląd etapów 19–36, Endless i tygodnia po 0.8.9 (pomiar, bez zmian w grze; 4.11.2026)

Pełny raport, tabele i warianty: `docs/reports/d37-balans-19-36.md`. N=100, Normalny, poza
Gradle (jak d33/d35); etapy 16–24 powtarzają d35 co do bitwy, 0 patów. Wygrane / oczekiwany czas
przejścia [min]; „realny gracz” = `medale-czas`, `BALANCE_PACE=now` (54 poziomy na etapie 28,
66 na 36), docelowy 28–36 = weteran 80%.

| Etapy | Realny gracz | „medale” | Docelowy | Docelowy + dowódca |
|---|---|---|---|---|
| 19–27 (śr. / Σ oczek.) | 72,2% / 129 min | 70,6% / 137 min | 65,6% / 143 min | 64,7% / 146 min |
| 28–36 (śr. / Σ oczek.) | 48,1% / 179 min | 43,4% / 207 min | 66,8% / 111 min | 67,6% / 110 min |
| 27 → 28 | 80 → 58% (12,8 → 21,6) | 69 → 40% (15,8 → 31,9) | 66 → 70% | 63 → 68% |
| 30 / 31 / 32 / 33 | 43 / 41 / 40 / 45% | 46 / 33 / 38 / 37% | 69 / 53 / 65 / 59% | 66 / 71 / 73 / 60% |
| 34 / 35 / 36 | 52 / 50 / 53% | 55 / 50 / 47% | 70 / 67 / 76% | 62 / 66 / 62% |

- **Klify (kryterium d33):** etap **28** — realny gracz strefa ostrzegawcza (−22 pp, 1,70×),
  „medale” ściana (−29 pp, 2,0×); przyczyna: aiPower 1,63 → 2,14 (+31%) na początku trzeciej
  części. Etapy **30–33** — realny gracz < 50% przez 4 etapy, „medale” 31–33 < 40%; etap 33
  (aiPower 2,90) trudniejszy niż 34–36 po korekcie tury 10. **Etap 19 z aktywnym dowódcą**
  (`BALANCE_COMMANDER=on`): realny gracz 88 → 57% (−31 pp), „medale” 86 → 53% — bez dowódcy płynnie.
- **Za łatwe / za krótkie:** etap 29 dla docelowego mediana 3,7 min (z dowódcą 2,0; znane od tury 9),
  realny gracz 8,1 min; docelowy 34–36 67–76% (powyżej pasma 25–55%, skutek tury 10 i profilu 80%).
- **Propozycja K1 (mnożnikiem, nie wpisana):** 28 ×0,90, 30 ×0,92, 31 ×0,88, 32 ×0,92, 33 ×0,92 →
  realny gracz 28 / 30 / 31 / 32 / 33: 65 / 54 / 52 / 50 / 52%, Σ 28–36 179 → 160 min; „medale”
  28–36 ≥ 45%; docelowy najwyżej 78%. Etap 19 dodatkowo ×0,96: z dowódcą 57 → 70%, ale docelowy 79%
  — wstrzymane do danych z gry.
- **Endless (weteran, do fali 60):** 1001–1100 fala 60 **9/100**, mediana 53; 2001–2100 **6/100**,
  mediana 53; fala 20 100/100 — identycznie jak po D6, w celu (5–15, 45–55).
- **Oblężenie tygodnia** (tygodnie 3022–3073, AI na zasadach tygodnia): mediana fali 12, progi
  10 / 20 / 30 fal w 31 / 5 / 2 z 52 tygodni; fala 60 tylko w 2 tygodniach z Podwójnym dochodem.

## Etapy 28 i 30–33 — wariant K1 z D37 (dzień 40, 4.11.2026) — ZATWIERDZONE PRZEZ WŁAŚCICIELA 5.11

**Status: zatwierdzone przez właściciela 5.11.2026, scalone na gałęzi `claude/dzien-47-k1`** (z `integ/tura10`,
PROTOCOL 0.8.10). Wpisuje rekomendację K1 z D37 (`docs/reports/d37-balans-19-36.md`, pytanie 1) do gry.
Raport: `docs/reports/d40-k1.md`.

### Zmiana

- `Campaign.stageRamp`, korekty: 28 **1 → 0,9**; 30 **0,989 → 0,90988004**; 31 **0,97 → 0,8536**;
  32 **0,97 → 0,8924001**; 33 **1 → 0,92** (= dotychczasowa korekta × 0,90 / 0,92 / 0,88 / 0,92 / 0,92).
  Wartości dobrane programem tak, żeby `aiPower` był bitowo równy (stary `aiPower` × mnożnik K1),
  czyli temu, co mierzył D37 (`BALANCE_POWER_SCALE_MAP`). 30 i 32 mają „brzydkie” literały, bo
  0,90988f i 0,8924f dają inny Float `aiPower`.
- `aiPower` 28 / 30 / 31 / 32 / 33: 2,1372 → **1,92348**; 2,3732967 → **2,183433**;
  2,5541394 → **2,2476428**; 2,790617 → **2,5673678**; 2,9010668 → **2,6689816**. Pozostałe 31 etapów
  bez zmian (sprawdzone porównaniem `Campaign.stages` przed/po).
- `SimVersion.PROTOCOL` 0.8.9 → **0.8.10** (`RANKED_REPLAY_FORMATS` i `Replay.VERSION` bez zmian);
  literały wersji w testach serwera, `ReleaseConfigTest`, `server/README.md`.
- Złote powtórki po regeneracji bez zmian (kampania: etapy 1 i 29). Testy `:sim` (poza Gradle)
  przed i po: OK 317. `SecondHourControlTest` (etap 21) bez zmian.

### Pomiar z kodu, N=100, Normalny, etapy 16–36

Wynik **identyczny co do bitwy z K1 z D37** we wszystkich sześciu profilach (etapy 19–36);
etapy 16–27, 29 i 34–36 identyczne jak przed zmianą (w `medale-czas` 29 i 34–36 różnią się tylko
licznikiem minut gry; z dowódcą etap 35 o 1 poziom badań mniej: 47 → 44%).
Wygrane / oczekiwany czas przejścia [min].

| Etap | Realny gracz (`medale-czas`) przed → po | „medale” | „medale” + dowódca | `medale-czas` + dowódca | Docelowy | Docelowy + dowódca |
|---:|---:|---:|---:|---:|---:|---:|
| 28 | 58% / 21,6 → **65% / 18,1** | 40 → 55% | 40 → 42% | 46 → 62% | 70 → 78% | 68 → 74% |
| 30 | 43% / 20,1 → **54% / 15,9** | 46 → 50% | 49 → 59% | 45 → 50% | 69 → 68% | 66 → 77% |
| 31 | 41% / 24,6 → **52% / 19,4** | 33 → 49% | 41 → 54% | 41 → 52% | 53 → 66% | 71 → 76% |
| 32 | 40% / 27,4 → **50% / 21,9** | 38 → 53% | 34 → 47% | 38 → 52% | 65 → 78% | 73 → 72% |
| 33 | 45% / 15,1 → **52% / 14,4** | 37 → 46% | 37 → 45% | 47 → 43% | 59 → 70% | 60 → 75% |
| Σ 28–36 (oczek.) | 179 → **160 min** | 207 → 174 | 204 → 182 | 190 → 169 | 111 → 100 | 110 → 102 |
| śr. wygrane 28–36 | 48,1 → **53,2%** | 43,4 → 50,0% | 43,7 → 48,8% | 46,3 → 50,7% | 66,8 → 71,7% | 67,6 → 71,6% |

Ocena (kryterium d33): realny gracz — etap 28 ostrzegawcza → **płynnie** (80 → 65%, −15 pp,
1,41×), 30–33 ≥ 50% (bez stref); „medale” bez ściany (28: 69 → 55%, 1,46× zamiast 2,02×; 31–33 46–53%; strefy
ostrzegawcze < 50% zostają na 29, 31, 33, 36);
„medale” + dowódca na 28 nadal **ściana** (69 → 42%, 1,95×). Docelowy najwyżej 78% (28, 32; cel
tury 10 „≤ ok. 75%”, +3 pp w szumie). Etapy 19–27 bez zmian (klif 19 z dowódcą jak w D37).

- 2026-11-04 — regeneracja złotych powtórek: K1: stageRamp 28 x0,90, 30 x0,92, 31 x0,88, 32 x0,92, 33 x0,92, PROTOCOL 0.8.10 (do decyzji właściciela); powtórki bez zmian.
