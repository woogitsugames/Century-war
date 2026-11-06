# Zlecenie 198 — pomiar Endless, przygotowanie

Data: 2026-11-06. Baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`; gałąź `gpt/198-endless-report`.

**GRADLE NIE ZOSTAŁ JESZCZE URUCHOMIONY. WYNIKI BIEŻĄCE NIE SĄ ZNANE.** Trwa przygotowanie pomiaru; wykonanie po zwolnieniu wspólnego Gradle. Raport nie jest ukończony. Jedyny zmieniony plik: ten raport. Bez zmian symulacji, balansu, testów i `docs/BALANCE.md`.

## Metoda i porównywalność

`server/src/test/kotlin/com/centurywar/server/EndlessBalanceReportTest.kt` definiuje badane warianty: AI kieruje graczem z 80% badań, seedy 1001–1020 albo 1001–1100, ograniczenie 90 000 kroków i fala 60. Przeżycie progu oznacza żywą bazę przy dotarciu do fali; fala 60 ucina pomiar i nie oznacza porażki. Mediana 100 seedów obejmuje tylko porażki. Przebieg kart `newCardsEffect206` obejmuje 50 seedów 1001–1050 i trzy aplikacje karty na fali 5, więc nie przedstawia częstości naturalnego wyboru; `ENDLESS100_CARD206` pokazuje tę częstość osobno. `weeklyRuleSets184` używa ośmiu tych samych tygodni na zestaw, nie profilu weterana.

## Bazowe dane z `docs/BALANCE.md`, sekcja D58

Poprzednio oznacza stan po strojeniu D58 z 5.11; bieżące wyniki i różnice zostaną wpisane dopiero z rzeczywistego uruchomienia na wskazanym SHA.

| Miara | Teraz | Poprzednio | Różnica |
|---|---:|---:|---:|
| Średnia fala, 100 seedów 1001–1100 | czeka | 50,42 | czeka |
| Średni wynik, 100 seedów 1001–1100 | czeka | 20 707 | czeka |
| Żywa baza przy fali 20, 1001–1100 | czeka | 100/100 | czeka |
| Żywa baza przy fali 60, 1001–1100 | czeka | 5/100 | czeka |
| Mediana fali porażki, 1001–1100 | czeka | 53 | czeka |
| Średnia fala, 20 seedów 1001–1020 | czeka | brak równoważnego wyniku D58 w tabeli | — |
| Żywa baza przy fali 60, 20 seedów 1001–1020 | czeka | brak równoważnego wyniku D58 w tabeli | — |

### Siła kart — średnia fala, 50 seedów, trzy aplikacje przy fali 5

| Karta | Teraz | Poprzednio D58 | Różnica |
|---|---:|---:|---:|
| Brak | czeka | 49,62 | czeka |
| Szyk pik (`PIKE_SQUARE`) | czeka | 49,80 | czeka |
| Straż przednia (`VANGUARD`) | czeka | 48,94 | czeka |
| Nagroda za głowę (`BOSS_BOUNTY`) | czeka | 50,52 | czeka |
| Rogi wojenne (`WAR_HORNS`) | czeka | 52,98 | czeka |
| Mur tarcz (`SHIELD_WALL`) | czeka | 53,44 | czeka |
| Saperzy (`SAPPERS`) | czeka | 52,28 | czeka |
| Pancerz piechoty (`FRONTLINE_PLATE`) | czeka | 55,42 | czeka |
| Skarbiec wojenny (`WAR_CHEST`) | czeka | 51,74 | czeka |
| Bębny natarcia (`WAR_DRUMS`) | czeka | 50,74 | czeka |

### Tygodnie — mediana fali na ośmiu tych samych tygodniach

| Zestaw | Teraz | Poprzednio D58 | Różnica |
|---|---:|---:|---:|
| day109_gold | czeka | 12,0 | czeka |
| day109_march | czeka | 11,0 | czeka |
| day109_rain | czeka | 11,5 | czeka |
| day109_veterans | czeka | 12,0 | czeka |
| day109_hired | czeka | 12,0 | czeka |
| day109_iron | czeka | 8,5 | czeka |
| week184_night | czeka | 11,0 | czeka |
| week184_mercs | czeka | 13,0 | czeka |
| week184_bastion | czeka | 10,0 | czeka |
| week206_blitz | czeka | 9,0 | czeka |
| week206_pikes | czeka | 10,5 | czeka |

## Pomiar do wykonania

Po zwolnieniu Gradle: `./gradlew :server:test -PserverOnly=true --tests '*EndlessBalanceReportTest*' --console=plain --no-configuration-cache --rerun-tasks`. Zachować pełne wyjście, w tym `ENDLESS_SEED`, `ENDLESS100_*`, `CARD206` i `WEEKLY184_SET`, a następnie wkleić zmierzone liczby, histogram i końcówkę polecenia. Nie traktować powyższych historycznych wartości jako nowych wyników. Skala 20/100 seedów ma ograniczoną precyzję; test AI nie zastępuje obserwacji ludzi.
