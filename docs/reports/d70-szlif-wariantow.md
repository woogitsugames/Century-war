# D70 — szlif wariantów epok (po D69)

Gałąź `claude/dzien-70-szlif-wariantow` (od `origin/main` f219a336). Tylko UI: bez zmian
w `sim/src/main`, PROTOCOL 0.8.15 bez zmian.

## 1. Kodeks pokazuje nowe jednostki epok

`CodexScreen`, strona „Warianty”:

- W epokach jednej roli (Plemiona, Brąz, Żelazo, Proch) są te same dwie karty co w oknie wyboru
  bitwy: „Podstawowy skład” i nowa jednostka (`Defs.variantChoices`). Domyślnie wybrana jest nowa
  jednostka; wcześniej strona mówiła „Brak wariantów”.
- Nagłówek: nazwa i opis cechy (`v_*_d`); po wybraniu podstawowego składu — `v_base_d`
  z nazwą zastępowanej jednostki.
- Karta zmienionej roli stoi pierwsza i ma złotą ramkę: sylwetka (`drawUnitIcon`), opis
  (`u_*_fl`), pełne statystyki (koszt, HP, obrażenia, zasięg, prędkość, szkolenie, rozprysk,
  oblężenie). Wieża i ostrzał podstawowe, jak w bitwie.
- Spis „Warianty” pod kartami obejmuje też cztery nowe jednostki, z dopiskiem
  „Epoka · Tylko w potyczce”.

## 2. Osobny dymek wyboru nowej jednostki

- Nowa wskazówka `HintOverlay.RECRUIT` (bit 26, `Profile.HINT_COUNT` 26 → 27), tekst
  `hint218_recruit`: „Wybierz skład: podstawowy albo z nową jednostką w jednej roli. Wieża i ostrzał
  zostają te same.” `VARIANT` (`hint_variant54`, „inne jednostki i wieże”) pokazuje się tylko
  przy pełnych wariantach (Para, Krzem). Każdy z dymków ma własny bit, więc gracz zobaczy oba.
- Przy okazji: na telefonie 720p przy 130% karty wyboru zajmują prawie cały ekran i dymek nie
  mieścił się obok nich — był oznaczany jako widziany, ale nie był rysowany. Teraz dymek wyboru
  staje wtedy przy dolnej krawędzi ekranu (bez wskaźnika).

## 3. Okno wyboru na zewnętrznym Foldzie

Na wąskim, wysokim ekranie (`h > 1,3·w`) karty mają wysokość treści (dłuższy z dwóch opisów +
siatka sylwetek), a sylwetki stoją w siatce 2×2 (ok. 2× większe niż w jednym rzędzie).
Limit 430 dp zostaje; przy bardzo długim opisie maleje wiersz sylwetek. Wysokość liczona przy
łamaniu opisów (`fitTallVariantCards`), bez alokacji w klatce. Telefon 720p i pozostałe ekrany
bez zmian (jeden rząd czterech sylwetek). `assert74Variants` sprawdza teraz rzeczywistą krawędź
opisu z rysowania (HUD bitwy rysuje tekst do 115%) i górę pola sylwetek (`variantIconTop`).

## Teksty

`part218()` w `L.kt` (10 języków): `hint218_recruit`, `cx218_skirmish`.

## Zrzuty (obejrzane)

| Ekran | Przed | Po |
|---|---|---|
| Kodeks, zewn. Fold | `codex70before_foldOuter_pl_era{0,4}.png` (w notatkach sesji; „Brak wariantów”) | `app/build/screenshots/codex70_foldOuter_{pl,de}_era{0,1,2,4}.png`, `_base`, `_scroll` |
| Kodeks, 720p | `codex70before_phone720_pl_era{0,4}.png` | `app/build/screenshots/codex70_phone720_{pl,de}_era{0,1,2,4}.png`, `_base`, `_scroll` |
| Okno wyboru, zewn. Fold | `recruit69_foldOuter_pl_era*_choice.png` z D69 (wysokie karty, sylwetki ok. 30 dp) | `app/build/screenshots/recruit69_foldOuter_*_era*_choice.png` |
| Okno wyboru, 720p | `recruit69_phone720_*_choice.png` z D69 | bez zmian |
| Dymek wyboru | — | `app/build/screenshots/codex70_hint_{phone720,foldOuter}_{pl,de,tr}.png` |

Uwaga: na zewnętrznym Foldzie dymek wyboru (jak dotychczasowy `hint_variant54`) staje nad
kartami i przez 8 s zasłania wstęgę „Wybierz wariant epoki”; zachowanie bez zmian względem D69.

## Testy

- `EraRecruits70CodexScreenshotTest`: karty, wybór domyślny, nazwa nowej jednostki na karcie
  roli, opis cechy w nagłówku, podstawowy skład; brak kolizji przycisków (720p, Fold, pl/de, 130%);
  dymek `RECRUIT` widoczny i w ekranie (pl/de/tr).
- `HintOverlayTest.recruitChoiceShowsOwnHintAndKeepsVariantHint`.
- Wzorce PNG regresji (`src/test/resources/screens`) bez zmian.

**Pełne testy** (`:app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun :app:lintDebug :app:assembleDebug --continue`): EXIT=0, BUILD SUCCESSFUL w 29 min 37 s; app 994 testy (0 błędów, 13 pominiętych), sim 381 (0, 4 pominięte), serwer 125 (0, 1 pominięty).
