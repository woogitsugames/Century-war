# Noc 3 — przegląd UX na urządzeniach (po fali 7 i zawartości 170)

Test: `app/src/test/java/com/centurywar/screens/NightUxTest.kt`.
Zrzuty: `app/build/screenshots/noc3_<urządzenie>_<język>_<nr>_<ekran>.png`, dziennik audytu
`noc3_audit.txt`, zestawienie wydarzeń dnia `noc3_wydarzenia.txt`.

Urządzenia: telefon 1600×720 d=2 (`phone720`), 2400×1080 d=2,7 z wycięciem (`phone20x9`), niski
1200×720 d=2 (`low1200`), Fold zewnętrzny 904×2316 d=2,7 (`foldOuter`), wewnętrzny 2184×1968 d=2,7
(`foldInner`). Języki pl, en, de (wszystkie ekrany), tr i uk (ekrany z tekstami 170). Panel wież
także w układzie leworęcznym (`*_pl_lewo_50_wieze`).

Ekrany: wyzwanie dnia z trzema nowymi wydarzeniami (Dzień inżynierów, Forsowny marsz, Łupy wojenne;
dni od `Daily.EVENTS_V2_DAY`), osiągnięcia z dwoma nowymi, „Co nowego” (wpis 170), karty Endless
(Kwatermistrz, Mistrzowie murarscy, Ekonomia), ustawienia i dostępność (góra i dół), panel wież
(dwie wieże kupione, dwa sloty puste), wyniki bitwy.

## Tabela

| # | Priorytet | Problem | Ekran / zrzut | Stan |
|---|-----------|---------|---------------|------|
| 1 | Wysoki | **Dzień inżynierów** (wieże tańsze i szybsze) losuje się razem z modyfikatorem **„Bez wieżyczek”** — opis wydarzenia nie ma sensu. Dotyczy już pierwszego takiego dnia (dzień 21164); w 2 latach 3 z 18 dni, plus 1 dzień z „Drogie wieże” i 1 Forsowny marsz z „Szybkie jednostki” | `noc3_*_00_dzien_siege_works`, `noc3_wydarzenia.txt` | **Do decyzji** (zmiana losowania w `sim/`, poza zakresem nocy) → noc 4: naprawione od `EVENTS_V2_DAY` (`Daily.EVENT_CONFLICTS`, BALANCE.md) |
| 2 | Wysoki | Karty Endless/doktryn na zewnętrznym ekranie Folda: trzy karty po ~90 dp, opisy łamane w pół słowa („Geschützt-ürme”, „Goldeinko-mmen”), nazwa „Quartiermeister” zmniejszona, wstęga „Wähle eine Karte” wychodziła za ekran | `noc3_foldOuter_*_05_endless_karty` | Naprawione: na wąskim pionowym ekranie karty stoją jedna pod drugą (do 340 dp szerokości), wstęga mieści się w ekranie |
| 3 | Średni | Osiągnięcia na zewnętrznym Foldzie: długie opisy (także nowe „Kalendarz wojny”, „Zaplecze frontu”) malały do 8 sp i wchodziły na medal i nagrodę „+300” | `noc3_foldOuter_*_03_osiagniecia_170` | Naprawione: opis, który zmalałby poniżej 10,5 sp, idzie w dwóch wierszach (11 lub 10,5 sp), pasek postępu niżej |
| 4 | Średni | Panel wież na zewnętrznym Foldzie: „Verkaufen 55”, „Kup 110”, „St. 2: 66” po 8 sp (przyciski 54–64 dp w wąskim panelu) | `noc3_foldOuter_*_10_wieze` | Naprawione: w wąskim panelu trzy przyciski po 80 dp, wyśrodkowane pod nazwą wieży; podpisy 9,5 sp jak na innych ekranach. Niski panel 161 i siatka 2×2 bez zmian |
| 5 | Średni | Wyniki bitwy na zewnętrznym Foldzie (de): „Wiederholung” obok ikony 8,2 sp, a Restart i Dalej mają ikonę nad podpisem | `noc3_foldOuter_*_11_wyniki` | Naprawione: gdy nawet krótki podpis się nie mieści, trzy równe przyciski z ikoną nad podpisem (~11 sp) |
| 6 | Niski | Łamanie wierszy przenosiło samotny znak na nowy wiersz („kosten 12” / „% weniger.”; fr „:” „!”) | karta Mistrzowie murarscy (de) | Naprawione w `HintFrame.wrapHintInto`: „%”, „:”, „;”, „!”, „?”, „»” trzymają się poprzedniego słowa |
| 7 | Niski | Niespójne nazwy wyzwania dnia: de „Tagesherausforderung” (ekran mówi „Tageschallenge”), tr „günlük meydan okuma” / „günlük görev” (ekran „Günlük Mücadele”; „görev” to misja), uk „щоденне випробування” (ekran „Виклик дня”), fr/it/es/nl drobne różnice w osiągnięciu „Sześć wydarzeń dnia” | osiągnięcia, powiadomienie, kodeks (dowódca) | Naprawione w miejscu: `ach_daily_events_6_d` (8 języków), `ach_daily_events_9_d` (de, nl, tr), `notification_body` (uk), `comm125_codex` (de) |
| 8 | Niski | Wyniki (de): „Gespeichert: 0 Kills” (angielskie słowo; wiersz wyżej „Abschüsse”) i „Replay ansehen” (wszędzie indziej „Wiederholung”) | `noc3_*_de_11_wyniki` | Naprawione: „Abschüsse”, „Wiederholung ansehen” |
| 8a | Niski | Nowe teksty 170 mówią „wieżyczki” (pl) i „Geschütztürme” (de), a cała gra i panel — „Wieże”/„Türme” (także modyfikator „Bez wież”) | wyzwanie dnia, karta Mistrzowie murarscy | Naprawione w miejscu: `day_siege_works_d`, `ec_master_masons_d` (pl, de) |
| 9 | Niski | Panel wież w siatce 2×2 i niskim (161): numer pustego slotu narysowany dwa razy — 8 sp w medalionie i obok | `noc3_phone720_*_10_wieze`, `noc3_low1200_*` | Do decyzji (zatwierdzona siatka 720p; wystarczy usunąć cyfrę z medalionu) → noc 4: cyfra usunięta z medalionu |
| 10 | Niski | Dwie osobne opcje drgań ekranu: Ustawienia › „Wstrząsy ekranu” (pełne/słabe/wył.) i Dostępność › „Mniej wstrząsów” | `noc3_*_07_ustawienia_dol`, `noc3_*_09_dostepnosc_dol` | Do decyzji (połączyć albo opisać zależność) → noc 4: podpisy przy obu opcjach (`part172`) |
| 11 | Niski | „Fala 5” w górnym pasku bitwy na zewnętrznym Foldzie ma 8 sp | `noc3_foldOuter_*_05_endless_karty` | Bez zmian (zatwierdzony `compactTop`) |
| 12 | Info | Karty Endless na 720p (de): trzeci wiersz opisu stoi tuż nad dolną lamówką karty | `noc3_low1200_de_05_endless_karty` | Mieści się (test `assertChoiceCards`); obserwować przy dłuższych opisach |

Bez uwag: wyzwanie dnia z nowymi wydarzeniami na wszystkich rozmiarach (opis w jednym lub kilku
wierszach, ikony: wieża, błyskawica, sakwa), „Co nowego” z wpisem 170 (przewijanie do końca),
Dostępność (cztery przełączniki 48 dp po przewinięciu), panel wież 161 na 1200×720 (modal, przyciski
48 dp) i w układzie leworęcznym, karty Endless na telefonach i Foldzie wewnętrznym. Fala 7 nie dodała
nowych opcji w Ustawieniach ani Dostępności (164 to opisy TalkBack, 160 cykl życia). Teksty `part170`
w tr/uk mieszczą się na wszystkich ekranach. Cele dotyku: audyt nie zgłosił przycisków < 48 dp.

## Zmienione pliki

- `BattleScreen.kt` — układ kart w kolumnie na wąskim pionowym ekranie, szerokość wstęgi kart.
- `AchievementsScreen.kt` — opis w dwóch wierszach zamiast pomniejszania do 8 sp.
- `BattleUpgradePanel.kt` — szersze przyciski wież w wąskim (niemodalnym) panelu.
- `BattleResultsPanel.kt` — równe przyciski wyników, gdy podpis powtórki się nie mieści.
- `HintFrame.kt` — „%”/„:” nie zaczynają wiersza.
- `L.kt` — poprawki w miejscu (pkt 7–8). Nowych kluczy brak, więc bez `part171()`.
- `NewPlayerJourneyTest.kt` — sprawdzenie podpisu powtórki uwzględnia układ z ikoną nad podpisem.
- `NightUxTest.kt` — nowy test: `nightTour` (zrzuty i twarde asercje punktów 2–5), `consistentTerms`
  (pkt 7–8), `dailyEventConflictsReport` (zestawienie do pkt 1).

Wzorce PNG (`reg100`) bez zmian — poprawione ekrany nie są w zestawie wzorców.
