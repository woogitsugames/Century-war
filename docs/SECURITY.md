# Audyt bezpieczeństwa aplikacji — zlecenie 114

Data przeglądu: 2026-11-01. Zakres: manifest Androida, przychodzące pliki powtórek,
własny provider udostępniania, przypomnienia, linki i klient rankingu, kopie danych
oraz sieć. Przegląd statyczny źródeł; **nie zastępuje testów APK na urządzeniu**.

## Wyniki i poprawki

| Obszar | Stan przed poprawką | Poprawka / stan po przeglądzie |
|---|---|---|
| Automatyczna kopia | `profile.json` trafiał do kopii chmurowej i transferu urządzeń, mimo że zawiera lokalny `playerSecret` | `allowBackup=false`; wykluczenie prywatnych domen w `backup_rules.xml` i osobno dla chmury oraz transferu Androida 12+ |
| Ruch HTTP | Brak jawnego `network_security_config` | `usesCleartextTraffic=false`, polityka sieciowa odrzucająca cleartext i zaufanie tylko do certyfikatów systemowych |
| Ranking | `ScoreClient.configured()` wymagał HTTPS, a przekierowania były wyłączone; typ połączenia nie był dodatkowo kontrolowany | Wymagany `HttpsURLConnection`; nadal brak podążania za HTTP 30x; odpowiedzi czytane do limitu **1 048 576 znaków**, bez wcześniejszego `readText()` bez ograniczeń |
| Import CWR | Limit wejściowy 2 MiB, a magazyn odczytywał tylko pliki do 1 MiB; strumień zwracający ciągle 0 mógł zapętlić odczyt | Wspólny limit **1 MiB**, minimum 24 B, natychmiastowe odrzucenie zera z `InputStream.read()`; dalej obowiązuje dekodowanie i `Replay.verify()` |
| Udostępnianie CWR | Provider już nie był eksportowany, dopuszczał tylko tryb `r` i sprawdzał ścieżkę kanoniczną | Dodatkowy zakaz dowiązań symbolicznych pliku **i katalogu** `replays`, kontrola katalogu względem korzenia; limit pliku wyrównany do 1 MiB |
| Link polityki prywatności | Odrębna, mniej ścisła walidacja `URI` | Ten sam filtr `SecureLinks.isHttps()`, który jest używany do otwierania linków w grze |

## Lista sprawdzonych punktów

- **Komponenty eksportowane:** z manifestu aplikacji wynika, że `MainActivity`
  ma `exported=true` (wymagane przez launcher i otwieranie CWR). Własny
  `ReplayShareProvider` i `DailyReminderReceiver` mają `exported=false`.
  Własnych usług Androida nie zadeklarowano. Provider inicjalizacji Play Games
  jest usuwany przez `tools:node="remove"`.
- **Intencje przychodzące:** `MainActivity.replayUri()` przyjmuje tylko
  `ACTION_VIEW` z URI o schemacie `content`; nie otwiera importu z `file://`,
  `http://` ani `https://`. Dane trafiają do osobnego wątku, po ograniczonym
  odczycie do `ReplayStore.importAsync()`. Weryfikacja odtwarza bitwę i sprawdza
  zwycięzcę, wynik, falę i czas. Obcy zapis otrzymuje prefiks `friend_`.
  Filtry MIME w manifeście są celowo szerokie ze względu na menedżery plików;
  **nie można polegać na MIME ani nazwie URI jako zabezpieczeniu**.
- **Provider CWR:** URI ma jeden segment `[a-z0-9_-]{1,64}.cwr`, brak zapytania
  i fragmentu, plik należy do `files/replays`, mieści się w limitach.
  `openFile()` działa wyłącznie w trybie `r`; `insert`, `update` i
  `delete` nie wykonują operacji. Udostępnienie następuje po działaniu gracza
  przez `ACTION_SEND` i jednorazową flagę `FLAG_GRANT_READ_URI_PERMISSION`.
- **Powiadomienia:** prywatny receiver; alarm jest kierowany do własnej klasy
  przez jawny `Intent` z `FLAG_IMMUTABLE`. Kliknięcie powiadomienia otwiera
  własną Activity. `POST_NOTIFICATIONS` jest proszone warunkowo; odbiornik
  nie ujawnia danych przez eksportowane API.
- **Sieć:** ranking aktywuje się tylko przy skonfigurowanym HTTPS, kluczu
  minimum 32 B oraz poprawnym URL polityki prywatności. URL rankingu
  nie akceptuje poświadczeń w adresie, query, fragmentu ani ścieżki różnej
  od korzenia. Zabezpieczenia transportu obowiązują też pozostałe połączenia
  aplikacji; Play Games korzysta z własnego stosu usług.
- **Dane lokalne:** profil, sekret instalacji, historia, kolejki wysyłek
  i powtórki pozostają w prywatnej przestrzeni aplikacji. Lokalny sekret
  `playerSecret` jest pomijany w `Profile.toCloudJson()`, ale **był**
  obecny w kopii Androida. Po zmianie automatyczny backup i transfer profilu
  są wyłączone. Oznacza to brak odtworzenia lokalnego profilu z mechanizmu
  backupu Androida po reinstalacji; własne funkcje synchronizacji gry
  są niezależne.
- **Raporty awarii:** wysłanie problemu inicjuje użytkownik z dialogu,
  nie ma automatycznej wysyłki danych przez ten przepływ.

## Testy i sposób weryfikacji

Dodano / rozszerzono testy:
- `ReleaseConfigTest`: eksport komponentów, prywatny receiver i provider,
  zakaz ruchu jawnego, magazyn zaufanych certyfikatów oraz wyłączenie
  kopii w chmurze i przeniesienia danych.
- `ReplayImportTest`: minimum 24 B, granica 1 MiB, odrzucenie zbyt dużego
  i pustego wejścia, wadliwego strumienia zwracającego 0.
- `ReplayShareProviderTest`: dowiązanie do pliku i całego katalogu, za krótki
  i za duży plik, URI z parametrami, brak dostępu do profilu.
- `ScoreClientTest`: niedozwolone adresy (poświadczenia, query, fragment,
  nieprawidłowy port, inna ścieżka); poprawny URL HTTPS.

Wymagane komendy przed scaleniem:

```bash
./gradlew :app:assembleDebug --console=plain --no-configuration-cache
./gradlew :app:testDebugUnitTest --tests '*ReleaseConfigTest*' --tests '*ReplayImportTest*' --tests '*ReplayShareProviderTest*' --tests '*ScoreClientTest*' --console=plain --no-configuration-cache
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --tests '*BattleRulesTest*' --tests '*Snapshot*' --tests '*LTest*' --tests '*Replay*' --tests '*Counter*' --tests '*Endless*' --tests '*AiPersonality*' --console=plain --no-configuration-cache
```

**Status uruchomienia:** budowa APK i testy Android/Robolectric wymagają
Android SDK 36 i zależności Gradle; w środowisku wykonawczym bez SDK i dostępu
do repozytoriów Maven/GitHub nie można ich wiarygodnie uruchomić.
Nie należy oznaczać ich jako zaliczonych bez raportu z uruchomienia lub CI.

## Ryzyka rezydualne i weryfikacja wydania

1. **Scalony manifest:** skontroluj wynik `merged_manifest` obu wariantów,
   w tym komponenty dodawane przez Play Games. Ten raport opisuje źródłowy
   manifest aplikacji, nie manifest z gotowego APK.
2. **Transfer OEM:** polityka `allowBackup=false` jest wsparta regułami
   wykluczeń; sprawdź zachowanie transferu na realnym Androidzie 12–16
   u używanych producentów. Użytkownik może utracić lokalny postęp
   po reinstalacji, jeśli nie ma niezależnej synchronizacji.
3. **Niezaufany provider wejściowy:** ograniczenie rozmiaru i odczyt poza
   głównym wątkiem zmniejszają ryzyko DoS, ale obcy ContentProvider może
   blokować samo `openInputStream()` lub `read()` bez końca. Wariant
   z wymuszonym timeoutem wymaga osobnego projektu anulowania I/O.
4. **Zaufanie TLS:** nie dodano certificate pinning, gdyż host rankingu
   pochodzi z konfiguracji wydania. Przed produkcją potwierdź domenę,
   certyfikat, brak przekierowań i zasadę ochrony klucza HMAC. Klucz
   umieszczony w APK **nie jest sekretem administracyjnym**.
5. **Współbieżny dostęp i symlinki:** sprawdzanie ścieżki przed otwarciem
   pliku nie zabezpiecza przed atakującym, który już wykonuje kod z UID
   aplikacji i może podmieniać pliki. Przy normalnym sandboxie inne aplikacje
   nie mają uprawnień zapisu w `filesDir`.
6. **Integracja/CI:** przed publikacją konieczny jest rzeczywisty build,
   pełne testy repozytorium i smoke testy udostępniania CWR,
   importowania błędnych plików oraz działania rankingu na HTTPS.
