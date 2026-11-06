# Jak zlecić pracę zewnętrznemu modelowi (GPT i inne)

Ściąga dla właściciela. Nie trzeba nic programować — wystarczy przeglądarka.

## Zlecenia do wysłania (paczka 166–169)

| Nr | Co robi | Plik zlecenia | Gałąź, którą ma wypchnąć |
|---|---|---|---|
| 166 | Audyt kodu (sam raport) | [166-code-audit.md](https://github.com/woogitsugames/century-war/blob/main/docs/tasks/166-code-audit.md) | `gpt/166-code-audit` |
| 167 | Przegląd tłumaczeń 10 języków | [167-l10n-native.md](https://github.com/woogitsugames/century-war/blob/main/docs/tasks/167-l10n-native.md) | `gpt/167-l10n-native` |
| 168 | Teksty marketingowe | [168-marketing.md](https://github.com/woogitsugames/century-war/blob/main/docs/tasks/168-marketing.md) | `gpt/168-marketing` |
| 169 | Recenzja projektu gry (sam raport) | [169-design-review.md](https://github.com/woogitsugames/century-war/blob/main/docs/tasks/169-design-review.md) | `gpt/169-design-review` |

Linki działają, gdy koordynator scali te pliki na `main`. Jeśli link pokazuje „404”, podmień w nim `blob/main/` na `blob/claude/paczki-ext/`.

Każde zlecenie to **osobna sesja** (jedno zlecenie = jedna rozmowa). Można je wysłać naraz — nie przeszkadzają sobie.

## 1. Uruchom sesję
1. Otwórz nową sesję modelu, który ma dostęp do GitHuba i może wypychać gałęzie (np. tryb agenta/kodowania z podłączonym repozytorium `woogitsugames/century-war`).
2. Wklej taką wiadomość (podmień numer i link):

   > Wykonaj zlecenie z pliku https://github.com/woogitsugames/century-war/blob/main/docs/tasks/166-code-audit.md w repozytorium woogitsugames/century-war. Przeczytaj cały plik i trzymaj się go dokładnie: utwórz gałąź podaną w zleceniu z `main`, wypchnij ją na GitHub, nie otwieraj Pull Requesta. Na koniec podaj nazwę gałęzi i hash ostatniego commita.

3. Jeśli model nie umie otworzyć linku, otwórz plik w przeglądarce, kliknij „Raw” (lub ikonę kopiowania), skopiuj **całą** treść i wklej ją zamiast linku.
4. Jeśli model pyta o coś po drodze, a nie wiesz — odpowiedz: „Zdecyduj sam zgodnie ze zleceniem i opisz decyzję w raporcie”.

## 2. Sprawdź, że wypchnął gałąź
1. Wejdź na https://github.com/woogitsugames/century-war/branches i wpisz w wyszukiwarkę `gpt/166` (albo inny numer).
2. Gałąź powinna być widoczna, z datą sprzed kilku minut. Kliknij ją i sprawdź, że jest w niej plik wyniku:
   - 166: `docs/reports/audit-166.md`,
   - 167: zmiany w `app/src/main/java/com/centurywar/engine/L.kt` i `docs/reports/l10n-167.md`,
   - 168: katalog `docs/store/marketing/`,
   - 169: `docs/reports/design-review-169.md`.
3. Jeśli gałęzi nie ma — napisz modelowi: „Nie widzę gałęzi na GitHubie. Wypchnij ją poleceniem `git push -u origin <nazwa gałęzi>` i podaj wynik”.

## 3. Przekaż koordynatorowi
Napisz w sesji koordynatora (Claude): „Gałąź `gpt/166-code-audit` gotowa” (lub kilka naraz). **Koordynator sam przegląda, testuje i scala** gałęzie do `main` — Ty niczego nie scalasz i nie zatwierdzasz na GitHubie. Jeśli model mimo zakazu otworzył Pull Request, nic nie szkodzi — powiedz o tym koordynatorowi.
