# Dzień 68 — nowe style przeciwnika (R170)

Decyzja właściciela 7.11: trzy prototypy z `docs/research/R170-wrogowie.md` — „Osłona i strzała”,
„Krótkie uderzenia”, „Zryw w kontakcie”. Krótka nazwa stylu w opisie etapu i potyczki, losowanie
w potyczce, po jednym późnym etapie kampanii na styl — tylko po pomiarze trudności.
PROTOCOL **0.8.14**, złote powtórki zregenerowane. `BattleSnapshot.VERSION` bez zmian (23): style
nie mają nowego stanu — używają istniejących `holdUntil`/`nextHoldAt`, a ordinal osobowości już
był w zapisie (nowe wartości dopisane na końcu enuma, test zapisu w połowie bitwy).

## Co zbudowano (`sim/.../AiPersonality.kt`, `Ai.kt`)

Wyłącznie decyzje AI: bez zmian przychodu, statystyk i wiedzy (reakcje tylko przez `canSeeEnemy`).

| Styl (enum) | Zachowanie | Kod |
|---|---|---|
| Osłona i strzała (`SCREEN`) | Najpierw dwie jednostki wręcz (żywe + w kolejce), potem przewaga strzelców za nimi; bez osłony od razu znowu wręcz. | `pickRole` (`SCREEN_FRONT = 2`), wagi 24/46/20/10 |
| Krótkie uderzenia (`RAIDS`) | Postój zbiera przy bazie grupę 3 jednostek, grupa rusza na 20 s, potem znowu postój (ocalali stają i walczą), zbiórka następnej. Wróg blisko bazy (≥3) zwalnia postój; po 10 s rusza choćby 2 jednostki lub gdy armia jest pełna. | `raidPosture` (`RAID_STRIKE = 20`, `RAID_MAX_WAIT = 10`) |
| Zryw w kontakcie (`CONTACT_RALLY`) | Zryw tylko przy ≥2 widocznych wrogach przy froncie (albo w obronie zagrożonej bazy), nigdy na marszu; mieszany skład, późniejsze i tańsze wieże, rzadszy ostrzał. | `useAbilities` (`CONTACT_MIN = 2`), wagi 36/26/28/10 |

Wariant epok, karty Endless i doktryny dostały preferencje dla kompletności `when` (karty — nowe
style nie występują w Endless).

## Gdzie włączone

* **Potyczka**: przeciwnik bez wybranego stylu losuje z seeda ze wszystkich ośmiu stylów; ekran
  przygotowania ma trzy nowe przyciski (w wąskiej kolumnie — telefon w pionie, zewnętrzny Fold —
  jedna kolumna, żeby nazwy mieściły się przy 130%) i wiersz „Wróg: …” nad opisem.
* **Kampania**: etap **13** — Osłona i strzała, etap **18** — Krótkie uderzenia, etap **21** — Zryw
  w kontakcie (`Campaign.STAGE_PERSONALITIES`). Okno etapu pokazuje „Wróg: <styl>” (wysoki panel:
  osobny wiersz z ikoną hełmu i ciaśniejsze wiersze, niski panel: obok pogody); także w opisie dla
  czytnika ekranu. Samouczek (zawsze bez stylu) i etapy 1–12 bez zmian.
* **Bez zmian**: Rozkazy sztabu, Endless (fale), wyzwanie dnia i tygodnia. Losowanie z seeda poza
  potyczką (i AI gracza) używa zamrożonej piątki `AiPersonality.LEGACY`, MIRROR_ARMY też
  (`Modes.kt` Daily) — stare seedy dają tego samego dowódcę (`AiPersonalityD68Test`).
* Statystyki: „Przeciw AI” ma osiem wierszy (`bh_ai_5..7`).

## Pomiar 1: style w potyczce (`AiPersonalityD68ReportTest`, 100 seedów)

`AI_D68_RUN=true AI_D68_SEEDS=100 ./gradlew :sim:test --tests '*AiPersonalityD68ReportTest*'`;
gracz BALANCED, te same seedy, scenariusze 0→3 i 2→2, bez dowódcy (jak `AiPersonalityProfileTest`),
remis = pół wygranej (remisów 0). Przedział Wilsona 95%.

| Styl | 0→3 wygrane gracza (95%) | 2→2 wygrane gracza (95%) | Razem |
|---|---:|---:|---:|
| ASSAULT | 37,0% (28,2–46,8) | 39,0% (30,0–48,8) | 38,0% |
| ENGINEER | 57,0% (47,2–66,3) | 66,0% (56,3–74,5) | 61,5% |
| ECONOMIST | 35,0% (26,4–44,7) | 40,0% (30,9–49,8) | 37,5% |
| SIEGER | 65,0% (55,3–73,6) | 51,0% (41,3–60,6) | 58,0% |
| BALANCED | 58,0% (48,2–67,2) | 45,0% (35,6–54,8) | 51,5% |
| **SCREEN** | 45,0% (35,6–54,8) | 42,0% (32,8–51,8) | **43,5%** |
| **RAIDS** | 44,0% (34,7–53,8) | 31,0% (22,8–40,6) | **37,5%** |
| **CONTACT_RALLY** | 48,0% (38,5–57,7) | 39,0% (30,0–48,8) | **43,5%** |

Wszystkie w bramce 30–70%. Sygnatury (średnio na bitwę, 0→3 / 2→2; pełna tabela w
`sim/build/ai-d68-report.md` po uruchomieniu):

* **Osłona i strzała**: udział dystansu do 60 s 62,1% / 61,0% (BALANCED 35,0% / 37,8%, ENGINEER
  50,7% / 51,9%, czyli +26 / +23 p.p. względem BALANCED i +11 / +9 p.p. względem Inżyniera);
  pierwsza para wręcz-przed-strzelcem po 7,4 / 7,3 s (BALANCED 9,6 / 10,1 s, ENGINEER 9,4 / 8,1 s).
  Odsetek próbek 0–90 s z parą wręcz przed strzelcem 37% (BALANCED ~50%) — nowe jednostki wręcz
  wychodzą spod bazy, więc za żywymi strzelcami; dwa szeregi są widoczne na początku natarcia, potem
  przód odbudowuje się z tyłu. To ograniczenie miary, nie stylu.
* **Krótkie uderzenia**: 4,1 wyjścia grupy do 90 s (BALANCED 0, ENGINEER 1,3, SIEGER 1,6), łącznie
  37,5 / 32,3 na bitwę, odstęp 21,8 s, drugie wyjście po 30 s (SIEGER 86–95 s), grupa spod bazy
  2,9 jednostki (SIEGER 5,3). Postój 6–7% czasu (SIEGER 20–24%).
* **Zryw w kontakcie**: Zrywy na marszu 0,07 / 0,05 na bitwę (BALANCED 1,19 / 2,01, ASSAULT 6,5);
  pierwszy Zryw 5,3 / 5,2 s po pierwszym kontakcie (pozostali 0,0–0,5 s, ECONOMIST 2–3 s); kontakt
  do 90 s we wszystkich bitwach (miara: wróg gracza ≤ 260 px od frontu). Wieże 0,46 / 1,27
  (BALANCED 1,83 / 1,90).

Odróżnienie od najbliższego istniejącego stylu (2→2, 15 miar, próg 15%): SCREEN 6 miar
(najbliższy BALANCED), RAIDS 3, CONTACT_RALLY 4 — wymagane minimum 2. Dotychczasowy test
`AiPersonalityProfileTest` (20 seedów, wszystkie osiem stylów) przechodzi; jego miara
„fala_ruszajaca” liczy teraz jednostki ruszające spod bazy, nie ocalałych w polu.

Strojenie: pierwsza wersja Krótkich uderzeń wstrzymywała szkolenie podczas uderzenia — gracz
BALANCED wygrywał 5% (wróg odkładał złoto na ulepszenia, a pojedynczy ocaleni stali pod bazą
gracza). Bez wstrzymania, z czasem uderzenia 20 s i zwolnieniem postoju przy pełnej armii — 37,5%.
Zryw w kontakcie z wagami BALANCED różnił się tylko w 2 miarach; mniej wież i rzadszy ostrzał dały
4 miary przy 43,5%.

## Pomiar 2: trudność etapów kampanii (`BalanceReport.campaignReport`)

`BALANCE_RUN=true BALANCE_PROFILE=target BALANCE_STAGES=… BALANCE_RUNS=… BALANCE_AI_PERSONALITY=…`
(`BALANCE_AI_PERSONALITY="13:SCREEN,21:SEED"` wymusza styl etapu albo dawne losowanie z seeda).
Te same seedy przed i po. Najpierw przegląd wszystkich trzech stylów na etapach 5–28 (60 seedów),
potem potwierdzenie wybranych etapów na 120 seedach.

Kandydaci z R170 (60 seedów, wygrane gracza, profil docelowy):

| Etap | Przed | Osłona i strzała | Krótkie uderzenia | Zryw w kontakcie |
|---:|---:|---:|---:|---:|
| 5 | 86,7% | 90,0% | 95,0% | 85,0% |
| 7 | 75,0% | 68,3% | 68,3% | 73,3% |
| 9 | 66,7% | **78,3% (+11,7)** | 70,0% | 70,0% |
| 11 | 58,3% | 60,0% | **71,7% (+13,3)** | 70,0% |
| 13 | 68,3% | 75,0% | 68,3% | 70,0% |
| 18 | 61,7% | 51,7% | 61,7% | 51,7% |
| 21 | 78,3% | 61,7% | 76,7% | 71,7% |
| 25 | 58,3% | 35,0% (−23,3) | 56,7% | **35,0% (−23,3)** |

Proponowane w R170 późniejsze etapy (9, 11, 25) przekraczały bramkę 10 p.p., a 5 i 7 to wczesne
etapy, więc wybrano najbliższe późne etapy z małą zmianą: 13, 18, 21. Potwierdzenie (120 seedów):

| Etap | Styl | Wygrane przed | Wygrane po | Zmiana | Mediana przed / po [min] | Oczek. przed / po [min] |
|---:|---|---:|---:|---:|---:|---:|
| 13 | Osłona i strzała | 68,33% | 75,00% | +6,7 p.p. | 8,16 / 8,31 | 11,09 / 10,49 |
| 18 | Krótkie uderzenia | 58,33% | 65,00% | +6,7 p.p. | 9,93 / 10,01 | 15,92 / 15,01 |
| 21 | Zryw w kontakcie | 75,00% | 71,67% | −3,3 p.p. | 9,85 / 9,90 | 12,08 / 13,77 |

Brak regresji > 10 p.p.; czasy bez zmian; patów 0%. Pozostałe etapy nie zmieniają przeciwnika
(losowanie z zamrożonej piątki), więc ich wyniki są identyczne.

## Testy i zgodność

* `AiPersonalityD68Test`: kolejność enuma, zamrożone losowanie poza potyczką (kampania, dzień,
  Rozkazy, AI gracza), wszystkie style w potyczce, MIRROR_ARMY, style tylko na etapach ≥ 9 i nigdy
  w samouczku, zapis/odczyt w połowie bitwy dla każdego nowego stylu, ≥3 wyjścia grupy na 120 s,
  brak Zrywu bez kontaktu.
* `EnemyStyleD68Test` (app): okno etapu 13/18/21 i ekran potyczki na 1600×720, 2400×1080,
  904×2316 (zewnętrzny Fold) i 2184×1968 przy 100% i 130% w 10 językach — „Wróg: …” jest
  narysowany, ma ≥ 10 dp, nie nachodzi na inne napisy i mieści się w panelu, modyfikatory zostają,
  odstęp do „Trudność” ≥ 10 dp; nazwy stylów nie wychodzą poza przyciski.
* `SecondHourControlTest`: nowy przypięty wynik etapu 21 (stały styl). `SiegeStarsDay2Test`
  sprawdza standardową presję na etapie 22 zamiast 21 (seed etapu 21 kończy się teraz przed presją).
* `AiCaps97Test`: preferencje doktryn trzech nowych stylów.

WYNIK_TESTOW

## Nowe teksty (`L.kt`, `part216`, 10 języków)

`sk_person_5..7`, `bh_ai_5..7` (nazwy stylów), `sk_desc_5..7` (opisy), `d68_enemy` („Wróg: %1$s”).

| Klucz | pl | en |
|---|---|---|
| sk_person_5 | Osłona i strzała | Shield and Arrow |
| sk_person_6 | Krótkie uderzenia | Quick Raids |
| sk_person_7 | Zryw w kontakcie | Rally on Contact |
| sk_desc_5 | Za osłoną piechoty ustawia strzelców. | Puts ranged troops behind an infantry screen. |
| sk_desc_6 | Atakuje małymi grupami z krótkimi przerwami. | Attacks in small groups with short pauses. |
| sk_desc_7 | Zryw zostawia na zetknięcie z twoją obroną. | Saves Rally for contact with your defense. |
| d68_enemy | Wróg: %1$s | Enemy: %1$s |

Nazwy w pozostałych językach używają istniejącej nazwy Zrywu (Ansturm, Ímpetu, Élan, Slancio,
Ímpeto, Ривок, Stormloop, Atılım); francuski w formie „vous”, portugalski pt-PT („contacto”).
