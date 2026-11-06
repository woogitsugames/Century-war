# Dzień 66 — Rozkazy sztabu, partia 2 (rozkazy 10–18)

Decyzja właściciela 6.11: druga partia 9 rozkazów, trudniejsza niż partia 1, z nowymi rodzajami
celów. Gałąź `claude/dzien-66-rozkazy-2`. Podstawa: `docs/reports/d62-rozkazy.md`,
`docs/research/R14-nowy-tryb.md` (propozycja B).

## Dla gracza

Lista Rozkazów ma teraz 18 pozycji (przewijana; gwiazdki sztabu x/54). Nowe rozkazy dopisano na
końcu `Orders.all` (indeksy 9–17 = rozkazy 10–18), więc zapis w profilu i powtórki partii 1 są
nienaruszone. Ikona na liście: tarcza (obrona), miecze (atak), **hełm (eskorta dowódcy)**.

| # | Rozkaz (pl) | Epoka | Cel | Ograniczenia / warunki | Cele dodatkowe |
|---|---|---|---|---|---|
| 10 | Straż wodza | Plemiona | **eskorta**: baza i dowódca 3:30 | bez wież, szturmowiec, siła 4,7 | baza ≥ 57%, ≥ 52 pokonanych |
| 11 | Brązowe wieże | Brąz | utrzymaj 3:30 | **tylko wieże i ostrzał** (bez jednostek) | baza ≥ 40%, ≥ 36 pokonanych |
| 12 | Chuda zima | Żelazo | utrzymaj 4:00 | **dochód 50%**, siła 3,2 | **zachowaj ≥ 100 złota**, **wystaw ≤ 70 jednostek** |
| 13 | Nocny herold | Feudalizm | **grot**: zniszcz w 6:00 bez utraty dowódcy | noc | wygraj do 5:15, ≤ 80 strat |
| 14 | Mokry proch | Proch | zniszcz w 5:15 | deszcz, kruche bazy | **wystaw ≤ 88**, **dowódca przeżywa** |
| 15 | Para pod presją | Para | utrzymaj 4:30 | **dochód 75%**, siła 2,8 | **zachowaj ≥ 250 złota**, ≥ 70 pokonanych |
| 16 | Konwój we mgle | Stal | **eskorta** 4:00 | ciężcy, elity, dowódca, wieże, ostrzał, zryw; mgła | baza ≥ 42%, ≤ 31 strat |
| 17 | Słaby sygnał | Krzem | zniszcz w 5:30 | **dochód 70%**, wiatr | **zachowaj ≥ 1000 złota**, wygraj do 4:45 |
| 18 | Ostatni grot | Orbitalna | **grot**: zniszcz w 6:00 bez utraty dowódcy | oblegający, wiatr | baza ≥ 30%, **wystaw ≤ 82** |

Medale i punkty jak w partii 1 (10/15/20 za nowe gwiazdki, gwiazdki × 1000 + zdrowie bazy × 500).
Nie podnosiłem medali za partię 2 — pytanie do właściciela niżej.

## Nowe rodzaje celów (`:sim`)

| Rodzaj | Gdzie | Jak działa | Domyślnie |
|---|---|---|---|
| `Goal.ESCORT` (eskorta) | rozkazy 10, 16 | gracz zaczyna z darmowym dowódcą (pierwszy krok); upływ limitu = zwycięstwo; **śmierć dowódcy = porażka** | — |
| `Goal.SPEARHEAD` (grot) | 13, 18 | jak ESCORT, ale trzeba zburzyć bazę w limicie | — |
| obrona samymi wieżami | 11 | maska `OrderRules.TURRETS or BARRAGE` (bez ról); AI bez ról nie szkoli, buduje wieże | — |
| obcięty dochód `Order.incomePct` | 12, 15, 17 | `Bonuses.incomeMul` zamrożonych bonusów × %; pole bonusów już było w zapisie | 100 |
| `ObjectiveKind.COMMANDER_ALIVE` | 14 | dowódca gracza żyje na końcu (`commanderCd == -1`) | — |
| `ObjectiveKind.GOLD_LEFT` | 12, 15, 17 | w skarbcu na końcu ≥ X złota | — |
| `ObjectiveKind.FIELDED_MAX` | 12, 14, 18 | `Side.spawned` ≤ X (z dowódcą i najemnikami) | — |

Mechanika eskorty (`BattleConfig.orderFlags`, bit `OrderFlags.ESCORT`, domyślnie 0):
- w pierwszym kroku bitwy, przed decyzjami AI, `spawnCommander(player)` bez kosztu;
- eskortowany dowódca **zostaje za linią obrony** (najdalej `Battle.ESCORT_LINE` = 260 od bazy,
  linia „trzymaj” to 430) i rusza tylko wtedy, gdy przed nim stoi własny żołnierz
  (`ESCORT_GAP` = 40); żołnierze gracza przechodzą obok niego (nie blokuje posiłków);
- przegrana, gdy `player.commanderCd != -1` (po śmierci dowódca odlicza odnowienie) — bez nowego
  stanu w zrzucie.
Bez tych reguł dowódca szedł z pierwszą falą albo stał na linii i blokował własne posiłki — AI
traciło go w 25–70 s w 100% bitew (pomiar przed poprawką).

Poza `Mode.ORDERS` z flagą eskorty nic się nie zmienia (wartości domyślne), co potwierdzają
złote powtórki.

Odrzucone: „zadaj bazie wroga X% w obronie” (AI gracza w obronie nie kontratakuje — 0% w każdej
próbie, cel niemierzalny na symulacji), „zabójstwa wież” (wymagałoby nowego licznika w zrzucie).

## Odblokowanie partii 2

Rozkaz partii 2 epoki *e* odblokowuje się po zdobyciu **2★ w rozkazie partii 1 tej samej epoki**
(`Orders.BATCH2_UNLOCK_STARS`, `Orders.prerequisite`); rozkaz partii 1 sam wymaga epoki w kampanii,
więc partia 2 nigdy nie wyprzedza kampanii. Dlaczego tak, a nie „ukończ kampanię epoki”:
- jasny, lokalny warunek widoczny na karcie: „Zdobądź 2★ w rozkazie „Pierwsza linia””;
- 2★ oznacza, że gracz opanował zasady danej epoki w łatwiejszej wersji (partia 2 jest trudniejsza);
- nie blokuje partii 2 graczom, którzy utknęli w kampanii, ale grają Rozkazy dobrze.
`Orders.unlocked(order, campaignStars, orderStars)` — nowa sygnatura (dodane gwiazdki rozkazów).

## Zmiana `:sim` (PROTOCOL 0.8.12 → 0.8.13)

- `BattleConfig.orderFlags: Int = 0` (+ `escort`), `OrderFlags`.
- `Battle`: start eskorty, porażka po śmierci dowódcy, ruch eskortowanego dowódcy.
- `Orders`: `Goal.ESCORT/SPEARHEAD` (`holdWins`, `escort`), nowe `ObjectiveKind`, `incomePct`,
  `batch`, prefiks tekstów `o211_` dla partii 2, `COUNT = 18`, `BATCH = 9`, `MAX_POINTS`,
  `prerequisite`, `commanderAlive`.
- `BattleSnapshot.VERSION` 22 → **23**: bajt `orderFlags` w konfiguracji tylko dla `Mode.ORDERS`;
  zrzuty v22 (także rozkazów partii 1) wczytują się z `orderFlags = 0` (test).
- `ReplayRecord.VERSION` 2 → **3** (ten sam bajt w pliku powtórki); v1–v2 czytane jak dotąd.
- `Replay.VERSION` i `RANKED_REPLAY_FORMATS` bez zmian; serwer tylko wersja w testach.
- Złote powtórki zregenerowane (`goldenReason` D66): we wszystkich 10 zmienił się **wyłącznie
  `snapshotSha256`** (nagłówek v23); wynik, zwycięzca, kroki, zabójstwa — bez zmian.
  `SecondHourControlTest`: tylko skrót zrzutu.

## Profil

`orderStars`/`orderPoints` mają teraz 18 pozycji. Odczyt kopiuje `min(długość, 18)`, więc profil
z partii 1 (9 pozycji) wczytuje się bez strat, a rozkazy 10–18 mają zera (`OrdersProfileTest`).
Zapis zawsze 18 pozycji.

## Ranking

`leaderboard_orders` liczy sumę najlepszych punktów **wszystkich** rozkazów: 0–63 000
(18 × 3500). Zaktualizowano opisy: `tools/pgs_sheet.py`, `docs/store/play_games_sheet.csv`,
`docs/PLAY_GAMES_SETUP.md`. ID bez zmian.

## Balans (symulacja)

`OrdersBalanceReport` (AI gracza kontra AI wroga, te same ograniczenia, 40 seedów). Cel partii 2
(trudniej niż partia 1): 1★ 40–70%, 3★ 5–25%, 0 patów.

BALANCE_TABLE

Strojenie: `ORDERS_SWEEP` przyjmuje teraz mnożniki względne (`x0.85,x1,x1.15`), a statystyki
wygranych pokazują wystawione jednostki i złoto na końcu (progi `FIELDED_MAX` i `GOLD_LEFT`).
Uwaga: w obronach z niskim dochodem AI gracza wydaje prawie wszystko — człowiek, który odkłada
złoto, łatwiej zdobędzie cel „zachowaj złoto”; AI to dolne oszacowanie.

## Ekran i dostępność

- Lista 18 kart, przewijana; podpis zablokowanej karty partii 2 mówi, ile gwiazdek i w którym
  rozkazie (`o211_locked`); TalkBack czyta ten podpis.
- Karta: warunki z „dochód 50%”, nowe cele i cele dodatkowe (`OrderTexts`).
- Wyniki: te same trzy cele (✓/✗); bez powiadomień (ekran i wyniki jak w partii 1).
- `OrdersScreenTest.batchTwoListAndCardsReadableWithoutOverlap`: telefon 1600×720, Fold
  zewnętrzny, Fold rozłożony, 100% i 130%, pl/en/de — lista przewinięta do końca i karty 10, 12,
  18: napisy ≥ 10 sp, bez nachodzenia.
- `compactTop` i celowanie ostrzału bez zmian.

## Teksty

`part211` w `L.kt` (part210 zajęta): 25 kluczy × 10 języków — 9 nazw i odpraw, cele eskorty i
grotu, 3 cele dodatkowe, dochód, warunek odblokowania. Nazwy własne, bez nawiązań do innych gier.
`LTest.orders211KeysHaveAllLanguages`.

## Zrzuty

`/tmp/claude-0/d66/d66_{phone720,foldOuter}_{100,130}_{pl,en}_{list2,order10,order12,order18}.png`
(lista przewinięta do partii 2 i trzy nowe karty: eskorta, chudy dochód, grot).

## Testy

TEST_RESULTS

## Otwarte / pytania do właściciela

- Medale za partię 2: takie same jak w partii 1 (10/15/20). Czy podnieść (np. 15/20/25) za
  trudniejsze rozkazy?
- Osiągnięcia za Rozkazy i „Co nowego” — nadal poza zakresem.
- `century-war.md` i `COORDINATOR_HANDOVER.md` opisują wersje — do aktualizacji przez koordynatora
  przy scalaniu (0.8.13, zrzut v23, ReplayRecord v3).
