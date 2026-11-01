# Century War — od konta firmowego do publikacji w Google Play Console

**Stan wymagań Google: sprawdzono 1 listopada 2026 r.** Dotyczy pierwszego wydania Androida, `applicationId=com.centurywar.game`, na **koncie organizacji (firma / JDG)**. Kolejność ekranów w konsoli może się zmienić; przy rozbieżności należy kierować się bieżącymi zadaniami na Dashboardzie. Linki w sekcjach prowadzą do **oficjalnych źródeł Google**, sprawdzonych tego dnia.

> **Najważniejsze rozróżnienie:** obowiązek **co najmniej 12 testerów przez 14 kolejnych dni** przed wnioskiem o dostęp do produkcji dotyczy **nowych kont osobistych utworzonych po 13.11.2023 r.**, a nie automatycznie kont organizacji. Dla firmy zalecamy mimo to test wewnętrzny i zamknięty. Źródła: [wymagania testowania kont osobistych](https://support.google.com/googleplay/android-developer/answer/14151465), [konfiguracja torów testowych](https://support.google.com/googleplay/android-developer/answer/9845334), [tworzenie aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152).

## 0. Szybka decyzja przed założeniem konta

1. **Wydawcą ma być rzeczywista firma/JDG, nie marka.** Wybierz **Organization** dla działalności zawodowej/gospodarczej i użyj **legalnej nazwy i adresu** podmiotu. Sama nazwa wyświetlana „WooGitsu Games” nie zastępuje danych prawnych. W przypadku JDG zweryfikuj, czy Dun & Bradstreet ma poprawny wpis Twojej działalności; nie zakładaj, że numer osoby fizycznej albo innej spółki wystarczy. [Typ konta](https://support.google.com/googleplay/android-developer/answer/13634885), [dane organizacji](https://support.google.com/googleplay/android-developer/answer/13628312).
2. **D-U-N-S:** sprawdź wpis w [Dun & Bradstreet](https://www.dnb.com/duns.html); w razie braku wystąp o numer bezpłatnie, z wyprzedzeniem (Google podaje możliwość oczekiwania **do 30 dni**). Numer, legalna nazwa i adres muszą odpowiadać profilowi płatności Google i rejestrowi D&B. Wyjątki dotyczą m.in. określonych organów państwowych i regionów nieobsługiwanych przez D&B; zwykła firma/JDG nie jest automatycznie zwolniona. [Wymagane dane](https://support.google.com/googleplay/android-developer/answer/13628312), [weryfikacja tożsamości](https://support.google.com/googleplay/android-developer/answer/10841920).
3. **Przygotuj dokumenty:** dokument rejestracyjny firmy (w Belgii np. aktualny dokument z BCE/KBO potwierdzający dane podmiotu, o ile Google zaakceptuje jego rodzaj), dokument tożsamości upoważnionego przedstawiciela i dostęp do telefonu oraz e-maili. To Google wskazuje akceptowane typy dokumentów; polski NIP czy belgijski numer przedsiębiorstwa nie zastępuje D-U-N-S. [Weryfikacja](https://support.google.com/googleplay/android-developer/answer/10841920), [dane organizacji](https://support.google.com/googleplay/android-developer/answer/13628312).
4. **Witryna organizacji:** przygotuj własną działającą domenę i zweryfikuj ją w **Google Search Console**; w razie żądania w Play Console wyślij prośbę o powiązanie witryny. Konto będące właścicielem witryny w Search Console może automatycznie zatwierdzić powiązanie, w przeciwnym razie akceptuje je właściciel. Sama strona społecznościowa nie zastępuje witryny. [Weryfikacja witryny](https://support.google.com/googleplay/android-developer/answer/13205715), [weryfikacja konta](https://support.google.com/googleplay/android-developer/answer/10841920).
5. **Kontakty:** przygotuj wewnętrzne e-mail i telefon do Google, osobne publiczne e-mail i telefon wydawcy (oba weryfikowane), nazwę i telefon organizacji oraz e-mail pomocy dla graczy. **Na profilu organizacji mogą być publiczne legalna nazwa, adres, e-mail i telefon.** Sprawdź je przed zatwierdzeniem. [Dane konta](https://support.google.com/googleplay/android-developer/answer/13628312), [weryfikacja kontaktów](https://support.google.com/googleplay/android-developer/answer/10841920).

**Nie wpisuj danych podmiotu „na próbę”:** nazwa i adres podmiotu muszą być spójne z profilem Google Payments oraz D&B. Gdy dane się rozchodzą, najpierw je uzgodnij. Źródło: [dane do utworzenia konta](https://support.google.com/googleplay/android-developer/answer/13628312).

## 1. Rejestracja konta organizacji

1. Wejdź na [Google Play Console](https://play.google.com/console/signup), zaloguj się na docelowe konto Google, zaakceptuj umowę deweloperską i zapłać **jednorazową opłatę rejestracyjną 25 USD** (kwota w walucie rozliczeniowej może się różnić). Zachowaj potwierdzenie płatności. [Rejestracja Play Console](https://support.google.com/googleplay/android-developer/answer/6112435).
2. Zaznacz **Organization**, utwórz/połącz **organizacyjny** profil Google Payments, podaj D-U-N-S, prawidłowe dane prawne, przedstawiciela, domenę i kontakty. Nazwa wydawcy widoczna w sklepie może być marką, ale dane prawne muszą odpowiadać rejestracji. [Typ konta](https://support.google.com/googleplay/android-developer/answer/13634885), [wymagane dane](https://support.google.com/googleplay/android-developer/answer/13628312).
3. Zakończ weryfikację tożsamości i firmy, powiąż witrynę przez Search Console, potwierdź publiczny i prywatny e-mail/telefon kodami. Sprawdź Dashboard, czy nie ma nierozwiązanych etapów; **weryfikacja konta jest potrzebna przed przesłaniem aplikacji do oceny**. [Weryfikacja konta](https://support.google.com/googleplay/android-developer/answer/10841920), [witryna](https://support.google.com/googleplay/android-developer/answer/13205715).
4. Ogranicz uprawnienia współpracowników do niezbędnych; konto właściciela i dostęp do uwierzytelniania dwuskładnikowego pozostają pod kontrolą wydawcy. To zalecenie organizacyjne, nie twierdzenie o warunku certyfikacji Google.

**Do wpisania przez właściciela:** legalna nazwa podmiotu, dokładny adres i D-U-N-S, domena, publiczny e-mail/telefon, prywatny e-mail/telefon, dane przedstawiciela. Nie umieszczaj skanów dokumentów ani D-U-N-S w publicznym repo, jeśli nie ma takiej konieczności.

## 2. Utworzenie aplikacji i karty sklepu

1. **Home → Create app:** nazwa „Century War: Wojna epok” (skontroluj limit znaków), domyślny język, **Game**, **Free**, kontakt pomocy, oświadczenia zasad i eksportu, warunki Play App Signing. **Nie wybieraj Paid** dla darmowego v1. Pakiet z kodu to `com.centurywar.game`; po pierwszym przesłaniu artefaktu identyfikator pakietu jest związany z aplikacją. [Tworzenie aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152), [test wewnętrzny i pakiet](https://support.google.com/googleplay/android-developer/answer/9845334).
2. **Store presence / Main store listing:** wpisz tytuł, krótki i pełny opis z [listing_pl.md](listing_pl.md) i [listing_en.md](listing_en.md), po uprzednim usunięciu nagłówków redakcyjnych i sekcji ASO. Dodaj ikonę, grafikę promocyjną oraz prawdziwe zrzuty ze zgodnego wydania, kierując się [STORE_GRAPHICS.md](STORE_GRAPHICS.md), [screenshots_plan.md](screenshots_plan.md) i [ICON_SPLASH.md](ICON_SPLASH.md). Sprawdź limity pól i formatów w konsoli. [Tworzenie aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152), [materiały sklepu](https://support.google.com/googleplay/android-developer/answer/9866151).
3. **Category / Tags:** jako gra strategiczna; dobieraj wyłącznie zgodne kategorie i tagi z aktualnie udostępnionej listy. [Kategorie i tagi](https://support.google.com/googleplay/android-developer/answer/9859673).
4. **Pricing and distribution / Countries/regions:** wybierz bezpłatną dystrybucję i rzeczywiście obsługiwane kraje. **Nie myl darmowej aplikacji z brakiem obowiązków dotyczących danych.** [Tworzenie aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

**Fakty z repo (gałąź bazowa, 1.11.2026):** `app/build.gradle.kts` deklaruje `applicationId=com.centurywar.game`, `minSdk=26`, `targetSdk=36`, `compileSdk=36`, zależność `play-services-games-v2:22.1.0`. Nie deklaruje SDK reklam ani Billing. Ostateczną listę uprawnień trzeba jednak sprawdzić w **scalonym manifeście wydania**; samo źródłowe `AndroidManifest.xml` nie uwzględnia wszystkich możliwych wpisów zależności.

## 3. App content — gotowe wskazówki do deklaracji

Otwórz **Monitor and improve → Policy and programs → App content** (lub analogiczne zadania Dashboardu). Google wymaga dokładnych oświadczeń o zawartości, reklamach, dostępie, grupie docelowej, klasyfikacji i bezpieczeństwie danych; wypełniaj je **dla końcowego AAB**, a nie dla planów rozwoju. [App content](https://support.google.com/googleplay/android-developer/answer/9859455).

| Sekcja konsoli | Odpowiedź robocza dla obecnej gry | Warunek potwierdzenia / plik repo |
|---|---|---|
| **Ads / Zawiera reklamy** | **No** — w v1 brak banerów, reklam wideo, AdMob i innego SDK reklamowego. | `app/build.gradle.kts`, [monetization_plan.md](monetization_plan.md); sprawdź finalny AAB i ewentualną promocję własnych aplikacji w grze. [Google: Ads](https://support.google.com/googleplay/android-developer/answer/9859455). |
| **In-app products / Zakupy** | **Brak zakupów w v1** — brak Google Play Billing, ofert IAP i subskrypcji. | `app/build.gradle.kts`, [monetization_plan.md](monetization_plan.md); plan monetyzacji to **przyszłość**, nie wdrożona funkcja. [Google: przygotowanie aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152). |
| **App access / Dostęp recenzenta** | Główna gra działa **bez logowania**. PGS i ranking online są opcjonalne/warunkowe; opisuj działające funkcje i zapewnij instrukcję dostępu testerowi, gdy konfiguracja PGS ogranicza mu wejście do funkcji. | [PLAY_GAMES_SETUP.md](../PLAY_GAMES_SETUP.md), `PlayGamesLeaderboards.kt`. Jeżeli recenzent musi być dopisany do testerów PGS, nie wybieraj bezwarunkowo „all functionality accessible”. [Google: App access](https://support.google.com/googleplay/android-developer/answer/9859455). |
| **Advertising ID** | Własny kod nie używa ID reklamowego; **wybór „No” tylko jeśli finalny scalony manifest NIE zawiera `com.google.android.gms.permission.AD_ID` i żaden SDK nie wykorzystuje AAID**. | `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `data_safety.md`; jeśli uprawnienie zostanie dodane transytywnie, sprawdź jego pochodzenie/wykorzystanie przed deklaracją. [Google: Advertising ID](https://support.google.com/googleplay/android-developer/answer/6048248). |
| **Data safety** | **Yes — dane są zbierane w scenariuszu z PGS**; nie wybieraj „No data collected” z powodu działania offline. Szczegóły poniżej. | [data_safety.md](data_safety.md), [privacy_policy_pl.md](privacy_policy_pl.md), [privacy_policy_en.md](privacy_policy_en.md). [Google: Data safety](https://support.google.com/googleplay/android-developer/answer/10787469). |
| **Privacy policy** | URL publicznej polityki **HTTPS**, dostępnej globalnie bez logowania, także **link lub tekst w aplikacji**. | [OWNER_INPUT.md](OWNER_INPUT.md); w [data_safety.md](data_safety.md) wskazano brak potwierdzonego linku w SettingsScreen — **blokada publikacji do naprawienia innym zleceniem**. [Google: User data](https://support.google.com/googleplay/android-developer/answer/10144311). |
| **Target audience and content** | Propozycja: **16–17 oraz 18+**, jeśli faktycznie do tych grup zaprojektowano marketing i grę; nie deklaruj automatycznie odbiorców młodszych niż 13. | Ostateczna decyzja wydawcy po obejrzeniu finalnych materiałów; Google ostrzega, że 16–17 w części jurysdykcji to nadal dzieci i mogą pojawić się obowiązki Families. [Google: Target audience](https://support.google.com/googleplay/android-developer/answer/9867159). |
| **Content rating (IARC)** | **Game**, przemoc **Tak**, stylizowana/kreskówkowa walka z postaciami i bronią; bez zakładania z góry PEGI 7/12/16. | [content_rating.md](content_rating.md) i finałowy ekran bitwy. Weryfikuj każdą odpowiedź; rating nadaje IARC. [Google: klasyfikacja](https://support.google.com/googleplay/android-developer/answer/9859655). |

### 3.1. Ankieta IARC — sugerowane odpowiedzi (nie dosłowny układ pól)

- **Przemoc / broń:** **tak**, regularna walka kreskówkowych wojowników, broni białej, dystansowej, artylerii, wież, zniszczenie baz i śmierć jednostek. Nie zaznaczaj „brak przemocy” tylko dlatego, że grafika jest rysowana.
- **Realizm, krew/gore, rany:** według [content_rating.md](content_rating.md) brak realistycznej krwi/gore; **przed wysłaniem obejrzyj animacje ze wszystkich epok i bossów**. Jeśli zawartość się zmieni, odpowiedź należy zaktualizować.
- **Horror, seks, narkotyki, hazard i płatne losowania:** według aktualnej dokumentacji **nie**, po kontroli końcowej grafiki i tekstów.
- **Czat / bezpośredni multiplayer:** **nie** — nie ma prywatnych wiadomości ani wspólnej bitwy na żywo.
- **Treści i interakcje użytkowników:** **nie zaznaczaj automatycznie „brak”**. PGS pokazuje publiczne nazwy/awatary i wyniki, a aktywowany własny ranking może udostępniać nicki. Pytania IARC o „Users Interact” i „User Generated Content” interpretuj zgodnie z ich definicjami i faktycznie działającym rankingiem. Przycisk zgłaszania nicków ze zlecenia 80 uwzględnij **tylko jeśli został scalony i przetestowany**.

Każde nowe wydanie z inną przemocą, publicznymi treściami, reklamami albo zakupami wymaga ponownej oceny zgodności odpowiedzi. Nie ustalaj samodzielnie końcowej kategorii wiekowej: wynik zależy od formularza i regionu. Źródła: [Google — Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655), [zasady klasyfikacji](https://support.google.com/googleplay/android-developer/answer/9898843); szczegóły gry: [content_rating.md](content_rating.md).

### 3.2. Grupa docelowa ≠ ocena PEGI

Dla strategii z regularną przemocą rysunkową i złożonym sterowaniem wstępnie rozważ **16–17 + 18+**; jest to **rekomendacja redakcyjna**, a nie decyzja Google ani sposób na ominięcie zasad Families. Nie kieruj marketingu do dzieci, jeśli aplikacja nie spełnia zasad dla dzieci. **13–15** dodaj tylko, jeśli rzeczywiście projektujesz produkt także dla tej grupy i przeanalizowano wymagania krajowe oraz Play Families. Google osobno weryfikuje deklarowaną grupę i wynik IARC; mogą być różne. [Google — target audience i Families](https://support.google.com/googleplay/android-developer/answer/9867159).

### 3.3. Data safety — nie wypełniać wyłącznie „nie”

Przepisz kategorie i warunki z [data_safety.md](data_safety.md). Przy skonfigurowanym Play Games v2 należy co najmniej ocenić następujące kategorie:

| Typ danych do rozważenia | Faktyczny kanał w projekcie |
|---|---|
| **User IDs; Name / pseudonim** | Identyfikator profilu PGS oraz nazwa/awatar; w wariancie z własnym serwerem także lokalny UUID i publiczny pseudonim. |
| **App activity / inne działania** | Wyniki rankingów, stan postępu, kopia profilu Saved Games; przy własnym serwerze metadane i weryfikacja powtórki. |
| **User-generated content** | Pseudonimy rankingów i ewentualne zgłoszenia nicków po integracji zlecenia 80. |
| **App interactions, diagnostics, crash logs, device IDs** | Sprawdź deklarację zbierania danych SDK Play Games **22.1.0** i finalny AAB. Crash logi własnej aplikacji w obecnym opisie są lokalne do momentu ręcznego udostępnienia. |
| **Udostępnianie / sharing; szyfrowanie; usuwanie** | Weryfikuj według definicji Google: PGS, odbiorcy danych, publiczne wyniki i potencjalne wyjątki dla usługodawców. PGS i własny endpoint HTTPS; usuwanie danych lokalnych, PGS i własnego rankingu to odrębne operacje. |

**Nie oznaczaj wszystkich danych jako opcjonalnych:** przy skonfigurowanym PGS możliwe jest automatyczne uwierzytelnienie oraz zbieranie diagnostyki przez SDK, mimo że gra offline działa bez logowania. **Nie deklaruj, że własny serwer jest aktywny**, dopóki nie skonfigurowano `CW_SERVER_URL`, `CW_HMAC_KEY`, zgody użytkownika i produkcyjnego HTTPS. Dokument `data_safety.md` opisuje także wariant po zleceniu 80, nie gwarantuje jego obecności w finalnym AAB.

**Wymagane przed zatwierdzeniem formularza:** przejrzyj transytywne SDK, wygenerowany manifest release, faktyczne przepływy podczas logowania i zapisów PGS, warunki retencji oraz dostępny sposób usunięcia danych. Google wymaga ujawnienia także operacji wykonywanych przez SDK firm trzecich. Źródła (1.11.2026): [Google — Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [PGS data collection](https://developer.android.com/games/pgs/data-collection), [User data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

### 3.4. Publiczna polityka prywatności i dostęp recenzenta

- Wypełnij sześć pól w [OWNER_INPUT.md](OWNER_INPUT.md), wygeneruj dokumenty przez `./gradlew :app:renderStorePolicies --console=plain`, **opublikuj wynikowe** wersje PL/EN na stronie WWW i przetestuj URL w trybie incognito. Nie publikuj nieuzupełnionych szablonów.
- Uzupełnij URL w Play Console i w konfiguracji `CW_PRIVACY_URL`. **Google wymaga polityki także wewnątrz aplikacji**; w bazie to otwarty punkt wymagający osobnego wdrożenia. URL ma wskazywać czytelną stronę, nie pobierany PDF. [Google — polityka prywatności](https://support.google.com/googleplay/android-developer/answer/10144311), [App content](https://support.google.com/googleplay/android-developer/answer/9859455).
- Przy weryfikacji App access opisz, jak uruchomić kampanię bez konta. Jeśli publikowane funkcje Play Games są niedostępne bez wpisania testera lub specjalnego dostępu, zapewnij recenzentowi działającą ścieżkę i instrukcję; nie podawaj publicznie haseł. [Google — App access](https://support.google.com/googleplay/android-developer/answer/9859455).

## 4. Play App Signing, klucz upload i Play Games Services

1. Utwórz **prywatny klucz upload JKS** zgodnie z [docs/CI.md](../CI.md) i zabezpiecz kopię oraz hasła poza repo. Skonfiguruj cztery zmienne `RELEASE_KEYSTORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` lokalnie, a w CI odpowiednie sekrety `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Nigdy nie wysyłaj prywatnego klucza ani haseł do repo. [Android: podpisywanie aplikacji](https://developer.android.com/studio/publish/app-signing).
2. Przy pierwszym release w Play Console wybierz **Play App Signing**: Google zarządza *app signing key*, a Twoim kluczem prywatnym do podpisywania AAB jest *upload key*. Rozróżnij certyfikaty i ich SHA-1; klucz upload można resetować według procedury Google. [Android: Play App Signing](https://developer.android.com/studio/publish/app-signing).
3. Dla PGS ustaw projekt i cztery rankingi, OAuth dla poprawnego `com.centurywar.game` oraz **SHA-1 certyfikatu podpisującego aplikację dystrybuowaną przez Google** z **Test and release → Setup → App integrity**; do lokalnych testów również odpowiedni debug/upload SHA-1. Uzupełnij `app/src/main/res/values/games_ids.xml`: identyfikator projektu i czterech rankingów nadal są `REPLACE_ME`. Testuj logowanie, tablice i Saved Games na rzeczywistym urządzeniu. Zob. [PLAY_GAMES_SETUP.md](../PLAY_GAMES_SETUP.md), [Google — PGS sign-in](https://developer.android.com/games/pgs/android/android-signin), [Google — konfiguracja](https://developer.android.com/games/pgs/console/enable-features).
4. W dokumentacji PGS istnieją placeholdery osiągnięć; **nie reklamuj synchronizacji osiągnięć z Google** bez adaptera mapującego ID, mimo że osiągnięcia lokalne działają. Źródło projektu: [PLAY_GAMES_SETUP.md](../PLAY_GAMES_SETUP.md).

**Budowanie (po uzupełnieniu danych właściciela, klucza, PGS i warunków wydania):**

```bash
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache
./gradlew :server:test -PserverOnly=true --console=plain
./gradlew :app:renderStorePolicies --console=plain
./gradlew :app:releaseChecklist --console=plain
./gradlew :app:bundleRelease --console=plain
```

Wgraj **`app/build/outputs/bundle/release/app-release.aab`**, nie debug APK. Zwiększ `releaseVersionCode` w `gradle.properties` przed każdym kolejnym wydaniem, sprawdź `versionName`, datę `Daily.RELEASE_DAY`, adres polityki i testy. Szczegóły: [docs/RELEASE.md](../RELEASE.md), [docs/CI.md](../CI.md). Wymóg podpisu upload i procedura publikacji: [Android — app signing](https://developer.android.com/studio/publish/app-signing), [Google — przygotowanie release](https://support.google.com/googleplay/android-developer/answer/9859348).

**Kontrola manifestu i SDK przed deklaracją AD_ID/Data safety:**

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain
./gradlew :app:processReleaseMainManifest --console=plain
# Następnie sprawdź w app/build/intermediates/merged_manifests/
# m.in. com.google.android.gms.permission.AD_ID i uprawnienia dodane przez SDK.
```

## 5. Testy: dokładnie które tory i jakie progi?

| Tor | Dla kogo i po co | Warunki / liczba testerów |
|---|---|---|
| **Internal testing** | Szybki QA i test instalacji przez Play; również w niekompletnym setupie aplikacji. | Do **100 testerów**. Zalecany jako pierwszy etap. [Źródło](https://support.google.com/googleplay/android-developer/answer/9845334). |
| **Closed testing** | Zaproszeni testerzy e-mail / Google Groups; test PGS, stabilności, UI, Fold, zapisu i recenzji. | **Organizacja:** brak ogólnego wymogu „12/14” tylko z racji nowego konta. **Nowe konto osobiste po 13.11.2023:** co najmniej **12 osób zapisanych nieprzerwanie przez 14 dni** przed wnioskiem o produkcję. [Źródło](https://support.google.com/googleplay/android-developer/answer/14151465). |
| **Open testing** | Wersja testowa widoczna szerszej publiczności; uruchamiaj dopiero przy gotowej karcie i politykach. | Dostępność zależy od stanu konta (nowe konta osobiste muszą uzyskać dostęp produkcyjny). [Źródło](https://support.google.com/googleplay/android-developer/answer/9845334). |
| **Production** | Wersja dostępna publicznie w wybranych krajach po akceptacji. | Dla nowych kont osobistych podlega procedurze dostępu po teście zamkniętym; dla firmy nie zakładaj automatycznie tej procedury, ale zastosuj wszystkie bieżące blokady Dashboardu i review. [Źródło](https://support.google.com/googleplay/android-developer/answer/9859348). |

**Rekomendowany proces dla Century War:**

1. **Internal:** dodaj 5–10 własnych testujących (to rekomendacja, nie próg Google), wygeneruj i udostępnij link zapisu, sprawdź instalację z Play, grę bez sieci oraz logowanie Play Games na podpisanej wersji sklepowej.
2. **Closed:** zaproś szerszą grupę i udostępnij link opt-in (każdy tester musi się zapisać), podaj adres zbierania opinii. Sprawdź rozdzielczości 720p/Fold, odtwarzanie bitwy, zapisy, prywatność, brak reklam/IAP, logowanie PGS i synchronizację danych. Jeśli konto **osobiste** objęte przepisem, weryfikuj rzeczywiste 12/14 w konsoli; zwykłe wysłanie 12 zaproszeń nie wystarcza. [Google — tory i link testerów](https://support.google.com/googleplay/android-developer/answer/9845334), [Google — próg 12/14](https://support.google.com/googleplay/android-developer/answer/14151465).
3. **Production:** utwórz nowe wydanie lub promuj sprawdzoną wersję AAB na tor produkcyjny, podaj notatki release, przejrzyj **Publishing overview**, wyślij do oceny, sprawdzaj status i dopiero po akceptacji opublikuj (lub użyj zarządzanego publikowania, jeżeli zostało włączone). [Google — wydania](https://support.google.com/googleplay/android-developer/answer/9859348), [Google — statusy](https://support.google.com/googleplay/android-developer/answer/9859751).

**Uwaga:** użytkownik będący zapisanym testerem wewnętrznym może nie otrzymywać wersji z innego toru, dopóki nie zrezygnuje z toru internal. Udostępnione linki mogą pojawić się z opóźnieniem po pierwszej publikacji testu. [Google — tory testowe](https://support.google.com/googleplay/android-developer/answer/9845334).

## 6. Checklista właściciela — zaznaczaj dopiero po wykonaniu

### Konto i dane podmiotu
- [ ] Potwierdzono, **który podmiot prawny / JDG** będzie wydawcą, i wybrano **Organization**. [Typ konta](https://support.google.com/googleplay/android-developer/answer/13634885)
- [ ] D-U-N-S istnieje; legalna nazwa i adres są identyczne w D&B, Google Payments i dokumentach rejestrowych. [Weryfikacja](https://support.google.com/googleplay/android-developer/answer/13628312)
- [ ] Opłacono rejestrację Play Console; zachowano rachunek. [Opłata](https://support.google.com/googleplay/android-developer/answer/6112435)
- [ ] Zweryfikowano tożsamość przedstawiciela, firmę, domenę w Search Console i wszystkie wymagane kontakty. [Weryfikacja](https://support.google.com/googleplay/android-developer/answer/10841920), [witryna](https://support.google.com/googleplay/android-developer/answer/13205715)
- [ ] W [OWNER_INPUT.md](OWNER_INPUT.md) wpisano prawdziwe dane właściciela i datę; **bez haseł ani skanów w repo**.

### Karta gry i deklaracje
- [ ] Aplikacja `com.centurywar.game`, kategoria **Game**, darmowa v1; poprawne języki, kraje, e-mail wsparcia. [Tworzenie](https://support.google.com/googleplay/android-developer/answer/9859152)
- [ ] Opis z [listing_pl.md](listing_pl.md) i [listing_en.md](listing_en.md), prawdziwe grafiki z [STORE_GRAPHICS.md](STORE_GRAPHICS.md), [screenshots_plan.md](screenshots_plan.md). [Karta sklepu](https://support.google.com/googleplay/android-developer/answer/9859152)
- [ ] W finalnej wersji faktycznie brak reklam i zakupów (sprawdzone `app/build.gradle.kts`, manifest release, [monetization_plan.md](monetization_plan.md)); **Ads = No**. [App content](https://support.google.com/googleplay/android-developer/answer/9859455)
- [ ] Rozstrzygnięto Advertising ID po weryfikacji scalonego manifestu i SDK, nie tylko źródłowego XML. [AAID](https://support.google.com/googleplay/android-developer/answer/6048248)
- [ ] Zatwierdzono ankietę IARC: przemoc kreskówkowa **Tak**; ewentualne pseudonimy rankingów; rzeczywisty wynik regionów. [content_rating.md](content_rating.md), [IARC](https://support.google.com/googleplay/android-developer/answer/9859655)
- [ ] Zatwierdzono grupę wieku zgodnie z rzeczywistą publicznością; sprawdzono ewentualne zasady Families. [Target audience](https://support.google.com/googleplay/android-developer/answer/9867159)
- [ ] Weryfikacja **App access**: recenzent może grać offline i — jeśli funkcje PGS są udostępniane — przetestować je zgodnie z instrukcją. [App content](https://support.google.com/googleplay/android-developer/answer/9859455)
- [ ] Data safety potwierdzono z [data_safety.md](data_safety.md) dla finalnego AAB i konfiguracji PGS/serwera; uwzględniono automatyczne dane SDK i usuwanie. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [ ] Wyrenderowane polityki PL/EN są publicznie dostępne przez HTTPS **oraz link/tekst polityki działa w samej grze**. [OWNER_INPUT.md](OWNER_INPUT.md), [User data](https://support.google.com/googleplay/android-developer/answer/10144311)

### Build, testy i publikacja
- [ ] Ustalono `Daily.RELEASE_DAY`; `releaseVersionCode` jest prawidłowy; `releaseChecklist` przechodzi bez pomijania blokad. [docs/RELEASE.md](../RELEASE.md)
- [ ] Ustawiono cztery sekrety podpisu; wykonano `bundleRelease`, AAB jest podpisany kluczem upload, nie debug. [docs/CI.md](../CI.md), [Google signing](https://developer.android.com/studio/publish/app-signing)
- [ ] W Play App Signing zapisano SHA-1 certyfikatu **app signing** i skonfigurowano OAuth PGS; `games_ids.xml` bez `REPLACE_ME` dla aktywnych usług; sprawdzono logowanie i Saved Games. [PLAY_GAMES_SETUP.md](../PLAY_GAMES_SETUP.md)
- [ ] Wykonano build i testy Gradle oraz kontrolę ostatecznego manifestu, PGS, offline, 720p/Fold, danych, stabilności. [docs/CI.md](../CI.md), [docs/RELEASE.md](../RELEASE.md)
- [ ] Przeprowadzono **internal** i zalecany **closed test**; nie zastosowano bezpodstawnie reguły 12/14 do konta organizacji. [Tory testów](https://support.google.com/googleplay/android-developer/answer/9845334)
- [ ] Utworzono release **Production**, przejrzano ostrzeżenia i Publishing overview, wysłano do review, potwierdzono status udostępnienia. [Wydania](https://support.google.com/googleplay/android-developer/answer/9859348)

## 7. Znane blokady i granice tej instrukcji (1.11.2026)

1. **Niewypełniony [OWNER_INPUT.md](OWNER_INPUT.md)**: nie ma prawnej nazwy wydawcy, adresu, e-maila, URL polityki, daty i ID PGS — to musi uzupełnić właściciel.
2. **Brak gotowej polityki w aplikacji:** [data_safety.md](data_safety.md) wskazuje brak odnośnika w `SettingsScreen.kt`. Dodać w odrębnym zleceniu; ten dokument nie zmienia kodu. [Polityka Google](https://support.google.com/googleplay/android-developer/answer/10144311).
3. **Placeholdery PGS:** `games_ids.xml` zawiera `REPLACE_ME` dla projektu, czterech rankingów i części osiągnięć. Wdrożenie PGS wymaga czynności w konsoli i testów fizycznych; [PLAY_GAMES_SETUP.md](../PLAY_GAMES_SETUP.md).
4. **Data safety jest warunkowe:** dokument źródłowy rozróżnia przyszły protokół rankingu ze zlecenia 80 od wersji faktycznie wdrożonej. Nie zakładaj, że funkcja moderacji już działa.
5. **Brak finalnego AAB i certyfikatów w tym zleceniu:** nie można potwierdzić `AD_ID`, rzeczywistych transferów transytywnych bibliotek, skuteczności OAuth i ostatecznych odpowiedzi Data safety.
6. **Brak obowiązku 12/14 dla konta firmy nie znaczy „brak testów”:** przed produkcją należy wykonać rzeczywisty QA i spełnić indywidualne zadania widoczne w Play Console. [Google — testing](https://support.google.com/googleplay/android-developer/answer/9845334).

**Reguła aktualizacji:** przed każdym nowym AAB sprawdzaj ponownie polityki Google, zmiany SDK, nowe zakupy/reklamy, publiczne nazwy graczy, ochronę danych i odpowiedzi IARC. Dokument opisuje wymagania zweryfikowane **1 listopada 2026 r.**; nie jest potwierdzeniem, że konkretne konto lub gra zostały już zaakceptowane przez Google.
