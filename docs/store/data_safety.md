# Google Play Console — Bezpieczeństwo danych (Data safety)

**Stan dokumentacji:** 1 listopada 2026 r. **Źródło danych prawnych wydawcy:** [OWNER_INPUT.md](OWNER_INPUT.md). Deklarację składa się dla rzeczywistego pakietu AAB i usług aktywnych w dniu wydania, nie wyłącznie dla kodu źródłowego.

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
| Lokalna rozgrywka | Pseudonim, postęp, medale, badania, misje, osiągnięcia, lokalne rankingi, statystyki, ustawienia | Wyłącznie prywatna pamięć aplikacji, bez internetu |
| Wznowienie bitwy | Stan przerwanej bitwy | Lokalny plik battle.snap, nieobjęty zapisem chmurowym |
| Raport awarii (zlecenie 65) | Wersja gry, model urządzenia, Android, ekran, maksymalnie 30 zdarzeń i stos wywołań; bez pseudonimu, ID i treści wyjątku | Maks. 10 raportów na urządzeniu; brak automatycznej transmisji i backupu. Użytkownik może świadomie wysłać raport przez systemowy mechanizm udostępniania (ACTION_SEND) do samodzielnie wybranego odbiorcy |
| Play Games — uwierzytelnianie | Google Player ID, nazwa konta, awatar oraz możliwa analityka/diagnostyka SDK | Google, gdy usługa jest skonfigurowana; uwierzytelnienie może być automatyczne |
| Play Games — rankingi | Wyniki liczbowe dla Endless, Daily, Campaign i Skirmish; publiczna nazwa/awatar profilu Google | Google Play Games i gracze, którzy widzą tablice zgodnie z ustawieniami konta |
| Play Games — Saved Games | Kompletny profil JSON: również pseudonim, wpisy rekordów (nazwy, dni, wyniki), postęp, badania, ustawienia i statystyki | Google po uwierzytelnieniu; synchronizacja może być automatyczna |
| Własny ranking (warunkowy) | Losowy wewnętrzny UUID, pseudonim, wynik, tryb, parametry bitwy i komendy powtórki do weryfikacji, znacznik czasu; sekret instalacji wymagany po zleceniu 80 | Serwer wydawcy wyłącznie po konfiguracji HTTPS i zgodzie. Serwer utrzymuje UUID, pseudonim, wynik i metadane; nie zapisuje pełnej powtórki w bazie SQLite. Sekret powinien być przechowywany po stronie serwera jako hash, a nie jawny ciąg |
| Publikacja własnego rankingu (po zleceniu 80) | Publiczny pseudonim, liczbowy wynik, nieodwracalne publiczne ID wpisu | Inni gracze; surowe UUID i sekret nie powinny być zwracane przez publiczne API |
| Zgłoszenie obraźliwego pseudonimu (po zleceniu 80) | Publiczne ID wskazanego wpisu, powód zgłoszenia; możliwy IP zgłaszającego do limitowania ruchu | Serwer wydawcy i upoważniony moderator; zgłoszenie nie jest publiczną wiadomością ani czatem |
| Ochrona serwera | Adres IP w czasie ograniczania nadużyć; możliwe logi reverse proxy | Serwer/proxy wydawcy; zakres i retencja logów wymagają zgodności z faktyczną konfiguracją hostingu |
| Systemowa kopia Androida | Tylko profile.json | Systemowy mechanizm kopii/przenoszenia urządzenia, niezależny od PGS |

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
- Systemowy backup zawiera tylko profile.json; dane battle.snap i files/crash są wyłączone. Kopia Androida i PGS Saved Games podlegają innym zasadom przechowywania.
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
- Backup Androida: https://developer.android.com/identity/data/autobackup
