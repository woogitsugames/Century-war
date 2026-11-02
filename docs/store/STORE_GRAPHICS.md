# Grafiki Google Play — zlecenie 117 (10 języków)

## Odświeżenie po Wodzu i stopniach wież — zlecenie 153 (2026-11-02)

Nowy zestaw jest **oddzielony od archiwalnego eksportu 8 ekranów**. Nie zmieniono rozgrywki ani UI. Generator `StoreGraphicsTest.generate153` wyświetla produkcyjne ekrany `BattleScreen` i `CampaignScreen`, a sceny pola bitwy bazują na deterministycznym seedzie. Przywołanie Wodza i podnoszenie stopni wież odbywa się przez `BattleController`. Karty Endless są otwartym stanem prawdziwego wyboru, a wykres bazuje na próbkach rzeczywistej symulacji zakończonej wynikiem. Profil testowy wyłącza samouczek, podpowiedzi, powiadomienia i pozostałe nakładki.

**Języki nowych grafik:** polski, angielski i kontrolnie niemiecki. Wszystkie 10 języków `listing_*.md` zawiera Wodza/dowódcę oraz trzy stopnie wież zarówno w krótkim, jak i pełnym opisie. Test `listingLimits` weryfikuje limit długości i obecność obu funkcji w każdym języku.

### Kadry zadania 153

| Nr | Scena (autentyczna zawartość gry) |
| --- | --- |
| 01 | Bitwa z Wodzem i widoczną aurą wspierającą pobliską armię |
| 02 | Panel zakupionych wież ze stopniami 2 i 3, pozostałe opcje |
| 03 | Mapa kampanii z etapami oraz gwiazdkami |
| 04 | Wybór kart Endless na tle bitwy z bossem |
| 05 | Ekran wyniku z zakładką **Wykres** i próbkami symulacji |

Każda scena jest renderowana w czterech rozdzielczościach:

| Katalog | Wymiary | Zastosowanie |
| --- | --- | --- |
| `phone16x9` | 1920×1080 | Telefon — **do Play** |
| `phone20x9_qa` | 2400×1080 | Telefon 20:9 — **tylko QA, nie publikować w Play** |
| `tablet10` | 2560×1440 | Tablet — **do Play** |
| `fold` | 2184×1968 | Rozłożony Fold — **do Play po kontroli kadru** |

**Dlaczego 20:9 jest tylko QA:** 2400/1080 = 2,22, a Google Play ogranicza stosunek boków zrzutów do **2:1**. Nie wolno uploadować plików z `phone20x9_qa` bez przekształcenia do dopuszczalnych proporcji. Dostępność kategorii tabletów w Play Console jest niezależna od tego, czy obraz pochodzi z Folda.

Każdy język ma **20 zrzutów** (15 zgodnych z proporcjami Play i 5 QA 20:9), **1 grafikę feature**, **1 stykówkę QA** oraz `manifest.tsv` zawierający flagę `play_upload`. W sumie nowe zadanie generuje **60 zrzutów, 3 feature graphics, 3 stykówki i 3 manifesty**. Feature `feature_1024x500.png` rysuje kodem stylizowaną bitwę z Wodzem i logo Century War w oryginalnej oprawie gry, bez przezroczystości. Teksty alternatywne są lokalizowane, mają maksymalnie 140 znaków.

### Eksport, test i przekazanie PNG

```bash
STORE_GRAPHICS_153=true ./gradlew :app:testDebugUnitTest \
  --tests '*StoreGraphicsTest.generate153*' \
  --tests '*StoreGraphicsTest.storeAssetRequirements*' \
  --tests '*StoreGraphicsTest.listingLimits*' \
  --no-configuration-cache --console=plain
python3 scripts/validate_store153.py app/build/store153
```

Wynik: `app/build/store153/{pl,en,de}/`. Workflow `.github/workflows/store-153.yml` na gałęzi `gpt/153-store-assets` buduje APK, uruchamia testy, sprawdza wymiary i kolory PNG, publikuje artefakt `century-war-store153` oraz dodaje rzeczywiste pliki PNG do `docs/store/assets/153/` na tej samej gałęzi (zrzuty z Play oraz osobno oznaczone podglądy QA). To pozwala obejrzeć pliki bezpośrednio w PR.

**Nie dodawać wszystkich 13 telefonicznych kadrów jednocześnie:** stary zestaw ma 8 ekranów, a nowe sceny są kolejnymi kandydatami. Limit Play to 8 zrzutów na typ urządzenia; wybierz do ostatecznego zestawu pięć nowych scen i maksymalnie trzy z poprzedniej serii.

### Potwierdzone wymagania Google Play (2026-11-02)

- Feature graphic: **1024×500**, JPEG lub 24-bitowy PNG bez alfa.
- Screenshoty: JPEG lub 24-bitowy PNG bez alfa; bok 320–3840 px, proporcje **do 2:1**, do ośmiu plików na kategorię urządzenia.
- Krótki opis: do **80 znaków**, pełny opis: do **4000 znaków**, nazwa: do **30 znaków**.
- Zrzuty i opisy muszą wiernie prezentować działanie gry. Nie umieszczać ocen, rankingów sklepu, obietnic nieistniejących funkcji ani elementów cudzej gry.
- Źródła: [Play Console — Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en), [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Best practices for your store listing](https://support.google.com/googleplay/android-developer/answer/13393723?hl=en).

### Kontrola jakości przed uploadem

Obejrzeć 3 stykówki i pełnowymiarowe PNG scen 01/02/04/05, zwłaszcza etykiety wież, karty na Foldzie i wykres na telefonie. Validator sprawdza format/wymiary i układ danych, lecz **nie zastępuje wizualnej kontroli obciętych tekstów**. Po przeglądzie w Play Console zatwierdzić tylko wpisy manifestu z `play_upload=true`, a dla każdej lokalizacji wybrać poprawne językowo zasoby.

---

Generator `StoreGraphicsTest` renderuje produkcyjne ekrany i sceny `BattleRenderer`. Zawartość obrazu pochodzi z gry, a lokalizowane nakładki i opisy są osobnymi zasobami. Dodatkowe podglądy Folda pokazują **prawdziwy HUD i dolny pasek akcji** z `BattleScreen` w obu orientacjach, bez symulowania elementów interfejsu.

## Eksport i uruchomienie

Wymagane: JDK 17, Android SDK 36, Robolectric SDK 35. Wynik nie trafia do repozytorium.

```bash
STORE_GRAPHICS=true ./gradlew :app:testDebugUnitTest --tests '*StoreGraphicsTest*' --no-configuration-cache --console=plain
python3 scripts/validate_store_graphics.py app/build/store
```

Domyślnie tworzone są wszystkie języki `L.CODES`: **pl, en, de, es, fr, it, pt, uk, nl, tr**. Można ograniczyć zestaw do kontroli lokalnej lub równoległej pracy CI, np. `STORE_LANGUAGES=pl,en`. Nieobsługiwane i powtórzone kody powodują błąd.

Workflow **Store graphics** w GitHub Actions ma pięć równoległych grup językowych i zadanie końcowe zbierające oraz walidujące całość. Artefakt: `century-war-store-graphics`; raporty testów są osobnymi artefaktami.

## Pliki wynikowe `app/build/store/{język}/`

| Zasób | Liczba / język | Rozmiar | Przeznaczenie |
| --- | ---: | --- | --- |
| `phone/01-menu.png` … `phone/08-custom-skirmish.png` | 8 | 1920 × 1080 | Google Play — telefon |
| `tablet7/01-menu.png` … `tablet7/08-custom-skirmish.png` | 8 | 1920 × 1080 | Google Play — tablet 7″ |
| `tablet10/01-menu.png` … `tablet10/08-custom-skirmish.png` | 8 | 2560 × 1440 | Google Play — tablet 10″ |
| `icon_512.png` | 1 | 512 × 512, PNG RGBA | Google Play — ikona |
| `feature_1024x500.png` | 1 | 1024 × 500, PNG RGB | Google Play — grafika promocyjna |
| `preview_foldLandscape_hud.png` | 1 | 2184 × 1968 | **Tylko QA** — Fold poziomo |
| `preview_foldPortrait_hud.png` | 1 | 1968 × 2184 | **Tylko QA** — Fold pionowo |
| `preview_contact.png` | 1 | 1600 × 960 | **Tylko QA** — stykówka |
| `manifest.tsv` | 1 | 26 wpisów | Metadane i teksty alt |

Na język: **26 grafik sklepu + 3 obrazy kontrolne = 29 PNG** oraz jeden `manifest.tsv`. Łącznie **260 PNG do Google Play, 30 podglądów QA, 10 manifestów**. Podglądy Folda **nie są** osobną kategorią uploadu do Play Console.

Wszystkie grafiki sklepu są PNG 8-bit RGB (bez kanału alfa), poza ikoną PNG RGBA. Alt ≤ 140 znaków i opisuje rzeczywistą scenę.

## Sceny

1. Menu — logo i emblemat.
2. Kampania — 36 etapów i gwiazdki profilu testowego.
3. Boss Endless — boss fali dziesiątej i jednostki wraz z prawdziwym HUD.
4. Nocny ostrzał — noc, salwa i eksplozje na produkcyjnym ekranie bitwy z paskiem akcji.
5. Wariant epoki — rzeczywisty wybór wariantu pary.
6. Pauza — produkcyjny panel ze statystykami.
7. Oblężenie tygodnia — produkcyjny ekran wyzwania.
8. Własna potyczka — ekran wyboru zasad.

Feature graphic używa własnego emblematu menu i nocnej sceny bitwy; ikona pochodzi z adaptacyjnych warstw Androida. Nie dodawać ocen, cudzych znaków, ani nieistniejących funkcji.

## Wymagania Google Play (sprawdzono 2026-11-01)

- Zrzuty: **JPEG lub PNG 24-bit (bez alfa)**; najkrótszy bok ≥ 320 px, najdłuższy ≤ 3840 px, stosunek długości boków ≤ 2:1; maks. **8 na kategorię urządzenia**; do publikacji potrzebne przynajmniej 2.
- Dla gier rekomendowane minimum **3 zrzuty 16:9, co najmniej 1920 × 1080**; na tabletach rekomendowane minimum 4 zrzuty i **16:9** poziomo. Stąd oba zestawy tabletowe mają 16:9, ale odmienne DPI/układ produkcyjnego UI.
- Ikona: **512 × 512 PNG 32-bit (alfa), maksimum 1024 KB**.
- Feature graphic: **1024 × 500 JPEG lub PNG 24-bit bez alfa**.
- Teksty alternatywne: **maksimum 140 znaków**.
- Tytuł aplikacji: do 30 znaków, krótki opis do 80, pełny do 4000. Istniejący test `listingLimits` sprawdza listy PL/EN.

Wymogi i zalecenia aktualne na wskazany dzień: https://support.google.com/googleplay/android-developer/answer/9866151

Walidator `scripts/validate_store_graphics.py` sprawdza liczbę plików, wymiary, typ PNG, nazwę, limity ikony, spójność `manifest.tsv` i opisy alt.

## Kontrola przed publikacją

1. Otworzyć stykówki wszystkich 10 języków, a potem pełne kadry telefonu i obu tabletów.
2. Sprawdzić łamanie tekstu, lokalizację UI, variant, pauzę, noc, oblężenie i oba podglądy Folda, zwłaszcza dolny pasek.
3. Zestawić grafikę z finalnym AAB; sprawdzić na urządzeniach fizycznych i w podglądzie Play Console.
4. Upewnić się, że do Play Console trafiają wyłącznie **26 pozycji z manifestu** na język, a nie podglądy QA.
5. Przed publikacją ponownie wygenerować scenę wyzwania tygodnia, bo opiera się na aktualnym tygodniu UTC.

**Brak automatycznego wysyłania do Play Console.** Zatwierdzenie i upload pozostają osobnym krokiem.
