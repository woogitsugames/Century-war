# Google Play Console — Bezpieczeństwo danych (Data safety)

**Przegląd: 2 listopada 2026 r.** Dokument jest instrukcją dla wydawcy, **nie** gotową deklaracją do wysłania. Odpowiedzi trzeba zatwierdzić na podstawie podpisanego AAB, scalonego manifestu, użytych wersji SDK i rzeczywiście uruchomionych usług. Dane wydawcy są w [OWNER_INPUT.md](OWNER_INPUT.md); adres serwera jest konfigurowany osobno.

## 1. Najważniejsze odpowiedzi

- **Czy gra zbiera dane poza urządzeniem? Tak, gdy używane są usługi sieciowe.** Play Games Services (PGS v2) może uwierzytelniać gracza automatycznie i wysyłać wyniki, **odblokowane osiągnięcia** oraz profil Saved Games po zalogowaniu. SDK zbiera również własne dane analityczne i diagnostyczne. Dane własnego serwera są wysyłane tylko po zgodzie gracza i przy poprawnej konfiguracji.
- **Czy wszystkie dane są opcjonalne? Nie.** Podstawowa rozgrywka działa offline, lecz skonfigurowane PGS może logować automatycznie, a dane techniczne SDK nie wymagają oddzielnego kliknięcia „wyślij”. Opcjonalność ocenia się **dla każdego typu danych** zgodnie z formularzem Google.
- **Czy dane mogą być publiczne? Tak.** Google może wyświetlać nazwę/awatar profilu PGS, wyniki i osiągnięcia zależnie od ustawień widoczności. Własny ranking (jeżeli zostanie uruchomiony) pokazuje pseudonim gracza, wynik i publiczne ID, bez surowego UUID.
- **Udostępnianie według definicji Play Console:** nie utożsamiaj publicznej widoczności z odpowiedzią na pole „sharing”. Dla każdej transmisji sprawdź wyjątki Google (m.in. usługodawca przetwarzający na polecenie wydawcy i świadome działanie użytkownika). Przetwarzanie SDK Google nie znika z zakresu **zbierania** tylko dlatego, że nie trafia na własny serwer.
- **Szyfrowanie transmisji:** PGS używa HTTPS; `ScoreClient` akceptuje wyłącznie `https://`. Ręczne udostępnienie raportu błędu otwiera aplikację wybraną przez gracza i nie stanowi automatycznej transmisji z gry. Sprawdź odpowiedź na pytanie o szyfrowanie dla całego finalnego AAB.
- **Konta i płatności:** gra nie tworzy kont wydawcy. Konto Play Games należy do Google. W obecnym kodzie brak reklam, Play Billing, Firebase Cloud Messaging i **własnego SDK analitycznego**. Nie oznacza to braku analityki/diagnostyki *wewnątrz SDK PGS*.

## 2. Zweryfikowane przepływy danych

| Kanał | Zawartość | Kiedy i do kogo |
|---|---|---|
| Lokalny profil (`profile.json`) | Pseudonim, postęp, badania, medale, statystyki, osiągnięcia, ustawienia; po zgodzie na własny ranking także UUID v4, 32-bajtowy sekret instalacji i zgoda | Lokalnie bez sieci; osobno może podlegać systemowej kopii Androida |
| Historia (`history.json`) i zapis bitwy (`battle.snap`) | Historia wyników bitew i możliwość wznowienia przerwanej bitwy | Lokalnie; **tylko history.json** jest objęty regułami kopii Androida |
| Lokalne raporty (`files/crash`) | Do 10 raportów: urządzenie, Android, wersja gry, ekran, ostatnie 30 zdarzeń technicznych i stos wywołań bez wiadomości wyjątków | Brak automatycznej transmisji lub backupu; tylko ręczne udostępnienie z systemowego okna |
| PGS — rankingi | Cztery wyniki liczbowe: Endless, wyzwanie dnia, kampania, potyczka | Po konfiguracji PGS i uwierzytelnieniu, do Google; zgłoszenia nowych i zapisanych wyników |
| **PGS — osiągnięcia** | Identyfikatory odblokowanych osiągnięć | **Po konfiguracji PGS i uwierzytelnieniu, do Google**; `PlayGamesLeaderboards.unlock()` / `flushAchievements()` i `Achievements.syncUnlocked()` po ponownym zalogowaniu |
| PGS — Saved Games | `Profile.toCloudJson()`: pseudonim, postęp, rekordy z nazwami/danymi czasu, badania, statystyki, misje, osiągnięcia, ustawienia | Do Google po uwierzytelnieniu i przy synchronizacji; **bez** lokalnego UUID, sekretu i zgody na własny ranking, **bez** `history.json` |
| PGS SDK 22.1.0 | Dane konta gracza (identyfikator, gamertag, awatar) oraz analityka i diagnostyka SDK | Google; dane automatyczne i zależne od użytych funkcji według dokumentacji PGS |
| Własny serwer rankingu (`ScoreClient`) | UUID v4, pseudonim, tablica, wynik, czas, seed, trudność, poziomy badań, komendy powtórki do weryfikacji; sekret instalacji w nagłówku autoryzacji | **Tylko po osobnej zgodzie** na ranking i przy skonfigurowanych HTTPS/HMAC; serwer przechowuje m.in. UUID, pseudonim, wynik, publiczne ID (HMAC UUID), SHA-256 sekretu i metadane, ale nie jawny sekret ani całą powtórkę w SQLite |
| Własny serwer — zgłoszenie nicku | Publiczne ID zgłaszanego, powód „offensive”, a w bazie również UUID i nick zgłaszanego, czas | Na żądanie gracza, gdy ranking działa; IP do limitowania zgłoszeń i potencjalnie w logach proxy; nie w tabeli zgłoszeń SQLite |
| Android Auto Backup / transfer urządzeń | **`profile.json` i `history.json`** (a więc także ewentualny sekret rankingu z profilu) | Mechanizm systemowy Google/Android; osobny od PGS Saved Games; warunki i ochrona różnią się między wersjami Androida |

**Ważne:** kod klienta i serwera nie dowodzi, że własny serwer jest wdrożony. Przed deklarowaniem aktywnych transferów wydawca musi sprawdzić `CW_SERVER_URL`, `CW_HMAC_KEY`, zgodę w aplikacji, faktyczny adres HTTPS i retencję w środowisku produkcyjnym. SQLite nie zapisuje IP, ale infrastruktura sieciowa może je logować.

## 3. Systemowa kopia Androida — nie deklaruj bezwarunkowego E2E

`AndroidManifest.xml` ustawia `android:allowBackup="true"`. W `res/xml/backup_rules.xml` i `res/xml/data_extraction_rules.xml` jedynymi plikami objętymi regułami są **`profile.json` i `history.json`**. Kopia nie obejmuje `battle.snap`, `score-queue*`, `files/crash`, `replays/` ani lokalnych kopii profilu. Transfer między urządzeniami również obejmuje te dwa pliki.

| System | Warunek w rzeczywistych regułach | Co wolno deklarować |
|---|---|---|
| **Android 12+ (API 31+)** | `cloud-backup disableIfNoEncryptionCapabilities="true"`; oddzielne `device-transfer` | Chmurowa kopia wymaga możliwości szyfrowania po stronie klienta. Transfer bezpośredni ma osobne reguły; nie utożsamiaj go z backupem E2E |
| **Android 9–11 (API 28–30)** | Oba `include` mają `requireFlags="clientSideEncryption"` | Backup w chmurze przy spełnionym warunku szyfrowania po stronie klienta (Android 9+ z odpowiednio skonfigurowaną blokadą ekranu). Bez warunku kopia może nie zostać wykonana |
| **Android 8/8.1 (API 26–27)** | Ten sam starszy XML, ale platforma nie zapewnia funkcji E2E z Androida 9 | **Nie gwarantuj E2E.** Warunek `requireFlags` nie stanowi ochrony na tych systemach; dostępność i zawartość kopii sprawdź na rzeczywistym API 26/27 |

Google opisuje backup E2E jako dostępny od Androida 9 przy włączonym backupie i blokadzie ekranu. Nie obiecuj, że dowolne konto albo dowolny Android ma aktywny backup. **Nie oznaczaj całego transferu profilu jako wyłączonego z Data safety tylko na podstawie przypuszczenia o E2E.** Zastosowanie wyjątku Google dla end-to-end encrypted data wymaga rzeczywistej ochrony danego przepływu i sprawdzenia finalnej deklaracji.

## 4. Kategorie do sprawdzenia w formularzu Play Console

| Kategoria / typ | Przewidywany przepływ i cel | Weryfikacja przez wydawcę |
|---|---|---|
| **Dane osobowe → Identyfikatory użytkownika** | PGS Player ID, a przy własnym rankingu UUID; logowanie, rankingi, kontrola własności i usuwanie | PGS automatyczne vs własny ranking opcjonalny; odrębne odbiorniki i usuwanie |
| **Dane osobowe → Imię / nazwa** | Gamertag Google, pseudonim gry w Saved Games, nick w publicznym rankingu wydawcy | Widoczność, przechowywanie, ew. moderacja nicków |
| **Aktywność w aplikacji → Inne działania** | Wyniki, postęp Saved Games, **odblokowane osiągnięcia PGS**, dane weryfikowanej bitwy | PGS po uwierzytelnieniu; własny serwer tylko po zgodzie |
| **Aktywność w aplikacji → Inne treści tworzone przez użytkownika** | Pseudonimy i zgłoszenia obraźliwego pseudonimu do własnego serwera | Przyporządkowanie kategorii nazwy/treści oraz retencja zgłoszeń |
| **Aktywność w aplikacji → Interakcje z aplikacją** | Analityka SDK PGS według dokumentacji dostawcy | Potwierdź dokładne typy, cele i opcjonalność PGS 22.1.0 |
| **Informacje o aplikacji i jej działaniu → Diagnostyka** | Diagnostyka automatyczna PGS; raport gry lokalny aż do ręcznego wysłania | Nie myl lokalnego logu z automatycznym transferem; przejrzyj deklarację SDK |
| **Dzienniki awarii** | Lokalne zgłoszenie problemu inicjuje wyłącznie udostępnienie przez użytkownika; SDK może zbierać własne dane diagnostyczne | Zweryfikuj klasyfikację logów crash dla konkretnej wersji SDK |
| **Identyfikatory urządzenia lub inne** | Potencjalne dane techniczne PGS, IP w infrastrukturze własnego serwera | Sprawdź faktyczne SDK, adresowanie IP, proxy, retencję i klasyfikację |
| **Zdjęcia / filmy, lokalizacja, kontakty, mikrofon, dane płatnicze** | Własny kod nie pobiera tych danych; awatar PGS jest częścią profilu Google, nie odczytem zdjęć z galerii | W finalnym AAB zweryfikuj scalony manifest i SDK; nie zakładaj kategorii „Zdjęcia” wyłącznie z awatara |

Zaznacz „szyfrowanie danych w transmisji” dopiero po sprawdzeniu **wszystkich** aktywnych kanałów przesyłania w finalnym AAB. Wyjątki „not collected” (wyłącznie na urządzeniu / w pełni E2E) i „not shared” (np. określony dostawca usług) wynikają z oficjalnych definicji, nie z samego faktu, że dane są pseudonimowe.

## 5. Powiadomienia, usuwanie i retencja

- Powiadomienia o wyzwaniu dnia używają lokalnie `AlarmManager` i `NotificationManager`, z `POST_NOTIFICATIONS` na Androidzie 13+; przy restarcie harmonogram może zostać odtworzony przez `RECEIVE_BOOT_COMPLETED`. **Brak FCM/push i przesyłania danych do serwera dla przypomnień**.
- Własny ranking można wyłączyć. „Nowa tożsamość” zapisuje żądanie usunięcia starego UUID i uruchamia ponawianie (również offline), ale **nowe UUID nie potwierdza usunięcia na serwerze**. Przed publikacją wymagany jest test jednoczesnego usuwania i zgłaszania wyniku (ustalenie **A156-05**). Żądania mogą zaginąć przy wyczyszczeniu danych aplikacji przed ich dostarczeniem.
- Własne rekordy wyników i zgłoszenia nie mają w źródłach stałego terminu wygaśnięcia; usuwanie zależy od żądania i operatora. Trzeba zatwierdzić retencję logów IP, backupów SQLite, weryfikować usunięcie również po odtworzeniu kopii.
- Reset postępu w grze zachowuje pseudonim; wyczyszczenie danych aplikacji usuwa pliki lokalne, ale **nie** usuwa danych PGS ani już wysłanych wpisów na serwerze. Google Play Games umożliwia oddzielne usunięcie danych tej gry. Android Auto Backup również jest oddzielny.
- Brak konta wydawcy nie zwalnia z podania kontaktu i możliwości zgłoszenia żądania prywatności. Dane Google podlegają zasadom Google; dane własnego rankingu wymagają procedury obsługi przez wydawcę.

## 6. Obowiązki wydawcy przed wysłaniem Data safety

1. Uzupełnić pięć pól w [OWNER_INPUT.md](OWNER_INPUT.md): **nazwa prawna, adres pocztowy, e-mail kontaktowy, publiczny URL polityki, data wydania**. Wygenerować i opublikować obie wersje językowe polityki przez `:app:renderStorePolicies`; nie publikować szablonów z nieuzupełnionymi polami wydawcy.
2. Podać faktyczny **adres HTTPS własnego serwera rankingu** w `CW_SERVER_URL` (jeśli serwer ma działać), potwierdzić dostawcę hostingu i logi/retencję. Sekret `CW_HMAC_KEY` i klucze podpisu trzymać poza repo. Ustawić `CW_PRIVACY_URL` na publiczną politykę.
3. Wypełnić w `play_games_ids.properties` **79 identyfikatorów: 1 projekt, 4 rankingi i 74 osiągnięcia**, po czym sprawdzić na podpisanej aplikacji logowanie, wysyłanie wyników, odblokowywanie osiągnięć oraz zapis i odczyt Saved Games.
4. Zweryfikować **scalony manifest i zależności release** (PGS 22.1.0; w szczególności `AD_ID`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`), działania uprawnień i transferów, łącznie z urządzeniami Android 8/8.1.
5. Sprawdzić i udokumentować na działającym serwerze: zgodę, publiczny nick/publiczne ID, brak jawnego sekretu w bazie, możliwość zgłaszania obraźliwych nazw, równoczesne usuwanie/wynik, żądania usunięcia, logi IP i okres przechowywania.
6. Dopiero na podstawie **wydawanego AAB i wdrożonych usług** zatwierdzić dla każdej kategorii zbieranie, udostępnianie, cel, opcjonalność, szyfrowanie i procedurę usuwania. Zmiany SDK, serwera, reklam i zakupów wymagają nowej oceny.

## Oficjalne źródła (zweryfikowane 2 listopada 2026 r.)

- [Google Play Console — Data safety: definicje zbierania, udostępniania i wyjątki](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play — polityka danych użytkowników i wymogi polityki prywatności](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Games SDK — typy danych, osiągnięcia, Saved Games, analityka i diagnostyka](https://developer.android.com/games/pgs/data-collection)
- [Google Play Games — uwierzytelnianie](https://developer.android.com/games/pgs/platform-authentication)
- [Google Play Games — usuwanie danych gry](https://support.google.com/googleplay/answer/9130646?hl=pl)
- [Android — Auto Backup i warunki szyfrowania od Androida 9](https://developer.android.com/identity/data/autobackup)
- [Android — zalecenia bezpieczeństwa kopii](https://developer.android.com/privacy-and-security/risks/backup-best-practices)
