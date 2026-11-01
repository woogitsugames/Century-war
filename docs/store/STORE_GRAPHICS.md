# Grafiki Google Play — zlecenie 117 (10 języków)

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
3. Boss Endless — boss fali dziesiątej i jednostki renderera.
4. Nocny ostrzał — noc, salwa i eksplozje.
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
