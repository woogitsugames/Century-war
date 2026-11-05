# D37 — przegląd balansu etapów 19–36, Endless i Oblężenia tygodnia (PROTOCOL 0.8.9)

Baza: `claude/dzien-35-etap-19` (etapy 2–18 skrócone, etap 19 złagodzony ×0,94).
Gałąź: `claude/dzien-37-balans-19-36`. **Tylko pomiar — gra, `:sim`, `BalanceReport` i PROTOCOL
bez zmian.** Propozycje korekt sprawdzone mnożnikiem w teście; wpisanie ich do gry wymaga decyzji
właściciela (pytania na końcu).

## Jak mierzono

Gradle nie działa (sieć blokuje dl.google.com). Tak jak w d33/d35: `:sim` i `BalanceReport`
skompilowane kompilatorem Kotlin 2.0.21 z `/opt/gradle-8.14.3/lib`, zaślepka `meta.Profile`,
JUnit uruchomiony bezpośrednio. Narzędzia tymczasowe tylko w `/tmp/claude-0/d37/` (nic w repo).

- **Kampania:** `BalanceReport.campaignReport`, N=100 seedów na etap, Normalny, etapy 16–36.
  Profile: `medale` (medale tylko z kampanii), `medale` + `BALANCE_COMMANDER=on`, `medale-czas`
  `BALANCE_PACE=now` (realny gracz: kampania + 140 medali / 45 min gry), `medale-czas` z dowódcą,
  docelowy (`target`: 19–27 średni 40%, 28–36 weteran 80%) i docelowy z dowódcą.
- **Kontrola:** etapy 16–24 powtarzają liczby d35 co do bitwy we wszystkich czterech profilach d35
  (np. etap 19: 65 / 76 / 72 / 73%). 0 patów w całym pomiarze.
- **Propozycje:** `BALANCE_POWER_SCALE` + `BALANCE_POWER_SCALE_STAGES` (jeden mnożnik na zbiór
  etapów); dla kombinacji różnych mnożników na różnych etapach — kopia `BalanceReport` w `/tmp`
  ze zmienną `BALANCE_POWER_SCALE_MAP` (np. `28:0.90,31:0.88`), poza repo. Zawsze pełny przebieg
  19–36, bo `medale-czas` liczy medale z czasu gry na etapach zmierzonych wcześniej w tym samym
  przebiegu.
- **Endless:** logika `EndlessBalanceReportTest.veteranHundredSeeds` (weteran 80%, do fali 60),
  seedy 1001–1100 i 2001–2100. **Oblężenie tygodnia:** `Weekly.config(w)` dla 52 kolejnych tygodni
  od tygodnia premiery (3022–3073), AI gracza na zasadach tygodnia (`normalizedBonuses`).

Komórka tabeli: **wygrane / mediana [min] / oczekiwany czas przejścia [min] / ★ wygranych**,
w nawiasie poziomy badań (profile medalowe). Oczekiwany czas = średnia wygrana + (1 − p)/p
średnich porażek (ile minut gracz stoi na etapie przy powtórkach do skutku).

## Wynik: stan obecny (0.8.9), N=100

| Etap | AI | medale | medale + dowódca | **medale-czas (realny gracz)** | docelowy | docelowy + dowódca |
|---:|---:|---:|---:|---:|---:|---:|
| 18 | 1,00 | 81% / 10,4 / 12,9 / 1,93 (31) | 86% / 10,5 / 12,1 / 1,84 (31) | 90% / 7,8 / 8,1 / 2,68 (37) | 60% / 9,9 / 15,5 / 2,17 | 62% / 10,2 / 15,3 / 2,08 |
| 19 | 1,52 | 65% / 13,4 / 19,7 / 1,68 (33) | 53% / 13,4 / 23,8 / 1,91 (33) | 76% / 10,1 / 12,1 / 2,37 (39) | 72% / 9,1 / 12,0 / 2,67 | 73% / 9,6 / 12,2 / 2,63 |
| 20 | 1,38 | 72% / 13,4 / 18,0 / 1,60 (35) | 69% / 13,5 / 18,5 / 1,72 (35) | 79% / 13,2 / 16,1 / 1,66 (40) | 77% / 13,2 / 16,7 / 1,65 | 74% / 13,3 / 16,9 / 1,89 |
| 21 | 1,47 | 70% / 10,4 / 14,0 / 2,61 (37) | 77% / 10,5 / 12,6 / 2,64 (37) | 88% / 9,7 / 10,2 / 2,55 (42) | 72% / 10,4 / 13,0 / 2,61 | 68% / 10,7 / 13,8 / 2,54 |
| 22 | 1,66 | 64% / 9,6 / 13,6 / 2,64 (38) | 66% / 9,9 / 13,4 / 2,58 (38) | 68% / 9,2 / 11,7 / 2,75 (44) | 64% / 9,6 / 13,0 / 2,64 | 68% / 9,3 / 11,8 / 2,65 |
| 23 | 1,52 | 69% / 12,9 / 17,2 / 1,93 (40) | 76% / 13,2 / 16,2 / 1,75 (40) | 67% / 13,1 / 17,6 / 1,99 (46) | 52% / 13,2 / 23,0 / 2,04 | 53% / 13,4 / 23,3 / 1,81 |
| 24 | 1,60 | 74% / 12,9 / 16,7 / 1,78 (42) | 71% / 13,4 / 18,0 / 1,55 (42) | 59% / 13,3 / 21,5 / 1,80 (48) | 63% / 13,1 / 19,7 / 1,76 | 57% / 13,4 / 22,0 / 1,67 |
| 25 | 1,62 | 84% / 9,4 / 10,5 / 2,63 (44) | 79% / 8,9 / 11,0 / 2,59 (44) | 61% / 10,7 / 15,7 / 2,62 (49) | 60% / 10,4 / 15,8 / 2,43 | 58% / 10,6 / 17,0 / 2,43 |
| 26 | 1,80 | 68% / 8,8 / 11,8 / 2,66 (45) | 65% / 9,6 / 13,1 / 2,46 (45) | 72% / 8,7 / 11,0 / 2,75 (51) | 64% / 9,3 / 13,1 / 2,63 | 68% / 8,8 / 12,1 / 2,65 |
| 27 | 1,63 | 69% / 12,6 / 15,8 / 2,12 (47) | 69% / 12,4 / 15,9 / 2,13 (47) | 80% / 11,8 / 12,8 / 2,24 (53) | 66% / 12,1 / 16,4 / 2,17 | 63% / 12,2 / 17,0 / 2,25 |
| 28 | 2,14 | **40%** / 13,6 / **31,9** / 1,53 (48) | **40%** / 13,6 / **31,7** / 1,58 (48) | **58%** / 13,6 / **21,6** / 1,55 (54) | 70% / 13,2 / 15,4 / 2,00 | 68% / 13,4 / 16,9 / 1,87 |
| 29 | 2,29 | 45% / 9,4 / 17,9 / 2,73 (50) | 48% / 8,8 / 16,4 / 2,67 (50) | 51% / 8,1 / 14,1 / 2,78 (56) | 72% / **3,7** / 7,7 / 2,92 | 80% / **2,0** / 6,8 / 2,89 |
| 30 | 2,37 | 46% / 10,4 / 20,4 / 2,50 (51) | 49% / 10,4 / 19,2 / 2,45 (51) | **43%** / 9,7 / 20,1 / 2,72 (57) | 69% / 8,9 / 10,9 / 2,71 | 66% / 9,5 / 11,6 / 2,68 |
| 31 | 2,55 | **33%** / 11,1 / 30,5 / 2,24 (53) | 41% / 11,0 / 24,3 / 2,34 (53) | **41%** / 11,3 / 24,6 / 2,24 (58) | 53% / 10,3 / 17,1 / 2,57 | 71% / 5,7 / 9,6 / 2,65 |
| 32 | 2,79 | **38%** / 12,6 / 29,7 / 2,05 (54) | **34%** / 12,2 / 32,2 / 2,24 (54) | **40%** / 12,1 / 27,4 / 2,05 (60) | 65% / 11,9 / 14,8 / 2,37 | 73% / 11,9 / 13,4 / 2,21 |
| 33 | 2,90 | **37%** / 8,5 / 19,9 / 2,84 (55) | **37%** / 8,1 / 19,4 / 2,95 (55) | **45%** / 7,2 / 15,1 / 2,78 (62) | 59% / 6,6 / 11,8 / 2,86 | 60% / 7,6 / 12,1 / 2,80 |
| 34 | 2,29 | 55% / 9,7 / 15,9 / 2,58 (57) | 45% / 10,3 / 20,2 / 2,47 (57) | 52% / 10,6 / 17,6 / 2,62 (63) | 70% / 6,9 / 10,1 / 2,81 | 62% / 8,8 / 12,2 / 2,82 |
| 35 | 2,66 | 50% / 8,2 / 14,1 / 2,90 (58) | 46% / 8,6 / 16,0 / 2,78 (58) | 50% / 8,6 / 14,6 / 2,74 (64) | 67% / 4,5 / 8,8 / 2,84 | 66% / 5,0 / 9,8 / 2,83 |
| 36 | 2,24 | 47% / 13,8 / 27,0 / 1,70 (59) | 53% / 13,7 / 24,2 / 1,49 (59) | 53% / 13,6 / 24,2 / 1,53 (66) | 76% / 12,4 / 14,5 / 2,09 | 62% / 13,1 / 17,6 / 2,15 |

`medale-czas` z dowódcą (`BALANCE_COMMANDER=on`), wygrane / oczekiwany czas: 16–18 89 / 88 / 88%
(9,4 min na 18), **19: 57% / 16,5**, 20–27: 81 / 83 / 77 / 66 / 63 / 65 / 79 / 78%,
28–36: 46 / 50 / 45 / 41 / 38 / 47 / 56 / 47 / 47%.

**Sumy oczekiwanych czasów i średnie wygrane:**

| Profil | 19–27 | 28–36 | 19–36 |
|---|---:|---:|---:|
| medale | 137 min (70,6%) | 207 min (43,4%) | 345 min |
| medale + dowódca | 143 min (69,4%) | 204 min (43,7%) | 346 min |
| **medale-czas (realny gracz)** | **129 min (72,2%)** | **179 min (48,1%)** | **308 min** |
| docelowy | 143 min (65,6%) | 111 min (66,8%) | 254 min |
| docelowy + dowódca | 146 min (64,7%) | 110 min (67,6%) | 256 min |

Realny gracz spędza na etapie 19–27 średnio 14,3 min, na 28–36 — **19,9 min** (1,4× dłużej),
mimo że mediany bitew są podobne (8–14 min): różnicę robią porażki.

## Porównanie z celami i historią (`docs/BALANCE.md`)

- **Pasmo `check_campaign_balance.py`** (docelowy, 7–27: 45–85%, 28–33: 40–75%, 34–36: 25–55%):
  docelowy mieści się wszędzie poza **etapem 34–36: 70 / 67 / 76% (powyżej 55%)**; po korektach
  tury 10 to było przewidziane (tura 10 opisała 36 → 76%). Cel tury 10 „docelowy ≤ ok. 75%”:
  etap 36 76%, etap 29 z dowódcą 80%.
- **Cel tury 10 dla „medali” z dowódcą na 34–36 (40–50%):** 45 / 46 / 53% — spełniony
  (było 45 / 46 / 46%; 36 +7 pp to szum).
- **Historia (tura 9, N=100, docelowy bez dowódcy):** 19–33 różnią się od dzisiejszych o −5…+9 pp
  (etap 20: 68 → 77%, 23: 57 → 52%) — w szumie; „medale” 28–33 tak samo: 38 / 48 / 42 / 36 / 47 / 37%
  wtedy, 40 / 45 / 46 / 33 / 38 / 37% dziś. **Etapy 20–36 nie zmieniły się od tury 10**
  (zmiany dni 1–35 dotyczyły tylko etapów 1–19); zmienia się tylko to, ile badań realny gracz ma
  na wejściu (`medale-czas` przy etapie 28: 54 poziomy).
- **Endless i Oblężenie tygodnia:** bez zmian względem D6 (niżej).

## Klify i strefy ostrzegawcze (kryterium d33)

Kryterium z d33 (względem poprzedniego etapu): **ściana** — iloraz oczekiwanych czasów > 2,0 albo
spadek wygranych > 25 pp albo wygrane < 40%; **ostrzegawcza** — > 1,6, > 15 pp albo < 50%.
Decyduje realny gracz (`medale-czas`), „medale” to dolna granica.

| # | Etap | Realny gracz (`medale-czas`) | „medale” | Ocena | Przyczyna |
|---|---:|---|---|---|---|
| 1 | **28** | 80 → **58%** (−22 pp), 12,8 → 21,6 min (**1,70×**) | 69 → 40% (−29 pp), **2,02×** | **ostrzegawcza** (u „medali” **ściana**) | skok `stageRamp` na początku trzeciej części: aiPower 1,63 → **2,14 (+31%)**; profil docelowy nie widzi klifu, bo przeskakuje z 40% na 80% badań |
| 2 | **30–33** | 43 / 41 / 40 / 45% (oczek. 15–27 min) | 46 / 33 / 38 / 37% | **ostrzegawcza** (wygrane < 50% przez 4 etapy; u „medali” 31–33 **ściana** < 40%) | aiPower rośnie 2,37 → 2,90, a etap 34 spada do 2,29 (korekta tury 10) — 33 jest trudniejszy niż 34–36 |
| 3 | **19 z dowódcą** | 88 → **57%** (**−31 pp**), 9,4 → 16,5 min (1,76×) | z dowódcą 86 → 53% (−33 pp, 1,96×) | **ściana** przy aktywnym dowódcy | bez dowódcy płynnie (76%); etap 19 to Szklane działo (GLASS_CANNON), a dowódca (2× cena ciężkiej) i odpowiedź dowódcy wroga przy aiPower 1,52 wypadają tu źle. Przy N=100 ±10 pp, ale −31 pp to > 3σ |
| 4 | 36 | 50 → 53%, ale oczek. 14,6 → 24,2 (1,65×) | 1,92× | formalnie ostrzegawcza | długi finał (mediana 13,6 vs 8,6 na 35); wygrane nie spadają — **nie ruszać** |
| 5 | 23 (docelowy) | realny gracz 67% (płynnie) | 69% | ostrzegawcza tylko u docelowego (64 → 52%, 1,76×) | szum + Mgła wojny; realny gracz nie widzi problemu — nie ruszać |

**Etapy za łatwe / za krótkie:**

- **Etap 29 dla pełnych badań:** mediana **3,7 min** (z dowódcą **2,0 min**) przy progu ≥ 4 min —
  znany od tury 9 (Kruche bazy + start w epoce 7). Realny gracz ma tu 8,1 min i 51%, więc to
  problem tylko gracza z 80% badań (powtórki dla gwiazdek). Nie podnosić siły AI (zaszkodzi realnemu
  graczowi); ewentualnie zmienić modyfikator etapu (np. zamiast Kruchych baz) — osobny pomiar.
- **Etap 21 realny gracz 88%**, etap 25 „medale” 84%, etap 18 → 19 → 21 realny gracz 90 / 76 / 88%:
  powyżej pasma 85% tylko etap 21 i o 3 pp (szum). Nie ruszać.
- **Docelowy 34–36 67–76%** (pasmo 25–55%): skutek tury 10 (cel dla „medali” 40–50%). Profil
  docelowy 28–36 = weteran 80% badań, a realny gracz ma tam 54–66 poziomów (49–60%) — to zła
  miara dla tych etapów (patrz pytanie 4).

## Propozycje korekt (sprawdzone mnożnikiem, N=100, bez zmian w grze)

### K1 — wygładzenie 28 i 30–33 (rekomendowana)

Mnożniki na `aiPower`: **28 ×0,90, 30 ×0,92, 31 ×0,88, 32 ×0,92, 33 ×0,92**
(`stageRamp` korekta: 28 1 → 0,90; 30 0,989 → 0,910; 31 0,97 → 0,854; 32 0,97 → 0,892;
33 1 → 0,92 — dokładne wartości Float dobrać jak w d35, żeby `aiPower` był bitowo równy pomiarowi).
aiPower 27–36: 1,63 → **1,93** → 2,29 → **2,18 → 2,24 → 2,57 → 2,67** → 2,29 → 2,66 → 2,24.

| Etap | medale-czas teraz → K1 | medale teraz → K1 | medale + dowódca teraz → K1 | docelowy teraz → K1 | docelowy + dowódca teraz → K1 | medale-czas + dowódca teraz → K1 |
|---:|---:|---:|---:|---:|---:|---:|
| 28 | 58 → **65%** (21,6 → 18,1 min) | 40 → **55%** | 40 → 42% | 70 → 78% | 68 → 74% | 46 → **62%** |
| 29 | 51 → 51% | 45 → 45% | 48 → 48% | 72 → 72% | 80 → 80% | 50 → 50% |
| 30 | 43 → **54%** | 46 → 50% | 49 → 59% | 69 → 68% | 66 → 77% | 45 → 50% |
| 31 | 41 → **52%** | 33 → **49%** | 41 → 54% | 53 → 66% | 71 → 76% | 41 → 52% |
| 32 | 40 → **50%** | 38 → **53%** | 34 → 47% | 65 → 78% | 73 → 72% | 38 → 52% |
| 33 | 45 → **52%** | 37 → **46%** | 37 → 45% | 59 → 70% | 60 → 75% | 47 → 43% |
| Σ 28–36 (oczek.) | 179 → **160 min** | 207 → 174 min | 204 → 182 min | 111 → 100 min | 110 → 102 min | — |
| śr. wygrane 28–36 | 48,1 → **53,2%** | 43,4 → 50,0% | 43,7 → 48,8% | 66,8 → 71,7% | 67,6 → 71,6% | — |

Etapy 19–27, 29 i 34–36 identyczne (kontrola). Po K1: realny gracz 28–36 wszędzie ≥ 50%,
spadek 27 → 28 −15 pp / 1,41× (na granicy „płynnie”); „medale” bez ściany (≥ 45%), zostają strefy
ostrzegawcze < 50% na 29, 31, 33 i 36 (45–49%). Koszt: docelowy (80% badań) do 78% na 28 i 32 (cel tury 10
„≤ ok. 75%”, +3 pp — w szumie). Oszczędność realnego gracza: **ok. 19 min** na 28–36.

**Odrzucone warianty:**
- 28 samo: ×0,85 → realny gracz 70%, ale docelowy 86%; ×0,80 → 82% / 89% (za łatwo).
  ×0,90 — jak w K1.
- 30–33 jednym mnożnikiem ×0,92: 31 zostaje na 43% u realnego gracza (stąd ×0,88 na 31 w K1).
- K2 (28 ×0,88, 30 ×0,90, 31 ×0,86, 32–33 ×0,90): niewiele lepiej na 30–33 (56 / 55 / 50 / 52%),
  a etap 29 realnego gracza spada do 42% — krótszy etap 28 to mniej minut gry, więc o 1 poziom
  badań mniej na 29 (artefakt modelu medali za czas, ale realny); docelowy z dowódcą 31 → 83%.

### K19 — etap 19 dla gracza używającego dowódcy (opcjonalna)

Dodatkowy mnożnik tylko na etapie 19 (na obecny `aiPower` 1,515), etapy 18–20, N=100:

| Mnożnik | medale-czas | medale-czas + dowódca | medale | medale + dowódca | docelowy | docelowy + dowódca |
|---:|---:|---:|---:|---:|---:|---:|
| 1 (dziś) | 76% | **57%** (−31 pp) | 65% | 53% | 72% | 73% |
| **0,96** | 79% | **70%** (−18 pp, 1,46×) | 71% | 56% | 79% | 76% |
| 0,92 | 82% | 81% (−7 pp) | 75% | 66% | 79% | 79% |

0,96 (`stageRamp` 19: 1,1891 → ok. 1,1415) zmienia klif z dowódcą ze „ściany” w „ostrzegawczą”,
ale docelowy przekracza 75% (79 / 76%) — a docelowy już dziś jest na 19 łatwiejszy niż na 18.
Odpowiedź na mnożnik jest bardzo nieliniowa (dowódca: 57 → 70 → 81%), więc przed decyzją warto
powtórzyć na drugim zestawie seedów. **Rekomendacja: na razie nie ruszać**, najpierw sprawdzić
w danych z gry, jak często gracze przywołują dowódcę na etapie 19 (patrz pytanie 2).

## Endless (weteran 80%, do fali 60)

| Metryka | 1001–1100 | 2001–2100 | Cel | D6 (po) |
|---|---:|---:|---|---:|
| Fala 60 (żywa baza) | **9/100** | **6/100** | 5–15/100 | 9 / 6 |
| Mediana fali porażki | **53** | **53** | 45–55 | 53 / 53 |
| Fala 20 | 100/100 | 100/100 | ≥ 95 | 100 / 100 |
| Fala 30 / 40 / 50 | 99 / 85 / 79 | 99 / 85 / 77 | raport | 85/79 · 85/77 |
| Średnia fala | 51,22 | 50,96 | — | 51,22 / 50,96 |
| Średni wynik | 21 064 | 20 952 | — | 21 064 / 20 952 |

**Identycznie co do biegu jak po D6** (zmiany kampanii nie dotykają Endless). W celu; fala 60
na próbie 2001–2100 (6/100) blisko dolnej granicy — przy kolejnej zmianie utrudniającej Endless
mierzyć ponownie. Korekta niepotrzebna.

## Oblężenie tygodnia (52 tygodnie od premiery, 3022–3073, AI na zasadach tygodnia)

Mediana fali **12**, średnia 13,5. Progi nagród (10 / 20 / 30 fal): **31 / 5 / 2 z 52** tygodni.
47 z 52 tygodni kończy się na falach 7–15. Odstające: tydzień 3033 (losowy, epoki 0–2, Podwójny
dochód + Szybkie jednostki) i 3052 (zestaw „Złoto”, Podwójny dochód + Kruche bazy, epoki 3–5) —
**fala 60 bez porażki**; 3027, 3036, 3066 — fale 22–23 (3 z 5 odstających mają Podwójny dochód).
Zestawy tematyczne: 7–15 fal, poza 3052. Brak zapisanego celu dla tygodnia; dla bota bez badań
trzeci próg (30 fal) jest prawie nieosiągalny, poza tygodniami z Podwójnym dochodem we wczesnej
epoce. Do obserwacji, bez korekty (to AI, nie człowiek; człowiek zwykle gra lepiej od bota).

## Pytania do właściciela (z rekomendacją)

1. **Etap 28 i etapy 30–33 — czy ułatwić?** Realny gracz po etapie 27 nagle przegrywa ponad
   połowę bitew przez 5 etapów (28 i 30–33: 40–58% wygranych) i stoi na każdym z nich ok. 20–27
   minut. Etap 33 jest dziś trudniejszy niż 34–36.
   **Rekomendacja: tak, wariant K1** (siła wroga: 28 −10%, 30 −8%, 31 −12%, 32 −8%, 33 −8%).
   Realny gracz: wszystkie te etapy 50–65%, ok. 19 minut mniej na końcówce kampanii; gracz
   z pełnymi badaniami nadal 66–80%. Wymaga PROTOCOL 0.8.10 i pomiaru z kodu (jak d35).
2. **Etap 19 z dowódcą — czy ułatwić jeszcze raz?** Bez dowódcy etap 19 jest już płynny (76%),
   ale gracz, który aktywnie przywołuje dowódcę, wygrywa tylko 57% (na etapie 18: 88%).
   **Rekomendacja: jeszcze nie.** Najpierw sprawdzić w danych z gry, jak często dowódca jest
   używany na 19; jeśli często — dodatkowo −4% siły wroga na 19 (wtedy 70% z dowódcą).
3. **Etap 29 — bardzo krótki dla gracza z pełnymi badaniami** (ok. 2–4 min). Realnego gracza to
   nie dotyczy (8 min, 51%). **Rekomendacja: zostawić**; jeśli przeszkadza przy powtórkach dla
   gwiazdek — zmienić modyfikator etapu (zamiast Kruchych baz), nie siłę wroga.
4. **Miara „profil docelowy” na etapach 28–36 to 80% badań, a realny gracz ma tam ok. 50–60%.**
   Przez to pasmo 34–36 (25–55%) jest „przekroczone” (67–76%), choć realny gracz ma tam ok. 50%.
   **Rekomendacja:** w pomiarach i celach dla 19–36 używać profilu `medale-czas` jako głównego,
   a docelowy tylko jako górnej granicy (≤ ok. 80%); pasma w `check_campaign_balance.py` zostawić
   do czasu decyzji.
5. **Endless i Oblężenie tygodnia** — w celu / bez celu, bez zmian. **Rekomendacja: nic nie
   ruszać**; ewentualnie ustalić cel dla tygodnia (np. „próg 10 fal osiąga ok. połowa graczy”),
   bo dziś nie ma z czym porównać.

## Do sprawdzenia pełnym Gradle

Liczby to wstępny pomiar poza Gradle (kontrola 16–24 zgodna z d35 co do bitwy). Wiążąco:
`BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=medale-czas BALANCE_PACE=now
BALANCE_STAGE_FROM=19 BALANCE_STAGE_TO=36 ./gradlew :app:testDebugUnitTest --rerun --tests
'*BalanceReport.campaignReport*'` (i `medale`, `target`, `BALANCE_COMMANDER=on`), a dla K1 —
po wpisaniu korekt do `stageRamp` pomiar z kodu.
