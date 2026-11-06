# Zlecenie 181 — testy raportu balansu kampanii

Data: 2026-11-06. Baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`.

**NIE URUCHAMIANO GRADLE** — zlecenie dodaje wyłącznie testy Pythona i dane testowe, bez zmiany narzędzia ani kodu gry. Gradle był w tym czasie używany przez koordynatora.

Gałąź: `gpt/181-tools-campaign-balance-tests`. Commit testów: `12169c59` (pełny hash: `git rev-parse 12169c59`). Hash ostatniego commita gałęzi z tym raportem można sprawdzić `git rev-parse HEAD`; wpisanie go we własnej zawartości zmieniłoby ten hash.

## Zmiany

Dodano test uruchamiający skrypt jako proces i sześć małych syntetycznych CSV: 36 etapów po 20 uruchomień. Sprawdzane są treść Markdown i kod wyjścia, brak etapu, brak wymaganego uruchomienia, pusty katalog, błędne liczby, zerowa liczba uruchomień i powtórzenie etapu. Dane są fiksturami, nie pomiarem balansu gry. Nie zmieniono skryptu ani innych plików poza zakresem zadania.

Pliki: `tests/test_check_campaign_balance.py`, sześć plików `tests/fixtures/campaign_balance/report-1.csv` … `report-6.csv`, ten raport.

## Wyniki

`python -m pytest tests/test_check_campaign_balance.py -v` (Windows, Python 3.14.7, pytest 9.2.0):

```text
tests/test_check_campaign_balance.py::test_complete_report_produces_markdown PASSED
tests/test_check_campaign_balance.py::test_missing_stage_reports_number PASSED
tests/test_check_campaign_balance.py::test_missing_seed_reports_stage_and_count PASSED
tests/test_check_campaign_balance.py::test_empty_directory_is_rejected PASSED
tests/test_check_campaign_balance.py::test_corrupt_numeric_data_is_rejected[wins-nie-liczba] PASSED
tests/test_check_campaign_balance.py::test_corrupt_numeric_data_is_rejected[n-0] PASSED
tests/test_check_campaign_balance.py::test_corrupt_numeric_data_is_rejected[median_minutes-?] PASSED
tests/test_check_campaign_balance.py::test_duplicate_stage_is_rejected PASSED
8 passed in 1.65s
```

`python -m pytest tests/`:

```text
FAILED tests/test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions
ERROR tests/test_playtest_merge.py::tester
3 failed, 76 passed, 1 error in 12.84s
```

## Niespełnione kryterium i dalsze prace

**Cały zestaw pytest nie jest zielony**, więc zlecenie nie spełnia wszystkich kryteriów ukończenia. Błędy dotyczą istniejących plików spoza zakresu: pomocnicza funkcja `tester(i, day)` jest zbierana przez pytest jako test z nieistniejącymi fiksturami; `tools/playtest_merge.py` i test oczekują czterech trybów, a gra ma też `ORDERS`; dwa testy dokumentów prywatności oczekują historycznych 79 osiągnięć i 84 ID, podczas gdy aktualne dane zawierają 83 osiągnięcia i 89 ID. Nie zmieniono tych plików, ponieważ zlecenie tego zabrania.

Osobne prace: **Aktualizacja testów i parsera raportu testera** (`tools/playtest_merge.py`, `tests/test_playtest_merge.py`); **Aktualizacja kontroli liczby identyfikatorów PGS** (`tests/test_privacy_docs.py` wraz z właściwą specyfikacją dokumentów). Ponadto obecny skrypt odczytuje pole `stage` przed blokiem walidacji: brak nagłówka albo nieliczbowy etap kończy się tracebackiem, a nie raportem Markdown. Osobne zlecenie może dodać czytelną obsługę błędnej struktury (`tools/check_campaign_balance.py`, `tests/test_check_campaign_balance.py`) bez zmiany wyników prawidłowych danych. Nie sprawdzano zachowania pełnego zestawu pytest na Linuksie ani pod inną wersją pytest.
