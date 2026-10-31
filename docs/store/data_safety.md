# Google Play Console — formularz „Bezpieczeństwo danych”

**Stan:** 31 października 2026. Odpowiedzi dotyczą wyłącznie bazowej wersji Century War bez aktywnych usług sieciowych. Przed publikacją sprawdź końcowy plik AAB po scaleniu innych prac.

## Potwierdzone w kodzie

- Profile.kt zapisuje pseudonim, postęp, badania, ustawienia i wyniki lokalnie (profile.json w prywatnej pamięci aplikacji).
- BattleSnapshot.kt zapisuje lokalnie zrzut przerwanej bitwy (battle.snap) do wznowienia; plik jest usuwany po zakończeniu bitwy.
- OnlineLeaderboards.kt udostępnia wyłącznie domyślną implementację Local; Game.kt jej nie zastępuje.
- AndroidManifest.xml zawiera uprawnienie VIBRATE, nie zawiera INTERNET ani AD_ID.
- W app/build.gradle.kts nie ma SDK reklamowych, analityki, Google Play Games ani Billing.
- android:allowBackup="true" dopuszcza systemowe kopie zapasowe Androida zależne od konfiguracji urządzenia; to osobny mechanizm, nie własny serwer gry. Reguły backup_rules.xml / data_extraction_rules.xml ograniczają kopię do profile.json (battle.snap jest pomijany).

## Proponowane odpowiedzi — wersja offline v1

| Pytanie / sekcja Play Console | Odpowiedź | Wyjaśnienie |
|---|---|---|
| Czy aplikacja zbiera dane użytkownika? | **Nie** | Dane pozostają w pamięci aplikacji. |
| Czy aplikacja udostępnia dane użytkownika innym podmiotom? | **Nie** | Nie ma transferów przez SDK ani usługę gry. |
| Kategorie zbieranych i udostępnianych danych | **Żadna** | Pseudonim i wyniki są lokalne, nie są „zbierane” przez wydawcę. |
| Szyfrowanie danych w transmisji | **Nie dotyczy** | Pytanie pojawia się warunkowo, gdy dane są przesyłane. |
| Możliwość żądania usunięcia danych serwerowych | **Nie dotyczy** | Brak kont, serwera i danych u wydawcy. |
| Reklamy („Ads”, osobna deklaracja) | **Nie** | Brak reklam w v1, również z nagrodą. |
| Zakupy w aplikacji | **Nie** | Brak Play Billing. |
| Tworzenie konta / logowanie | **Nie** | Brak logowania lub rejestracji w v1. |

**Ważne:** formularz Data safety i publiczna polityka prywatności są wymagane również wtedy, gdy odpowiedź brzmi „Nie zbieramy danych”.

## Lista kontrolna przed wysłaniem AAB

1. Sprawdź końcowy scalony manifest, zależności transytywne, ruch sieciowy i wszystkie dostępne ścieżki funkcji. Deklaracja musi obejmować wszystkie wersje aplikacji aktywnie dystrybuowane w Google Play.
2. Uzupełnij pełną nazwę prawną wydawcy i e-mail w obu politykach prywatności. Opublikuj wybraną politykę jako publiczną stronę HTTPS bez logowania (np. GitHub Pages), a następnie dodaj jej URL w Play Console.
3. Dodaj link do polityki prywatności **wewnątrz aplikacji**. Ekrany są wyłączone z edycji w tym zleceniu — konieczna późniejsza zmiana w ustawieniach lub menu.
4. Nie przedstawiaj „Resetuj postęp” jako usunięcia całych danych: Profile.reset() zachowuje pseudonim. Pełne usunięcie danych lokalnych następuje po wyczyszczeniu pamięci aplikacji albo odinstalowaniu; kopie Androida podlegają osobnym ustawieniom.
5. Ponów audyt po dodaniu dowolnego SDK; nie przepisuj odpowiedzi v1 bez sprawdzenia zmienionego kodu.

## Po dodaniu rankingów, reklam lub zakupów — NIE stosować do v1

| Funkcja | Potencjalne dane opuszczające urządzenie | Dalsze działania |
|---|---|---|
| Własny serwer rankingów | Pseudonim, losowe UUID, wynik, identyfikator wyzwania, czas zgłoszenia; ewentualne IP w logach | Ujawnij właściwe kategorie danych, cel przetwarzania, opcjonalność, czas retencji i procedurę usuwania; użyj TLS. |
| Google Play Games Services | Identyfikator i nick konta gracza, wyniki oraz dane przesyłane przez SDK | Przejrzyj aktualną dokumentację Data safety Google oraz realne transfery. |
| AdMob z mediacją | Zależnie od SDK: identyfikatory, zdarzenia reklamowe i dane partnerów mediacyjnych | „Zawiera reklamy” = Tak; audyt adapterów i zgód (w tym EOG) oraz aktualizacja Data safety. |
| Google Play Billing | Identyfikatory zakupów, historia uprawnień / potwierdzenia (jeśli aplikacja lub backend je odbiera) | Sprawdź rzeczywiste przepływy i zakres danych Google; nie zakładaj automatycznie braku zbierania. |

UUID jest co do zasady **identyfikatorem pseudonimowym**, a nie dowodem pełnej anonimizacji. Nie deklaruj szyfrowania bez wdrożonego HTTPS ani możliwości usuwania danych bez konkretnej procedury.

## Oficjalne materiały

- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/9859455
- https://developer.android.com/identity/data/autobackup
