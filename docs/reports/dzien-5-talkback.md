# Dzień 5: TalkBack dla HUD w bitwie

Zlecenie: `docs/tasks/D5-talkback-hud.md`. Gałąź `claude/dzien-5-talkback`.

## Co zrobione

- `engine/GameAccessibility.kt`: zamiast jednego węzła „Bitwa, Pauza” rodzaje drzewa
  `BATTLE_HUD`, `TURRETS`, `UPGRADE`, `CHOICE`, `ERA_INTRO`, `RESULTS` (pauza bez zmian).
  Stałe ID: przycisk = `10_000 + Button.id`, informacje `20_001..20_009`, komunikaty `20_100`.
  Menu, ustawienia i pauza zostały przy `i + 1`.
- Węzły informacyjne (TextView, bez kliknięcia): złoto (do 10, poniżej 100 dokładnie),
  doświadczenie (% do następnej epoki albo „gotowe do awansu”), bazy (do 5%), fala i następny
  motyw w Endless, armia. Granice = prostokąty rysowania (`goldRect`, `xpRect`, minimapa z paskami,
  `armyRect`, zastępczo `goldRect`); rysowanie i `compactTop` bez zmian.
- Jednostki: „nazwa, koszt złota” + stan (w szkoleniu, w kolejce N, za mało złota, kolejka pełna,
  zablokowane); wyłączony węzeł zostaje. Ulepszenie to akcja `cw_a11y_upgrade` na węźle jednostki
  (także przy trzech rzędach), wywołuje to samo dotknięcie strzałki.
- Ostrzał: `ACTION_CLICK` i trzy akcje (najbliższa grupa, czoło wroga, przed moją bazą) strzelają
  bez celowania palcem przez `snapBarrageTarget` + `fireAimedBarrage` → ta sama komenda
  `BARRAGE_AT` co po przeciągnięciu. Odnowienia zaokrąglane w górę do 5 s.
- Pozostałe akcje: nazwa + koszt/stan (Postawa: Trzymaj/Naprzód, Prędkość ×3, Dowódca, Zbrojownia
  z poziomem, Awans: epoka / brakujące XP). Kliknięcie = sztuczne DOWN/UP w punkcie obszaru dotyku,
  którego nie przykrywa wcześniejszy przycisk (np. strzałka ulepszenia na małym przycisku).
- Wybór doktryny/karty/wariantu: pełny opis (nazwa, rzadkość, ten sam opis co na karcie),
  `paneTitle` na API 28+. Samouczek: węzeł z tekstem kroku i tylko dozwolone przyciski.
- Odświeżanie: struktura (id, enabled, selected, granice, klucz stanu) porównywana co klatkę bez
  alokacji; teksty najwyżej co 1000 ms i tylko przy faktycznej zmianie, wtedy zdarzenie
  `TYPE_WINDOW_CONTENT_CHANGED` ze źródłem konkretnego węzła. Bez usługi `refreshIfEnabled()` wraca od razu.
- Komunikaty (żywy region POLITE): awans epoki gracza i wroga, baza < 50% i < 25%, baza wroga < 25%,
  boss, Ostrzał/Zryw/Dowódca gotowe po użyciu, otwarty wybór, koniec bitwy. 1 komunikat na 3 s,
  ważniejszy (baza < 25%, koniec) od razu i zastępuje oczekujący.
- Teksty: `part178()` w `L.kt` (10 języków), `res/values/ids.xml` z czterema id akcji.
- Testy: `BattleAccessibilityTest` (12 testów) i zmieniony test bitwy w `GameAccessibility164Test`.
- `docs/RELEASE.md`: nowy zakres i kroki testów ręcznych.

## Co zostało

- Mapa kampanii, Kodeks, ekrany trybów i wyników poza bitwą nadal bez osobnych kontrolek.
- Suwaki głośności w pauzie nadal wymagają dotyku.
- Tempo ×½ dla graczy z TalkBack: zlecenie R2-03.
- Przesuwanie kamery i minimapa nie mają akcji dostępności (ostrzał nie wymaga kamery).
