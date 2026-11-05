# Dzień 57 — ankieta końcowa w 10. dniu testu

Baza: `origin/main`. Gałąź: `claude/dzien-57-ankieta-koncowa`. R13 TOP 5 poz. 5 (`docs/research/R13-testy.md`).

## Co gra robi

Tylko w wersjach testowych (`Game.testerSurveyEnabled` = `BuildConfig.TESTER_SURVEY`, ten sam warunek co
ankieta z part200 i przypomnienie z d48). Wersja produkcyjna: bez banera, bez pytań, raport bez zmian (format 1).

- **Jeden baner 10. dnia.** Od 10. dnia testu (dzień liczony jak w d48, `today − firstDay + 1`, UTC) baner
  przypomnienia z d48 ma tekst „Dzień 10: 3 pytania i raport” (`tr205_banner`). Nie ma osobnego banera
  ankiety ani drugiego przypomnienia. Baner pokazuje się w pasie powiadomień tylko w menu, czyli nigdy
  nad bitwą ani na wynikach. Czeka na spokojne menu (1,5 s, bez okien, dymków, banerów i zaległej
  pierwszej ankiety).
- **Dotknięcie banera** otwiera ankietę (ten sam panel co ankieta testera):
  1. „Czy polecisz Century War znajomym?” 0–10. Przyciski mają ≥ 48 dp: na szerokim ekranie 2 rzędy
     po 6, na wąskim 3 rzędy po 4. Pod spodem skala „0 = na pewno nie, 10 = na pewno tak”.
  2. „Co najbardziej się podoba?”: kampania, bitwy i jednostki, epoki i badania, wyzwania dnia,
     grafika i dźwięk, nic szczególnego.
  3. „Co najbardziej przeszkadza?”: za trudno, za łatwo, za długie bitwy, niejasne zasady, wolny postęp,
     nic, jest dobrze.

  Po ostatniej odpowiedzi albo „Pomiń” (Wstecz = Pomiń) gra od razu otwiera to samo okno „Raport dla
  testera” co baner z 3. dnia. Nic nie jest wysyłane automatycznie.
- **Raz.** Bit `REMINDER_FINAL` (4) w `reminders` zapisuje się przy pokazaniu banera, razem z bitami
  3. i 10. dnia. Zignorowany baner nie wraca. Odpowiedź i pominięcie są zapisywane od razu.
- **Bez dwóch ankiet jednego dnia.** Gdy pierwsza ankieta (part200) była tego samego dnia UTC (np. tester
  zaczął grać później), dziś nie pokazujemy żadnego banera. Baner z ankietą pojawi się przy następnym
  wejściu kolejnego dnia.
- **Aktualizacja w trakcie testu:** jeśli baner z 10. dnia z d48 już był (bit 2), a ankiety końcowej
  nie było, gra pokaże ją raz.
- Daty odpowiedzi biorą czas z zegara gry (`g.nowMillis()`), także w pierwszej ankiecie. Bez zmiany
  zachowania: domyślnie to zegar systemowy.

## Dane

Plik `playtest.json` ma nowe pola opcjonalne (`VERSION` bez zmian; starszy plik wczytuje się bez nich):
- `final`: 1 = odpowiedź, 2 = pominięta;
- `finalNps`: 0–10;
- `finalLiked`, `finalIssue`: indeksy odpowiedzi;
- `finalDay`: dzień UTC.

Złe wartości przy wczytaniu:
- ocena spoza 0–10 zmienia stan na „pominięta”;
- indeks spoza listy jest usuwany.

Raport dla testera (tylko format 2) ma stałą sekcję po angielsku, zaraz po `[Tester survey]`.
Dzięki temu nie znika przy obcięciu raportu do 6000 znaków:

```
[Final survey]
Recommend (0-10): 8 (2025-11-19)
Liked most: campaign
Bothered most: battles too long
```

Inne warianty sekcji:
- `Skipped (data)` — tester pominął ankietę;
- `Not asked yet` — ankiety jeszcze nie było;
- `Liked most: skipped` i `Bothered most: skipped` — tester ocenił, ale pominął pytanie z listy.

Etykiety odpowiedzi: `PlaytestLog.FINAL_LIKED` i `PlaytestLog.FINAL_ISSUES`.

## `tools/playtest_merge.py`

- Parser czyta `[Final survey]` (sekcja opcjonalna; raporty v2 z d48 dalej mają status „ok”). „Skipped”
  i „Not asked yet” w tej sekcji nie nadpisują pierwszej ankiety. Polecenie > 10 oznacza raport jako
  uszkodzony. Działa też odtwarzanie linii dla raportu wklejonego w jedną linię.
- CSV: kolumny `final_nps`, `final_liked`, `final_issue`.
- Markdown: sekcja „Ankieta końcowa (10. dzień)”. Pokazuje rozkład 0–10, liczbę pominięć i raportów
  bez ankiety, medianę, NPS (9–10 minus 0–6) z dopiskiem, że przy kilkunastu osobach to tylko orientacja,
  oraz liczniki odpowiedzi z obu list. Sekcja pojawia się tylko wtedy, gdy któryś raport ma ankietę.
- Testy: strażnik etykiet i list (porównanie z `PlaytestReportText.kt` i `PlaytestLog.kt`), warianty
  sekcji, NPS, Markdown i CSV.

## Teksty

`part205` w `L.kt` (10 języków):
- `tr205_banner`, `tr205_title`, `tr205_recommend`, `tr205_scale`, `tr205_nps_n` (TalkBack);
- `tr205_liked` i 6 × `tr205_like_*`;
- `tr205_issue_easy`, `tr205_issue_slow`.

Pozostałe odpowiedzi i „Pomiń” biorą teksty z part200. Banery z 3. dnia i powiadomienia zostają bez zmian.

## Testy

- Nowe: `screens/TesterFinalSurveyTest` (9 testów):
  - próg i jednorazowość;
  - przebieg baner → 3 pytania → raport;
  - Pomiń i Wstecz;
  - dzień pierwszej ankiety i wersja produkcyjna;
  - aktualizacja po d48;
  - zapis i odczyt pliku;
  - TalkBack;
  - teksty w 10 językach;
  - układ na phone720, foldOuter i foldInner, tekst 100% i 130%, 10 języków (baner, panel, przyciski
    ≥ 48 dp, napisy ≥ 10 sp).
- Nowe: `screens/TesterFinalSurveyScreenshotTest` (kategoria screenshots).
- `TesterReportReminderTest`: 10. dzień ma teraz baner z ankietą (bit `REMINDER_FINAL`).
- `:app:testDebugUnitTest --tests` `*Playtest*`, `*TesterReport*`, `*Survey*`, `*Menu*` → 20 klas,
  **64 testy OK**.
- `python3 -m unittest discover -s tests` → **71 OK**.

Lint, fastTest i `./gradlew build` nie były uruchamiane.

## Zrzuty (`/tmp/claude-0/d57/`)

| Ekran | phone720 | foldOuter |
|---|---|---|
| Baner | `d57_banner_phone720_{pl,en}.png` | `d57_banner_foldOuter_{pl,en}.png` |
| Polecenie 0–10 | `d57_recommend_phone720_{pl,en}.png` | `d57_recommend_foldOuter_{pl,en}.png` |
| Co się podoba | `d57_liked_phone720_{pl,en}.png` | `d57_liked_foldOuter_{pl,en}.png` |

## Do decyzji / uwagi

- Ankieta jest dostępna tylko z banera, raz. Tester, który zignoruje baner, nie dostanie pytań. Tak
  jest celowo, żeby nie męczyć testera; jego raport ma wtedy `Not asked yet`.
- Polityka prywatności i Data safety bez zmian. Odpowiedzi to ten sam lokalny dziennik testu
  i ręcznie udostępniany raport co przy ankiecie z part200.
