# D5: TalkBack dla przycisków HUD w bitwie

**Gałąź:** `claude/dzien-5-talkback` z `origin/integ/tura10`. **Teksty:** tylko `part178()` w
`engine/L.kt` (10 języków: pl, en, de, es, fr, it, pt, uk, nl, tr; `putAll(part178())` w bloku
`putAll`; liczba `putAll(part` = liczba `private fun part`). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Podstawa: `docs/research/R1-backlog.md`,
sekcja 3.2. Commity po polsku, wypychaj często.

## Cel

Dziś poza pauzą bitwa ma dla czytnika ekranu jeden węzeł „Bitwa, Pauza”
(`GameAccessibility`, rodzaj `BATTLE`). Po zmianie osoba z TalkBack (eksploracja dotykiem albo
przesuwanie fokusu) i z Switch Access:
- słyszy stan bitwy (złoto, doświadczenie do awansu, HP obu baz, fala w Endless);
- szkoli jednostki, ulepsza je, wywołuje ostrzał (bez przeciągania), Zryw, postawę, ewolucję,
  dowódcę, otwiera wieże, zmienia tempo i pauzuje;
- wybiera doktrynę, kartę Endless i wariant epoki z pełnym opisem;
- słyszy krótkie komunikaty o ważnych zdarzeniach.

Bez usługi dostępności nic nie może się zmienić: zero alokacji i brak budowy drzewa
(`refreshIfEnabled()` wraca od razu).

## Stan w kodzie (wiersze z `integ/tura10` = 6119948)

- `engine/GameAccessibility.kt`:
  - stałe `NONE/FALLBACK_ID/BUTTONS/PAUSE/BATTLE/OTHER/ACCESS_POINTER` (24–30);
  - `Node`/`Tree` (32–56);
  - `provider.createAccessibilityNodeInfo` (67–102), `findFocus` (104), `performAction` (108–153),
    gdzie `ACTION_CLICK` (128) wysyła przez `game.post` sztuczne DOWN/UP w środek `button.r`
    (+`hitOffsetX/Y`) i dla `BATTLE` przerywa, gdy `!paused` jest fałszem. Uwaga: dziś w bitwie
    przepuszcza tylko węzeł Pauzy;
  - `refreshIfEnabled()` (156), `refresh()` (162–222), `eligible()` (225–234; bitwa tylko w pauzie),
    `boundsFor()` (236), `matches()` (244–270), `onHover()` (272), `sendNodeEvent()` (293).
  - Id węzłów `BUTTONS/PAUSE` = indeks w `screen.buttons` + 1. W bitwie to się nie nadaje, bo
    przycisk dowódcy pojawia się od etapu 8 i przesuwa indeksy.
- `engine/GameView.kt:206`: `accessibility.refreshIfEnabled()` po każdej klatce (wątek gry).
- `screens/BattleScreen.kt`:
  - stałe ID przycisków (172–199): `UNIT0=0..3`, `TURRET=10`, `ARMORY=11`, `BARRAGE=12`,
    `RALLY=13`, `STANCE=14`, `SPEED=15`, `EVOLVE=16`, `PAUSE=17`, `DOCTRINE0=30..32` (doktryny
    **i** karty Endless), `SLOT_BUY0=40..`, `SLOT_SELL0=50..`, `CLOSE=60`, `UNIT_UPGRADE0=70..73`,
    `UNIT_UPGRADE_BUY=74`, `UNIT_UPGRADE_CLOSE=75`, `VARIANT0=80..81`, `SLOT_TIER0=110..`,
    `COMMANDER=203`, `queueInfoBtn` = 90, `EvolveBanner.GO/WAIT`;
  - pola przycisków 237–270; `onClick` 870–935 (np. `BARRAGE -> barrageView.onButton()`,
    karty i doktryny 921–934); `accessibilityLabel` 1095–1100 (dziś tylko pauza);
  - stan: `paused`, `finished`, `showTurrets`, `showUpgrade`, `aimingBarrage`, `speed`,
    `battle.pendingChoice/pendingCards/pendingVariant`, `eraIntro?.active`, `tut` (samouczek).
- `screens/BattleBottomHud.kt`: `layout()` (52–110, trzy układy: jeden, dwa, trzy rzędy;
  `unitUpgrade[i]` to strzałka 24 dp **w rogu** przycisku jednostki z `hitSlop`) oraz
  `refreshButtons()` (ok. 111–250: `b.sub` = koszt, `enabled`, `progress`).
- `screens/BarrageAiming.kt`: `onButton()` (52; pierwsze dotknięcie włącza celowanie, drugie
  strzela w punkt domyślny), `fireAimedBarrage(worldX)` (114), `snapBarrageTarget(x)` (128),
  `avoidOwnUnits` (160).
- Wartości do opisów: `battle.player` / `battle.enemy` (`Side` w `sim/.../Battle.kt`: `gold`,
  `xp`, `era`, `baseHp`, `baseMaxHp`, `aliveCount`, `cap`, `queue`, `abilityCd`, `commanderCd`),
  `battle.wave`, `battle.endless`, `Defs.evolveXp[era]`, `battle.canEvolve(p)`, `battle.abilityReady(p, i)`,
  `battle.canSummonCommander(p)`, `battle.unitCost(p, role)`, nazwy jednostek z `Defs.eras[...]`.
- Testy: `app/src/test/java/com/centurywar/engine/GameAccessibility164Test.kt` (`Fixture`:
  `frame()`, `node(id)`, `press(id)`, `childCount()`; test
  `battleExposesOnlyPauseUntilPausedAndThenOnlyFiveControls` w wierszu 169 trzeba zmienić).

## Projekt

### 1. Nowy rodzaj drzewa `BATTLE_HUD` (zastępuje `BATTLE`, gdy bitwa trwa i nie ma pauzy)

Rodzaje w bitwie (wybór w `refresh()`):
- `finished` → `RESULTS`: przyciski wyników (`restartEnd`, `replayEnd`, `continueBtn`,
  zakładki). To zwykła lista przycisków, jak `BUTTONS`.
- `paused` → `PAUSE` (bez zmian).
- `eraIntro?.active` → jeden węzeł z tytułem zapowiedzi i akcją „Dalej”.
- `pendingVariant != null` / `pendingChoice != null` / `pendingCards != null` → `CHOICE`:
  `variantBtn[0..1]` albo `doctrineBtn[0..2]` z pełnym opisem (nazwa + rzadkość karty + opis,
  ten sam tekst co dymek: `BattleScreen` buduje go z `cardDescKeys`/`doctrineDescKeys`;
  wydziel funkcję zwracającą opis, nie kopiuj logiki). Czas stoi, więc odświeżanie nie jest problemem.
  Na otwarcie okna: `setPaneTitle(...)` (API 28+) na węźle głównym.
- `showTurrets` → `TURRETS`: `slotBuy/slotSell/slotTier` widoczne + `closeBtn`.
- `showUpgrade >= 0` → `UPGRADE`: `upgradeBuy`, `upgradeClose` + węzeł informacyjny z opisem.
- inaczej → `BATTLE_HUD`.
- Samouczek (`tut != null`): tylko przyciski, na które `tut.allows(b)` pozwala, plus węzeł
  informacyjny z tekstem bieżącego kroku samouczka (wtedy samouczek da się przejść z TalkBack).

### 2. Stałe ID węzłów (nie indeksy)

```
FALLBACK_ID = 0                       (bez zmian)
INFO_GOLD = 20_001, INFO_XP = 20_002, INFO_BASES = 20_003, INFO_WAVE = 20_004,
INFO_ARMY = 20_005, INFO_TUTORIAL = 20_006, INFO_UPGRADE = 20_007
STATUS = 20_100                       (żywy region z komunikatami)
BTN_BASE = 10_000                     (węzeł przycisku = BTN_BASE + Button.id; np. jednostka 0 = 10_000, BARRAGE = 10_012, COMMANDER = 10_203)
```
`Button.id` w bitwie są unikalne i stałe. Sprawdź unikalność testem, bo `EvolveBanner.GO/WAIT`
i `queueInfoBtn` = 90 też muszą się mieścić. Menu i ustawienia zostają przy dotychczasowym
`i + 1`, bo testy 164 na tym polegają.

### 3. Kolejność przechodzenia (`BATTLE_HUD`)

Kolejność w liście `nodes` jest kolejnością dla przesuwania fokusu:
1. `INFO_GOLD` „Złoto: 340”;
2. `INFO_XP` „Doświadczenie: 60% do: Epoka brązu” albo „Gotowe do awansu”;
3. `INFO_BASES` „Twoja baza 82%, baza wroga 64%”;
4. `INFO_WAVE` (tylko Endless) „Fala 12, następna: …”, `INFO_ARMY` „Armia 9/12”;
5. jednostki `UNIT0..3`;
6. akcje w kolejności z `actionBtns` (`turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn, commanderBtn`) i `evolveBtn`;
7. `evolveBanner` (jeśli widoczny);
8. `pauseBtn`;
9. `STATUS` na końcu.
Tryb leworęczny zmienia tylko pozycje, kolejności nie.

Węzły informacyjne: `className = "android.widget.TextView"`, `isClickable = false`, granice to
prostokąty, w których wartość jest rysowana (`goldRect`, `xpRect`, `mini`, paski HP z
`BattleTopBar`). **Bez zmiany rysowania i układu `compactTop`.** Jeśli prostokąta nie ma, użyj
`goldRect`.

### 4. Treść węzłów przycisków

- **Jednostka:** `contentDescription = "<nazwa jednostki>, <koszt> złota"`. Stan
  (`setStateDescription`, API 30+; na 26–29 dopisz do opisu po przecinku): „za mało złota”,
  „armia pełna”, „zablokowane” (ikona kłódki), „w szkoleniu, w kolejce N”. `isEnabled = b.enabled`.
  **Węzeł zostaje w drzewie także wyłączony.**
- **Ulepszenie jako akcja niestandardowa** węzła jednostki, nie osobny węzeł (strzałka 24 dp leży
  na przycisku jednostki). Dodaj `res/values/ids.xml`:
  `<item type="id" name="cw_a11y_upgrade"/>`, `cw_a11y_barrage_front`, `cw_a11y_barrage_cluster`,
  `cw_a11y_barrage_base`. Akcja: `AccessibilityAction(R.id.cw_a11y_upgrade, "Ulepsz: <nazwa>")`
  → `performAction` wywołuje tę samą ścieżkę co dotknięcie `unitUpgrade[i]` (otwiera panel
  ulepszenia, rodzaj `UPGRADE`). Przy trzech rzędach HUD (`threeRowHud`) strzałki są osobnymi
  przyciskami 48 dp. Wtedy też tylko akcja, bez osobnego węzła, żeby było jednolicie.
- **Ostrzał:** opis „Ostrzał” + stan „gotowy” / „odnowienie: 12 s” (sekundy zaokrąglone
  w górę do 5 s, żeby nie zmieniać tekstu co sekundę). `ACTION_CLICK` **nie** włącza celowania
  palcem. Trzy akcje niestandardowe:
  - „Ostrzał: najbliższa grupa wrogów” → `snapBarrageTarget(battle.player.front + 400f)`,
  - „Ostrzał: czoło wroga” → `snapBarrageTarget(<x najbliższego wroga>)`,
  - „Ostrzał: przed moją bazą” → `snapBarrageTarget(battle.player.baseFront + 260f)`,
  następnie `fireAimedBarrage(x)`. Celownik dalej omija własnych żołnierzy (`avoidOwnUnits`),
  więc zatwierdzony mechanizm celowania się nie zmienia. Komenda trafia do symulacji jak po
  przeciągnięciu (`commands.barrage(worldX)`), więc powtórki są zgodne. Domyślne `ACTION_CLICK`
  = pierwsza akcja.
- **Zryw, postawa, zbrojownia, ewolucja, dowódca, tempo, wieże, pauza:** opis = nazwa + koszt
  lub stan („Postawa: obrona”, „Tempo ×3”, „Dowódca: 40 s”, sekundy zaokrąglane jak wyżej).
  Postawa i tempo: `isCheckable = false`, stan przez `stateDescription`.
- **Granice:** obszar dotyku, nie rysunek: `b.r` powiększone o `hitSlop` i przesunięte o
  `hitOffsetX/Y`, przycięte do widoku. Testem sprawdź, że w żadnym z trzech układów HUD granice
  przycisków się nie nakładają. `onHover` bierze pierwszy pasujący węzeł.

### 5. Kliknięcie w bitwie

W `performAction`/`ACTION_CLICK` (128–152) dla nowych rodzajów:
- sprawdzaj `game.current === snapshot.screen`, `!finished`, ten sam rodzaj drzewa co w migawce
  (np. kliknięcie z `CHOICE` po zamknięciu wyboru jest ignorowane), `button.visible && enabled`;
- wywołuj **tę samą ścieżkę co dotyk** (sztuczne DOWN/UP z `ACCESS_POINTER`), z wyjątkiem
  ostrzału i akcji niestandardowych. Te wołają metody `BattleScreen`/`BarrageAiming` przez
  `game.post`.
- Samouczek: `tut.allows(b)` jest sprawdzane w `onClick` (875–876), więc zostaje bez zmian.

### 6. Częstotliwość odświeżania (najważniejsze dla wydajności)

`refresh()` jest wołane po każdej klatce. W `BATTLE_HUD` wartości (złoto, odnowienia, HP)
zmieniają się ciągle, a `matches()` porównuje dziś `label/sub`, więc bez zmian drzewo
przebudowywałoby się 60 razy na sekundę.
- **Struktura** (lista widocznych ID, `enabled`, `selected`, rodzaj, rozmiar widoku, język):
  sprawdzana co klatkę, **bez alokacji** (pętla po polach, jak `matches()`). Zmiana struktury
  oznacza natychmiastową przebudowę.
- **Teksty z liczbami** (węzły `INFO_*`, koszt, sekundy odnowienia): przebudowa najwyżej co
  **1000 ms** (`SystemClock.uptimeMillis()` w `Tree.builtAt`) i tylko gdy tekst faktycznie się
  zmienił. HP baz zaokrąglaj do 5%, złoto do pełnych 10 (wyjątek: < 100 dokładnie).
- Po przebudowie wysyłaj `TYPE_WINDOW_CONTENT_CHANGED` **ze źródłem konkretnego węzła**
  (`setSource(view, id)`, `contentChangeTypes = CONTENT_CHANGE_TYPE_TEXT` albo
  `CONTENT_CHANGE_TYPE_STATE_DESCRIPTION` na API 30+), a zdarzenie dla całego widoku tylko przy
  zmianie struktury.
- Budżet: przy włączonej usłudze średnio ≤ 2 przebudowy na sekundę w zwykłej bitwie; brak
  przebudów, gdy nic się nie zmienia.

### 7. Komunikaty (węzeł `STATUS`)

`View.announceForAccessibility` jest od API 36 przestarzałe, dlatego używamy żywego regionu:
`STATUS` z `setLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)`, tekst = ostatni komunikat,
zdarzenie `TYPE_WINDOW_CONTENT_CHANGED` z `CONTENT_CHANGE_TYPE_TEXT` przy zmianie.
Zdarzenia (wykrywane na wątku gry, porównaniem z poprzednią klatką, bez alokacji poza samą zmianą):
awans epoki gracza, awans wroga, baza gracza poniżej 50% i 25%, baza wroga poniżej 25%, fala
z bossem, Zryw/ostrzał/dowódca gotowe po odnowieniu (tylko gdy gracz ich wcześniej użył),
wybór kart lub doktryny otwarty, koniec bitwy. Limit: **1 komunikat na 3 s**. Ważniejszy
(baza < 25%, koniec) zastępuje oczekujący mniej ważny.

### 8. Teksty `part178()`

Klucze z prefiksem `a178_`. Przykłady: `a178_gold` („Złoto: %d”), `a178_xp` („Doświadczenie:
%d%% do: %s”), `a178_xp_ready`, `a178_bases` („Twoja baza %d%%, baza wroga %d%%”), `a178_wave`,
`a178_army` („Armia %d/%d”), `a178_unit` („%s, %d złota”), `a178_no_gold`, `a178_army_full`,
`a178_locked`, `a178_training`, `a178_upgrade_action` („Ulepsz: %s”), `a178_barrage_cluster`,
`a178_barrage_front`, `a178_barrage_base`, `a178_ready`, `a178_cooldown` („odnowienie: %d s”),
`a178_era_up`, `a178_enemy_era_up`, `a178_base_half`, `a178_base_low`, `a178_enemy_base_low`,
`a178_boss`, `a178_choice_open`, `a178_status`. Używaj istniejących nazw (`L[Defs.eraKeys[..]]`,
nazwy jednostek, `L["hold"]`, `L["advance"]`, `L["evolve"]`, `L["comm125_short"]`) zamiast nowych.
Plural: `L.plural`, gdy liczba stoi przy rzeczowniku (np. „12 s” nie wymaga). `LTest` sprawdza 10 języków.

## Testy Robolectric do dopisania (`GameAccessibility164Test` albo nowy `BattleAccessibilityTest`)

Wzór: `Fixture` z `GameAccessibility164Test` (`Game(host, profile, Quality.LOW)`, `GameView`,
`view.refreshAccessibilityNodes()`, `provider.createAccessibilityNodeInfo(id)`).
1. **HUD ma węzły:** bitwa w potyczce (`BattleConfig(Mode.SKIRMISH, ...)`) po kilku klatkach:
   istnieją węzły `10_000..10_003`, `10_012`, `10_017`, `20_001..20_003`. Opis jednostki zawiera
   nazwę i koszt. Węzeł jednostki ma akcję `R.id.cw_a11y_upgrade`.
2. **Stałe ID przy dowódcy:** etap kampanii 8 (dowódca dostępny) i etap 1: ten sam przycisk
   ma to samo ID węzła, a fokus na `10_012` przeżywa pojawienie się dowódcy.
3. **Kliknięcie jednostki:** `ACTION_CLICK` na `10_000` przy wystarczającym złocie dodaje
   jednostkę do kolejki (`battle.player.queueSize` rośnie po `frame()`). Przy braku złota
   `isEnabled == false` i `performAction` zwraca `false`.
4. **Ostrzał z akcji:** przy `abilityReady(p, 0)` akcja `cw_a11y_barrage_cluster` odpala ostrzał
   (odnowienie > 0), `aimingBarrage` pozostaje `false`, a cel nie pokrywa własnych jednostek
   (asercja jak w testach `avoidOwnUnits`). Log komend zawiera komendę ostrzału.
5. **Wybór karty:** Endless do oferty kart (`pendingCards != null`): drzewo ma 3 węzły
   `10_030..10_032` z opisem karty i `paneTitle`. Kliknięcie wybiera kartę.
6. **Częstotliwość:** 600 klatek bitwy z włączoną usługą (wymuś `refresh()` jak w testach 164):
   liczba przebudów (`revision`) ≤ 2 na sekundę symulacji. Przy 600 klatkach bez zmian (pauza
   wymuszona w symulacji) przyrost `revision` = 0.
7. **Brak kosztu bez usługi:** `refreshIfEnabled()` bez aktywnego TalkBack nie zmienia `revision`.
   Istniejący pomiar alokacji pętli (`AllocationTest`/`LowEndScreensPerformanceTest`) bez zmian.
8. **Granice:** dla telefonu 1600×720, zewnętrznego Folda 904×2316 (trzy rzędy) i trybu
   leworęcznego żadne dwa węzły przycisków nie mają nachodzących się `boundsInParent`.
   Każdy ma ≥ 48 dp w obu wymiarach albo jest najbliższy możliwy do 48 dp przy trzech rzędach
   (`min(48 * dp, ...)` w `layout`). Zapisz rzeczywistą wartość w komunikacie asercji.
9. **Komunikat:** po wymuszeniu awansu epoki (`commands.evolve()` przy wystarczającym XP) węzeł
   `STATUS` ma tekst `a178_era_up`. Drugi komunikat w ciągu 3 s czeka, chyba że jest ważniejszy.
10. **Pauza i wyniki:** zmiana `battleExposesOnlyPauseUntilPausedAndThenOnlyFiveControls` (169):
    w pauzie nadal tylko 5 kontrolek pauzy, a po zakończeniu bitwy węzły wyników z opisem.
11. **Samouczek:** w etapie 1 z samouczkiem węzeł `INFO_TUTORIAL` ma tekst bieżącego kroku,
    a kliknięcie przycisku niedozwolonego w tym kroku nic nie robi.

## Kryteria odbioru

- Wszystkie testy wyżej i pełne testy zielone:
  `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.
- Złote powtórki i PROTOCOL bez zmian (`:sim:test`), bo akcje dostępności wysyłają te same komendy.
- Zrzuty ekranu (`ScreenshotTest`, `regression100`) identyczne, bo nic się nie rysuje inaczej.
- `docs/RELEASE.md`, sekcja „Testy ręczne czytnika ekranu (164)”: zaktualizowany opis zakresu
  i nowe kroki: eksploracja HUD palcem na telefonie 720p i Foldzie (trzy rzędy), akcja Ulepsz
  z menu akcji TalkBack (gest), ostrzał z akcji, wybór karty Endless, komunikat o awansie epoki,
  Switch Access na jednostkach, porównanie płynności z wyłączoną usługą.
- Krótki raport `docs/reports/dzien-5-talkback.md`: co zrobione, co zostało (np. kampania,
  kodeks).

## Ryzyka

- **Wydajność z włączoną usługą:** budowa `Node` z `String` przy każdej przebudowie; dlatego
  limit 1 s dla tekstów i porównanie struktury bez alokacji.
- **Wątki:** migawka (`Tree`) jest niezmienna i powstaje na wątku gry; provider czyta ją
  na wątku UI. Nie czytaj `battle` z wątku UI.
- **Gry czasu rzeczywistego a TalkBack:** podwójne dotknięcie trwa. Tempo ×½ przyjdzie
  w R2-03 (`docs/tasks/R2-03-system-a11y-settings.md`). W tym zadaniu nie zmieniaj tempa.
- **Android 8 (API 26–29):** brak `stateDescription`, więc stan w `contentDescription`.
  `setPaneTitle` jest od API 28; na 26–27 pominąć.

## Czego nie ruszać

- Zatwierdzony układ górnego paska `compactTop` (`BattleTopBar`) i układ HUD
  (`BattleBottomHud.layout`), w tym tryb leworęczny i trzy rzędy na Foldzie.
- Mechanizm celowania ostrzału przez przeciąganie (decyzja właściciela). Dodajemy tylko akcje
  dostępności.
- Balans, `:sim`, PROTOCOL, `BattleSnapshot.VERSION`, złote powtórki.
- Węzły menu, ustawień i pauzy (id `i + 1`), poza dopasowaniem testu 169.
