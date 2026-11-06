# Zlecenie 196 — karta sklepu po ukraińsku

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/196-listing-uk`.

Zmodyfikowano `docs/store/listing_uk.md`, dodano niniejszy raport. Przegląd językowy i zgodność nazw z ukraińskimi wartościami w `L.kt`; brak nowych obietnic dla niewidomych, brak porównań z innymi grami.

| Przed | Po | Dlaczego |
|---|---|---|
| Наймай бійців | Навчай бійців | W grze jednostki są szkolone. |
| Приклич … підсилює | Поклич … посилює | Bardziej naturalna forma i zgodność z opisem w grze. |
| нескінченної облоги … власний бій | Нескінченної облоги … власну Сутичку | Zgodność z nazwami `endless` i `skirmish`. |
| Brak nazw trzech trybów | Виклик дня, Тижневу облогу та Накази штабу | Aktualne tryby; klucze `daily`, `weekly`, `o209_title`. |
| зберігай профіль у файл | експортуй та імпортуй профіль | Wyraźne opisanie obu dostępnych operacji. |
| 83 досягнення … постійно дають нові цілі | На тебе чекають 83 досягнення … | Zwięzłe, naturalniejsze sformułowanie. |

Niespójności z `L.kt`: nie wykryto w sprawdzonych nazwach trybów, komendzie przywołania, ustawieniach i ścieżce zgłaszania błędów. To nie jest pełny audyt wszystkich kluczy językowych.

## Testy

`python tools/check_store_listings.py` — kod 0, końcowy wynik dla UK:

```text
uk Title: 11/30
uk Short description: 50/80
uk Full description: 1004/4000
```

`git diff --check` — bez błędów. Gradle (`--no-daemon`, `--no-configuration-cache`, JDK 17, SDK 36) po pierwszym wyniku z cache uruchomiono ponownie z `--rerun-tasks`. Na czas testu lokalne karty miały końce linii LF, po teście przywrócono ich stan z indeksu bez zmian treści w innych kartach:

```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 1m 18s
37 actionable tasks: 37 executed
```

## Ograniczenia

Nie przeprowadzono niezależnej korekty przez rodzimego użytkownika języka ukraińskiego. Kryterium rodzimego przeglądu pozostaje niepotwierdzone; zalecane dalsze zlecenie: korekta ukraińskiej karty przez native speakera (`docs/store/listing_uk.md`). Hash ostatniego commita raportu można odczytać przez `git rev-parse HEAD`, ponieważ wpisanie go do jego własnej treści zmieniłoby hash.
