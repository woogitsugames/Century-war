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

Plik wynikowy: `app/build/balance/variants.md`. Po uruchomieniu pomiaru należy
włączyć wynik do tego dokumentu i porównać wyniki etapów z wcześniejszymi
progami kampanii. **Pomiar nie został jeszcze wykonany w tej gałęzi;
nie ma podstaw, by deklarować spełnienie progu 10 pp.**

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
