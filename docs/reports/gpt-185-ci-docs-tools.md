GRADLE NIE URUCHAMIANO — workflow dotyczy wyłącznie Pythona i dokumentacji.

# 185 — ręczne CI dokumentów i narzędzi

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/185-ci-docs-tools`; końcowy hash można odczytać `git rev-parse HEAD` (raport nie może zawierać własnego hasha).

Na osobnej gałęzi z bazy scalono lokalnie 181 `4995b280`, 182 `80b1d428`, 183 `289041f2`, 184 `dfde00f8` w merge `4a9f3c8e`; bez konfliktów. Oprócz ich plików własny zakres 185 obejmuje `.github/workflows/docs-tools.yml`, `docs/CI.md`, `tests/test_ci_tools.py` i ten raport. Workflow ma wyłącznie ręczny wyzwalacz, uprawnienie odczytu, concurrency oraz limit 10 minut. Statystyki są w podsumowaniu i artefakcie, pozostałe kroki działają również po błędzie testów.

Lista wyjątków odnośników jest jawnie pusta: nie uznano automatycznie istniejących braków za dopuszczalne. Wynik kontroli pozostaje czerwony do osobnej analizy i decyzji o wyjątkach lub napraw. Nie uruchomiono workflow na GitHub Actions; lokalny wynik nie potwierdza wykonania na runnerze.

## Wyniki lokalne

`python -m pytest tests/ -v`:
```text
FAILED tests/test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions
ERROR tests/test_playtest_merge.py::tester
3 failed, 103 passed, 1 error, 24 subtests passed in 4.84s
```
Problemy odtworzone wcześniej również na bazie: niezgodność ORDERS, nieaktualne wartości w testach dokumentów i zbieranie pomocniczego `tester(i, day)` przez pytest 9. Naprawa wykracza poza zakres.

`python -m pytest tests/test_ci_tools.py tests/test_ci_release_gates.py -q`: `13 passed, 7 subtests passed in 0.04s`.

`python tools/check_store_listings.py` — kod 0, końcówka:
```text
tr Title: 11/30
tr Short description: 55/80
tr Full description: 998/4000
```

`python tools/check_doc_links.py --allow-list <pusty plik>` — kod 1: `Plików: 352; błędów: 159; wyjątków: 0; zewnętrznych: 295.` Liczba zależy od stanu plików; zewnętrzne URL nie są sprawdzane sieciowo. `python tools/l10n_stats.py --markdown` — kod 0, wygenerowano Markdown.

Odpowiednik `grep -n '^on:' -A3 .github/workflows/docs-tools.yml` w PowerShell:
```text
3:on: workflow_dispatch
4:
5:permissions:
6:  contents: read
```

## Dalsze prace

Warunek zielonego pełnego pytest i wszystkich komend pozostaje niespełniony. Proponowane osobne zadania: „Napraw aktualność testów raportów i prywatności” (`tests/test_playtest_merge.py`, `tools/playtest_merge.py`, `tests/test_privacy_docs.py`); „Przejrzyj brakujące cele i zatwierdź wyjątki” (`docs/`, `.github/workflows/docs-tools.yml`).
