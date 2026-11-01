# Google Play Games Services v2 — konfiguracja Century War

Zależność: **com.google.android.gms:play-services-games-v2:22.1.0** (stabilna od 15.09.2026). Aplikacja działa lokalnie bez skonfigurowanych ID i bez usług Google.

## 1. Projekt gry i poświadczenia

1. Otwórz Google Play Console, aplikację **Century War**, pakiet **com.centurywar.game** (sprawdź identyfikator w app/build.gradle.kts).
2. Przejdź do Grow users → Play Games Services → Setup and management → Configuration i utwórz/połącz projekt Play Games Services v2 z grą.
3. Skopiuj **Game services project ID** (same cyfry, a nie OAuth Client ID) do docs/store/play_games_ids.properties jako game_services_project_id.
4. Utwórz poświadczenia Android OAuth przypisane do pakietu com.centurywar.game i poprawnych odcisków SHA-1.
5. Dodaj konta testerów do testowej konfiguracji PGS, opublikuj ją do testów i zaloguj się na urządzeniach wskazanymi kontami.

### Certyfikaty SHA-1

**Debug** (Linux/macOS):

    keytool -list -v -alias androiddebugkey -keystore "$HOME/.android/debug.keystore" -storepass android -keypass android

**Debug Windows**: użyj pliku %USERPROFILE%\.android\debug.keystore i tych samych parametrów.

**Upload key**:

    keytool -list -v -keystore /sciezka/do/upload-key.jks -alias NAZWA_ALIASU

**Play App Signing**: w Play Console → Test and release → Setup → App integrity skopiuj SHA-1 z **App signing key certificate**. Ten certyfikat jest zwykle inny niż klucz upload. Do obsługi debug APK i aplikacji ze sklepu rejestruj odpowiadające im odciski w poświadczeniach PGS.

**Ważne:** build release wymaga prawdziwego klucza upload. Niekompletna konfiguracja podpisu zatrzymuje build, a debugowy wyjątek dostępny jest wyłącznie w smoke-teście CI.

## 2. Cztery rankingi

Play Games Services → Setup and management → Leaderboards: utwórz cztery tablice, wszystkie sortowane od największego wyniku.

| Klucz w grze | Klucz w play_games_ids.properties | Jednostka | Okno wyświetlania |
| --- | --- | --- | --- |
| endless | leaderboard_endless | punkty oblężenia | all-time |
| daily | leaderboard_daily | punkty wyzwania dnia | daily |
| campaign | leaderboard_campaign | suma gwiazdek 0–96 | all-time |
| skirmish | leaderboard_skirmish | punkty potyczki | all-time |

Wstaw ID przydzielone przez Play Console do zasobów docs/store/play_games_ids.properties, zastępując wszystkie napisy REPLACE_ME. PGS włącza się dopiero po ustawieniu ID projektu i wszystkich czterech tablic. Tablica dzienna korzysta z wbudowanego wariantu czasowego TIME_SPAN_DAILY; nie jest to oddzielny reset fizycznych rekordów. Wyniki trzeba wysyłać w dniu rozgrywki, a nie po kolejnej północy UTC.

## 3. Osiągnięcia

Lokalny katalog `meta/Achievements.kt` zawiera **54 osiągnięcia** (40 dotychczasowych + 14 nowych).
Nazwy i opisy znajdują się w `engine/L.kt` w 10 językach. Nagrody medalowe
są zapisywane w profilu lokalnie, działają offline i nie zależą od usług Google.
`AchievementBattleTracker` obserwuje wyniki kroków symulacji, nie wpływając
na stan gry, seed ani kodowanie powtórek.

W Play Console → Play Games Services → Setup and management → Achievements utwórz
odpowiednie osiągnięcia **standardowe, odblokowywane jednorazowo**. Przepisz
nadane przez Google identyfikatory do `docs/store/play_games_ids.properties`.
Wszystkie 54 pola mają nazwy `achievement_<id>` i wartości `REPLACE_ME` do zastąpienia.
Ich logiczny klucz po stronie katalogu to `cw_<id>`.

| ID katalogu / sufiks zasobu | Warunek |
| --- | --- |
| `steam_twins` | Wygraj bitwy z obydwoma wariantami epoki pary |
| `silicon_twins` | Wygraj bitwy z obydwoma wariantami epoki krzemu |
| `rain_victory` | Wygraj bitwę w deszczu |
| `wind_victory` | Wygraj bitwę przy wietrze |
| `night_victory` | Wygraj bitwę nocą |
| `barrage_eight` | Traf co najmniej 8 różnych przeciwników jednym **celowanym** ostrzałem |
| `boss_knockback` | Pokonaj bossa Odrzut w Endless |
| `boss_aura` | Pokonaj bossa Aura tempa w Endless |
| `boss_shield` | Pokonaj bossa Tarczownik w Endless |
| `boss_recruiter` | Pokonaj bossa Werbownik w Endless |
| `boss_rage` | Pokonaj bossa Szał w Endless |
| `weekly_gold` | Osiągnij złoty próg oblężenia tygodnia |
| `custom_three` | Wygraj własną potyczkę z trzema modyfikatorami |
| `replay_record` | Obejrzyj przez co najmniej 10 s powtórkę najlepszego wyniku zachowanego w lokalnym rankingu (bez przewijania) |

Stan trwały: `achievementCounters`, `unlockedAchievements` i istniejące
metryki profilu. Starsze zapisy nie zawierające nowych liczników zachowują
wartości domyślne. Jedno osiągnięcie jest nagradzane tylko raz.
Warianty epok są zliczane po wygranej, bossowie i celowane salwy są
zbierane podczas bitwy bez alokacji w pętli. Wynik tygodniowy liczy się
również wtedy, gdy oblężenie kończy się porażką po osiągnięciu progu.

**Integracja online:** `OnlineAchievements` udostępnia logiczne klucze
`cw_*` i bezpieczny adapter `NoOp`. W obecnej wersji
mapowania `cw_*` na zasoby odpowiada `PlayGamesIds.kt`. Po pełnej konfiguracji
`PlayGamesLeaderboards` implementuje `OnlineAchievements`, wysyła odblokowania
przez `AchievementsClient` i ponawia lokalne osiągnięcia po zalogowaniu i
odtworzeniu chmury. Bez konfiguracji działa `NoOp`, a osiągnięcia lokalne
pozostają w pełni funkcjonalne.

## 4. Włącz Saved Games

1. Wejdź w Play Games Services → Configuration → Edit properties.
2. Ustaw **Saved Games: ON** i zapisz konfigurację. Zmiany mogą aktywować się do 24 godzin.
3. Gra przechowuje snapshot o nazwie **century_war_profile_v1** i nie zmienia formatu JSON v1 z lokalnego profile.json.
4. Po logowaniu najpierw odczytuje chmurę i porównuje postęp; nie nadpisuje zdalnego profilu pustym lokalnym.
5. Reguła konfliktu: **suma gwiazdek × 100 + medale wydane na badania + medale posiadane + liczba bitew**. Wyższa suma wygrywa. Remis podczas porównania chmury z urządzeniem pozostawia wersję lokalną. Dwie konfliktujące wersje chmurowe porównywane są tą samą regułą (remis pozostawia pierwszą).
6. Nie dodawaj medali z obu wersji: oznaczałoby to podwojenie waluty. Save i load są asynchroniczne, lokalny postęp pozostaje dostępny offline.

## 5. Integracja UI (zrealizowana)

- **Rankingi → Ranking globalny**: przycisk jest widoczny tylko wtedy, gdy podano poprawne ID PGS i inicjalizacja SDK powiodła się. Wybór otwiera odpowiednią tablicę (endless, daily, campaign, skirmish). Użytkownik niezalogowany może zalogować się przy otwieraniu tablicy.
- **Ustawienia → Play Games**: ekran pokazuje stan konta i przycisk logowania lub synchronizacji. Bez konfiguracji wyświetla „Niedostępne w tej wersji” bez przycisku logowania.
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
