# Dzień 32 — ręczne wydanie testowe (R12 pozycja 2a)

Gałąź `claude/dzien-32-wydanie-testowe` (baza `integ/tura10`). Workflowów nie uruchamiano.

## Co zrobiono

- **`.github/workflows/android.yml`:** `workflow_dispatch` dostał wejścia `test_release`
  (boolean, domyślnie `false`) i `version_name` (domyślnie `0.1.0`, walidacja `X.Y.Z`). Nowe
  zadanie `test-release`: `needs` jak `release`, warunek
  `workflow_dispatch && inputs.test_release == true && ref nie jest tagiem`. Podpis tylko kluczem
  upload z czterech sekretów (brak sekretu albo alias `androiddebugkey` przerywa), wymagane
  `CW_PRIVACY_URL`, jawnie puste `CW_SERVER_URL`/`CW_HMAC_KEY`, Gradle z
  `-PskipCheck=releaseDay,serverUrl,hmacKey -PtesterSurvey=true -PreleaseVersionName=…`.
  Artefakty AAB, APK i mapowanie R8 na 90 dni, podsumowanie w `GITHUB_STEP_SUMMARY`.
  Wyzwalacze bez zmian: tag `v*`, PR do `main`, ręcznie. Zadanie `release` (tag) bez zmian.
- **Blokada w Gradle** (`ReleaseChecklist.ciSkipProblem`, wywołanie w `app/build.gradle.kts`):
  przy `GITHUB_ACTIONS=true` zdarzenie `push` nie może mieć żadnego `-PskipCheck`; build
  z ważnym podpisem upload może pominąć wyłącznie podzbiór `releaseDay,serverUrl,hmacKey` i tylko
  przy `workflow_dispatch`. Smoke-test z podpisem debug (`release-check.yml`) i buildy lokalne
  działają jak dotąd. Test jednostkowy w `ReleaseChecklistTest.ciSkipsAreLimitedToManualTestRelease`.
- **`tests/test_ci_release_gates.py`:** push tylko z tagami; jedyne `-PskipCheck` w `android.yml`
  to `releaseDay,serverUrl,hmacKey` w zadaniu `test-release`; brak `ciTestDebugSigning`,
  `CW_RELEASE_SMOKE` i `ownerInput` w `android.yml`; sekrety podpisu, ankieta, 3× retencja 90 dni.
- **Dokumenty:** `docs/RELEASE.md` („Kiedy wysyłamy testerom” — ścieżka 0.1.0: test wewnętrzny,
  potem ten sam AAB na zamknięty; „Automatyczna blokada Gradle” — tabela wyjątków w CI),
  `docs/CI.md` (sekcja „Wydanie testowe”), `docs/OWNER_CHECKLIST.md` (krok 11 dla właściciela:
  sekrety, Run workflow, pobranie AAB, test wewnętrzny, awans na zamknięty).

## Jak działały zabezpieczenia przed zmianą (sprawdzone w kodzie)

- Podpis debug: tylko gdy jednocześnie `-PciTestDebugSigning=true`, `GITHUB_ACTIONS=true`,
  `CW_RELEASE_SMOKE=true` i zdarzenie `pull_request`/`workflow_dispatch`
  (`canUseCiDebugSigning`). Zadanie `test-release` żadnego z tych znaczników nie ustawia
  i dodatkowo sprawdza brak `CW_RELEASE_SMOKE`.
- `ownerInput`: pomijalne tylko przy dozwolonym podpisie debug (check w `build.gradle.kts`) —
  w wydaniu testowym niemożliwe.
- Podpis: `releaseTaskRequested` wymaga ważnego keystore upload (nie `debug.keystore`,
  nie `androiddebugkey`); `problems()` też blokuje brak podpisu niezależnie od `skipCheck`.
- Luka zamknięta tą zmianą: wcześniej `-PskipCheck` w CI nie był ograniczony do żadnego zadania
  (wystarczyło dopisać flagę w kroku `release`). Teraz Gradle sam odrzuca wyjątki przy tagu
  i inne niż trzy testowe przy podpisie upload.

## Weryfikacja

- `python3 -c "import yaml; yaml.safe_load(...)"` dla `android.yml`: OK.
- `python3 -m unittest discover -s tests -p 'test_*.py'`: 55 testów OK.
- Gradle tu nie działa (sieć blokuje dl.google.com), więc **nie skompilowano** zmian w
  `buildSrc` i `app/build.gradle.kts` ani nie uruchomiono `ReleaseChecklistTest`. Zmiana jest
  mała (jedna funkcja, jedno wywołanie), ale wymaga `./gradlew -p buildSrc test` i konfiguracji
  `:app` u koordynatora.

## Dla koordynatora

1. **Część (b) pozycji 2 — pominięta:** lokalna próba release jak 3.11 (syntetyczne ID, podpis
   debug tylko do próby): R8 i `shrinkResources`, kontrola pól zapisu po R8 (`playtest.json` ze
   stanem ankiety, pola powitania, kamieni milowych i utraconej bitwy w profilu),
   `TestLoopActivity` w procesie `:testloop` w buildzie release (`tools/r8-sim-smoke`, intencja
   `TEST_LOOP` przez `adb`), rozmiar AAB, brak `.so`; wynik do `RELEASE.md`.
2. Uruchomić `./gradlew -p buildSrc test` oraz np. `./gradlew :app:help` (konfiguracja) po scaleniu.
3. Sprawdzić, że krok „Potwierdź blokadę release bez klucza upload” w `release-check.yml` nadal
   kończy się błędem podpisu (bez podpisu nowa reguła nie działa dla PR, więc powinien).
4. Po wgraniu AAB do Play: `release/last_version_code.txt` = `1`; przed kolejnym wydaniem
   testowym zwiększyć `releaseVersionCode`.
5. Uwaga: gdy commit ma tag SemVer, Gradle bierze `versionName` z tagu, nie z `version_name`.
   Wydania testowego nie należy uruchamiać na otagowanym commicie (ref tagu jest i tak odrzucany).
