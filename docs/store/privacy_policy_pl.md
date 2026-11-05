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
- historię rozegranych bitew (plik `history.json`);
- statystyki (np. liczbę bitew, zwycięstw, pokonanych jednostek i czas gry);
- ustawienia gry (np. język, jakość grafiki, dźwięk, wibracje, przypomnienia i ich godzinę);
- tymczasowy zapis przerwanej bitwy do wznowienia (plik battle.snap);
- do 10 lokalnych raportów awarii i błędów oraz do 3 raportów o zakończeniu procesu przez system (katalog files/crash);
- lokalny dziennik testu (plik files/playtest.json): dni i sesje gry, czas do ukończenia samouczka i pierwszej wygranej, serie porażek, kapitulacje, odwiedzone ekrany i wydajność ostatnich sesji;
- przy włączeniu własnego rankingu: losowy UUID i oddzielny, 32-bajtowy sekret
  instalacji (w `profile.json`), używany do potwierdzania własności wpisów.

Podstawowa rozgrywka, lokalne rankingi i zapisywanie postępu **działają bez internetu**. Gra nie wymaga zakładania konta u wydawcy. Nie prosi o dostęp do lokalizacji, kontaktów, mikrofonu ani aparatu. Nie ma reklam, zakupów w aplikacji, czatu ani własnego SDK reklamowego lub analitycznego. SDK Google Play Games może niezależnie zbierać dane analityczne i diagnostyczne opisane w sekcji 3.

Plik battle.snap służy wyłącznie lokalnemu wznowieniu bitwy, nie jest wysyłany w zapisie chmurowym Play Games i nie jest objęty regułami systemowej kopii zapasowej.

### Lokalne raportowanie problemów

Po awarii aplikacja zapisuje lokalnie raport techniczny: wersję gry, model urządzenia, wersję Androida, nazwę ekranu, ostatnie 30 zdarzeń technicznych (np. otwarcie ekranu, etap bitwy) i stos wywołań. Na Androidzie 11 i nowszym raport może też opisywać systemową przyczynę poprzedniego zamknięcia gry (np. brak odpowiedzi aplikacji, błąd natywny, brak pamięci): kod przyczyny, krótki opis systemu, zużycie pamięci, a przy braku odpowiedzi stos wywołań wątku głównego i wątku gry. Zapis nie obejmuje pseudonimu, identyfikatora gracza, treści wyjątków ani danych profilu. Przechowywanych jest maksymalnie 10 raportów awarii i błędów oraz 3 raporty o zakończeniu procesu; starsze są automatycznie usuwane. Zakończenie z braku pamięci w tle (normalna praca systemu) nie jest zapisywane. Raporty nie są synchronizowane z Play Games ani obejmowane systemową kopią zapasową.

Przy kolejnym uruchomieniu po awarii gra **jednorazowo pyta**, czy udostępnić raport. Można też wybrać **Ustawienia → O grze → Zgłoś problem**. W obu przypadkach aplikacja wyłącznie otwiera systemowe okno udostępniania tekstu (np. e-mail); **nie wysyła raportów automatycznie ani do wydawcy, ani do zewnętrznej usługi**. Użytkownik decyduje, czy i komu go przekazać. Zapisane raporty można usunąć przez wyczyszczenie danych aplikacji.

**Ustawienia → O grze → Raport dla testera** działa tak samo: po opisie zawartości otwiera systemowe okno udostępniania z tekstem zawierającym wersję gry, model urządzenia, wersję Androida, postęp, czas gry, tryby, wydajność i ustawienia (z dziennika testu i profilu). Zawiera też **ustawienia dostępności, w tym informację, czy działa TalkBack**; okno przed wysłaniem mówi o tym wprost. Raport nie zawiera pseudonimu, identyfikatorów ani konta Play Games i **nie jest wysyłany automatycznie**. W wersjach testowych gry raport zawiera też losowy kod testera (np. „K7QM”), wylosowany w telefonie i niepowiązany z kontem, urządzeniem ani profilem gracza; służy tylko do połączenia raportów jednego testera i trafia do nas wyłącznie wtedy, gdy tester sam wyśle raport. Dziennik testu nie jest synchronizowany z Play Games ani obejmowany systemową kopią zapasową.

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

**Osiągnięcia Google Play Games:** po skonfigurowaniu identyfikatorów PGS i uwierzytelnieniu gracza gra przekazuje Google informacje o odblokowanych osiągnięciach. Nowe odblokowania są zgłaszane przez usługę osiągnięć PGS, a już zdobyte osiągnięcia są ponownie synchronizowane przy kolejnym uwierzytelnieniu. Osiągnięcia są też częścią zapisu profilu w chmurze Saved Games. Bez prawidłowej konfiguracji PGS osiągnięcia pozostają lokalne.

Gra obsługuje również **opcjonalny własny serwer rankingowy** wydawcy. Funkcja działa wyłącznie w wydaniach ze skonfigurowanym adresem HTTPS, kluczem HMAC i adresem tej polityki oraz po osobnej zgodzie w grze. Aplikacja wysyła losowe UUID gracza, pseudonim, planszę, wynik, datę i parametry bitwy, komendy powtórki do weryfikacji oraz **sekret instalacji 32 B** przy żądaniach dotyczących własnego konta. Serwer porównuje jego skrót SHA-256 z wartością przypisaną do UUID; sam sekret jest przesyłany przez HTTPS i **nie jest zapisywany w bazie SQLite w postaci jawnej**. Baza zapisuje UUID, pseudonim, wyniki i metadane, skrót zgłoszenia oraz **publiczne, pseudonimowe ID** wyliczone jako HMAC z UUID z wykorzystaniem osobnego klucza dostępnego tylko na serwerze. Publiczne rankingi pokazują nick, wynik i publiczne ID, **nie pokazują surowego UUID**. Serwer nie przechowuje pełnej powtórki (tylko jej skrót). **Adres IP jest używany do limitów, a do dziennego limitu zapisywany jest jego jednokierunkowy skrót powiązany z dniem, usuwany następnego dnia**; sam adres IP nie jest zapisywany w bazie, ale może znajdować się w logach infrastruktury (serwer proxy). Bez konfiguracji i zgody nie są przesyłane wyniki do własnego rankingu. Sam kod nie oznacza uruchomionego serwera.

**Zgłaszanie pseudonimów:** po przytrzymaniu wpisu w rankingu globalnym można zgłosić nick do moderacji. Wydawca otrzymuje publiczne ID zgłaszanego gracza i powód („obraźliwy”); serwer zapisuje ponadto UUID tego gracza, publiczny nick w chwili zgłoszenia oraz datę. Zgłoszenie nie wymaga podawania konta zgłaszającego; jego IP jest czasowo wykorzystywane do limitowania zgłoszeń i może zostać zapisane w logach proxy. Zgłoszenia są dostępne tylko administratorowi i służą rozpatrywaniu nadużyć, nie reklamie.

**Publiczne pseudonimy i zgłaszanie naruszeń:** jeżeli własny serwer rankingu zostanie uruchomiony, inni gracze mogą zobaczyć wybrany przez Ciebie pseudonim i wyniki. Identyfikator widoczny publicznie jest inny niż wewnętrzny UUID instalacji; sekret instalacji służy do uwierzytelniania własnych zgłoszeń i usuwania danych. Serwer może przechowywać skrót sekretu, nie sam sekret w postaci jawnej. Użytkownik może zgłosić wpis z rankingu przez przytrzymanie jego nazwy: przesyłane są publiczne ID zgłaszanego wpisu i powód zgłoszenia; administrator przegląda zgłoszenia i może moderować nazwy. Adres IP zgłaszającego może zostać użyty do limitowania zgłoszeń i zapisany w logach infrastruktury zgodnie z konfiguracją serwera. Funkcje zależą od rzeczywiście wdrożonej wersji serwera i aplikacji, nie od samej obecności ich projektu w repozytorium.

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

Przy włączonej kopii systemowej Android może zapisać **dwa pliki**: `profile.json` (w tym pseudonim, postęp, ustawienia oraz UUID i sekret własnego rankingu, jeśli gracz go włączył) i `history.json` (historia bitew). Wynika to z `backup_rules.xml` i `data_extraction_rules.xml` w aplikacji. Reguły nie obejmują m.in. `battle.snap`, kolejki wyników i żądań usunięcia, lokalnych powtórek ani raportów awarii. Transfer danych między urządzeniami na Androidzie 12+ także obejmuje te dwa pliki.

**Szyfrowanie zależy od wersji Androida i sposobu przenoszenia danych:**
- **Android 12 i nowszy (API 31+):** reguły chmurowego backupu wymagają możliwości szyfrowania po stronie klienta (`disableIfNoEncryptionCapabilities="true"`). Transfer bezpośrednio między urządzeniami ma odrębne reguły; nie jest automatycznie kopią E2E w chmurze.
- **Android 9–11 (API 28–30):** wpisy chmurowego backupu wymagają `clientSideEncryption`. Według Androida szyfrowanie end-to-end jest dostępne przy włączonej kopii i blokadzie ekranu (PIN, wzór lub hasło); niespełnienie warunku może uniemożliwić wykonanie takiej kopii.
- **Android 8/8.1 (API 26–27):** platforma **nie gwarantuje szyfrowania end-to-end**. Aplikacja ustawia w starszym XML warunek `clientSideEncryption`, ale starsze systemy nie obsługują tej ochrony na zasadach Androida 9+. Dostępność i zachowanie backupu na tych wersjach wymagają sprawdzenia na urządzeniu; nie zapewniamy, że kopia powstanie ani że będzie E2E.

Systemowy backup Androida jest **oddzielny** od synchronizacji Saved Games w Google Play Games; `history.json` nie należy do pliku profilu wysyłanego przez Saved Games. Wydawca nie uzyskuje automatycznie dostępu do kopii systemowej. Zasady i ograniczenia: https://developer.android.com/identity/data/autobackup oraz https://developer.android.com/privacy-and-security/risks/backup-best-practices.

## 8. Usuwanie danych i okres przechowywania

- **Dane lokalne:** funkcja „Resetuj postęp” usuwa postęp i rekordy, lecz **zachowuje pseudonim**. Aby usunąć również pseudonim i inne dane lokalne, wyczyść pamięć aplikacji w Androidzie lub odinstaluj grę. Kopia Androida może pozostawać dostępna według ustawień konta.
- **Dane własnego rankingu i zgłoszenia dotyczące konta:** wybierz **Ustawienia → Nowa tożsamość → Potwierdź**. Aplikacja zapisuje żądanie usunięcia wszystkich wyników starego UUID i pseudonimu na serwerze, a następnie nada nowy identyfikator. Gdy nie ma internetu, ponawia próbę po ponownym uruchomieniu, także po wyłączeniu wysyłania wyników. **Nowe UUID nie oznacza potwierdzonego usunięcia danych na serwerze.** Do czasu dostarczenia żądania wyniki mogą nadal być widoczne; nie odinstalowuj gry ani nie czyść jej pamięci, dopóki żądanie nie zostanie obsłużone. W razie wątpliwości skontaktuj się z wydawcą poprzez kontakt z sekcji 1. Usunięcie konta kasuje również zgłoszenia przypisane do jego UUID, natomiast wewnętrzna informacja o blokadzie UUID może zostać zachowana dla ochrony przed nadużyciami. Zgłoszenia o innych użytkownikach nie są usuwane przez zmianę tożsamości zgłaszającego, gdyż raport nie jest z nim powiązany. Lokalne kopie bezpieczeństwa są rotowane i po ich odtworzeniu operator musi powtórzyć późniejsze usunięcia.
- **Prośba e-mailowa (np. po utracie telefonu):** napisz na adres z sekcji 1 i podaj swój pseudonim dokładnie tak, jak jest widoczny w rankingu (także „Dowódca-XXXX”), oraz przybliżoną datę ostatniej gry. Operator odnajduje wpis po pseudonimie i usuwa wszystkie wyniki, pseudonim i zgłoszenia przypisane do tego UUID **w ciągu 30 dni** od otrzymania prośby. Jeżeli kilka wpisów ma ten sam pseudonim, możemy poprosić o dodatkowe informacje z gry (np. tryb i przybliżony wynik); nie prosimy o dane, których serwer nie przechowuje.
- **Okresy przechowywania danych własnego rankingu** (usuwane automatycznie raz na dobę):
  <!-- R5/d38: terminy = wartości domyślne serwera (CW_RETENTION_* w .env); do potwierdzenia przez właściciela przed uruchomieniem serwera. Zmiana w .env wymaga zmiany tutaj i w data_safety.md. -->
  - wyniki rankingu dnia i tygodnia: **90 dni** od dnia wyzwania (dla tygodnia: od jego końca);
  - gracz, który przez **24 miesiące** nie wysłał nowego wyniku: usuwany w całości (wszystkie wyniki, pseudonim, zgłoszenia); wyniki stałych rankingów (oblężenie bez końca, potyczka, kampania) są przechowywane do tego czasu albo do usunięcia na żądanie;
  - zgłoszenia pseudonimów: **180 dni** od zgłoszenia;
  - informacja o blokadzie UUID: do czasu zdjęcia blokady, a gdy konto nie istnieje — najdłużej 24 miesiące od blokady;
  - jednokierunkowe skróty adresów IP do dziennego limitu: do następnego dnia (UTC);
  - dziennik usunięć i dziennik działań administratora: **400 dni**. Zawierają wyłącznie jednokierunkowy skrót identyfikatora (nie UUID ani pseudonim), datę i rodzaj operacji; służą wyłącznie do ponownego wykonania usunięć po odtworzeniu kopii zapasowej oraz do rozliczalności moderacji;
  - kopie zapasowe bazy: **14 dni** na serwerze i **30 dni** w zaszyfrowanej kopii poza serwerem (w UE). Usunięte dane znikają z kopii najpóźniej po upływie tych okresów.
- **Dane Google Play Games:** przejdź do https://play.google.com/games/profile, wybierz ustawienia danych Gier Play i **usuń dane tej konkretnej gry**. Alternatywnie skorzystaj z: Sklep Play → profil → Ustawienia → Ogólne → Profil w Grach Play → Prywatność i ustawienia → Usuń profil i dane gier. Nazwy opcji mogą się zmieniać. Możliwe jest też usunięcie całego profilu Gier Play. Szczegóły: https://support.google.com/googleplay/answer/9130646?hl=pl.
- **Ważne:** własny ranking, profil lokalny, systemowa kopia Androida i dane PGS to cztery oddzielne miejsca. Reset lokalny nie usuwa danych własnego serwera ani PGS; synchronizacja PGS może przywrócić postęp.

Okres przechowywania danych przez Google jest określany przez Google i ustawienia konta użytkownika. Wydawca może przechowywać pseudonimowe dane rankingu opisane powyżej, jeśli opcjonalny serwer zostanie uruchomiony, najdłużej przez okresy podane w tej sekcji.

## 9. Ochrona danych i dzieci

Gra przechowuje lokalne dane w prywatnej przestrzeni aplikacji Android. Przy aktywnych rankingach publicznie widoczne mogą być pseudonimy i wyniki PGS oraz osobnego serwera wydawcy; użytkownik nie ma możliwości publikowania postów ani wysyłania wiadomości. Obraźliwe pseudonimy w rankingu globalnym można zgłosić do moderacji. Nie udostępnia czatu, wiadomości między graczami ani lokalizacji. Jedynie raport techniczny można ręcznie udostępnić przez systemowe okno Androida. Rankingi PGS mogą jednak pokazywać nazwy profili Google i wyniki zgodnie z wybraną widocznością. Klasyfikacja wieku jest ustalana na podstawie ankiety IARC, a nie stanowi automatycznej deklaracji kierowania produktu do dzieci.

## 10. Zmiany polityki

Przed aktywacją własnego rankingu w opublikowanym wydaniu lub dodaniem nowych sposobów przetwarzania danych (np. reklam albo zakupów) zweryfikujemy politykę i informacje w Google Play.

_Wersja angielska: /store/privacy-policy-en/._

**Podstawy opisu usług i ochrony danych (zweryfikowane 2 listopada 2026 r.):** [Play Games SDK](https://developer.android.com/games/pgs/data-collection), [Google Play — polityka danych użytkownika](https://support.google.com/googleplay/android-developer/answer/10144311), [formularz Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [Android Auto Backup](https://developer.android.com/identity/data/autobackup).
