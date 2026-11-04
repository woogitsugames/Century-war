# D33 — pomiar klifu etapu 18 → 19 (przygotowanie)

Baza: `integ/tura10` (PROTOCOL 0.8.8). Zadanie: R12, pozycja 1, sekcja 2 (M1, M2, M3).
Decyzja właściciela: najpierw pomiar, potem ewentualne złagodzenie etapu 19.
Gra, `:sim`, wyniki bitew i PROTOCOL **bez zmian**; zmienił się tylko kod testu
(`BalanceReport`) i doszedł skrypt `tools/measure_stage19.sh`.

## Co jest w gałęzi

**`app/src/test/java/com/centurywar/game/BalanceReport.kt`** (działa tylko przy
`BALANCE_RUN=true`, więc zwykłe testy i gra się nie zmieniają):

- **Profil `BALANCE_PROFILE=medale-czas` (M2).** Badania z medali, które realny gracz ma
  przed danym etapem:
  - kampania: pierwsza wygrana każdego etapu `20 + 6·i + 17·★` (5/★ + 12 za nową ★), gdzie
    ★ to zmierzona średnia gwiazdek wygranych profilu „medale” z `docs/BALANCE.md`; od etapu 7
    jedna porażka `5 + 2·i + 10` (jak w dotychczasowym profilu „medale”);
  - czas gry: `BALANCE_DAILY_MEDALS` (domyślnie 140) × dni, dni = (suma oczekiwanych czasów
    etapów przed tym etapem + `BALANCE_BREAK_MINUTES` po każdej bitwie, domyślnie 1,5)
    / `BALANCE_MINUTES_PER_DAY` (domyślnie 45);
  - zakup: najtańszy następny poziom (wspólna funkcja `spendCheapest`, ta sama co w „medale”).
  - `BALANCE_PACE=before|now` wybiera tablicę etapów 1–18: `before` = PROTOCOL 0.8.4 (przed
    skróceniem 2–18), `now` = 0.8.8 (domyślnie). Czasy to oczekiwane czasy profilu docelowego
    z `BALANCE.md` (jak w modelu R12), gwiazdki — profilu „medale”. Etapy 13–18 „teraz” mają
    w `BALANCE.md` tylko sumę (93,4 min), więc rozkład na etapy to mediana / odsetek wygranych
    „po”, przeskalowany do tej sumy (11,5 / 17,0 / 18,4 / 15,7 / 15,2 / 15,6).
  - Etapy 19+ biorą czas przejścia i ★ **z bieżącego przebiegu** (pomiar idzie rosnąco, więc
    etap 20 liczy medale z etapu 19 zmierzonego chwilę wcześniej). Etap niezmierzony (np. przy
    `BALANCE_STAGES`) bierze 26,6 min / 1,51 ★ (etap 19), 18,0 / 1,60 (etap 20) albo
    18 min / 1,6 ★; linia `BALANCE` pokazuje wtedy `fallbackStages=N`.
- **`BALANCE_POWER_SCALE_STAGES`** (np. `19`): `BALANCE_POWER_SCALE` działa tylko na tych etapach.
  Bez tej zmiennej mnożnik działa jak dotąd na wszystkie mierzone etapy. Dzięki temu M3
  mierzy etapy 18–20 w jednym przebiegu, a skalowany jest tylko 19.
- **CSV raportu:** na końcu doszły kolumny `win_mean_minutes, loss_mean_minutes,
  expected_minutes, mean_win_stars, research_levels, power_scale, commander, pace,
  campaign_medals, timed_medals, play_minutes_before`. Wcześniej oczekiwany czas i ★ były
  tylko w stdout. Kolumny są dopisane na końcu, więc `tools/check_campaign_balance.py`
  (DictReader) działa bez zmian. Markdown raportu ma kolumny „Oczek.” i „★”.

**`tools/measure_stage19.sh`** — uruchamia pomiary kolejno, każdy przez
`flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun --tests
'*BalanceReport.campaignReport*' --console=plain --no-configuration-cache`, z wyczyszczonymi
zmiennymi `BALANCE_*` (nic nie przecieka z powłoki ani z poprzedniego kroku) i `PERF_REPORT=true`
(linie `BALANCE stage=…` w logu). Po każdym kroku sprawdza, czy powstał oczekiwany CSV.
Na końcu składa podsumowanie Markdown.

## Jak uruchomić

Z katalogu repozytorium, po odblokowaniu sieci (Gradle musi pobrać zależności):

```bash
tools/measure_stage19.sh --dry-run --m3    # tylko wypisuje 17 poleceń Gradle
tools/measure_stage19.sh                   # M1 + M2 (5 uruchomień)
tools/measure_stage19.sh --only M3 --m3    # potem M3 (12 uruchomień), jeśli M1/M2 pokażą ścianę
tools/measure_stage19.sh --m3              # albo wszystko naraz (17 uruchomień)
tools/measure_stage19.sh --summary-only    # tylko podsumowanie z istniejących CSV
```

Opcje: `--runs N` (domyślnie 100), `--m3-scales "0.94 0.90 0.86"`, `--lock`, `--out`,
`--keep-going` (nie przerywaj po błędzie). Podsumowanie:
`app/build/balance/d33-stage19-summary.md`, logi: `app/build/balance/d33-logs/<tag>.log`.

| Krok | Tag (`report-<od>-<do>-<tag>.csv`) | Profil | Etapy | Bitew |
|---|---|---|---|---:|
| M1 | `d33-m1-medale` | `medale` | 16–24 | 900 |
| M1 | `d33-m1-target` | `target` | 16–24 | 900 |
| M1 | `d33-m1-target-cmd` | `target`, `BALANCE_COMMANDER=on` | 16–24 | 900 |
| M2 | `d33-m2-before` | `medale-czas`, `BALANCE_PACE=before` | 16–30 | 1500 |
| M2 | `d33-m2-now` | `medale-czas`, `BALANCE_PACE=now` | 16–30 | 1500 |
| M3 | `d33-m3-{medale,target,target-cmd,czas-now}-<S>` | jw., `BALANCE_POWER_SCALE=S`, `BALANCE_POWER_SCALE_STAGES=19` | 18–20 | 4 × 3 × 300 |

Czas: R12 szacował M1 na 30–40 min przez Gradle. M1 + M2 to ok. 5 700 bitew, M3 kolejne
3 600 (w jednym procesie testowym, bo to jedna klasa).

Ręcznie, bez skryptu (to samo co skrypt robi w kroku M2 „teraz”):

```bash
BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=medale-czas BALANCE_PACE=now \
BALANCE_STAGE_FROM=16 BALANCE_STAGE_TO=30 BALANCE_TAG=d33-m2-now PERF_REPORT=true \
flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun \
  --tests '*BalanceReport.campaignReport*' --console=plain --no-configuration-cache
```

## Czego się spodziewać

**Kontrola (M1 „medale” i „docelowy”):** stan kodu jest ten sam co przy ostatnim pomiarze
w `BALANCE.md` (sekcja „Etapy 13–18”), a seedy zależą tylko od etapu i numeru próby, więc
M1 powinien **powtórzyć** te liczby co do bitwy: „medale” etap 18 81% / mediana 10,4,
etap 19 49% / 13,5 / oczekiwany 26,6 min, etap 20 72% / 13,4; docelowy etap 18 60% / 10,0,
etap 19 60% / 10,1 / 15,9, etap 20 77% / 13,2; docelowy z dowódcą etap 18 62% / 10,2.
Inne liczby na tych etapach znaczą, że coś zmieniło się w symulacji albo w teście — wtedy
przerwać i wyjaśnić przed czytaniem reszty.

**Model M2 (python, te same wzory i stałe co w teście):**

| Przed etapem | Medale kampanii przed / teraz | Minut gry przed / teraz | Medale z czasu przed / teraz | Poziomy przed / teraz | „medale” (dotąd) |
|---:|---:|---:|---:|---:|---:|
| 18 | 1 964 / 2 034 | 315 / 238 | 981 / 740 | 38 / 37 | 31 |
| 19 | 2 164 / 2 238 | 335 / 255 | 1 041 / 794 | **40 / 39** | 33 |
| 20 | 2 368 / 2 443 | 363 / 283 | 1 129 / 881 | 42 / 41 | 35 |
| 25 | 3 519 / 3 594 | 460 / 381 | 1 432 / 1 184 | 50 / 49 | 44 |
| 30 | 4 870 / 4 945 | 558 / 478 | 1 735 / 1 488 | 58 / 57 | 51 |

(Etapy 19+ w modelu mają stały czas 18 min i 1,6 ★; w teście wezmą wartości zmierzone.)
Zgadza się z R12: ok. 335 → 255 min do etapu 19 i 40 → 39 poziomów. Ważne: `medale-czas`
ma na etapie 19 **ok. 6 poziomów więcej** niż „medale” (39 zamiast 33), bo „medale”
pomija medale spoza kampanii. Klif widoczny u „medali” może więc być u realnego gracza
łagodniejszy — po to jest M2. Różnica „przed” vs „teraz” to 1–2 poziomy, czyli model
przewiduje −1…−3 pp na etapach 19–30.

## Jak czytać wynik

Podsumowanie ma cztery części:

1. **Etapy 16–24 per profil:** wygrane / mediana / oczekiwany czas / ★ oraz poziomy badań
   profili medalowych.
2. **Klif 18 → 19:** dla każdego profilu spadek wygranych [pp], iloraz oczekiwanych czasów
   19/18 i ocena (kryterium niżej). Przy wygranych 19 podany jest ±95% (N=100: ok. ±10 pp
   przy 50%).
3. **M2 przed vs teraz:** poziomy, wygrane, różnica [pp] i oczekiwany czas dla 16–30;
   oznaczenie przy spadku > 5 pp.
4. **M3:** wygrane i oczekiwany czas etapu 19 dla każdego mnożnika i profilu, iloraz do
   etapu 18, wygrane etapu 20 i cele R12.

### Proponowane kryterium „ściany”

Oczekiwany czas przejścia (`expected`) jest lepszą miarą niż sam odsetek wygranych: łączy
szansę i długość bitwy, czyli to, ile minut gracz stoi w miejscu.

| Ocena | Warunek na etapie 19 (wystarczy jeden) |
|---|---|
| **Ściana** | `expected(19) / expected(18)` > **2,0**, albo wygrane spadają o > **25 pp** względem 18, albo wygrane 19 < **40%** |
| Strefa ostrzegawcza | iloraz > **1,6**, albo spadek > **15 pp**, albo wygrane 19 < **50%** |
| Płynnie | żaden z powyższych |

- **Który profil decyduje:** `medale-czas, teraz` (realny gracz: medale z kampanii i z gry
  dziennej przy 45 min/dzień). „Medale” to dolna granica (gracz bez nagród dziennych):
  ściana tylko u „medali”, a u `medale-czas` strefa ostrzegawcza → łagodzić; ściana tylko
  u „medali”, a u `medale-czas` płynnie → nie ruszać etapu 19, opisać w `BALANCE.md`.
  Profil docelowy (40% badań) i docelowy z dowódcą klifu nie pokażą (przeskok 10% → 40%),
  służą do sprawdzenia, że korekta nie zrobi etapu 19 za łatwym.
- **Dlaczego te progi:** 2× to próg R12 (M1). 25 pp przy N=100 to ponad 3 odchylenia
  standardowe różnicy dwóch odsetków (ok. 7 pp), więc nie jest szumem; 15 pp to ok. 2σ.
  1,6× to cel po korekcie z R12 (M3). Etapy 2–18 po skróceniach mają oczekiwany czas
  ok. 9–18 min, więc etap 19 powyżej ok. 25 min to „dwa etapy w jednym”.
- **M2:** spadek wygranych „teraz” względem „przed” o > 5 pp na którymś z 19–30 →
  ten etap do lekkiego złagodzenia (R12). Pojedyncze −5…−7 pp przy N=100 to jeszcze szum;
  istotny jest wzór (kilka etapów z rzędu albo > 10 pp).

### Co dalej po wyniku

- **Płynnie / ostrzegawcza tylko u „medali”:** bez zmian w `:sim`; wpis do `BALANCE.md`
  (tabela M1/M2, opis ekonomii: medale na etap vs na godzinę, farma etapów 13–18).
- **Ściana albo strefa ostrzegawcza u `medale-czas`:** M3 i wybór **najmniejszej** korekty,
  która spełnia: `medale` na 19 ≥ 60%, iloraz `medale-czas` ≤ 1,6×, docelowy na 19 ≤ 75%
  (dziś 60%) i docelowy nie łatwiejszy niż na 18. Korekta w `Campaign.stageRamp`
  (`19 -> 1.265f`) = 1,265 × mnożnik (0,94 → 1,189, 0,90 → 1,139, 0,86 → 1,088).
  Uwaga z `BALANCE.md` (etap 16): wartość wpisana do kodu może dać inne wyniki niż mnożnik
  w teście (zaokrąglenie `aiPower`), więc po zmianie trzeba zmierzyć stan z kodu. To osobne
  zadanie: PROTOCOL 0.8.8 → 0.8.9, regeneracja złotych powtórek, kontrola determinizmu
  w `SecondHourControlTest` przeniesiona z etapu 19 na 21 — i **przed** pierwszym AAB.
