# Zlecenie 198 — pomiar Endless, przygotowanie

Data: 2026-11-06. Baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`; gałąź `gpt/198-endless-report`.

**WYMAGANA KOMENDA ZLECENIA NIE ZOSTAŁA JESZCZE URUCHOMIONA.** Poniższe rzeczywiste wyniki pochodzą ze świeżego `:server:test` uruchomionego przez koordynatora w worktree zlecenia 172 na tym samym SHA `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7` (`C:/Users/matma/.codex/century-war-build/172-server.log`). Wszystkie cztery wymienione niżej metody Endless przeszły, ale cały zestaw serwera zakończył się błędem innego testu. Po zwolnieniu Gradle trzeba wykonać komendę wymaganą w 198. Jedyny zmieniony plik: ten raport. Bez zmian symulacji, balansu, testów i `docs/BALANCE.md`.

## Metoda i porównywalność

`server/src/test/kotlin/com/centurywar/server/EndlessBalanceReportTest.kt` definiuje badane warianty: AI kieruje graczem z 80% badań, seedy 1001–1020 albo 1001–1100, ograniczenie 90 000 kroków i fala 60. Przeżycie progu oznacza żywą bazę przy dotarciu do fali; fala 60 ucina pomiar i nie oznacza porażki. Mediana 100 seedów obejmuje tylko porażki. Przebieg kart `newCardsEffect206` obejmuje 50 seedów 1001–1050 i trzy aplikacje karty na fali 5, więc nie przedstawia częstości naturalnego wyboru; `ENDLESS100_CARD206` pokazuje tę częstość osobno. `weeklyRuleSets184` używa ośmiu tych samych tygodni na zestaw, nie profilu weterana.

## Bazowe dane z `docs/BALANCE.md`, sekcja D58

Poprzednio oznacza stan po strojeniu D58 z 5.11; wartości bieżące pochodzą z opisanego wyżej przebiegu serwera. Wyniki D58 w dokumencie są zaokrąglone.

| Miara | Teraz | Poprzednio | Różnica |
|---|---:|---:|---:|
| Średnia fala, 100 seedów 1001–1100 | 50,42 | 50,42 | 0,00 |
| Średni wynik, 100 seedów 1001–1100 | 20 707,45 | 20 707 | +0,45 (zaokrąglenie) |
| Żywa baza przy fali 20, 1001–1100 | 100/100 | 100/100 | 0 |
| Żywa baza przy fali 60, 1001–1100 | 5/100 | 5/100 | 0 |
| Mediana fali porażki, 1001–1100 | 53 | 53 | 0 |
| Średnia fala, 20 seedów 1001–1020 | 50,95 | brak równoważnego wyniku D58 w tabeli | — |
| Żywa baza przy fali 60, 20 seedów 1001–1020 | 1/20 | brak równoważnego wyniku D58 w tabeli | — |

Pełny rozkład 100 seedów, przedziały `<20 / 20–29 / 30–39 / 40–44 / 45–49 / 50–54 / 55–59 / żywa baza przy 60`: **0 / 2 / 14 / 3 / 3 / 63 / 10 / 5**. Żywa baza przy falach 20/30/40/50/60: **100/98/84/78/5** na 100. Porażki: 95/100. W próbie 20 seedów przeżycia tych progów to **20/20, 20/20, 17/20, 16/20, 1/20**; mediana porażek 54.

### Siła kart — średnia fala, 50 seedów, trzy aplikacje przy fali 5

| Karta | Teraz | Poprzednio D58 | Różnica |
|---|---:|---:|---:|
| Brak | 49,62 | 49,62 | 0,00 |
| Szyk pik (`PIKE_SQUARE`) | 49,80 | 49,80 | 0,00 |
| Straż przednia (`VANGUARD`) | 48,94 | 48,94 | 0,00 |
| Nagroda za głowę (`BOSS_BOUNTY`) | 50,52 | 50,52 | 0,00 |
| Rogi wojenne (`WAR_HORNS`) | 52,98 | 52,98 | 0,00 |
| Mur tarcz (`SHIELD_WALL`) | 53,44 | 53,44 | 0,00 |
| Saperzy (`SAPPERS`) | 52,28 | 52,28 | 0,00 |
| Pancerz piechoty (`FRONTLINE_PLATE`) | 55,42 | 55,42 | 0,00 |
| Skarbiec wojenny (`WAR_CHEST`) | 51,74 | 51,74 | 0,00 |
| Bębny natarcia (`WAR_DRUMS`) | 50,74 | 50,74 | 0,00 |

Wyniki D58 dla tych samych wariantów (fala 60 / 50; średni wynik): brak 3 / 20 304;
Szyk pik 1 / 20 458; Straż przednia 2 / 19 930; Nagroda za głowę 2 / 20 734;
Rogi wojenne 3 / 21 910; Mur tarcz 3 / 22 076; Saperzy 4 / 21 599;
Pancerz piechoty 9 / 23 010; Skarbiec wojenny 3 / 21 238; Bębny natarcia 2 / 20 763.

### Tygodnie — mediana fali na ośmiu tych samych tygodniach

| Zestaw | Teraz | Poprzednio D58 | Różnica |
|---|---:|---:|---:|
| day109_gold | 12,0 | 12,0 | 0,0 |
| day109_march | 11,0 | 11,0 | 0,0 |
| day109_rain | 11,5 | 11,5 | 0,0 |
| day109_veterans | 12,0 | 12,0 | 0,0 |
| day109_hired | 12,0 | 12,0 | 0,0 |
| day109_iron | 8,5 | 8,5 | 0,0 |
| week184_night | 11,0 | 11,0 | 0,0 |
| week184_mercs | 13,0 | 13,0 | 0,0 |
| week184_bastion | 10,0 | 10,0 | 0,0 |
| week206_blitz | 9,0 | 9,0 | 0,0 |
| week206_pikes | 10,5 | 10,5 | 0,0 |

## Wnioski z dostępnego pomiaru

1. Na 100 seedach średnia fala 50,42 i mediana porażek 53 są takie jak po D58. Fala 60 osiągnięta w 5/100 jest na dolnej granicy celu 5–15; to cel raportowy, nie przyczyna automatycznej zmiany balansu.
2. W pierwszych 20 seedach 1/20 dochodzi do fali 60; jedna obserwacja zmienia wynik o 5 punktów procentowych, więc próba nie daje precyzyjnego szacunku ogona.
3. W izolowanym scenariuszu karta ×3: Rogi wojenne zwiększają średnią falę względem braku karty o 3,36; Straż przednia zmniejsza ją o 0,68. To wynik tego scenariusza i wyborów AI, nie dowód siły każdej karty w rękach człowieka.
4. Mediany wszystkich 11 zestawów tygodnia są identyczne z D58. Nowe zestawy mają mediany 9,0 i 10,5, w historycznym paśmie 8,5–13,0; jedynie `week184_mercs` osiągnął falę 20 w 2 z 8 przebiegów. Wnioski ogranicza mała próba.
5. Naturalny wybór nowych kart: Szyk pik 44/100, Straż 19/100, Nagroda 11/100 i Rogi 25/100; w każdym przypadku liczba stosów jest równa liczbie przebiegów. Nie wnioskuję o przyczynowości na podstawie częstości wyboru.

## Wyniki dostępnego przebiegu

Wybrane linie rzeczywistego logu koordynatora (metody oznaczone `PASSED`):

```text
ENDLESS_MEAN=50.95
ENDLESS_SURVIVE_20=20/20
ENDLESS_SURVIVE_30=20/20
ENDLESS_SURVIVE_40=17/20
ENDLESS_SURVIVE_50=16/20
ENDLESS_SURVIVE_60=1/20
ENDLESS_DEFEAT_WAVE_MEDIAN=54
ENDLESS100_SEEDS=1001..1100
ENDLESS100_MEAN=50.42
ENDLESS100_SCORE_MEAN=20707.45
ENDLESS100_FAILURES=95/100
ENDLESS100_DEFEAT_MEDIAN=53
ENDLESS100_SURVIVE_20=100/100
ENDLESS100_SURVIVE_30=98/100
ENDLESS100_SURVIVE_40=84/100
ENDLESS100_SURVIVE_50=78/100
ENDLESS100_SURVIVE_60=5/100
ENDLESS100_DISTRIBUTION=0,2,14,3,3,63,10,5
ENDLESS100_CARD206 PIKE_SQUARE RUNS=44/100 STACKS=44
ENDLESS100_CARD206 VANGUARD RUNS=19/100 STACKS=19
ENDLESS100_CARD206 BOSS_BOUNTY RUNS=11/100 STACKS=11
ENDLESS100_CARD206 WAR_HORNS RUNS=25/100 STACKS=25
ENDLESS100_TARGET_SURVIVE_60=OK (cel 5-15)
ENDLESS100_TARGET_MEDIAN=OK (cel 45-55)
CARD206 CARD=BRAK N=50 WAVE_MEAN=49.62 SURVIVE60=3 SCORE_MEAN=20304.34
CARD206 CARD=PIKE_SQUARE N=50 WAVE_MEAN=49.8 SURVIVE60=1 SCORE_MEAN=20458.36
CARD206 CARD=VANGUARD N=50 WAVE_MEAN=48.94 SURVIVE60=2 SCORE_MEAN=19929.92
CARD206 CARD=BOSS_BOUNTY N=50 WAVE_MEAN=50.52 SURVIVE60=2 SCORE_MEAN=20733.82
CARD206 CARD=WAR_HORNS N=50 WAVE_MEAN=52.98 SURVIVE60=3 SCORE_MEAN=21909.58
CARD206 CARD=SHIELD_WALL N=50 WAVE_MEAN=53.44 SURVIVE60=3 SCORE_MEAN=22076.28
CARD206 CARD=SAPPERS N=50 WAVE_MEAN=52.28 SURVIVE60=4 SCORE_MEAN=21598.58
CARD206 CARD=FRONTLINE_PLATE N=50 WAVE_MEAN=55.42 SURVIVE60=9 SCORE_MEAN=23010.18
CARD206 CARD=WAR_CHEST N=50 WAVE_MEAN=51.74 SURVIVE60=3 SCORE_MEAN=21237.52
CARD206 CARD=WAR_DRUMS N=50 WAVE_MEAN=50.74 SURVIVE60=2 SCORE_MEAN=20762.96
WEEKLY184_SET KEY=day109_gold MEDIAN=12.0 MEAN=11.75 WAVE20=0/8 WAVES=12,13,12,7,12,14,12,12
WEEKLY184_SET KEY=day109_march MEDIAN=11.0 MEAN=10.625 WAVE20=0/8 WAVES=12,12,10,10,9,12,8,12
WEEKLY184_SET KEY=day109_rain MEDIAN=11.5 MEAN=10.875 WAVE20=0/8 WAVES=12,10,12,8,13,11,9,12
WEEKLY184_SET KEY=day109_veterans MEDIAN=12.0 MEAN=11.5 WAVE20=0/8 WAVES=13,12,10,12,13,8,11,13
WEEKLY184_SET KEY=day109_hired MEDIAN=12.0 MEAN=11.125 WAVE20=0/8 WAVES=12,12,12,9,12,10,12,10
WEEKLY184_SET KEY=day109_iron MEDIAN=8.5 MEAN=9.125 WAVE20=0/8 WAVES=7,12,12,11,7,8,9,7
WEEKLY184_SET KEY=week184_night MEDIAN=11.0 MEAN=10.875 WAVE20=0/8 WAVES=13,10,12,9,9,13,9,12
WEEKLY184_SET KEY=week184_mercs MEDIAN=13.0 MEAN=19.25 WAVE20=2/8 WAVES=60,13,13,10,13,15,9,21
WEEKLY184_SET KEY=week184_bastion MEDIAN=10.0 MEAN=10.375 WAVE20=0/8 WAVES=8,11,12,9,9,12,9,13
WEEKLY184_SET KEY=week206_blitz MEDIAN=9.0 MEAN=8.875 WAVE20=0/8 WAVES=6,9,12,7,7,11,10,9
WEEKLY184_SET KEY=week206_pikes MEDIAN=10.5 MEAN=10.625 WAVE20=0/8 WAVES=12,9,9,10,12,9,11,13
```

Końcowy wynik całego `:server:test` w przebiegu 172, dla uniknięcia pomylenia go z zielonym zestawem:

```text
117 tests completed, 1 failed, 1 skipped
> Task :server:test FAILED
BUILD FAILED in 55s
```

Niepowodzenie dotyczyło `SqliteMaintenanceTest.migratesSchemaTwoToThreeKeepingDataAndRefusesNewerSchema`, a nie wymienionych metod pomiarowych.

## Pomiar do wykonania

Po zwolnieniu Gradle: `./gradlew :server:test -PserverOnly=true --tests '*EndlessBalanceReportTest*' --console=plain --no-configuration-cache --rerun-tasks`. Zachować pełne wyjście, w tym `ENDLESS_SEED`, `ENDLESS100_*`, `CARD206` i `WEEKLY184_SET`, a następnie wkleić zmierzone liczby, histogram i końcówkę polecenia. Nie traktować powyższych historycznych wartości jako nowych wyników. Skala 20/100 seedów ma ograniczoną precyzję; test AI nie zastępuje obserwacji ludzi.
