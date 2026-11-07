# 194 — pomiar podpisów bitwy

Data: 2026-11-06. Gałąź `gpt/194-battle-short-labels` bazuje na `gpt/191-hint-map-bubble` (`0ee8f4a4a1b89340da05a0d2a454d4a5854c55a8`), której baza main to `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Ostatni commit pomiaru przed raportem: `76741d57`. Gradle uruchomiono.

## Wynik i zakres

Zadanie **niedokończone**: pomierzono warianty, lecz nie zmieniono `battle84_armory_short`. Instrukcja koordynacji dopuszcza dla 194 zmiany `L.kt` wyłącznie w nowej `part213`; istniejący klucz znajduje się w innej części, a `BattleScreen.kt` nie należy do dozwolonego zakresu. Nowy klucz bez podłączenia nie zmieniłby podpisu. Zachowano istniejące teksty i odziedziczoną `part212`.

Nowy plik `app/src/test/java/com/centurywar/screens/BattleShortLabels194Test.kt` mierzy czcionkę pogrubioną przez rzeczywiste `Ui.measure` i `Ui.fit`, wykorzystując geometrię przycisku ekranu bitwy, w 10 językach, 100/130%, phone720 i zewnętrzny Fold; dostępne miejsce to szerokość przycisku minus 8 dp. Native graphics Robolectric symuluje czcionkę; nie zastępuje pomiaru na urządzeniu. Pierwszy pomiar w trybie domyślnym użył sztucznego Paint oraz błędnej skali i został odrzucony.

## Zbrojownia — porównanie przy 130%

Poniżej rzeczywisty tekst → kandydat z istniejącego `armory`; szerokość kandydata po `fit` / dostępna szerokość (dp), phone720 i Fold. To propozycje, nie wdrożone zamiany; nawet wynik po zmniejszeniu może dawać zbyt małą czcionkę. Wartości powyżej miejsca oznaczają, że `fit` osiąga minimalny rozmiar i tekst się nie mieści.

| Klucz | Język | Obecnie → kandydat | phone720 | Fold |
|---|---|---|---:|---:|
| battle84_armory_short | pl | Zbroj. → Zbrojownia | 60,56 / 62 | 39,87 / 40 |
| battle84_armory_short | en | Armory → Armory | 42,78 / 62 | 39,49 / 40 |
| battle84_armory_short | de | Rüst. → Rüstkammer | 60,00 / 62 | 45,13 / 40 |
| battle84_armory_short | es | Armería → Armería | 45,00 / 62 | 36,48 / 40 |
| battle84_armory_short | fr | Arsenal → Armurerie | 56,67 / 62 | 38,36 / 40 |
| battle84_armory_short | it | Armeria → Armeria | 45,00 / 62 | 36,48 / 40 |
| battle84_armory_short | pt | Arsenal → Arsenal | 43,33 / 62 | 38,74 / 40 |
| battle84_armory_short | uk | Арсен. → Зброярня | 58,33 / 62 | 39,49 / 40 |
| battle84_armory_short | nl | Arsenaal → Wapenkamer | 60,56 / 62 | 46,26 / 40 |
| battle84_armory_short | tr | Cephane → Cephanelik | 61,11 / 62 | 39,87 / 40 |

Polska i ukraińska propozycja mieści się tylko po zmniejszeniu; niemiecki `Rüstkammer` nie mieści się na Foldzie. Pozostałe podpisy HUD `battle84_barrage_short`, `battle84_rally_short` i `comm125_short` są pełnymi słowami w obecnych tłumaczeniach; w układzie trzyrzędowym podpis dowódcy jest pusty. Inne klucze z `_short` w bitwie dotyczą wyników i paska informacji, a nie tego przycisku; nie zmieniono ich.

## Testy

```text
:app:testDebugUnitTest --tests '*BattleShortLabels194Test*' --console=plain --no-configuration-cache --no-daemon
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 21s
37 actionable tasks: 2 executed, 35 up-to-date
```

```text
:app:testDebugUnitTest --tests '*BattleShortLabels194Test*' --tests '*LTest*' --tests '*LeftHandedHud*' --console=plain --no-configuration-cache --no-daemon
BUILD SUCCESSFUL in 57s
37 actionable tasks: 18 executed, 19 from cache
```

Łączny przebieg powyżej używał pierwotnej wersji pomiaru; po poprawce natywny pomiar powtórzono osobno. `LTest` i `LeftHandedHud` pozostały niezmienione.

```text
:app:assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 11s
43 actionable tasks: 8 executed, 7 from cache, 28 up-to-date
```

```text
fastTest --console=plain --no-configuration-cache --no-daemon
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

`fastTest` przerwano po błędzie; pełny wynik nie jest zielony. Kod i teksty aplikacji nie zostały zmienione, zrzutów nie aktualizowano. Dalsza praca: rozstrzygnąć zakres zmiany istniejącego klucza i ocenić czytelność pełnych nazw na Foldzie; osobno zbadać błąd `ReplayShareProviderTest` na Windows.

## Decyzja koordynatora (7.11.2026)

Pomiar wystarcza: obecne skróty mieszczą się na phone720 i zewnętrznym Foldzie przy 130%, a pełne nazwy z `armory` w części języków (de, nl) się nie mieszczą. Zostawiamy obecne teksty; zlecenie zamknięte bez zmian w `L.kt`. Test pomiarowy `BattleShortLabels194Test` zostaje.
