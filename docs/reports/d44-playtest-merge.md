# Dzień 44 — narzędzie scalania raportów testerów (R13, TOP 5 poz. 4)

Baza: `integ/tura10`. Gałąź: `claude/dzien-44-playtest-merge`. Kod gry bez zmian.

## Co powstało

| Plik | Co robi |
|---|---|
| `tools/playtest_merge.py` | Scala „Raporty dla testera” i ocenia bramkę G2. Tylko Python 3 (biblioteka standardowa). |
| `tests/test_playtest_merge.py` | 13 testów `unittest`. Dołącza do `python3 -m unittest discover -s tests`. |
| `tests/fixtures/playtest_report_v1.txt` | Raport wzorcowy w formacie 1, zbudowany według `PlaytestReportText.build`. |
| `docs/RELEASE.md` | Nowy podrozdział „Scalanie raportów testerów” (w „Testy zamknięte w Play Console”): jak uruchomić narzędzie. |

## Format raportu: co sprawdziłem w kodzie

- `crash/PlaytestReportText.kt`:
  - etykiety treści są **stałe, po angielsku**;
  - lokalizacji podlega tylko temat e-maila `pt182_subject` (10 języków, `L.kt`) i kod w `Language:`;
  - raport nie ma linii `Report format` (to format 1).
- Narzędzie rozpoznaje temat w 10 językach (także z przedrostkiem `Subject:`, `Temat:`, `Betreff:`…),
  gdy tester wkleił go razem z treścią albo raport przyszedł jako `.eml`.
- Wklejone „Zgłoś problem” (`Century War - raport techniczny` i `report_subject` w 10 językach)
  narzędzie liczy osobno jako kandydatów na P0. Nie traktuje ich jako raportu testera.
- **Obcięcie.** Gra tnie raport do `MAX_CHARS` = 6000 znaków, a ankieta jest na początku, więc przetrwa.
  Raport bez ostatniej linii `Battle speed x1/2:` dostaje stan **obcięty**. Jego ostatniej
  linii narzędzie nie liczy, bo liczba w niej mogła zostać ucięta.
- **Uszkodzenie.** Stan **uszkodzony** dostaje raport z nieznanymi liniami, bez nagłówka albo bez
  kluczowych pól. Jego pola, które da się odczytać, nadal wchodzą do median.
- **Inne odporności:**
  - końce linii CRLF, BOM, twarde spacje;
  - kilka raportów w jednej komórce;
  - tekst wklejony w pole jednowierszowe: podziały linii są odtwarzane po znanych etykietach;
  - p95 w zapisie Kotlina `1.0E8`.
- **Strażnik zgodności.** `test_constants_match_game_code` czyta pliki gry: `PlaytestReportText.kt`,
  `PlaytestLog.kt`, `L.kt` (`CODES`, `pt182_subject`, `report_subject`), `Modes.kt` i
  `CrashReportText.kt`. Test nie przejdzie, jeśli zmieni się etykieta, sekcja, tryb, ekran,
  odpowiedź ankiety albo temat.
  R13 proponował zamiast tego dopisać asercję do `PlaytestReportTextTest`. Tego nie zrobiłem,
  bo zlecenie zabrania zmian w kodzie gry.

## Bramka G2: co liczy, a czego nie

Progi są stałymi `G2` na górze pliku i odpowiadają dokładnie R13 §1. Zbiór n to raporty z 10. dnia,
a jeśli ich nie ma, wszystkie raporty (wtedy z dopiskiem „ocena wstępna”).

| # | Liczone z raportu | Uwagi |
|---|---|---|
| 2.1 | nie | Play Console: `--active-testers` |
| 2.2 | nie | raport v1 nie liczy awarii/ANR; `--crash-groups`; pola `Crashes:`/`ANRs:` (v2) przygotowane |
| 2.3 | nie | Play Console: `--prelaunch-crashes` |
| 2.4 | tak, z zastrzeżeniem | liczba raportów po odrzuceniu duplikatów, nie pewne osoby (brak kodu testera) |
| 2.5 | tak | mediany dni, czasu bitew, sesji; osoby z ≥ 4 dniami; ciągłość (mapa dni) dopiero w v2 |
| 2.6–2.9, 2.11–2.15 | tak | 2.7 pomija „before this log”; 2.14 liczy tylko sesje z bitwą (p95 > 0) |
| 2.10 | częściowo | odsetek z max serią ≥ 5 tak; „przy których etapach” nie (lejek, TOP 5 poz. 2) |

Wynik bramki:
- **PRZECHODZI**;
- **NIE PRZECHODZI**: przy pierwszym niespełnionym twardym kryterium;
- **NIEROZSTRZYGNIĘTE**: twarde kryteria spoza raportu nie zostały podane.

**Łączenie dnia 3 i 10 tej samej osoby.** Kod testera nie istnieje, więc narzędzie łączy raporty
po pseudonimie z formularza albo z nazwy pliku (`kasia-dzien3.txt`) i modelu `Device:`. Bez pseudonimu
raporty zostają osobno, a odrzucany jest tylko identyczny tekst wklejony drugi raz. Dwie osoby
z tym samym telefonem i dniem startu to częsty przypadek: próba demonstracyjna pokazała, że
łączenie po samym modelu i dacie błędnie scaliło połowę testerów. Dlatego ten sposób usunąłem.

**Miejsce na Raport v2.** `FUTURE_FIELDS` zawiera `Report format`, `Tester code`, `Crashes`, `ANRs`,
`Day map` i `Research levels`, a `STAGE_LINE` wiersze lejka `S19: 4 tries, 1 win, 0 surr, 52m, cmd 3`.
`Tester code` ma już pierwszeństwo przy łączeniu, a `Crashes` trafia do 2.2. Wzorce są
**propozycją z R13**. Po wdrożeniu poz. 1–2 trzeba je dopasować do prawdziwego formatu.

## Dzień 3 i 10

Kolejność źródeł:
1. pole formularza „Który raport” (`dzień 3`, `Dzień 10`; `problem`/`inny` → bez dnia);
2. nazwa pliku (`dzien3`, `day10`, `d3`; `android15` nie jest dniem);
3. rozpiętość `First day`–`last day`: < 7 → 3, inaczej 10 (`--day-split`). W tabeli z gwiazdką.

Duplikat tego samego testera i dnia: zostaje raport z większym czasem bitew.

## Wyjście

- Markdown (`-o`):
  - nagłówek z wynikiem bramki;
  - tabele G2 (twarde/miękkie);
  - lista „Czego nie da się policzyć”;
  - mediany dla dnia 3, dnia 10 i wszystkich raportów;
  - tabela per raport (21 kolumn);
  - rozkład ocen i „Biggest issue”;
  - wydajność według telefonu;
  - wolne teksty z formularza (do ręcznego pogrupowania w tematy);
  - uwagi parsera.
- Opcjonalnie CSV „tester × dzień” (`--csv-out`).

## Sprawdzenie

- `python3 -m unittest discover -s tests`: **66 testów OK**, w tym 13 nowych.
- Próba na 24 raportach demonstracyjnych (12 osób × dzień 3 i 10, jeden obcięty) z danymi PC:
  - bramka „PRZECHODZI”;
  - obcięty raport opisany w „Uwagach parsera”;
  - bez `--active-testers` i pozostałych danych z PC wynik to „NIEROZSTRZYGNIĘTE”.

## Do decyzji / dalej

- Po Raporcie v2 (TOP 5 poz. 1–2) dopasować `FUTURE_FIELDS` i `STAGE_LINE`, a 2.10 rozszerzyć
  o etapy i dowódcę na 19 (d37).
- Pseudonimy z formularza trafiają do Markdown. Zanim `test-faleN.md` trafi do repozytorium,
  koordynator sprawdza, czy nie ma w nim imion i nazwisk (zapisane w `RELEASE.md`).
- Grupowanie wolnych tekstów w tematy zostaje ręczne: narzędzie tylko je zbiera.
