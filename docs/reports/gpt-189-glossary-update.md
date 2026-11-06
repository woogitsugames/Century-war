# Zlecenie 189 — słownik pojęć

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź: `gpt/189-glossary-update`.

**GRADLE NIE URUCHAMIANO** — zmiana wyłącznie dokumentacji; zlecenie nie wymaga budowania.

Uzupełniono słownik o 88 wierszy: doktryny, karty Endless, wydarzenia dnia i zestawy tygodnia oraz wybrane nazwy Rozkazów sztabu i interfejsu. Każdy wiersz zachowuje klucz i dziesięć wartości z `L.kt`; kontekst wyjaśniają nagłówki grup. Zmodyfikowano `docs/GLOSSARY.md`, dodano ten raport. `L.kt` pozostaje bez zmian.

W uzupełnieniu dodano 31 dalszych etykiet dotyczących m.in. dowódcy, wież, parametrów walki, jednostek, pogody, wyniku i powtórek. Kandydatów wybrano z krótkich nazw, których angielska fraza występuje co najmniej osiem razy w wartościach kluczy; następnie ręcznie odrzucono część ogólnych przycisków i powtórzeń. To wyszukiwanie pomocnicze nie rozstrzyga znaczenia w każdym kontekście ani nie dowodzi kryterium trzech kluczy. Tabela zawiera dokładne wartości dziesięciu języków z podanych kluczy, wraz z ogólnym znaczeniem grupy.

## Kontrola

Kontrola skryptem porównała wszystkie nowe komórki z wartościami `t(...)` w `L.kt`:

```text
88 nowych wierszy: 10 języków zgodnych z L.kt
```

`git diff --check` nie zgłosił błędów. Testy Gradle i aplikacji nie były uruchamiane; nie zmieniono kodu.

Kontrola dodatkowego uzupełnienia: 31 wybranych kluczy po 10 wartości, generowanych bezpośrednio z `L.kt`; `git diff --check` bez błędów.

## Niespójności do przeglądu

- Zryw: `rally`, `o209_k_rally`, `a178_rally_ready` i `battle84_rally_short` opisują tę samą umiejętność, ale ostatni stosuje DE „Sturm” zamiast „Ansturm”, ES „Impulso” zamiast „Ímpetu”, NL „Aanval” zamiast „Stormloop” i TR „Hücum” zamiast „Atılım”. Skrót na przycisku może uzasadniać różnicę; wymaga oceny tłumacza.
- Osobowości: `sk_person_2` / `cx127_economist` to PL „Ekonomista” / „Gospodarz”, a `sk_person_4` / `cx127_balanced` to „Zrównoważony” / „Taktyk”; nazwy w kodeksie i konfiguracji różnią się również w innych językach. `sk_person_3` / `cx127_sieger` różni się po ukraińsku („Облоговець” / „Майстер облоги”). Należy potwierdzić, czy odmienne nazwy są zamierzone.
- `weekly_week_tab`, `global_period_week` i `board_weekly`: po polsku „Tydzień”, po angielsku odpowiednio „Week”, „Week”, „Weekly”; po turecku „Hafta”, „Hafta”, „Haftalık”. Kontekst zakładki i nazwy rankingu może uzasadniać różną formę.

## Ograniczenia i dalsza praca

Nie potwierdzono kryterium kompletności wszystkich pojęć występujących w co najmniej trzech kluczach: wymaga ono semantycznego przyporządkowania całego katalogu tekstów, również fragmentów zdań, a sama zgodność tabel nie dowodzi takiego pokrycia. Zlecenie pozostaje **NIEUKOŃCZONE** w tym zakresie. Wskazane są dalsze zadania: audyt grup semantycznych w `L.kt` i uzupełnienie `docs/GLOSSARY.md`; redakcyjny przegląd zgłoszonych nazw z kontekstem ekranów. Ryzyko: różnice mogą wynikać z gramatyki lub ograniczonej szerokości etykiet; nie należy automatycznie ujednolicać tłumaczeń.

Hash ostatniego commita jest identyfikatorem commita zawierającego ten raport (`git rev-parse HEAD`); wpisanie go dosłownie do jego własnej treści zmieniłoby ten hash.
