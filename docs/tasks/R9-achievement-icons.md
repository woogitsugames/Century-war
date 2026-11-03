# R9: Ikony osiągnięć i rankingów Play Games (512×512, rysowane kodem)

**Gałąź:** `claude/r9-ikony-pgs` z `origin/integ/tura10`, **po scaleniu R6-01** (78 osiągnięć).
**Gradle:** wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`; eksport uruchamiaj w tle.
**Bez nowych tekstów w `L.kt`** (ikony są bez napisów). **Bez workflowów CI.** Commity po polsku,
wypychaj często.

## Cel

Play Console potrzebuje ikony **512×512 PNG/JPEG** dla każdego z 78 osiągnięć i 4 rankingów.
Wytyczne Google ([Integrate achievements → Icon guidelines](https://developer.android.com/games/pgs/integrate-achievements),
[Leaderboards → Icon guidelines](https://developer.android.com/games/pgs/leaderboards)):
- podajemy tylko wersję **odblokowaną**; szarą wersję „nieodblokowaną” Google robi sam, więc
  ikona musi być **kolorowa** i czytelna także w odcieniach szarości;
- w toaście Androida ikona jest **przycięta do koła**: najważniejsze w środkowym kole
  (promień ok. 0,42 boku), rogi mogą zniknąć;
- **bez tekstu i cyfr zależnych od języka** (ta sama ikona we wszystkich językach);
- ikony są pomniejszane, więc bez drobnych szczegółów;
- ranking jest pokazywany na **ciemnoszarym tle**; przezroczystość jest zachowywana;
- „nowe osiągnięcia powinny mieć unikalne ikony” (wytyczne jakości PGS), więc każda ikona inna.

Dziś repo **nie ma** takich ikon ani testu, który je generuje. `StoreGraphicsTest` robi tylko
ikonę sklepu i zrzuty, a ekran osiągnięć w grze używa kilku wspólnych symboli (`MEDAL`,
`CROWN`, `TOWER_TIERS`, …).

## Zmiany

1. **Nowy test-generator** `app/src/test/java/com/centurywar/screens/PgsIconsTest.kt`
   (Robolectric, `@GraphicsMode(NATIVE)`, `@Config(sdk = [35])`, jak `StoreGraphicsTest`).
   Uruchamiany tylko przy `PGS_ICONS=true` (`assumeTrue`). Zapis do `build/pgs-icons/`:
   - `ach_<id>.png` dla każdego `Achievements.all` (78);
   - `lb_endless.png`, `lb_campaign.png`, `lb_skirmish.png`, `lb_daily.png`;
   - `AchievementsIconsMappings.csv` (bez nagłówka: `<nazwa pl z importu>,ach_<id>.png`, nazwa
     dokładnie jak w `docs/store/pgs_import/AchievementsMetadata.csv`, czyli po zamianie
     przecinków; najprościej wczytać ten plik i dopasować wiersze po kolejności z
     `docs/store/play_games_sheet.csv`);
   - `contact.png`: stykówka wszystkich ikon (np. 10 kolumn po 128 px) plus wersja w szarości.
2. **Styl gry** (bez zewnętrznych grafik):
   - tło: koło lub tarcza w kolorze **kategorii** (`AchievementCategory`: COMBAT czerwień,
     CAMPAIGN zieleń, ENDLESS fiolet, RESEARCH błękit, DAILY pomarańcz, TACTICS złoto), cieniowanie
     jak `ui.card` / `ui.panel`, złota obwódka `Ui.GOLD`;
   - symbol: istniejące wektorowe `Icons.draw(...)` (np. `SWORD`, `CROSSED_SWORDS`, `STAR`,
     `MAP`, `FLASK`, `CALENDAR`, `TOWER`, `TOWER_TIERS`, `CROWN`, `COMMANDER_PULSE`, `METEOR`,
     `SKULL`, `RAIN`, `WIND`, `MOON`, `EVOLVE`, `STOPWATCH`, `SHIELD`, `BOSS_RECALL`,
     `FIELD_MEDICS`, `FINAL_SPARK`, `FIRST_GUARD`, `WRENCH`, `DICE`, `CHEST`, `GLOBE`…);
     mapowanie `id → symbol` w jednej tabeli w teście;
   - stopnie serii (np. `win_1/10/100/500`, `kills_*`, `wave_*`, `research_*`) odróżnia **liczba
     gwiazdek lub belek** (1–4) i kolor obwódki (brąz, srebro, złoto, platyna), **bez cyfr**;
   - ukryte (`hidden`) dostają ten sam styl, bo Google sam pokazuje zastępczą ikonę, dopóki
     osiągnięcie jest ukryte;
   - rankingi: ten sam styl z symbolem trybu (Oblężenie, kampania, potyczka, wyzwanie dnia),
     na przezroczystym tle, czytelne na ciemnoszarym.
3. **Kontrole w teście** (zawsze, bez flagi, na kilku ikonach; z flagą na wszystkich):
   - rozmiar 512×512, PNG < 1 MB (limit pliku w imporcie ZIP);
   - piksele poza kołem nie niosą treści: w środkowym kole r = 0,42 boku jest ≥ 90% nieprzezroczystych
     pikseli symbolu i tła;
   - kontrast w szarości: luminancja symbolu różni się od tła o ≥ 25%;
   - unikalność: żadne dwie ikony nie mają identycznego skrótu (SHA-256) pikseli;
   - liczba plików = 78 + 4, a mapowanie obejmuje każdy wiersz `AchievementsMetadata.csv`.
4. **Wynik w repo:** tylko `docs/store/assets/pgs/contact.png`, `contact_gray.png`
   i `AchievementsIconsMappings.csv`. Pełne 82 PNG zostają w `build/pgs-icons/`. Komenda
   eksportu i spakowania ZIP-a importu (`AchievementsMetadata.csv`, `AchievementsLocalizations.csv`,
   `AchievementsIconsMappings.csv`, 78 PNG, bez podkatalogów, ≤ 403 pliki) opisana w
   `docs/store/play_games_sheet.md` (krok 3).

## Kryteria odbioru

- `PGS_ICONS=true flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --tests '*PgsIconsTest*'`
  tworzy 82 ikony i przechodzi kontrole.
- Bez flagi test kontrolny przechodzi w zwykłych testach.
- Stykówka dołączona do raportu `docs/reports/r9-ikony.md` z krótkim opisem mapowania i stopni.

## Czego nie ruszać

- `Icons.kt` może dostać nowe symbole, ale bez zmiany istniejących (ekrany i zrzuty wzorcowe).
- `Achievements.kt`, `L.kt`, `play_games_ids.properties`, generator ID w `buildSrc`.
