# Ikona aplikacji i splash (zlecenie 58)

Znak: oryginalna tarcza z niebieska i czerwona polowa (obie armie), zoltym obramowaniem i klepsydra (zmiana epok). Wszystkie kontury pozostaja czytelne przy 48 dp. Projekt nie korzysta z zasobow zewnetrznych.

## Zasoby

- `drawable/ic_launcher_background.xml` i `drawable/ic_launcher_foreground.xml`: dwie warstwy ikony adaptacyjnej, pole robocze 108 dp.
- `drawable/ic_launcher_monochrome.xml`: biala sylwetka z przezroczystymi otworami dla ikon tematycznych Androida 13+.
- `drawable/ic_daily_notification.xml`: odpowiednik 24 dp, bialy z przezroczystym tlem.
- `drawable/bg_launch.xml`: logo posrodku jednolitego tla (Android 8-11 i czas wczytywania aplikacji).
- `values-v31/themes.xml`: natywny Android 12+ splash bez AndroidX.

## Eksport do Google Play i podglad

```
./gradlew :app:testDebugUnitTest --tests '*AppIconScreenshotTest*' --no-configuration-cache --console=plain
```

Test renderuje **oryginalne Android VectorDrawable** przez `Canvas` i zapisuje PNG do `app/build/store/`:

- `icon-48.png`, `icon-96.png`, `icon-192.png` - podglad ikony adaptacyjnej z okragla maska;
- `icon-512.png` - format Google Play, bez zaokraglania; ten sam wektor, kadrowany do bezpiecznej strefy 18-90 dp;
- `icon-monochrome-192.png`, `icon-notification-192.png` - warianty jednokolorowe;
- `splash-phone720.png`, `splash-fold.png` - ekran startowy na dwoch proporcjach.

Kopie sa zapisywane do `app/build/screenshots/icons/` i trafiaja do artefaktu CI `century-war-screenshots`. PNG nie sa nowymi zasobami aplikacji, a wynikami testu. Nie dodano zaleznosci.
