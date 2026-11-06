# 200 — ekonomia medali i kampania

**NIEUKOŃCZONE: brak pomiarów wygranych rzeczywistych graczy.**

Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd` (6.11.2026). Jedyny zmieniony plik to ten raport. Nie zmieniono balansu. Wszystkie poniższe wyniki to symulacje AI, a nie wyniki testerów.

## Metoda

Przeprowadzono `campaignReport` z `BALANCE_RUNS=8`, `BALANCE_PROFILE=target` oraz `campaignDifficultyReport` z 20 próbami. Profil target oznacza różne poziomy badań dla grup etapów; w tabeli podano dokładne nazwy profili z programu. Osobno uruchomiono 8 prób profilu `medale-czas`. N=8 to krok 12,5 p.p.; N=20 to krok 5 p.p. Wyniki nie są wiarygodną estymacją wygranych ludzi.

## Docelowy profil: wszystkie etapy

| Etap | Profil | Wygrane N=8 | Mediana min N=8 | Łatwy N=20 | Normalny N=20 | Trudny N=20 |
|---:|---|---:|---:|---:|---:|---:|
| 1 | nowy | 87.5% | 13.72 | 19/20 (95%) | 15/20 (75%) | 14/20 (70%) |
| 2 | nowy | 87.5% | 7.80 | 19/20 (95%) | 19/20 (95%) | 14/20 (70%) |
| 3 | nowy | 75.0% | 7.64 | 20/20 (100%) | 16/20 (80%) | 18/20 (90%) |
| 4 | nowy | 75.0% | 7.70 | 19/20 (95%) | 18/20 (90%) | 15/20 (75%) |
| 5 | nowy | 87.5% | 7.56 | 18/20 (90%) | 18/20 (90%) | 15/20 (75%) |
| 6 | nowy | 87.5% | 7.63 | 16/20 (80%) | 15/20 (75%) | 16/20 (80%) |
| 7 | poczatkujacy | 100.0% | 7.85 | 17/20 (85%) | 14/20 (70%) | 13/20 (65%) |
| 8 | poczatkujacy | 37.5% | 9.50 | 19/20 (95%) | 8/20 (40%) | 11/20 (55%) |
| 9 | poczatkujacy | 75.0% | 8.29 | 19/20 (95%) | 14/20 (70%) | 12/20 (60%) |
| 10 | poczatkujacy | 62.5% | 9.21 | 17/20 (85%) | 16/20 (80%) | 13/20 (65%) |
| 11 | poczatkujacy | 87.5% | 8.57 | 15/20 (75%) | 12/20 (60%) | 11/20 (55%) |
| 12 | poczatkujacy | 75.0% | 9.07 | 17/20 (85%) | 15/20 (75%) | 11/20 (55%) |
| 13 | poczatkujacy | 62.5% | 7.46 | 17/20 (85%) | 13/20 (65%) | 8/20 (40%) |
| 14 | poczatkujacy | 25.0% | 10.35 | 15/20 (75%) | 11/20 (55%) | 8/20 (40%) |
| 15 | poczatkujacy | 62.5% | 9.01 | 18/20 (90%) | 12/20 (60%) | 8/20 (40%) |
| 16 | poczatkujacy | 75.0% | 10.96 | 15/20 (75%) | 10/20 (50%) | 12/20 (60%) |
| 17 | poczatkujacy | 87.5% | 10.08 | 17/20 (85%) | 17/20 (85%) | 17/20 (85%) |
| 18 | poczatkujacy | 75.0% | 10.41 | 16/20 (80%) | 15/20 (75%) | 9/20 (45%) |
| 19 | sredni | 62.5% | 8.41 | 19/20 (95%) | 15/20 (75%) | 13/20 (65%) |
| 20 | sredni | 75.0% | 12.04 | 20/20 (100%) | 14/20 (70%) | 8/20 (40%) |
| 21 | sredni | 87.5% | 10.80 | 17/20 (85%) | 17/20 (85%) | 12/20 (60%) |
| 22 | sredni | 50.0% | 10.87 | 14/20 (70%) | 10/20 (50%) | 8/20 (40%) |
| 23 | sredni | 25.0% | 14.10 | 17/20 (85%) | 8/20 (40%) | 9/20 (45%) |
| 24 | sredni | 62.5% | 12.83 | 15/20 (75%) | 13/20 (65%) | 6/20 (30%) |
| 25 | sredni | 50.0% | 12.39 | 18/20 (90%) | 12/20 (60%) | 6/20 (30%) |
| 26 | sredni | 50.0% | 7.29 | 18/20 (90%) | 12/20 (60%) | 9/20 (45%) |
| 27 | sredni | 62.5% | 12.99 | 14/20 (70%) | 14/20 (70%) | 9/20 (45%) |
| 28 | weteran | 87.5% | 10.75 | 19/20 (95%) | 16/20 (80%) | 10/20 (50%) |
| 29 | weteran | 75.0% | 2.65 | 20/20 (100%) | 16/20 (80%) | 15/20 (75%) |
| 30 | weteran | 87.5% | 6.16 | 17/20 (85%) | 14/20 (70%) | 12/20 (60%) |
| 31 | weteran | 100.0% | 9.14 | 20/20 (100%) | 15/20 (75%) | 11/20 (55%) |
| 32 | weteran | 87.5% | 12.16 | 18/20 (90%) | 17/20 (85%) | 10/20 (50%) |
| 33 | weteran | 75.0% | 10.50 | 17/20 (85%) | 12/20 (60%) | 11/20 (55%) |
| 34 | weteran | 50.0% | 10.46 | 17/20 (85%) | 11/20 (55%) | 9/20 (45%) |
| 35 | weteran | 62.5% | 10.09 | 18/20 (90%) | 13/20 (65%) | 10/20 (50%) |
| 36 | weteran | 62.5% | 11.85 | 18/20 (90%) | 15/20 (75%) | 11/20 (55%) |

## Model medali przed wejściem do etapu

Osobny profil `medale-czas`: kolumny kampania i czas są skumulowanymi przychodami w modelu, a nie stanem konta. Budżet to ich suma; algorytm wydaje ją na najtańsze dostępne poziomy w kolejności definicji. Saldo mniejsze od następnego kosztu jest skutkiem tego algorytmu i samo w sobie nie oznacza zablokowania gracza. Profil zakłada medale zależne od czasu i historyczne założenia opisane w `BalanceReport.kt`; nie jest to śledzenie konkretnego gracza. Koszty i kolejność zakupów odtworzono z `Research.kt`, sprawdzono zgodność liczby zakupionych poziomów dla wszystkich 36 wierszy.

| stage | cumulative gross budget | levels | remaining | next research key | next cost | gap | wins % (N=8) |
|---:|---:|---:|---:|---|---:|---:|---:|
| 1 | 0.00 | 0 | 0 | r_startGold | 30 | 30 | 87.50 |
| 2 | 97.42 | 2 | 27 | r_startGold | 40 | 13 | 62.50 |
| 3 | 181.48 | 4 | 31 | r_turretDmg | 40 | 9 | 62.50 |
| 4 | 273.23 | 6 | 38 | r_unitHp | 45 | 7 | 100.00 |
| 5 | 374.61 | 8 | 44 | r_xp | 50 | 6 | 75.00 |
| 6 | 479.73 | 10 | 45 | r_baseHp | 54 | 9 | 75.00 |
| 7 | 593.42 | 12 | 51 | r_income | 55 | 4 | 62.50 |
| 8 | 736.45 | 15 | 21 | r_train | 69 | 48 | 62.50 |
| 9 | 899.62 | 17 | 45 | r_cd | 70 | 25 | 62.50 |
| 10 | 1068.89 | 19 | 71 | r_baseHp | 73 | 2 | 87.50 |
| 11 | 1249.57 | 22 | 30 | r_unitDmg | 79 | 49 | 62.50 |
| 12 | 1442.34 | 24 | 65 | r_rally | 90 | 25 | 87.50 |
| 13 | 1640.69 | 26 | 78 | r_xp | 97 | 19 | 87.50 |
| 14 | 1839.67 | 28 | 81 | r_cd | 99 | 18 | 87.50 |
| 15 | 2061.39 | 31 | 4 | r_income | 105 | 101 | 87.50 |
| 16 | 2295.97 | 33 | 28 | r_unitHp | 105 | 77 | 100.00 |
| 17 | 2529.47 | 35 | 37 | r_train | 131 | 94 | 100.00 |
| 18 | 2774.68 | 37 | 17 | r_baseHp | 136 | 119 | 87.50 |
| 19 | 3031.69 | 39 | 2 | r_xp | 137 | 135 | 75.00 |
| 20 | 3289.86 | 40 | 123 | r_unitDmg | 140 | 17 | 62.50 |
| 21 | 3571.50 | 42 | 125 | r_cd | 141 | 16 | 100.00 |
| 22 | 3844.63 | 44 | 112 | r_cap | 150 | 38 | 25.00 |
| 23 | 4223.32 | 46 | 160 | r_train | 181 | 21 | 87.50 |
| 24 | 4510.66 | 48 | 80 | r_turretDmg | 186 | 106 | 75.00 |
| 25 | 4818.94 | 50 | 15 | r_unitHp | 187 | 172 | 50.00 |
| 26 | 5154.09 | 51 | 164 | r_xp | 192 | 28 | 75.00 |
| 27 | 5473.69 | 53 | 91 | r_cd | 200 | 109 | 100.00 |
| 28 | 5786.72 | 54 | 204 | r_cap | 240 | 36 | 62.50 |
| 29 | 6135.27 | 56 | 68 | r_unitDmg | 249 | 181 | 50.00 |
| 30 | 6492.53 | 57 | 176 | r_unitHp | 249 | 73 | 12.50 |
| 31 | 7001.87 | 59 | 186 | r_baseHp | 253 | 67 | 62.50 |
| 32 | 7366.27 | 61 | 45 | r_slots | 260 | 215 | 62.50 |
| 33 | 7743.33 | 62 | 162 | r_xp | 268 | 106 | 50.00 |
| 34 | 8143.36 | 64 | 18 | r_cd | 284 | 266 | 37.50 |
| 35 | 8578.89 | 65 | 169 | r_startGold | 330 | 161 | 62.50 |
| 36 | 8981.31 | 66 | 242 | r_unitDmg | 331 | 89 | 37.50 |

## Ściany i porównanie

W profilu `medale-czas` poniżej 55% w N=8: 22 (25%), 25 (50%), 29 (50%), 30 (12,5%), 33 (50%), 34 (37,5%) i 36 (37,5%). To lista kandydatów z symulacji AI, nie lista etapów, na których rzeczywiści gracze osiągają mniej niż 55%.

D37 przed K1 przy N=100 podawał odpowiednio 68%, 61%, 51%, 43%, 45%, 52% i 53%. D40 po K1 dla etapów 29, 30, 33, 34, 36 podaje 51%, 54%, 52%, 52%, 53% przy N=100. D47 potwierdza włączenie K1, bez nowego pomiaru wygranych. Mała próba i zależność profilu od wyników wcześniejszych etapów uniemożliwiają uznanie różnic za dowód zmiany balansu. Należy powtórzyć pomiar z N≥100 i zebrać anonimowe wyniki rzeczywistych testerów przed decyzją o zmianie.

## Wyniki poleceń

```text
BALANCE_RUN=true BALANCE_RUNS=8 BALANCE_PROFILE=target :app:testDebugUnitTest --rerun --tests *BalanceReport.campaignReport*
BUILD SUCCESSFUL in 26s; 37 actionable tasks: 17 executed, 20 from cache
BALANCE_RUN=true BALANCE_RUNS=20 :app:testDebugUnitTest --rerun-tasks --tests *BalanceReport.campaignDifficultyReport*
BUILD SUCCESSFUL in 2m 7s; 37 actionable tasks: 37 executed
BALANCE_RUN=true BALANCE_RUNS=8 BALANCE_PROFILE=medale-czas :app:testDebugUnitTest --rerun --tests *BalanceReport.campaignReport*
BUILD SUCCESSFUL in 19s; 37 actionable tasks: 1 executed, 36 up-to-date
```

Szczegółowe dane pomiaru docelowego i wszystkich trudności są przedstawione w tabeli powyżej; zero timeoutów w tych wierszach. Uruchomiono Gradle w Windows, JDK 17 i SDK 36. Dalsza praca: większa próba modelu i testy graczy.
