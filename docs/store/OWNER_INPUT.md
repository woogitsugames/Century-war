# OWNER_INPUT — dane właściciela do wydania Century War

Ten plik jest **jedynym źródłem danych właściciela** dla dokumentów sklepu i polityk prywatności. Zastąp każdą wartość `WYMAGA_DANYCH_WLASCICIELA` rzeczywistymi informacjami, zachowując nazwy kluczy i układ tabeli. Nie wpisuj haseł, klucza JKS ani sekretów HMAC.

| Pole | Wartość |
| --- | --- |
| `publisher_name` | WYMAGA_DANYCH_WLASCICIELA |
| `contact_email` | WYMAGA_DANYCH_WLASCICIELA |
| `postal_address` | WYMAGA_DANYCH_WLASCICIELA |
| `privacy_policy_url` | WYMAGA_DANYCH_WLASCICIELA |
| `release_date` | WYMAGA_DANYCH_WLASCICIELA |
| `play_games_project_id` | WYMAGA_DANYCH_WLASCICIELA |

## Znaczenie pól

- `publisher_name` — pełna nazwa prawna podmiotu publikującego grę (nie sama marka WooGitsu Games).
- `contact_email` — działający adres kontaktowy w sprawach prywatności i usuwania danych.
- `postal_address` — adres korespondencyjny wydawcy wymagany w opublikowanej polityce (sprawdź wymogi Google Play dla konta dewelopera).
- `privacy_policy_url` — publiczny adres HTTPS opublikowanej polityki, np. polskiej lub głównej wersji wielojęzycznej. Ten sam adres ustaw jako `CW_PRIVACY_URL` w konfiguracji release.
- `release_date` — planowana i zatwierdzona data publikacji, dokładnie `RRRR-MM-DD` w UTC; służy też do datowania polityk. Ustal osobno niezmienną wartość `Daily.RELEASE_DAY`, zgodnie z `docs/RELEASE.md`.
- `play_games_project_id` — numeryczny identyfikator projektu Play Games (nie OAuth Client ID ani nazwa). Wpisz go również do `games_ids.xml` wraz z ID czterech rankingów.

## Walidacja i publikowanie

1. Uzupełnij wszystkie sześć pól rzeczywistymi danymi.
2. Uruchom `./gradlew :app:renderStorePolicies --console=plain`. Zadanie sprawdzi dane i wytworzy `build/store-policies/privacy_policy_pl.md` oraz `privacy_policy_en.md`, z podstawioną nazwą, adresem i e-mailem wydawcy.
3. Opublikuj **wygenerowane** polityki (nie szablony z `docs/store/`) pod docelowym URL, sprawdź ich publiczną dostępność oraz zgodność wersji PL/EN.
4. Uruchom `./gradlew :app:releaseChecklist` i kompletne testy przed tworzeniem AAB.

`releaseChecklist` blokuje wydanie, jeśli plik nie istnieje, którekolwiek pole jest niewypełnione albo ma niepoprawny podstawowy format. Pominięcie danych właściciela jest możliwe **wyłącznie** w podpisywanym debugowo smoke-teście CI i nigdy podczas publikacji.

## Decyzje właściciela (1.11.2026)

- Data premiery: jeszcze nieustalona (`Daily.RELEASE_DAY` pozostaje 2027-12-01 do czasu decyzji).
- Wydawca: firma / JDG (konto organizacji w Play Console, wymagany D-U-N-S); nazwa prawna i adres do uzupełnienia.
- Polityka prywatności: miejsce publikacji do ustalenia później.
- Google Play Games: włączone. Potrzebne: ID projektu Play Games i ID czterech rankingów do `games_ids.xml`.
