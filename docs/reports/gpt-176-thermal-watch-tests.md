# 176 — testy ThermalWatch

Baza `origin/main` 7d847576e27bc5411bfd93877c1ba6df18af05cd (6.11.2026); gałąź `gpt/176-thermal-watch-tests`. Zmieniono wyłącznie `app/src/test/java/com/centurywar/engine/ThermalWatchTest.kt` oraz ten raport. Kod produkcyjny niezmieniony.

Cztery testy sprawdzają przejścia stanu, przekazanie sygnału do regulatora jakości, usunięcie słuchacza i brak kolejnych aktualizacji po stop, powtórne start/stop oraz brak PowerManager. Po zmianie testu końcową wersję uruchomiono trzy razy z `--rerun`; wszystkie trzy przebiegi przeszły. Testy nie są pomiarem przegrzewania fizycznego urządzenia i nie weryfikują wszystkich implementacji producentów.

```text
ThermalWatchTest, trzy przebiegi: BUILD SUCCESSFUL (18s, 14s, 14s)
QualityGovernorTest: BUILD SUCCESSFUL in 10s
:app:assembleDebug: BUILD SUCCESSFUL in 10s
LTest: BUILD SUCCESSFUL in 16s
```

`fastTest` przerwano po błędzie istniejącego `ReplayShareProviderTest.onlyReadOnlyReplayFilesAreExposed` (FileSystemException, linia 63) na Windows. Cały zestaw nie jest zielony ani kompletny. Zlecenie wymaga ponowienia pełnego fastTest w środowisku bez tego błędu. Nie otwarto PR.
