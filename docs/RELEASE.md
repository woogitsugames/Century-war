# Wydanie Android / Google Play

## Numer wersji

- `releaseVersionCode` w `gradle.properties` to dodatni, rosnący numer publikacji w Google Play.
  **Zwiększ go przed każdym nowym wydaniem**, także gdy nie zmienia się `versionName`.
- `versionName` jest pobierane z tagu SemVer wskazującego **bieżący commit**
  (np. `v1.2.3` daje `1.2.3`). Bez tagu stosowane jest
  `releaseVersionName` z `gradle.properties` (`0.1.0-dev`).
- Wersjonowanie korzysta z `providers.exec`, zgodnego z cache konfiguracji Gradle.
  Przy budowaniu z tagu pobierz pełną historię i tagi (`git fetch --tags`).

## Podpisywanie

Zwykłe `assembleRelease` i `bundleRelease` **bez sekretów** budują
**niepodpisane** artefakty. Nigdy nie publikuj ich ani plików podpisanych debug key.

Do podpisania produkcyjnego ustaw w środowisku cztery zmienne:

```bash
export CW_UPLOAD_STORE_FILE="/bezpieczna/sciezka/upload-key.jks"
export CW_UPLOAD_STORE_PASSWORD="..."
export CW_UPLOAD_KEY_ALIAS="..."
export CW_UPLOAD_KEY_PASSWORD="..."
./gradlew :app:assembleRelease :app:bundleRelease --console=plain
```

Zachowaj keystore i hasła poza repozytorium, skonfiguruj **Play App Signing**
w Play Console i przekaż podpisany plik
`app/build/outputs/bundle/release/app-release.aab`.

Wyłącznie do testu urządzeniowego lub w CI:

```bash
./gradlew :app:assembleRelease :app:bundleRelease -PreleaseTestSigning=true
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n com.centurywar.game/.MainActivity
adb shell pidof com.centurywar.game
```

Flagi `releaseTestSigning` nie wolno używać do publikacji; profil release
jest wtedy nadal optymalizowany R8, ale podpisany lokalnym debug key.

## AAB, optymalizacja i Baseline Profile

- `com.android.application` udostępnia zadanie `bundleRelease` bez dodatkowych wtyczek.
  Brak kodu natywnego, więc osobne podziały ABI nie mają zastosowania; Play dystrybuuje
  odpowiednie zasoby z AAB.
- AGP 8.13 stosuje R8 full mode; `android.enableR8.fullMode=true`
  utrzymuje konfigurację jawną. Minifikacja i usuwanie zasobów są włączone.
  Gra zapisuje JSON bez refleksji, a jedyna Activity jest wskazana w manifeście.
  Ostateczną poprawność R8 potwierdza uruchomienie release na emulatorze.
- `app/src/main/baseline-prof.txt` zawiera ręcznie wybrane metody startu, pętli
  renderowania i symulacji. AGP pakuje profil do APK/AAB i aktualizuje deskryptory
  po R8. Na instalacjach z Play Android 9+ wykorzystuje profil podczas instalacji.
- Nie dodano `androidx.profileinstaller`: wymagałby zależności w aplikacji,
  a profil jest dystrybuowany przez Play na Androidzie 9+. Na Androidzie 8/8.1
  profil **nie zostanie automatycznie zainstalowany** bez ProfileInstaller.
  Ewentualną korzyść wydajnościową należy mierzyć na telefonach docelowych.

## Języki i backup

- `android:localeConfig` deklaruje `pl` i `en`. Teksty UI są obsługiwane przez
  istniejący `engine/L.kt`; warto sprawdzić systemowy wybór języka w ustawieniach
  Androida oraz ręczny wybór języka w samej grze.
- `android:fullBackupContent` dla Androida 11 i starszych oraz
  `android:dataExtractionRules` dla Androida 12+ obejmują jedynie
  `filesDir/profile.json`. Pozostałe dane, w tym cache i pliki `.tmp`
  oraz `.corrupt`, nie są objęte backupem.

## Walidacja i rozmiar przed / po

Workflow `.github/workflows/release-check.yml` pobiera gałąź bazową
`claude/bold-sagan-vwz5gm`, buduje jej APK/AAB, następnie buduje bieżącą gałąź
ze świadomie włączonym podpisem testowym, uruchamia:

```bash
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --console=plain
./gradlew :app:testDebugUnitTest --tests '*ReleaseConfigTest*' --console=plain
./gradlew :app:assembleRelease :app:bundleRelease -PreleaseTestSigning=true --console=plain
```

Workflow sprawdza obecność `baseline.prof` w APK/AAB, zawartość manifestu
przez `aapt`, raportuje dokładne rozmiary bajtowe APK i AAB w GitHub Actions
Job Summary i uruchamia aplikację na emulatorze API 35 (kontrola procesu
i `AndroidRuntime`). Logi i tabela rozmiarów trafiają do artefaktu
`release-walidacja`. Porównywane APK/AAB są podpisane debug key w obu wariantach
— to miarodajne porównanie rozmiarów, **nie** pliki do publikacji.

Po podpisaniu upload key i testach release pozostają jeszcze kroki w Play Console:
konfiguracja pakietu/aplikacji, polityki prywatności, deklaracje Data safety,
listing i wdrożenie w torze testowym.
