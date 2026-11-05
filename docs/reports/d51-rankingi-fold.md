# Dzień 51: zakładki tablic Rankingów na zewnętrznym Foldzie

Ostatnia pozycja z `d36-fold-napisy.md` („Zostało”): pięć zakładek tablic w Rankingach na zewnętrznym
Foldzie (904×2316, 2,7 dp) miało po ok. 56 dp i podpis pod ikoną ok. 10,5 sp przy 100%, a przy 130%
mniej albo samą ikonę.

## Zmiana (`RankingsScreen.layout`)

- Tylko na wąskim pionie (szerokość < 420 dp i ekran wyższy niż szeroki, jak w `RecordsTabs`).
- Jeden rząd z ikonami zostaje, gdy każdy podpis z `Chrome.tabLabel` mieści się w 10 sp z zapasem
  jednego kroku `Ui.fit` (10 / 0,96 sp; ta sama reguła co w d43).
- W przeciwnym razie zakładki stoją w siatce bez ikon: 3 + 2, a gdy któryś podpis (pełny ani skrót)
  nie mieści się w 10 sp, 2 + 2 + 1 (ostatnia na całą szerokość). W komórce pełna nazwa, a gdy się nie
  mieści, skrót. Panel tablicy przesuwa się pod ostatni rząd.
- TalkBack: podpis nigdy nie jest pusty (`accessibilityLabel` = `label`), więc zakładki bez
  `tooltipTitle` zachowują etykietę. `Chrome.tabLabel` bez zmian.
- Telefon i rozłożony Fold: bez zmian (jeden rząd z ikonami).

Na Foldzie zewn. przy 130% (de, tr) wychodzi siatka 2 + 2 + 1 z pełnymi nazwami
(„Endlose Belagerung”, „Kampagne — Sterne”, „Günlük Mücadele”…).

## Test

`CodexFoldOuterLabelsTest.boardTabsReadableOnFoldOuter`: 10 języków × 100/130%. Każda zakładka
tablicy ma podpis ≥ 10 sp, napis mieści się w zakładce, zakładki nie nachodzą na siebie, na zakładki
Rekordów ani na panel, mają niepustą etykietę TalkBack. Siatka musi się włączyć w co najmniej jednym
wariancie. 1600×720 i rozłożony Fold (2184×1968) zostają w jednym rzędzie z ikonami. Przy `CW_SHOT_DIR`
test zapisuje zrzuty de/tr 130%.

Uruchomione obejściem `tools/offline-build/` (`CW_OFFLINE_DIR=/tmp/claude-0/d51`, zależności
podlinkowane z d48): `CodexFoldOuterLabelsTest` + `RecordsUxTest` — OK (9 testów). Pełnego
`run_fast.sh` nie uruchamiałem (limit czasu).

Zrzuty: `/tmp/claude-0/d51/d51_rankingi_foldOuter_de130.png`, `/tmp/claude-0/d51/d51_rankingi_foldOuter_tr130.png`.

## Zostaje

- Siatka zajmuje 2 rzędy więcej (ok. 108 dp) kosztem listy wyników. Na Foldzie zewn. (ok. 860 dp
  wysokości) lista nadal mieści kilka wierszy i się przewija.
- Wynik offline nie gwarantuje zielonego `./gradlew build` (Kotlin 2.0.21 zamiast 2.2.20, brak lintu).
