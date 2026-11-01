# Google Play Console — Bezpieczeństwo danych (Data safety)

**Stan dokumentacji:** 1 listopada 2026 r. **Źródło danych prawnych wydawcy:** [OWNER_INPUT.md](OWNER_INPUT.md). Deklarację składa się dla rzeczywistego pakietu AAB i usług aktywnych w dniu wydania, nie wyłącznie dla kodu źródłowego.
**Audyt źródeł:** 1 listopada 2026 r. **Zakres:** Android, Google Play Games Services (PGS v2) i lokalne przypomnienia o wyzwaniu dnia. To instrukcja do wypełnienia Play Console, nie gotowy formularz do zatwierdzenia bez kontroli wydania.

## Podsumowanie odpowiedzi do Google Play

- **Czy aplikacja zbiera dane? Tak.** Po skonfigurowaniu Google Play Games Services (PGS v2) konto, wyniki i kompletna kopia profilu mogą być przesyłane do Google; SDK może automatycznie przetwarzać tożsamość, dane analityczne i diagnostyczne. Aktywowany własny ranking dodatkowo zbiera pseudonim, identyfikator, wynik i dowód jego poprawności.
- **Czy dane mogą być publicznie widoczne lub udostępniane? Tak.** Rankingi PGS mogą pokazywać nazwę/awatar profilu Google i wyniki. Ranking wydawcy, jeśli zostanie uruchomiony, pokazuje wybrany pseudonim, wynik i nieodwracalny identyfikator publiczny (po wdrożeniu zlecenia 80). Nie zakładaj, że brak czatu oznacza brak treści użytkowników. W pytaniu o „sharing” zweryfikuj wyjątki dla procesorów danych Google, ale nie pomijaj publicznej widoczności danych.
- **Szyfrowanie podczas przesyłania:** dane PGS przez HTTPS; własny serwer rankingu może być aktywny tylko za HTTPS. Ręczne udostępnienie raportu błędu używa aplikacji wybranej przez użytkownika, więc jej kanał transmisji zależy od odbiorcy.
- **Usuwanie:** lokalne dane — wyczyszczenie pamięci aplikacji; PGS — narzędzia konta Google; ranking wydawcy — „Nowa tożsamość” i ponawiane żądanie usunięcia starego UUID. Zmiana UUID nie jest jeszcze potwierdzeniem usunięcia z serwera. Kontakt i adres polityki pochodzą z OWNER_INPUT.md.
- **Czy wszystkie dane są opcjonalne? Nie deklaruj „tak”.** Rozgrywka działa bez logowania, ale skonfigurowany PGS może uwierzytelnić użytkownika automatycznie, a SDK może zbierać dane techniczne bez osobnego potwierdzenia. Własny ranking wymaga osobnej zgody w aplikacji.
- **Konto:** nie istnieje konto tworzone u wydawcy; kontem PGS zarządza Google. **Reklamy / zakupy:** w opisywanej wersji brak SDK reklamowego i Play Billing.

## Wykaz przepływów i odbiorców

| Funkcja | Dane | Odbiorca i podstawa działania |
|---|---|---|
| Prywatna pamięć aplikacji | Plik profile.json: pseudonim, postęp, wyniki, ustawienia oraz po zgodzie na własny ranking UUID i losowy 32-bajtowy sekret instalacji | Lokalne zapisywanie gry oraz poświadczenie własności konta rankingu; nie wymaga internetu |
| Prywatna pamięć aplikacji | battle.snap: stan przerwanej bitwy | Lokalna możliwość wznowienia; nie jest częścią JSON profilu ani kopii Androida |
| Prywatna pamięć aplikacji | files/crash: maks. 10 raportów (wersja, model urządzenia, Android, ekran, 30 zdarzeń technicznych, stos bez wiadomości wyjątków) | Diagnoza awarii; bez pseudonimu i ID, tylko lokalnie; bez automatycznej transmisji lub backupu |
| Google Play Games, po konfiguracji i uwierzytelnieniu | Cztery liczbowe wyniki: oblężenie bez końca, wyzwanie dnia, kampania i potyczka | Tablice wyników PGS; w publicznym rankingu tożsamość gracza pochodzi z profilu Play Games |
| Google Play Games — Saved Games, po konfiguracji i uwierzytelnieniu | **Profile.toCloudJson()**: pseudonim gracza, rekordy, postęp, badania, statystyki, osiągnięcia, misje i ustawienia; **bez lokalnego UUID, sekretu instalacji i zgody na ranking wydawcy** | Odczyt i zapis kopii profilu w chmurze po zalogowaniu oraz przy późniejszych zapisach lokalnych |
| Google Play Games SDK / Google | Tożsamość profilu Gier Play (np. identyfikator gracza, nazwa, awatar), dane analityczne i diagnostyczne SDK | Zakres wynikający z dokumentacji dostawcy; może obejmować automatyczne zbieranie po zainicjowaniu SDK |
| Wydawca — opcjonalny własny serwer rankingu | UUID v4, pseudonim, wynik, tryb, parametry bitwy, badania, komendy powtórki, sekret instalacji przesyłany przez HTTPS | Po zgodzie: SQLite zapisuje UUID, pseudonim, publiczne ID (HMAC UUID z prywatnym kluczem serwera), wynik, SHA-256 sekretu i metadane; **nie** zapisuje jawnego sekretu, powtórki ani IP. Publiczne API pokazuje pseudonim, wynik i publiczne ID, nie UUID. IP służy tymczasowo do limiterów i może być w logach proxy. |
| Wydawca — zgłoszenia nicków | Publiczne ID zgłaszanego gracza, powód, nick w chwili zgłoszenia, UUID tego gracza i czas; IP zgłaszającego do limitowania | Na żądanie użytkownika z ekranu rankingu, bez konta zgłaszającego. SQLite przechowuje zgłoszenie dla operatora, bez IP; IP może być w logach proxy. |
| Android — kopia systemowa | Tylko profile.json (z lokalnym UUID i sekretem rankingu, jeśli gracz wyraził zgodę) i history.json; bez score-queue*, files/crash, battle.snap, powtórek | Przeniesienie postępu na nowy telefon tego samego konta Google; kopia w chmurze tylko z szyfrowaniem end-to-end (blokada ekranu). Mechanizm Androida zależny od konta i ustawień; nie jest tym samym co Saved Games PGS |

Zlecenie 80 zmienia protokół tożsamości, publiczne identyfikatory i możliwość zgłaszania nicków. Powyższe informacje opisują **planowany wariant po scaleniu i wdrożeniu zlecenia 80**, a nie deklarację, że wszystkie funkcje są już czynne na gałęzi bazowej. Przed wydaniem należy porównać tabelę z wynikiem testów klienta i serwera oraz dostępnymi ekranami rankingu.

## Kategorie w formularzu Play Console

Przy działającym PGS stosuj następującą konserwatywną klasyfikację. „Warunkowo” oznacza, że odpowiedź zależy od aktywnego backendu lub konkretnej wersji SDK i wymaga sprawdzenia końcowego pakietu. **Nie oznacza to zgody na opublikowanie niezweryfikowanego formularza.**

| Kategoria Google Play | Zbieranie / udostępnianie | Cel | Wymagalność, transmisja i usunięcie |
|---|---|---|---|
| Dane osobowe → **Identyfikatory użytkownika** | Tak: identyfikator Google Player ID, a przy własnym serwerze UUID | Logowanie PGS, rankingi, uwierzytelnianie wpisów i usuwanie | PGS może automatycznie logować; własny ranking dobrowolny. HTTPS. PGS: Google; UUID: żądanie usunięcia na własnym serwerze |
| Dane osobowe → **Imię / nazwa** | Tak: pseudonim profilu Google i pseudonim użytkownika; własny nick może być **publiczny** | Profil PGS, rankingi, kopia zapisu, własny ranking | Widoczność zależy od usługi i ustawień. HTTPS. Usuwanie oddzielnie w PGS i własnym rankingu |
| Aktywność w aplikacji → **Inne treści generowane przez użytkownika** | Tak: pseudonimy i — po zadaniu 80 — powody zgłoszeń nicków; nie ma czatu | Udział w rankingu, moderacja obraźliwych nazw | Własne zgłoszenia tylko po aktywacji funkcji; HTTPS. Obsługa wniosku przez operatora |
| Aktywność w aplikacji → **Inne działania** | Tak: wyniki, postęp, zapis profilu; dla własnego rankingu także metadane zweryfikowanej bitwy | Rankingi, synchronizacja stanu gry, przeciwdziałanie fałszowaniu wyników | Przez Google albo dobrowolnie do serwera, HTTPS; usuwanie według miejsca przechowywania |
| Aktywność w aplikacji → **Interakcje z aplikacją** | Co najmniej możliwe po stronie PGS SDK; zakwalifikuj zgodnie z deklaracją dla przypiętej wersji 22.1.0 | Analityka i stabilność usług Google | Automatyczne dane SDK nie muszą być opcjonalne; według polityki Google |
| Informacje o aplikacji i jej działaniu → **Diagnostyka** | Tak według informacji SDK PGS; raporty błędów aplikacji są wyłącznie lokalne aż do ręcznego udostępnienia | Diagnostyka usług Google / dobrowolne zgłoszenia | PGS: według Google; ręczny raport: kanał wybranego odbiorcy |
| Informacje o aplikacji i jej działaniu → **Dzienniki awarii** | Lokalnie: tak; przez wydawcę: wyłącznie jeśli gracz sam prześle raport. Sprawdź, czy SDK PGS niezależnie przekazuje crash logs | Diagnostyka i wsparcie | Ręcznie opcjonalne; PGS według jego deklaracji i ustawień |
| Zdjęcia i filmy → **Zdjęcia** | Aplikacja nie pobiera zdjęć z telefonu; PGS może przetwarzać awatar konta Google w ramach tożsamości gracza | Wyświetlanie profilu PGS | Nie deklaruj zdjęć przesyłanych *przez grę*, jeśli SDK ich nie zbiera w tej kategorii. Zweryfikuj deklarację dostawcy |
| Identyfikatory urządzenia lub inne | Możliwe po stronie PGS / infrastruktury (np. dane techniczne, IP); sam UUID własnego serwera jest identyfikatorem gracza | Usługi Google, bezpieczeństwo / rate limiting | Potwierdź klasyfikację identyfikatorów SDK i logów w końcowym AAB |
| Lokalizacja, kontakty, mikrofon, wiadomości prywatne, dane płatnicze | Brak funkcji gry i własnych uprawnień przekazujących takie dane | Nie dotyczy | Sprawdź scalony manifest i zależności, nie wyciągaj wniosku wyłącznie z manifestu źródłowego |

## Powiadomienia, raporty i przechowywanie

- Lokalny AlarmManager/NotificationManager planuje przypomnienie o wyzwaniu dnia; **brak FCM i serwera push**. Uprawnienia: INTERNET (PGS i własny HTTPS), POST_NOTIFICATIONS (Android 13+), RECEIVE_BOOT_COMPLETED (odtworzenie alarmu), VIBRATE (reakcje dotykowe).
- Samo zapisanie raportu w files/crash nie jest zbieraniem przez wydawcę. Dopiero świadomy wybór aplikacji odbiorczej i wysłanie wiadomości może przekazać raport wybranemu podmiotowi.
- Systemowy backup zawiera tylko profile.json i history.json (android:allowBackup="true" z regułami include); battle.snap, files/crash, kolejka wyników score-queue*, powtórki i kopie sprzed importu są wyłączone. Kopia w chmurze wymaga szyfrowania end-to-end. Kopia Androida i PGS Saved Games podlegają innym zasadom przechowywania. W Data safety kopia systemowa Google nie jest „udostępnianiem” ani „zbieraniem” przez dewelopera (szyfrowana, zarządzana przez Androida).
- Baza własnego rankingu: zgodnie z istniejącym kodem rekordy nie wygasają automatycznie, a kasowanie jest inicjowane przez użytkownika. Logi IP, zgłoszenia obraźliwych nicków, ewentualne kopie zapasowe i ich retencję trzeba porównać z wdrożonym serwerem i polityką przed pierwszym uruchomieniem tej funkcji.
- Dla PGS usuwanie danych tej konkretnej gry odbywa się niezależnie w ustawieniach Play Games. Reset profilu lokalnego nie usuwa kopii w PGS ani danych własnego serwera.

## Kontrola przed wysłaniem formularza

1. Uzupełnij dane wydawcy w [OWNER_INPUT.md](OWNER_INPUT.md), opublikuj wyrenderowaną politykę PL/EN pod publicznym HTTPS i ustaw ten sam adres w konfiguracji wydania.
2. Skonfiguruj identyfikatory projektu Play Games i czterech tablic; sprawdź rzeczywiste logowanie, tablice i Saved Games na urządzeniu. Nie reklamuj osiągnięć PGS jako aktywnych, dopóki adapter osiągnięć nie jest połączony.
3. Uruchom kontrolę zależności transytywnych i scalonego manifestu release; szczególnie zweryfikuj AD_ID, ACCESS_NETWORK_STATE, WAKE_LOCK oraz inne uprawnienia dodane przez SDK.
4. Porównaj deklaracje konkretnej wersji PGS SDK 22.1.0 z publikowanym pakietem i aktualnym formularzem Google Play. Dla aktywnego SDK uwzględnij automatyczne dane tożsamości, analityki i diagnostyki nawet bez własnego backendu.
5. Jeśli integrujesz zlecenie 80, przetestuj sekret instalacji, maskowanie UUID, możliwość zgłoszenia nicka, retencję zgłoszeń, moderację, limity IP i usuwanie wpisów. Uaktualnij politykę po weryfikacji konfiguracji serwera.
6. Sprawdź, że aplikacja zawiera osiągalny link do publicznej polityki prywatności (w szczególności w Ustawieniach) oraz dostępny sposób kontaktu i usuwania danych.
7. Formularz zatwierdza wydawca na podstawie **konkretnego wydawanego AAB**, nie samego opisu w repozytorium. Powtórz deklaracje po wprowadzeniu reklam, zakupów, nowych SDK, serwera rankingu lub innych transferów.

## Oficjalne źródła

- Google PGS — zakres danych SDK: https://developer.android.com/games/pgs/data-collection
- Google PGS — uwierzytelnianie: https://developer.android.com/games/pgs/platform-authentication
- Play Console — definicje Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Play Games — usuwanie danych gry: https://support.google.com/googleplay/answer/9130646?hl=pl
| Uprawnienie w manifeście źródłowym | Cel |
|---|---|
| VIBRATE | Lokalne efekty dotykowe gry |
| INTERNET | Połączenie z Google Play Games, jeżeli skonfigurowano usługę |
| POST_NOTIFICATIONS | Pokazywanie lokalnych przypomnień na Androidzie 13+ za zgodą systemową |
| RECEIVE_BOOT_COMPLETED | Przywrócenie harmonogramu lokalnych przypomnień po restarcie |

Nie korzystamy z Firebase Cloud Messaging. Sam alarm i wyświetlenie przypomnienia nie przekazują pseudonimu ani wyników na serwer. Android może wykonywać odrębny backup profile.json i history.json: android:allowBackup="true", backup_rules.xml i data_extraction_rules.xml (tylko include); battle.snap, score-queue*, files/crash i powtórki są pomijane.

## Otwarte punkty przed publikacją

1. **[DO DECYZJI] Uzupełnić ID PGS** (w tej gałęzi pozostają REPLACE_ME), skonfigurować tablice i Saved Games w Play Console, wykonać próbę na urządzeniu; zdecydować, czy osiągnięcia Google będą wdrożone osobno, czy nie będą reklamowane.
2. **[DO DECYZJI] Zweryfikować zależności transytywne i manifest release** poleceniami: ./gradlew :app:dependencies --configuration releaseRuntimeClasspath oraz ./gradlew :app:processReleaseMainManifest, a następnie sprawdzić app/build/intermediates/merged_manifests/ (m.in. AD_ID, ACCESS_NETWORK_STATE, WAKE_LOCK). Nie utożsamiać braku AD_ID w źródłowym manifeście z brakiem w całym AAB.
3. **[DO DECYZJI] Uzupełnić i udostępnić politykę prywatności** po polsku i angielsku, określić pełną nazwę prawną wydawcy i działający e-mail, ustalić procedurę próśb o usunięcie danych.
4. **[ZAMKNIĘTE W KODZIE — zlecenie 93] Link i usuwanie danych w aplikacji.** Ustawienia → O grze zawierają zawsze przycisk „Polityka prywatności”: przy publicznym adresie HTTPS z `CW_PRIVACY_URL` (release: `BuildConfig.PRIVACY_URL`) otwiera politykę przez `SecureLinks`, a bez adresu (debug) pokazuje krótki tekst offline w 10 językach. „Usuń moje dane z rankingu” wymaga ponownego potwierdzenia; korzysta z istniejącego `ScoreClient.deletePlayerData` i trwałej kolejki offline. Komunikat oznacza zapisanie żądania, **nie** potwierdzenie usunięcia na serwerze. Po zapisie ranking wydawcy zostaje wyłączony, a identyfikator odnowiony. Dane Play Games i Android usuwa się osobno. **Wydanie nadal zablokowane**, dopóki właściciel nie uzupełni `OWNER_INPUT.md`, nie opublikuje pełnej polityki pod HTTPS i nie zweryfikuje adresu na wydanym AAB.
5. **[DO DECYZJI] Potwierdzić deklarację „udostępniania”, opcjonalności danych diagnostycznych i klasyfikację identyfikatorów** na podstawie polityk SDK, sposobu uwierzytelnienia i wszystkich aktywnych kanałów publikacji.
6. Przed uruchomieniem własnego rankingu w wydaniu sprawdzić aktywną konfigurację, HTTPS, UUID, pseudonim, wyniki, IP w logach proxy, retencję backupów i ponawianie usunięć po ich odtworzeniu. Serwera **nie przedstawiać jako czynnego bez weryfikacji wdrożenia**.
7. Powtórzyć audyt przed uruchomieniem reklam, zakupów, dodatkowej analityki lub innego SDK; nie deklarować takich funkcji w obecnej wersji.

**Uwaga o usuwaniu:** przy usunięciu własnego UUID serwer kasuje także zgłoszenia powiązane z tym UUID; zachowuje rekord blokady, aby nie obchodzić moderacji. Sekret instalacji w kopii Androida (profile.json) nie jest wysyłany do Google Play Games Saved Games. Zgłoszenia dotyczące innych osób nie są łączone z UUID zgłaszającego.

**Uwaga o usuwaniu:** Profile.reset() zachowuje pseudonim i nie usuwa danych Google ani własnego rankingu. Własny ranking: **Ustawienia → Nowa tożsamość**, trwała kolejka (bez gwarancji usunięcia przed utratą plików aplikacji). PGS: osobne usunięcie danych gry z konta Google. Lokalne dane: wyczyszczenie pamięci aplikacji. Android Backup pozostaje odrębny. Przy odtworzeniu kopii SQLite operator musi ponowić późniejsze usunięcia.

## Źródła oficjalne

- PGS — deklarowanie danych SDK: https://developer.android.com/games/pgs/data-collection
- PGS — automatyczne uwierzytelnianie: https://developer.android.com/games/pgs/platform-authentication
- Play Console — definicje typów, udostępniania i opcjonalności: https://support.google.com/googleplay/android-developer/answer/10787469
- Usuwanie danych pojedynczej gry z Play Games: https://support.google.com/googleplay/answer/9130646?hl=pl
- Polityka prywatności Google: https://policies.google.com/privacy
- Backup Androida: https://developer.android.com/identity/data/autobackup
