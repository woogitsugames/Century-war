# CI Android: Century War

Workflow: [`.github/workflows/android.yml`](../.github/workflows/android.yml).

## Automatyczne budowanie

- Push i pull request skierowany do `main`, `claude/**` lub `gpt/**` uruchamiaja job `build-and-test` (JDK 17 Temurin, Gradle, Android SDK 36). Powstaje debug APK oraz raporty JUnit (`BattleSimTest`).
- `lint` uruchamia `:app:lintDebug`. To job nieblokujacy, ale jego wynik i raport HTML/XML nalezy sprawdzic.
- W zakladce **Actions** wybierz run i na dole strony pobierz artefakty: `century-war-debug-apk`, `battle-sim-test-reports`, `android-lint-report`.
- `ScreenshotTest` nie jest uruchamiany przy kazdym pushu (wolny i kosztowny). Aby go uruchomic, otworz **Actions > Android CI > Run workflow**, wybierz galaz i uruchom workflow. Osobny job `screenshots` zapisuje PNG jako `century-war-screenshots` oraz raport testow. Cache obejmuje `~/.m2/repository/org/robolectric`.

## Keystore do podpisywania release

Wygeneruj nowy klucz upload do Google Play i przechowuj plik oraz hasla poza repozytorium. Przy pierwszym publikowaniu wybierz trwale wartosci — zmiana klucza upload bedzie wymagac procedury w Google Play Console.

```bash
keytool -genkeypair -v \
  -keystore century-war-upload.jks \
  -alias century-war-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Komenda interaktywnie poprosi o haslo keystore i haslo klucza (moga byc takie same). Wygeneruj zawartosc sekretu `KEYSTORE_BASE64` z pliku `.jks`:

```bash
base64 -w 0 century-war-upload.jks > century-war-upload.b64   # Linux
# Na macOS: base64 < century-war-upload.jks | tr -d '\n'
```

W repozytorium GitHub otworz **Settings > Secrets and variables > Actions > New repository secret** i dodaj cztery sekrety:

| Sekret | Wartosc |
|---|---|
| `KEYSTORE_BASE64` | Cala zawartosc zakodowanego Base64 pliku `.jks` (bez nowych linii) |
| `KEYSTORE_PASSWORD` | Haslo do keystore |
| `KEY_ALIAS` | Alias klucza, np. `century-war-upload` |
| `KEY_PASSWORD` | Haslo do klucza |

Nie umieszczaj keystore, zakodowanego Base64 ani hasel w commitach, artefaktach lub logach. Zachowaj kopie zapasowa `.jks` i danych logowania w bezpiecznym menedzerze sekretow. GitHub Actions odtwarza plik tylko w katalogu tymczasowym runnera; nie zapisuje go w artefaktach.

## Release po tagu

Push taga `v*` (np. `v0.1.0`) uruchamia wylacznie job `release`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Przed tagowaniem upewnij sie, ze commit zawiera workflow i przechodzi CI. Job **przerywa prace**, jesli brakuje choc jednego z czterech sekretow; nie wolno publikowac buildow produkcyjnych podpisanych kluczem debug. Po udanym buildzie dostepne sa artefakty `century-war-release-apk` i `century-war-release-aab`. To sa artefakty GitHub Actions, a nie automatyczna publikacja w Google Play.

## Lokalny release

Bez zmiennych srodowiskowych `:app:assembleRelease` i `:app:bundleRelease` nadal korzystaja z debug signingConfig, zeby nie popsuc dotychczasowego lokalnego procesu. Aby lokalnie sprawdzic podpis produkcyjny, ustaw wszystkie cztery zmienne (najlepiej w menedzerze sekretow, bez zapisywania ich w repo):

| Zmienna Gradle | Wartosc |
|---|---|
| `RELEASE_KEYSTORE_FILE` | Bezwzgledna sciezka do pliku `.jks` |
| `RELEASE_STORE_PASSWORD` | Haslo keystore |
| `RELEASE_KEY_ALIAS` | Alias klucza |
| `RELEASE_KEY_PASSWORD` | Haslo klucza |

Przy czesciowo ustawionych zmiennych konfiguracja Gradle zwroci blad zamiast niejawnie uzyc klucza debug.

## Diagnostyka

- Kod buduje sie na JDK 17 i Android SDK z `platforms;android-36` oraz `build-tools;36.0.0`.
- `BattleSimTest` dziala w JVM; nie wymaga emulatora. `ScreenshotTest` uzywa Robolectric (SDK 35) i moze pobierac duze artefakty, dlatego jest uruchamiany recznie.
- Artefakty z jobow debug/lint/screenshot sa przechowywane przez 14 dni, a release przez 90 dni.
- Raport z nieudanego kroku oraz diagnostyka `lint` znajduja sie na stronie uruchomienia workflow w zakladce **Actions**.