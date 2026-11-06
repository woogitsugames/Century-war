# Zlecenie 177 — testy panelu ulepszeń

GRADLE NIE URUCHOMIONO — TESTY OCZEKUJĄ NA KOLEJKĘ, ZLECENIE NIE JEST JESZCZE GOTOWE.

Data: 2026-11-06. Baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`. Gałąź `gpt/177-upgrade-panel-tests`.

Dodano `app/src/test/java/com/centurywar/screens/BattleUpgradePanelTest.kt`: osiem kombinacji wielkości ekranu i skali tekstu, w tym oba ekrany Folda, sprawdzanie obszarów dotyku, położenia i kolizji, oraz dostępności akcji przy braku złota i maksymalnym stopniu. Testy są wstępne i mogą ujawnić istniejące błędy układu albo wymagać poprawek po pierwszym uruchomieniu. Raport to `docs/reports/gpt-177-upgrade-panel-tests.md`.

Nie zmieniono kodu aplikacji, symulacji ani wzorców zrzutów. Budowanie i testy: oczekują na wolny Gradle. Po uruchomieniu należy tu wkleić końcówki logów, a rzeczywiste błędy układu opisać z wymiarami i oddzielnym testem `@Ignore` zgodnie ze zleceniem.
