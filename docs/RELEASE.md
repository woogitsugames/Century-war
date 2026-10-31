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

- Wszystkie cztery ustawione → release podpisany kluczem upload.
- Żadna nieustawiona → release podpisany **kluczem debug** (lokalne testy, jak dotąd).
  Takich plików **nie wolno publikować**.
- Część ustawiona → błąd konfiguracji Gradle (zamiast cichego użycia klucza debug).

W CI job `release` (push taga `v*`) odtwarza keystore z sekretów GitHub i przerywa pracę,
jeśli któregoś brakuje. Skonfiguruj **Play App Signing** w Play Console i wgraj
`app/build/outputs/bundle/release/app-release.aab`.

## AAB, R8 i Baseline Profile

- `bundleRelease` jest dostępne w `com.android.application` bez dodatkowych wtyczek.
  Brak kodu natywnego, więc podziały ABI nie mają zastosowania.
- Release: `isMinifyEnabled` + `isShrinkResources`; AGP 8.x domyślnie używa R8 full mode.
  Gra nie używa refleksji (profil i zrzut bitwy to ręczny JSON / ręczny format binarny,
  jedyna Activity jest w manifeście), więc `proguard-rules.pro` nie potrzebuje reguł keep.
- `app/src/main/baseline-prof.txt` zawiera ręcznie wybrane metody startu, pętli
  renderowania i symulacji. AGP kompiluje go do `assets/dexopt/baseline.prof` (APK)
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

## Walidacja

Lokalnie:

```bash
./gradlew :app:testDebugUnitTest --tests '*ReleaseConfigTest*' --console=plain
./gradlew :app:assembleRelease :app:bundleRelease --console=plain
```

Workflow `.github/workflows/release-check.yml` (PR do `main` i ręcznie) buduje release,
sprawdza obecność `baseline.prof` w APK/AAB, manifest przez `aapt`, raportuje rozmiary
w Job Summary i uruchamia aplikację na emulatorze API 35
(`com.centurywar.game/com.centurywar.MainActivity`, kontrola procesu i `AndroidRuntime`).
Logi trafiają do artefaktu `release-walidacja`.

Po podpisaniu upload key pozostają kroki w Play Console: polityka prywatności,
Data safety, listing (`docs/store/`) i wdrożenie w torze testowym.
