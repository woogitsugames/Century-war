# Audyt 73 — obszar C: ekonomia i postęp (medale, badania, nagrody)

Gałąź `claude/audyt-73-ekonomia` z `origin/main` (b9d84eed). **Ekonomia w kodzie bez zmian** —
tylko pomiar i propozycje z liczbami do decyzji właściciela. Nie znalazłem błędu typu „nagroda
naliczana dwa razy”, więc nie ma poprawek w grze.

Narzędzie: `app/src/test/java/com/centurywar/meta/EconomyCurveReport.kt` (tylko liczenie,
uruchamiane na żądanie):

```
ECONOMY_REPORT=true ./gradlew :app:testDebugUnitTest --tests com.centurywar.meta.EconomyCurveReport.curve
ECONOMY_REPORT=true ECONOMY_RUNS=24 ECONOMY_STAGES=12,28 ./gradlew :app:testDebugUnitTest --tests com.centurywar.meta.EconomyCurveReport.researchFeel
```

Wyniki: `app/build/balance/economy-curve.md`, `economy-research.md`.

## 1. Skąd są medale (z kodu) i na co je wydać

Medale wydaje się **tylko na badania** (banery są za osiągnięcia/gwiazdki, nie za medale).
Badania: 13 pozycji, 110 poziomów, razem **44 789 medali**; 43% tej sumy to `r_unitDmg` i
`r_unitHp` (po 9 684, poziom 15 kosztuje 2 438 za +4%).

| Źródło | Wzór w kodzie | Typowo | Powtarzalne? |
|---|---|---:|---|
| Kampania, wygrana | `(20 + 6·etap + 5·★ + 12·nowe★) × trudność` (Łatwy 0,75 / Normalny 1 / Trudny 1,3), `BattleScreen.settleRewards` | etap 1: 71; etap 36 na 3★: 245 | **tak, bez limitu** |
| Kampania, porażka | `((5 + 2·etap)·wysiłek + min(20, zabici/8)) × trudność`, `LostBattle.lossReward` | 13–83 | tak |
| Korona (Trudny) | brak własnej nagrody; tylko ×1,3 i osiągnięcie `campaign_hard` (600) po **wszystkich 36** | 0 | — |
| Logowanie | 5/8/12/17/23/30/40 + ★/16, przerwa = seria −2 (`Missions.loginRewardFor`) | 5–46/dzień | 1×/dzień |
| Powitanie po przerwie | 30 po ≥ 3 dniach (`WelcomeBack.BONUS`) | 30 | rzadko |
| Misje dzienne (3) | `10 + 2·bonus + ★/12` | 12–25 szt. | 1×/dzień |
| Misje tygodniowe (3) | `34 + 4·bonus + ★/3` | 38–82 szt. | 1×/tydz. |
| Wyzwanie dnia (ranking) | `min(90, wynik/350) + 10`; wynik wygranej ≈ 10 000 + HP + szybkość + zabici ≈ 17–21 tys. | ≈ 61 (wygrana), 15–20 (porażka) | 1×/dzień |
| Oblężenie tygodnia | `(3·fala + zabici/10)/2` + progi fal 10/20/30 → 25/60/120 raz w tygodniu | ≈ 22 + 25 | próg 1×/tydz. |
| Endless | `3·fala + zabici/10`; kamienie milowe = tylko wstążka rekordu i osiągnięcia fali 10/25/50/100 | fala 23 → 75 | tak |
| Potyczka rankingowa | wygrana `15 + 14·preset` (15/29/43/57) | | tak |
| Rozkazy sztabu | nowe ★: 10/15/20 (partia 1), 15/20/30 (partia 2); razem 990 | jednorazowo | nie |
| Osiągnięcia | 83 szt., razem 16 835 | jednorazowo | nie |

Uwaga: badania działają w kampanii, Endless i potyczce; **wyzwanie dnia, Oblężenie tygodnia i
Rozkazy mają stałe bonusy** (`Daily.normalizedBonuses`: limit 14, 3 sloty, elita i zryw) —
tam badania nic nie dają (celowo, ranking).

## 2. Krzywa medali w czasie (model przeciętnego gracza, godziny 1–20)

Założenia (z raportów, nie z ludzi): 45 min dziennie; czas etapów 1–18 = `minutesNow`
(BALANCE.md, z porażkami), 19–27 Σ 129 min, 28–36 Σ 160 min (d37 „realny gracz” po K1);
★ z `starsNow`, później 1,6; wygrane 85% / 70% / 72% / 52%. Każdego dnia: logowanie, 1 próba
wyzwania dnia (55% wygranych), 2 z 3 misji dziennych, 2 z 3 tygodniowych w tygodniu, raz w
tygodniu Oblężenie (fala 12 = mediana z d37, brąz w 60% tygodni), 1 próba Rozkazu (do 2★,
co trzecia próba 3★), co 3. dzień Endless (fala 18 + poziomy/3), reszta czasu kampania (po
Normalnym — Trudny). Zakupy: najtańsze dostępne badanie od razu, gdy stać (tak jak podpowiada
`NextGoals.affordableResearch`). Osiągnięcia liczone tylko te, które da się policzyć z liczników
(41 z 83); 42 „za umiejętność” (7 750 medali) poza modelem — to zapas, nie przychód.

| h | Kampania | Trudny | Login | Misje | Wyzw. dnia | Tydz. | Endless | Rozkazy | Osiągn. | **Σ** | Wydane | Saldo | Poziomy | Zakupy w h | Najdłuższe czekanie [min] | Nast. koszt | Postęp |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 163 | 0 | 13 | 25 | 41 | 0 | 0 | 25 | 110 | **377** | 330 | 47 | 8 | 8 | 18 | 50 | etap 3 |
| 2 | 234 | 0 | 12 | 25 | 41 | 37 | 87 | 25 | 110 | **572** | 524 | 95 | 17 | 9 | 13 | 70 | etap 6 |
| 3 | 299 | 0 | 17 | 25 | 41 | 0 | 0 | 35 | 505 | **922** | 1003 | 14 | 29 | 12 | 16 | 100 | etap 9 |
| 4 | 243 | 0 | 54 | 54 | 82 | 0 | 0 | 60 | 100 | **594** | 515 | 93 | 34 | 5 | 16 | 120 | etap 11 |
| 5 | 266 | 0 | 41 | 27 | 41 | 0 | 110 | 25 | 290 | **801** | 794 | 100 | 40 | 6 | 20 | 140 | etap 13 |
| 6 | 287 | 0 | 24 | 120 | 41 | 0 | 0 | 35 | 175 | **682** | 716 | 66 | 45 | 5 | 21 | 181 | etap 15 |
| 7 | 153 | 0 | 72 | 58 | 82 | 37 | 125 | 60 | 85 | **673** | 548 | 191 | 48 | 3 | 31 | 186 | etap 16 |
| 8 | 515 | 0 | 41 | 29 | 41 | 0 | 0 | 35 | 180 | **841** | 952 | 80 | 53 | 5 | 35 | 200 | etap 19 |
| 9 | 181 | 0 | 42 | 29 | 41 | 0 | 136 | 25 | 0 | **454** | 440 | 94 | 55 | 2 | 30 | 245 | etap 20 |
| 10 | 382 | 0 | 84 | 60 | 82 | 0 | 0 | 55 | 0 | **664** | 494 | 265 | 57 | 2 | 31 | 249 | etap 22 |
| 11 | 410 | 0 | 55 | 136 | 41 | 0 | 144 | 25 | 40 | **850** | 752 | 363 | 60 | 3 | 30 | 253 | etap 24 |
| 12 | 665 | 0 | 32 | 31 | 41 | 0 | 0 | 35 | 0 | **805** | 1057 | 110 | 64 | 4 | 31 | 284 | etap 27 |
| 13 | 271 | 0 | 84 | 62 | 82 | 37 | 0 | 25 | 0 | **562** | 614 | 58 | 66 | 2 | 42 | 331 | etap 28 |
| 14 | 279 | 0 | 42 | 31 | 41 | 0 | 152 | 35 | 0 | **580** | 331 | 308 | 67 | 1 | 52 | 331 | etap 29 |
| 15 | 581 | 0 | 43 | 33 | 41 | 0 | 0 | 0 | 170 | **869** | 1019 | 157 | 70 | 3 | 36 | 345 | etap 31 |
| 16 | 302 | 0 | 69 | 179 | 82 | 0 | 155 | 25 | 0 | **813** | 721 | 249 | 72 | 2 | 51 | 381 | etap 32 |
| 17 | 628 | 0 | 33 | 33 | 41 | 0 | 0 | 35 | 300 | **1071** | 1169 | 151 | 75 | 3 | 36 | 440 | etap 34 |
| 18 | 326 | 0 | 43 | 33 | 41 | 37 | 163 | 20 | 0 | **663** | 440 | 374 | 76 | 1 | 54 | 440 | etap 35 |
| 19 | 334 | 0 | 86 | 66 | 82 | 0 | 0 | 20 | 250 | **839** | 886 | 327 | 78 | 2 | 54 | 468 | etap 36 ✓ |
| 20 | 0 | 111 | 43 | 33 | 41 | 0 | 167 | 0 | 0 | **396** | 468 | 254 | 79 | 1 | 69 | 468 | 2 korony |

(„Login” obejmuje powitanie po przerwie; wahania godzin wynikają z podziału na dni po 45 min.)

**Suma 20 h: 14 027 medali** → 79 ze 110 poziomów (≈ 31% kosztu badań zostaje na później).
Udział: kampania 46%, osiągnięcia 17%, Endless 9%, wyzwanie dnia 8%, logowanie 6%, misje
dzienne 6%, Rozkazy 4%, misje tygodniowe 2%, Oblężenie tygodnia 1%, Trudny 1% (dopiero od
h 19), powitanie po przerwie 0,2%.

Wrażliwość: 30 min/dzień — 12 833 medali, etap 25 w h 20, udział kampanii 26%, a „dziennych”
źródeł (login + misje + wyzwanie) 37%; 90 min/dzień — kampania skończona w h 12,8, potem
Trudny (15% przychodu), 77 poziomów w h 20.

### Medale na minutę (źródła powtarzalne)

| Źródło | Medale / próba | Min / próba | **Medale / min** |
|---|---:|---:|---:|
| Powtórka etapu 6 na 3★, Normalny | 59 | 7,4 | 7,9 |
| Powtórka etapu 12, Normalny | 81 | 9,2 | 8,9 |
| Powtórka etapu 18, Normalny | 110 | 10,9 | 10,1 |
| Powtórka etapu 27, Normalny | 156 | 10,3 | 15,1 |
| Powtórka etapu 36, Normalny (p 52%) | 167 | 9,2 | 18,2 |
| Powtórka etapu 36, **Łatwy** (p ≈ 90%) | 172 | 7,9 | **21,7** |
| Powtórka etapu 36, **Trudny** (p ≈ 36%) | 184 | 10,6 | 17,4 |
| Endless (dowolna fala, 0,42 min/fala) | 3,8/fala | | 9,0 |
| Potyczka rankingowa preset 0 / 1 / 2 / 3 | 15 / 29 / 43 / 57 | 8 | 1,9 / 3,6 / 5,4 / 7,1 |
| Wyzwanie dnia (wygrana, 1×/dzień) | 61 | 8 | 7,6 |

## 3. Odpowiedzi na pytania

**Czy w każdej godzinie jest co odblokować i realny cel?** Tak w godzinach 1–12: 2–12 zakupów
na godzinę, najdłuższe czekanie 13–35 min, co godzinę nowy etap (2–3), Rozkazy, osiągnięcia.
Od ok. **h 13 zakupy rzedną do 1–2 na godzinę** (czekanie 42–69 min), każdy to +4–8% jednej
statystyki — cel jest, ale słabo odczuwalny (problem 1). Po skończeniu Normalnego (h 19 przy
45 min/dzień, h 13 przy 90 min/dzień) celem jest Trudny, ale ten nie płaci (problem 2).

**Czy jest moment, gdy nie ma nic do kupienia?** Nie w 20 h — badań starcza na ok. 50–60 h
(44,8 tys. przy 600–1 300/h). Nie ma też „pustych” przestojów > 70 min. Ryzyko jest odwrotne:
koniec drzewa jest daleko i drogi (problem 1).

**Czy któreś źródło jest za hojne?** Żadne nie psuje postępu w pierwszych 20 h. Najmocniejsze
jest nielimitowane powtarzanie ostatniego etapu, a najlepiej płaci **Łatwy** (21,7/min) — to
nie rozwala ekonomii (Łatwy daje ok. 1 300/h, drzewo i tak wymaga > 20 h), ale uczy, że
opłaca się grać łatwiej (problem 2). Skok w h 3 (505 z osiągnięć: `kills_1000`, `stars_24`,
`research_10`, `win_10`, `battles_25`…) jest dobry — gracz kupuje wtedy 12 poziomów.

**Czy jakieś źródło jest martwe?** Prawie: Oblężenie tygodnia (1%, problem 3), powitanie po
przerwie (0,2%, problem 4), Trudny/korony (problem 2), sufit 90 w wyzwaniu dnia (problem 5),
potyczka rankingowa na presetach 0–1 (1,9–3,6/min, poniżej każdego innego trybu).

**Czy badania zmieniają rozgrywkę odczuwalnie?** — sekcja 5 (pomiar).

## 4. Problemy od najważniejszych

### P1. Druga połowa drzewa badań: drogo i mało odczuwalnie
Dowód: od h 13 1–2 zakupy/h, najdłuższe czekanie 42–69 min, następny koszt 331–468 (tabela).
Do pełnego drzewa brakuje po 20 h ok. 31 tys. medali; 43% całej sumy to `r_unitDmg`/`r_unitHp`,
których poziomy 10–15 kosztują 787–2 438 za +4%. Gracz „robi postęp” liczbowo, ale go nie widzi.
**Propozycja (do wyboru):**
- (a) `r_unitDmg` i `r_unitHp`: max 15 → 10, efekt +4% → +6% na poziom (sufit +60% bez zmian),
  wzrost 1,33 → 1,40. Koszty 45…(45·1,4⁹ = 932), suma 3 662 zamiast 9 684 każde — drzewo
  44,8 → ok. 32,7 tys., każdy późny zakup = +6% (widać w liczbach obrażeń/HP).
- (b) bez ruszania kosztów: „kamienie” drzewa — co 25 kupionych poziomów jednorazowa premia
  rozgrywkowa (np. +1 limit armii przy 25, 3. slot wieży przy 50) zamiast medalowej;
  wymaga nowych tekstów (part221).
Uwaga: zmiana mocy badań przesuwa balans kampanii 19–36 (pomiar `medale-czas`), więc (a)
tylko z ponownym pomiarem d37.

### P2. Trudność odwrócona: Łatwy płaci najlepiej, korona nie płaci nic
Dowód: powtórka etapu 36 — Łatwy 21,7/min, Normalny 18,2/min, Trudny 17,4/min (tabela). Korona
(`recordHardVictory`) nie ma nagrody; jedyna premia to `campaign_hard` 600 po **36/36**
koronach; w modelu 90 min/dzień gracz zdobywa 15 koron w 7 h — 0 medali za cel.
**Propozycja:** (1) nowa korona = jednorazowo `10 + 2·etap` medali (Σ 36 koron = 1 620);
(2) Trudny ×1,3 → ×1,5 (etap 36: 20,0/min); (3) Łatwy przy powtórce bez nowych ★ ×0,75 → ×0,6
(etap 36: 17,4/min). Kolejność po zmianie: Trudny 20,0 > Normalny 18,2 > Łatwy 17,4.
Opcjonalnie pośrednie osiągnięcia 12/24 koron.

### P3. Oblężenie tygodnia: progi praktycznie nieosiągalne, podpowiedź wskazuje cel nie do zdobycia
Dowód: d37 (AI na zasadach tygodnia, 52 tygodnie): mediana fali 12; próg 10/20/30 osiągnięty w
31 / 5 / 2 tygodniach. Model: 148 medali w 20 h (1%). `NextGoals.pick` po skończeniu kampanii
pokazuje „dojdź do fali 20/30” — przy 10% / 4% szans. Osiągnięcie `weekly_gold` (200) prawie
martwe. Oblężenie ma stałe bonusy, więc badania gracza nie pomagają.
**Propozycja:** progi 10/20/30 → **8/14/22** przy tych samych nagrodach 25/60/120 (wg rozkładu
d37: ok. 75% / 30% / 10% tygodni), albo zostawić progi i dodać 2 medale za każdą falę ponad
10 raz w tygodniu. Opis progów jest w tekstach — przy zmianie sprawdzić `L.kt`.

### P4. Powitanie po przerwie: 30 medali to 3–5% godziny gry
Dowód: przychód 450–1 070/h od h 4; 30 medali = 0,2% przychodu 20 h, mniej niż 7. dzień
logowania (40+). Powracający gracz nie ma za co wrócić do badań (następny koszt 250–470).
**Propozycja:** `30 → 40 + 4·ukończone etapy`, max 150 (etap 12: 88, etap 27+: 148) — wystarcza
na 1 zakup z drugiej połowy drzewa. Koszt: przy 1 powrocie na 2 tygodnie ≈ +1% przychodu.

### P5. Wyzwanie dnia: sufit 90 martwy, a dzień „opłaca się” tylko raz
Dowód: `min(90, wynik/350)+10` — sufit wymaga 31 500 pkt; wygrana daje 17–21 tys.
(10 000 + HP ≤ 5 000 + szybkość ≤ 6 000 − 4·s + 10·zabici), więc realnie 58–70; porażka
(`killValue/4`) 15–20. W modelu wyzwanie = 8% przychodu. Nie szkodzi, ale nagroda nie rośnie z
umiejętnością tak, jak sugeruje wzór.
**Propozycja:** dzielnik 350 → 250 (wygrana 78–94, sufit przy 22 500 — osiągalny przy szybkiej
wygranej bez strat). Koszt ≈ +25 medali/dzień.

### Pomniejsze
- **Potyczka rankingowa preset 0–1:** 1,9–3,6 medala/min, najsłabsze źródło; jeśli ma być
  alternatywą dla kampanii: `15 + 14·preset` → `25 + 14·preset`.
- **Misje i logowanie** (razem ok. 14% przy 45 min, 26% przy 30 min) — w normie; skalowanie
  od ★ działa. Bez zmian.
- **Rozkazy sztabu:** 990 jednorazowo + 300/900/40/150 z osiągnięć — dobre „cele z boku”
  w h 1–19. Bez zmian.
- **Osiągnięcia** — 17% przychodu, mocny zastrzyk w h 3 (505), potem rzadziej. Bez zmian.
- **Identyfikatory** `stars_96` (próg 108) i `stages_32` (próg 36) mają stare liczby w nazwie —
  to tylko ID Play Games, nie błąd.

## 5. Czy badania czuć (pomiar)

RESEARCH_PLACEHOLDER

## 6. Sprawdzone pod kątem podwójnego naliczania (bez błędów)

`settleRewards` (jedno dodanie `reward`, osiągnięcia osobno, `achievementMedals` = różnica),
`LostBattle.settle` (raz przez `settleBattle`), `Missions.claim`/`claimLogin` (flagi
`claimed`/`loginClaimedDay`), `Profile.claimWeekly` (maska progów na tydzień konfiguracji),
`Profile.recordOrder` (tylko nowe ★), `claimWelcomeBack` (`welcomeBackDay`, zapis atomowy),
`Achievements.onBattleEnd` (zbiór odblokowanych). Nie znalazłem nagrody liczonej dwa razy.

## 7. Ograniczenia

Model jest arytmetyczny: zachowanie gracza to założenia z symulacji AI (BALANCE.md, d37,
noc-5), nie dane z testów z ludźmi. Liczby godzinowe są wrażliwe na minuty/dzień (sekcja 2).
Propozycje P1–P2 zmieniają moc lub tempo — przed wpisaniem powtórzyć `medale-czas` (d37).
