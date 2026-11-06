# D67: osiągnięcia i medale Rozkazów po 2. partii

Gałąź `claude/dzien-67-rozkazy-medale` (baza `integ/tura10` = d65 + d66). Decyzje właściciela z 6.11.

## 1. „Weteran sztabu” i „Marszałek polny” liczą wszystkie 18 rozkazów

Po scaleniu d65 (osiągnięcia z progami 9 / 27★) z d66 (18 rozkazów) padał
`OrdersAchievements65Test` (próg 9 ≠ `Orders.COUNT` = 18).

| id (bez zmian — ID Play Games i zapisu) | Warunek | Medale | Punkty PGS |
|---|---|---|---|
| `orders_all_9` — Weteran sztabu | ≥ 1★ we wszystkich **18** rozkazach | 150 → **300** | 15 → **25** |
| `orders_stars_27` — Marszałek polny | komplet **54★** | 450 → **900** | 30 → **40** |

- `meta/Achievements.kt`: progi to teraz `Orders.COUNT.toLong()` i
  `Orders.COUNT * Orders.MAX_STARS.toLong()` — rosną same z liczbą rozkazów (jak `stars_96`
  z `Campaign.stages.size`). Opisy i arkusz PGS mają jednak liczby wpisane na sztywno, więc
  `OrdersAchievements65Test.definitionsTextsAndThresholds` sprawdza, że każdy z 10 opisów zawiera
  bieżący próg (oraz wprost 18 / 54 i medale 300 / 900). Przy partii 3 test padnie i wymusi
  poprawę tekstów i przegenerowanie arkusza.
- Odblokowane wcześniej osiągnięcie zostaje odblokowane (zapis jednorazowy); d65 i d66 nie były
  jeszcze wydane, więc w praktyce nie dotyczy graczy.
- `L.kt` part210: poprawione opisy `ach_orders_all_9_d` (18) i `ach_orders_stars_27_d` (54)
  w 10 językach (pl „wszystkie 54 gwiazdki”, uk „усі 54 зірки” — liczebnik z 4 na końcu).
- `tools/pgs_sheet.py`: progi Rozkazów czytane ze stałych `COUNT`/`MAX_STARS` w `Orders.kt`.
  Suma punktów PGS 1585 → **1605** (+10 i +10), więc własny budżet 1600 → **1650**
  (limit Google 2000, zapas 395). Przegenerowane `docs/store/play_games_sheet.csv` oraz
  `docs/store/pgs_import/AchievementsMetadata.csv` i `AchievementsLocalizations.csv`; kolejność
  listy (wg medali) przesunęła się o jedno miejsce dla pozycji 41–75. Nazwy osiągnięć bez zmian,
  więc mapowanie ikon (`docs/store/assets/pgs/AchievementsIconsMappings.csv`) bez zmian.
  `play_games_sheet.md` i raport d65 — dopiski.

## 2. Medale za gwiazdki w 2. partii: 15/20/30

- `sim/.../Orders.kt`: `STAR_MEDALS_BATCH2 = [15, 20, 30]`, `starMedals(order)` wybiera tabelę
  wg `order.batch`; `medalsFor(order, old, new)` (nowy parametr rozkazu). Partia 1 bez zmian
  (10/15/20, 45 za rozkaz, 405 za partię); partia 2: 65 za rozkaz, 585 za partię; razem 990.
- Nadal jednorazowo: medale tylko za nowe gwiazdki (`Profile.recordOrder`, który teraz bierze
  rozkaz z `Orders.all[index]`; zły indeks = 0 medali).
- Karta rozkazu (`OrdersScreen`) liczy „Medale za nowe gwiazdki: N” z `medalsFor(o, …)`, więc
  dla rozkazów 10–18 pokazuje 65 (lub resztę). Panel wyników bitwy pokazuje wynik `recordOrder`.
- **Symulacja bez zmian**: medale żyją tylko w `Orders` jako tabela używana przez profil i ekran;
  nie wchodzą do `BattleConfig`, kroku bitwy, zrzutu ani powtórki. PROTOCOL zostaje 0.8.13.
- Raport d66 — dopisek przy medalach i przy pytaniu do właściciela.

## Testy

- `OrdersTest.starsMedalsPointsAndUnlocks` (sim): tabele obu partii dla każdego rozkazu,
  15/65/30/0 dla rozkazu 10, suma 990.
- `OrdersProfileTest`: rozkaz 10 — 15 za 1★, potem 50 za 2★ i 3★, powtórka 0.
- `OrdersScreenTest`: `medalsFor` z rozkazem.
- `OrdersAchievements65Test`: progi 18 / 54, medale 300 / 900, liczby w 10 opisach, postęp 20
  (3★ + 17 × 1★).

Wyniki pełnych przebiegów — niżej.
