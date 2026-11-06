# Dzień 62 — Rozkazy sztabu (tryb B z R14)

Decyzja właściciela 6.11: pierwsza wersja 9 rozkazów (po jednym na epokę), dla testerów przed
premierą (bez blokady `RELEASE_DAY`), medale jednorazowo za zdobyte gwiazdki, jeden nowy ranking
Play Games, bez serwera. Gałąź `claude/dzien-62-rozkazy`.

## Dla gracza

Menu → **Rozkazy** (w poziomie obok Misji, w pionie osobny kafel z licznikiem gwiazdek).
Lista 9 rozkazów; każdy otwiera kartę: epoka i warunki (pogoda, modyfikatory), odprawa,
cel główny, dwa cele dodatkowe, „Twoje siły”, „Wróg: tylko …”, medale do zdobycia, przycisk Graj.
Cel główny = 1★, każdy cel dodatkowy +1★ (liczone tylko przy wykonanym celu głównym).
Po bitwie panel wyników pokazuje czas, trzy cele (✓/✗), punkty sztabu i medale; Dalej wraca do
karty tego rozkazu. Rozkaz epoki odblokowuje się razem z epoką w kampanii (rozkaz 1 po etapie 1,
rozkaz epoki e po etapie 4e).

| # | Rozkaz (pl) | Epoka | Cel | Ograniczenia gracza | Wróg | Warunki | Cele dodatkowe |
|---|---|---|---|---|---|---|---|
| 1 | Pierwsza linia | Plemiona | utrzymaj 4:00 | bez wież | szturmowiec, siła 4,5 | — | baza ≥ 35%, ≥ 55 pokonanych |
| 2 | Szarża włóczników | Brąz | zniszcz w 6:00 | wręcz, dowódca, ostrzał, zryw | tylko łucznicy | kruche bazy | wygraj do 5:00, baza ≥ 20% |
| 3 | Żelazny deszcz | Żelazo | zniszcz w 6:00 | dystans, ciężcy, wieże, ostrzał, zryw, dowódca | tylko ciężcy | deszcz, opancerzony wróg, kruche bazy | baza ≥ 25%, ≤ 55 strat |
| 4 | Nocna warta | Feudalizm | utrzymaj 4:00 | — | siła 3 | noc, bez wież (obie strony) | baza ≥ 40%, ≥ 55 pokonanych |
| 5 | Wyścig prochu | Proch → Para | zniszcz w 6:00 | — | bez wież | kruche bazy | wygraj do 5:20, baza ≥ 25% |
| 6 | Bateria parowa | Para | utrzymaj 4:00 | dystans, wieże, ostrzał | siła 3 | — | baza ≥ 45%, ≤ 60 strat |
| 7 | Stalowa pięść | Stal | zniszcz w 5:00 | ciężcy, elity, dowódca, wieże, ostrzał, zryw | tylko piechota | mgła wojny | wygraj do 4:15, ≥ 150 pokonanych |
| 8 | Szybkie cięcie | Krzem | zniszcz w 4:00 | — | bez wież | szybkie wojska, szklane działa | baza ≥ 90%, ≤ 40 strat |
| 9 | Ostatni bastion | Orbitalna | utrzymaj 5:00 | — | oblegający, siła 2 | wiatr | baza ≥ 10%, ≥ 75 pokonanych |

Wszystkie rozkazy mają te same zamrożone bonusy (`Daily.normalizedBonuses`: armia 14, 3 sloty
wież, elity i zryw), więc badania nic nie zmieniają, a ranking porównuje umiejętności.

## Nagrody i ranking

- **Medale jednorazowo** za każdą nową gwiazdkę rozkazu: 10 / 15 / 20 (razem 45 za rozkaz, 405 za
  komplet). Powtórka bez nowej gwiazdki: 0 medali (także porażka i kapitulacja — `LostBattle`).
  Osiągnięcia i misje działają jak w innych trybach (misje nie liczą gwiazdek sztabu).
- **Ranking „Rozkazy sztabu — punkty” (`leaderboard_orders`)**: suma najlepszych punktów 9
  rozkazów; punkty za rozkaz = gwiazdki × 1000 + zdrowie bazy na końcu × 500 (0–500), czyli
  0–31 500 łącznie. Dlaczego nie sama suma gwiazdek: przy 27 możliwych wartościach większość
  graczy remisowałaby na kilku liczbach; gwiazdki nadal dominują (1★ zawsze > 0★ + pełne
  zdrowie), a zdrowie bazy rozstrzyga remisy i nagradza czystsze zwycięstwa bez dodatkowych
  medali. Wynik jest lokalny i liczony z symulacji (bez serwera), wysyłany do Play Games jak
  inne rankingi (`publishSavedScores`), plus lokalna tablica `orders` w profilu.
- Play Games: nowy klucz `leaderboard_orders=REPLACE_ME` w `docs/store/play_games_ids.properties`,
  generator ID (`buildSrc`), `PlayGamesIds`, `PlayGamesLeaderboards`, arkusz
  `docs/store/play_games_sheet.csv` (wiersz 5), `tools/pgs_sheet.py`, ikona `lb_orders`
  (`PgsIconsTest`). **Razem 85 ID** (było 84): projekt, 5 rankingów, 79 osiągnięć — dokumenty
  właściciela poprawione (`OWNER_CHECKLIST`, `OWNER_INPUT`, `PLAY_CONSOLE`, `PLAY_GAMES_SETUP`,
  `play_games_sheet.md`, `RELEASE`, `GDD`, `century-war.md`). Uwaga: przy regeneracji
  `pgs_import/AchievementsLocalizations.csv` zmieniły się nazwy, które już wcześniej zmieniono w
  `L.kt` (np. de „Neue Epoche”) — import był nieaktualny.
- Ekran Rozkazów ma przycisk „Ranking” (gdy Play Games jest dostępne; bez połączenia — logowanie).

## Zmiana `:sim` (PROTOCOL 0.8.11 → 0.8.12)

- `Mode.ORDERS` dopisany na końcu enuma.
- `BattleConfig`: `playerAllowed`, `enemyAllowed` (maska `OrderRules`: wręcz, dystans, ciężcy,
  elity, dowódca, wieże, ostrzał, zryw), `timeLimitSteps`, `holdWins` — wszystkie z wartościami
  domyślnymi (bez ograniczeń, bez limitu), więc inne tryby działają bez zmian.
- `Battle`: maska sprawdzana w `canQueue`, dowódcy, `abilityReady` (ostrzał); wieże/elity/zryw
  przez sloty i flagi strony przy starcie. Limit czasu: obrona wygrywa, atak przegrywa.
- `Ai`: przy ograniczeniu wybiera tylko dozwolone role (bez ograniczeń — identyczne losowanie).
- Presja oblężnicza Rozkazów: od 90 s, +80%/min (`Orders.SIEGE_START/RAMP`, tylko `Mode.ORDERS`).
  Bez niej 3–6-minutowa bitwa prawie nigdy nie dochodziła do bazy (AI zadawało 0–3% obrażeń bazie)
  a obrona była wygrana w 100%.
- `BattleSnapshot.VERSION` 21 → **22** (pola rozkazu w konfiguracji zapisywane tylko dla
  `Mode.ORDERS`; odczyt v16–v21 bez zmian). Plik powtórki (`ReplayRecord`) i `Replay.VERSION` bez
  zmian formatu dla dotychczasowych trybów. `ScoreWire` odrzuca ORDERS, `ScoreClient` ich nie wysyła.
- Złote powtórki zregenerowane: we wszystkich 10 zmienił się **wyłącznie `snapshotSha256`**
  (nagłówek v22); wynik, zwycięzca, kroki, zabójstwa — bez zmian. `SecondHourControlTest`: tylko
  skrót zrzutu.

## Zapis w profilu

`orderStars` i `orderPoints` (tablice po 9 pozycji, nowe rozkazy dopisywane na końcu). Starszy
profil bez tych pól = zera; wartości przycinane do zakresu (`OrdersProfileTest`).

## Balans (symulacja)

`OrdersBalanceReport` (`BALANCE_RUN=true`): AI gracza kontra AI wroga z tymi samymi
ograniczeniami, 40 seedów na rozkaz. AI to dolne oszacowanie przeciętnego gracza (jak w kampanii).
Cele R14: 1★ 50–80%, 3★ 10–35%, mediana 2–5 min, 0 patów.

| Rozkaz | Epoka | Cel | aiPower | N | ≥1★ | 2★+ | 3★ | cel dod. 1 | cel dod. 2 | mediana min | paty |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 first_line | 0 | HOLD 240 s | 4.50 | 40 | 80% | 53% | 28% | 35% | 45% | 4.00 | 0 |
| 2 shield_wall | 1 | DESTROY 360 s | 0.70 | 40 | 65% | 40% | 10% | 10% | 40% | 5.24 | 0 |
| 3 iron_rain | 2 | DESTROY 360 s | 0.70 | 40 | 78% | 43% | 18% | 30% | 30% | 5.29 | 0 |
| 4 night_watch | 3 | HOLD 240 s | 3.00 | 40 | 68% | 58% | 23% | 38% | 43% | 4.00 | 0 |
| 5 powder_race | 4 | DESTROY 360 s | 0.85 | 40 | 63% | 15% | 13% | 13% | 15% | 5.59 | 0 |
| 6 steam_battery | 5 | HOLD 240 s | 3.00 | 40 | 75% | 38% | 10% | 23% | 25% | 4.00 | 0 |
| 7 steel_fist | 6 | DESTROY 300 s | 1.40 | 40 | 73% | 73% | 10% | 43% | 40% | 4.49 | 0 |
| 8 blitz | 7 | DESTROY 240 s | 0.20 | 40 | 50% | 30% | 25% | 28% | 28% | 3.24 | 0 |
| 9 last_bastion | 8 | HOLD 300 s | 2.00 | 40 | 68% | 53% | 20% | 35% | 38% | 5.00 | 0 |

Wszystkie rozkazy w paśmie celu. Mediany ataku z limitem 6 min są nieco powyżej 5 min (5,2–5,6):
AI gracza gra ostrożnie; człowiek naciera szybciej. Ostrożność: `aiPower` w obronach (2–4,5) jest
wysoki, bo w 4 minuty mnożnik dochodu działa krótko — to nie jest „podwójny” wróg w odczuciu gracza.
Wartości są strojone pod próbę 40 seedów; po przyszłych zmianach jednostek test
`OrdersTest.everyOrderHasReachableThreeStars` pilnuje, że każdy rozkaz ma osiągalne 3★.

## Ekran i dostępność

- Lista: jedna kolumna (telefon, Fold zewnętrzny 904×2316, Fold rozłożony), przewijana, karta
  68–78 dp; karta rozkazu przewijana, gdy tekst nie mieści się przy 130%.
- `OrdersScreenTest`: telefon 1600×720, Fold zewnętrzny 904×2316, Fold rozłożony 2208×1840,
  100% i 130% tekstu, pl/en/de — lista, karta rozkazu, wyniki: wszystkie napisy ≥ 10 sp, bez
  nachodzenia, wyniki w obrębie ekranu. W wynikach Rozkazów nie ma podpowiedzi „następny cel”
  (miejsce na trzy cele); podpis wiersza wyników zmniejsza się obok długiej wartości (≥ 10 sp) —
  wcześniej de „Für die Schlacht” nachodziło na „+20 Medaillen” przy 130% na zewnętrznym Foldzie.
- TalkBack: tytuł ekranu, standardowe etykiety kart (nazwa + podpis + gwiazdki), jeden węzeł
  informacji (nagłówek albo treść karty), przewijanie — jak na innych listach, nic ponad.
- Powiadomienia: ekran blokuje banery (`blocksNotices`), wyniki nie wysyłają powiadomień.
- `compactTop` i celowanie ostrzału bez zmian.

## Teksty

`part209` w `L.kt`, 47 kluczy × 10 języków (nazwy i odprawy rozkazów, cele, siły, ranking,
`board_orders`). Nazwy własne, bez nawiązań do innych gier. `putAll(part` = `private fun part` (81).

## Testy

- `:sim`: `OrdersTest` (9 testów: kolejność i dane, wartości domyślne, maski, AI, limit czasu,
  medale/punkty/odblokowanie, osiągalne 3★, zrzut v22 i plik powtórki), wersje 22 w testach zrzutu.
- `:app`: `OrdersScreenTest`, `OrdersProfileTest`, `LTest.orders209KeysHaveAllLanguages`,
  `PlayGamesIdsTest` (5 rankingów), `LostBattleTest`, `ReleaseConfigTest` (0.8.12).
- `:server`: wersja 0.8.12 w testach.

## Zrzuty

`/tmp/claude-0/d62/d62_<urządzenie>_<skala>_<język>_{list,order,result}.png`
(phone720 i foldOuter przy 100% i 130%, foldInner przy 100%; pl/en/de).

## Otwarte

- ID `leaderboard_orders` do utworzenia w Play Console (razem z pozostałymi 84).
- „Co nowego” i osiągnięcia za Rozkazy — nie dodane (poza zakresem decyzji).
- Kolejne partie rozkazów dopisywać na końcu `Orders.all` (indeks = zapis w profilu).
