# Zlecenie 193 — dłoń samouczka

**GRADLE NIE URUCHOMIONO — PRACE W TOKU, OCZEKIWANIE NA KOLEJKĘ TESTÓW.**

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/193-tutorial-hand`.

Zmieniono `TutorialOverlay.kt`, dodano `TutorialHand193Test.kt` i ten raport. W kroku złota dłoń wskazuje cel z boku, zamiast sięgać drugiego rzędu. Obliczany jest zachowawczy prostokąt dłoni, a w razie przecięcia z licznikiem armii dłoń nie jest rysowana. Wyświetlane „0 : 0” według `BattleTopBar.drawArmyCounts` oznacza żywe jednostki obu stron. Nie zmieniono układu paska ani tekstów gry.

Stan: testy i kompilacja oczekują na kolejkę Gradle; nie potwierdzono działania zmiany. Ryzyka: synchronizacja wyliczenia prostokąta licznika z `BattleTopBar`; w nietypowej kolizji ręka jest pomijana i pozostaje obwódka celu. Wzorców zrzutów nie aktualizowano. Dalsze zadanie: przegląd ekranów samouczka na telefonie i Foldzie.
