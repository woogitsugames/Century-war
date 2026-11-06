# D64: poprawka po scaleniu d61 + d62 (menu pionowe)

Gałąź `claude/dzien-64-poprawka` (baza `origin/main` 513ad748). Po scaleniu d61 (dymki menu po banerach,
nowe położenie dymka) i d62 (kafel Rozkazy) padały 3 testy:
`FirstHourJourneyFoldTest.firstHour`, `FirstHourJourneyFoldOddTest.firstHour` („etap 3: brak mapy
kampanii (MenuScreen)”) i `Tura8UiTest.menuBadgeWhenEliteAffordable` (dymek 21 = -1).

## Przyczyna

Obie usterki są w kodzie gry: d62 dodał w menu pionowym dziesiąty kafel (`orders`) **przed** Badaniami.

1. **Tura8, `foldInner` (1802×2000, d=3,6):** lista ma tam ok. 331 dp wysokości. Badania spadły z pozycji 5
   na 6, pod dolną krawędź listy (`research.visible = false`). Warunek dymka z d61 wymaga widocznego kafla
   Badań, więc dymek „Masz medale na: Doktryna elit” nie pojawiał się wcale. Ta sama ścieżka dotyczy gracza.
   phone720 i foldOuter były w porządku.
2. **FirstHour, `foldOuter` (904×2316, d=2,7):** przed d62 dziewięć kafli mieściło się bez przewijania.
   Przy dziesięciu brakowało ok. 47 dp, więc lista zaczęła się przewijać. Ścieżka (tak jak gracz) przewija
   menu do końca, żeby wejść w Badania. Po powrocie menu zostaje przewinięte o 47 dp, a Kampania jest prawie
   cała pod logo: środek kafla wypada nad `portraitTop`, więc `onTouch` połyka dotyk. Ani dymek, ani inny
   kafel niczego nie przechwytywał: Kampania po prostu nie była na ekranie.

## Poprawka (`screens/MenuScreen.kt`, tylko układ pionowy)

- Nowa kolejność kafli: Kampania, Oblężenie, Wyzwanie dnia, Potyczka, **Badania, Rozkazy**, Rekordy,
  Misje, Ustawienia, Kodeks. Pierwsze pięć kafli stoi tam, gdzie przed d62. Rozkazy nadal mają osobny
  kafel z licznikiem gwiazdek.
- `layoutPortrait`: jeśli cała lista mieści się po obniżeniu kafli najwyżej o 12 % (i nie poniżej 48 dp),
  kafle są niższe, a przewijania nie ma. Na foldOuter kafel ma ok. 51 dp zamiast 56. Gdy brakuje więcej
  miejsca (np. foldInner), wysokość się nie zmienia i lista się przewija, tak jak przedtem.
- Układ poziomy (Rozkazy obok Misji) bez zmian, więc wzorce `ScreenshotGoldenTest` (phone720 i foldOpen
  w poziomie) też się nie zmieniają.

Zrzuty po poprawce: `app/build/screenshots/tura8_menu_elite_foldOuter.png` (wszystkie 10 kafli, dymek pod
tabliczką, Badania podświetlone) i `tura8_menu_elite_foldInner.png` (Badania widoczne, dymek ich nie zasłania).

Testów nie zmieniałem: ich założenia (Kampania dotykalna po powrocie do menu, dymek elit na każdym
urządzeniu) są aktualne.

## Weryfikacja

- 3 padające testy: zielone.
- `*Menu*`, `*Orders*`, `*FirstHour*`, `*FirstMinutes*`, `*Tura8*`, `*Screenshot*`: 129 testów, 0 błędów.
- Pełne `:app:testDebugUnitTest --rerun`: 951 testów, 12 pominiętych, 0 błędów (BUILD SUCCESSFUL).
