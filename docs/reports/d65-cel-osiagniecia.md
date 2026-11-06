# D65: „Następny cel” w menu i osiągnięcia za Rozkazy sztabu

Gałąź `claude/dzien-65-cel-osiagniecia` (baza `origin/main` z 6.11). Decyzje właściciela z 6.11:
(1) „Następny cel” w menu (R2-01) jako cienki pasek pod logo, nad kaflami, na wszystkich układach;
(2) 3–4 osiągnięcia za Rozkazy sztabu (d62).

## 1. Pasek „Następny cel” (`screens/MenuScreen.kt`)

- Pasek leży w **pasie powiadomień** między wstęgą podtytułu a kaflami (`noticeTop`…`noticeBottom`),
  na każdym układzie: poziomy (phone720, 20:9, rozłożony Fold) i pionowy (foldOuter, foldInner).
  Szerokość jak siatka kafli (poziomo) albo lista (pionowo). Wysokość 28 dp (najmniej 22 dp),
  wyśrodkowany w pasie. Wygląd jak dotąd: przyciemniona pigułka, ikona celu, złoty napis, „›”.
- Napis 13 sp (ograniczony wysokością paska), **najmniej 11 sp**; dłuższy tekst dostaje
  wielokropek (liczony raz przy odświeżeniu celu, nie w klatce). Dawniej linia znikała.
- **Jeden komunikat naraz:** pasek chowa się, gdy jest (albo czeka) baner, przypomnienie testera,
  dymek albo okno (wznowienie, ankieta) — `updateGoalVisibility()` co klatkę, bez alokacji.
  Wraca po banerze.
- Kafle **bez zmian** — pasek mieści się w istniejącym pasie (pion: ok. 53 dp, 720p: ok. 40 dp).
  Mechanizm d64 (obniżanie kafli o ≤ 12 %, min. 48 dp) nietknięty: na foldOuter wszystkie
  10 kafli widać bez przewijania (sprawdza test), na foldInner Badania nadal widoczne.
- Pion: dotyk paska nie jest połykany przez `onTouch` (pasek leży nad `portraitTop`).
  Cel dotyku 48 dp (`hitSlop`); kafle są wcześniej na liście przycisków.
- TalkBack: bez zmian (opis paska istniał od R2-01).
- Test `screens/MenuGoal65Test`: 5 urządzeń × pl/de/en × skala tekstu 100/130 % — pasek widoczny,
  pod podtytułem, nad kaflami, bez kolizji, ≥ 22 dp, napis ≥ 11 sp i mieści się; znika przy
  banerze i wraca; foldOuter bez przewijania; dotknięcie otwiera ekran celu.
- Wzorce `ScreenshotGoldenTest`: zaktualizowane `phone720_reg100_menu` i `foldOpen_reg100_menu`
  (zamierzona zmiana, wpis w `UPDATES.md`).

Zrzuty: `/tmp/claude-0/d65/menu_<urządzenie>_<pl|de>.png` (phone720, phone20x9, foldOpen,
foldInner, foldOuter) i `menu_<urządzenie>_pl_baner.png` (baner zamiast paska).

## 2. Osiągnięcia za Rozkazy sztabu (`meta/Achievements.kt`)

| id | Nazwa (pl / en) | Warunek | Medale | Punkty PGS |
|---|---|---|---|---|
| `orders_perfect_1` | Wzorowy rozkaz / Textbook Order | dowolny rozkaz na 3★ | 40 | 5 |
| `orders_all_9` | Weteran sztabu / Staff Veteran | ≥ 1★ we wszystkich 9 rozkazach | 150 | 15 |
| `orders_stars_27` | Marszałek polny / Field Marshal | komplet 27★ | 450 | 30 |
| `orders_hold_no_turrets` | Mur z ludzi / Living Wall | wygrany rozkaz obrony z dozwolonymi wieżami (6 i 9) bez zbudowania wieży | 150 | 15 |

> **D67 (decyzja właściciela 6.11):** po 2. partii (d66) `orders_all_9` = ≥ 1★ we wszystkich
> **18** rozkazach (300 medali, 25 pkt PGS), `orders_stars_27` = komplet **54★** (900 medali,
> 40 pkt PGS); klucze ID bez zmian. Suma PGS 1605, budżet 1650. Szczegóły: `d67-rozkazy-medale.md`.

- Trzy pierwsze liczone z profilu (`orderStars`, zapisane przed `Achievements.onBattleEnd`),
  czwarte z bitwy (`markOnce`). Nowe metryki na końcu enuma; kategoria TACTICS; niejawne: żadne.
  Medale przyznaje `unlockReady` jak przy innych osiągnięciach (baner i suma na wynikach).
- Teksty `part210` w `L.kt` (10 języków, bez przecinków — import PGS). Następna wolna: `part211`.
- Play Games: `play_games_ids.properties` (4 × `REPLACE_ME`), `PlayGamesIdsGenerator`,
  `PlayGamesIds`, `tools/pgs_sheet.py` → `play_games_sheet.csv` i `pgs_import/*` (zmieniła się
  kolejność listy, bo jest wg nagrody). **Razem 89 ID** (projekt, 5 rankingów, 83 osiągnięcia),
  suma punktów **1585** (budżet 1600, limit 2000). Ikony: `PgsIconsTest` (flaga z gwiazdką,
  ptaszkiem, koroną; tarcza z flagą), stykówki i mapowanie w `docs/store/assets/pgs/`.
- Liczby poprawione w: `OWNER_CHECKLIST`, `OWNER_INPUT`, `PLAY_CONSOLE`, `PLAY_GAMES_SETUP`,
  `play_games_sheet.md`, `RELEASE`, `data_safety`, `century-war.md`, karty sklepu (10 języków:
  „83 osiągnięcia”). Zlecenia `docs/tasks/16x–171` zostawione (stan historyczny, „sprawdź w kodzie”).
- Testy: `meta/OrdersAchievements65Test` (definicje, teksty, progi = `Orders.COUNT`/27, medale,
  jednorazowość, warunek obrony: rozkaz bez wież / atak / zbudowana wieża / porażka / inny tryb
  się nie liczą); liczby 79 → 83 w `Achievements*`, `Achievement60/130`, `Content176`,
  `PlayGamesIdsTest`.

Bez zmian: wyniki bitew, `:sim` i `PROTOCOL`, `compactTop`, celowanie, powiadomienia na wynikach,
nic nowego dla TalkBack.

## Weryfikacja

- `MenuGoal65Test`, `OrdersAchievements65Test`, `PgsIconsTest` (z `PGS_ICONS=true`), `*Menu*`,
  `*FirstHour*`, `*Tura8*`, `*HardCampaign189*`, `*TesterReminder*`, `*NextGoal*`, `meta.*`: zielone.
- Pierwsze pełne `:app:testDebugUnitTest --rerun`: 956 testów, 1 błąd —
  `ScreenshotTestFold.foldAudit98` wymagał 48 dp **wysokości rysunku** każdego przycisku; pasek celu
  ma 28 dp i cel dotyku 48 dp przez `hitSlop`. Audyt liczy teraz wysokość z `hitSlop` (tak jak
  `GameAccessibility` liczy obszar dotyku).
- Drugie pełne `:app:testDebugUnitTest --rerun`: **956 testów, 12 pominiętych, 0 błędów**
  (BUILD SUCCESSFUL, ok. 28 min).
- Uwaga (bez zmian w kodzie): `FirstHourImpression61Test.phonePl` bywa niestabilny — pierwsza bitwa
  ma ziarno `System.nanoTime()` i czasem trwa > 12 min (13,2 min także na czystym `main`;
  przy ponownym uruchomieniu zielony). W obu pełnych przebiegach d65 przeszedł.
- `updateScreenshots` zapisuje wzorce, ale kończy się błędem configuration cache
  (`DefaultTask` z referencją do skryptu) i przekłamuje polskie znaki z `ORG_GRADLE_PROJECT_screenReason`;
  wpis w `UPDATES.md` poprawiony ręcznie.
