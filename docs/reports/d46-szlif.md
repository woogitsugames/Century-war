# Dzień 46 — szlif: wzorce reg100, tekst de, progi medali tygodnia

Gałąź: `claude/dzien-46-szlif`. Gradle niedostępny (dl.google.com zablokowany). Testy uruchomione
obejściem `tools/offline-build/` (`CW_OFFLINE_DIR=/tmp/claude-0/d46`, Kotlin 2.0.21, Robolectric 4.16).
Nie zastępuje to `./gradlew build`: nie ma lintu, R8 ani `releaseChecklist`.

## 1. Wzorce reg100 (R11-06) — `bd9ad76f`

Zaktualizowane 4 wzorce, czyli phone720/foldOpen × `campaign`/`campaign_info`, oraz wpis w `UPDATES.md`.
Sprawdzenie stary ↔ nowy wzorzec (różnica > 24 na kanał, regiony zgrupowane w siatce 40 px):

| Wzorzec | Piksele | Regiony |
|---|---|---|
| phone720 `campaign` | 1374 | 5 iskier „Nowość” przy etapach na mapie |
| phone720 `campaign_info` | 6907 | linia „✦ Nowość: Epoka brązu (Rydwan), Mgła wojny” w panelu etapu i 2 iskry na mapie w tle |
| foldOpen `campaign` | 4852 | 5 iskier przy etapach |
| foldOpen `campaign_info` | 25759 | linia „Nowość” w panelu i 1 iskra w tle |

Potwierdzone: różnice są **tylko w miejscach „Nowość”**. W `campaign_info` region linii obejmuje też
górną krawędź wiersza „Epoki”, który leży tuż pod nią. Pozostałe 24 wzorce się nie zmieniły.
`ScreenshotGoldenTest` i `ScreenshotTestMenus` (porównanie z wzorcami) przechodzą.

## 2. Tekst de — `46908085`

`t203_rules_note`, `t203_rules_note_short` i `t203_ach_release`: zamiast „ab dem Start” jest
„ab dem Release”, bo „Start” można było odczytać jako start bitwy. Pozostałe języki zostały bez
zmian (w kontekście gry ich odpowiedniki znaczą wydanie). Długi wariant „Testversion: volle
Challenge-Regeln ab dem Release” mieści się na phone720 przy 130% (zrzut niżej). Na foldOuter
wyświetla się krótki wariant.

## 3. Progi medali wyzwania tygodnia — `9ccdb67f`

**Błąd:** na phone720 przy de 130% wiersz „Gold: 30 Wellen (+120)” wychodził w prawo poza wewnętrzną
lamówkę. Przyczyny:
- szerokość progu liczona z 1/3 panelu, a tekst zaczynał się 23 dp przed środkiem kolumny,
- `Ui.fit` schodził do 8 sp.

**Poprawka** (`ModeScreen.layoutTiers`, liczone przy przebudowie karty, nie w każdej klatce):
- **Szeroki panel:** trzy progi w jednym rzędzie od lamówki do lamówki (16 dp wcięcia). Każdy
  blok, czyli medal (27 dp) i realna szerokość tekstu z konturem, dostaje tyle miejsca, ile
  potrzebuje. Wolne miejsce dzieli się po równo, z odstępem co najmniej 6 dp. Tekst zawsze
  ma 11 sp. Gdy rząd się nie mieści, każdy próg idzie w dwóch wierszach (podział przy spacji,
  drugi wiersz nie zaczyna się od dwukropka, ważne dla fr). Dopiero potem całość się zmniejsza.
  W żadnej z 60 testowanych konfiguracji tak się nie stało.
- **Wąski panel (foldOuter):** każdy próg w swoim wierszu, 12 sp, nie mniej niż 11 sp. Gdy się
  nie mieści, dwa wiersze.
- **Odstęp od ostatniego wiersza kolumn** („Endet in…”, „Bestwert”) rośnie z rozmiarem tekstu
  systemu: `17 dp × textScale`, przy 100% bez zmian. Przy 130% ascent progu nie wchodzi już na
  te wiersze.
- **Przy dwóch wierszach progu** środek medalu zostaje w połowie wysokości tekstu.
- **Efekt uboczny przy 100%:** „Bronze: 10 Wellen (+25)” nie łamie się już niepotrzebnie.

**Test** `WeeklyTierLayoutTest`: 10 języków × tekst 100/130% × phone720/foldOpen/foldOuter (60
układów). Dla każdego wiersza progu (prostokąt z ascent/descent i konturem) sprawdza, że:
- leży w całości w wewnętrznej lamówce (`Ui.panelInner`),
- ma ≥ 11 sp,
- nie nachodzi na Graj ani na inne napisy karty.

Wynik: **OK**.

Zrzut po zmianie (de 130%, phone720): `/tmp/claude-0/d46/d46_phone720_de130_wyzwanie_tygodnia.png`.
Wszystkie trzy progi mieszczą się w jednym wierszu w 11 sp, między „Endet in…” a Spielen.

## 4. Testy (obejście)

| Zestaw | Wynik |
|---|---|
| fastTest (`run_fast.sh`, 138 klas, 6 paczek) | **OK, 753 testy** |
| kategoria screenshots (`run_shots.sh`, 36 klas, jedna JVM na klasę) | **OK, wszystkie 36 klas** (w tym `ScreenshotGoldenTest`, `ScreenshotTestMenus`, `PrereleasePolish*`, `Localization120/136`) |
| `:sim` (`build_sim.sh`) | **OK, 318 testów** |

**Wyjątki:**
- `run_fast.sh` pomija w całości 4 klasy, w których do kategorii balance należą tylko
  pojedyncze metody: `AiPersonalityProfileTest`, `EndlessBossTest`, `Doctrine69BattleSimTest`,
  `BattleSimTest`. Ich pozostałe metody (fast) nie zostały uruchomione.
- Kategoria balance nie była uruchamiana (nie ma jej w zadaniu).
- Kompilacja idzie na Kotlinie 2.0.21 zamiast 2.2.20 i bez lintu.
