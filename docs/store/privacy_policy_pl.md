---
layout: null
title: "Polityka prywatności — Century War"
permalink: /store/privacy-policy-pl/
lang: pl
---

# Polityka prywatności gry Century War

**Obowiązuje od:** {{OWNER.release_date}}  
**Dotyczy:** gry Century War na Androida, w tym integracji Google Play Games, jeżeli została skonfigurowana w opublikowanym wydaniu.

## 1. Wydawca i kontakt

Century War jest rozwijana pod marką **WooGitsu Games**. Wydawca odpowiada za własne funkcje gry oraz informacje dotyczące prywatności przedstawione użytkownikom w Google Play. Google przetwarza dane swojej usługi Google Play Games zgodnie z własnymi zasadami.

**Dane prawne wydawcy (źródło: [OWNER_INPUT.md](OWNER_INPUT.md)):**
- Pełna nazwa prawna wydawcy: **{{OWNER.publisher_name}}**
- Adres korespondencyjny: **{{OWNER.postal_address}}**
- Kontakt w sprawach prywatności i usuwania danych: **{{OWNER.contact_email}}**
- Publiczny adres polityki: **{{OWNER.privacy_policy_url}}**

Na ten adres można kierować pytania o przetwarzanie danych i wnioski dotyczące danych obsługiwanych przez wydawcę. Dane przechowywane przez Google Play Games usuwa się również za pomocą narzędzi Google opisanych poniżej.

## 2. Dane przechowywane na urządzeniu

Gra zapisuje w prywatnej pamięci aplikacji:
- pseudonim dowódcy wybrany w grze;
- etapy kampanii, gwiazdki, medale, badania, misje, osiągnięcia i lokalne rekordy;
- statystyki (np. liczbę bitew, zwycięstw, pokonanych jednostek i czas gry);
- ustawienia gry (np. język, jakość grafiki, dźwięk, wibracje, przypomnienia i ich godzinę);
- tymczasowy zapis przerwanej bitwy do wznowienia (plik battle.snap);
- do 10 lokalnych raportów awarii i błędów (katalog files/crash).

Podstawowa rozgrywka, lokalne rankingi i zapisywanie postępu **działają bez internetu**. Gra nie wymaga zakładania konta u wydawcy. Nie prosi o dostęp do lokalizacji, kontaktów, mikrofonu ani aparatu. Nie ma reklam, zakupów w aplikacji ani czatu.

Plik battle.snap służy wyłącznie lokalnemu wznowieniu bitwy, nie jest wysyłany w zapisie chmurowym Play Games i nie jest objęty regułami systemowej kopii zapasowej.

### Lokalne raportowanie problemów

Po awarii aplikacja zapisuje lokalnie raport techniczny: wersję gry, model urządzenia, wersję Androida, nazwę ekranu, ostatnie 30 zdarzeń technicznych (np. otwarcie ekranu, etap bitwy) i stos wywołań. Zapis nie obejmuje pseudonimu, identyfikatora gracza, treści wyjątków ani danych profilu. Przechowywanych jest maksymalnie 10 raportów; starsze są automatycznie usuwane. Raporty nie są synchronizowane z Play Games ani obejmowane systemową kopią zapasową.

Przy kolejnym uruchomieniu po awarii gra **jednorazowo pyta**, czy udostępnić raport. Można też wybrać **Ustawienia → O grze → Zgłoś problem**. W obu przypadkach aplikacja wyłącznie otwiera systemowe okno udostępniania tekstu (np. e-mail); **nie wysyła raportów automatycznie ani do wydawcy, ani do zewnętrznej usługi**. Użytkownik decyduje, czy i komu go przekazać. Zapisane raporty można usunąć przez wyczyszczenie danych aplikacji.

## 3. Google Play Games — konto i uwierzytelnianie

Gra zawiera integrację **Google Play Games Services v2**. Działa ona tylko w wydaniach, w których wydawca skonfigurował identyfikatory usługi. Korzystanie z sieciowych funkcji Google Play Games nie jest wymagane do gry bez internetu.

Po włączeniu integracji SDK Google może **automatycznie uwierzytelnić profil Gier Play przy uruchomieniu gry**, jeśli konto jest do tego skonfigurowane. Gra sprawdza stan uwierzytelnienia również przy powrocie z tła. W Ustawieniach gry, na ekranie Play Games, można ponowić logowanie lub wywołać synchronizację. **Przesłanie wyników i kopii profilu może nastąpić automatycznie po uwierzytelnieniu** — nie jest wymagane osobne potwierdzenie każdego zapisu.

Uwierzytelnienie wykorzystuje tożsamość konta Gier Play, w tym identyfikator gracza i nazwę profilu. Google może przetwarzać również awatar oraz dane analityczne i diagnostyczne swoich usług. Aplikacja nie wymaga podania wydawcy adresu e-mail, numeru telefonu ani hasła do konta Google.

**Wylogowanie / wyłączenie logowania:** obecna aplikacja nie ma przycisku wylogowania z Play Games ani odrębnego wyłącznika chmury. Usługą i automatycznym logowaniem dla gier zarządza się przez ustawienia konta/profilu Gier Play w Google Play lub ustawieniach Androida; sposób wyświetlania opcji zależy od urządzenia i wersji usług. Zmiana konta lub wyłączenie uwierzytelniania Play Games **nie usuwa automatycznie** wcześniej zapisanych danych.

Zasady Google: https://policies.google.com/privacy  
Zarządzanie profilem: https://play.google.com/games/profile  
Pomoc Google: https://support.google.com/googleplay/answer/9130646?hl=pl

## 4. Rankingi Google Play Games i zapis w chmurze

Po skonfigurowaniu usługi i uwierzytelnieniu aplikacja może wysyłać do Google wyniki **czterech rankingów**: oblężenie bez końca, wyzwanie dnia, gwiazdki kampanii i potyczka. W publicznych tabelach Google może być widoczna nazwa profilu Play Games i wynik zgodnie z ustawieniami widoczności konta. Pseudonim wybrany *w grze* nie jest wysyłany jako publiczna nazwa do interfejsu rankingu PGS.

**Zapis w chmurze obejmuje cały profil gry w formacie JSON**, nie tylko stan kampanii: pseudonim wybrany w grze, lokalne wyniki (także zapisane w nich nazwy i daty), postęp, medale, badania, misje, lokalne osiągnięcia, statystyki oraz ustawienia. Przesyłany profil może zostać odczytany po ponownym uruchomieniu gry lub na innym urządzeniu, na którym uwierzytelniono to samo konto PGS. W przypadku konfliktu wersji gra wybiera profil o większym postępie według własnych reguł.

**Osiągnięcia Google Play Games:** w bieżącym kodzie działa system osiągnięć lokalnych, ale ich przesyłanie do usługi osiągnięć Google nie jest jeszcze podłączone. Osiągnięcia lokalne są jednak częścią profilu objętego kopią w chmurze.

Gra obsługuje również **opcjonalny własny serwer rankingowy** wydawcy. Funkcja działa wyłącznie w wydaniach ze skonfigurowanym adresem HTTPS, kluczem HMAC i adresem tej polityki oraz po osobnej zgodzie w grze. Wtedy aplikacja wysyła losowe UUID gracza, pseudonim, planszę, wynik, datę i parametry bitwy oraz komendy powtórki do weryfikacji wyniku. Serwer zapisuje w SQLite UUID, pseudonim, wyniki, metadane i skrót SHA-256 zgłoszenia, **nie zapisuje pełnej powtórki ani adresu IP w SQLite**. Adres IP może być używany tymczasowo do ograniczania ruchu i występować w logach infrastruktury. Bez konfiguracji i zgody nie są przesyłane wyniki do własnego serwera. Sam kod nie oznacza, że serwer jest już dostępny publicznie.

**Publiczne pseudonimy i zgłaszanie naruszeń:** jeżeli własny serwer rankingu zostanie uruchomiony, inni gracze mogą zobaczyć wybrany przez Ciebie pseudonim i wyniki. Identyfikator widoczny publicznie jest inny niż wewnętrzny UUID instalacji; sekret instalacji służy do uwierzytelniania własnych zgłoszeń i usuwania danych. Serwer może przechowywać skrót sekretu, nie sam sekret w postaci jawnej. Po wdrożeniu funkcji zgłaszania zastrzeżeń do pseudonimów (zlecenie 80) użytkownik może zgłosić wpis z rankingu przez przytrzymanie jego nazwy: przesyłane są publiczne ID zgłaszanego wpisu i powód zgłoszenia; administrator przegląda zgłoszenia i może moderować nazwy. Adres IP zgłaszającego może zostać użyty do limitowania zgłoszeń i zapisany w logach infrastruktury zgodnie z konfiguracją serwera. Funkcje zależą od rzeczywiście wdrożonej wersji serwera i aplikacji, nie od samej obecności ich projektu w repozytorium.

Google deklaruje szyfrowanie danych Play Games w transmisji (HTTPS). Informacje o zakresie danych, przechowywaniu i ustawieniach widoczności: https://developer.android.com/games/pgs/data-collection

## 5. Lokalne powiadomienia o wyzwaniu dnia

Gra może przypominać raz dziennie o wyzwaniu dnia, **po pierwszym zwycięstwie** i przy włączonych przypomnieniach. Godzinę można zmienić w Ustawieniach gry. Na Androidzie 13 i nowszym aplikacja prosi o systemowe zezwolenie na powiadomienia po pierwszym zwycięstwie, a nie przy samym uruchomieniu.

Przypomnienia są planowane lokalnie przez Android AlarmManager i pokazywane przez NotificationManager. Harmonogram może zostać odtworzony po restarcie telefonu, aktualizacji aplikacji lub zmianie czasu. **Do wysłania przypomnienia gra nie przekazuje danych na serwer** i nie korzysta z Firebase Cloud Messaging ani innych powiadomień push.

Przypomnienia można wyłączyć w **Ustawieniach gry** oraz niezależnie w **ustawieniach powiadomień Androida**.

## 6. Uprawnienia Androida

| Uprawnienie | Wykorzystanie |
|---|---|
| INTERNET | Połączenie z Play Games i opcjonalnym własnym rankingiem HTTPS; gra offline nadal działa |
| POST_NOTIFICATIONS | Lokalne przypomnienie o wyzwaniu dnia, za zgodą użytkownika na Androidzie 13+ |
| RECEIVE_BOOT_COMPLETED | Ponowne zaplanowanie przypomnienia po restarcie urządzenia |
| VIBRATE | Wibracje stanowiące efekt gry, które można wyłączyć w ustawieniach |

Uprawnienie INTERNET nie oznacza, że każda funkcja gry wymaga stałego połączenia.

## 7. Systemowa kopia zapasowa Androida

Android może przechowywać kopię danych aplikacji zgodnie z ustawieniami urządzenia i konta Google. Reguły aplikacji ograniczają chmurową kopię Androida i przenoszenie danych do **profile.json**; plik battle.snap jest pominięty. Wydawca nie otrzymuje automatycznie dostępu do kopii systemowej.

Kopia Androida jest **oddzielna** od synchronizacji Saved Games w Google Play Games i może podlegać innym zasadom usuwania oraz przywracania. Zobacz https://developer.android.com/identity/data/autobackup i https://policies.google.com/privacy.

## 8. Usuwanie danych i okres przechowywania

- **Dane lokalne:** funkcja „Resetuj postęp” usuwa postęp i rekordy, lecz **zachowuje pseudonim**. Aby usunąć również pseudonim i inne dane lokalne, wyczyść pamięć aplikacji w Androidzie lub odinstaluj grę. Kopia Androida może pozostawać dostępna według ustawień konta.
- **Dane własnego rankingu:** wybierz **Ustawienia → Nowa tożsamość → Potwierdź**. Aplikacja zapisuje żądanie usunięcia wszystkich wyników starego UUID i pseudonimu na serwerze, a następnie nada nowy identyfikator. Gdy nie ma internetu, ponawia próbę po ponownym uruchomieniu, także po wyłączeniu wysyłania wyników. **Nowe UUID nie oznacza potwierdzonego usunięcia danych na serwerze.** Do czasu dostarczenia żądania wyniki mogą nadal być widoczne; nie odinstalowuj gry ani nie czyść jej pamięci, dopóki żądanie nie zostanie obsłużone. W razie wątpliwości skontaktuj się z wydawcą poprzez kontakt z sekcji 1. Rekordy własnego rankingu pozostają do usunięcia na żądanie lub zamknięcia usługi; nie mają automatycznego terminu ważności. Lokalne kopie bezpieczeństwa są rotowane i po ich odtworzeniu operator musi powtórzyć późniejsze usunięcia.
- **Dane Google Play Games:** przejdź do https://play.google.com/games/profile, wybierz ustawienia danych Gier Play i **usuń dane tej konkretnej gry**. Alternatywnie skorzystaj z: Sklep Play → profil → Ustawienia → Ogólne → Profil w Grach Play → Prywatność i ustawienia → Usuń profil i dane gier. Nazwy opcji mogą się zmieniać. Możliwe jest też usunięcie całego profilu Gier Play. Szczegóły: https://support.google.com/googleplay/answer/9130646?hl=pl.
- **Ważne:** własny ranking, profil lokalny, systemowa kopia Androida i dane PGS to cztery oddzielne miejsca. Reset lokalny nie usuwa danych własnego serwera ani PGS; synchronizacja PGS może przywrócić postęp.

Okres przechowywania danych przez Google jest określany przez Google i ustawienia konta użytkownika. Wydawca może przechowywać pseudonimowe dane rankingu opisane powyżej, jeśli opcjonalny serwer zostanie uruchomiony.

## 9. Ochrona danych i dzieci

Gra przechowuje lokalne dane w prywatnej przestrzeni aplikacji Android. Przy aktywnych rankingach publicznie widoczne mogą być pseudonimy i wyniki PGS oraz osobnego serwera wydawcy; użytkownik nie ma możliwości publikowania postów ani wysyłania wiadomości. Zgłoszenia obraźliwych pseudonimów są dostępne po wdrożeniu mechanizmu moderacji (zlecenie 80). Nie udostępnia czatu, wiadomości między graczami ani lokalizacji. Jedynie raport techniczny można ręcznie udostępnić przez systemowe okno Androida. Rankingi PGS mogą jednak pokazywać nazwy profili Google i wyniki zgodnie z wybraną widocznością. Klasyfikacja wieku jest ustalana na podstawie ankiety IARC, a nie stanowi automatycznej deklaracji kierowania produktu do dzieci.

## 10. Zmiany polityki

Przed aktywacją własnego rankingu w opublikowanym wydaniu lub dodaniem nowych sposobów przetwarzania danych (np. reklam albo zakupów) zweryfikujemy politykę i informacje w Google Play.

_Wersja angielska: /store/privacy-policy-en/._
