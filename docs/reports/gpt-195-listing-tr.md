# 195 — turecka karta sklepu

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź: `gpt/195-listing-tr`. Hash końcowego commita: `git rev-parse HEAD` na tej gałęzi (hash pliku raportu nie może zawierać własnego hasha).

Przejrzano kartę, glosariusz i wybrane teksty gry. Zmieniono `docs/store/listing_tr.md` oraz dodano niniejszy raport. Sprawdzono brak porównań z innymi grami i obietnicy, że gra jest przeznaczona dla niewidomych.

| Przed | Po | Dlaczego |
|---|---|---|
| kule seviyeleri arasında yükseltmeler yaparak | kule seviyelerini yükselterek | Naturalniejszy opis podnoszenia poziomów wież, zachowujący frazę wymaganą testem. |
| Zorluk seçeneğiyle 36 sefer aşamasını oyna | Zorluk seviyeni seçerek seferin 36 aşamasını oyna | Płynniejsze powiązanie wyboru poziomu trudności z kampanią. |
| sonsuz kuşatmada | Sonsuz Kuşatma'da | Nazwa trybu jak w `L.kt`, także w informacji o nowościach. |
| Her çağın kendi kayıtlı müziği var: savaş sırasında birbirine akıcı biçimde geçen sakin ve savaş temaları. | Her çağın kendine özgü kayıtlı müziği var: sakin ve savaş temaları oyun sırasında akıcı biçimde birbirine geçer. | Pełne zdanie i czytelniejszy opis przejść muzycznych. |
| daha az parlama ve ekran sarsıntısı | azaltılmış parlama ve ekran sarsıntısı | Jednoznaczniejszy opis ograniczenia obu efektów. |

## Testy

`python tools/check_store_listings.py` — zakończono pomyślnie, końcówka:
```text
tr Title: 11/30
tr Short description: 55/80
tr Full description: 999/4000
```
Informacja o nowościach: 460/500 znaków. `python -m pytest tests/ -q` — `3 failed, 68 passed, 1 error, 24 subtests passed`: istniejące błędy testów `test_playtest_merge.py::PlaytestMergeTest::test_constants_match_game_code`, `test_privacy_docs.py::PrivacyDocumentationTest::test_owner_fields_are_still_renderable_and_require_real_input`, `test_privacy_docs.py::PrivacyDocumentationTest::test_pgs_catalog_count_matches_owner_instructions` oraz brak fixture `i` dla `test_playtest_merge.py::tester`. Nie dotyczą tureckiej karty.

`gradlew.bat :app:testDebugUnitTest --tests '*StoreGraphicsTest.listingLimits*' --console=plain --no-configuration-cache --no-daemon` — test nie przeszedł. Pierwsza próba bez `ANDROID_HOME` zakończyła się błędem braku SDK; ponowienie ze wskazanym SDK:
```text
StoreGraphicsTest > listingLimits FAILED
    java.lang.IllegalStateException at StoreGraphicsTest.kt:170
1 test completed, 1 failed
BUILD FAILED in 23s
37 actionable tasks: 15 executed, 19 from cache, 3 up-to-date
```
XML podaje `Brak sekcji: Tytuł`: test szuka nagłówka zakończonego LF, natomiast plik polskiej karty w tym checkout miał CRLF. Po lokalnej normalizacji samych końców linii dziesięciu kart do LF (bez zmian treści w git diff) ponowienie zakończyło się:
```text
StoreGraphicsTest > listingLimits FAILED
    java.lang.AssertionError at StoreGraphicsTest.kt:201
1 test completed, 1 failed
BUILD FAILED in 17s
```
XML wskazał `Pełny opis bez dowódcy/stopni wież: tr`: pierwsza redakcja usunęła wymagany fragment `kule seviyeleri`. W poprawionym opisie występuje `kule seviyelerini`. Ponowienie z lokalnymi końcami LF przeszło:
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 15s
37 actionable tasks: 1 executed, 36 up-to-date
```

## Ograniczenia i dalsze kroki

Nie wykonano niezależnej weryfikacji przez rodzimego użytkownika języka tureckiego; wymagany przegląd natywny pozostaje otwarty. Fragment `comm125` w `L.kt` używa angielskiego „Endless”, chociaż nazwa trybu to „Sonsuz Kuşatma”; plik poza zakresem. Przed publikacją warto potwierdzić liczbę osiągnięć i informacje o wersji na podstawie finalnego AAB.

Proponowane zadania: „Natywny przegląd tureckiej karty” (`docs/store/listing_tr.md`); „Spójność nazwy trybu w tureckim opisie dowódcy” (`app/src/main/java/com/centurywar/engine/L.kt`).
