# Zlecenie 178 — panel porównania Kodeksu

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/178-codex-compare-panel-tests`. Pierwszy commit testów `548778e40af782eaa3fc4687c9128b07e776b652`; własny hash końcowego commita raportu jest dostępny przez `git rev-parse HEAD`.

## Zmiany

Dodano `app/src/test/java/com/centurywar/screens/CodexComparePanelTest.kt` i ten raport. Test w 40 kombinacjach (10 języków, 720p i zewnętrzny Fold, 100% i 130%) mierzy faktycznie rysowane selektory, podpisy kolumn oraz legendę; sprawdza granice ekranu, rozdział kolumn, strzałki i kompletność legendy. Dodatkowo testuje niezależny wybór jednostek, zawijanie epok i ról oraz przewijanie treści przy nieruchomych selektorach. Używa Robolectric Native Graphics i nie zmienia wzorców zrzutów ani kodu produkcyjnego.

## Ustalone wyjątki

Pierwszy przebieg wykazał trzy podpisy obu kolumn poniżej 10 sp na **904×2316, 130%** (wymiary z rejestratora tekstu w pikselach):

| Język i napis | Rozmiar sp | Lewa kolumna (x od–do) | Prawa kolumna (x od–do) |
|---|---:|---:|---:|
| de „Reichweite” | 9,67 | 69,13–230,13 | 489,22–650,22 |
| pt „Velocidade” | 9,24 | 69,13–222,13 | 489,22–642,22 |
| uk „Швидкість” | 9,37 | 69,13–228,13 | 489,22–648,22 |

W tych przypadkach test dopuszcza jedynie udokumentowane progi odpowiednio 9,60 / 9,20 / 9,30 sp; pozostałe kombinacje obowiązuje 10 sp z tolerancją pomiaru 0,05. Nie stwierdzono wyjścia tych podpisów poza ekran w wykonywanym teście. Wyjątek nie dowodzi czytelności dla wszystkich użytkowników. Nie wykonano ręcznej oceny na fizycznym urządzeniu; test nie udowadnia braku wszystkich kolizji z wartościami statystyk ani braku przycięcia w każdym położeniu przewijania.

## Testy

Windows, JDK 17, SDK 36, `--no-daemon`:

```text
:app:testDebugUnitTest --tests '*CodexComparePanelTest*' --no-configuration-cache
Pierwszy przebieg: 3 tests completed, 1 failed (trzy wyjątki opisane wyżej).
Po uwzględnieniu jawnych wyjątków:
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 49s
37 actionable tasks: 2 executed, 35 up-to-date

:app:testDebugUnitTest --tests '*CodexCompareModelTest*' --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 10s
37 actionable tasks: 1 executed, 36 up-to-date

:app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 16s
43 actionable tasks: 10 executed, 5 from cache, 28 up-to-date

:app:testDebugUnitTest --tests '*LTest*' --no-configuration-cache
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 16s
37 actionable tasks: 1 executed, 36 up-to-date
```

`fastTest` uruchomiono z `--no-configuration-cache --no-daemon`; po ponad 40 sekundach nadal trwał w `:app:testDebugUnitTest`, a log podał:

```text
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
``` 

Przebieg przerwano na polecenie koordynatora po potwierdzeniu znanego błędu; nie ma pełnego wyniku i zestaw nie jest zielony. Przyczyny błędu nie diagnozowano w tym zleceniu.

## Dalej

- Przeanalizować podział lub skrócenie trzech podpisów na zewnętrznym Foldzie (`CodexComparePanel.kt`) i zweryfikować 10 sp na urządzeniu.
- Uzupełnić test o granice podpisu względem wartości w tej samej karcie i sprawdzić widoczne przycięcia na zrzutach wszystkich języków, bez automatycznej aktualizacji wzorców.

