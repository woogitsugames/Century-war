---
layout: null
title: "Polityka prywatności — Century War"
permalink: /store/privacy-policy-pl/
lang: pl
---

# Polityka prywatności gry Century War

**Obowiązuje od:** 1 listopada 2026 r.  
**Dotyczy:** gry Century War na Androida, w tym integracji Google Play Games, jeżeli została skonfigurowana w opublikowanym wydaniu.

## 1. Wydawca i kontakt

Century War jest rozwijana pod marką **WooGitsu Games**. Wydawca odpowiada za własne funkcje gry oraz informacje dotyczące prywatności przedstawione użytkownikom w Google Play. Google przetwarza dane swojej usługi Google Play Games zgodnie z własnymi zasadami.

**Do uzupełnienia przed publikacją:**
- Pełna nazwa prawna wydawcy: **[UZUPEŁNIJ_NAZWĘ_PRAWNĄ_WYDAWCY]**
- Kontakt w sprawach prywatności: **[UZUPEŁNIJ_EMAIL_KONTAKTOWY]**

Na ten adres można kierować pytania o przetwarzanie danych i wnioski dotyczące danych obsługiwanych przez wydawcę. Dane przechowywane przez Google Play Games usuwa się również za pomocą narzędzi Google opisanych poniżej.

## 2. Dane przechowywane na urządzeniu

Gra zapisuje w prywatnej pamięci aplikacji:
- pseudonim dowódcy wybrany w grze;
- etapy kampanii, gwiazdki, medale, badania, misje, osiągnięcia i lokalne rekordy;
- statystyki (np. liczbę bitew, zwycięstw, pokonanych jednostek i czas gry);
- ustawienia gry (np. język, jakość grafiki, dźwięk, wibracje, przypomnienia i ich godzinę);
- tymczasowy zapis przerwanej bitwy do wznowienia (plik battle.snap);
- do 10 lokalnych raportów awarii i błędów (katalog files/crash);
- przy włączeniu własnego rankingu: losowy UUID i oddzielny, 32-bajtowy sekret
  instalacji (w `profile.json`), używany do potwierdzania własności wpisów.

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

**Zapis w chmurze obejmuje profil gry w formacie JSON, z wyłączeniem lokalnych danych własnego rankingu (UUID, sekret instalacji i zgoda na publikację)**, nie tylko stan kampanii: pseudonim wybrany w grze, lokalne wyniki (także zapisane w nich nazwy i daty), postęp, medale, badania, misje, lokalne osiągnięcia, statystyki oraz ustawienia. Przesyłany profil może zostać odczytany po ponownym uruchomieniu gry lub na innym urządzeniu, na którym uwierzytelniono to samo konto PGS. W przypadku konfliktu wersji gra wybiera profil o większym postępie według własnych reguł.

**Osiągnięcia Google Play Games:** w bieżącym kodzie działa system osiągnięć lokalnych, ale ich przesyłanie do usługi osiągnięć Google nie jest jeszcze podłączone. Osiągnięcia lokalne są jednak częścią profilu objętego kopią w chmurze.

Gra obsługuje również **opcjonalny własny serwer rankingowy** wydawcy. Funkcja działa wyłącznie w wydaniach ze skonfigurowanym adresem HTTPS, kluczem HMAC i adresem tej polityki oraz po osobnej zgodzie w grze. Aplikacja wysyła losowe UUID gracza, pseudonim, planszę, wynik, datę i parametry bitwy, komendy powtórki do weryfikacji oraz **sekret instalacji 32 B** przy żądaniach dotyczących własnego konta. Serwer porównuje jego skrót SHA-256 z wartością przypisaną do UUID; sam sekret jest przesyłany przez HTTPS i **nie jest zapisywany w bazie SQLite w postaci jawnej**. Baza zapisuje UUID, pseudonim, wyniki i metadane, skrót zgłoszenia oraz **publiczne, pseudonimowe ID** wyliczone jako HMAC z UUID z wykorzystaniem osobnego klucza dostępnego tylko na serwerze. Publiczne rankingi pokazują nick, wynik i publiczne ID, **nie pokazują surowego UUID**. Serwer nie przechowuje pełnej powtórki ani IP w SQLite. IP służy tymczasowo do ograniczania ruchu i może znajdować się w logach infrastruktury. Bez konfiguracji i zgody nie są przesyłane wyniki do własnego rankingu. Sam kod nie oznacza uruchomionego serwera.

**Zgłaszanie pseudonimów:** po przytrzymaniu wpisu w rankingu globalnym można zgłosić nick do moderacji. Wydawca otrzymuje publiczne ID zgłaszanego gracza i powód („obraźliwy”); serwer zapisuje ponadto UUID tego gracza, publiczny nick w chwili zgłoszenia oraz datę. Zgłoszenie nie wymaga podawania konta zgłaszającego; jego IP jest czasowo wykorzystywane do limitowania zgłoszeń i może zostać zapisane w logach proxy. Zgłoszenia są dostępne tylko administratorowi i służą rozpatrywaniu nadużyć, nie reklamie.

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
- **Dane własnego rankingu i zgłoszenia dotyczące konta:** wybierz **Ustawienia → Nowa tożsamość → Potwierdź**. Aplikacja zapisuje żądanie usunięcia wszystkich wyników starego UUID i pseudonimu na serwerze, a następnie nada nowy identyfikator. Gdy nie ma internetu, ponawia próbę po ponownym uruchomieniu, także po wyłączeniu wysyłania wyników. **Nowe UUID nie oznacza potwierdzonego usunięcia danych na serwerze.** Do czasu dostarczenia żądania wyniki mogą nadal być widoczne; nie odinstalowuj gry ani nie czyść jej pamięci, dopóki żądanie nie zostanie obsłużone. W razie wątpliwości skontaktuj się z wydawcą poprzez kontakt z sekcji 1. Rekordy własnego rankingu i zgłoszenia przypisane do UUID pozostają do usunięcia na żądanie lub zamknięcia usługi; nie mają automatycznego terminu ważności. Usunięcie konta kasuje również zgłoszenia przypisane do jego UUID, natomiast wewnętrzna informacja o blokadzie UUID może zostać zachowana dla ochrony przed nadużyciami. Zgłoszenia o innych użytkownikach nie są usuwane przez zmianę tożsamości zgłaszającego, gdyż raport nie jest z nim powiązany. Lokalne kopie bezpieczeństwa są rotowane i po ich odtworzeniu operator musi powtórzyć późniejsze usunięcia.
- **Dane Google Play Games:** przejdź do https://play.google.com/games/profile, wybierz ustawienia danych Gier Play i **usuń dane tej konkretnej gry**. Alternatywnie skorzystaj z: Sklep Play → profil → Ustawienia → Ogólne → Profil w Grach Play → Prywatność i ustawienia → Usuń profil i dane gier. Nazwy opcji mogą się zmieniać. Możliwe jest też usunięcie całego profilu Gier Play. Szczegóły: https://support.google.com/googleplay/answer/9130646?hl=pl.
- **Ważne:** własny ranking, profil lokalny, systemowa kopia Androida i dane PGS to cztery oddzielne miejsca. Reset lokalny nie usuwa danych własnego serwera ani PGS; synchronizacja PGS może przywrócić postęp.

Okres przechowywania danych przez Google jest określany przez Google i ustawienia konta użytkownika. Wydawca może przechowywać pseudonimowe dane rankingu opisane powyżej, jeśli opcjonalny serwer zostanie uruchomiony.

## 9. Ochrona danych i dzieci

Gra przechowuje lokalne dane w prywatnej przestrzeni aplikacji Android. Nie udostępnia czatu, wiadomości między graczami ani lokalizacji. Jedynie raport techniczny można ręcznie udostępnić przez systemowe okno Androida. Rankingi PGS mogą jednak pokazywać nazwy profili Google i wyniki zgodnie z wybraną widocznością. Klasyfikacja wieku jest ustalana na podstawie ankiety IARC, a nie stanowi automatycznej deklaracji kierowania produktu do dzieci.

## 10. Zmiany polityki

Przed aktywacją własnego rankingu w opublikowanym wydaniu lub dodaniem nowych sposobów przetwarzania danych (np. reklam albo zakupów) zweryfikujemy politykę i informacje w Google Play.

_Wersja angielska: /store/privacy-policy-en/._
