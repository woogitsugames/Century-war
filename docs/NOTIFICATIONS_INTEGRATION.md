# Powiadomienia o wyzwaniu dnia

## Działanie

- Domyślnie `Profile.notifications = true`, `Profile.notifyHour = 18` (godzina lokalna, 0–23).
- Dopiero **zwycięstwo w bitwie** (po zapisaniu wyniku w `BattleScreen`) uruchamia zapytanie o `POST_NOTIFICATIONS` (Android 13+). Na starszych wersjach po zwycięstwie zakładany jest alarm bez pytania. Zgoda może być ponownie zaproponowana przy kolejnym zwycięstwie po upływie 14 dni od odmowy.
- Harmonogram opiera się na `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)`; nie jest dokładny i może być opóźniony przez Doze. Nie używa `SCHEDULE_EXACT_ALARM` ani AndroidX.
- Po restarcie, aktualizacji pakietu oraz zmianie czasu/strefy lokalnej alarm jest uzbrajany ponownie. Po każdym alarmie planowany jest kolejny termin.
- Ustawienia i informacja o podejściu rankingowym są wczytywane strumieniowo z `profile.json` bez tworzenia `Game` i `Profile`. Data wyzwania jest UTC (`Daily.dayNumber`), godzina alarmu — lokalna.
- Powiadomienie jest ciche (`IMPORTANCE_LOW`). Nie powstanie przy braku zgody, wyłączonej opcji, zerowej liczbie wygranych, dzisiejszym podejściu rankingowym ani ponownie tego samego dnia lokalnego. Mocno opóźnione alarmy (ponad 4 h) są pomijane.

## Ustawienia w grze

- Ekran **Ustawienia** ma przełącznik powiadomień i przycisk wyboru godziny. Wybór godziny otwiera natywne okno zegara Androida w trybie 24-godzinnym.
- Ustawienia są zapisane w `Profile` i natychmiast przeliczają alarm. Przed pierwszą wygraną prośba o uprawnienie nie pojawi się, nawet po włączeniu przełącznika.
- Napisy powiadomień i nazwy modyfikatorów pochodzą z `engine/L.kt` we wszystkich 10 językach obsługiwanych przez grę.

## Testy ręczne (Android 13–16)

1. Nowa instalacja / wyczyszczone dane: uruchom grę i sprawdź, że **nie ma** prośby o powiadomienia przy starcie. Wygraj bitwę — dopiero wtedy system pyta o zgodę.
2. Odmów. Wygraj następną bitwę: brak ponownej prośby przez 14 dni. Sprawdź `adb shell run-as com.centurywar.game cat shared_prefs/daily_reminder.xml` (APK debug). Po zgodzie pojawia się alarm.
3. Sprawdź alarm: `adb shell dumpsys alarm | grep -A 12 -B 5 com.centurywar.game` i listę poleceń urządzenia: `adb shell cmd alarm help` (podpolecenia różnią się zależnie od wersji Androida).
4. W Ustawieniach ustaw godzinę przypomnienia na najbliższą pełną godzinę. Harmonogram powinien się zaktualizować po zamknięciu selektora czasu. Sprawdź `adb shell dumpsys notification --noredact | grep -A 8 -B 4 daily_challenge` po jego doręczeniu. Godzina jest przybliżona; Doze może przesunąć termin.
5. Wejdź do wyzwania dnia i rozpocznij podejście rankingowe. Przed alarmem upewnij się, że `lastRankedDaily` odpowiada aktualnemu `Daily.dayNumber()` (UTC). Powiadomienie nie powinno się pojawić.
6. Zrestartuj urządzenie (`adb reboot`), odblokuj je i ponownie sprawdź `dumpsys alarm`. Zmień strefę i godzinę urządzenia, zweryfikuj nowy termin lokalny. Sprawdź brak crasha po cofnięciu `POST_NOTIFICATIONS`:

   ```sh
   adb shell pm revoke com.centurywar.game android.permission.POST_NOTIFICATIONS
   adb shell dumpsys alarm | grep -A 12 -B 5 com.centurywar.game
   ```

7. Zweryfikuj, że po zmianie godziny i przy przejściu DST (czas letni/zimowy) harmonogram pozostaje przypięty do wybranej godziny lokalnej. Testy jednostkowe sprawdzają DST dla `Europe/Brussels`.

## Ograniczenia

- Dokładna pora dostarczenia nie jest gwarantowana (Doze, ograniczenia baterii producenta, force-stop, brak uruchomienia aplikacji po instalacji). Alarm nie przetrwa ręcznego wymuszenia zatrzymania aplikacji, dopóki użytkownik jej nie uruchomi.
- Akcja po dotknięciu powiadomienia otwiera `MainActivity`, nie przenosi bezpośrednio na ekran wyzwania.
