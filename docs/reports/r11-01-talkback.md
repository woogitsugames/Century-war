# R11-01: TalkBack poza menu — krok 1 (droga do bitwy i listy)

Wariant zatwierdzony przez właściciela: najpierw droga do bitwy i listy. Kodeks i Statystyki
(krok 2) zostają na później.

## Mechanizm

- Nowy rodzaj drzewa `SCREEN` w `GameAccessibility`: ekran, który nadpisuje
  `Screen.accessibilityTitle()`, dostaje węzły przycisków (id = indeks + 1, jak dotąd),
  węzły informacyjne (`INFO_SCREEN_BASE + i`, klasa `TextView`) i przewijany korzeń
  (`ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD`, wykonanie na wątku gry; `false` na końcu listy).
- API w `Screen`: `accessibilityEligible`, `accessibilityKey`, `accessibilityOrder`,
  `accessibilityInfoCount/Info/InfoBounds/InfoOrder/InfoDismiss`, `accessibilityCanScroll`,
  `accessibilityScroll`.
- Porównanie drzewa (`screenMatches`) bez kolekcji i bez budowania etykiet: stan etykiety
  przekazuje `accessibilityKey`, teksty informacji są buforowane przez ekrany. Bez zmian stanu
  nie ma przebudów (test `idleFramesDoNotRebuildAndScrollDoes`). Bez TalkBack nic się nie liczy.
- Okna nad ekranem: przy otwartym dymku podpowiedzi w drzewie są tylko jego tekst i OK;
  zapowiedź epoki to jeden węzeł, którego kliknięcie zamyka planszę (ta sama droga co dotyk).
- Wygląd, `Button.label` i rysowanie bez zmian.

## Co czyta czytnik

| Ekran | Węzły | Co można zrobić |
|---|---|---|
| Kampania — mapa | nagłówki widocznych epok („Epoka kamienia, 3 z 12 gwiazdek, korony Trudnego: 1 z 4”), etapy „Etap 2, Epoka kamienia, 0 z 3 gwiazdek, następny do zdobycia” (+ korona R10-02), zablokowane „…, zablokowany” jako niedostępne, Wstecz | otworzyć etap, przewijać mapę (oś mapy, także pion na Foldzie) |
| Kampania — panel etapu | jedno zdanie: etap, gwiazdki, korona, epoki, siła AI + „wróg słabszy/równy/silniejszy”, pogoda, modyfikatory, poziom trudności; trzy poziomy jako zaznaczane; Start; Zamknij | zmienić poziom, Start bitwy, zamknąć |
| Bez końca / Wyzwanie dnia / Oblężenie tygodnia / Potyczka (tryby) | opis zasad, rekordy, wydarzenie dnia, modyfikatory, progi tygodnia z „zaliczone”; zakładki Dzień/Tydzień jako wybrane; poziomy potyczki jako zaznaczane | Graj, przełączać zakładki i poziom |
| Potyczka własna | podsumowanie (epoki, siła, ranking, osobowość); wszystkie przyciski, strzałki epok i siły z opisem | ustawić i zagrać, przewijać |
| Badania | „Masz N medali”; karta: nazwa, poziom X z Y, koszt albo poziom maksymalny, stać cię / za mało medali, opis | kupić, przewijać |
| Misje | seria logowań; każda widoczna misja (treść, postęp, nagroda, stan); „Odbierz: <misja>, <N medali>” | odebrać nagrodę, przewijać |
| Osiągnięcia | widoczne wiersze (nazwa, opis albo „ukryte”, zdobyte/postęp, nagroda), licznik; zakładki rekordów | przewijać, przełączać zakładki |
| Co nowego | jedno zdanie na widoczne wydanie (wersja i punkty) | przewijać, wrócić |
| Sztandary | podgląd (nazwa, stan, warunek); kafelki jako zaznaczane z nazwą, stanem i warunkiem | wybrać, założyć, przewijać |

## Pominięte (na później)

- Kodeks i Statystyki (krok 2 zlecenia, decyzja właściciela) oraz Rankingi, Powtórki,
  Rankingi globalne, Play Games, Podsumowanie prywatności — nadal jeden węzeł `a164_other`.
- Baner `showNotice` nie wyłącza węzłów pod spodem (jest krótki i niemodalny).
- Odliczanie „Koniec za…” tygodnia i resety misji nie są w opisie (zmieniają się co sekundę/30 s,
  przebudowywałyby drzewo).
