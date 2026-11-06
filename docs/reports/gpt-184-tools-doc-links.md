# Raport 184 — sprawdzanie odnośników

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/184-tools-doc-links`.

**GRADLE NIE URUCHOMIONO — ZMIENIONO TYLKO NARZĘDZIE PYTHON, FIXTURES, TESTY I RAPORTY.**

## Wynik

`tools/check_doc_links.py` sprawdza pliki Markdown z pominięciem `.git`, `build` i katalogów cache. Obsługuje linki inline i obrazki, odwołania `[tekst][etykieta]`, względne ścieżki i kotwice nagłówków, w tym powtórzone nagłówki. Sprawdza również dosłowne ścieżki w backtickach zaczynające się od `docs/`, `tools/`, `app/src/`; końcowe numery linii są usuwane przed sprawdzeniem istnienia pliku. Adresy HTTP(S) wypisuje bez połączeń sieciowych. `--markdown` formatuje raport, a opcjonalny argument wybiera plik lub katalog (np. `docs/testers`). `--root` pozwala wskazać repozytorium; `--allow-list` przyjmuje plik UTF-8 ze wzorcami źródła lub `źródło:cel`, po jednym wierszu (glob, `#` rozpoczyna komentarz całego wiersza). Wyjątki nie są błędami; liczba wyjątków jest widoczna w podsumowaniu. Kod 0 oznacza brak niepominiętych błędów, 1 oznacza błędy linków, a 2 problem z wejściem.

`docs/reports/gpt-184-doc-links.md` zawiera listę rozpoznanych braków zgrupowaną po plikach. Pomiar wykonano przed dodaniem dwóch raportów zadania; z 336 sprawdzonych plików 333 to pliki bazowego `main`, a 3 to poprawne testowe Markdown w fixture. Wystąpiło 178 podejrzeń i 295 odwołań HTTP(S), nie sprawdzanych w sieci. Część braków w `docs/tasks/` to przyszłe produkty zleceń, a nie awarie już istniejącej dokumentacji; część referencji jest historyczna. Nie wprowadzono zmian do sprawdzanych dokumentów.

Pliki: `tools/check_doc_links.py`, `tests/test_check_doc_links.py`, `tests/fixtures/doc_links/README.md`, `tests/fixtures/doc_links/docs/guide.md`, `tests/fixtures/doc_links/docs/GDD.md`, `tests/fixtures/doc_links/docs/photo.svg`, `tests/fixtures/doc_links/tools/check_doc_links.py`, `docs/reports/gpt-184-doc-links.md` i ten raport. Ostatni hash commita przekazano w wiadomości końcowej; sam commit nie może zawierać własnego hasha.

## Testy

`python tools/check_doc_links.py --markdown | Select-Object -First 60` (PowerShell, odpowiednik `head -60`) po wygenerowaniu raportu:

```text
# Sprawdzenie odnośników
Plików: 338; błędów: 174; wyjątków: 0; zewnętrznych: 295.
## Brakujące cele i kotwice
### docs/Century-war.md
- Linia 103: app/src/main/res/values/games_ids.xml — brak ścieżki
```

Wcześniejszy skan 178 → 174 po zapisaniu obu raportów, ponieważ ich cele pojawiły się w drzewie. Kod samego skanera przy brakujących celach: **1**, czego potok ograniczający linie nie przekazuje niezawodnie.

`python -m pytest tests/test_check_doc_links.py -v`:

```text
test_fragment_only_encoded_space_and_reference_style PASSED
test_filter_to_subdirectory PASSED
test_missing_input_and_allow_list_report_errors PASSED
test_backtick_line_references_and_ellipses PASSED
8 passed in 1.36s
```

`python -m pytest tests/`:

```text
FAILED tests/test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions
ERROR tests/test_playtest_merge.py::tester
3 failed, 76 passed, 1 error in 1.78s
```

Wymóg zielonego pełnego pytest pozostaje niespełniony. Te same problemy odtworzono na czystym bazowym `7d847576` w ramach 182: niezgodność trybu ORDERS, stare wartości 79/84 w dwóch testach oraz zbieranie pomocniczego `tester(i, day)` przez pytest 9. Nie zmieniano obszarów poza zakresem; proponowane osobne zlecenie: uaktualnić `tests/test_playtest_merge.py`, `tools/playtest_merge.py` oraz `tests/test_privacy_docs.py` po uzgodnieniu z koordynatorem.

## Ograniczenia

Parser nie jest pełnym silnikiem Markdown: pomija bloki kodu i komentarze, wzorce z wielokropkiem, gwiazdkami i szablonami, skrócone odwołania `[etykieta]`, zaawansowane wieloliniowe linki i kotwice tworzone ręcznym HTML. Sprawdza kotwice wyłącznie w plikach Markdown. `--allow-list` nie służy do automatycznego ukrywania braków; użytkownik wybiera wyjątki świadomie. Nie weryfikowano dostępności żadnego zewnętrznego adresu. Wynik listy wymaga ręcznej oceny, w tym rozróżnienia planowanych plików od uszkodzonych odnośników.
