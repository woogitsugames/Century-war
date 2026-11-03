# Noc 5: pierwsza godzina gracza

Gałąź `claude/noc-5-pierwsza-godzina` (od `integ/tura10`, dc656370).
Test: `app/src/test/java/com/centurywar/screens/FirstHourJourneyTest.kt`.

## Jak to sprawdzałem

`FirstHourJourneyTest.firstHour` przechodzi ścieżkę zupełnie nowego profilu na dwóch urządzeniach:
telefon 1600×720 d=2 i zewnętrzny ekran Folda 904×2316 d=2,7 (wcięcie 88 px), po polsku i po
angielsku, czyli cztery przejścia:

1. pierwsze uruchomienie, czyli menu (bez „Co nowego”, bo to świeża instalacja);
2. „Graj” uruchamia samouczek. Samouczek przechodzę palcem, prawdziwymi dotknięciami,
   jak w `NewPlayerJourneyTest`;
3. resztę etapu 1 i etapy 2–8 gra AI gracza (`Ai(battle, 0)` przez `BattleController`). To profil
   „nowy” z `BalanceReport`, ale z prawdziwymi zakupami badań w menu. Po porażce gracz próbuje do
   3 razy;
4. pierwsze badania po etapie 2 (najtańsze dostępne, aż skończą się medale), a kolejne po
   kampanii;
5. potyczka, Endless i wyzwanie dnia (AI, kapitulacja z menu pauzy po 15–18 min);
6. „Co nowego”: Ustawienia → O grze → Historia zmian, potem Wstecz aż do menu.

Co klatkę bitwy test sprawdza, że:
- jednocześnie widać najwyżej jedną nakładkę (samouczek, dymek, zapowiedź epoki, baner awansu);
- dymek nie zasłania aktywnego przycisku HUD;
- dymek samouczka nie zasłania swojego celu, Wodza ani przycisku wieży w slocie 1;
- nad bitwą nie ma banera powiadomień.

Na każdym ekranie poza bitwą test sprawdza, że:
- baner powiadomień nie nachodzi na Wstecz, tytuł, dymek ani kafle menu;
- ekran ma przycisk Wstecz, a systemowe Wstecz zawsze zmienia ekran (brak martwych końców);
- każdy aktywny przycisk ma opis TalkBack.

Po każdej bitwie test sprawdza wyniki i przycisk „Dalej”. Na koniec porównuje nagrody: porażka
musi dać mniej niż wygrana tego samego etapu, a wygrane rosną z etapem przy tych samych
gwiazdkach.

`FirstHourJourneyTest.tutorialInterruptedResumedSettlesOnce` sprawdza przerwanie samouczka:
- przy kroku „pierwsze zabicie” wywołuję `onPause` i robię kopię katalogu danych (to model zabitego
  procesu);
- przy drugim uruchomieniu menu pyta o wznowienie. „Wznów” otwiera bitwę w pauzie, bez nakładki
  samouczka, i bitwa kończy się wygraną;
- medale rosną dokładnie o nagrodę, `battles == 1`, `tutorialDone == true`;
- trzecie uruchomienie nie ma już czego wznawiać i medale się nie zmieniają (rozliczenie raz);
- jeśli gracz odmówi wznowienia, zrzut znika, medale się nie zmieniają, a „Graj” uruchamia
  samouczek od nowa.

Dzienniki: `app/build/screenshots/fh_<urządzenie>_<język>_os_czasu.txt` i `fh_audit.txt`, zrzuty
`fh_*.png`. To nie są wzorce, tylko podgląd.

## Oś czasu gracza (telefon, pl; jedno przejście)

Czas w bitwie liczę jako czas symulacji przy ×1, a poza bitwą jako czas klatek. Seedy bitew
pochodzą z `System.nanoTime()`, więc liczby różnią się trochę między przebiegami. Pozostałe trzy
przejścia mają ten sam przebieg z odchyleniem ±2 min na bitwę.

| Czas | Zdarzenie | Medale |
|---|---|---|
| 00:00 | Menu, kafel Kampania z napisem „Zniszcz bazę wroga”, bez dymków | 5 |
| 00:00–02:51 | Samouczek, kroki 1, 2, 4, 5, 7, 8, 11, 12, 13 (krok z doktryną i część kroków są pomijane, gdy warunek jest spełniony albo minie limit). Zapowiedź epoki 2 po awansie | 5 |
| 02:59 | Pierwszy dymek po samouczku (po poprawce dokładnie 8 s po „Do boju!”) | 5 |
| 03:36–08:11 | Kolejno dymki: stopień wieży, ulepszenie jednostki, armia pełna, presja oblężnicza | 5 |
| 08:42 | Etap 1 wygrany (8,5 min, 3★): nagroda 71 plus osiągnięcia, łącznie +196 | 201 |
| 08:48 | Mapa: dymek „Wybierz następny etap…”, baner „Nowe osiągnięcie” obok, bez nakładania | 201 |
| 21:29 | Etap 2 wygrany (12,6 min, 2★, +130 z osiągnięciem) | 331 |
| 21:37 | Badania: dymek „Medale zdobywasz nawet za porażki…”, 7 zakupów od 30 medali | 22 |
| 35:04 | Etap 3 wygrany (mgła wojny, 2 dymki) | 88 |
| 48:34 | Etap 4 wygrany | 160 |
| 61:26 | Etap 5 wygrany (pogoda, dymek o ikonie pogody) | 388 |
| 73:40 | Etap 6 wygrany (3★, +101) | 489 |
| 87:09 | Etap 7 wygrany (zapowiedź epoki 3 w trakcie bitwy) | 579 |
| 87:17 | Etap 8: Wódz w HUD i dymek „Od 8. etapu możesz wezwać dowódcę…” | 579 |
| 101:28 / 115:58 | Etap 8: porażka (+39), potem wygrana w drugim podejściu (1★, +134) | 752 |
| 116:14 | Kolejne badania (10 zakupów, dwa z premią za osiągnięcie) | 83 |
| 130:03 | Potyczka wygrana (5 zapowiedzi epok 4–8 w jednej bitwie), +29 | 112 |
| 139:47 | Endless: fala 23, 5 dymków (fala tematyczna, elita, karty, boss, zryw), +75 plus osiągnięcia | 297 |
| 145:47 | Wyzwanie dnia (3.11: bez wydarzenia, modyfikatory mgła, kruche bazy, opancerzony wróg) przegrane w 5,9 min, +19 | 336 |
| 145:53 | „Co nowego” przez Ustawienia → O grze → Historia zmian, Wstecz do menu | 336 |

Medale za etap kampanii (nagroda za wynik bez osiągnięć):

| Etap | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 (porażka) | 8 |
|---|---|---|---|---|---|---|---|---|---|
| Gwiazdki | 3 | 2 | 2 | 2 | 2 | 3 | 2 | 0 | 1 |
| Nagroda | 71 | 60 | 66 | 72 | 78 | 101 | 90 | 39 | 79 |
| Minuty | 8,5 | 12,6 | 13,2 | 13,3 | 12,7 | 12,0 | 13,3 | 14,1 | 14,3 |

## Co działa

- **Pierwsze badanie** jest osiągalne już po 1. bitwie: 196–356 medali (zależnie od osiągnięć),
  a najtańsze badanie kosztuje 30. Ekran Badań sam objaśnia medale.
- **Nagrody** przy tych samych gwiazdkach rosną z etapem (20 + 6·etap + 5·★ + 12·nowe ★).
  Porażka zawsze daje mniej niż wygrana tego samego etapu (etap 8: 39 wobec 79).
- **Dymki** w bitwie pojawiają się pojedynczo, co najmniej 20 s jeden po drugim. Żaden nie
  zasłania aktywnego przycisku HUD na żadnym z 4 przejść. Dymek Wodza stoi nad przyciskiem.
- **Banery powiadomień** czekają z wyświetleniem do końca bitwy. Na mapie, w menu i w Badaniach
  nie nachodzą na Wstecz, tytuł ani dymek.
- **Wstecz** działa na każdym odwiedzonym ekranie. W bitwie otwiera pauzę, na wynikach prowadzi
  dalej. Nie znalazłem ekranu bez wyjścia.
- **Przerwanie samouczka** (zabity proces): wznowienie działa, bitwa rozlicza się raz, a odmowa
  wznowienia przywraca samouczek.

## Znalezione problemy i poprawki

1. **Panel wież zostawał otwarty po kroku „Kup wieżę” w samouczku.** Zasłaniał przyciski
   jednostek aż do kolejnego kroku. Gracz między krokami nie mógł szkolić wojska, a krok
   „Zbrojownia” czekał na pełną armię i po 30 s znikał bez pokazania. Na Foldzie po angielsku
   test utknął w tym miejscu.
   *Poprawka:* zakończenie kroku wieży zamyka panel (`TutorialOverlay.advance`).
2. **Zapowiedź epoki zasłaniała nieaktualny dymek samouczka.** Po awansie w kroku „Awansuj” pod
   przyciemnieniem zapowiedzi nadal wisiał dymek „Awansuj” razem z „Pomiń samouczek”.
   *Poprawka:* samouczek nie jest rysowany, a jego przyciski są ukryte, dopóki trwa zapowiedź
   epoki (`BattleScreen`).
3. **Pierwszy dymek wyskakiwał 1 s po „Do boju!”.** Dymek stopnia wieży pojawiał się zaraz po
   ostatnim oknie samouczka, bo przerwa między dymkami nie liczyła samouczka.
   *Poprawka:* `HintOverlay.holdOff`, czyli 8 s bez dymków po samouczku
   (`BattleScreen.AFTER_TUTORIAL_HINT_DELAY`).
4. **Mapa kampanii nie przewijała się do następnego etapu po „Dalej”.** `Game.setScreen` i
   `Game.frame` wywołują `layout()` dwa razy, a drugi layout wyłączał przewijanie, więc mapa stała
   0,72 ekranu przed celem. Na telefonie po etapie 7 węzeł etapu 8 był ucięty przy prawej
   krawędzi. Nie dało się go dotknąć bez przeciągnięcia, a gracz nie miał wskazówki.
   *Poprawka:* `CampaignScreen.layout` przerywa przewijanie tylko wtedy, gdy zmienił się zakres
   mapy (obrót, Fold).
5. **TalkBack w potyczce: cztery strzałki epok (#10–13) bez opisu.**
   *Poprawka:* opis „Początek: Epoka brązu, wcześniejsza epoka” itd. Nowe teksty
   `fh173_era_earlier` i `fh173_era_later` są w `part173()` w 10 językach.

Wszystkie poprawki pilnuje `FirstHourJourneyTest`. Wzorce PNG (`ScreenshotTest`) się nie
zmieniły, więc `updateScreenshots` nie było potrzebne.

## Obserwacje bez zmian (zatwierdzony HUD, balans, świadome układy)

- Na Foldzie dymek samouczka w kroku wieży stoi tuż pod slotem 1, z miejscem na dłoń, i zasłania
  sloty 2–4. Cel jest odsłonięty, a układ jest świadomy (punktacja `TutorialOverlay` woli panel
  blisko celu). Zostawiam.
- Dymek ulepszenia jednostki przykrywa nieaktywny licznik kolejki (#90) nad przyciskiem piechoty,
  najwyżej na 8 s. Licznik należy do zatwierdzonego dolnego paska.
- Na wynikach etapu 8 na Foldzie napis „Presja oblężnicza ×2,3” na pasku górnym zachodzi pod
  przyciemnieniem na liczby HP baz. To `compactTop`, więc go nie ruszam.
- W Endless AI gracza wybiera karty w kroku symulacji, więc pierwszy wybór kart pokazany w
  interfejsie przypadł dopiero na falę 20. To skutek testu, nie gry (karty sprawdza `NightUxTest`).

## Decyzje dla właściciela

1. **Długość bitew przy profilu „nowy”.** Etapy 2–8 trwają 12–14 min symulacji, bo kończy je
   oblężenie po 8. minucie. Dlatego pierwsza godzina gry to samouczek i etapy 2–5, a nie 1–8. Do
   etapu 8 gracz dochodzi po ok. 1,5 h gry, a potyczka, Endless i wyzwanie dnia wypadają w 2.
   godzinie. Balansu nie zmieniałem. Jeśli pierwsza godzina ma objąć dowódcę z etapu 8, trzeba
   skrócić wczesne etapy (np. wcześniejsze oblężenie lub słabsza baza wroga na etapach 2–4).
2. **Gęstość dymków w pierwszej bitwie.** W pozostałych 6 min etapu 1 po samouczku pojawia się
   5 dymków (stopień wieży, ulepszenie, ranga weterana, pełna armia, oblężenie). Dymek
   weterana pojawia się, bo samouczek kończy się w ostatniej epoce etapu. Do rozważenia: dłuższa
   przerwa (np. 45 s zamiast 20 s) w pierwszej bitwie albo wyłączenie dymku weterana w etapie 1.
3. **Zapowiedzi epok w potyczce.** Nowy gracz w pierwszej potyczce widzi 5 pełnoekranowych
   zapowiedzi (epoki 4–8), mniej więcej co 1,5–2 min. Do rozważenia: zapowiedzi tylko w
   kampanii, albo w potyczce wersja skrócona (baner zamiast okna).
4. **Osiągnięcia jako główne źródło medali na starcie.** Za pierwszą bitwę gracz dostaje 71
   medali nagrody i 125–285 za osiągnięcia. Badania są przez to dostępne bardzo szybko (dobrze),
   ale kwota „+134” na wynikach etapu 8 przy nagrodzie 79 może dziwić, bo różnicy nie widać na
   ekranie wyników.
5. **„Co nowego” dla nowego gracza** jest tylko w Ustawieniach → O grze → Historia zmian (trzy
   poziomy, a w O grze na telefonie trzeba przewinąć). To zgodne z założeniem (pokazujemy je
   automatycznie tylko po aktualizacji), ale jeśli ma być łatwiej dostępne, potrzebne byłoby
   wejście z menu.
6. **Wyzwanie dnia 3.11 nie ma wydarzenia** (`dailyEvent == null`, same modyfikatory). AI gracza
   bez badań przegrywa je w 6 min (kruche bazy i opancerzony wróg). Dla nowego gracza to surowy
   pierwszy kontakt, ale nagroda (+19) i tak jest.
