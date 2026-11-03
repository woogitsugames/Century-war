# OWNER_INPUT — dane właściciela do wydania Century War

Ten plik jest **jedynym źródłem danych właściciela** dla dokumentów sklepu i polityk prywatności. Zastąp każdą wartość `WYMAGA_DANYCH_WLASCICIELA` rzeczywistymi informacjami, zachowując nazwy kluczy i układ tabeli. Nie wpisuj haseł, klucza JKS ani sekretów HMAC.

| Pole | Wartość |
| --- | --- |
| `publisher_name` | WYMAGA_DANYCH_WLASCICIELA |
| `contact_email` | WYMAGA_DANYCH_WLASCICIELA |
| `postal_address` | WYMAGA_DANYCH_WLASCICIELA |
| `privacy_policy_url` | WYMAGA_DANYCH_WLASCICIELA |
| `release_date` | WYMAGA_DANYCH_WLASCICIELA |

## Jak wypełnić pięć pól

- `publisher_name` — **pełna nazwa prawna wydawcy** widniejąca w dokumentach rejestracyjnych i danych konta Google Play. Marka „WooGitsu Games” sama nie zastępuje nazwy prawnej.
- `contact_email` — **działający e-mail do spraw prywatności i usuwania danych**; powinien być regularnie obsługiwany. To nie hasło ani login.
- `postal_address` — **adres do korespondencji wydawcy**, który może zostać opublikowany w polityce prywatności. Musi być rzeczywisty i zgodny z danymi właściwego podmiotu.
- `privacy_policy_url` — **publiczny adres HTTPS** strony polityki (bez logowania, nie plik PDF i nie dokument, który każdy może edytować). Udostępnij również czytelną wersję polską i angielską, połączoną odsyłaczami. Ten sam główny adres ustaw w `CW_PRIVACY_URL` podczas budowania wydania.
- `release_date` — **ustalona data publikacji** w formacie `RRRR-MM-DD`. Nie wpisuj tymczasowej daty tylko dla przejścia kontroli. Osobno koordynator musi ustalić stałą `Daily.RELEASE_DAY` (obecna wartość 2027-12-01 jest zastępcza).

**Nie dodawaj nowych wierszy z kluczami do tabeli.** Narzędzie publikacji odczytuje dokładnie pięć istniejących pól. `WYMAGA_DANYCH_WLASCICIELA` znaczy, że przed wydaniem właściciel musi podać prawdziwą wartość.

## Pozostałe rzeczy, które musi dostarczyć lub zatwierdzić właściciel

Te pozycje **nie są dodatkowymi polami tabeli**. Przekaż je koordynatorowi wydania albo skonfiguruj we wskazanym miejscu:

- [ ] **Adres własnego serwera rankingowego:** produkcyjny adres `https://` wpisywany jako `CW_SERVER_URL`. Jeżeli serwer nie będzie uruchomiony przy premierze, potwierdź tę decyzję i sposób wyłączenia funkcji/zwolnienia odpowiedniej bramki wydania. Nie twierdź w polityce, że serwer już działa bez testu.
- [ ] **Bezpieczna konfiguracja serwera:** `CW_HMAC_KEY` i klucze do podpisywania aplikacji przekazywane wyłącznie kanałem sekretów CI; **nigdy** do tego pliku ani do repozytorium. Zatwierdź hosting, logowanie IP, czas przechowywania i procedurę usuwania danych.
- [ ] **Google Play Games:** skonfiguruj w Play Console i wpisz w [`play_games_ids.properties`](play_games_ids.properties) **79 identyfikatorów: 1 projekt, 4 rankingi i 74 osiągnięcia**. Nie wpisuj ich do tabeli OWNER_INPUT. Uruchom i sprawdź logowanie, wyniki, osiągnięcia i Saved Games na urządzeniu z podpisaną aplikacją.
- [ ] **Konto deweloperskie:** sprawdź zgodność nazwy prawnej, adresu, e-maili i danych weryfikacyjnych z Google Play Console (w tym D-U-N-S, jeśli wymaga go dane konto organizacji).
- [ ] **Publikacja i zgody:** wybierz kraje oraz grupę wiekową, wypełnij ankietę IARC i zatwierdź [`data_safety.md`](data_safety.md) dla rzeczywistego pakietu AAB. Sprawdź, że w grze działa link do polityki i że można zgłaszać żądania usunięcia.
- [ ] **Dwa języki polityki:** sprawdź poprawność swoich danych, obu wersji PL/EN, publiczną dostępność URL oraz rzeczywistą obsługę żądań prywatności; potwierdź przed publikacją.
- [ ] **Data premiery:** poinformuj koordynatora o ustalonej dacie, aby aktualizował datę publikacji i `Daily.RELEASE_DAY` — to dwie różne wartości o innym zastosowaniu.

## Walidacja i publikowanie

1. Uzupełnij **wszystkie pięć pól** tabeli prawdziwymi wartościami. Nie przechowuj haseł, kluczy JKS, sekretów HMAC ani skanów dokumentów w repo.
2. Uruchom `./gradlew :app:renderStorePolicies --console=plain`. Zadanie sprawdzi dane i wygeneruje `build/store-policies/privacy_policy_pl.md` oraz `privacy_policy_en.md` z podstawionymi danymi wydawcy.
3. Opublikuj **wygenerowane** polityki (nie szablony z `docs/store/`) pod publicznymi adresami HTTPS. Sprawdź obie wersje, działający kontakt i możliwość dotarcia z aplikacji do polityki.
4. Zweryfikuj zgodność dokumentów z rzeczywistym AAB i uruchom `./gradlew :app:releaseChecklist` oraz testy przed publikacją.

`releaseChecklist` blokuje wydanie przy brakujących lub niepoprawnych danych. Wyjątek dla wpisów właściciela jest przewidziany wyłącznie dla jasno oznaczonego smoke-testu z podpisem debug w CI; nie można go użyć do normalnego wydania.

## Aktualny status decyzji (2.11.2026)

- **Nieustalone:** nazwa prawna wydawcy, e-mail, adres, URL polityki i ostateczna data premiery — wszystkie pięć pól pozostaje do wypełnienia.
- **Niepotwierdzone w produkcji:** adres własnego serwera rankingu, jego hosting i retencja; właściciel musi zdecydować, czy włącza go przy premierze.
- **Google Play Games:** integracja kodowa obejmuje 4 rankingi, synchronizację osiągnięć i Saved Games, ale identyfikatory w pliku konfiguracyjnym nadal są zastępcze. Ich uruchomienie w sklepie wymaga konfiguracji i testu.
- **Data premiery w kodzie:** `Daily.RELEASE_DAY` = 2027-12-01 jest wartością do podjęcia decyzji, nie potwierdzoną datą wydania.
