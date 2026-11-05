# Dzień 56: drobne poprawki (francuskie „vous”, artefakty w Rankingach na Foldzie)

## 1. Francuski: ostatnie 7 tekstów z „tu” na „vous”

Pozycja „Do decyzji” z `d53-jezyki.md`. Zmienione w `L.kt` (kolumna fr):

| Klucz | Przed | Po |
|---|---|---|
| `ec_war_chest_d` | „…tu reçois 4 % de ton or…” | „…vous recevez 4 % de votre or…” |
| `week184_night_d` | „Tu commences avec la carte Vigilance.” | „Vous commencez avec la carte Vigilance.” |
| `week184_mercs_d` | „Tu commences avec la carte Marche forcée.” | „Vous commencez avec la carte Marche forcée.” |
| `week184_bastion_d` | „Tu commences avec la carte Blindage du front.” | „Vous commencez avec la carte Blindage du front.” |
| `resume_desc` | „Ta bataille précédente… Tu peux la terminer…” | „Votre bataille précédente… Vous pouvez la terminer…” |
| `ms_research` | „Achète %d niveau(x) de recherche” | „Achetez %d niveau(x) de recherche” |
| `ms_armory` | „Achète %d amélioration(s) d’arsenal” | „Achetez %d amélioration(s) d’arsenal” |
| `ms_unit_upgrades` | „Achète %d amélioration(s) d'unité” | „Achetez %d amélioration(s) d'unité” |

(`week184_*_d` to trzy klucze, razem 8 kluczy / 7 pozycji z raportu d53.) Formy liczby mnogiej
(`|`) i `%d` bez zmian. Test: `LTest.d56FrenchUsesVous` — brak form „tu/ta/ton/te”, „Achète”,
„commences”, „reçois”, „peux”, jest forma „vous/votre/-ez”.

## 2. Rankingi na zewnętrznym Foldzie: „⌄” i „—” między siatkami

**Co to było:** ptaki animowanego tła menu (`MenuBg.drawBirds`), rysowane na wysokości
0,20–0,36 h (przy 2316 px: ok. 463–834 px). Ptak to dwie kreski w kolorze argb(200, 40, 30, 60);
w zależności od fazy machania wygląda jak „⌄” albo płaska kreska „—”. Przy siatce zakładek
(Rekordy 2 × 2 + tablice 2 + 2 + 1) niebo zasłaniają przyciski, a ptaki widać tylko w ok. 18 px
szczelinie między siatkami (y ≈ 680–690), gdzie czytają się jak wskaźnik przewijania. Ptaki są
zamierzoną dekoracją tła, ale w tym miejscu efekt był niezamierzony. Ikony zakładek nie są
rysowane (`iconId = -1` w obu siatkach) — to nie one.

**Poprawka:**
- `MenuBg.draw(..., birds: Boolean = true)` — nowy parametr; `false` pomija ptaki. Inne ekrany bez zmian.
- `RankingsScreen.bgBirds = tabCols == 5 && !recordsTabs.grid`; `render` rysuje tło z `birds = bgBirds`.
  Telefon i rozłożony Fold (jeden rząd zakładek) zachowują ptaki.

**Test:** `CodexFoldOuterLabelsTest.boardTabsReadableOnFoldOuter` — `Recorder` zapisuje odcinki
w kolorze ptaków. Przy siatce (10 języków × 100/130%): `bgBirds == false`, zero odcinków ptaków,
w tym w szczelinie między dolną krawędzią zakładek Rekordów a górną zakładek tablic. Dla 1600×720
i 2184×1968 ptaki nadal są rysowane. Sprawdzone, że bez poprawki asercja łapie problem
(m.in. „de 130%: kreska tła w szczelinie między siatkami (y=680)”).

Zrzut po poprawce: `/tmp/claude-0/d56/d56_rankingi_foldOuter_de130.png` (test zapisuje też
`d51_rankingi_foldOuter_de130.png` / `_tr130.png` w tym katalogu; przed poprawką:
`/tmp/claude-0/d56/before/`).

## 3. Testy

`./gradlew :app:testDebugUnitTest --tests '*CodexFoldOuter*' --tests '*Rankings*' --tests '*LTest*'
--tests '*ScreenshotTestMenus*'` — BUILD SUCCESSFUL, 46 testów, 0 błędów (LTest 22,
LTestReview120/136, LTestScreens88, CodexLTest, CodexFoldOuterLabelsTest 7, ScreenshotTestMenus 6,
SkirmishSetupTest 1).
