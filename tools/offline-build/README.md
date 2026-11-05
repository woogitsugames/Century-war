# Kompilacja i testy `:app` bez Gradle (obejście tymczasowe)

Te skrypty są **obejściem na czas blokady dl.google.com**. W tym czasie Gradle, AGP i Android SDK
nie działają. Skrypty **nie zastępują Gradle**. Pozwalają wyłapać błędy kompilacji `sim` + `app` i
uruchomić testy JVM/Robolectric z `app/src/test`, zanim sieć zacznie działać. Wynik „OK” nie
oznacza, że przejdzie `./gradlew build`.

Skrypty nie pobierają niczego z Google Maven ani dl.google.com. Proxy nie jest obchodzone.

## Wymagania

Narzędzia, które są już na maszynie:
- JDK 17+ (sprawdzone na 21),
- Python 3,
- Maven (`mvn`),
- `curl`,
- `apt-get` i `dpkg` (Ubuntu 24.04),
- kompilator Kotlin 2.0.21 z dystrybucji Gradle w `/opt/gradle-8.14.3/lib`. Projekt używa 2.2.20,
  ale ta wersja nie jest dostępna offline.

## Co pobiera `setup.sh` i skąd

Wszystko trafia do `$CW_OFFLINE_DIR` (domyślnie `/tmp/cw-offline`), nigdy do repo.

| Co | Skąd | Po co |
|---|---|---|
| `robolectric:4.16` + zależności, `junit:4.13.2` | Maven Central (mirror `maven-central.storage-download.googleapis.com`, zmiana przez `CW_MAVEN_MIRROR`) | testy |
| `android-all:16-robolectric-13921718` | Maven Central | klasy Androida do kompilacji (API 36 = `compileSdk`) |
| `android-all:14-robolectric-10818077` | Maven Central | baza linkowania zasobów (aapt2 2.19 nie czyta arsc API 35/36) |
| `android-all-instrumented` 9/10/15/16 | Maven Central | środowiska Robolectric dla `sdk = 28/29/35` i domyślnego 36 |
| pakiet `aapt` (aapt2 2.19-debian) i jego biblioteki | archive.ubuntu.com (`apt-get download`, rozpakowanie bez instalacji) | prawdziwe `R.java` i `resources.ap_` |

Sumy sha1 jarów `android-all` są sprawdzane.

## Czym te skrypty różnią się od Gradle/AGP

- **Play Games** (`com.google.android.gms`, tylko na Google Maven): `mkstubs.py` tworzy minimalne
  zaślepki, wyłącznie dla symboli używanych w kodzie.
- **`androidx.test:monitor`** (tylko Google Maven): `mkmonitor.py` tworzy 15 minimalnych klas, których
  Robolectric 4.16 używa w `AndroidTestEnvironment`.
- **`BuildConfig`**: `mkr.py` pisze go ręcznie, z wartościami jak w debug. Ten sam skrypt tworzy
  zapasowe `R`, które `build_res.py` zastępuje prawdziwym z aapt2.
- **Manifest**: `build_res.py` naśladuje scalanie manifestów. Dodaje `package`, podstawia
  `${applicationId}` i usuwa `tools:node="remove"`. Tworzy też `games_ids.xml` w wariancie debug,
  jak `PlayGamesIdsGenerator` przy `REPLACE_ME`.
- **Mockable android.jar**: `MockableJar.java` jest odpowiednikiem
  `unitTests.isReturnDefaultValues = true`.
- **JVM**: testy działają z `--add-opens java.base/java.io` i `java.base/java.lang` (JDK 21).
- **API `@hide`**: kompilacja idzie na implementacyjnym `android-all`. Użycie ukrytego API nie da
  tu błędu, choć z `android.jar` z SDK by dało.
- **Czego nie ma**: lintu, R8, `releaseChecklist`, budowy APK/AAB.

## Użycie

```bash
tools/offline-build/setup.sh                       # raz; pobiera i przygotowuje zależności
tools/offline-build/build_app.sh "$PWD"            # kompilacja sim + app (main + test)
tools/offline-build/run_tests.sh "$PWD" com.centurywar.screens.CodexFoldOuterLabelsTest
tools/offline-build/run_fast.sh "$PWD"             # odpowiednik fastTest (paczki po 25 klas)
tools/offline-build/build_sim.sh "$PWD/sim"        # :sim main + test i wszystkie testy :sim
```

Wyniki kompilacji trafiają do `$CW_OFFLINE_DIR/out`, a logi `run_fast.sh` do `$CW_OFFLINE_DIR/fast_*.log`.

Gdy Gradle znów zadziała, ten katalog można usunąć.
