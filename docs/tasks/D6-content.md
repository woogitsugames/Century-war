# D6: Nowa zawartość: 4 karty Endless, 3 zestawy Oblężenia tygodnia, 2 wydarzenia dnia

**Gałąź:** `claude/dzien-6-tresc` z `origin/integ/tura10` (po scaleniu dnia 5).
**Teksty:** tylko `part184()` w `engine/L.kt` (10 języków; `putAll(part184())`; liczba
`putAll(part` = liczba `private fun part`). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`; długie raporty uruchamiaj w tle
(`run_in_background`). Podstawa: `docs/research/R1-backlog.md`, sekcja 5. Commity po polsku,
wypychaj często, a przed regeneracją złotych powtórek zrób osobny commit.

## Cel

Więcej różnych podejść w Endless i świeże tygodnie w pierwszych miesiącach po premierze, bez
wyjścia poza zmierzone pasma balansu. Endless (weteran 80% badań, 100 seedów): **dotarcie żywą
bazą do fali 60 = 5–15/100** (dziś 7/100), mediana fali porażki 45–55 (dziś 54).

## Stan w kodzie

- `sim/.../EndlessCards.kt`:
  - `enum class EndlessCard` (12–68; nowe wartości **tylko na końcu**, po `MASTER_MASONS`), `textKey` = `ec_<nazwa>`;
  - `EndlessCards.persistent()`, `canOffer()` (limity i warunki), `economicSynergyCards`,
    `stackPower`/`totalStackPower` (moc kolejnych stosów 1; 0,6; 0,4 …).
- `sim/.../Battle.kt`:
  - `applyEndlessCard(side, card)` (ok. 621–705, `when (card)`), `applyCard71Synergies` (708);
  - obrażenia jednostek `damageUnit(...)` (1537; tarcza bossa `SHIELD`, `FIRST_GUARD`,
    `EMBER_FORGE`), śmierć bossa (1584: `bossesDefeated`, `BOSS_RECALL`);
  - nowa fala (ok. 1690–1715: budżet wroga, `repairPerWave`, `FIELD_MEDICS`);
  - `score()` (1816);
  - modyfikatory i wydarzenia dnia przy starcie stron (ok. 370–410; `DailyEvent.SIEGE_WORKS`,
    `FORCED_MARCH`, `SPOILS_OF_WAR`, symetrycznie dla obu stron).
- `sim/.../Modes.kt`:
  - `DailyEvent` (71–82, nowe na końcu);
  - `ChallengeRuleSets` (274–305: `SETS`, `COUNT = 6`, `forWeek(index)` = `floorMod(index, COUNT)`);
  - `Weekly` (311–390: `RELEASE_MODIFIERS`, `START_CARDS`, `RULE_SET_EVERY = 3`,
    `firstRuleSetWeek()`, `config(week)`);
  - `Daily` (392–520: `RELEASE_DAY`, `RULES_V2_DAY`, `EVENTS_V2_DAY = RELEASE_DAY + 8`,
    `EVENTS_V1`, `EVENT_CONFLICTS`, losowanie z `DailyEvent.entries` od `EVENTS_V2_DAY`).
- `sim/.../Ai.kt`: `cardPreference(card)` (453, osobowości), `chooseCard(options)` (551). AI gracza
  w raportach sama wybiera karty, więc nowe karty muszą mieć wagę w `cardPreference`.
- `sim/.../BattleSnapshot.kt`: `VERSION = 19`, zapis `cardStacks` w długości `EndlessCard.entries.size`
  (205/407), wzór zgodności `CARDS_BEFORE_170` dla starszych wersji.
- `meta/BattleHistory.kt`: `cards = readArray(o, "cards", EndlessCard.entries.size)` (180). Trzeba
  sprawdzić, że krótsza tablica ze starej historii się wczytuje.
- UI: opisy kart (`BattleScreen` ok. 222–236, `cardDescKeys`), Kodeks (`CodexScreen`, karty
  i wydarzenia), osiągnięcia i misje (`meta/Achievements.kt`, `meta/Missions.kt`: `CARD_VARIETY`,
  `ENDLESS_CARDS`).
- `sim/.../SimVersion.kt`: `PROTOCOL = "0.8.5"`, `RANKED_REPLAY_FORMATS = setOf(7)`.

## Zawartość

### Karty Endless (dopisać po `MASTER_MASONS`)

| Karta | Rzadkość | Trwała? | Efekt (w `applyEndlessCard` i miejscach użycia) | `canOffer` |
|---|---|---|---|---|
| `SAPPERS` „Saperzy” | UNCOMMON | tak | ciężkie jednostki gracza zadają bossom `× (1 + 0,25 × totalStackPower(stosy))`; w `damageUnit`, gdy `u.isBoss && shooterRole == Role.HEAVY && attacker == 0`. Działa też na tarczę `SHIELD` (mnożnik przed `shield`) | zawsze; limit 8 stosów |
| `SHIELD_WALL` „Mur tarcz” | COMMON | tak | piechota i ciężkie gracza otrzymują od **pocisków** (`projectile == true`) `× (1 − 0,10 × totalStackPower)`, minimalnie ×0,75 | zawsze |
| `WAR_CHEST` „Skarbiec wojenny” | RARE | tak | na początku fali (ok. 1706): `gold += min(gold × 0,04 × totalStackPower, 40 × Defs.costScale(era))`; dopisać do `economicSynergyCards` | zawsze |
| `WAGER` „Zakład” | RARE | **nie** (jednorazowa, ≤ 1) | wrogie fale od następnej: HP jednostek wroga ×1,10 (pole strony wroga, np. `enemyWaveHpMul`, mnożone przy tworzeniu jednostek fali); fale liczą się w `score()` ×1,25 (`wave * 250L` → `wave * (if wager) 312L`) | `cardStacks[WAGER] == 0`; w opisie wyraźnie: ryzyko |

Każda karta: `ec_<nazwa>` i `ec_<nazwa>_d` w `part184()`, ikona z istniejących (`Icons`),
wpis Kodeksu, waga w `Ai.cardPreference` dla każdej osobowości (np. SAPPERS wyżej dla SIEGER,
WAR_CHEST dla ECONOMIST, SHIELD_WALL dla ENGINEER/BALANCED, WAGER niska poza ASSAULT).

**WAGER zmienia ranking Endless** (wynik ×1,25 za fale). To dopuszczalne, bo ofertę losuje `rng`
z seeda, więc w Oblężeniu tygodnia wszyscy mają te same szanse. Jeśli pomiar pokaże, że
z WAGER średni wynik rośnie o > 10%, zmniejsz premię do ×1,15 albo usuń kartę z `START_CARDS`
i opisz decyzję w `BALANCE.md`.

### Zestawy Oblężenia tygodnia (`ChallengeRuleSets.SETS`, dopisać po 6 istniejących)

| Klucz | Modyfikatory | Karta tygodnia |
|---|---|---|
| `week184_night` „Nocna straż” | `FOG_OF_WAR` + `ARMORED_FOE` | `VIGILANCE` (jeśli tydzień zaczyna się bez slotów wież, `canOffer` ją odrzuci; wtedy `FORTRESS`, rozstrzygnij w `Weekly.config` i dopisz test) |
| `week184_mercs` „Żołd i łupy” | `MERCENARIES` + `DOUBLE_INCOME` | `PLUNDER` |
| `week184_bastion` „Kruchy bastion” | `EXPENSIVE_TOWERS` + `FRAGILE_BASES` | `FRONTLINE_PLATE` |

`COUNT` 6 → 9. `forWeek` używa `floorMod(index, COUNT)`, więc zmiana `COUNT` przestawia
kolejność **przyszłych** tygodni tematycznych. Przed premierą (`RELEASE_DAY` = 2027-12-01,
`firstRuleSetWeek()` jeszcze nie nadszedł) nie ma historycznych tygodni, więc wolno. Dopisz
w KDoc `ChallengeRuleSets`, że po premierze nowe zestawy wymagają progu tygodnia (wzór
`firstRuleSetWeek`). Losowe tygodnie nie mogą powtarzać par z nowych zestawów (`matchesRuleSet`
korzysta z `forModifiers`, więc działa samo; test).
**Bez pogody w Oblężeniu tygodnia** w tym zadaniu (`Weekly.config` nie ustawia `weather`).
To osobna decyzja na później.

### Wydarzenia dnia (`DailyEvent`, dopisać na końcu, symetryczne)

| Wydarzenie | Efekt (obie strony, w bloku ok. 389–405) | `EVENT_CONFLICTS` |
|---|---|---|
| `HEAVY_DAY` „Dzień ciężkiej piechoty” (`day184_heavy`, `_d`) | koszt ciężkich ×0,8 (`unitCostMul` dla `Role.HEAVY`; jeśli brak pola na rolę, dodaj tablicę `roleCostMul[4]` w `Side`) | brak |
| `TOWER_DUEL` „Pojedynek wież” (`day184_towers`, `_d`) | `slots = min(MAX_TURRET_SLOTS, slots + 1)` | `NO_TURRETS`, `EXPENSIVE_TOWERS` |

**Determinizm dni:** od `EVENTS_V2_DAY` pula to `DailyEvent.entries`, więc dopisanie 2 wartości
przelosowuje dni od `RELEASE_DAY + 8`. Zrób jak przy zleceniu 170: `EVENTS_V2` = zamrożona
dziewiątka (obecne), nowe `EVENTS_V3_DAY = RELEASE_DAY + 15L` z pulą `entries`. Tylko tak
złote powtórki dni i przypięte powtórki się nie zmienią.

### Osiągnięcia i misje (opcjonalnie, tylko jeśli starczy czasu)

Jedno osiągnięcie „Wygrany zakład: fala 30 z kartą Zakład” (nowe ID PGS → `play_games_ids.properties`
`REPLACE_ME`, liczba 79 → 80 w `docs/RELEASE.md`, `OWNER_INPUT.md`, `data_safety.md`).
Bez nowych typów misji (`MissionType` ma ordinal w profilu).

## Formaty, PROTOCOL i złote powtórki: kiedy co

1. **`BattleSnapshot.VERSION` 19 → 20**: tablica `cardStacks` rośnie o 4. Zapis v20, odczyt v19
   z `cards = size − 4` (dodaj `CARDS_BEFORE_184`, wzór `CARDS_BEFORE_170`) i v18/v17/v16 jak
   dotąd. Plus nowe pole stanu dla WAGER, jeśli dodajesz (np. `enemyWaveHpMul`). Test
   zgodności: zrzuty zapisane jako v16–v19 (`encode(..., formatVersion)`, wzór w
   `sim/src/test/kotlin/com/centurywar/game/SnapshotSettlementTest.kt`) wczytują się bez zmian.
2. **`Replay.VERSION`** bez zmian, jeśli format komend się nie zmienia (karta to indeks 0–2
   oferty). Wtedy `RANKED_REPLAY_FORMATS` zostaje `setOf(7)`.
3. **PROTOCOL 0.8.5 → 0.8.6**: nowe karty zmieniają oferty, więc zmieniają przebieg bitew
   Endless i tygodnia. Podbij **w tym samym commicie co zmiany `:sim`** i opisz w `docs/BALANCE.md`.
4. **Złote powtórki**: po zmianach `:sim` i **po** pomiarze balansu (ostatnie strojenie)
   `ORG_GRADLE_PROJECT_goldenReason="184: nowe karty Endless, zestawy tygodnia, wydarzenia dnia" flock /tmp/claude-0/gradle.lock ./gradlew :sim:regenerateGolden -PserverOnly=true --no-configuration-cache`.
   Regeneracja ma zmienić tylko powtórki Endless i tygodnia. Jeśli zmienia kampanię, potyczkę
   albo dzień sprzed `EVENTS_V3_DAY`, to błąd determinizmu: popraw, zanim zregenerujesz.
5. Kolejność commitów: (a) `:sim` + teksty + testy jednostkowe, (b) pomiar i strojenie
   (`BALANCE.md`), (c) PROTOCOL + złote powtórki, (d) UI, Kodeks, „Co nowego”, zrzuty.

## Plan pomiaru balansu

Raporty są w kategorii `balance` (`gradle/test-categories.properties`) i nie uruchamiają się
w `fastTest`. Uruchamiaj w tle i z `--rerun`.

1. **Endless 100 seedów (główny cel):**
   `flock /tmp/claude-0/gradle.lock ./gradlew :server:test -PserverOnly=true --rerun --tests '*EndlessBalanceReportTest.veteranHundredSeeds*' --console=plain --no-configuration-cache`
   (`server/src/test/kotlin/com/centurywar/server/EndlessBalanceReportTest.kt`). Odczyt z logu:
   `ENDLESS100_SURVIVE_60` (cel **5–15/100**), `ENDLESS100_DEFEAT_MEDIAN` (cel **45–55**),
   `ENDLESS100_SURVIVE_20` (≥ 95/100, twarda asercja), `ENDLESS100_MEAN`, `ENDLESS100_DISTRIBUTION`.
   Pomiar **przed** zmianami (baza, ten sam HEAD bez Twoich commitów, osobny worktree) i **po**.
   Tabela „przed/po” w `BALANCE.md`, nowa sekcja „Dzień 6 — karty 184”.
2. **Próba kontrolna:** te same metryki dla seedów 2001–2100 (tymczasowo zmieniony zakres seedów
   w kopii testu, bez commitu, jak w turze 6). Różnica > 5 punktów między próbami oznacza szum,
   więc nie strój na jednym przebiegu.
3. **Endless 20 seedów:** `*EndlessBalanceReportTest.veteranTwentySeeds*` (szybka iteracja
   strojenia).
4. **Tydzień:** `*EndlessBalanceReportTest.weeklyModifierPairs122*` — nowe pary modyfikatorów
   muszą mieć medianę fali w tym samym paśmie co istniejące zestawy (zanotuj wartości, porównaj
   z najtrudniejszą i najłatwiejszą obecną parą; poza pasmem — zmień kartę tygodnia, nie
   modyfikatory).
5. **Wydarzenia dnia:** symetryczne, więc lustrzana potyczka AI vs AI (`BalanceReport.mirrorReport`)
   z wymuszonym wydarzeniem: odsetek zwycięstw strony 0 w 45–55% przy zamianie stron;
   czas bitwy nie dłuższy o > 20% niż bez wydarzenia. Uruchomienie:
   `BALANCE_RUN=true flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun --tests '*BalanceReport.mirrorReport*' --console=plain --no-configuration-cache`
   (liczba prób: `BALANCE_MIRROR_RUNS`, domyślnie 20; raport nie przyjmuje dziś wydarzenia dnia,
   więc dodaj zmienną `BALANCE_DAILY_EVENT`).
6. **Regresja:** `DoctrineBalance82Test` (pasmo 40–65%) i `campaignReport` bez zmian. Karty nie
   występują w kampanii, więc wynik ma być identyczny. Każda różnica oznacza błąd.

Strojenie: gdy fala 60 > 15/100, najpierw osłab `WAR_CHEST` (limit 40 → 30) i `SAPPERS`
(0,25 → 0,18), dopiero potem pozostałe. Gdy < 5/100, sprawdź WAGER (AI może ją brać za często;
obniż wagę w `cardPreference`). Nie ruszaj krzywej fal (`hpMul` po fali 48) bez zgody koordynatora.

## Testy do dopisania

- `app/src/test/java/com/centurywar/game/EndlessCardsTest.kt`:
  - `cardsApplyTheirModifiers`: dopisać 4 karty;
  - SAPPERS: ciężka jednostka zadaje bossowi więcej, lekka tyle samo;
  - SHIELD_WALL: pocisk mniej, walka wręcz tyle samo, dolny limit ×0,75;
  - WAR_CHEST: limit na falę;
  - WAGER: jednorazowa, HP następnej fali ×1,1, wynik fal ×1,25;
  - `cardOfferBossesAndThemesAreDeterministicForSameSeed` nadal zielony.
- Test `Weekly`: 9 zestawów, każdy wraca co `COUNT × RULE_SET_EVERY` tygodni, losowy tydzień
  nie trafia w parę zestawu, „Nocna straż” ma ważną kartę startową.
- Test `Daily`: dni przed `EVENTS_V3_DAY` mają identyczne wydarzenia jak przed zmianą (lista
  dni i wydarzeń z HEAD przed zmianą zapisana w teście), od `EVENTS_V3_DAY` pojawiają się nowe;
  `TOWER_DUEL` nigdy z `NO_TURRETS`/`EXPENSIVE_TOWERS`.
- `BattleSnapshot`: v20 zapis/odczyt z nowymi kartami; odczyt v19/v18/v17/v16.
- `BattleHistory`: stary wpis z krótszą tablicą `cards` się wczytuje.
- `LTest`/`CodexLTest`: 10 tłumaczeń nowych kluczy, wpisy Kodeksu.
- Zrzuty: oferta kart z nową kartą (`PrereleasePolishTest` ma ekran kart Endless) na 720p
  i zewnętrznym Foldzie; opis karty nieucięty.

## Kryteria odbioru

- `ENDLESS100_SURVIVE_60` w paśmie 5–15/100 w obu próbach (1001–1100 i 2001–2100), mediana
  porażek 45–55, fala 20 ≥ 95/100. Wyniki z numerem commita w `BALANCE.md`.
- Kampania, potyczka i dni sprzed `EVENTS_V3_DAY` mają złote powtórki bez zmian (regeneracja dotyka
  tylko Endless/tygodnia).
- PROTOCOL 0.8.6, `BattleSnapshot.VERSION` 20 z odczytem starszych, `Replay.VERSION` bez zmian.
- „Co nowego” (`meta/WhatsNewCatalog.kt`) z krótkim wpisem; Kodeks z nowymi kartami i dniami.
- Pełne testy zielone:
  `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- **Determinizm i ranking:** każda zmiana kolejności enumów albo pul zmienia historyczne bitwy.
  Dopisujemy tylko na końcu, a dni pilnuje nowy próg `EVENTS_V3_DAY`.
- **WAGER a wynik:** wpływ na ranking Endless jest celowy, ale musi być zmierzony
  (`ENDLESS100_MEAN` i średni `score`).
- **AI gracza w raportach** wybiera karty według `cardPreference`. Zbyt wysoka waga nowej karty
  zafałszuje pomiar.
- Oferty mogą być „puste” przy wielu ograniczeniach. `draw()` ma `require(total > 0)`, więc test
  1000 ofert w różnych stanach strony.

## Czego nie ruszać

- Zatwierdzony `compactTop`, układ HUD, celowanie ostrzału.
- Krzywa fal Endless, bossowie, kampania i jej balans, doktryny (poza kartami-doktrynami bez zmian).
- Istniejące karty, zestawy i wydarzenia (wartości i kolejność).
- Pogoda w Oblężeniu tygodnia, bo to osobna decyzja.
