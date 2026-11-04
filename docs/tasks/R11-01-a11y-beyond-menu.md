# R11-01: TalkBack i robot Play na ekranach poza bitwą

**Gałąź:** `claude/r11-01-talkback-ekrany` z `origin/integ/tura10`.
**Teksty:** tylko nowa `part196()` w `engine/L.kt` (10 języków: pl, en, de, es, fr, it, pt, uk, nl, tr;
`putAll(part196())`; liczba `putAll(part` = liczba `private fun part`). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Źródło: `docs/research/R11-backlog.md`, TOP 1.
Commity po polsku, wypychaj często (kontener bywa restartowany).

## Cel

Dziś osobne kontrolki dla czytnika ekranu mają tylko Menu, Ustawienia, Dostępność, Podpowiedzi,
O grze i bitwa (D5). **Każdy inny ekran** jest jednym węzłem `a164_other`: „Ekran gry. Ten ekran
wymaga sterowania dotykowego”. Osoba z TalkBack po samouczku nie wybierze więc etapu na mapie
kampanii, nie kupi badania i nie odbierze misji. TalkBack w HUD bitwy (D5) i tempo ×½ (R2-03)
są dla niej nieosiągalne. Gotowy opis korony z R10-02 (`CampaignScreen.accessibilityLabel`)
nigdy nie trafia do czytnika, bo ekran ma rodzaj `OTHER`.

Ten sam brak dotyczy robota raportu przedpremierowego Google Play, który chodzi po drzewie
dostępności. Za menu robot widzi jeden węzeł na cały ekran i dalej klika losowo.

Po zmianie gracz z TalkBack przechodzi: Menu → Kampania → etap 7 („Etap 7, Epoka żelaza, 2 z 3
gwiazdek, następny do zdobycia”) → panel etapu (czytany opis: epoki, siła wroga, pogoda,
modyfikatory, poziom trudności) → Start → bitwa → wyniki → Badania („Skarbiec, poziom 2 z 5,
koszt 120 medali, stać cię”) → Misje („Odbierz: Wygraj 3 bitwy, 40 medali”).

## Stan w kodzie

- `engine/GameAccessibility.kt`:
  - `refresh()` (ok. 320): `BattleScreen` → `refreshBattle`; Menu/Settings/Accessibility/
    HintSettings/About → `BUTTONS`; reszta → `OTHER` (jeden węzeł `FALLBACK_ID`).
  - `refreshList` (ok. 346): węzły o id = indeks przycisku + 1, filtr `eligible` (ok. 389).
  - `matches` (ok. 405): porównanie bez alokacji, przebudowa tylko po zmianie.
  - `performAction` (ok. 185): `ACTION_CLICK` → `tap` (DOWN/UP jak palcem), akcje własne
    przez `postAction`. **Brak akcji przewijania.**
  - `stillValid` (ok. 262) sprawdza `eligible` przed kliknięciem.
- `engine/Screen.kt`: `accessibilityLabel(b)` (42), `accessibilityCheckable`, `accessibilityChecked`,
  `onDrag(dx, dy)` (83), `isDragScreen()` (178).
- Ekrany i przyciski (`btn(id)`):
  - `CampaignScreen`: `tiles` (węzły etapów, `tag = Stage`, **pusty `label`**, `enabled` =
    odblokowany, `highlight` = następny), `back`, `start`, `close`, `difficultyButtons`;
    panel etapu `selected != null` (`drawInfo` ok. 715, teksty `infoRange`, `infoPower`,
    `infoModifiers`, pogoda, gwiazdki, `hardInfoLine`); przewijanie `scroll`/`scrollTarget`,
    `onDrag` (324), mapa pozioma albo pionowa (`portraitMap`); `EraIntroOverlay`, dymek
    `HintOverlay`. `accessibilityLabel` (887) dodaje koronę.
  - `ModeScreen`: `back`, `start`, `diff[4]`, `dailyTab`, `weeklyTab`; opis trybu rysowany
    tekstem (rekord, zasady dnia, progi tygodnia, karta startowa).
  - `ResearchScreen`: `cards` (`tag = ResearchDef`, **pusty `label`**, `enabled = canBuy`,
    `visible` tylko w całości na ekranie), `back`, `hints.okBtn`; `scroll`, `onDrag` (121).
  - `MissionsScreen`: `claimButtons[6]`, `back`; opisy misji rysowane; `scroll`.
  - `SkirmishSetupScreen`: wiele przycisków, ma już `accessibilityLabel` (328); `scroll`.
  - `AchievementsScreen`, `WhatsNewScreen`, `BannerScreen`: listy przewijane, opisy rysowane.
  - Krok 2: `CodexScreen` (`tabs`, `categories`, `toggle`, `variantBtn`, treść rysowana),
    `StatsScreen` (`tabs`, `detailBack`, wykresy).
- Testy: `app/src/test/.../engine/GameAccessibility164Test.kt`, `BattleAccessibilityTest.kt`,
  `SystemAccessibilityTest.kt`; wzorzec testu drzewa z `refresh()` bez aktywnego TalkBack.

## Zmiany

### 1. Mechanizm ogólny (`GameAccessibility`, `Screen`)
- Nowy rodzaj `SCREEN` (albo rozszerzenie `BUTTONS`) dla ekranów z listy niżej. Węzły przycisków
  jak dziś (id = indeks + 1, filtr `eligible`). Do tego **węzły informacyjne** z nowego API:
  ```kotlin
  /** Teksty do przeczytania, których nie ma na przyciskach (opis trybu, panel etapu). */
  open fun accessibilityInfoCount(): Int = 0
  open fun accessibilityInfo(i: Int): String = ""
  open fun accessibilityInfoBounds(i: Int, out: RectF) {}
  ```
  Węzły informacyjne mają stałe id `INFO_SCREEN_BASE + i` (np. 30 000 + i), `info = true`,
  klasę `TextView`. Kolejność w drzewie: tytuł ekranu, informacje, przyciski w kolejności
  czytania (od góry do dołu, potem od lewej).
- Przewijanie: nowe API w `Screen`:
  ```kotlin
  /** +1 dalej, −1 wstecz; true, gdy coś się przesunęło. Domyślnie brak przewijania. */
  open fun accessibilityScroll(direction: Int): Boolean = false
  ```
  Węzeł główny (`View.NO_ID`) przy ekranach z przewijaniem dostaje `isScrollable = true`
  i akcje `ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD`. `performAction` dla `View.NO_ID`
  wysyła je przez `game.post` do `accessibilityScroll`. Ekrany przesuwają o ok. 80% widocznej
  wysokości (szerokości dla mapy poziomej), tą samą drogą co `onDrag`.
- `matches` dalej bez alokacji: porównuj też liczbę i treść węzłów informacyjnych. Teksty
  informacyjne ekran buforuje i odświeża tylko po zmianie stanu (jak `costs` w `ResearchScreen`).
  Bez TalkBack (`refreshIfEnabled`) nic się nie liczy.
- Etykiety tylko przez `accessibilityLabel`. Rysowanie i `Button.label` zostają bez zmian,
  bo zmiana `label` przestawiłaby rysunek kart i węzłów mapy.
- Okna nad ekranem (dymek `HintOverlay`, `EraIntroOverlay`, baner `showNotice`): gdy są
  otwarte, w drzewie są tylko ich przyciski i tekst (jak `MenuScreen.hasOpenModal`).

### 2. Ekrany kroku 1 (ścieżka do bitwy)
- **CampaignScreen:**
  - mapa bez panelu: węzły widocznych `tiles` z etykietą
    `L.f("a196_stage", n, eraName)` + gwiazdki (`a196_stars` „%1$d z 3 gwiazdek”) + stan
    (`a196_locked` / `a196_next` / korona R10-02). `enabled = false` dla zablokowanych
    (TalkBack przeczyta „niedostępne”). Nagłówek epoki jako węzeł informacyjny regionu.
  - panel etapu (`selected != null`): węzeł informacyjny z jednym zdaniem z `refreshInfo`
    (epoki, siła wroga słowami „słabszy / równy / silniejszy”, pogoda, modyfikatory, korona),
    przyciski poziomu trudności (`accessibilityCheckable` = true, `checked` = wybrany),
    `start`, `close`. Węzły mapy pod panelem ukryte.
  - `accessibilityScroll`: przesuwa `scrollTarget` w osi mapy.
- **ModeScreen:** informacja = tytuł trybu + rekord + zasady (dzień: wydarzenie, modyfikatory;
  tydzień: zestaw, progi z zaliczeniem ✓ słowami); `diff` jako zaznaczane; zakładki dnia
  i tygodnia jako `selected`.
- **ResearchScreen:** etykieta karty: nazwa, „poziom X z Y”, koszt albo „maksimum”, opis
  (`descKeys`), „stać cię” / „za mało medali”. Karty częściowo widoczne są poza drzewem
  (`visible`), więc przewijanie jest konieczne. Informacja: liczba medali.
- **MissionsScreen:** informacja dla każdej widocznej misji (treść, postęp, nagroda),
  przyciski odbioru z etykietą „Odbierz: <misja>, <nagroda>”.
- **SkirmishSetupScreen:** dopisz do listy, ma już etykiety; dodaj przewijanie.
- **AchievementsScreen, WhatsNewScreen, BannerScreen:** informacje z widocznych wierszy
  (osiągnięcie: nazwa, opis, odblokowane/postęp; wpis zmian; sztandar: nazwa, warunek,
  wybrany), przewijanie.

### 3. Krok 2 (po zielonym kroku 1, osobny commit)
- **CodexScreen:** zakładki epok i kategorii jako zaznaczane, karta jednostki jako informacja
  (nazwa, rola, koszt, HP, obrażenia, zasięg, kontra), porównanie (`CodexComparePanel`)
  jako jedno zdanie na wiersz.
- **StatsScreen:** zakładki; wykresy jako zdanie podsumowania (np. „Ostatnie 10 bitew:
  7 wygranych, najlepsza fala 41”).
- **RankingsScreen, ReplayListScreen, GlobalRankingsScreen, PlayGamesScreen,
  PrivacySummaryScreen:** przyciski + wiersze jako informacje.
- Na końcu `a164_other` zostaje tylko dla ekranów, których nie da się sensownie opisać
  (jeśli takie zostaną, wypisz je w raporcie).

### 4. Teksty `part196()`
- `a196_stage` „Etap %1$d, %2$s”, `a196_stars` „%1$d z 3 gwiazdek”, `a196_locked`
  „zablokowany”, `a196_next` „następny do zdobycia”, `a196_enemy_weaker` / `_equal` /
  `_stronger`, `a196_afford` „stać cię”, `a196_no_medals` „za mało medali”,
  `a196_level` „poziom %1$d z %2$d”, `a196_claim` „Odbierz: %1$s, %2$s”, `a196_done` „zaliczone”,
  `a196_scroll_hint` (opcjonalnie). Używaj istniejących kluczy (`L["max"]`, `L.f("level", …)`,
  `L.plural("medals_n", …)`), gdzie pasują. Krótko: czytnik czyta całość przy każdym fokusie.

## Kroki

1. API `accessibilityInfo*` + `accessibilityScroll` + węzeł główny przewijany + testy mechanizmu.
2. Kampania (mapa, panel, przewijanie, okna nad mapą).
3. Tryby, Badania, Misje, Potyczka.
4. Osiągnięcia, Co nowego, Sztandary. Pełne testy, wypchnij.
5. Krok 2: Kodeks, Statystyki, Rankingi, Powtórki i pozostałe.

## Testy do dopisania

- `GameAccessibilityScreensTest` (w duchu `GameAccessibility164Test`, Robolectric, `refresh()`
  bez aktywnego TalkBack), na telefonie 1600×720 i zewnętrznym Foldzie 904×2316, pl i en:
  - dla każdego ekranu kroku 1: brak węzła `a164_other`, każdy widoczny przycisk ma niepustą
    etykietę, granice węzłów w obrębie widoku;
  - kampania: `ACTION_CLICK` na węźle etapu 1 otwiera panel; w panelu węzły mapy znikają,
    jest węzeł informacyjny z pogodą i modyfikatorami; `ACTION_CLICK` na „Start” uruchamia
    `BattleScreen` z tym etapem;
  - zablokowany etap: `isEnabled == false`, kliknięcie zwraca `false`;
  - `ACTION_SCROLL_FORWARD` na korzeniu Badań odsłania karty, które wcześniej nie były w drzewie;
    na końcu listy zwraca `false`;
  - Misje: kliknięcie „Odbierz” dodaje medale raz (drugie kliknięcie: przycisk nieaktywny);
  - przy otwartym dymku podpowiedzi w drzewie jest tylko dymek.
- Ścieżka końcowa „bez wzroku”: Menu → Kampania → etap 2 → Start → (bitwa: `BattleAccessibilityTest`)
  wyłącznie przez `performAction` na węzłach, bez dotyku współrzędnych.
- Alokacje: przy włączonym czytniku i stałym stanie ekranu `refresh()` nie alokuje
  (wzorzec z testów D5); przebudowa tylko po zmianie tekstu albo przewinięciu.
- `LTest`: nowe klucze mają 10 tłumaczeń.

## Kryteria odbioru

- Gracz z TalkBack przechodzi samą nawigacją czytnika od menu do bitwy dowolnego odblokowanego
  etapu, kupuje badanie, odbiera misję, uruchamia Endless i wyzwanie dnia.
- Bez TalkBack zero zmian w wyglądzie i zachowaniu: zrzuty ekranów bez różnic (`ScreenshotTest`,
  `PrereleasePolish*`), ten sam koszt klatki (`LowEndScreensPerformanceTest`).
- Dotychczasowe testy dostępności zielone (`GameAccessibility164Test`, `BattleAccessibilityTest`,
  `SystemAccessibilityTest`, `HardCampaign189ScreenshotTest`).
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.
- Raport `docs/reports/r11-01-talkback.md`: lista ekranów i co czyta czytnik (pl), ekrany
  pozostawione z `a164_other` i dlaczego.

## Ryzyka

- Wydajność przy włączonym TalkBack: ekrany z dziesiątkami węzłów (mapa 36 etapów, Kodeks).
  Do drzewa trafiają tylko widoczne elementy, teksty są buforowane, a przebudowa następuje tylko
  po zmianie.
- Kolejność czytania na mapie pionowej (zewnętrzny Fold) i poziomej. Sortuj według osi mapy,
  nie według indeksu przycisku.
- Kliknięcie z czytnika na przycisk przykryty dymkiem: zostaje mechanizm `tap` z R10-04 L6,
  ale okna nad ekranem mają wyłączać węzły pod spodem.

## Czego nie ruszać

- Wygląd ekranów, `Button.label` i rysowanie (etykiety tylko przez `accessibilityLabel`).
- Drzewo bitwy z D5 (`refreshBattle`) i komunikaty `a178_*`.
- Zatwierdzony pasek `compactTop`, celowanie ostrzału, zasada „bez powiadomień na wynikach”.
