# D7: Grafiki sklepu Google Play z nowościami (zestaw premierowy)

**Gałąź:** `claude/dzien-7-sklep` z `origin/integ/tura10`, **po scaleniu dnia 5**. Dzięki temu
zrzuty bitwy pokazują aktualny HUD, a opisy mogą już mówić o TalkBack w bitwie. **Gradle:**
wyłącznie `flock /tmp/claude-0/gradle.lock ./gradlew ...`; eksport uruchamiaj w tle. **Bez
nowych tekstów w `L.kt`**: podpisy i teksty alternatywne są w
`app/src/test/resources/store/graphics_{pl,en,de,es,fr,it,pt,uk,nl,tr}.properties`. **Bez
workflowów CI** (eksport lokalny). Commity po polsku, wypychaj często. Nie commituj setek PNG,
tylko stykówki i manifesty (patrz „Wynik”).

## Cel

Jeden kompletny, **premierowy zestaw zrzutów** do Play Console w **10 językach**. Ma pokazywać
to, co dziś najbardziej wyróżnia grę: Wodza, stopnie wież, kampanię z krótszymi pierwszymi
etapami, karty i bossów Endless, wyniki z medalami, misje i osiągnięcia, dostępność, wyzwania.
Dziś są dwa rozłączne zestawy:
- zlecenie 117: 8 kadrów × 10 języków, `generate()`; część kadrów jest sprzed Wodza i stopni wież;
- zlecenie 153: 5 kadrów, tylko pl/en/de, `generate153()`.

Play pozwala na **8 zrzutów na typ urządzenia**, więc potrzebny jest jeden, wybrany zestaw.

## Stan w kodzie (`app/src/test/java/com/centurywar/screens/StoreGraphicsTest.kt`, 785 wierszy)

- Urządzenia `devices` (54–58): `phone` 1920×1080 d=2, `tablet7` 1920×1080 d=1,8, `tablet10`
  2560×1440 d=2; `foldDevices` (60–63, tylko QA); `spotlight153Devices` (205–210: `phone16x9`,
  `phone20x9_qa`, `tablet10`, `fold`).
- `generate()` (72–138, `STORE_GRAPHICS=true`, języki z `STORE_LANGUAGES`), `generate153()`
  (214–272, `STORE_GRAPHICS_153=true`, tylko pl/en/de).
- Pomocnicze: `game(device, language)` (574, profil bez samouczka i nakładek),
  `renderScreen(c, g, screen)` (446), `commander153Battle()` (297), `draw153Commander` (312),
  `draw153TurretTiers` (316), `draw153EndlessCards` (332), `render153Battle` (340),
  `draw153BattleChart` (362), `drawGameplay` (473), `drawVariant` (453),
  `drawPauseSummary` (487), `drawCaption` (707), `drawFeatureTitle` (749),
  `writePng`/`checkPng` (610/629), `contactSheet`/`contactSheet153` (504/413),
  `captions(language)` (548; wymaga `caption.1..8`, `alt.1..8` ≤ 140 znaków, `alt.feature`, `alt.icon`).
- Testy bez eksportu (zawsze uruchamiane): `storeAssetRequirements` (142), `listingLimits`
  (158: limity 30/80/4000 i obecność „wódz”/„stopnie wież” we wszystkich `listing_*.md`),
  `spotlight153HudScenes` (275).
- Walidatory: `scripts/validate_store_graphics.py`, `scripts/validate_store153.py`.
- Opisy sklepu (runda R4): `docs/store/listing_*.md` mają już akapit o muzyce, misjach i
  dostępności oraz sekcję „Co nowego” dla wydania testowego.

## Zestaw premierowy: 8 kadrów

| Nr | Plik | Ujęcie (autentyczny stan gry, bez makiet) | Źródło w kodzie |
|---|---|---|---|
| 01 | `01-commander.png` | Bitwa z Wodzem i aurą, pełny HUD z przyciskiem dowódcy | `draw153Commander` |
| 02 | `02-turret-tiers.png` | Panel wież ze stopniami 2 i 3 | `draw153TurretTiers` |
| 03 | `03-campaign.png` | Mapa kampanii z gwiazdkami w kilku epokach (profil z ukończonymi ok. 10 etapami) | `renderScreen(CampaignScreen)` z przygotowanym profilem |
| 04 | `04-endless-cards.png` | Wybór 3 kart Endless przy fali z bossem | `draw153EndlessCards` |
| 05 | `05-results.png` | Wyniki zwycięstwa: gwiazdki, „Za bitwę” i „Za osiągnięcia” osobno, zakładka Wykres **albo** Statystyki (wybierz czytelniejszą na telefonie) | `draw153BattleChart` + stan wyników z dnia 2 |
| 06 | `06-missions.png` | Misje dzienne i tygodniowe z serią dni i jedną misją do odebrania | **nowe**: `renderScreen(MissionsScreen)` z profilem z postępem misji |
| 07 | `07-accessibility.png` | Bitwa w palecie „wysoki kontrast” albo „błękit/bursztyn” ze znacznikami drużyn i tekstem 115% | **nowe**: `Profile.palette`, `teamMarkers`, `textScale` + `render153Battle` |
| 08 | `08-weekly-siege.png` | Oblężenie tygodnia z progami Brąz/Srebro/Złoto | `renderScreen(ModeScreen(g, Mode.DAILY, true))` (jak kadr 7 z 117) |

Uwagi do kadrów:
- **Muzyki nie da się pokazać na zrzucie**. Mówi o niej opis, nie podpis zrzutu.
- Kadr 07 ma pokazywać **prawdziwe** ustawienie (paleta + znaczniki). Nie dorysowuj ramek ani
  napisów „tryb dostępności” poza podpisem.
- Kadr 06: stan misji przygotuj przez `MissionState`/`Missions.refresh` z ustalonym dniem
  (`nowMillis` stały), żeby zrzut był powtarzalny.
- Profil testowy: bez samouczka, podpowiedzi, banerów powiadomień i „Co nowego” (jak w `game()`).
- Wszystkie sceny bitwy z deterministycznym seedem. Brak `System.nanoTime()` w ustawieniach scen.

## Urządzenia i języki

- **Do Play:** `phone` 1920×1080, `tablet7` 1920×1080, `tablet10` 2560×1440 (16:9, ≤ 2:1).
- **Rozłożony Fold** 2184×1968 (`fold`): eksportuj do kontroli. Do Play tylko po przeglądzie
  kadru (Play i tak traktuje go jako tablet).
- **QA, nie do Play:** `phone20x9_qa` 2400×1080 i zewnętrzny Fold 904×2316 (pion; tylko
  stykówka, bo zrzut pionowy nie pasuje do poziomej gry w sklepie).
- **Języki: wszystkie 10** (`L.CODES`). Do każdego `graphics_<lang>.properties` dopisz
  `captionD7.1..8` (krótkie, ≤ 32 znaki, bez wykrzykników i superlatywów typu „najlepsza”)
  i `altD7.1..8` (≤ 140 znaków, opis tego, co widać). Napisy dla de/es/fr/it/pt/uk/nl/tr
  z terminologią gry: nazwy jak w `L.kt` (np. de „Kommandant”, „Turmstufen”; uk „командир”,
  „рівні веж”), tak jak w `listing_*.md`.
- Grafika promocyjna 1024×500: zostaje z 153 (Wódz + logo), eksportowana dla każdego języka
  z `alt153.feature` → dopisz `altD7.feature` w 10 językach. Ikona bez zmian.

## Implementacja

1. Nowy test `generateD7()` (`assumeTrue(System.getenv("STORE_GRAPHICS_D7") == "true")`),
   języki z `selectedLanguages()`, wynik `build/storeD7/<lang>/<device>/NN-nazwa.png`, `manifest.tsv`
   (`file, width, height, alt, play_upload`) i `preview_contact.png` (wzór `contactSheet153`).
2. Rozszerz `captions(language)` albo dodaj `captionsD7(language)` z kontrolą kompletności
   `captionD7.1..8`/`altD7.1..8`/`altD7.feature` (≤ 140 znaków). Test **bez eksportu**
   `d7CaptionsComplete()` w zwykłym zestawie testów (szybki, sprawdza 10 plików).
3. Podpis: `drawCaption` (707) jak w zestawie 117. Sprawdź, że najdłuższy podpis (zwykle de/fr)
   mieści się bez zmniejszania poniżej 70% rozmiaru. Jeśli nie, skróć tekst, nie zmniejszaj czcionki.
4. Walidator: nowy `scripts/validate_store_d7.py` (wzór `validate_store153.py`): 10 katalogów
   językowych, 8 PNG na urządzenie Play, wymiary, brak kanału alfa, proporcje ≤ 2:1,
   `play_upload` tylko dla urządzeń Play, alt ≤ 140.
5. Dokumentacja: `docs/store/STORE_GRAPHICS.md` nowa sekcja „Zestaw premierowy D7” (tabela
   kadrów, polecenia, co uploadować), `docs/store/screenshots_plan.md` wskazuje D7 jako
   obowiązujący zestaw, a 117/153 jako archiwalne.

Polecenia:
```bash
STORE_GRAPHICS_D7=true flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest \
  --tests '*StoreGraphicsTest.generateD7*' --tests '*StoreGraphicsTest.d7CaptionsComplete*' \
  --tests '*StoreGraphicsTest.listingLimits*' --tests '*StoreGraphicsTest.storeAssetRequirements*' \
  --rerun --no-configuration-cache --console=plain
python3 scripts/validate_store_d7.py app/build/storeD7
```
(Jeśli eksport 10 języków przekracza ok. 15 min, uruchamiaj partiami przez `STORE_LANGUAGES=pl,en,de` itd.)

## Wynik (co commitować)

- Kod testu, 10 plików `.properties`, walidator, dokumentacja.
- Do `docs/store/assets/D7/`: **tylko** `preview_contact.png` i `manifest.tsv` dla każdego
  języka plus pełne PNG dla `pl` i `en` (telefon i tablet10), do przeglądu przez właściciela.
  Pozostałe PNG generuje się lokalnie przed uploadem; nie zapychaj repo (dziś 153 to już ok. 60 plików).
- Krótki raport `docs/reports/dzien-7-sklep.md`: lista kadrów, uwagi z przeglądu stykówek
  (ucięte napisy, nieczytelne elementy na tablecie 7″), propozycja wyboru 8 kadrów na telefon
  i 8 na tablet (mogą się różnić).

## Kryteria odbioru

- `generateD7` tworzy 8 kadrów × 3 urządzenia Play × 10 języków (240 PNG) + Fold i QA, wszystkie
  przechodzą `checkPng` i walidator. Grafika promocyjna × 10.
- Kontrola wzrokowa stykówek wszystkich 10 języków przez agenta: brak uciętych podpisów,
  brak nakładek (samouczek, dymki, banery), HUD po dniu 5 bez zmian wizualnych, wyniki
  z dwoma wierszami medali, kadr dostępności czytelny.
- `listingLimits`, `storeAssetRequirements`, `d7CaptionsComplete` zielone; pełne testy zielone
  (eksport jest pomijany bez zmiennej środowiskowej, więc pełne testy nie wydłużają się).
- Żadnych zmian w kodzie gry (`app/src/main`) poza ewentualnym publicznym akcesorem potrzebnym
  do przygotowania sceny. Taką zmianę opisz w raporcie.

## Ryzyka

- Czas eksportu (Robolectric NATIVE, 10 języków × 5 urządzeń × 8 kadrów). Uruchamiaj partiami.
- Pamięć natywna przy wielu bitmapach (`forkEvery = 40` w testach). Zwalniaj bitmapy (`recycle`)
  po zapisie, jak `writePng`.
- Długie podpisy w de/fr/uk. Skracaj tekst, nie zmniejszaj czcionki.
- Zgodność z zasadami Play: zrzuty mają pokazywać grę, bez ocen, nagród sklepu i porównań
  z innymi grami. Podpisy bez superlatywów.

## Czego nie ruszać

- Rozgrywka, balans, `:sim`, PROTOCOL, zatwierdzony `compactTop` i HUD (zrzuty mają je
  pokazywać, nie zmieniać).
- Zestawy 117 i 153 i ich testy (zostają jako archiwum; nie usuwaj `generate()`/`generate153()`).
- Ikona aplikacji i grafika promocyjna (poza eksportem dla 10 języków).
