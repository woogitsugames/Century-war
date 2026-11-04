# Dzień 28: „Nowość na tym etapie” (R11, pozycja 6)

Gałąź `claude/dzien-28-nowosc` (baza `integ/tura10`). Kodu nie kompilowano: w środowisku nie
ma Android SDK (sieć blokuje dl.google.com).

## Co zrobiono

- **`app/src/main/java/com/centurywar/meta/StageNovelty.kt`** (nowy). To czysta logika liczona
  z `Campaign.stages`. Etap 1 (samouczek) jest punktem odniesienia i sam nie ma nowości. Każdy
  kolejny etap dostaje listę tego, co pojawia się na nim po raz pierwszy, w tej kolejności:
  - dowódca (`COMMANDER_STAGE = 7`, odpowiada warunkowi `stageIndex >= 7` w
    `Battle.commanderAvailable`),
  - nowa epoka wroga, czyli `maxEra` wyższa niż na wszystkich wcześniejszych etapach; do niej
    nazwa ciężkiej jednostki tej epoki (elity wroga bywają wyłączone),
  - pogoda inna niż bezchmurna,
  - modyfikatory.

  `line(i)` zwraca napis „Nowość: …”, zapamiętany dla każdego etapu do zmiany języka.
  `showSpark(i, stars)` zwraca true dla etapu z nowością, którego gracz jeszcze nie wygrał
  (0 gwiazdek). Na obecnej kampanii nowości mają etapy 3–8, 11, 15, 19, 23, 27 i 31.
- **`screens/CampaignScreen.kt`**:
  - `drawInfo`: napis nowości zawsze stoi w wierszu gwiazdek (gwiazdki 26 dp, iskra i tekst),
    więc panel nie ma dodatkowego wiersza. Na niskim panelu (720p) w tym samym wierszu jest też
    korona R10-02 (nowa funkcja `drawStarRow`). Oba napisy maleją razem najwyżej do 11 sp.
    Napis korony zostaje cały, a nowość w razie potrzeby skraca się wielokropkiem. Gdy zostaje
    mniej niż 48 dp, napis nowości znika z tego wiersza, ale TalkBack nadal czyta go w treści
    panelu. Do testów dodano `noveltyInfoRect` i `noveltyShown`.
  - `drawNode`: na węźle jest złota czteroramienna iskra w miejscu korony. Miejsce jest wolne,
    bo koronę ma tylko wygrany etap. Iskra widnieje do pierwszej wygranej. Delikatnie pulsuje,
    a przy `reduceFlash` stoi w miejscu. Na zablokowanym węźle jest półprzezroczysta.
  - `accessibilityLabel` węzła ma dopisek „, Nowość: …” do pierwszej wygranej (klucz pamięci
    podręcznej zawiera już gwiazdki). `panelText` czyta nowość po gwiazdkach i koronie.
- **`engine/L.kt`**: nowa część `part201` z kluczami `nov201_new`, `nov201_commander` i
  `nov201_era`, każdy w 10 językach. Liczba `putAll(part` i liczba `private fun part` to teraz
  76 i 76.
- **`app/src/test/java/com/centurywar/meta/StageNoveltyTest.kt`**: zwykły JUnit bez
  Robolectric. Sprawdza:
  - kolejność epok,
  - że każdy modyfikator i każda pogoda są tylko pierwszym wystąpieniem i że żadnych nie brakuje,
  - zgodność dowódcy z `Battle.commanderAvailable`,
  - przerwy między nowościami (najwyżej 4 etapy),
  - `compute` na własnej liście etapów,
  - iskrę do pierwszej wygranej,
  - napisy we wszystkich językach (bez surowych kluczy i `%`).

`:sim` jest bez zmian.

## Czego nie sprawdzono

- Kompilacji i testów (`./gradlew :app:testDebugUnitTest`) nie uruchomiono.
- Zrzutów ekranu nie oglądano: nie wiadomo, jak wygląda wiersz gwiazdek z nowością (zwłaszcza
  720p z koroną po niemiecku i turecku) ani iskra na węźle.
- Testy zrzutów `HardCampaign189ScreenshotTest.checkPanel` biorą pierwszy etap z modyfikatorem
  (etap 3), który ma teraz nowość. Napis korony powinien zostać cały i mieć co najmniej 11 sp,
  ale tego nie zweryfikowano.

## Ryzyka

- Na wąskich panelach (Fold, zewnętrzny ekran) napis nowości będzie skrócony albo ukryty.
- Iskra stoi w miejscu korony. Testy nachodzenia korony (R10-02) jej nie obejmują, ale ma
  mniejszy promień niż kółko korony (9 dp wobec 12 dp).
- `COMMANDER_STAGE` powiela stałą z `Battle`. Test pilnuje zgodności, ale zmiana w `:sim`
  wymaga też zmiany tutaj.
