# Zlecenie 191 — dymek po pierwszej bitwie

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/191-hint-map-bubble`; końcowy hash podaje `git rev-parse HEAD`.

## Zmiany

- `HintOverlay.kt`: na mapie, dla dymka po pierwszej bitwie, prostokąt widocznych odblokowanych węzłów i łączącej je drogi (z zapasem 6 dp na obrys) staje się obszarem chronionym. Dymek najpierw szuka miejsca z istniejącymi ograniczeniami, potem priorytetowo omija drogę, tytuł i Wstecz; jeśli standardowe pozycje nie pasują, przeszukuje wolne miejsca. Nie zmienia zachowania dymków w bitwie ani kolejności powiadomień.
- `L.kt`: wyłącznie `putAll(part212())` i nowa `part212` z `map191_next_steps` w 10 językach, używana przez dymek mapy. Poprzedni `first_next_steps` pozostaje bez zmian. Nowy klucz wynika z ograniczenia prac do zarezerwowanej części; tekst zachowuje radę o trwałych badaniach za medale, a angielski jest krótszy. Liczba wywołań `putAll(part` i funkcji `private fun part` wynosi 85 / 85.
- `app/src/test/java/com/centurywar/screens/MapHint191Test.kt`: sprawdza rzeczywistą mapę i dymek w czterech rozmiarach, 10 językach i 100%/130%, w tym położenie poza węzłami i prostokątem drogi; angielski ma najwyżej tyle wierszy co polski.
- Ten raport.

Pierwszy test wykrył brak wolnego położenia w 1280×720. Dodano przeszukiwanie wolnej przestrzeni; powtórny test wszystkich 80 kombinacji przeszedł. Nie zmieniano `CampaignScreen.kt` ani wzorców. Zrzuty pierwszej mapy z aktywną wskazówką mogą się zmienić; nie aktualizowano ich ani nie porównywano ręcznie. Nie przeprowadzono testu na fizycznym urządzeniu. Gdy w nieobjętym teście rozmiarze nie istnieje miejsce dla dymka poza chronionym obszarem, jego rysowanie jest pomijane; zasłanianie ścieżki nie jest wówczas alternatywą.

## Testy

Windows, JDK 17, SDK 36; `--no-daemon` i dla testów `--no-configuration-cache`:

```text
:app:testDebugUnitTest --tests '*MapHint191Test*'
Pierwszy przebieg: FAILED (1280x720/pl/100: dymek nie został ułożony)
Po poprawce: BUILD SUCCESSFUL in 23s
37 actionable tasks: 4 executed, 33 up-to-date

:app:testDebugUnitTest --tests '*HintOverlay*' --tests '*HintFramePlacementTest*'
  --tests '*FirstMinutesTest*' --tests '*FirstHourImpression61Test*' --tests '*LTest*'
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 36s
37 actionable tasks: 1 executed, 36 up-to-date
```

Wyniki XML potwierdzają uruchomienie każdej z pięciu wymaganych klas/filtrów; uruchomiono je razem jednym procesem Gradle.

```text
:app:assembleDebug
BUILD SUCCESSFUL in 16s
43 actionable tasks: 10 executed, 5 from cache, 28 up-to-date

fastTest --no-configuration-cache
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

`fastTest` przerwano na polecenie koordynatora po ujawnieniu znanego błędu; pełny wynik nie jest dostępny i zestaw nie jest zielony. Przyczyny nie diagnozowano w tym zadaniu.

## Dalej

- Sprawdzić `ReplayShareProviderTest.kt:63` na Windows i ponowić kompletny `fastTest`.
- Obejrzeć aktualne zrzuty pierwszej mapy w różnych językach i na fizycznych urządzeniach; ocenić wpływ nowej pozycji wskazówki przed ewentualną aktualizacją wzorców.
