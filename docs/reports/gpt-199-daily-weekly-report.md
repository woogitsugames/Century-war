# 199 — wyzwania dnia i tygodnia

**POMIAR SKORYGOWANY: zachowano osobowość AI z konfiguracji każdego dnia.**

Baza: `origin/main` **7d847576e27bc5411bfd93877c1ba6df18af05cd**, 6.11.2026. Gałąź `gpt/199-daily-weekly-report`. Wyniki są pomiarem AI, nie wynikami graczy.

## Metoda i ograniczenia

Dni: 28 kolejnych dni od `Daily.RELEASE_DAY`; dla każdego `Daily.config` zachowano epoki, modyfikatory, pogodę, wydarzenie, bonusy i przypisaną osobowość AI. Pierwsza bitwa ma oryginalne ziarno, kolejne dziewięć używa ziarna powiększanego o 997. Zmieniają się więc także zachowania losowe i osobowości; 9 prób to **warianty symulacyjne, a nie faktyczne dodatkowe podejścia danego dnia**. AI steruje oboma stronami, limit to 108 000 kroków (30 minut), odsetek to wygrane strony 0 z 10 prób. Mała próba ma skok 10 p.p. i nie dowodzi trudności dla ludzi.

Tydzień: po 8 tych samych tygodni bazowych, na których wymuszono każdy z 11 zestawów i jego kartę, z normalizowanymi bonusami wyzwania. To kontrolowane porównanie reguł, nie osiem oficjalnych tygodni każdego zestawu. Mierzono falę i czas do końca albo fali 60; tygodniowy Endless nie ma zwycięstwa w sensie zniszczenia bazy przeciwnika, dlatego podajemy osiągnięcie fali 20. Timeout = 90 000 kroków bez końca i bez fali 60.

Test pomiarowy uruchomiono lokalnie w jednorazowym pliku testowym, po czym usunięto go z końcowego drzewa gałęzi; w historii gałęzi pozostaje commit z tym plikiem oraz commit go usuwający. Zgodnie z ograniczeniem użytkownika końcowy diff zawiera tylko raport.

## 28 dni

| Dzień UTC | Od premiery | Wydarzenie | Modyfikatory | Wygrane AI / 10 | Mediana min | Timeouty |
|---:|---:|---|---|---:|---:|---:|
| 21153 | 0 | null | MERCENARIES+RICH_START | 2/10 | 13.14 | 0 |
| 21154 | 1 | null | MERCENARIES+DOUBLE_INCOME | 2/10 | 13.23 | 0 |
| 21155 | 2 | null | SHORT_DAY+DOUBLE_INCOME+VETERANS | 0/10 | 8.00 | 0 |
| 21156 | 3 | MIRROR_ARMY | MERCENARIES+RANGED_HORDE+SLOW_XP | 4/10 | 14.40 | 0 |
| 21157 | 4 | null | FRAGILE_BASES+FAST_UNITS | 3/10 | 13.91 | 0 |
| 21158 | 5 | null | RANGED_HORDE+SHORT_DAY | 0/10 | 8.00 | 0 |
| 21159 | 6 | null | VETERANS+EXPENSIVE_TOWERS | 3/10 | 13.72 | 0 |
| 21160 | 7 | MIRROR_ARMY | FAST_UNITS+EXPENSIVE_TOWERS | 5/10 | 13.03 | 0 |
| 21161 | 8 | null | SLOW_XP+SHORT_DAY | 0/10 | 8.00 | 0 |
| 21162 | 9 | null | FRAGILE_BASES+SLOW_XP | 1/10 | 14.16 | 0 |
| 21163 | 10 | null | GLASS_CANNON+SLOW_XP | 3/10 | 13.94 | 0 |
| 21164 | 11 | FORTIFIED_BASES | DOUBLE_INCOME+NO_TURRETS | 0/10 | 12.41 | 0 |
| 21165 | 12 | null | NO_TURRETS+SLOW_XP | 6/10 | 13.97 | 0 |
| 21166 | 13 | null | ARMORED_FOE+SHORT_DAY | 0/10 | 8.00 | 0 |
| 21167 | 14 | null | FRAGILE_BASES+EXPENSIVE_TOWERS | 2/10 | 13.92 | 0 |
| 21168 | 15 | FORTIFIED_BASES | GLASS_CANNON+EXPENSIVE_TOWERS | 3/10 | 12.30 | 0 |
| 21169 | 16 | null | DOUBLE_INCOME+ARMORED_FOE | 0/10 | 13.20 | 0 |
| 21170 | 17 | null | NO_TURRETS+MERCENARIES | 0/10 | 12.08 | 0 |
| 21171 | 18 | null | FOG_OF_WAR+VETERANS | 1/10 | 13.73 | 0 |
| 21172 | 19 | HEAVY_DAY | FAST_UNITS+RICH_START | 2/10 | 13.32 | 0 |
| 21173 | 20 | null | RICH_START+SHORT_DAY | 0/10 | 8.00 | 0 |
| 21174 | 21 | null | DOUBLE_INCOME+MERCENARIES | 1/10 | 12.18 | 0 |
| 21175 | 22 | null | RANGED_HORDE+FOG_OF_WAR | 0/10 | 14.38 | 0 |
| 21176 | 23 | SIEGE_WORKS | SHORT_DAY+MERCENARIES | 0/10 | 8.00 | 0 |
| 21177 | 24 | null | FAST_UNITS+VETERANS | 2/10 | 12.44 | 0 |
| 21178 | 25 | null | SLOW_XP+FOG_OF_WAR | 1/10 | 13.96 | 0 |
| 21179 | 26 | null | FRAGILE_BASES+RICH_START | 2/10 | 13.87 | 0 |
| 21180 | 27 | FREE_BARRAGE | GLASS_CANNON+RICH_START | 1/10 | 13.88 | 0 |

## 11 zestawów tygodnia

| Zestaw | N | Mediana fali | Fala 20 | Mediana min | Timeouty |
|---|---:|---:|---:|---:|---:|
| day109_gold | 8 | 12.0 | 0/8 | 5.28 | 0 |
| day109_march | 8 | 11.0 | 0/8 | 4.83 | 0 |
| day109_rain | 8 | 11.5 | 0/8 | 4.97 | 0 |
| day109_veterans | 8 | 12.0 | 0/8 | 5.38 | 0 |
| day109_hired | 8 | 12.0 | 0/8 | 5.22 | 0 |
| day109_iron | 8 | 8.5 | 0/8 | 3.75 | 0 |
| week184_night | 8 | 11.0 | 0/8 | 4.89 | 0 |
| week184_mercs | 8 | 13.0 | 2/8 | 5.71 | 0 |
| week184_bastion | 8 | 10.0 | 0/8 | 4.46 | 0 |
| week206_blitz | 8 | 9.0 | 0/8 | 4.02 | 0 |
| week206_pikes | 8 | 10.5 | 0/8 | 4.67 | 0 |

## Wnioski

- Mediana dziennych median wynosi 13.21 min; żaden dzień nie przekracza 1,5 jej wartości w tej próbie.
- Poniżej 30% wygranych AI: przesunięcia od premiery 0, 1, 2, 5, 8, 9, 11, 13, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27. To sygnał do zbadania, nie propozycja automatycznej korekty balansu; w każdym dniu jest tylko 10 prób.
- Dni z `SHORT_DAY` kończą się w medianie po 8 minutach i w tej próbie mają 0/10 wygranych AI. Przy remisie procentowego zdrowia baz po upływie czasu wygrywa przeciwnik (`Battle.kt:1072–1076`); pomiar nie zapisał liczby remisów, więc nie można przypisać im tych wyników.
- Zestawy tygodnia mają medianę 8,5–13 fal; `week184_mercs` osiągnął falę 20 w 2/8 prób, pozostałe 0/8. Potrzebna większa liczba prawdziwych tygodni i obserwacja graczy.
- W badanym oknie nie wystąpiły nowe zdarzenia `SCHOLARS_DAY` ani `FURY_DAY`; ich osobny pomiar pozostaje otwarty. Nie proponuję zmiany parametrów bez większej próby i testów z ludźmi.

## Testy

```
:app:testDebugUnitTest --rerun --tests *BalanceReport.mirrorReport*: BUILD SUCCESSFUL in 1m 25s
:sim:test -PserverOnly=true --tests *DailyTest* --tests *WeeklyTest* --tests *DailyEvent*: BUILD SUCCESSFUL in 26s
:app:testDebugUnitTest --rerun --tests *DailyWeeklyBalance199Test*: BUILD SUCCESSFUL in 27s (ponowny pomiar z zachowaną osobowością)
```

Pomiar lustrzany: mediana 13,87 / 13,66 / 13,24 min dla epok 0 / 4 / 8, wygrane strony 0 odpowiednio 15/40, 22/40, 19/40; bez timeoutów.

Jedyny plik w końcowym diffie: `docs/reports/gpt-199-daily-weekly-report.md`. Kod gry i balans niezmienione; raport nie jest prognozą wyników testerów.
