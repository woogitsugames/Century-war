# R6-01: Co najmniej 4 osiągnięcia w pierwszej godzinie (Play Games)

**Gałąź:** `claude/r6-01-osiagniecia` z `origin/integ/tura10` (po scaleniu dnia 9).
**Teksty:** tylko nowa `part185()` w `engine/L.kt` (10 języków; `putAll(part185())`; liczba
`putAll(part` = liczba `private fun part`). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Commity po polsku, wypychaj często.

**Decyzja właściciela (3.11 wieczorem):** zatwierdzone 4 nowe osiągnięcia z tabeli niżej
(`first_evolve`, `first_turret`, `first_research`, `stages_3`), nagrody razem **50 medali**.
Po zmianie: **78 osiągnięć, 83 ID Play Games**.

## Cel

Wytyczna Play Games **LU-AC-GAC** (warunek „Quests” w programie Level Up): co najmniej
**4 osiągnięcia „rozsądnie i niezawodnie” osiągalne w pierwszej godzinie gry przez każdego**.
Źródło: [Level Up guidelines](https://developer.android.com/games/guidelines),
`docs/research/R3-notes.md` §2. Osiągnięcia mają też unikalną nazwę i opis (LU-AC-GAB). Dla
gracza: szybkie, czytelne sukcesy na start, a w aplikacji Play Games ma od razu coś do pokazania.

## Stan dziś (kod, 921cd50)

`meta/Achievements.kt` (`Achievements.all`, 74 pozycje, wiersze 59–136; postęp `progress()`
147–172; rozliczenie `onBattleEnd()` od 228, liczniki bitwy w wierszach ok. 348–351; zakupy
`onResearchBought()` 363). Na podstawie osi czasu z `docs/reports/noc-5-pierwsza-godzina.md`
(etap 1 wygrany ok. 8:42, etap 2 ok. 21:29, badania od ok. 21:37, etap 4 ok. 48:34;
etapy 2–7 skrócone w dniu 1 do ok. 7–9 min) w pierwszej godzinie wpadają:

| Osiągnięcie | Warunek | W 1. godzinie? |
|---|---|---|
| `win_1` | 1 wygrana | **tak, zawsze** (koniec samouczka) |
| `kills_100` | 100 zabitych łącznie | **prawie zawsze** (etapy 1–3) |
| `perfect_1` | wygrana bez obrażeń bazy (`baseDamageTaken <= 0`) | **nie zawsze**: zależy od gry; w teście AI etap 1 bywa idealny |
| `research_10` | 10 poziomów badań | **nie zawsze**: test kupował 7 poziomów po etapie 2, kolejne po etapie 8 (ok. 1:56) |
| `rain_victory` | wygrana w deszczu | etap 5 (ok. 61 min): na granicy |
| `daily_1`, `wave_10`, `commander_first` | wyzwanie dnia, Endless fala 10, dowódca (etap 8) | nie (wymaga wyjścia poza kampanię albo > 1,5 h) |

Wniosek: **niezawodnie tylko 2** (`win_1`, `kills_100`). Brakuje co najmniej 2, a z zapasem 4.

## Zmiany

Nowe osiągnięcia na **istniejących licznikach**, bez nowych pól symulacji. Dopisać **na końcu**
`Achievements.all` (kolejność stała; `pgsKey = "cw_<id>"`):

| id | Metryka | Próg | Nagroda (medale) | Kiedy wpada | Kategoria |
|---|---|---|---|---|---|
| `first_evolve` | `EVOLUTIONS` | 1 | 10 | koniec 1. bitwy (samouczek ma awans epoki) | TACTICS |
| `first_turret` | `TURRETS_BUILT` | 1 | 10 | koniec 1. bitwy (samouczek ma krok z wieżą) | TACTICS |
| `first_research` | `RESEARCH_LEVELS` | 1 | 10 | pierwszy zakup w Badaniach (po etapie 1–2) | RESEARCH |
| `stages_3` | `STAGES` | 3 | 20 | etap 3 (ok. 30–35 min) | CAMPAIGN |

Uwagi:
- Sprawdź, że samouczek **zawsze** zawiera awans epoki i budowę wieży. Jeśli krok można pominąć
  (np. „Pomiń samouczek”), ten wariant zalicz w teście: `first_evolve`/`first_turret` mogą wtedy
  przyjść w etapie 2, nadal w pierwszej godzinie.
- `onBattleEnd` nie wyklucza samouczka ani kapitulacji. Licznik `TURRETS_BUILT` jest dodawany z
  `player.turretsBuilt` także po porażce. To w porządku (osiągnięcie za akcję, nie za wygraną).
- **Nagrody małe** (razem 50 medali). Pierwsze badania są i tak dostępne po etapie 1
  (`noc-5`: 196–356 medali). Zmiana przesunie ekonomię pierwszej godziny o ok. 10–15%, więc
  zanotuj w `docs/BALANCE.md` jedno zdanie, bez strojenia.
- Teksty w `part185()`: `ach_<id>` (nazwa) i `ach_<id>_d` (opis, jak `ach_win_1_d`), 10 języków.
  Nazwy unikalne względem istniejących (LU-AC-GAB). Bez „najlepszy” i bez porównań. Przykłady pl:
  „Nowa epoka” / „Awansuj do następnej epoki.”, „Pierwsza wieża” / „Zbuduj wieżę.”,
  „Pierwsze badanie” / „Kup dowolne badanie.”, „Trzy etapy” / „Ukończ 3 etapy kampanii.”
- Ikony i kategorie jak dla pokrewnych osiągnięć (`AchievementsScreen`).

## Pliki i liczby do aktualizacji (74 → 78 osiągnięć, 79 → 83 ID Play Games)

- `app/src/main/java/com/centurywar/meta/Achievements.kt` (definicje).
- `buildSrc/src/main/kotlin/com/centurywar/build/PlayGamesIdsGenerator.kt` (`achievementNames`, + 4).
- `docs/store/play_games_ids.properties`: 4 nowe klucze `achievement_<id>=REPLACE_ME`.
- Testy z liczbą 74/79: `app/src/test/java/com/centurywar/meta/AchievementsTest.kt:41–43`,
  `PlayGamesIdsTest.kt:17, 32`, `buildSrc/src/test/kotlin/com/centurywar/build/PlayGamesIdsGeneratorTest.kt:74–76`;
  sprawdź też `Achievement60Test`, `Achievement130Test`, `Content176Test`, `ScreenshotTestFold`
  (`grep -rn "\b74\b"`).
- Dokumenty z liczbą 74/79: `docs/RELEASE.md` (wiersze ok. 67, 90, 772, 783),
  `docs/store/OWNER_INPUT.md:29`, `docs/store/data_safety.md:72`, `docs/PLAY_GAMES_SETUP.md:44`,
  `docs/store/listing_pl.md` i `listing_en.md` („74 osiągnięcia”) oraz 8 pozostałych
  `listing_*.md` („74 …”), `docs/COORDINATOR_HANDOVER.md` (tylko nowy wpis, starych nie zmieniać).
- `meta/WhatsNewCatalog.kt` + tekst „Co nowego” (w `part185()`).

## Testy do dopisania

- `AchievementsTest`: 4 nowe id, unikalne `pgsKey`, nazwy i opisy przetłumaczone (10 języków),
  progi i metryki zgodne z tabelą. Ten sam profil po `onBattleEnd` z `erasAdvanced = 1`
  i `turretsBuilt = 1` odblokowuje `first_evolve` i `first_turret`. `onResearchBought` odblokowuje
  `first_research`.
- **Test pierwszej godziny** w `screens/FirstHourJourneyBase.kt` (wszystkie 4 przejścia: telefon
  i Fold, pl i en): zapisuj czas odblokowania każdego osiągnięcia na osi czasu
  (`fh_*_os_czasu.txt`). Asercja: **co najmniej 4** z `win_1`, `kills_100`, `first_evolve`,
  `first_turret`, `first_research`, `stages_3` odblokowane przed 60:00 czasu gry.
- **Wariant ostrożny** (nowy test w tej samej bazie): gracz pomija samouczek
  (`tut.skip`), przegrywa etap 2 raz i nie kupuje badań do 30. minuty. Nadal ≥ 4 osiągnięcia
  przed 60:00 (bez `first_research` musi wystarczyć reszta). Progów nie zmieniaj (zestaw
  zatwierdzony); jeśli nie wystarczy, opisz to w raporcie i zgłoś koordynatorowi.
- `PlayGamesIdsTest` i `PlayGamesIdsGeneratorTest` z nowymi liczbami. `releaseChecklist`
  nadal wymaga wszystkich ID (83).

## Kryteria odbioru

- Testy pierwszej godziny pokazują ≥ 4 osiągnięcia przed 60:00 w każdym przejściu, a oś czasu
  w raporcie ma czasy odblokowań.
- Pełne testy zielone:
  `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`
  oraz `./gradlew -p buildSrc test` (albo zadanie, które uruchamia testy `buildSrc` w tym repo).
- Wszystkie liczby 74/79 w dokumentach zmienione na 78/83. Krótki raport
  `docs/reports/r6-01-osiagniecia.md` z tabelą „osiągnięcie → minuta odblokowania” dla 4 przejść.

## Ryzyka

- Właściciel jeszcze nie wpisał ID w Play Console, więc dodanie osiągnięć **teraz** nic nie
  kosztuje. Po konfiguracji konsoli każde nowe osiągnięcie to dodatkowa ręczna praca właściciela.
- Banery „Nowe osiągnięcie” po pierwszej bitwie: będzie ich więcej (do 4–5 naraz). Sprawdź w
  teście pierwszej godziny, że nie nachodzą na dymki i przycisk „Dalej” (asercje już są).
  Rozważ łączenie banerów, jeśli kolejka wydłuża się ponad ok. 6 s.

## Czego nie ruszać

- Istniejące osiągnięcia (progi, nagrody, kolejność, id), `:sim`, PROTOCOL, balans etapów.
- Zatwierdzony `compactTop`, wyniki bitwy (medale „Za bitwę” i „Za osiągnięcia” osobno).
