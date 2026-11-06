# Raport 183 — statystyki lokalizacji

Data: 2026-11-06. Baza: `origin/main` `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/183-tools-l10n-stats`.

**GRADLE NIE URUCHOMIONO — ZMIENIONO WYŁĄCZNIE NARZĘDZIE PYTHON, JEGO TESTY I RAPORTY.**

## Wynik

`tools/l10n_stats.py` czyta `L.kt`, liczy klucze według części, wykrywa podejrzaną identyczność z angielskim, stosunek długości, placeholdery i odstępy. Obsługuje `--markdown`, `--lang`, `--threshold`, `--top` i opcjonalną ścieżkę wejściową. Domyślny próg długości to >1,5 długości polskiej lub angielskiej, z pominięciem wzorca krótszego niż 8 znaków. Odstęp przed dwukropkiem, średnikiem, pytajnikiem i wykrzyknikiem w języku francuskim nie jest uznawany za błąd.

Wynik z bazowego `L.kt`: `docs/reports/gpt-183-l10n-stats.md` zawiera wszystkie części oraz do 30 podejrzeń dla każdego z 10 języków (angielski ma ich 13). Podejrzenia są uporządkowane według rodzaju, potem klucza, nie według faktycznej wagi błędu. Długie teksty są skracane w raporcie; pełne znajdują się w źródle. Tłumaczenie może być prawidłowo identyczne z angielskim, a różnica długości nie dowodzi ucięcia; wyniki wymagają oceny człowieka.

Lista dokładnych wyjątków identyczności: `AI`, `API`, `Android`, `CENTURY WAR`, `Century War`, `DPS`, `FPS`, `Galaxy Fold`, `Google Play`, `Google Play Games`, `HP`, `HTTPS`, `HUD`, `ID`, `JSON`, `OK`, `PNG`, `Play Games`, `TalkBack`, `URL`, `UTC`, `XP`. Wyjątki nie maskują innych rodzajów ostrzeżeń; identyczność dłuższych fraz nie jest automatycznie ignorowana.

Pliki: `tools/l10n_stats.py`, `tests/test_l10n_stats.py`, `tests/fixtures/l10n/L.kt`, `docs/reports/gpt-183-l10n-stats.md`, `docs/reports/gpt-183-tools-l10n-stats.md`. `L.kt` gry pozostaje bez zmian. Hash ostatniego commita jest w wiadomości końcowej i dostępny przez `git rev-parse HEAD`; treść commita nie może zawierać własnego hasha.

## Testy

`python tools/l10n_stats.py --markdown | Select-Object -First 80` (odpowiednik `head -80` w PowerShell): kod 0. Początek:

```text
# Statystyki lokalizacji
Klucze: 1643; części: 84.
Próg długości: > 1.5× PL lub EN, odniesienie min. 8 znaków.
## Liczba kluczy na część
- part0: 191
- part1: 198
- part2: 180
- part3: 211
```

Pełny przebieg i zapis pomiaru: 0,171 s na tym komputerze, poniżej 5 s.

`python -m pytest tests/test_l10n_stats.py -v`:

```text
test_language_sensitive_typography_and_length PASSED
test_incomplete_or_duplicate_entries_are_errors PASSED
test_cli_markdown_lang_and_limit PASSED
test_real_table_is_parsed_completely PASSED
8 passed in 0.21s
```

`python -m pytest tests/`:

```text
FAILED tests/test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input
FAILED tests/test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions
ERROR tests/test_playtest_merge.py::tester
3 failed, 76 passed, 1 error in 0.60s
```

Pełny pytest nie jest zielony. Te same cztery problemy odtworzono na czystym bazowym `7d847576` w osobnym worktree przy weryfikacji 182: helper `tester` zbierany przez pytest 9, brak `ORDERS` w oczekiwanych trybach i dawne liczby 79/84 zamiast 83/89. Pliki należą do innych zadań i nie zostały tu zmienione. Dalsze zlecenia: uaktualnić testy raportów i dokumentów (`tests/test_playtest_merge.py`, `tools/playtest_merge.py`, `tests/test_privacy_docs.py`) po uzgodnieniu z koordynatorem.

## Ograniczenia parsera

Analiza dotyczy literałów w `partNNN()` zapisanych jako `"klucz" to t("...", ...)`; obsługuje zwykłe escape Kotlin, w tym `\$` i `${'$'}`, wielowierszowe wywołania i komentarze. Nie wykonuje kodu Kotlin. Nietypowy nieliteralny argument, inna liczba kolumn, duplikat klucza lub nierozpoznane wywołanie kończy się jawnym błędem, zamiast cichego pominięcia. Wyrażenia i niestandardowe formaty `String.format` poza `%s`, `%d`, `%f`, ich pozycyjnymi i precyzją oraz `%%` wymagają rozszerzenia parsera przed wiarygodnym pomiarem. To nie zastępuje `LTest` ani oceny rodzimego użytkownika języka.
