# Dzień 58: paczka zawartości 206 (karty Endless, zestawy tygodnia, wydarzenia dnia)

Gałąź `claude/dzien-58-tresc` z `origin/main` (42a26ca). Decyzja właściciela: nowa zawartość,
kampania bez zmian (etapy 1–36 nietknięte). Wzór: D6 (`docs/tasks/D6-content.md`, zlecenie 184).

## Co dodano

### Karty Endless (dopisane na końcu enuma, po `WAGER`; wszystkie trwałe, limit 8 stosów)

| Karta | Klucz | Rzadkość | Efekt (pełny stos; kolejne stosy 0,6 / 0,4 …) | Ikona |
|---|---|---|---|---|
| Szyk pik | `PIKE_SQUARE` | COMMON | piechota gracza otrzymuje od ciężkich wroga w walce wręcz −20% (min. ×0,6) | HELMET |
| Straż przednia | `VANGUARD` | UNCOMMON | piechota gracza zadaje strzelcom wroga +30% | SWORD |
| Nagroda za głowę | `BOSS_BOUNTY` | RARE | za pokonanego bossa 120 złota × skala kosztów epoki | CROWN |
| Rogi wojenne | `WAR_HORNS` | UNCOMMON | przez pierwsze 10 s fali jednostki gracza +20% obrażeń | ADVANCE |

Efekty liczą się ze stosów kart i `waveTimer` (`Battle.waveElapsed()`, `card206Mul` w `damageUnit`,
nagroda przy śmierci bossa), więc zrzut nie ma nowych pól. Wagi AI (`Ai.cardPreference`):
Rogi i Straż — szturmowy, Nagroda — ekonomista, Szyk pik — zrównoważony.

### Oblężenie tygodnia (`ChallengeRuleSets`, `COUNT` 9 → 11, przed premierą)

| Zestaw | Modyfikatory | Karta tygodnia |
|---|---|---|
| `week206_blitz` „Wojna błyskawiczna” | Szklane działa + Szybkie wojska | Rogi wojenne |
| `week206_pikes` „Żelazny szyk” | Opancerzony wróg + Bogaty start | Szyk pik |

Kolejność modyfikatorów odwrotna niż w parach dnia, więc `Daily.ruleSet` ich nie zwraca. Zestawy
działają od `Weekly.firstRuleSetWeek()` (premiera + 3 tygodnie), jak zestawy D6. Maska osiągnięcia
„Sześć oblicz wojny” rośnie do 11 bitów (dowolne 6 z 11), bez nowych osiągnięć.

### Wydarzenia dnia (dopisane na końcu `DailyEvent`, symetryczne)

| Wydarzenie | Efekt | Konflikt |
|---|---|---|
| `SCHOLARS_DAY` „Dzień nauki” | XP obu stron ×1,5 | Wolne doświadczenie |
| `FURY_DAY` „Dzień furii” | obrażenia jednostek obu stron ×1,2 | Szklane działa |

`Daily.EVENTS_V4_DAY` = `RELEASE_DAY` + 19 (pierwszy dzień wydarzenia po `EVENTS_V3_DAY`);
do niego pula to zamrożona jedenastka `EVENTS_V3`. `RELEASE_DAY` bez zmian.

### Formaty i wersje

- `SimVersion.PROTOCOL` 0.8.10 → **0.8.11** (literały w testach serwera, `ReleaseConfigTest`, `server/README.md`, `century-war.md`).
- `BattleSnapshot.VERSION` 20 → **21**: 4 nowe stosy kart; odczyt v16–v20 (`CARDS_BEFORE_206`). `Replay.VERSION` bez zmian.
- Historia bitew: krótsza tablica kart (o 4 i o 8) wczytuje się z zerami (`BattleHistoryTest`).
- Złote powtórki zregenerowane: w 10 plikach zmienił się tylko `snapshotSha256`; tak samo stała
  w `SecondHourControlTest` (etap 21).

### Teksty i UI

`part206` w `L.kt` (10 języków; liczba `putAll(part` = `private fun part` = 80): nazwy i opisy kart,
zestawów i dni, `wn_206_content` w „Co nowego” (na górze). Ikony w `BattleScreen`, `CodexScreen`,
`Icons.dailyEventIcon`; Kodeks pokazuje nowe karty, dni i zestawy automatycznie.

## Balans (szczegóły: `docs/BALANCE.md`, sekcja D58)

| Endless, weteran 80% | przed | po |
|---|---:|---:|
| Fala 60, seedy 1001–1100 | 9/100 | **5/100** |
| Fala 60, seedy 2001–2100 | 6/100 | **8/100** |
| Mediana fali porażki | 53 / 53 | 53 / 53 |
| Fala 20 | 100 / 100 | 100 / 100 |
| Średnia fala | 51,22 / 50,96 | 50,42 / 50,98 |

- Pierwsza wersja kart (−12%, +20%, 80 złota, +15% przez 8 s) dała 5 i 4/100 (słabe karty wypierały
  lepsze z ofert), więc wzmocniono je do wartości z tabeli wyżej.
- Siła kart (`newCardsEffect206`, 50 seedów, karta ×3 przy fali 5; średnia fala): bez karty 49,6;
  Szyk pik 49,8; Straż 48,9; Nagroda 50,5; Rogi 53,0 — dla porównania Mur tarcz 53,4, Saperzy 52,3,
  Pancerz piechoty 55,4. **Żadna nowa karta nie jest dominująca.**
- Zestawy tygodnia (mediana fali, 8 tygodni): Wojna błyskawiczna 9,0, Żelazny szyk 10,5; pasmo
  istniejących 8,5–13,0.
- Lustrzane bitwy AI vs AI (360 bitew): bez wydarzenia 52,5%, Dzień nauki 50,3%, Dzień furii 48,9%;
  mediany czasu bez zmian (12,8–13,9 min).

Nowe raporty/pomiary w `EndlessBalanceReportTest`: `ENDLESS100_FIRST_SEED` (próba kontrolna bez
edycji testu), `ENDLESS100_CARD206` (jak często AI bierze nowe karty), `newCardsEffect206`.

## Testy

- `:sim:test`: **339 testów, 0 błędów** (1 pominięty jak wcześniej); nowy `Content206Test` (19 testów:
  efekty kart z limitami, nagroda, okno Rogów, oferty, zrzut v21 i odczyt v16–v20, rotacja zestawów,
  dni sprzed `EVENTS_V4_DAY` bez zmian, brak konfliktów, mnożniki dni, AI).
- `:app` (Endless*, Weekly*, Daily*, LTest*, Achievement130Test, BattleHistoryTest, WhatsNewCatalogTest,
  ReleaseConfigTest, Content206ScreenshotTest): zielone.
- `:server:test`: 95 testów, 0 błędów (1 pominięty).

## Zrzuty

`/tmp/claude-0/d58/content206_{phone720,foldOuter}_{pl,de,fr,pt,es,uk}_{cards,cards2,scholars_day,fury_day}.png`
(48 plików; test `Content206ScreenshotTest`, języki przez `CW_TEXT_LANGS`). Opisy kart mieszczą się
(najdłuższe: francuski, 3 wiersze na 720p).

## Czego nie zrobiono

- Bez nowych osiągnięć (84 ID PGS bez zmian) i bez nowych typów misji.
- Bez pogody w Oblężeniu tygodnia, bez zmian krzywej fal i kampanii.
