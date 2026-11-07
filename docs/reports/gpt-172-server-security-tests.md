# 172 — testy Security.kt

Baza: `origin/main` f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7 (6.11.2026). Gałąź: `gpt/172-server-security-tests`. Gradle uruchomiono na Windows z JDK 17.

Dodano 22 testy brzegowe podpisu, granic okna czasu, formatu nagłówków i sekretu, limitera, identyfikatorów oraz adresu proxy. Zmienione pliki: `server/src/test/kotlin/com/centurywar/server/SecurityTest.kt` i ten raport. Produkcyjny kod serwera pozostał bez zmian.

## Wyniki

```text
:server:test -PserverOnly=true --tests *SecurityTest*
BUILD SUCCESSFUL in 42s
10 actionable tasks: 8 executed, 2 from cache
```

```text
:server:test -PserverOnly=true
117 tests completed, 1 failed, 1 skipped
BUILD FAILED in 55s
```

Istniejący `SqliteMaintenanceTest.migratesSchemaTwoToThreeKeepingDataAndRefusesNewerSchema` zakończył się błędem zamknięcia katalogu tymczasowego JUnit na Windows: nie udało się usunąć `v2.db`, `v2.db-shm`, `v2.db-wal`. Podejrzenie na podstawie kodu: przy odrzuceniu nowszej wersji schematu konstruktor `SqliteStore` otwiera połączenie i wykonuje rollback, ale nie zamyka go, gdy konstruktor rzuca wyjątek (`SqliteStore.kt:72,81–89,155,225–230`; scenariusz `SqliteMaintenanceTest.kt:139–144`). Log potwierdza blokadę plików, ale sam nie wskazuje procesu; ten test i kod produkcyjny są poza zakresem zlecenia. Wymagany zielony pełny zestaw serwera nie został osiągnięty. Należy odtworzyć problem i przejrzeć zamykanie połączeń SQLite w `SqliteMaintenanceTest.kt` i `SqliteStore`, a następnie ponowić pełny zestaw.

`:app:assembleDebug` (BUILD SUCCESSFUL in 13s) i `LTest` (BUILD SUCCESSFUL in 20s) uruchomiono na tej gałęzi. `fastTest` rozpoczęto, lecz przerwano po błędzie istniejącego `ReplayShareProviderTest.onlyReadOnlyReplayFilesAreExposed` (`FileSystemException`, linia 63), aby zwolnić kolejkę; wynik całego zestawu pozostaje nieznany i wymaga ponownego uruchomienia. Zlecenie jest nieukończone do czasu przejścia pełnych wymaganych testów. Nie otwierano PR.
