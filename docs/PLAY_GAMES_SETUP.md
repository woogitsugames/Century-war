# Google Play Games Services v2 — konfiguracja Century War

Zależność: **com.google.android.gms:play-services-games-v2:22.1.0** (stabilna od 15.09.2026). Aplikacja działa lokalnie bez skonfigurowanych ID i bez usług Google.

## 1. Projekt gry i poświadczenia

1. Otwórz Google Play Console, aplikację **Century War**, pakiet **com.centurywar.game** (sprawdź identyfikator w app/build.gradle.kts).
2. Przejdź do Grow users → Play Games Services → Setup and management → Configuration i utwórz/połącz projekt Play Games Services v2 z grą.
3. Skopiuj **Game services project ID** (same cyfry, a nie OAuth Client ID) do app/src/main/res/values/games_ids.xml jako game_services_project_id.
4. Utwórz poświadczenia Android OAuth przypisane do pakietu com.centurywar.game i poprawnych odcisków SHA-1.
5. Dodaj konta testerów do testowej konfiguracji PGS, opublikuj ją do testów i zaloguj się na urządzeniach wskazanymi kontami.

### Certyfikaty SHA-1

**Debug** (Linux/macOS):

    keytool -list -v -alias androiddebugkey -keystore "$HOME/.android/debug.keystore" -storepass android -keypass android

**Debug Windows**: użyj pliku %USERPROFILE%\.android\debug.keystore i tych samych parametrów.

**Upload key**:

    keytool -list -v -keystore /sciezka/do/upload-key.jks -alias NAZWA_ALIASU

**Play App Signing**: w Play Console → Test and release → Setup → App integrity skopiuj SHA-1 z **App signing key certificate**. Ten certyfikat jest zwykle inny niż klucz upload. Do obsługi debug APK i aplikacji ze sklepu rejestruj odpowiadające im odciski w poświadczeniach PGS.

**Ważne:** aktualny build release projektu jest tymczasowo podpisywany kluczem debug. Przed publikacją ustaw prawdziwy klucz upload.

## 2. Cztery rankingi

Play Games Services → Setup and management → Leaderboards: utwórz cztery tablice, wszystkie sortowane od największego wyniku.

| Klucz w grze | Zasób w games_ids.xml | Jednostka | Okno wyświetlania |
| --- | --- | --- | --- |
| endless | leaderboard_endless | punkty oblężenia | all-time |
| daily | leaderboard_daily | punkty wyzwania dnia | daily |
| campaign | leaderboard_campaign | suma gwiazdek 0–96 | all-time |
| skirmish | leaderboard_skirmish | punkty potyczki | all-time |

Wstaw ID przydzielone przez Play Console do zasobów app/src/main/res/values/games_ids.xml, zastępując wszystkie napisy REPLACE_ME. PGS włącza się dopiero po ustawieniu ID projektu i wszystkich czterech tablic. Tablica dzienna korzysta z wbudowanego wariantu czasowego TIME_SPAN_DAILY; nie jest to oddzielny reset fizycznych rekordów. Wyniki trzeba wysyłać w dniu rozgrywki, a nie po kolejnej północy UTC.

## 3. Osiągnięcia

W Play Games Services → Setup and management → Achievements utwórz osiągnięcia, ustal opisy, ikony, typ standardowy/inkrementalny, widoczność i progi. Przykładowe osiągnięcia Century War: pierwsze zwycięstwo, pierwsze trzy gwiazdki, 32 ukończone etapy, 100 pokonanych jednostek, pierwsze badanie i 10 wygranych potyczek. Zanotuj wygenerowane przez Google ID.

**Zakres tego zlecenia:** podłączenie SDK, rankingów i snapshotów. Wywołania AchievementsClient.unlock/increment i ich integracja z logiką gry należą do osobnego zadania osiągnięć; samo ich utworzenie w konsoli nie uruchamia odblokowywania.

## 4. Włącz Saved Games

1. Wejdź w Play Games Services → Configuration → Edit properties.
2. Ustaw **Saved Games: ON** i zapisz konfigurację. Zmiany mogą aktywować się do 24 godzin.
3. Gra przechowuje snapshot o nazwie **century_war_profile_v1** i nie zmienia formatu JSON v1 z lokalnego profile.json.
4. Po logowaniu najpierw odczytuje chmurę i porównuje postęp; nie nadpisuje zdalnego profilu pustym lokalnym.
5. Reguła konfliktu: **suma gwiazdek × 100 + medale wydane na badania + medale posiadane + liczba bitew**. Wyższa suma wygrywa. Remis podczas porównania chmury z urządzeniem pozostawia wersję lokalną. Dwie konfliktujące wersje chmurowe porównywane są tą samą regułą (remis pozostawia pierwszą).
6. Nie dodawaj medali z obu wersji: oznaczałoby to podwojenie waluty. Save i load są asynchroniczne, lokalny postęp pozostaje dostępny offline.

## 5. Integracja UI (zrealizowana)

- **Rankingi → Ranking globalny**: przycisk jest widoczny tylko wtedy, gdy podano poprawne ID PGS i inicjalizacja SDK powiodła się. Wybór otwiera odpowiednią tablicę (endless, daily, campaign, skirmish). Użytkownik niezalogowany może zalogować się przy otwieraniu tablicy.
- **Ustawienia → Play Games**: ekran pokazuje stan konta (sprawdzanie, niezalogowany, połączono, synchronizacja, błąd) oraz przycisk zalogowania albo ręcznej synchronizacji.
- Wznowienie aplikacji uruchamia asynchroniczną kontrolę `isAuthenticated`; nigdy nie blokuje ekranu.
- Wyniki zwycięskiej potyczki wysyłane są bezpośrednio, a zapisane wcześniej rekordy może dosłać mechanizm synchronizacji profilu.
- `g.online.available` informuje, czy funkcjonalność została poprawnie skonfigurowana i zainicjalizowana. **Nie** oznacza, że gracz jest zalogowany; do prezentacji stanu służy `g.online.state`.
- Brak ID oznacza pełny tryb lokalny bez prób logowania. Dla skonfigurowanej gry brak sieci nie wpływa na rozgrywkę.

## 6. Testy akceptacyjne na urządzeniu

1. Pozostaw REPLACE_ME — uruchom debug APK bez usług Google: brak promptu PGS, lokalne zapisy i rankingi działają.
2. Uzupełnij ID, uruchom na koncie testera. Sprawdź ciche isAuthenticated i przełączenie available.
3. Wyślij wyniki z każdej tablicy, sprawdź poprawne ID, sortowanie i daily/UTC.
4. Połącz dwa telefony z jednym kontem, przetestuj przeniesienie silniejszego profilu w obie strony i konflikt różnych medali/badań.
5. Sprawdź uruchomienie offline, bez Google Play Services, odmowę logowania i błąd snapshotu.
6. Zastąp podpis release kluczem upload przed wdrożeniem do sklepu.

## Źródła Google

- https://developer.android.com/games/pgs/android/android-signin
- https://developer.android.com/games/pgs/android/leaderboards
- https://developer.android.com/games/pgs/android/saved-games
- https://developer.android.com/games/pgs/console/enable-features
- https://developers.google.com/android/guides/releases
