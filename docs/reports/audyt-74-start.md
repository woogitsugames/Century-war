# Audyt 74 — obszar D: pierwsze 30 minut

Gałąź `claude/audyt-74-start` (od `origin/main` b9d84eed). Przejście startu gry oczami nowego gracza
po D61 (pierwsze wrażenie), D65 (pasek „Następny cel”), D68 (style wroga), D69/D70 (nowe jednostki
i warianty), Rozkazach sztabu i nowym menu.

## Jak mierzone

`FirstHourImpression61Test` (rozbudowany): świeży profil, samouczek palcem, reszta pierwszej bitwy
z AI gracza, wyniki, mapa, menu; **nowe**: gracz idzie za dymkiem/paskiem celu do Badań, kupuje
badanie z celu, gra kolejne etapy kampanii aż do 30. minuty czasu gracza (zrzut mapy, okna etapu,
startu, każdego dymka, zapowiedzi epoki i wyników), na koniec menu i Rozkazy sztabu.
Stałe ziarno, telefon 1600×720 (phone720) i zewnętrzny Fold 904×2316, pl i en — wszystkie 4 przebiegi
bez problemów z kontroli (dwie nakładki naraz, dymek nad HUD, baner nad bitwą itd.).

Zrzuty: `app/build/screenshots/d61/<urządzenie>_<język>/NN_*.png`, oś czasu: `os_czasu.txt` w tym
samym katalogu. Uruchomienie:
`flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --tests 'com.centurywar.screens.FirstHourImpression61Test'`
(ok. 1,5 min na przebieg).

Uwaga: po pierwszej bitwie gra AI gracza (jak BalanceReport), nie człowiek — czasy bitew i wyniki
etapów to sygnał, nie pomiar na ludziach.

## Oś czasu (phone720, pl; Fold i en identyczne co do czasu)

| Czas gracza | Co widzi | Czekanie / uwagi |
|---|---|---|
| 00:00 | Menu: 8 kafli (Zagraj, Oblężenie, Wyzwanie dnia, Potyczka, Badania, Rekordy, Misje, Rozkazy), pasek serii dni, Kodeks, Ustawienia (`01_menu_start`) | wszystko odblokowane od razu |
| 00:00 | Samouczek 1/12 „Wyszkol pałkarza” (`03_tut_krok_0`) | bitwa stoi |
| 00:02 | 2/12 „Złoto i teren” (`04_tut_krok_1`) | |
| 00:39 | **4/12** „Pierwszy pokonany wróg” (`05_tut_krok_3`) — licznik przeskakuje 3/12 | 36 s gry |
| 02:28 | 5/12 „Nowa epoka” (`06_tut_krok_4`) | **107 s bez żadnej wskazówki** (czeka na XP) |
| 02:29 | Zapowiedź Epoki brązu (4 nowe jednostki, `07_epoka_2`) → wybór doktryny (`08_doktryna_wybor`) → **7/12** „Wieże obronne” (`09_tut_krok_6`) | 3 okna w 8 s |
| 03:28 | **11/12** „Ostrzał!” (`11_tut_krok_10`) — przeskok 7→11 (zbrojownia, postawa, zryw pominięte) | 55 s |
| 03:34 | „Gotowe, dowódco!” (`13_tut_krok_12`) | |
| 04:26–08:11 | 4 dymki co ~1–2 min: stopień wieży, ulepszenie, zbrojownia, presja oblężnicza (`15`–`18`) | dobre tempo |
| 10:45 | ZWYCIĘSTWO 3★, +71 za bitwę, **+305 za 6 osiągnięć**, cel „Stać cię! Doktryna elit (120)” (`20_wyniki`) | pierwsza bitwa 10,5 min |
| 10:52 | Mapa: banery osiągnięć, potem dymek „Wybierz kolejny etap…” (`21`, `23_mapa_dymek`) | 7 s banerów |
| 10:57 | Menu: dymek „Masz medale na: Doktryna elit” (pasek celu schowany), po OK pasek celu (`26`, `27`) | |
| 11:00 | Badania: dymek, zakup Doktryny elit (`28`, `29`) | |
| 11:05–19:08 | Etap 2 (Plemiona → Plemiona, bez modyfikatorów): 1 dymek („rangi weteranów”), WYGRANA 8 min, 1★, +43 (+90 za osiągnięcia) (`30`–`33`) | **8 min bez nowej treści** |
| 19:12–27:14 | Etap 3 (Mgła wojny): dymek mgły, starej wieży, Trzymaj; PORAŻKA 8 min, 0★, +26 (`34`–`39`) | |
| 27:17–35:19 | Etap 3 ponownie: PORAŻKA 8 min, „Dalej” otwiera Badania (pomoc) (`40`–`42`) | 16 min porażek z rzędu |
| 35:26 | Menu 30 min: cel „Stać cię na badanie: Skarbiec”, Rozkazy: tylko „Pierwsza linia” otwarta (`43_menu_30min`, `44_rozkazy`) | Rozkazów nikt nie przedstawił |

Bilans 30 minut: 1 samouczek + 2 wygrane etapy + 2 porażki; 556 medali, 4 gwiazdki. Dla porównania
pierwszy przebieg (kupione najtańsze badanie, Skarbiec, zamiast Doktryny elit z celu) wygrał etap 3
i przegrał etap 4.

## Problemy (od najważniejszych)

1. **Za długie bitwy na start, mało nowego w środku.** Pierwsza bitwa 10,5 min, każda kolejna ~8 min;
   w 30 minut gracz rozgrywa tylko 4–5 bitew. Etap 2 to „Plemiona → Plemiona” bez modyfikatorów —
   8 minut tej samej treści co samouczek, nagroda +43 i 1★ (`phone720_pl/33_e2_wyniki.png`,
   `30_e2_okno_etapu.png`). Propozycja (balans, poza zakresem): krótsze etapy 2–3 (niższe HP baz /
   wcześniejsza presja) albo etap 2 już z awansem epoki.
2. **Cel „Doktryna elit” jako pierwszy zakup prowadzi w ścianę.** Pasek celu, dymek menu i wyniki
   trzy razy polecają Doktrynę elit (120). Gracz, który posłucha, przegrywa etap 3 dwa razy z rzędu
   (16 min, 0★, `39_e3_wyniki.png`, `42_e3_wyniki.png`); w przebiegu ze Skarbcem etap 3 wygrany.
   Elity są drogie we wczesnych epokach. Propozycja (NextGoals, poza zakresem drobnych poprawek):
   pierwszy cel badawczy = tanie badanie statystyczne (Kowalstwo/Pancerze 45), Doktryna elit dopiero
   od etapu z elitami wroga albo po 2–3 badaniach.
3. **Samouczek: przeskakujący licznik i długa cisza, potem trzy okna naraz.** Licznik „Samouczek
   x/12” pokazuje 1, 2, 4, 5, 7, 11, 12 — kroki pominięte (strzelec kupiony wcześniej, zbrojownia,
   postawa, zryw) wyglądają jak zgubione (`05_tut_krok_3`, `09_tut_krok_6`, `11_tut_krok_10`).
   Między 4/12 a 5/12 jest 107 s bez wskazówki, a potem w 8 s: krok „Awans”, zapowiedź epoki,
   wybór doktryny i krok wież (`06`–`09`). Propozycja: licznik bez sumy („Krok 4”) albo liczenie
   tylko pokazanych kroków; krok wież (S_TURRET) przed awansem, żeby wypełnić ciszę i rozdzielić okna.
4. **Menu od pierwszego uruchomienia pokazuje wszystko.** 8 kafli + seria dni + Kodeks + Ustawienia,
   zanim gracz zagra pierwszą bitwę (`01_menu_start.png`). Rozkazy sztabu (D62) w ciągu 30 minut nie
   dostają żadnego dymka ani celu — gracz nie wie, czym są (`44_rozkazy.png`), choć „Pierwsza linia”
   (4:00) byłaby krótką, satysfakcjonującą nagrodą między 8-minutowymi etapami. Propozycja: przed
   pierwszą wygraną przyciemnić kafle poza „Zagraj” (bez blokowania), dymek Rozkazów po etapie 3–4
   albo cel „Rozkaz: Pierwsza linia” w NextGoals.
5. **Dymki w bitwie na telefonie 720p leżą na linii walki.** Dymek celu w dolnym HUD stoi tuż nad
   paskiem — dokładnie tam, gdzie idą jednostki — i przez do 8 s zasłania starcie, a niebo nad nim
   jest puste (`15_dymek_23.png`, `17_dymek_1.png`, `37_e3_dymek_11.png`). To świadomy układ
   (komentarz w `HintOverlay.placePanel`), ale na niskim ekranie kosztuje widok bitwy. Propozycja:
   dla celów w dolnym HUD dymek u góry pola z dłuższym łącznikiem, gdy pole ma < 200 dp wysokości.
6. **Dwie strzałki w Badaniach.** Dymek „Medale zdobywasz nawet za porażki” wskazuje licznik medali
   i zasłania nazwy kart „Okrzyk bojowy” i „Handel”, a jednocześnie karta z celu (Doktryna elit)
   ma złotą obwódkę (`28_badania_dymek.png`). Zaraz potem baner osiągnięcia zakrywa nazwy kart
   (`29_badania_po_zakupie.png`).
7. **Nagroda: duży skok i spadek.** Pierwsza bitwa daje +376 (6 osiągnięć naraz, w tym „Precyzyjna
   salwa” +160), kolejne +26…+60 za 8 minut. Na start nagrody jest dużo (dobrze), ale potem gracz ma
   550 medali i nie wie, co z nimi zrobić poza jednym celem. „NOWY REKORD!” przy pierwszym przejściu
   etapu 2 (`33_e2_wyniki.png`) to szum.
8. **Żargon „rangi”.** Na etapie 2 po minucie dymek „W ostatniej epoce nadwyżka XP daje rangi
   weteranów” wskazuje przycisk „Ranga 1” w miejscu „Awans” (`32_e2_dymek_4.png`) — gracz nie wie,
   że etap ma limit epoki; okno etapu mówi tylko „Epoki: Plemiona → Plemiona”.
9. **Nowości D68/D69/D70 we właściwym czasie.** Style wroga (etapy 13/18/21) i warianty (tylko
   potyczka) nie pojawiają się w pierwszych 30 minutach — dobrze. Nowe jednostki epoki (np. „Bestia
   wojenna”) są tylko na planszy zapowiedzi epoki (`07_epoka_2.png`), bez nadmiaru tekstu.
10. **Fold zewnętrzny:** bez uciętych przycisków; jedyne ucięcie to „Nowość: Epoka brązu (Ryd…” w
    oknie etapu 3 (widoczne w przebiegu sprzed poprawki; po niej `foldOuter_pl/34_e3_okno_etapu.png` mieści się) — usunięte poprawką 2.

## Co poprawiłem

1. **Bez dwóch dymków o mgle po 20 s** (`HintOverlay`): na etapie 3 gracz dostawał „Zasady bitwy:
   Mgła wojny — ukryte jednostki”, a 20 s później „Mgła wojny ukrywa część sił wroga. Osłaniaj bazę.”
   Gdy mgła jest jedynym modyfikatorem, „Zasady bitwy” nie startują (mgła ma własny dymek). Dymek
   zasad jest jednorazowy, więc zostaje na etap 4 — wcześniej „Szybkie wojska” nie były w bitwie
   objaśniane wcale.
2. **Epoka z samouczka nie jest „Nowością” etapu 3** (`StageNovelty`): samouczek dopuszcza epokę
   wyżej (`Campaign.config(tutorial = true)`), gracz i wróg są w Epoce brązu już w pierwszej bitwie,
   a okno etapu 3 ogłaszało „Nowość: Epoka brązu (Rydwan), Mgła wojny” (na Foldzie ucięte). Teraz
   „Nowość: Mgła wojny” (`phone720_pl/34_e3_okno_etapu.png`). `StageNoveltyTest` dostosowany.
3. **Test podróży do 30. minuty** (`FirstHourImpression61Test`, opis wyżej).

Bez nowych tekstów (part222 niepotrzebna), bez zmian `sim/src/main` i PROTOCOL.

Testy uruchomione (filtrowane): `FirstHourImpression61Test` (4), `StageNoveltyTest`,
`HintOverlayTest`, `Tura8UiTest` — zielone.

## Wzorce zrzutów regresji, które się zmienią (nie aktualizowane)

- `campaign_info` (okno etapu 3) w `ScreenshotTestMenus` (phone720 i pozostałe urządzenia tej klasy)
  oraz w `ScreenshotTestFold` — linia „Nowość:” bez „Epoka brązu (Rydwan)”.
- Ewentualnie zrzut okna etapu 3 w `Tura8UiTest` (wybór `Campaign.stages[2]`), jeśli porównuje wzorzec.
- Dymki `hint_modifiers` / `hint_fog` w `ScreenshotTestBattle` i `Tura8UiTest` wołają `activate`
  bezpośrednio — bez zmian.

## Kluczowe zrzuty

`app/build/screenshots/d61/phone720_pl/`: `01_menu_start.png`, `05_tut_krok_3.png`,
`06_tut_krok_4.png`–`09_tut_krok_6.png`, `15_dymek_23.png`, `20_wyniki.png`, `28_badania_dymek.png`,
`33_e2_wyniki.png`, `34_e3_okno_etapu.png`, `42_e3_wyniki.png`, `44_rozkazy.png`;
`app/build/screenshots/d61/foldOuter_pl/34_e3_okno_etapu.png`.
