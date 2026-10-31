# Grafiki karty Google Play (PL i EN)

Ten katalog opisuje **generator zrzutów z prawdziwego renderera i ekranów gry**,
bez zewnętrznych grafik i bez zmian w produkcyjnym kodzie.

## Uruchomienie

Wymagane: JDK 17, Android SDK 36, Android Gradle Plugin z projektu i zasoby testowe Robolectric (SDK 35).

```bash
# Projekt wywołuje test tylko na wyraźne żądanie:
STORE_GRAPHICS=true ./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest*' --no-configuration-cache --console=plain

# Zwykły build i testy NIE generują grafik (JUnit oznacza test jako pominięty):
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest*' --no-configuration-cache --console=plain
```

Opcjonalnie użyj GitHub Actions: workflow **Store graphics** z gałęzi
`gpt/50-store-graphics`; wynik jest do pobrania jako artefakt `century-war-store-graphics`.
Grafiki powstają lokalnie z kodu, bez połączeń z siecią podczas renderowania.

## Wynik

Dwa równoległe zestawy w `app/build/store/{pl,en}/`:

| Pliki | Ilość na język | Wymiary |
| --- | ---: | --- |
| `phone/01-frontline.png` … `phone/08-daily-challenge.png` | 8 | 1920 × 1080, poziomo, 16:9 |
| `tablet7/01-frontline.png` … `tablet7/08-daily-challenge.png` | 8 | 1920 × 1200, poziomo, 8:5 |
| `tablet10/01-frontline.png` … `tablet10/08-daily-challenge.png` | 8 | 2560 × 1600, poziomo, 8:5 |
| `icon_512.png` | 1 | 512 × 512, PNG RGBA (32-bit) |
| `feature_1024x500.png` | 1 | 1024 × 500, PNG RGB (24-bit) |
| `manifest.tsv` | 1 | Ścieżka, rozdzielczość, tekst alternatywny |

**Razem: 26 PNG na język, 52 PNG i 2 manifesty.** Każdy plik PNG jest
sprawdzany pod względem rozmiaru i typu koloru bezpośrednio w teście.
Wygenerowane PNG są artefaktami builda, nie są dopisywane do repo.

## Sceny

| Nr | Plik | Ujęcie |
| --- | --- | --- |
| 01 | `frontline` | Armie epoki żelaza; walka wręcz i efekt trafienia |
| 02 | `barrage` | Epoka stali, deszcz, celowany ostrzał i eksplozje |
| 03 | `endless-boss` | Rzeczywisty boss 10. fali Endless i jednostki obu drużyn |
| 04 | `night` | Nocna walka w erze orbitalnej |
| 05 | `era-variants` | Rzeczywisty modal wyboru wariantu epoki pary |
| 06 | `campaign` | Kampania z gwiazdkami z przygotowanego testowego profilu |
| 07 | `research` | Badania i medale z przygotowanego testowego profilu |
| 08 | `daily-challenge` | Rzeczywisty ekran wyzwania dnia |

Bitwy są składane **z jednostek i efektów renderowanych przez `BattleRenderer`**,
nie z makiet: stałe seedy, ręcznie ustawione pozycje i stały krok symulacji.
Sceny 05–08 korzystają z ekranów produkcyjnych gry, więc interfejs jest
lokalizowany przez `L.kt`. Dodatkowe krótkie hasła i opisy alternatywne
znajdują się osobno w `app/src/test/resources/store/graphics_pl.properties`
oraz `graphics_en.properties`. Pozostałe osiem języków nie jest wymagane.

Jedyny zmienny element między dniami: scena 08 odzwierciedla
**aktualne wyzwanie UTC** (tak działa właściwy ekran gry). Nie jest
fałszowane ani zamrażane dla materiałów promocyjnych. Brak danych osobowych,
identyfikatorów kont i HUD-u diagnostycznego.

## Wymagania Google Play (sprawdzone 31.10.2026)

Źródło: [Google Play Console — Add preview assets to showcase your app](https://support.google.com/googleplay/android-developer/answer/9866151).

- **Zrzuty:** JPEG lub PNG 24-bit bez alfa; min. 320 px, maks. 3840 px na bok;
  dłuższy wymiar nie może przekroczyć dwukrotności krótszego. Co najmniej
  2 zrzuty do publikacji i do 8 na typ urządzenia. Gry: zalecane co
  najmniej 3 obrazy 1920 × 1080 w 16:9, pokazujące faktyczną rozgrywkę.
- **Tablety 7/10 cali:** można dodać do 8 na typ. Google mocno zaleca
  co najmniej 4 i proporcje 16:9 dla lepszej prezentacji na dużych ekranach;
  powyższe **8:5** odpowiada natywnym przykładom ze zlecenia i prawdziwym
  układom tabletu, ale do ekspozycji promowanej warto dodatkowo przygotować
  natywne kadry 16:9. Nie rozciągać gotowych zrzutów.
- **Ikona sklepu:** PNG 32-bit z alfa, 512 × 512, maksymalnie 1024 KB.
  Generator renderuje dwie warstwy istniejącej ikony adaptacyjnej Androida
  (tło + wektor tarczy).
- **Feature graphic:** 1024 × 500, JPEG albo PNG 24-bit bez alfa.
  Generator rysuje scenę gry i wyłącznie nazwę „CENTURY WAR”.
- **Opisy alternatywne:** w `manifest.tsv`, krótkie (do 140 znaków).
  Przed publikacją przejrzeć obrazy ręcznie, zwłaszcza czytelność napisów,
  ewentualne obcięcia na dużym ekranie i zgodność z aktualnym buildem.

Ważne: Google zaleca na tabletach unikać reklamowych nakładek tekstowych,
które mogą być przycinane w niektórych ekspozycjach. Generator pozostawia
krótkie hasła zgodnie ze zleceniem, ale przy bezpośrednim wgraniu do Play
Console warto zweryfikować w podglądzie, czy nie należy ich wyłączyć dla
tabletów.

## Kontrola po wygenerowaniu

1. Sprawdź `pl/manifest.tsv` i `en/manifest.tsv`: zestaw 8 obrazów na każdy typ.
2. Obejrzyj pierwsze trzy screenshoty telefonu oraz modal wariantu epoki.
3. Skontroluj czytelną treść PL/EN, brak obciętych przycisków, realistyczny
   wygląd i wymiary tabletów. Generator nie potwierdza wyglądu na prawdziwym urządzeniu.
4. Przed publikacją wyeksportuj obrazy z buildu odpowiadającego wydanej wersji gry.
