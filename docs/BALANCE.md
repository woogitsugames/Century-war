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
- Zmienne: `BALANCE_STAGES="1,7,13"`, `BALANCE_PROFILE` (`target` albo nazwa), `BALANCE_POWER_SCALE`, `BALANCE_STRICT=true` (progi), `BALANCE_TAG`. Wyniki: `app/build/balance/`.
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
- W zwykłych pojedynkach doktryny `BULWARK`, `FURY` i `PLUNDER` mają osłabione wartości (odpowiednio 5%/3%, 8% i 8%) po audycie lustrzanym. **Karty Endless zachowują pierwszą historyczną premię** (20%/10%, 12% i 35%); ich kolejne stosy są wygaszane według powyższej tabeli. Opisy ofert rozróżniają te tryby. Opis w dziesięciu językach uwzględnia obie nagrody.

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
