# Grafiki Google Play — odświeżenie 75 (PL/EN)

Generator `StoreGraphicsTest` renderuje prawdziwe ekrany gry i sceny `BattleRenderer`, bez przerabiania kodu produkcyjnego. Wersje PL i EN mają własne napisy, alternatywne opisy i interfejs w odpowiednim języku.

## Uruchomienie

Wymagane: JDK 17, Android SDK 36, zasoby Robolectric SDK 35.

```bash
STORE_GRAPHICS=true ./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest*' --no-configuration-cache --console=plain
```

Bez `STORE_GRAPHICS=true` test eksportujący jest pomijany; niezależny test `listingLimits` sprawdza opisy sklepu przy zwykłym uruchomieniu.

GitHub Actions: workflow **Store graphics**, uruchamiany na zmianach generatora w gałęziach `gpt/50-store-graphics` i `gpt/75-store-refresh`. Artefakt: `century-war-store-graphics`.

## Pliki w `app/build/store/`

| Wzorzec w `{pl,en}/` | Liczba na język | Wymiary | Do Play Console? |
| --- | ---: | --- | --- |
| `phone/01-menu.png` … `phone/08-custom-skirmish.png` | 8 | 1920 × 1080, RGB | Tak |
| `tablet7/01-menu.png` … `tablet7/08-custom-skirmish.png` | 8 | 1920 × 1200, RGB | Tak, po kontroli kadru |
| `tablet10/01-menu.png` … `tablet10/08-custom-skirmish.png` | 8 | 2560 × 1600, RGB | Tak, po kontroli kadru |
| `icon_512.png` | 1 | 512 × 512, RGBA | Tak |
| `feature_1024x500.png` | 1 | 1024 × 500, RGB | Tak |
| `preview_contact.png` | 1 | 1600 × 960, RGB | **Nie** — miniatury do przeglądu |
| `manifest.tsv` | 1 | Ścieżka, wymiary, alt | **Nie** — metadane |

Razem: **27 PNG i 1 manifest na język**, tj. 54 PNG i 2 manifesty. W `manifest.tsv` wpisujemy tylko 26 właściwych grafik sklepu. Zasoby są artefaktami builda i nie są dopisywane do repozytorium.

## Osiem scen

1. **Menu** — aktualne złote logo i emblemat epok z `MenuScreen`.
2. **Kampania** — produkcyjna mapa i gwiazdki lokalnego profilu testowego.
3. **Boss Endless** — prawdziwy boss fali 10 oraz jednostki oryginalnego renderera.
4. **Nocny ostrzał** — `Weather.NIGHT`, komenda `BattleController.barrage` i efekty trafienia.
5. **Wariant epoki** — rzeczywisty awans z epoki prochu do pary przez `BattleController.evolve()` i modal `BattleScreen`.
6. **Pauza** — rzeczywisty panel pauzy ze statystykami bitwy, nie imitacja interfejsu.
7. **Oblężenie tygodnia** — produkcyjny `ModeScreen`, zakładka `weeklySelected`.
8. **Własna potyczka** — produkcyjny `SkirmishSetupScreen` i jego ustawienia.

Feature graphic łączy rzeczywiste tło bitwy z **`MenuEmblem` z nowego logo menu** i jego złotą typografią. Ikona jest renderowana z istniejących adaptacyjnych warstw Androida (zlecenie 58).

## Kontrola przed publikacją

- PNG: wymagany typ koloru RGB (24-bit) dla zrzutów i feature, RGBA dla ikony; generator sprawdza sygnaturę, wymiary i typ koloru.
- Zrzuty: min. 320 px i max. 3840 px na bok, proporcje maks. 2:1, do 8 na typ urządzenia.
- Ikona: maks. 1024 KB. Feature graphic: dokładnie 1024 × 500.
- Alt: krótki (do 140 znaków), opisujący faktyczną scenę, nie samo hasło.
- Najpierw obejrzyj miniatury `preview_contact.png` PL i EN, następnie pełne PNG na telefon i tablety.
- Zakładka tygodnia bazuje na bieżącej dacie UTC; jej szczegóły mogą się zmienić między eksportami.
- Sprawdź na finalnym AAB, czy przyciski, podpisy, pogoda i logo zgadzają się z wydaniem; dodatkowo test na rzeczywistych urządzeniach.

Źródło: https://support.google.com/googleplay/android-developer/answer/9866151
