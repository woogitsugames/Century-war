# Zlecenie 188 — poradnik gracza

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/188-player-guide`. Commit poradnika: `e8dcd19a20283243baf31188223f3c87be522d99`; ostatni commit z raportem: `git rev-parse HEAD` (jego hash nie może być umieszczony we własnej treści bez zmiany hasha).

**NIE URUCHAMIANO GRADLE** — dokumentacja, zlecenie określa „bez budowania”. Nie zmieniono kodu gry ani `L.kt`.

## Wynik

Dodano `docs/players/PORADNIK.md` oraz ten raport. Poradnik ma spis treści i obejmuje pierwszą bitwę, awans, zasoby, role i kontry, wieże, ostrzał, Zryw, dowódcę, doktryny, badania, tryby, pogodę, modyfikatory, ustawienia, powtórki i zapis. Rozkazy opisano ogólnie, bez ich szczegółów i nagród. Nazwy polskie porównano z `engine/L.kt`, a zachowanie z kodem `:sim` i ekranów.

## Liczby i źródła

| Liczba użyta w poradniku | Źródło kodu |
|---|---|
| Dziewięć epok i ich kolejność | `sim/src/main/kotlin/com/centurywar/game/Defs.kt:190–273` |
| Cztery role podstawowe w każdej epoce | `sim/src/main/kotlin/com/centurywar/game/Defs.kt:4–5,390–394` |
| Maksymalnie cztery miejsca na wieże | `sim/src/main/kotlin/com/centurywar/game/Defs.kt:162–163` |
| Jedna z trzech doktryn w zwykłym wyborze | `sim/src/main/kotlin/com/centurywar/game/Modes.kt:3–5` |
| Dzień wyzwania według UTC | `sim/src/main/kotlin/com/centurywar/game/Modes.kt:637` |
| Tydzień zaczyna się w poniedziałek według UTC | `sim/src/main/kotlin/com/centurywar/game/Modes.kt:441–463` |

Źródła przy liczbach są również podlinkowane w poradniku. Sformułowania porządkowe typu „pierwsza bitwa” nie określają parametru balansu.

## Kontrola

Lokalny skrypt Pythona policzył słowa i sprawdził istnienie plików oraz numerów linii w odnośnikach:

```text
words=2199; source_links=11; files_and_lines=OK
```

Wymagane 1500–3000 słów jest spełnione; `git diff --check` nie zgłosił błędów. Skrypt nie sprawdza merytorycznej zgodności wszystkich zdań. Nie testowano poradnika z graczami ani na urządzeniu.

## Ograniczenia i dalsze zadania

Mechaniki opisano na poziomie pomocy gracza, bez gwarancji wyniku bitwy i bez liczb dotyczących zmieniających się rozkazów. Instrukcja wznowienia uwzględnia możliwość braku zgodności starszego zapisu, a import profilu opisano jako zastąpienie danych. Propozycja dalszego zadania: **Sprawdzenie poradnika przez nowych graczy** (`docs/players/PORADNIK.md` i raport z testów), zwłaszcza etykiet oraz zrozumiałości na telefonie.
