# Zlecenie 174 — dokumentacja API serwera

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź `gpt/174-server-api-docs`; hash końcowego commita podaje `git rev-parse HEAD`.

## Zmiany

`server/README.md`: tabela wszystkich ośmiu literalnych tras `Api.kt` z metodami, auth, limitami, polami i kodami; doprecyzowanie etapu walidacji, przyczyn 422, limitu UTF-16 nicku i wersji Replay 7. `server/src/test/kotlin/com/centurywar/server/ApiRoutesDocTest.kt`: porównanie par metoda/ścieżka w kodzie i tabeli oraz test wykrycia syntetycznie dodanej trasy i odmiennej metody. Dodano ten raport. Nie zmieniono kodu produkcyjnego ani `docs/SERVER_DEPLOY.md`.

Test parsuje literalne wywołania tras na początku wiersza w `Api.kt`; dynamiczne ścieżki, aliasy lub trasy w innych plikach wymagają rozwinięcia testu. Nie sprawdza semantyki wszystkich pól ani zgodności wersji dokumentu z faktycznie wdrożonym serwerem. W sprawdzonych fragmentach `docs/SERVER_DEPLOY.md` nie znaleziono rozbieżności z tabelą; ten dokument nie był objęty pełnym audytem.

## Wyniki

Windows, JDK 17, SDK 36; każde wywołanie z `--no-daemon`:

```text
:server:test -PserverOnly=true --tests '*ApiRoutesDocTest*' --no-configuration-cache
ApiRoutesDocTest > everyLiteralApiRouteHasMethodAndPathInReadmeTable() PASSED
ApiRoutesDocTest > newUnlistedMethodAndRouteAreDetected() PASSED
BUILD SUCCESSFUL in 22s

Próba negatywna: tymczasowo usunięto wiersz GET /healthz z tabeli README;
:server:test -PserverOnly=true --tests '*ApiRoutesDocTest.everyLiteralApiRouteHasMethodAndPathInReadmeTable*'
1 test completed, 1 failed
BUILD FAILED in 7s
```

Negatywna próba wykazała brak opisanej trasy; wiersz README został przywrócony. Niezależnie test w pamięci dopisuje nieopisaną trasę i sprawdza wykrycie brakującej pary. Dane produkcyjne nie były zmieniane.

```text
:app:assembleDebug
BUILD SUCCESSFUL in 13s
43 actionable tasks: 15 executed, 23 from cache, 5 up-to-date

:app:testDebugUnitTest --tests '*LTest*' --no-configuration-cache
BUILD SUCCESSFUL in 21s
37 actionable tasks: 7 executed, 2 from cache, 28 up-to-date

fastTest --no-configuration-cache
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

`fastTest` przerwano po potwierdzeniu znanego błędu na polecenie koordynatora; pełny zestaw nie ukończył się i nie jest zielony. Przyczyny błędu nie zbadano w tym zleceniu. `:server:test` w pełnym zakresie nie uruchomiono w tej gałęzi.

## Dalej

- Zdiagnozować test udostępniania powtórek na Windows i ponowić pełne `fastTest`.
- Przy zmianie sposobu rejestracji tras rozbudować `ApiRoutesDocTest.kt` o nowe konstrukcje; okresowo porównać dokument z wdrożoną wersją.
