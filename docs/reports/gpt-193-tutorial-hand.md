# Zlecenie 193 — dłoń samouczka

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/193-tutorial-hand`.

Zmieniono `TutorialOverlay.kt`, dodano `TutorialHand193Test.kt` i ten raport. W kroku złota dłoń wskazuje cel z boku, zamiast sięgać drugiego rzędu. Obliczany jest zachowawczy prostokąt dłoni, a w razie przecięcia z licznikiem armii dłoń nie jest rysowana. Wyświetlane „0 : 0” według `BattleTopBar.drawArmyCounts` oznacza żywe jednostki obu stron. Nie zmieniono układu paska ani tekstów gry.

## Budowanie i testy

Gradle uruchomiono kolejno z JDK 17, SDK 36, `--no-daemon`, `--console=plain`; testy z `--no-configuration-cache`.

```text
:app:testDebugUnitTest --tests '*TutorialHand193Test*'
BUILD SUCCESSFUL in 1m 12s
37 actionable tasks: 20 executed, 17 from cache

:app:testDebugUnitTest --tests '*Tutorial*'
BUILD SUCCESSFUL in 30s
37 actionable tasks: 1 executed, 36 up-to-date

:app:testDebugUnitTest --tests '*FirstMinutesTest*'
BUILD SUCCESSFUL in 15s
37 actionable tasks: 1 executed, 36 up-to-date

:app:testDebugUnitTest --tests '*FirstHourImpression61Test*'
BUILD SUCCESSFUL in 33s
37 actionable tasks: 1 executed, 36 up-to-date

:app:testDebugUnitTest --tests '*LTest*'
BUILD SUCCESSFUL in 17s
37 actionable tasks: 1 executed, 36 up-to-date

:app:assembleDebug
BUILD SUCCESSFUL in 16s
43 actionable tasks: 10 executed, 5 from cache, 28 up-to-date
```

`fastTest` przerwano po ujawnieniu znanego błędu środowiska; pełny wynik zestawu nie jest znany:

```text
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

Test geometrii obejmuje wszystkie kroki, oba ustawienia ręczności i cztery formaty ekranu. Ryzyka: synchronizacja wyliczenia prostokąta licznika z `BattleTopBar`; w nietypowej kolizji ręka jest pomijana i pozostaje obwódka celu. Nie wykonano ręcznego przeglądu obrazów na urządzeniu. Wzorców zrzutów nie aktualizowano: ręka w zrzutach kroku „Złoto i teren” zmienia pozycję i ewentualne wzorce pokazujące ten krok wymagają osobnego zatwierdzenia. Dalsze zadania: przegląd ekranów samouczka na telefonie i Foldzie; diagnoza błędu `ReplayShareProviderTest.kt`. Hash ostatniego commita raportu jest dostępny przez `git rev-parse HEAD`; wpisanie go do jego własnej treści zmieniłoby ten hash.
