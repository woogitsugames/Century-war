# Dzień 60 — szlif wizualny

Gałąź: `claude/dzien-60-szlif` (baza `origin/main`).

## Zakres przeglądu

Zrzuty z `PrereleasePolish*` (pełna trasa: menu, kampania z oknem etapu, Endless, Dzień/Tydzień, potyczka,
badania, misje, rekordy, statystyki, historia, powtórki, osiągnięcia, ranking globalny, kodeks z kategoriami,
ustawienia, dostępność, podpowiedzi, sztandary, Play Games, O grze, Co nowego, prywatność, bitwa: HUD, wieże,
ulepszenie, doktryny, pauza, wyniki, wykres, cel, wariant epoki, karty Endless) na czterech urządzeniach:

| urządzenie | rozdzielczość | języki | 130% tekstu |
|---|---|---|---|
| telefon 720p | 1600×720 | pl, en, de | tak (dostępność, HUD, pauza) |
| telefon 20:9 | 2400×1080 (wycięcie 96 px z lewej) | pl, en, de | — |
| Fold rozłożony | 2184×1968 | pl, en, de | — |
| Fold zewnętrzny | 904×2316 | pl, en, de | tak |

Obejrzane arkusze (ok. 540 PNG) + raporty audytu `pre10_audit_*.txt`. Na końcu przeszły klasy
`Screenshot*`, `Prerelease*`, `Localization*`, `Codex*` (wynik niżej).

Zrzuty:
- przed: `/tmp/claude-0/d60/przed/pre10_<urządzenie>_<język>_<nr>_<ekran>.png` (+ `pre10_audit_*.txt`),
- po: `/tmp/claude-0/d60/po/` (te same nazwy),
- arkusze przeglądowe: `/tmp/claude-0/d60/sheets/`, porównania przed/po: `/tmp/claude-0/d60/cmp_*.png`.

## Usterki naprawione

| # | usterka | gdzie | poprawka | zrzut (przed → po) |
|---|---|---|---|---|
| 1 | Wstęga „Pauza” wystawała ponad górną krawędź ekranu | 720p, 20:9 (100% i 130%) | `BattleScreen.layoutPause`: górny margines = połowa wstęgi + 6 dp (zależny od rozmiaru tekstu), dolny bez zmian | `pre10_phone720_pl_42_bitwa_pauza.png`, `cmp_phone720_pl_42_bitwa_pauza.png` |
| 2 | Wstęga „Wybierz wariant epoki” ucięta u góry | 720p, 20:9 | karty wariantów zaczynają się nie wyżej niż 27 dp + połowa wstęgi + 8 dp; wysokość dopasowana do dołu | `pre10_phone720_de_46_bitwa_wariant_start.png` |
| 3 | Opis wariantu dotykał lamówki karty (de „…Durchbruch.” od ramki do ramki, Fold zewn. „Kruche konstrukcje o”) | wszystkie, najwyraźniej de i Fold zewn. | margines jak w kartach doktryn (`CHOICE_TEXT_PAD`), łamanie z dywizem jak w kartach doktryn | `cmp_phone720_de_46_bitwa_wariant_start.png`, `pre10_foldOuter_pl_47_bitwa_wariant_start.png` |
| 4 | Trzeci wiersz opisu karty Endless/doktryny wchodził na dolną lamówkę („(do 40%).”, de „Feinden (max. 40 %).”) | 720p, 20:9 | gdy opis się nie mieści, medalion maleje (min. 18 dp), a treść podnosi się o brakujące miejsce; jedno-/dwuwierszowe karty bez zmian | `cmp_phone720_pl_47_endless_karty.png` |
| 5 | Ranking globalny: zakładki wyśrodkowane na całym ekranie, pierwsza poza bezpiecznym obszarem (audyt „POZA BEZPIECZNYM OBSZAREM”) | 20:9 | środek liczony z obszaru bez wycięć | `cmp_phone20x9_pl_16_ranking_globalny.png` |
| 6 | Ranking globalny: przycisk okresu „Całość” leżał na ramie panelu | wszystkie | filtry odsunięte od lamówki (`PANEL_TRIM_INSET + 6 dp`), napis i lista przesunięte pod nie | jw., `pre10_foldOuter_pl_17_ranking_globalny.png` |
| 7 | Podtytuł logo „Wojna przez stulecia” 7,8 dp (audyt „DROBNY TEKST”) | 720p, 20:9, pl/en/de | co najmniej 9 dp | `cmp_phone720_pl_00_menu.png` |
| 8 | Okno etapu: wstęga „Etap 1” na przyciemnionej wstędze „Kampania” (dwie wstęgi jedna na drugiej) | 720p, 20:9 | tytuł ekranu nie jest rysowany, gdy wchodziłby pod wstęgę okna (Fold bez zmian) | `cmp_phone720_pl_02_kampania_etap1.png` |
| 9 | Prywatność: tekst przewijalny bez żadnej wskazówki, ostatni akapit przy ramce; panel wyśrodkowany bez wycięć | 720p, 20:9 | strzałka `ScrollCue` jak w „Co nowego”/ustawieniach, 20 dp miejsca na nią z prawej; środek z bezpiecznego obszaru | `pre10_phone720_pl_36_prywatnosc.png` |

Audyt napisów po zmianach: zniknęły wszystkie „DROBNY TEKST 7.8 dp” w menu i wszystkie „POZA BEZPIECZNYM
OBSZAREM” w rankingu globalnym; nowych wpisów brak.

Wzorce `regression100` zaktualizowane (zamierzone różnice; wpis w `app/src/test/resources/screens/UPDATES.md`):
`phone720_reg100_menu` (podtytuł), `phone720_reg100_battle_pause`, `foldOpen_reg100_battle_pause` (położenie panelu),
`phone720_reg100_campaign_info` (bez tytułu pod oknem). Przy okazji odświeżyły się `*_reg100_achievements`
(licznik „0/68” → „0/79”: wzorzec był sprzed nowych osiągnięć, różnica mieściła się w tolerancji).

## Usterki zostawione (z powodem)

| usterka | gdzie | powód |
|---|---|---|
| Baner „Seria dni 1/7 · Dziś +5 medali” przykrywa nagłówek treści (kampania: nazwa epoki, dostępność: „Paleta barw”) | wszystkie, zwłaszcza Fold zewn. i 130% | baner trwa 2,9 s i z założenia leży pod wstęgą tytułu (tura 9); stałe miejsce na niego zmieniłoby układ wszystkich ekranów |
| Zakładki kodeksu (kategorie, epoki) ucięte na krawędzi paska (audyt „UCIĘTY TEKST”) | wszystkie | pasek przewijany w poziomie ze strzałką — ucięcie pokazuje, że jest dalej (zatwierdzony szlif 2) |
| „Od nowa”/„Neustart” mniejszym pismem niż sąsiednie przyciski wyników | 720p, 20:9 | celowy podział szerokości 26/44/30% (noc 3/8); przy równych przyciskach maleje „Obejrzyj powtórkę”; ekran wyników bez zmian zgodnie z zasadą |
| Tytuły-wstęgi zmniejszone na Foldzie rozłożonym („Ranking globalny”, „Podpowiedzi i samouczek”) i drobna pierwsza zakładka rankingu („Oblężenie bez końca”) | Fold rozłożony | reguła szerokości wstęgi (`Chrome.titleMaxW`) jest wspólna dla wszystkich ekranów i wzorców; zmiana wymaga osobnej decyzji |
| Duże puste pola w panelach (Wyzwanie dnia na Foldzie, wyniki na Foldzie zewn., karty wariantów) | Fold | przyciski zakotwiczone u dołu z zamysłu (stałe miejsce kciuka); dopełnianie treścią to zmiana projektu |
| Bardzo małe sylwetki jednostek w kartach wariantu | Fold zewn. | karty ~135 dp szerokości; większe sylwetki wymagają układu pionowego kart (osobne zlecenie) |
| „Trudność” tuż pod wierszem „Modyfikatory” w oknie etapu | 720p | odstęp ~6 dp, bez nakładania; panel 302 dp nie ma zapasu bez zmiany wysokości wierszy |
| Liczba „70813” 8,5 dp pod paskiem HUD | Fold zewn., wariant epoki | HUD bitwy (`compactTop`) — nie zmieniamy bez zgody |

## Zasady

Bez zmian: układ `compactTop`, celowanie ostrzału, wyniki bitew (logika), powiadomienia na wynikach, nic nowego dla TalkBack.

## Testy

`./gradlew :app:testDebugUnitTest` z klasami `CodexCompareModelTest`, `CodexCompareScreenshotTest`,
`CodexFoldOuterLabelsTest`, `CodexLTest`, `Localization120ScreenshotTest`, `Localization136ScreenshotTest`,
`PrereleasePolish{,Phone20x9,FoldOuter,FoldInner}Test`, `ScreenshotGoldenTest`, `ScreenshotTestBattle`,
`ScreenshotTestFold`, `ScreenshotTestLayout`, `ScreenshotTestMenus`: 15 klas, 63 testy, 0 błędów
(po aktualizacji wzorców `regression100`).
