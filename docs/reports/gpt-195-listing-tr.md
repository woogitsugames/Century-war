GRADLE NIE URUCHOMIONO — oczekiwanie na dostęp do kolejki Gradle.

# 195 — turecka karta sklepu

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź: `gpt/195-listing-tr`. Hash końcowego commita: `git rev-parse HEAD` na tej gałęzi (hash pliku raportu nie może zawierać własnego hasha).

Przejrzano kartę, glosariusz i wybrane teksty gry. Zmieniono `docs/store/listing_tr.md` oraz dodano niniejszy raport. Sprawdzono brak porównań z innymi grami i obietnicy, że gra jest przeznaczona dla niewidomych.

| Przed | Po | Dlaczego |
|---|---|---|
| kule seviyeleri arasında yükseltmeler yaparak | kulelerini daha yüksek seviyelere yükselterek | Naturalniejszy opis podnoszenia poziomów wież. |
| Zorluk seçeneğiyle 36 sefer aşamasını oyna | Zorluk seviyeni seçerek seferin 36 aşamasını oyna | Płynniejsze powiązanie wyboru poziomu trudności z kampanią. |
| sonsuz kuşatmada | Sonsuz Kuşatma'da | Nazwa trybu jak w `L.kt`, także w informacji o nowościach. |
| Her çağın kendi kayıtlı müziği var: savaş sırasında birbirine akıcı biçimde geçen sakin ve savaş temaları. | Her çağın kendine özgü kayıtlı müziği var: sakin ve savaş temaları oyun sırasında akıcı biçimde birbirine geçer. | Pełne zdanie i czytelniejszy opis przejść muzycznych. |
| daha az parlama ve ekran sarsıntısı | azaltılmış parlama ve ekran sarsıntısı | Jednoznaczniejszy opis ograniczenia obu efektów. |

## Testy

`python tools/check_store_listings.py` — zakończono pomyślnie, końcówka:
```text
tr Title: 11/30
tr Short description: 55/80
tr Full description: 1015/4000
```
Informacja o nowościach: 460/500 znaków. `python -m pytest tests/ -q` — `3 failed, 68 passed, 1 error, 24 subtests passed`: istniejące błędy testów `test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code`, `test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input`, `test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions` oraz brak fixture `i` dla `test_playtest_merge.py::tester`. Nie dotyczą tureckiej karty.

`StoreGraphicsTest.listingLimits` — nieuruchomiony; oczekiwanie na kolejkę Gradle. Warunek zielonego testu pozostaje niespełniony.

## Ograniczenia i dalsze kroki

Nie wykonano niezależnej weryfikacji przez rodzimego użytkownika języka tureckiego; wymagany przegląd natywny pozostaje otwarty. Fragment `comm125` w `L.kt` używa angielskiego „Endless”, chociaż nazwa trybu to „Sonsuz Kuşatma”; plik poza zakresem. Przed publikacją warto potwierdzić liczbę osiągnięć i informacje o wersji na podstawie finalnego AAB.

Proponowane zadania: „Natywny przegląd tureckiej karty” (`docs/store/listing_tr.md`); „Spójność nazwy trybu w tureckim opisie dowódcy” (`app/src/main/java/com/centurywar/engine/L.kt`).
