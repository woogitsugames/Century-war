# 192 — odstęp w oknie etapu

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź: `gpt/192-stage-panel-spacing`. Ostatni commit kodu przed raportem: `0560e7366375bdcea78b36dacd01df400a9bddb5`; hash końcowego commita raportu podaje Git.

## Zmiany

Na niskim panelu zagęszczono wiersze i pokazano nazwę modyfikatora w jednym wierszu z nagłówkiem. Dopasowanie długich tekstów zapobiega wychodzeniu poza panel. Wysokości panelu nie zmieniono. Nie dodano tekstów ani nie zmieniono `L.kt`. Test mierzy odstęp i granice tekstu w 80 kombinacjach: cztery ekrany, 10 języków i dwie skale tekstu; sprawdza też obecność nazwy modyfikatora na aktualnym etapie z modyfikatorem.

Pliki: `app/src/main/java/com/centurywar/screens/CampaignScreen.kt`, nowy `app/src/test/java/com/centurywar/screens/StagePanel192Test.kt`, niniejszy raport.

## Testy

Gradle uruchomiono na JDK 17 i Android SDK 36. Cztery filtry wymienione w zleceniu uruchomiono razem jedną komendą:

```text
:app:testDebugUnitTest --tests '*StagePanel192Test*' --tests '*CampaignLossStreakTest*' --tests '*LTest*' --tests '*HardCampaign189ScreenshotTest*' --console=plain --no-configuration-cache --no-daemon
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 36s
37 actionable tasks: 2 executed, 35 up-to-date
```

Początkowa wersja testu szukała etapu z dwoma modyfikatorami, którego w obecnej kampanii nie znaleziono: `NoSuchElementException`. Po zmianie na istniejący etap z modyfikatorem powyższy przebieg przeszedł.

```text
:app:assembleDebug --console=plain --no-daemon
> Task :app:assembleDebug
BUILD SUCCESSFUL in 16s
43 actionable tasks: 10 executed, 5 from cache, 28 up-to-date
```

```text
fastTest --console=plain --no-configuration-cache --no-daemon
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

`fastTest` przerwano po ujawnieniu tego błędu; pełny zestaw nie ma zielonego wyniku.

## Zrzuty i ograniczenia

`HardCampaign189ScreenshotTest` uruchomiono bez aktualizowania wzorców. Wizualnie obejrzano `pre10_hard189_phone720_pl_03_etap5_trudny.png`: poprzednio nazwa modyfikatora nie mieściła się, po zmianie jest widoczna. Do przeglądu wzorców kwalifikują się obrazy okna etapu `pre10_hard189_{phone720,phone20x9,foldOuter,foldInner}_{pl,en,de}_{02_etap2_normalny,03_etap5_trudny,04_etap5_normalny}.png` oraz istniejące obrazy `05_cel_okno` dla phone720, phone20x9 i foldInner. To lista potencjalnie zmienionych obrazów, nie wynik pełnego porównania pikseli; wzorców nie zatwierdzono ani nie aktualizowano. Nie wykonano prób na fizycznym urządzeniu. Test obejmuje istniejący etap z modyfikatorem, nie potwierdza zachowania hipotetycznego etapu z wieloma modyfikatorami.

Dalsza praca: przegląd i zatwierdzenie różnic zrzutów przez koordynatora; osobna diagnostyka `ReplayShareProviderTest.kt:63` na Windows.
