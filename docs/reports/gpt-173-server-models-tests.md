# Zlecenie 173 — walidacja modeli serwera

Data: 2026-11-06. Baza `origin/main`: `7d847576` (pełny hash: patrz `git rev-parse 632a6b3c^`). Gałąź `gpt/173-server-models-tests`. Commit z testami: `632a6b3c84ecabeed6324ac289c58319405ec980`; własnego hasha końcowego commita raport nie może zawierać — podaje go `git rev-parse HEAD`.

## Zakres

Dodano wyłącznie `server/src/test/kotlin/com/centurywar/server/ModelsValidationTest.kt` oraz ten raport. Sześć testów tabelarycznych obejmuje UUID v4 i wariant, poprawne i niepoprawne znaki oraz granice nicków, granice i składnię tablic, mapowanie kampanii/tygodnia/pozostałych trybów i błędne kombinacje, badania, poziomy trudności i samouczek, a także UTC ±1 ms po obu stronach północy i przed 1970. Nie zmieniono kodu produkcyjnego.

`validateSubmission` sprawdza wyłącznie metadane; UUID i wersję sprawdza `Api.kt`, a replay, dodatni wynik i relację dnia/seeda — `ReplayVerifier.kt`. Test z pustymi pozostałymi polami dokumentuje tę granicę funkcji, nie oznacza akceptacji takiego wyniku przez endpoint. `submittedBoard` samo ignoruje parametr tygodnia przy pozostałych planszach; dalsza weryfikacja odrzuca niedozwolony dzień w kampanii/Endless/potyczce.

## Niespójność dokumentacji

`server/README.md` opisuje nick jako „1–24 znaki Unicode”. `validNick` używa `String.length`, czyli jednostek UTF-16, a nie liczby punktów kodowych: 12 liter spoza BMP (24 jednostki) przechodzi, 13 takich liter (26 jednostek) nie. Test utrwala obecne zachowanie. Dokumentacja nie wyjaśnia tego ograniczenia; dodatkowo osobny znak łączący akcent nie przechodzi regexu, mimo że litery złożone fabrycznie są obsługiwane. Nie zmieniano implementacji ani README. README w innej sekcji podaje też przestarzałą wartość formatu Replay 5, mimo że sekcja protokołu i kod podają 7.

## Weryfikacja

Windows, JDK 17, `-PserverOnly=true`, `--no-daemon` i `--no-configuration-cache`:

```text
:server:test --tests '*ModelsValidationTest*'
ModelsValidationTest > utcDaysUseFloorAcrossEpochAndMidnight() PASSED
ModelsValidationTest > submittedBoardsCoverStageWeekAndNoStage() PASSED
ModelsValidationTest > submissionChecksResearchDifficultyTutorialAndBoardCombination() PASSED
ModelsValidationTest > nickUsesOneToTwentyFourUtf16UnitsAndAllowedCharacters() PASSED
ModelsValidationTest > playerRequiresCanonicalLowercaseUuidV4AndRfcVariant() PASSED
ModelsValidationTest > boardNamesHaveExplicitIndexAndLengthBounds() PASSED
BUILD SUCCESSFUL in 22s
10 actionable tasks: 7 executed, 3 from cache

:server:test
SqliteMaintenanceTest > migratesSchemaTwoToThreeKeepingDataAndRefusesNewerSchema() FAILED
    org.junit.platform.commons.JUnitException: Failed to close extension context
    Caused by: java.io.IOException: Failed to delete temp directory
    paths: <root>, v2.db, v2.db-shm, v2.db-wal
101 tests completed, 1 failed, 1 skipped
BUILD FAILED in 56s
10 actionable tasks: 1 executed, 9 up-to-date
```

Cały zestaw serwera **nie jest zielony**: błąd sprzątania plików SQLite w istniejącym teście na Windows, niezależny od nowej klasy według miejsca awarii; przyczyny nie diagnozowano do końca. Nie uruchomiono testów Androida — ten zakres dotyczy wyłącznie testów serwera.

## Dalsza praca

- Sprawdzić zamykanie uchwytów SQLite w `SqliteMaintenanceTest.kt` i ponowić pełne `:server:test` na Windows oraz Linux.
- Uzgodnić sposób liczenia długości nicku i doprecyzować `server/README.md`; rozważyć osobną decyzję co do znaków łączących, bez zmiany tego zachowania w zleceniu testowym.
