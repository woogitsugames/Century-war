# D61: pierwsze wrażenie (i limit 60 FPS poza bitwą)

Gałąź `claude/dzien-61-pierwsze-wrazenie` (baza `origin/main` z 6.11.2026). Decyzje właściciela z 6.11:
(1) limit 60 FPS poza bitwą, bitwa bez zmian; (2) przejść pierwsze 10 minut świeżego gracza, wypisać
tarcia i naprawić 5–8 najważniejszych minimalnie.

## 1. Limit 60 FPS poza bitwą

- `engine/FramePacer.kt`: `capFor(userCap, battle)` — w bitwie i powtórce limit gracza/trybu baterii
  bez zmian (0 = tempo ekranu), poza nimi najwyżej 60 (niższy wybór gracza, np. 30 albo tryb baterii,
  obowiązuje dalej). `sleepNanos` liczy odstęp od początku do początku klatki z dokładnością do ns
  i zaokrągla 1 s / 60 w górę (16 666 667 ns). Dawne `Thread.sleep(ms)` obcinało 16,67 ms do 16 ms,
  czyli limit „60” dawał ok. 62 kl./s.
- `Game.uncappedScreen` (ustawiane tam, gdzie `battleActive`, przy każdej zmianie ekranu):
  `BattleScreen` albo `ReplayScreen`. `battleActive` (ThermalWatch) bez zmian.
- `GameView.loop`: limit z `FramePacer.capFor(frameCapFps, game.uncappedScreen)`; przy zmianie limitu
  wskazówka `Surface.setFrameRate` na wątku UI (60 Hz poza bitwą, w bitwie jak dotąd), więc ekran
  120 Hz może zwolnić bez szarpania (system zmienia tryb tylko płynnie).
- Animacje menu, mapy i Kodeksu liczą czas z `dt` (`MenuBg.time += dt`, `CampaignScreen.time += dt`),
  więc ich prędkość się nie zmienia, tylko liczba klatek.
- Test `engine/MenuFrameCap61Test`: menu i Kodeks 59–60 klatek w sekundzie (pętla z klatką 2 ms,
  czyli szybki telefon bez vsync), bitwa > 400 (bez limitu), wybór gracza 60 w bitwie nadal działa,
  pacer przy klatkach 0,1–16 ms nie przekracza 180 klatek / 3 s.

## 2. Pierwsze wrażenie — jak sprawdzałem

Test `screens/FirstHourImpression61Test` (na bazie `FirstHourJourneyBase`, cztery przypadki:
phone720 1600×720 d=2 i foldOuter 904×2316 d=2,7 × pl/en): menu startowe → „Zagraj” → samouczek palcem
ze zrzutem każdego kroku, zapowiedzi epoki i wyboru doktryny → reszta pierwszej bitwy (AI gracza)
ze zrzutem każdego dymka → wyniki (wejście i po animacji) → „Dalej” na mapę → dymek mapy → Wstecz do
menu → dymek menu. Każda klatka: kontrole nakładek, banera nad bitwą/wynikami i opisów (z bazy).
Nowe kontrole: dymek mapy i menu nie razem z banerem, pierwsza bitwa ≤ 12 min.

Zrzuty: `CW_D61_OUT` (domyślnie `app/build/screenshots/d61`)/`<urządzenie>_<język>/NN_*.png` i
`os_czasu.txt`. Przed poprawkami: `/tmp/claude-0/d61/przed/`, po: `/tmp/claude-0/d61/po/`,
zestawienia przed/po: `/tmp/claude-0/d61/porownanie_*.png`.

Oś czasu (phone720 pl, przed): samouczek 0:00–2:51 (kroki 1, 2, 4, 5, 7, 8, 11, 12, 13; między
„Pierwszy pokonany wróg” a „Nowa epoka” ok. 90 s gry bez wskazówki), 4 dymki po samouczku, wygrana
po 9,9 min (3★, +71 i +305 za 6 osiągnięć), mapa z banerem i dymkiem, menu z banerem i dymkiem.

## 3. Tarcia — naprawione

| # | Tarcie (co widać na zrzucie) | Poprawka | Przed / po |
|---|---|---|---|
| 1 | Dymek „Gdy bitwa się przeciąga, presja oblężnicza rośnie…” podświetla **pasek doświadczenia** („Epoka brązu”), a nie napis presji (telefon i Fold). | `BattleTopBar.siegeDrawn` (napis pod minimapą albo w bocznym polu), `HintOverlay.Targets.siege`; SIEGE wskazuje ten napis, gdy jest rysowany. | `porownanie_1_presja.png` |
| 2 | Dymek ulepszenia: „Dotknij **strzałki**”, a przycisk ma **szewrony** (dymek stopnia wieży mówi już o szewronach). | Nowy tekst `hint208_upgrade` (paczka 208, 10 języków): „Dotknij szewronów na jej przycisku.” | `porownanie_2_szewrony.png` |
| 3 | „Pomiń samouczek” skacze na pasek górny i **zasłania pauzę** i „Wróg: Plemiona” (krok Zbrojownia na telefonie). | `TutorialOverlay.layoutSkip`: w pierwszym przebiegu bez paska górnego; przed krawędziami ekranu pas tuż pod/nad opisem kroku. Teraz pod opisem, nad polem bitwy. | `porownanie_3_pomin.png` |
| 4 | Panel wież w samouczku: ręka zasłania **przycisk „Kup” slotu 2** i tytuł opisu — wygląda, jakby wskazywała drugi slot. | W otwartym panelu wież bez ręki (cel ma pulsującą obwódkę, reszta przyciemniona). | `porownanie_4_panel_wiez.png` |
| 5 | Mapa po pierwszej bitwie: **naraz** baner „Nowe osiągnięcie…” (potem „Kolejne… 5”) i dymek „Wybierz następny etap…”; baner zasłania nagłówek epoki. | Dymek mapy czeka na koniec banerów (`!g.hasNotices()`), ok. 6 s; etap 2 jest podświetlony od razu. | `porownanie_5_mapa.png` |
| 6 | Menu po powrocie: naraz baner osiągnięć i dymek „Masz medale na: Doktryna elit”; dymek **zasłania tabliczkę gracza** („Dowódc”, medale, gwiazdki ucięte) — na telefonie i Foldzie. | Dymek menu po banerach; `HintOverlay.keepClear`/`maxWidth`/`minTop`: poziomo wyśrodkowany między tabliczką a Kodeksem (2 wiersze), w menu pionowym pod tabliczką (na logo). `HintFrame.placeAvoiding` dostał pas przy prawej/lewej krawędzi jako ostatnią próbę przed dawnym zapasowym położeniem. | `porownanie_6_menu.png`, `porownanie_6_menu_fold.png` |

Bez zmian: wyniki bitew, symulacja i `PROTOCOL`, `compactTop`, celowanie ostrzału, powiadomienia
(nigdy na wynikach — kontrola w teście), nic nowego dla TalkBack. Nowy tekst tylko w `part208`.
Wzorce zrzutów (`ScreenshotGoldenTest`) bez zmian — żadna poprawka nie trafia w zapisane kadry.

## 4. Tarcia — zostawione

- **„Następny cel” w menu niewidoczny** na obu urządzeniach: na phone720 pod kaflem Misji jest
  < 20 dp, a menu pionowe Folda nie ma linii celu (`refreshGoal`). Cel widać na wynikach (dotykalny),
  a w menu podpowiada go „!” na Badaniach i dymek. Pokazanie wymaga zmiany układu kafli — do decyzji.
- **Ok. 90 s gry bez wskazówki** między „Pierwszy pokonany wróg” a „Nowa epoka” (napełnianie XP).
  To tempo bitwy (XP = wynik bitwy), nie samouczka; gracz w tym czasie kupuje wojsko.
- **Pierwsza bitwa 8,9–10,6 min** (AI gracza; człowiek z ostrzałem i awansem bywa szybszy) — balans.
- **Zapowiedź epoki → wybór doktryny → krok „Wieże obronne”** jeden po drugim w ok. 5 s (trzy
  plansze z tekstem); doktryna „Czujność (w tej bitwie nie ma mgły)” bywa bezużyteczna w samouczku.
  Losowanie doktryn wpływa na symulację — nie ruszałem.
- Dymek mapy zasłania węzeł etapu 1 i kawałek ścieżki (etap 2 odsłonięty i podświetlony).
- Drobne: ręka w kroku „Złoto i teren” nachodzi na licznik zabitych 0 : 0; skrót „Zbroj.” na
  przycisku; w en dymek mapy łamie „from the menu.” do drugiego wiersza.

## Weryfikacja

- `FirstHourImpression61Test` (4 przypadki) — 0 problemów po poprawkach.
- `Tutorial*`, `FirstHour*`, `Menu*`, `*Screenshot*` (z `ScreenshotGoldenTest`), `Prerelease*`,
  `LowEnd*`, `HintOverlay*`, `Tura8*`, `LTest*`, `BattleAlerts*`: zielone.
  `Tura8UiTest.menuBadgeWhenEliteAffordable` czyści banery przed oczekiwaniem dymka (nowa zasada
  „jeden komunikat naraz”).
- Pełne `:app:testDebugUnitTest`: 939 testów, 12 pominiętych, 1 błąd —
  `FirstMinutesTest.oneTimeNextStepHintAppearsAfterFirstBattleEvenOnDefeat` oczekiwał dymka mapy
  w pierwszej klatce, a świeży profil ma baner serii dni. Test czyści teraz banery (i sprawdza, że
  dymek nie pojawia się razem z banerem); po zmianie zielony. `:sim` nie był zmieniany.
