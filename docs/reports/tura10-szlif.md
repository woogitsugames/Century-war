# Tura 10 — szlif przed premierą (przegląd UX całej gry)

Test: `app/src/test/java/com/centurywar/screens/PrereleasePolishTest.kt`.
Zrzuty: `app/build/screenshots/pre10_<urządzenie>_<język>_<nr>_<ekran>.png`, dziennik
`pre10_audit.txt`. Urządzenia: telefon 1600×720 d=2 (`phone720`), 2400×1080 d=2,7 z wycięciem
(`phone20x9`), Fold zewnętrzny 904×2316 d=2,7 (`foldOuter`), Fold wewnętrzny 2184×1968 d=2,7
(`foldInner`); języki pl, en, de; świeży profil (nowy gracz).

Ekrany: menu, kampania + okno etapu, Endless, wyzwanie dnia i tygodnia, ustawienia potyczki,
badania, misje, rekordy (rankingi, statystyki, historia, powtórki, osiągnięcia), ranking globalny,
kodeks (10 kategorii), ustawienia, dostępność, podpowiedzi, sztandary, Play Games, O grze,
„Co nowego”, prywatność, bitwa (HUD, panel wież, ulepszenie, doktryny, pauza, wyniki, wykres,
wybór wariantu epoki, karty Endless).

Nowe w audycie: płótno testowe zapisuje każdy napis (prostokąt, przycięcie, rozmiar), więc test
sam wykrywa napisy ucięte, nachodzące na siebie i drobniejsze niż 8,5 dp. Twarde asercje
(`require10`) pilnują tylko rzeczy naprawionych w tej turze.

## Tabela

| # | Priorytet | Problem | Ekran / zrzut | Stan |
|---|-----------|---------|---------------|------|
| 1 | Wysoki | Karta **Oblężenia tygodnia** na 720p: progi medali (Brąz/Srebro/Złoto) nachodziły na „Koniec za…” i „Najlepszy” — napisy nieczytelne | `pre10_phone720_*_05_tydzien` | Naprawione: odstępy wierszy dopasowują się do wysokości panelu, progi stoją pod treścią |
| 2 | Wysoki | Karty dnia/tygodnia na zewnętrznym ekranie Folda: dwie kolumny po ~120 dp, tekst zmniejszany do ~6 sp (nieczytelny), niemieckie „Bestwert…” wychodziło poza ekran | `pre10_foldOuter_*_05_dzien`, `_06_tydzien` | Naprawione: w wąskim panelu jedna kolumna, progi medali w osobnych wierszach |
| 3 | Średni | Karty dnia/tygodnia: pierwszy wiersz opisu dotykał zakładek Dzień/Tydzień; na 720p wiersz „Podejście rankingowe” wchodził na przycisk Graj | `pre10_phone720_de_04_dzien` | Naprawione |
| 4 | Średni | Okno etapu kampanii na 720p: „brak” (modyfikatory) wpadało pod napis „Trudność” | `pre10_phone720_*_02_kampania_etap1` | Naprawione: „brak” w wierszu „Modyfikatory:” |
| 5 | Średni | Baner powiadomień (np. „Seria dni 1/7”) zasłaniał tytuł i pierwszy wiersz okna etapu oraz kart Endless/dnia/tygodnia/potyczki (tytuł leży tam na panelu, nie na górze ekranu) | `pre10_*_03_endless`, `_02_kampania_etap1` (przed) | Naprawione: te okna wstrzymują baner (jak Misje i Osiągnięcia); komunikat pokaże się na następnym ekranie |
| 6 | Średni | Lista powtórek: jedno dotknięcie ✕ od razu kasowało powtórkę, bez pytania | `pre10_replay_00_usun_potwierdz` | Naprawione: pierwsze dotknięcie pyta („Dotknij jeszcze raz, by usunąć powtórkę”), ✕ się wyróżnia; inne dotknięcie anuluje |
| 7 | Średni | Lista powtórek blokuje baner powiadomień, więc komunikat „stara wersja” przy udostępnianiu nigdy się nie pokazywał (przycisk „nic nie robił”) | lista powtórek | Naprawione: komunikat na dole panelu |
| 8 | Średni | Pusta lista powtórek mówiła tylko „Brak zapisanych powtórek” — nowy gracz nie wie, skąd się biorą | `pre10_*_13_powtorki_pusto` | Naprawione: dopisek „Każda bitwa zapisuje się tu sama (20 ostatnich). Flaga przypina powtórkę na stałe.” |
| 9 | Średni | Misje: przy nagłówkach stało samo „UTC” — niezrozumiałe | `pre10_*_09_misje` | Naprawione: „Nowe za 07:04” / „Nowe za 6d 07:03” |
| 10 | Niski | Misje: „Seria dni” i liczniki „0/1”, „+16” leżały na złotej lamówce paneli | `pre10_*_09_misje` | Naprawione (przesunięcie o 4–6 dp) |
| 11 | Niski | Misja dzienna „Brązowe progi tygodnia: 1” — nie wiadomo, o co chodzi | misje | Naprawione (pl/en/de): „Brąz w Oblężeniu tygodnia: 1” |
| 12 | Niski | Podpowiedzi i samouczek na 720p: przyciski 41 dp (< 48 dp) | `pre10_phone720_*_30_podpowiedzi` | Naprawione: 48 dp |
| 13 | Niski | Podpowiedzi: wiersz „Pokaż ponownie” z przyciskiem „Reset podpowiedzi”, a wiersz „Zapowiedzi epok” z przyciskiem „Pokaż ponownie” — mylące | podpowiedzi | Naprawione (10 języków): wiersz „Podpowiedzi od nowa” |
| 14 | Niski | „Co nowego”: przycisk Wstecz/Dalej 44 dp | `pre10_*_co_nowego` | Naprawione: 48 dp |
| 15 | Niski | Menu na 720p: kafelki 46,8 dp | `pre10_phone720_*_00_menu` | Naprawione: minimum 48 dp |
| 16 | Niski | Wyniki po porażce: „Zapisano: 0 zabitych, postęp 1 misji” — niezgrabne | `pre10_phone720_pl_42_wyniki` | Naprawione (pl): „Zachowano: zabici 0, misje z postępem 1” |
| 17 | Niski | Kodeks › Warianty dla epoki bez wariantów: „Brak wariantów” dwa razy (pod nazwą epoki i w liście) | `pre10_phone720_pl_18_kodeks_2` | Naprawione (szlif 2): napis tylko w nagłówku, z opisem pod spodem |
| 18 | Niski | Kodeks: zakładka „Epoka stali” i niemieckie „Fähigk./Waffenk.” bardzo pomniejszone/ucięte na wąskich ekranach | `pre10_*_kodeks*` | Naprawione (szlif 2): zakładka częściowo schowana za krawędzią listwy rysuje się w pełnym rozmiarze (12,5–13,5 sp) i jest tylko przycinana |
| 19 | Niski | Rekordy › Rankingi na 720p: zakładka „Oblężenie bez końca” zmniejszona do drobnego pisma | `pre10_phone720_pl_10_rankingi` | Naprawione (szlif 2): krótka etykieta „Oblężenie” (10 języków, de „Endlos”) |
| 20 | Niski | Endless na Foldzie wewnętrznym: ogromny pusty panel (2 wiersze tekstu i Graj) | `pre10_foldInner_*_03_endless` | Naprawione (szlif 2): na ekranach od 480 dp wysokości panel ma wysokość treści |
| 21 | Niski | Misje: nagłówki „Misje dzienne/tygodniowe” stoją na tle krajobrazu bez podkładu | misje | Naprawione (szlif 2): nagłówki mają własny podkład |
| 22 | Niski | Menu 720p: baner serii dni dotyka górnej krawędzi kafelka Kampania | `pre10_phone720_pl_00_menu` | Naprawione (szlif 2): siatka kafelków zsuwa się o brakujące miejsce, odstęp 6 dp |
| 23 | Niski | Lista powtórek w trakcie ładowania: „Wczytywanie powtórki…” (l. poj.; ten sam tekst co w odtwarzaczu) | lista powtórek | Naprawione (szlif 2): „Wczytywanie powtórek…” na liście, odtwarzacz bez zmian |
| 24 | Info | „O grze” pokazuje wersję `0.1.0-dev (1)` | `pre10_*_o_grze` | Sprawdzić w buildzie wydania |

Bez uwag: HUD bitwy i oba paski (zatwierdzony układ), panel wież, ulepszenie jednostki,
doktryny, pauza z szybkimi ustawieniami, wyniki i wykres, wybór wariantu epoki, karty Endless,
ustawienia, dostępność, sztandary, Play Games (stan „Niedostępne” z wyjaśnieniem), puste
statystyki/historia/rankingi (mają komunikat „zagraj!”). Na zrzutach pl/de nie widać tekstów
angielskich. Przegląd kodu: „Wstecz” na każdym ekranie zdejmuje ekran ze stosu (zakładki Rekordów
podmieniają ekran bez dokładania do stosu), więc wraca tam, skąd gracz przyszedł.

## Zmienione pliki

- `ModeScreen.kt` — karta dnia i tygodnia (odstępy, jedna kolumna w wąskim panelu), blokada banera.
- `CampaignScreen.kt` — „brak” w wierszu modyfikatorów, blokada banera przy otwartym etapie.
- `MissionsScreen.kt` — czas do odnowienia, odsunięcie tekstów od lamówki.
- `ReplayListScreen.kt` — potwierdzenie usuwania, komunikaty na ekranie, podpowiedź pustej listy.
- `HintSettingsScreen.kt`, `WhatsNewScreen.kt`, `MenuScreen.kt` — cele dotyku 48 dp.
- `L.kt` — `part150()` (3 nowe teksty, 10 języków) i poprawki w miejscu: `battle84_kept` (pl),
  `hint_reset_label` (10 języków), `ms_weekly_bronze` (pl/en/de). Klucze doktryn nietknięte.

Wzorce PNG (`reg100`) bez zmian — poprawione ekrany nie są w zestawie wzorców.

## Szlif 2 (zatwierdzone poprawki 17–23)

- **Kodeks** (`CodexScreen.kt`): w Wariantach dla epoki bez wariantów „Brak wariantów” pojawia się raz
  (w nagłówku, z opisem pod spodem; zniknął zdublowany wiersz listy). Zakładki kategorii i epok były
  zmniejszane, bo napis dopasowywał się do widocznego kawałka przyciętego przycisku. Teraz przycisk
  rysuje się w pełnej szerokości i listwa go tylko przycina, a obszar dotyku nadal kończy się na
  krawędzi listwy. Na 720p i na zewnętrznym ekranie Folda zakładki mają 12,5/13,5 sp
  (`pre10_phone720_pl_19_kodeks_2`, `pre10_foldOuter_de_23_kodeks_5`).
- **Rekordy** (`RankingsScreen.kt`): krótka etykieta `endless151_tab` („Oblężenie”, en „Endless”, de „Endlos”,
  uk „Облога”, tr „Kuşatma”), gdy pełna nazwa się nie mieści (`pre10_phone720_pl_10_rankingi`,
  `pre10_banner720_uk_01_rankingi`, `pre10_banner720_tr_01_rankingi`). Na zewnętrznym ekranie Folda
  (pięć zakładek po ~55 dp) etykiety mają ~10,5 sp; ten przypadek zostaje tylko w dzienniku audytu.
- **Endless** (`ModeScreen.kt`): na ekranach o wysokości od 480 dp (oba Foldy) panel ma wysokość
  treści (opis, rekord fali, wynik, Graj). Telefony mają poziomo najwyżej ~400 dp, więc ich układ
  się nie zmienia (`pre10_foldInner_*_03_endless`, `pre10_foldInner36_*_00_endless`, `pre10_foldOuter_*_04_endless`).
- **Menu** (`MenuScreen.kt`): na 720p pas na baner miał ~11 dp przy banerze o wysokości 24 dp. Siatka kafelków
  zsuwa się o brakujące miejsce, więc między banerem a Kampanią jest 6 dp (`pre10_banner720_pl_00_menu`).
- **Misje** (`MissionsScreen.kt`): podkład pod nagłówkami „Misje dzienne/tygodniowe” (`pre10_*_misje`).
- **Powtórki** (`ReplayListScreen.kt`): lista w trakcie wczytywania pokazuje `replay151_list_loading`
  („Wczytywanie powtórek…”); odtwarzacz zostaje przy `replay_loading` (`pre10_phone720_*_14_powtorki_wczytywanie`).
- `L.kt`: `part151()` (2 klucze, 10 języków).
- `PrereleasePolishTest`: nowe asercje `polish2` (zakładki kodeksu ≥ 11,5 sp, jedno „Brak wariantów”,
  zakładka Oblężenia ≥ 11 sp, Endless na wysokim ekranie bez pustki nad Graj, baner ≥ 6 dp od kafelków,
  podkład nagłówka misji, tekst wczytywania listy) oraz test `polish2EndlessFoldAndMenuBanner`
  (Fold 1802×2000 d=3,6 i baner 720p w pl/en/de/tr/uk).
