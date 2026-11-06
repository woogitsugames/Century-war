# Zlecenie 179 — testy banerów bitwy

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/179-battle-banners-tests`; końcowy hash wskazuje `git rev-parse HEAD`.

## Zmiany

Dodano `app/src/test/java/com/centurywar/screens/MilestoneRibbonTest.kt`, `EvolveBannerTest.kt` i niniejszy raport. Sprawdzono FIFO, zatrzymanie czasu przy `hold`, wyczyszczenie i ponowne użycie oraz bounded queue (1001 zdarzeń: pierwsze czekające i najnowsze zachowane); opóźnienie, pojedyncze pokazanie na epokę, blokadę, odrzucenie, wygasanie, zmianę języka i geometrię banera oraz przycisków na 1600×720 i 904×2316. `MilestoneRibbon` przy przepełnieniu celowo zastępuje ostatnią oczekującą wiadomość najnowszą — nie jest to kolejka bezstratna.

Nie zmieniono kodu gry ani wzorców zrzutów; test geometrii sprawdza także 48 dp i kolizję przycisków. Nie sprawdzano ręcznie czytelności tekstu i animacji na fizycznych urządzeniach ani wszystkich języków i skal.

## Testy

Windows, JDK 17, SDK 36, `--no-daemon`:

```text
:app:testDebugUnitTest --tests '*MilestoneRibbonTest*' --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 50s
37 actionable tasks: 18 executed, 19 from cache

:app:testDebugUnitTest --tests '*EvolveBannerTest*' --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 15s
37 actionable tasks: 1 executed, 36 up-to-date

:app:assembleDebug
BUILD SUCCESSFUL in 10s
43 actionable tasks: 8 executed, 7 from cache, 28 up-to-date

:app:testDebugUnitTest --tests '*LTest*' --no-configuration-cache
BUILD SUCCESSFUL in 16s
37 actionable tasks: 1 executed, 36 up-to-date

fastTest --no-configuration-cache
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

`fastTest` przerwano po ujawnieniu znanego błędu według instrukcji koordynatora; pełny wynik nie jest dostępny i zestaw nie jest zielony. Przyczyny błędu nie diagnozowano w tym zleceniu.

## Dalej

- Zbadać `ReplayShareProviderTest.kt:63` na Windows i uruchomić pełne `fastTest`.
- Ocenić zrzuty banera i wstęgi przy 130% tekstu, w wielu językach i na urządzeniach, bez aktualizowania wzorców w tym zadaniu.
