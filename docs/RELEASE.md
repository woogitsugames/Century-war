# Wydanie Android / Google Play

## Numer wersji

- `releaseVersionCode` w `gradle.properties` to dodatni, rosnący numer publikacji w Google Play.
  **Zwiększ go przed każdym nowym wydaniem**, także gdy nie zmienia się `versionName`.
- `versionName` jest pobierane z tagu SemVer wskazującego **bieżący commit**
  (np. `v1.2.3` daje `1.2.3`). Bez tagu (lub bez gita) stosowane jest
  `releaseVersionName` z `gradle.properties` (`0.1.0-dev`).
- Wersjonowanie korzysta z `providers.exec`, zgodnego z cache konfiguracji Gradle.
  Przy budowaniu z tagu pobierz pełną historię i tagi (`git fetch --tags`);
  job `release` w CI robi checkout z `fetch-depth: 0`.

## Podpisywanie

Jeden schemat dla CI i lokalnie (szczegóły i generowanie klucza: [`docs/CI.md`](CI.md)):

| Zmienna środowiskowa | Wartość |
|---|---|
| `RELEASE_KEYSTORE_FILE` | ścieżka do pliku `.jks` (klucz upload) |
| `RELEASE_STORE_PASSWORD` | hasło keystore |
| `RELEASE_KEY_ALIAS` | alias klucza |
| `RELEASE_KEY_PASSWORD` | hasło klucza |

- **Wszystkie cztery ustawione i poprawne:** release jest podpisywany kluczem upload.
  Wskazany plik JKS musi istnieć i nie może być standardowym plikiem debug.keystore;
  alias androiddebugkey jest odrzucany.
- **Żadna nieustawiona:** polecenia assembleRelease, bundleRelease i releaseChecklist
  **kończą się błędem**. Nie ma automatycznego przełączenia na podpis debug.
  Nie można obejść tego warunku przez -PskipCheck.
- **Część ustawiona:** Gradle kończy konfigurację błędem.
- **Wyjątek CI:** -PciTestDebugSigning=true jest dostępne wyłącznie dla jawnie
  oznaczonego smoke-testu GitHub Actions (zdarzenie pull_request albo
  workflow_dispatch, GITHUB_ACTIONS=true i CW_RELEASE_SMOKE=true).
  Taki artefakt jest podpisany debugowo i **NIE NADAJE SIĘ DO GOOGLE PLAY**.
  Lokalnie ani w produkcyjnym workflow z taga nie można używać tej flagi.

W CI job `release` (push taga `v*`) odtwarza keystore z sekretów GitHub i przerywa pracę,
jeśli któregoś brakuje. Skonfiguruj **Play App Signing** w Play Console i wgraj
`app/build/outputs/bundle/release/app-release.aab`.

## AAB, R8 i Baseline Profile

- `bundleRelease` jest dostępne w `com.android.application` bez dodatkowych wtyczek.
  Brak kodu natywnego, więc podziały ABI nie mają zastosowania.
- Release: `isMinifyEnabled` + `isShrinkResources`; AGP 8.x domyślnie używa R8 full mode.
  Profil i zrzut bitwy mają ręczny JSON / format binarny. Reguły
  `proguard-rules.pro` zachowują komponenty manifestu oraz enumy o trwałych
  ordinalach i nazwach zapisywanych do profilu (zlecenie 128).
- `app/src/main/baseline-prof.txt` zawiera ręcznie prześledzone ścieżki
  startu (`CenturyWarApp`, `MainActivity`, `Profile`), menu
  (`GameView`, `Game`, `MenuScreen`, `MenuBg`), wyboru trybu
  i bitwy (`BattleScreen`, `BattleRenderer`, `Battle`, `Ai`, `Rng`).
  Lista została przygotowana na podstawie bieżących wywołań w kodzie,
  **nie** z pomiaru Macrobenchmark na urządzeniu; realny zysk w czasie startu
  wymaga odrębnego pomiaru na telefonie. AGP kompiluje go do `assets/dexopt/baseline.prof` (APK)
  i `BUNDLE-METADATA` (AAB) oraz przepisuje deskryptory po R8. Instalacje z Google Play
  (Android 9+) korzystają z niego przy instalacji.
- Nie dodano `androidx.profileinstaller` (gra nie ma zależności bibliotecznych). Bez niego
  profil nie jest instalowany przy sideloadzie ani na Androidzie 8/8.1 — korzyść jest
  marginalna dla tak małej aplikacji, a zależność łamałaby zasadę „bez bibliotek”.

## Języki i backup

**Języki.** `android:localeConfig` (`res/xml/locales_config.xml`) deklaruje 10 języków gry:
`pl, en, de, es, fr, it, pt, uk, nl, tr` — dokładnie `L.CODES` z `engine/L.kt`
(sprawdza to `ReleaseConfigTest`). Dodając język, dopisz go w obu miejscach i uzupełnij
wszystkie klucze tabeli (`LTest` wymaga kompletu 10 języków i zgodnych placeholderów).
Dzięki `localeConfig` Android 13+ pokazuje grę w ustawieniach języka aplikacji.

**Backup.** `android:allowBackup="true"`; kopia Androida obejmuje **wyłącznie**
`filesDir/profile.json` (postęp, ustawienia, sekret rankingu) i `filesDir/history.json`
(historia bitew). Reguły `include` wyłączają całą resztę: kolejkę wyników
(`score-queue.json*`), `crash/`, `battle.snap` (przerwana bitwa — przejściowa),
`replays/` (rozmiar), `profile-backups/`, pliki `.tmp`/`.corrupt` i SharedPreferences.

- Android 12+ (`android:dataExtractionRules` → `res/xml/data_extraction_rules.xml`):
  `<cloud-backup disableIfNoEncryptionCapabilities="true">` — kopia w chmurze powstaje
  tylko, gdy urządzenie potrafi ją zaszyfrować end-to-end (ustawiona blokada ekranu);
  `<device-transfer>` (przeniesienie kablem/Wi-Fi na nowy telefon) obejmuje te same dwa
  pliki bez wymogu szyfrowania.
- Android 6–11 (`android:fullBackupContent` → `res/xml/backup_rules.xml`): oba pliki mają
  `requireFlags="clientSideEncryption"` (respektowane od Androida 9) — bez szyfrowania po
  stronie klienta nie trafiają do kopii.
- Wymóg szyfrowania wynika z tego, że `profile.json` zawiera sekret rankingu
  (`docs/SECURITY.md`); przywrócenie go na nowym telefonie tego samego konta Google jest
  zamierzone. `adb backup` dla aplikacji niedebugowalnej z targetSdk 31+ i tak pomija dane.
- Konfigurację pilnuje `ReleaseConfigTest` (manifest, reguły obu formatów). Dodając nowy
  plik z postępem, dopisz go w obu XML-ach i w tym opisie.

## Pomiar wydania i R8 (zlecenie 66)

- `.github/workflows/release-check.yml` działa dla PR do `main` i `claude/**`
  oraz uruchomienia ręcznego. Buduje **APK i AAB z R8 i shrinkResources**
  z jawną flagą `-PciTestDebugSigning=true` i dodatkowymi ograniczeniami środowiska CI.
  Tylko ten smoke-test może pominąć pola właściciela przez
  `-PskipCheck=ownerInput`. Artefakt debugowo podpisany **nie nadaje się do sklepu**.
- Przy PR workflow kompiluje również commit wspólny z gałęzią docelową
  (`git merge-base`) na tym samym runnerze i SDK. Skrypt
  `scripts/release_size_report.py` porównuje wielkość plików APK/AAB
  **przed / po**, podaje największe spakowane składniki i grupy:
  DEX, zasoby, profil ART, assets i metadane. Są to rozmiary paczek,
  a nie prognoza transferu przy instalacji z Google Play.
- Raport `build/release-metrics/rozmiary.md` jest dostępny w Job Summary
  i artefakcie `release-walidacja`. Bez PR (uruchomienie ręczne)
  wartości **przed** są oznaczone jako `n/d`; nie są zgadywane.
- `mapping.txt`, `usage.txt` i `configuration.txt` są zachowywane
  jako artefakt `release-r8-mapping`; produkcyjny workflow z taga
  `v*` również archiwizuje `mapping.txt` (`century-war-release-mapping`).
  Skrypt blokuje job, gdy w mapowaniu brakuje klas krytycznych startu
  i bitwy. Zawartość `usage.txt` jest raportowana do ręcznej analizy.
- Przegląd produkcyjnych źródeł `:app`/`:sim`: nie stwierdzono
  refleksyjnego ładowania klas/metod/pól ani serializacji przez refleksję.
  Po kontroli zlecenia 128 dodano wąskie reguły keep: komponenty manifestu,
  enumy z ordinalami w snapshotach/powtórkach oraz `AchievementMetric`
  zapisywany po nazwie w JSON. Nie dodano szerokiego `-keep com.centurywar.**`.
- `shrinkResources = true` jest włączone. Zasoby statyczne to głównie
  ikona, konfiguracja, manifest, kopia zapasowa i tekst licencji Apache
  odczytywany przez `MainActivity`; brak zasobów z pewnością zbędnych
  do bezpiecznego ręcznego usunięcia. Rzeczywiste składniki i udział
  zasobów pokazuje raport CI.
- Release APK jest także instalowany i uruchamiany na emulatorze w tym
  workflow. Test Robolectric zwykle korzysta z klas JVM bez obfuskacji,
  dlatego **nie jest dowodem zgodności po R8**. Niezależny smoke-test
  release sprawdza manifest, start Activity, istnienie procesu i brak
  `AndroidRuntime FATAL EXCEPTION`; nie zastępuje testów wszystkich
  scenariuszy na urządzeniach.

Test skryptu bez Android SDK:

```bash
python3 -m unittest discover -s tests -p 'test_release_size_report.py' -v
```

## Kontrola rzeczywistego DEX i rozruchu (zlecenie 128)

**Zakres.** Konfiguracja release używa `minifyEnabled=true` (R8) i
`shrinkResources=true`; nie dodajemy bibliotek ani nie modyfikujemy
`versionCode` lub danych wydawcy. `ReplayShareProvider`, `MainActivity`,
`CenturyWarApp` i odbiornik przypomnienia są komponentami manifestu,
dlatego ich nazwy muszą być zachowane. Play Games jest wywoływane bezpośrednio
przez SDK (jego reguły pochodzą z biblioteki); w projekcie nie ma `BackupAgent`.
`AchievementMetric.name` jest utrwalane w JSON profilu, a ordinale
`Role`, `Mode`, `Doctrine`, `Modifier`, `Weather`,
`AiPersonality`, `DailyEvent`, `EndlessCard`,
`EndlessWaveTheme`, `EraArt` i `Replay.Kind` są używane
w zapisach bitwy, definicjach lub powtórkach. Zachowanie klas enumów
jest celowe i może nieco zwiększyć APK względem agresywnego unboxingu R8.

**Weryfikacja po minifikacji.** `scripts/verify_release_dex.py` uruchamia
`dexdump -f` na *każdym* `classes*.dex` wyjętym z gotowego APK.
Uwzględnia nazwy po obfuskacji z `mapping.txt`; wymaga klas startu,
bitwy, profilu i Play Games, obecności 12 enumów z nadklasą
`java.lang.Enum` i niezmienionych nazw komponentów Androida.
W przeciwieństwie do samego `mapping.txt`, wykrywa brak klasy
w wynikowym APK. Nie sprawdza zachowania rozgrywki na urządzeniu.
Zamknięty zestaw 7 testów bez Android SDK: `tests/test_verify_release_dex.py`.

```bash
python3 -m unittest discover -s tests -p 'test_verify_release_dex.py' -v
python3 scripts/verify_release_dex.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --mapping app/build/outputs/mapping/release/mapping.txt \
  --dexdump "$ANDROID_HOME/build-tools/36.0.0/dexdump"
```

**Rozmiar APK/AAB.** Workflow `release-check.yml` buduje oba artefakty
z testowym podpisem oraz porównuje je z commitem bazowym tego samego PR.
Wynik to `build/release-metrics/rozmiary.md` w artefakcie
`release-walidacja` i w GitHub Actions Job Summary. Raport podaje
rzeczywiste wielkości ZIP osobno dla APK i AAB, w tym skompresowany
DEX (`classes*.dex`), zasoby (`res/*`, `resources.arsc`,
`resources.pb`), manifest, profil ART i pozostałe elementy.
Bez wykonanego buildu **nie podajemy hipotetycznych wartości**.
`shrinkResources` był już włączony; bez danych z narzędzia
nie usuwamy ręcznie zasobów, których użycie może być pośrednie.
Do dalszej diagnostyki trafiają `mapping.txt`, `usage.txt`,
`configuration.txt`, `dex-audit.txt`, `r8-warnings.txt`
i pełny `release.log`.

**Start na emulatorze i telefonie.** CI buduje również APK debug,
wykonuje *czystą instalację* i start przez `adb shell am start -W`.
W debug log `CWStartup` podaje czas od `Application.onCreate`
do pierwszej klatki menu, wraz z fazami
`Application`, profil, snapshot, wykrywanie jakości, tłumaczenia,
`Game`, Play Games, muzyka i `SurfaceView`.
Następnie CI usuwa aplikację, instaluje APK release i zbiera
`am start -W` oraz log błędów AndroidRuntime. Wyniki:
`start-debug.txt`, `startup-debug.log`, `start.txt` w
`release-walidacja` (i podsumowaniu workflow).

Na fizycznym urządzeniu zmierz release po instalacji **testowej**
(nie produkcyjny upload key w CI) i każdorazowym `force-stop`:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.centurywar.game
adb shell am start -W -n com.centurywar.game/com.centurywar.MainActivity
# Dla debug: ten sam pomiar + fazy StartupTimings w logcat
adb logcat -d -s CWStartup:I '*:S'
```

Zapisz osobno `TotalTime` i `WaitTime` z `am start -W`
(dla obu wariantów) oraz `CWStartup` z debug. To **inne miary**:
`am start -W` mierzy zgłoszenie uruchomienia Activity
(może zakończyć się już na ekranie startowym), a `CWStartup`
mierzy *pierwszą klatkę menu*. Release nie loguje
`StartupTimings` (warunek `BuildConfig.DEBUG`), więc do uczciwego
porównania wariantów służy ten sam pomiar `am start -W`,
a fazy debug są diagnozą opóźnień. Dla wiarygodnych wyników
powtórz co najmniej 5 zimnych startów na tym samym telefonie,
bez aktywnego procesu aplikacji, i podaj medianę.
Zwykły emulator jest kontrolą regresji, nie benchmarkiem taniego telefonu.

**Warunki publikacji.** Testowe ID i podpis CI nie nadają się do wydania;
normalny release wymaga prawdziwego klucza upload i kompletnej
`releaseChecklist`. Po R8 nadal trzeba na telefonie sprawdzić
ścieżki zewnętrznych providerów, osiągnięcia i rankingi Play Games,
import/eksport powtórek i wznawianie zapisów.

## Walidacja

Lokalnie (po wprowadzeniu prawdziwej konfiguracji publikacji):

```bash
./gradlew -p buildSrc test --console=plain
./gradlew :app:testDebugUnitTest --tests '*ReleaseConfigTest*' --tests '*DailyTest*' --console=plain
./gradlew :app:renderStorePolicies --console=plain
./gradlew :app:releaseChecklist --console=plain
./gradlew :app:assembleRelease :app:bundleRelease --console=plain
```

Workflow `.github/workflows/release-check.yml` (PR do `main` lub `claude/**`, i ręcznie) buduje release,
sprawdza obecność `baseline.prof` w APK/AAB, manifest przez `aapt`, raportuje rozmiary
w Job Summary i uruchamia aplikację na emulatorze API 35
(`com.centurywar.game/com.centurywar.MainActivity`, kontrola procesu i `AndroidRuntime`).
Logi trafiają do artefaktu `release-walidacja`.

Po podpisaniu upload key pozostają kroki w Play Console: polityka prywatności,
Data safety, listing (`docs/store/`) i wdrożenie w torze testowym.


## Play Games: konfiguracja premierowa z jednego pliku

**Jedynym źródłem identyfikatorów jest `docs/store/play_games_ids.properties`.**
Nie edytuj ręcznie XML. Gradle generuje `games_ids.xml` osobno dla każdego wariantu
w `app/build/generated/res/playGames/<wariant>/values/games_ids.xml`.
Plik zawiera 70 kluczy: ID projektu, 4 rankingi i 65 osiągnięć.

1. Otwórz Google Play Console → **Century War** (`com.centurywar.game`) →
   **Play Games Services → Setup and management → Configuration**. Utwórz usługę
   i odczytaj numeryczny **Game services project ID** (nie OAuth Client ID).
   Wpisz go jako `app_id` w `docs/store/play_games_ids.properties`.
2. W **Play Games Services → Leaderboards** utwórz cztery rankingi
   z sortowaniem malejącym i skopiuj ich identyfikatory z Play Console do
   `leaderboard_endless` (oblężenie), `leaderboard_campaign` (gwiazdki),
   `leaderboard_skirmish` (potyczka) i `leaderboard_daily` (wyzwanie dnia).
   Gra wyświetla ranking dzienny w oknie `TIME_SPAN_DAILY`.
3. W **Play Games Services → Achievements** utwórz 65 osiągnięć standardowych.
   Wykorzystaj listę `Achievements.all` z `meta/Achievements.kt`. Dla każdego
   identyfikatora katalogu `id` skopiuj ID przyznane przez Play Console do
   `achievement_<id>`, np. `achievement_win_1`. Wszystkie klucze z szablonu
   muszą mieć prawdziwe ID. Mapowanie `cw_<id>` na zasoby jest w
   `meta/PlayGamesIds.kt`; nie zmieniaj tego mapowania przy wpisywaniu ID.
4. W **Play Games Services → Configuration → Credentials** utwórz
   poświadczenia OAuth dla Androida (`com.centurywar.game`). Dodaj SHA-1
   certyfikatu debug oraz certyfikatu **App signing key certificate** z
   Play Console. Klucz upload może mieć inny SHA-1. Włącz **Saved Games**,
   dodaj konta testerów i opublikuj konfigurację PGS do testów.
5. Uruchom `./gradlew :app:generateDebugPlayGamesIds :app:assembleDebug` oraz
   `./gradlew -p buildSrc test`. Debug bez konfiguracji lub z `REPLACE_ME`
   uruchamia wyłącznie funkcje lokalne (`play_games_configured=false`).
   Brak choć jednego ID lub placeholder blokuje **release** niezależnie od
   `-PskipCheck=playGames`. ID o złym formacie powoduje błąd walidacji.
6. Na urządzeniu testera z Google Play Services zainstaluj APK debug
   z kompletem ID. Otwórz **Ustawienia → Play Games**: sprawdź logowanie
   i synchronizację. Otwórz cztery tablice, wyślij wyniki z czterech trybów,
   odblokuj osiągnięcie i potwierdź je w PGS. Na drugim urządzeniu sprawdź
   pobranie profilu z chmury. Powtórz próbę offline i bez usług Google;
   profil i gra muszą działać lokalnie. Zainstaluj również AAB z toru
   **Internal testing** i potwierdź logowanie przy podpisie produkcyjnym.
7. Przed premierą wykonaj `:app:releaseChecklist`, build wydania, testy
   jednostkowe i testy na rzeczywistych urządzeniach. Sam build nie
   potwierdza dostępności usług po stronie Play Console.

Szczegóły i znaczenie osiągnięć: [PLAY_GAMES_SETUP.md](PLAY_GAMES_SETUP.md).

## Lista kontrolna dnia wydania — dla właściciela

Przed pierwszą publikacją i przed **każdą kolejną** aktualizacją przejdź kroki w podanej kolejności.

**Krok 0 — dane wydawcy (obowiązkowy):** otwórz
[docs/store/OWNER_INPUT.md](store/OWNER_INPUT.md), uzupełnij pięć pól:
pełna nazwa prawna wydawcy, e-mail kontaktowy, adres korespondencyjny,
publiczny URL polityki HTTPS i rzeczywista data pierwszego wydania UTC. Nie wpisuj tutaj sekretów.
Następnie uruchom `./gradlew :app:renderStorePolicies` i opublikuj
**wygenerowane** pliki PL/EN z `build/store-policies/` pod wskazanym adresem.
Nie publikuj szablonów z nierozstrzygniętymi znacznikami właściciela.

1. **Ustal dzień pierwszej publicznej publikacji w UTC.** W
   `sim/src/main/kotlin/com/centurywar/game/Modes.kt` ustaw jedną stałą
   `Daily.RELEASE_DAY`: liczbę dni od 1970-01-01 00:00 UTC. Obecne **21153**
   (2027-12-01) jest wyłącznie wartością zastępczą, **nie jest zatwierdzoną datą**.
   Po pierwszym wydaniu **nigdy jej nie zmieniaj**: dotyczy jednocześnie Epoki żelaza,
   wariantów epok i pogody w wyzwaniu dnia. Starsze dni korzystają z `legacyConfig`
   i muszą odtwarzać się identycznie. Datę w UTC przelicz np.:
   `python3 -c "from datetime import date; print((date.fromisoformat('RRRR-MM-DD')-date(1970,1,1)).days)"`
   (zastąp `RRRR-MM-DD` prawdziwą datą).
2. **Zweryfikuj Play Console.** Utwórz aplikację `com.centurywar.game`,
   uzupełnij stronę sklepu i wymagane deklaracje, upewnij się, że dostępne są
   właściwe konta właścicielskie i dostęp do torów testowania.
3. **Podpis produkcyjny.** Włącz Play App Signing, przygotuj klucz upload,
   kopię zapasową i cztery zmienne `RELEASE_*` lokalnie lub sekrety CI z
   [CI.md](CI.md). Zweryfikuj SHA-1 certyfikatu podpisu aplikacji i upload.
   Nie publikuj APK/AAB podpisanych kluczem debug.
4. **Play Games i osiągnięcia.** Utwórz usługę, cztery rankingi i 54
   osiągnięcia w Play Console. Ich ID wpisz wyłącznie w
   `docs/store/play_games_ids.properties` (bez ręcznego XML).
   Skonfiguruj testerów, OAuth i Saved Games; zweryfikuj logowanie,
   chmurę, rankingi i osiągnięcia na urządzeniu.
   Szczegóły w sekcji wyżej i [PLAY_GAMES_SETUP.md](PLAY_GAMES_SETUP.md).
5. **Backend rankingu i podpis wyników.** Uruchom serwer z HTTPS, sprawdź
   `/health`, dostępną wersję protokołu i wysyłkę zweryfikowanej powtórki.
   Ustaw `CW_SERVER_URL`, `CW_HMAC_KEY` i `CW_PRIVACY_URL` w
   `local.properties` albo zmiennych środowiskowych na etapie kompilacji.
   Adres serwera oraz adres opublikowanej polityki prywatności muszą być HTTPS.
   Nie wprowadzaj wartości produkcyjnych ani sekretów do repozytorium.
6. **Polityka prywatności i Data safety.** Sprawdź, że wygenerowane z
   `OWNER_INPUT.md` polityki PL i EN są publicznie dostępne pod HTTPS,
   a `CW_PRIVACY_URL` prowadzi do właściwej strony. W Play Console uwzględnij
   dane PGS, chmurę, publiczne pseudonimy w rankingach PGS i własnych,
   moderowanie nicków po wdrożeniu zlecenia 80, ręcznie udostępniane
   raporty awarii ze zlecenia 65, dane techniczne i usuwanie danych.
   Potwierdź faktyczne działanie własnego backendu oraz aktualny formularz Data safety.
7. **Materiały sklepu.** Sprawdź nazwy, opisy i tłumaczenia, ikonę, grafikę
   promocyjną i prawdziwe zrzuty z telefonu 720p oraz dużego ekranu/Folda.
   Zrzuty aktualnej wersji i zawartość sklepu muszą być zgodne z grą.
8. **Numer wersji.** Podnieś `releaseVersionCode` w `gradle.properties`
   ponad liczbę w `release/last_version_code.txt` (początkowo `0`).
   Sprawdź `releaseVersionName` lub tag SemVer; nie używaj ponownie kodu
   już opublikowanego w Google Play.
9. **Testy przed publikacją.** Uruchom `./gradlew -p buildSrc test`,
   `./gradlew :app:assembleDebug`, testy `Daily*`, `Replay*`,
   `BattleSimTest*`, `LTest*` oraz `./gradlew :server:test -PserverOnly=true`.
   Przetestuj debug i AAB na rzeczywistym tanim telefonie oraz Foldzie,
   online i offline; sprawdź wyzwanie UTC po obu stronach północy.
10. **Zbuduj release bez pominięć.** Uruchom
    `./gradlew :app:releaseChecklist :app:bundleRelease --console=plain`.
    `assembleRelease` uruchamia tę samą checklistę; **debug jej nie uruchamia**.
    Zbadaj podpis i przeskanuj artefakt; nie ignoruj czerwonej checklisty.
11. **Wdrożenie i kontrola.** Wgraj AAB na tor *internal testing*,
    sprawdź instalację z Google Play, logowanie, rankingi, chmurę i crash logi.
    Dopiero po zatwierdzeniu wyników przejdź do docelowego toru publikacji.
    Po **faktycznym opublikowaniu** wpisz użyty kod do
    `release/last_version_code.txt` i zatwierdź zmianę — kolejny release
    będzie wymagał większego kodu.

### Automatyczna blokada Gradle i świadome wyjątki

Zadanie `:app:releaseChecklist` odczytuje `Daily.RELEASE_DAY` bezpośrednio
z `Modes.kt` oraz dzisiejszy dzień **UTC**. Zgłasza wszystkie problemy
jednym polskim komunikatem i blokuje `:app:assembleRelease` oraz
`:app:bundleRelease`, jeśli:

- `RELEASE_DAY` jest dawniej niż 30 dni temu lub dalej niż 60 dni w przyszłości;
- `play_games_ids.properties` nie zawiera kompletu prawidłowych ID (generator release blokuje wydanie);
- `CW_SERVER_URL` lub `CW_PRIVACY_URL` nie jest poprawnym adresem HTTPS;
- `CW_HMAC_KEY` jest pusty;
- `releaseVersionCode` nie przewyższa `release/last_version_code.txt`
  albo ten plik ma niepoprawną zawartość;
- nie istnieje kompletny i poprawnie wskazany keystore upload lub podano
  standardowy podpis debug (ta kontrola jest **niepomijalna** przy normalnym release);
- brakuje `docs/store/OWNER_INPUT.md`, któreś z jego pięciu pól pozostaje
  niewypełnione albo ma nieprawidłowy format (e-mail, URL, data, ID PGS);
- brakuje dokumentów `privacy_policy_pl.md`, `privacy_policy_en.md`,
  `data_safety.md` lub `content_rating.md`, lub jakikolwiek dokument
  `docs/store/*.md` zawiera znacznik do uzupełnienia typu INSERT,
  UZUPEŁNIJ albo DO DECYZJI. Dozwolone są tylko znane pola szablonu
  OWNER, podstawiane przez `renderStorePolicies`.

Przy **świadomej decyzji** można pominąć pojedyncze kontrole zewnętrznej
konfiguracji przez `-PskipCheck=releaseDay`, `playGames`, `serverUrl`,
`privacyUrl`, `hmacKey` lub `versionCode`; wiele wartości oddziel przecinkami.
**Nie umożliwia to pominięcia podpisu ani znaczników dokumentów.**
`ownerInput` można pominąć jedynie w oznaczonym smoke-teście CI
z `-PciTestDebugSigning=true`; nie jest to opcja publikacyjna. Przykład:

```bash
./gradlew :app:assembleRelease -PskipCheck=playGames,releaseDay
```

Wyjątek `playGames` nie wyłącza walidacji generatora zasobów release.
Wyjątek **nie konfiguruje** brakującej usługi ani nie naprawia podpisu.
Ostateczna publikacja wymaga zaliczenia całej listy kontrolnej bez flag pomijających.
Workflow `release-check.yml` świadomie pomija konfigurację niezbędną
wyłącznie do publikacji; produkcyjny workflow z taga nie pomija niczego.

### Kontrola rzeczywistego podpisu

Po zbudowaniu sprawdź certyfikat APK i porównaj jego SHA-256 z kluczem upload
właściciela (certyfikat Play App Signing w Google Play może być inny):

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore /bezpieczna/sciezka/upload-key.jks -alias TWOJ_ALIAS
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Na potrzeby publikacji uruchom oba polecenia build bez `ciTestDebugSigning`
i bez `skipCheck`. W Play Console wybierz najpierw tor testów wewnętrznych,
potwierdź działanie rankingu, chmury, usuwania danych i polityki, dopiero potem
uruchom publikację.
