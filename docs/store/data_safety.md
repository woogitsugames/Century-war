# Google Play Console — formularz „Bezpieczeństwo danych”

**Audyt źródeł:** 31 października 2026 r. **Zakres:** Android, Google Play Games Services (PGS v2) i lokalne przypomnienia o wyzwaniu dnia. To instrukcja do wypełnienia Play Console, nie gotowy formularz do zatwierdzenia bez kontroli wydania.

## Status wdrożenia i istotne zastrzeżenia

- Kod zawiera SDK **com.google.android.gms:play-services-games-v2:22.1.0**, uprawnienie INTERNET oraz obsługę czterech tablic wyników i zapisu w chmurze. SDK inicjuje CenturyWarApp tylko po poprawnym skonfigurowaniu identyfikatorów. **[DO DECYZJI]** W obecnym app/src/main/res/values/games_ids.xml wszystkie identyfikatory to REPLACE_ME: przed udostępnieniem tych funkcji trzeba wprowadzić prawidłowe ID i przetestować wydanie.
- Po skonfigurowaniu PGS v2 **może uwierzytelnić użytkownika automatycznie przy starcie**, zgodnie z mechaniką SDK. MainActivity.onResume sprawdza stan przez refreshSignIn(); po potwierdzonym uwierzytelnieniu aplikacja wysyła rekordy i rozpoczyna synchronizację chmury, **bez dodatkowej zgody na każdy transfer**. Przycisk w ustawieniach umożliwia ręczne logowanie i synchronizację, ale w aplikacji nie ma osobnego wyłącznika synchronizacji ani przycisku wylogowania PGS.
- **Nie deklarować osiągnięć Google Play Games jako działających.** Osiągnięcia lokalne są zapisywane w Profile; Game.onlineAchievements pozostaje OnlineAchievements.NoOp, a MainActivity nie podłącza klienta osiągnięć Google. W SDK istnieje taka funkcjonalność, ale obecny adapter jej nie realizuje.
- Powiadomienia korzystają wyłącznie z Android AlarmManager / NotificationManager (bez FCM i bez serwera push). Prośba o POST_NOTIFICATIONS na Androidzie 13+ pojawia się po pierwszym zwycięstwie, jeśli przypomnienia są włączone. Po restarcie telefonu lub aktualizacji aplikacji alarm jest planowany ponownie.
- Własny backend rankingu w server/ jest **planowany do podłączenia w zleceniu 23**, ale aplikacja nie wysyła do niego wyników. Nie deklarować jeszcze transferów ani polityki retencji własnego serwera.

## Potwierdzone przepływy danych

| Odbiorca / miejsce | Dane | Kiedy i dlaczego |
|---|---|---|
| Prywatna pamięć aplikacji | Plik profile.json: pseudonim, postęp kampanii, gwiazdki, medale, badania, misje, osiągnięcia, statystyki, wyniki i ustawienia | Lokalne zapisywanie rozgrywki; nie wymaga internetu |
| Prywatna pamięć aplikacji | battle.snap: stan przerwanej bitwy | Lokalna możliwość wznowienia; nie jest częścią JSON profilu ani backupu |
| Google Play Games, po konfiguracji i uwierzytelnieniu | Cztery liczbowe wyniki: oblężenie bez końca, wyzwanie dnia, kampania i potyczka | Tablice wyników PGS; w publicznym rankingu tożsamość gracza pochodzi z profilu Play Games |
| Google Play Games — Saved Games, po konfiguracji i uwierzytelnieniu | **Cały wynik Profile.toJson()**, nie tylko liczba medali: w tym pseudonim gracza, rekordy z nazwami, dniami i wynikami, postęp, badania, statystyki, osiągnięcia lokalne, misje i ustawienia (np. język, dźwięk, godzina przypomnienia) | Odczyt i zapis kopii profilu w chmurze po zalogowaniu oraz przy późniejszych zapisach lokalnych |
| Google Play Games SDK / Google | Tożsamość profilu Gier Play (np. identyfikator gracza, nazwa, awatar), dane analityczne i diagnostyczne SDK | Zakres wynikający z dokumentacji dostawcy; może obejmować automatyczne zbieranie po zainicjowaniu SDK |
| Wydawca — własne serwery | **Brak obecnie potwierdzonego transferu z aplikacji** | Nie utożsamiać tego z brakiem zbierania danych przez SDK Google |
| Android — kopia systemowa | Tylko profile.json (przez reguły kopii i przenoszenia urządzeń) | Mechanizm Androida zależny od konta i ustawień; nie jest tym samym co Saved Games PGS |

Przetwarzanie **na urządzeniu** nie jest samo w sobie „zbieraniem” w formularzu Google Play. Natomiast transfer przez osadzony SDK jest zbieraniem również wtedy, gdy wydawca nie otrzymuje tych danych na własny serwer. Pseudonim i identyfikatory pseudonimowe nie stają się anonimowe tylko przez brak nazwiska.

## Proponowane odpowiedzi ogólne — wariant wydania z PGS

| Pytanie Play Console | Odpowiedź robocza i uzasadnienie |
|---|---|
| Czy aplikacja zbiera dane? | **Tak**, gdy skonfigurowany PGS przesyła dane użytkownika i informacje SDK do Google. Nie odpowiadać „Nie zbiera” tylko dlatego, że wydawca nie prowadzi własnego serwera. |
| Czy aplikacja udostępnia dane innym podmiotom? | **[DO DECYZJI]** Ustalić, czy Google jest dostawcą usługi działającym wyłącznie na rzecz wydawcy, czy odrębnym odbiorcą realizującym własne cele. Sam transfer do SDK oznacza zbieranie, ale kwalifikacja „udostępnianie” zależy od definicji i wyjątków Google Play; dokumentacja PGS mówi także o udostępnianiu tożsamości i widoczności profilu innym graczom. Nie zaznaczać automatycznie „Nie”. |
| Czy wszystkie przesyłane dane są szyfrowane w tranzycie? | **Tak dla danych PGS wymienionych w dokumentacji Google** (HTTPS). **[DO DECYZJI]** Potwierdzić po audycie pełnego grafu zależności i końcowego AAB, że nie ma innych transferów bez szyfrowania. |
| Czy użytkownik może poprosić o usunięcie danych? | Google udostępnia usuwanie danych **konkretnej gry** w profilu Gier Play. **[DO DECYZJI]** Odpowiedź o mechanizmie żądania usunięcia u wydawcy zależy od opublikowania czynnego kontaktu i przyjętej procedury. Reset postępu w grze nie usuwa kopii PGS. |
| Czy zbieranie jest opcjonalne? | **[DO DECYZJI]** PGS można nie używać do rozgrywki, a kontem zarządza użytkownik w usługach Google, lecz SDK v2 może automatycznie logować i zbierać własne dane diagnostyczno-analityczne po inicjalizacji. Nie deklarować wszystkich kategorii jako „opcjonalne”. |
| Czy dane są wykorzystywane wyłącznie do działania aplikacji? | Wyniki i zapis gry: funkcjonalność aplikacji. Dane analityczne i diagnostyczne **zbierane przez SDK** służą też poprawie działania usług Google; opisać je osobno. |
| Tworzenie konta | Brak własnej rejestracji i konta wydawcy; **konto/profil Google Play Games** może być używane do usług opcjonalnych dla rozgrywki. |
| Reklamy i zakupy | Brak SDK reklamowego i Play Billing zadeklarowanego w plikach modułu aplikacji. **[DO DECYZJI]** Potwierdzić końcowy pakiet i opublikowany wariant przed wysłaniem deklaracji. |

## Kategorie danych Play Console — karta robocza

„Tak” w poniższej tabeli dotyczy **wydania z poprawnie skonfigurowanym PGS**. Kolumna „Szyfr.” opisuje potwierdzony transport PGS, a nie automatyczne zapewnienie dotyczące nieprzejrzanych SDK. „Usunięcie: Google” oznacza usuwanie danych gry w ustawieniach profilu Play Games, nie usunięcie lokalnego profilu.

| Kategoria / typ | Zbieranie i uzasadnienie | Cel | Wymagane czy opcjonalne? | Szyfr. | Usunięcie |
|---|---|---|---|---|---|
| **Dane osobowe → Identyfikatory użytkowników (User IDs)** | **Tak, PGS**: konto gracza posiada identyfikator Player ID używany w usłudze | Funkcjonalność aplikacji / obsługa konta PGS | **[DO DECYZJI]** Automatyczne uwierzytelnianie | PGS: HTTPS | Google |
| **Dane osobowe → Imię/nazwa (Name)** | **Tak, PGS**: nazwa gracza Play Games; pełny zapis w chmurze zawiera także lokalny, samodzielnie wybrany pseudonim | Funkcjonalność, widoczność rankingu i synchronizacja | **[DO DECYZJI]** Zależne od stanu PGS i zasad SDK | PGS: HTTPS | Google |
| **Zdjęcia i filmy → Zdjęcia** | **[DO DECYZJI]** Google opisuje awatar konta jako Game Service identity, ale kod Century War nie wysyła zdjęć ani awatara; ustalić klasyfikację danych obsługiwanych przez sam SDK | Konto PGS | **[DO DECYZJI]** | PGS: HTTPS | Google |
| **Aktywność w aplikacji → Inne działania (Other actions)** | **Tak, PGS**: wyniki, postęp, rekordy i zdarzenia gry zapisane w JSON oraz w tablicach | Funkcjonalność / rankingi / zapis chmurowy | **[DO DECYZJI]** Zależne od uwierzytelnienia, ale logowanie bywa automatyczne | PGS: HTTPS | Google |
| **Aktywność w aplikacji → Inne treści tworzone przez użytkownika** | **[DO DECYZJI]** Pseudonim i rekordy są w chmurze; ocenić, czy pseudonim należy tylko do „Name”, czy również do „Other user-generated content” (nie dublować bez powodu) | Zapis w chmurze | **[DO DECYZJI]** | PGS: HTTPS | Google |
| **Aktywność w aplikacji → Interakcje z aplikacją (App interactions)** | **[DO DECYZJI]** SDK deklaruje automatyczne dane analityczne, ale nie rozpisuje zdarzeń na tę kategorię; sprawdzić dokumentację dokładnej wersji | Analityka / ulepszanie SDK | **[DO DECYZJI]** Może dotyczyć użytkowników SDK niezależnie od świadomej synchronizacji | PGS: HTTPS | Google — zgodnie z zasadami usługi |
| **Informacje o aplikacji i jej działaniu → Diagnostyka (Diagnostics)** | **Tak według dokumentacji PGS**: SDK automatycznie zbiera dane diagnostyczne | Analityka / stabilność i ulepszanie SDK | **[DO DECYZJI]** Zweryfikować, czy jest możliwe wyłączenie; nie zakładać opcjonalności | PGS: HTTPS | Google — zgodnie z zasadami usługi |
| **Informacje o aplikacji i jej działaniu → Dzienniki awarii (Crash logs)** | **[DO DECYZJI]** Google potwierdza diagnostykę, ale nie wskazuje jednoznacznie osobnego rodzaju Crash logs dla użytej wersji | Diagnostyka, jeśli występuje | **[DO DECYZJI]** | PGS: HTTPS, jeśli zbierane | Google |
| **Identyfikatory urządzenia lub inne (Device or other IDs)** | **[DO DECYZJI]** Player ID jest przede wszystkim identyfikatorem użytkownika PGS; nie zakładać automatycznie, że to identyfikator urządzenia. Zbadać zależności transytywne i manifest release pod kątem takich ID, w tym AD_ID | Zależnie od faktycznego użycia | **[DO DECYZJI]** | **[DO DECYZJI]** | Zależnie od dostawcy |
| **Lokalizacja, kontakty, wiadomości, nagrania, płatności** | **Brak transferów zadeklarowanych przez kod funkcji gry**; nie ma takich żądań uprawnień własnych. **[DO DECYZJI]** Ostateczną deklarację oprzeć na artefakcie release i danych SDK | Nie dotyczy, jeśli brak | Nie dotyczy | Nie dotyczy | Nie dotyczy |

W dokumentacji PGS zbieranie **Gamertag, avatara, analityki i diagnostyki** jest opisane jako automatyczne, a dane wyników i zapisu gry zależą od użytych API. Google podkreśla, że opis dotyczy **najnowszej wersji SDK**, niekoniecznie przypiętej tutaj wersji 22.1.0. **[DO DECYZJI]** Przed publikacją sprawdzić SDK Index oraz oświadczenia dokładnej wersji i ostateczny sposób udostępniania danych w Play Console.

## Powiadomienia i uprawnienia

| Uprawnienie w manifeście źródłowym | Cel |
|---|---|
| VIBRATE | Lokalne efekty dotykowe gry |
| INTERNET | Połączenie z Google Play Games, jeżeli skonfigurowano usługę |
| POST_NOTIFICATIONS | Pokazywanie lokalnych przypomnień na Androidzie 13+ za zgodą systemową |
| RECEIVE_BOOT_COMPLETED | Przywrócenie harmonogramu lokalnych przypomnień po restarcie |

Nie korzystamy z Firebase Cloud Messaging. Sam alarm i wyświetlenie przypomnienia nie przekazują pseudonimu ani wyników na serwer. Android może wykonywać odrębny backup profile.json: android:allowBackup="true", backup_rules.xml i data_extraction_rules.xml; battle.snap jest pomijany.

## Otwarte punkty przed publikacją

1. **[DO DECYZJI] Uzupełnić ID PGS** (w tej gałęzi pozostają REPLACE_ME), skonfigurować tablice i Saved Games w Play Console, wykonać próbę na urządzeniu; zdecydować, czy osiągnięcia Google będą wdrożone osobno, czy nie będą reklamowane.
2. **[DO DECYZJI] Zweryfikować zależności transytywne i manifest release** poleceniami: ./gradlew :app:dependencies --configuration releaseRuntimeClasspath oraz ./gradlew :app:processReleaseMainManifest, a następnie sprawdzić app/build/intermediates/merged_manifests/ (m.in. AD_ID, ACCESS_NETWORK_STATE, WAKE_LOCK). Nie utożsamiać braku AD_ID w źródłowym manifeście z brakiem w całym AAB.
3. **[DO DECYZJI] Uzupełnić i udostępnić politykę prywatności** po polsku i angielsku, określić pełną nazwę prawną wydawcy i działający e-mail, ustalić procedurę próśb o usunięcie danych.
4. **[DO DECYZJI] Dodać link do polityki prywatności wewnątrz aplikacji.** SettingsScreen.kt nie ma takiego przycisku/odnośnika; zakres tego zlecenia nie obejmuje zmian kodu.
5. **[DO DECYZJI] Potwierdzić deklarację „udostępniania”, opcjonalności danych diagnostycznych i klasyfikację identyfikatorów** na podstawie polityk SDK, sposobu uwierzytelnienia i wszystkich aktywnych kanałów publikacji.
6. Po włączeniu własnego backendu (zlecenie 23) przeprowadzić nowy audyt: identyfikator gracza/pseudonim, wyniki, czas, adresy IP w logach, transport TLS, retencja, możliwość usunięcia i ewentualna weryfikacja powtórek. Serwera **nie przedstawiać teraz jako czynnego**.
7. Powtórzyć audyt przed uruchomieniem reklam, zakupów, dodatkowej analityki lub innego SDK; nie deklarować takich funkcji w obecnej wersji.

**Uwaga o usuwaniu:** Profile.reset() zachowuje pseudonim i nie usuwa danych Google. Wyczyść pamięć aplikacji, aby usunąć dane lokalne; Google Play Games wymaga osobnego usunięcia danych gry z profilu Google. Android Backup pozostaje odrębny.

## Źródła oficjalne

- PGS — deklarowanie danych SDK: https://developer.android.com/games/pgs/data-collection
- PGS — automatyczne uwierzytelnianie: https://developer.android.com/games/pgs/platform-authentication
- Play Console — definicje typów, udostępniania i opcjonalności: https://support.google.com/googleplay/android-developer/answer/10787469
- Usuwanie danych pojedynczej gry z Play Games: https://support.google.com/googleplay/answer/9130646?hl=pl
- Polityka prywatności Google: https://policies.google.com/privacy
- Backup Androida: https://developer.android.com/identity/data/autobackup
