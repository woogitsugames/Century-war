# Century War — plan aktualnych zrzutów Google Play (75)

Celem jest **8 autentycznych kadrów PL i 8 EN** na każdy typ urządzenia, wygenerowanych przez `StoreGraphicsTest` z produkcyjnych ekranów lub renderera gry. Wrzucamy do Play Console najwyżej osiem na typ. Nakładki marketingowe są osobne dla PL/EN i nie udają elementów interfejsu.

## Kolejność i podpisy

| Nr | Plik | Autentyczna scena | Hasło PL | Caption EN |
| --- | --- | --- | --- | --- |
| 01 | `01-menu.png` | Menu z aktualnym emblematem, złotym logo i trybami gry | Dowódź przez epoki | Command across the eras |
| 02 | `02-campaign.png` | Mapa 36 etapów, postęp i gwiazdki testowego profilu | Odkryj kampanię 36 etapów | Explore 36 campaign stages |
| 03 | `03-endless-boss.png` | Boss 10. fali Endless z oddziałami i efektami prawdziwego renderera | Zmierz się z bossami Endless | Face the Endless bosses |
| 04 | `04-night-barrage.png` | Nocna pogoda i celowany ostrzał; faktyczne efekty bitwy | Noc zmienia przebieg bitwy | Battle through the night |
| 05 | `05-era-variants.png` | Dwie prawdziwe karty wariantów epoki pary po wykonanym awansie | Wybierz wariant epoki | Choose your era variant |
| 06 | `06-pause-summary.png` | Panel pauzy z czasem, złotem, statystykami i ustawieniami | Planuj w każdej chwili | Pause and plan your next move |
| 07 | `07-weekly-siege.png` | Zakładka oblężenia tygodnia z zasadami i progami nagród | Podejmij oblężenie tygodnia | Take on the Weekly Siege |
| 08 | `08-custom-skirmish.png` | Konfigurator potyczki z zakresem epok, AI i modyfikatorami | Ustaw własną potyczkę | Customize your skirmish |

**Powtórki:** są funkcją opisywaną na karcie Google Play, lecz w obecnych ośmiu ujęciach priorytet mają nowe elementy taktyczne i tryby. Nie generujemy zmyślonej listy nagrań. Można wymienić dowolny kadr na autentyczny ekran odtwarzacza po nagraniu bitwy w finalnym buildzie.

## Eksport

- `phone/`: 1920 × 1080 (16:9), `tablet7/`: 1920 × 1200 (8:5), `tablet10/`: 2560 × 1600 (8:5). Render jest natywny, bez rozciągania.
- Każdy zestaw językowy: 24 zrzuty urządzeń, ikona 512 × 512 RGBA, feature graphic 1024 × 500 RGB oraz dodatkowy `preview_contact.png` **wyłącznie do przeglądu**, nie do Play Console.
- Krótkie hasła i teksty alternatywne: `app/src/test/resources/store/graphics_{pl,en}.properties`. Alt maks. 140 znaków.
- Overlay poza kluczowym HUD. Nie dodawać ocen, cudzych logo, nawiązań do innych gier ani nieistniejących mechanik.
- Weryfikować kadry PL/EN osobno, szczególnie łamanie tekstu, ekran wariantu, pauzę, nocny ostrzał, mapę i tablet.
- Data tygodniowego wyzwania jest rzeczywista i zależy od zegara UTC w chwili generowania; przed publikacją warto wygenerować zestaw ponownie.
- Ujęcia wymagają potwierdzenia w **finalnym AAB** i podglądzie Play Console. Obrazy wygenerowane przez Robolectric nie zastępują kontroli na fizycznym telefonie ani Foldzie.

## Kontrola jakości

1. Uruchom `STORE_GRAPHICS=true ./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest*' --no-configuration-cache --console=plain`.
2. Przejrzyj `app/build/store/pl/preview_contact.png` i `app/build/store/en/preview_contact.png`, następnie pełne PNG.
3. Potwierdź w `manifest.tsv`: 8 plików `phone`, 8 `tablet7`, 8 `tablet10`, ikona i feature graphic dla każdego języka.
4. Zweryfikuj wartości metadanych Google Play: 30 znaków tytułu, 80 krótkiego, 4000 pełnego opisu.
5. Porównaj wersję builda z wersją publikowaną i sprawdź obrazy w Play Console.

## Źródła

- https://support.google.com/googleplay/android-developer/answer/9866151
- https://support.google.com/googleplay/android-developer/answer/9859152
