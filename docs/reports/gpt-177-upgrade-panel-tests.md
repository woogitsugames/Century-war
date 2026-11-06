# Zlecenie 177 — testy panelu ulepszeń

Data: 2026-11-06. Baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`. Gałąź `gpt/177-upgrade-panel-tests`. Commit z testami: `0e84c896fe238943380ea7cc9b56d37211ebde98`; hash ostatniego commita raportującego ten wynik ustala `git rev-parse HEAD` (nie można wpisać jego własnego hasha do jego treści).

## Zakres

Dodano `app/src/test/java/com/centurywar/screens/BattleUpgradePanelTest.kt` i ten raport. Test sprawdza dwa przyciski ulepszenia jednostki i wszystkie widoczne przyciski czterech zajętych slotów wież oraz zamknięcia panelu: bezpieczny obszar, rzeczywisty obszar dotyku z `hitSlop` i przesunięciem, minimum 48 dp i wzajemne kolizje. Cztery ekrany (1280×720, 1600×720, 904×2316, 2184×1968) przy skali 100% i 130% dają łącznie osiem kombinacji. Dodatkowo testuje stan przy braku środków, dostatecznych środkach i maksymalnym stopniu jednostki i wieży, widoczność sprzedaży i ulepszenia przy pustym/zajętym slocie. Testy operują na osobnym profilu i bez kroków symulacji.

Nie zmieniono `BattleUpgradePanel.kt`, `BattleScreen.kt`, kodu symulacji ani wzorców zrzutów. Test układu w ośmiu konfiguracjach przeszedł; nie wykryto w nim błędu wymagającego `@Ignore`. Test nie sprawdza zawijania i czytelności tekstu piksel po pikselu ani wszystkich insets lub języków. Nie wykonywano ręcznej inspekcji na urządzeniach.

## Wyniki Gradle

Środowisko Windows, JDK 17, SDK 36; komendy z `--no-daemon`. Pierwsze `assembleDebug` nie powiodło się, gdy ograniczony PATH nie zawierał Git (`A problem occurred starting process 'command git'`); po dodaniu Git do PATH ponowiono skutecznie.

```text
:app:testDebugUnitTest --tests '*BattleUpgradePanelTest*' --console=plain --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 17s
37 actionable tasks: 19 executed, 18 from cache

:app:testDebugUnitTest --tests '*LeftHandedHud*' --console=plain --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 20s
37 actionable tasks: 1 executed, 36 up-to-date

:app:assembleDebug --console=plain
> Task :app:assembleDebug UP-TO-DATE
BUILD SUCCESSFUL in 9s
43 actionable tasks: 43 up-to-date

:app:testDebugUnitTest --tests '*LTest*' --console=plain --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 16s
37 actionable tasks: 1 executed, 36 up-to-date

fastTest --console=plain --no-configuration-cache
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
TestLoopIntentTest > ownFilesAndProvidersAreRejected FAILED
    java.lang.AssertionError at TestLoopIntentTest.kt:49
```

`fastTest` przerwano po wykryciu powyższych błędów na polecenie koordynatora, aby zwolnić kolejkę; cały zestaw nie zakończył się i nie jest zielony. Przyczyny błędów nie ustalono, nie przypisuję ich zmianie testu. Osobna diagnoza tych testów powinna sprawdzić zgodność z Windows i odtworzyć wyniki w odpowiednim środowisku.

## Dalsza praca

- Diagnoza `ReplayShareProviderTest.kt:63` i `TestLoopIntentTest.kt:49` oraz pełny `fastTest` na środowisku docelowym.
- Opcjonalnie przegląd zrzutów paneli w 10 językach i na urządzeniach 720p/Fold; bez aktualizacji wzorców w tej gałęzi.
