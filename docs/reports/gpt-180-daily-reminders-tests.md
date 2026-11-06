# Zlecenie 180 — testy przypomnień dnia

Data: 2026-11-06. Baza `origin/main`: `7d847576e27bc5411bfd93877c1ba6df18af05cd`. Gałąź: `gpt/180-daily-reminders-tests`.

Dodano `app/src/test/java/com/centurywar/notifications/DailyRemindersTest.kt` i niniejszy raport. Pięć testów Robolectric sprawdza zastąpienie alarmu i jego godzinę, anulowanie po wyłączeniu opcji lub cofnięciu zgody, odrzucenie spóźnionego powiadomienia, język kanału oraz brak ponownej prośby po odmowie. Testy oczekują na zakończenie kolejki operacji wewnętrznego executora; kod produkcyjny pozostał bez zmian.

## Budowanie i testy

Gradle uruchomiono z JDK 17 i SDK 36, kolejno, `--no-daemon` i `--console=plain`; testy dodatkowo `--no-configuration-cache`.

```text
:app:testDebugUnitTest --tests '*DailyRemindersTest*'
BUILD SUCCESSFUL in 52s
37 actionable tasks: 18 executed, 19 from cache

:app:testDebugUnitTest --tests '*DailyReminderLogicTest*'
BUILD SUCCESSFUL in 11s
37 actionable tasks: 1 executed, 36 up-to-date

:app:testDebugUnitTest --tests '*SettingsNotificationsTest*'
BUILD SUCCESSFUL in 14s
37 actionable tasks: 1 executed, 36 up-to-date

:app:testDebugUnitTest --tests '*LTest*'
BUILD SUCCESSFUL in 15s
37 actionable tasks: 1 executed, 36 up-to-date

:app:assembleDebug
BUILD SUCCESSFUL in 10s
43 actionable tasks: 8 executed, 7 from cache, 28 up-to-date
```

`fastTest` przerwano po ujawnieniu istniejącej usterki środowiskowej, więc pełny wynik zestawu nie jest znany:

```text
> Task :app:testDebugUnitTest
ReplayShareProviderTest > onlyReadOnlyReplayFilesAreExposed FAILED
    java.nio.file.FileSystemException at ReplayShareProviderTest.kt:63
```

## Ograniczenia i dalsze kroki

Nie wykonano testów na urządzeniu: Robolectric nie potwierdza zachowania Doze i implementacji alarmów producenta. Proponowane osobne zadania: weryfikacja przyczyny błędu systemu plików `ReplayShareProviderTest.kt`; testy urządzenia zgodnie z `docs/NOTIFICATIONS_INTEGRATION.md`. Test odczytuje prywatny executor przez refleksję, więc jego zmiana może wymagać poprawki testu. Hash ostatniego commita raportu można odczytać przez `git rev-parse HEAD`; umieszczenie go w samym commicie zmieniłoby hash.
