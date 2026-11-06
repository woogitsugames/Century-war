# Raport 182 — testy kart sklepu

Data: 2026-11-06. Gałąź: `gpt/182-tools-store-listings-tests`. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`.

**GRADLE NIE URUCHOMIONO — ZMIANY DOTYCZĄ NARZĘDZIA PYTHON I JEGO TESTÓW.**

## Wynik i pliki

- `tools/check_store_listings.py` przyjmuje opcjonalny katalog jako argument pozycyjny; bez argumentu używa `docs/store/`. Walidację przeniesiono do funkcji, zachowując dotychczasowe pomiary i wynik na istniejących kartach. Błędne sekcje mają dodatkową diagnozę na stderr; puste pola nie mogą pochłonąć następnego nagłówka.
- `tests/test_check_store_listings.py` testuje uruchomienie procesu na tymczasowej kopii, komplet kart, brak pliku lub sekcji, pustą sekcję, limity 30/31, 80/81 i 4000/4001 oraz UTF-16 dla znaków wielobajtowych i par zastępczych. Nazwy katalogów mogą zawierać spacje.
- `tests/fixtures/store_listings/listing_{pl,en,de,es,fr,it,pt,uk,nl,tr}.md` to kopie 10 kart z bazowego commita, niezależne od późniejszych zmian w `docs/store/`.
- `docs/reports/gpt-182-tools-store-listings-tests.md`: raport.

Końcowy hash commita należy odczytać przez `git rev-parse HEAD`; commit nie może zawierać swojego własnego hasha. Rzeczywisty hash i wynik push są w wiadomości końcowej.

## Testy

`python -m pytest tests/test_check_store_listings.py -v`:

```text
test_exact_limit_and_one_over[Full description-4000] PASSED
test_multibyte_unicode_uses_utf16_units_not_utf8_bytes PASSED
test_polish_headings_are_checked PASSED
10 passed in 1.83s
```

`python tools/check_store_listings.py`: kod 0; ostatnie wiersze:

```text
nl Title: 11/30
nl Short description: 60/80
nl Full description: 1074/4000
tr Title: 11/30
tr Short description: 55/80
tr Full description: 998/4000
```

Dodatkowe porównanie oryginalnego skryptu z `HEAD` na kopii tych samych kart oraz nowego bez argumentu: kod, stdout i stderr identyczne bajtowo, 30 wierszy, kod 0.

`python -m pytest tests/` (Windows, Python 3.14.7, pytest 9.2.0):

```text
FAILED tests/test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions
ERROR tests/test_playtest_merge.py::tester
3 failed, 78 passed, 1 error in 4.43s
```

Sprawdzono również niezmieniony bazowy commit w osobnym worktree poleceniem `python -m pytest tests/ --tb=no -q`: **te same trzy błędy i ten sam błąd setup**, 68 passed; bieżąca gałąź dodaje 10 przechodzących testów. Test stałych oczekuje czterech trybów, kod ma też `ORDERS`; testy prywatności oczekują starych 79 osiągnięć i 84 ID zamiast 83/89; pytest 9 zbiera pomocniczą funkcję `tester(i, day)` jako test bez fixture `i`. Są poza zakresem zlecenia 182; nie zmieniono ich ani obszarów Play Games/Rozkazów.

## Ograniczenia i dalsze zlecenia

Pełny zestaw Python pozostaje czerwony z przyczyn odtworzonych na bazie. Osobno naprawić test i narzędzie raportów dla nowych trybów (`tests/test_playtest_merge.py`, `tools/playtest_merge.py`) oraz zaktualizować test dokumentacji do aktualnego katalogu (`tests/test_privacy_docs.py`) po uzgodnieniu z koordynatorem obszarów współdzielonych. Nie zmieniano kart sklepu, kodu Androida, L.kt ani workflowów.
