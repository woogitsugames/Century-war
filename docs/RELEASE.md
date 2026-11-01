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
  Gra nie używa refleksji (profil i zrzut bitwy to ręczny JSON / ręczny format binarny,
  jedyna Activity jest w manifeście), więc `proguard-rules.pro` nie potrzebuje reguł keep.
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

- `android:localeConfig` (`res/xml/locales_config.xml`) deklaruje 10 języków gry:
  `pl, en, de, es, fr, it, pt, uk, nl, tr` — dokładnie `L.CODES` z `engine/L.kt`
  (sprawdza to `ReleaseConfigTest`). Dodając język, dopisz go w obu miejscach.
- `android:fullBackupContent` (Android 11 i starsze) oraz `android:dataExtractionRules`
  (Android 12+) obejmują wyłącznie `filesDir/profile.json`. `battle.snap` (zrzut
  przerwanej bitwy) jest przejściowy i celowo pominięty — w kopii w chmurze i przy
  przenoszeniu na nowe urządzenie. Pliki `.tmp` i `.corrupt` również.

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
  refleksyjnego ładowania klas/metod/pól, ani serializacji przez refleksję.
  Komponenty manifestu mają reguły generowane przez AGP, dlatego
  **nie dodano szerokiego `-keep`**. Jeśli pojawią się później
  refleksyjne wywołania, trzeba dopisać precyzyjne reguły i test.
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


## Lista kontrolna dnia wydania — dla właściciela

Przed pierwszą publikacją i przed **każdą kolejną** aktualizacją przejdź kroki w podanej kolejności.

**Krok 0 — dane wydawcy (obowiązkowy):** otwórz
[docs/store/OWNER_INPUT.md](store/OWNER_INPUT.md), uzupełnij sześć pól:
pełna nazwa prawna wydawcy, e-mail kontaktowy, adres korespondencyjny,
publiczny URL polityki HTTPS, rzeczywista data pierwszego wydania UTC,
numeryczny identyfikator projektu Play Games. Nie wpisuj tutaj sekretów.
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
4. **Play Games i rankingi.** W Play Console utwórz usługę gier, OAuth i cztery
   tablice (endless, daily, campaign, skirmish). Zastąp wszystkie `REPLACE_ME`
   w `app/src/main/res/values/games_ids.xml`. Skonfiguruj testerów i Saved
   Games; przeprowadź rzeczywiste logowanie, zapis w chmurze i publikację wyników.
   Szczegóły: [PLAY_GAMES_SETUP.md](PLAY_GAMES_SETUP.md).
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
- manifest deklaruje Play Games, a `games_ids.xml` nadal ma `REPLACE_ME`;
- `CW_SERVER_URL` lub `CW_PRIVACY_URL` nie jest poprawnym adresem HTTPS;
- `CW_HMAC_KEY` jest pusty;
- `releaseVersionCode` nie przewyższa `release/last_version_code.txt`
  albo ten plik ma niepoprawną zawartość;
- nie istnieje kompletny i poprawnie wskazany keystore upload lub podano
  standardowy podpis debug (ta kontrola jest **niepomijalna** przy normalnym release);
- brakuje `docs/store/OWNER_INPUT.md`, któreś z jego sześciu pól pozostaje
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
