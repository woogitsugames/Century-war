# R11 — backlog z researchu (runda 11, 4.11.2026)

Baza: `integ/tura10` (fbabcf0, po D7 odświeżonym, powitaniu po przerwie, sygnałach w bitwie,
przybliżeniu na zewnętrznym Foldzie i utraconej bitwie po aktualizacji). Dokument nie zmienia kodu gry.
Źródła: przegląd kodu (`app/src/main`, `sim/src/main`), `docs/BALANCE.md` (pomiar dnia 1),
`docs/reports/` (dzień 4, dzień 5 TalkBack, foldzoom), zrzuty `docs/store/assets/D7/`,
`docs/reports/foldzoom/`, pliki muzyki w `app/src/main/assets/music/` (długości przez `ffprobe`)
i dokumentacja raportu przedpremierowego Google Play i Firebase Test Lab (linki przy pozycji 4).

**Nie dubluje:** R1/R2/R6/R10 (zrobione), samouczka po zabiciu procesu (`claude/dzien-20-samouczek`,
w toku), osiągnięć po D6 (`claude/osiagniecia-po-d6`, zewnętrzna sesja), R5 (serwer) ani Game Stats
(czekają na właściciela). Nie rusza układu `compactTop` ani celowania ostrzału.

Rozmiar: S ≤ pół dnia jednego programisty, M ≈ 1 dzień, L > 1 dzień. Osiem pozycji to razem
ok. 5–6 dni pracy jednej osoby, czyli ok. 2–3 dni dla dwóch. TOP 1–4 mają gotowe zlecenia.
Teksty: każde zlecenie ma własną część od `part196` wzwyż.

## Najważniejsze wnioski

- **TalkBack kończy się na menu.** `GameAccessibility.refresh` daje osobne kontrolki tylko
  dla Menu, Ustawień, Dostępności, Podpowiedzi, O grze i bitwy. Każdy inny ekran (mapa
  kampanii, tryby, Badania, Misje, Potyczka, Kodeks, Osiągnięcia, Statystyki, Rankingi,
  Powtórki, Co nowego, Sztandary) jest jednym węzłem „Ekran gry. Ten ekran wymaga sterowania
  dotykowego” (`a164_other`). Osoba z czytnikiem po samouczku **nie wybierze etapu**, więc
  TalkBack w HUD bitwy (D5) i tempo ×½ (R2-03) są dla niej nieosiągalne. Gotowy opis korony
  na mapie (`CampaignScreen.accessibilityLabel`, R10-02) nigdy nie trafia do czytnika. Ten sam
  brak drzewa ogranicza robota raportu przedpremierowego Google Play: chodzi on po drzewie
  dostępności, więc za menu nie dotrze.
- **Klif po pierwszej godzinie.** Dzień 1 skrócił etapy 2–7 do 7,6–7,8 min. Etap 8 wraca do
  13,4 min przy 66% wygranych, a oczekiwany czas przejścia to 19,3 min (`docs/BALANCE.md`,
  „Pomiar przed/po”). Etapy 8–18 mają mediany 12–14 min. Tester, który po godzinie dostał
  dowódcę, w drugiej godzinie przejdzie ok. 4 etapy, a każda bitwa nagle trwa dwa razy dłużej.
- **Endless po fali 30 nie daje celów w trakcie biegu.** Progi Oblężenia tygodnia (10/20/30),
  rekord fali (`Profile.bestWave`) i medale gracz widzi dopiero na wynikach
  (`BattleScreen.settleRewards`). W bitwie nie ma „Brąz zdobyty”, „Nowy rekord: fala 38” ani
  „do rekordu brakuje 4 fal”. Mediana porażki to fala 54, więc ostatnie 20–25 minut biegu nie
  ma żadnego kamienia milowego poza bossami co 10 fal.
- **Muzyka szybko się powtarza.** Pętle bojowe trwają ok. 51–53 s (`era2_battle.ogg` 53,3 s,
  `era6_battle.ogg` 51,5 s), a menu 80 s. Etap 8+ trwa ok. 13 min, a jedna epoka obejmuje 4 etapy.
  Gracz słyszy więc tę samą minutę ok. 15 razy w bitwie i ok. 60 razy w epoce. Mikser
  (`RecordedMixer`) nie ma przerw ani rotacji. W opiniach gier mobilnych to częsta skarga
  „wyłączyłem muzykę”.
- **Raport przedpremierowy Play ma niską wartość bez pętli testowej.** Pomoc Play mówi, że dla
  gier na własnym płótnie trzeba dostarczyć pętlę gry (game loop). Bez niej robot klika losowo
  po `SurfaceView`. Z pętlą Google za darmo uruchamia bitwę na kilku prawdziwych telefonach,
  także tanich, i zbiera awarie, ANR, klatki i zrzuty. Gra ma już wszystko, czego pętla
  potrzebuje: `Battle.playerAi` (AI za gracza), raport testera i `FrameMetrics`.
- **Zapis, start, wydajność i grafiki sklepu są w dobrym stanie** (audyty 154/156, R2-05, R10-01,
  R10-04, D7). Nowych luk nie znalazłem. Na zrzutach D7 i foldzoom nic nie wymaga poprawki
  poza znanymi drobiazgami z dnia 4 (pozycja 8).

---

## TOP 8

### 1. TalkBack i robot Play na ekranach poza bitwą — [R11-01](../tasks/R11-01-a11y-beyond-menu.md)
- **Cel:** osobne kontrolki z opisem dla mapy kampanii (węzły etapów, panel etapu, poziom
  trudności, start), ekranu trybu (Endless, wyzwanie dnia, Oblężenie tygodnia), Badań (nazwa,
  poziom, koszt, opis), Misji, Potyczki, Osiągnięć, Co nowego i Sztandarów. Dochodzą akcje
  przewijania (`ACTION_SCROLL_FORWARD`/`BACKWARD`) dla list i mapy. Kodeks i Statystyki w drugim
  kroku: zakładki i tekst czytany jako węzły informacyjne.
- **Dla gracza:** osoba z czytnikiem ekranu przechodzi od menu do bitwy i z powrotem:
  wybiera etap, kupuje badania, odbiera misje. Robot raportu przedpremierowego Google Play
  dociera dalej niż menu, więc zgłasza błędy z większej części gry.
- **Pliki:** `engine/GameAccessibility.kt` (`refresh`, `eligible`, `matches`, nowe akcje
  przewijania), `engine/Screen.kt` (`accessibilityScroll`, `accessibilityInfo`),
  `screens/CampaignScreen.kt`, `ModeScreen.kt`, `ResearchScreen.kt`, `MissionsScreen.kt`,
  `SkirmishSetupScreen.kt`, `AchievementsScreen.kt`, `WhatsNewScreen.kt`, `BannerScreen.kt`,
  (krok 2) `CodexScreen.kt`, `StatsScreen.kt`; `L.kt` `part196`.
- **Rozmiar:** M–L (krok 1 M, krok 2 S–M). **Ryzyko:** niskie–średnie. Bez TalkBack nic się
  nie zmienia (`refreshIfEnabled`), ale trzeba pilnować braku alokacji co klatkę przy
  włączonym czytniku. Mapa przewija się w poziomie i w pionie.
- **Zależności:** brak. Przydaje się pozycji 4 (robot).

### 2. Druga godzina bez klifu: krótsze etapy 8–12 — [R11-02](../tasks/R11-02-second-hour-pacing.md)
- **Cel:** w etapach 8–12 (indeksy 7–11) presja oblężnicza rusza po ok. 5 min zamiast 8,
  a jej przyrost to ok. 50%/min. Etapy 2–7 mają 4 min i 80%, reszta zostaje na 8 min i 20%.
  Cel: mediana 9–10 min, wygrane ±5 pp względem dziś. Ten sam mechanizm co w dniu 1
  (`Campaign.siegeStart`/`siegeRamp`), z pomiarem `BalanceReport` na 100 seedach.
- **Dla gracza:** po pierwszej godzinie bitwy nie wydłużają się nagle dwukrotnie. W drugiej
  godzinie tester przejdzie ok. 6 etapów zamiast 4 i zobaczy dowódcę w kilku bitwach.
- **Pliki:** `sim/.../Modes.kt` (`Campaign`), `sim/.../SimVersion.kt` (PROTOCOL 0.8.6 → 0.8.7),
  złote powtórki, `docs/BALANCE.md`, `FirstHourJourney*` (opcjonalnie drugie przejście).
- **Rozmiar:** M. **Ryzyko:** średnie. Zmienia wynik bitew, więc trzeba podbić PROTOCOL
  i wygenerować złote powtórki. Za krótka bitwa osłabia dowódcę (etap 8 go wprowadza).
- **Zależności:** decyzja właściciela (zakres 8–12 czy 8–18). Najlepiej przed pierwszym AAB
  dla testerów, bo potem każda zmiana PROTOCOL unieważnia ich powtórki rankingowe.

### 3. Kamienie milowe w trakcie Endless i Oblężenia tygodnia — [R11-03](../tasks/R11-03-endless-milestones.md)
- **Cel:** w bitwie Endless jednorazowa wstęga pod paskiem górnym, sygnał i komunikat TalkBack,
  gdy gracz przekroczy próg tygodnia (Brąz 10, Srebro 20, Złoto 30, z medalami) i gdy pobije
  swój rekord fali. Od fali `rekord − 5` wiersz „następna fala” pokazuje „Do rekordu: 3 fale”.
  W menu pauzy: „Rekord: fala 41 · Brąz ✓ Srebro ✓ Złoto 30”.
- **Dla gracza:** długi bieg ma widoczne etapy i powód, żeby zagrać jeszcze 5 fal. Wie,
  że Złoto już ma, zanim przegra.
- **Pliki:** nowy `screens/EndlessMilestones.kt` (czysta logika), `screens/BattleScreen.kt`,
  `screens/BattleTopBar.kt` (tylko wiersz pod paskiem, w miejscu „Następna fala”; sam pasek bez zmian),
  `screens/BattleSfx.kt`, `screens/BattleAccessibility.kt` / `engine/GameAccessibility.kt`
  (komunikat), `L.kt` `part198`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Tylko UI, bez `:sim` i PROTOCOL. Wstęga nie może
  kolidować z paskiem bossa i dymkami.
- **Zależności:** brak (korzysta z `BattleAlerts`/`BattleSfx` z R10-03).

### 4. Pętla testowa gry dla raportu przedpremierowego Play i Test Lab — [R11-04](../tasks/R11-04-game-loop-prelaunch.md)
- **Cel:** obsługa intencji `com.google.intent.action.TEST_LOOP` (standard Firebase Test Lab,
  z którego korzysta raport przedpremierowy dla gier). Scenariusze: 1) zimny start i menu,
  2) bitwa etapu 8 AI kontra AI ×3 przez 3 min, 3) Endless od fali 20 przez 3 min,
  4) przejście po ekranach. Wynik w JSON do pliku z intencji: p50/p95 klatki, stopień jakości,
  pamięć, czas startu, błędy. Profil gracza osobny, w pamięci, nic nie trafia do PGS.
- **Dla gracza:** zanim tester dostanie wersję, Google uruchomi bitwę na kilku prawdziwych
  telefonach, także tanich. Awarie i przycięcia znajdziemy przed testerami, nie po ich ocenie.
- **Pliki:** `AndroidManifest.xml` (filtr intencji), nowy `testloop/GameLoopRunner.kt`,
  `MainActivity.kt` (`onCreate`/`onNewIntent`), `engine/Game.kt` (profil tymczasowy),
  `meta/Profile.kt` (tryb bez zapisu), `docs/RELEASE.md` (ustawienia Play Console),
  `docs/PERFORMANCE.md`. Teksty: raczej brak (`part199` zarezerwowane).
- **Rozmiar:** M. **Ryzyko:** niskie–średnie. Pętla nie może dotknąć prawdziwego profilu,
  rankingów ani powiadomień. Uruchomić ją może każda aplikacja na telefonie, więc scenariusz nie
  może niczego trwale zapisać ani wysłać.
- **Zależności:** brak. Robot po R11-01 da dodatkowo lepsze pokrycie ekranów.

### 5. Muzyka bez zmęczenia: oddech i rotacja pętli
- **Cel:** `RecordedMixer` po 2–3 okrążeniach pętli (ok. 2,5 min) przez 20–40 s ścisza ją do
  ok. −12 dB albo do ciszy i wraca miękko. Przy spokojnej fazie bitwy może wziąć spokojną wersję
  sąsiedniej epoki. W menu po 2 okrążeniach przerwa 30 s. Czas liczony w czasie gry, bez
  alokacji. Głośność efektów i przerywniki bez zmian.
- **Dla gracza:** 13-minutowa bitwa nie jest jedną minutą muzyki powtórzoną 15 razy.
  Mniej graczy wyłącza muzykę, a cisza między frazami daje miejsce sygnałom R10-03.
- **Pliki:** `engine/RecordedMixer.kt`, `engine/RecordedMusic.kt`, `engine/Music.kt`,
  `RecordedMusicTest`, `docs/music/README.md`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Przerwa nie może wypaść w chwili przerywnika wyniku
  ani przenikania epok.
- **Zależności:** decyzja właściciela (cisza czy ściszenie, czy wolno pożyczać utwory sąsiednich epok).

### 6. „Nowość na tym etapie” na mapie i w panelu etapu
- **Cel:** panel etapu (`CampaignScreen.drawInfo`) dostaje jeden wiersz „Nowość”, gdy etap
  pierwszy raz wprowadza coś nowego: nową epokę wroga (z nazwą nowej jednostki), modyfikator albo
  pogodę widzianą pierwszy raz, dowódcę (etap 8). Na mapie przy takim węźle jest mała iskra,
  dopóki gracz go nie wygra. Wyliczane statycznie z `Campaign.stages` (pierwsze wystąpienie),
  bez zmian `:sim`.
- **Dla gracza:** w środku kampanii (etapy 10–25) widzi, że co 1–3 etapy czeka coś nowego, i wie,
  na co się przygotować. Dziś modyfikatory są tylko chipami bez informacji, że to nowość.
- **Pliki:** nowy `meta/StageNovelty.kt` (czysta logika + test), `screens/CampaignScreen.kt`
  (`drawInfo`, `drawNode`, `accessibilityLabel`), `L.kt` `part200`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Niski panel na telefonie 720p ma mało miejsca, więc
  wiersz musi dzielić linię z gwiazdkami jak korona R10-02.
- **Zależności:** po R11-01, jeśli opis ma trafić do TalkBack (wspólny `accessibilityLabel`).

### 7. Krótka ankieta testera w grze (lokalnie, do raportu testera)
- **Cel:** jednorazowo, po pierwszej wygranej na etapie 8 albo w 3. dniu gry, w menu pojawia się
  nieblokujący baner „Jak ci się gra? 1–5”. Po ocenie jest jedno pytanie z 4 odpowiedzi
  („za trudno / za długie bitwy / niejasne / nic, jest dobrze”). Wynik zapisuje się lokalnie
  w `PlaytestLog` i trafia do „Raportu dla testera” (R2-04). Nic nie jest wysyłane
  automatycznie, więc Data safety bez zmian.
- **Dla gracza (testera):** jednym dotknięciem mówi, co mu przeszkadza. My w raporcie mamy
  porównywalne odpowiedzi zamiast samych liczników.
- **Pliki:** `meta/PlaytestLog.kt`, `crash/PlaytestReportText.kt`, `screens/MenuScreen.kt`,
  `meta/Profile.kt` (flaga „zapytano”), `L.kt` `part201`.
- **Rozmiar:** S. **Ryzyko:** niskie. Najwyżej raz na profil i nigdy nad bitwą ani wynikami
  (zasada powiadomień).
- **Zależności:** decyzja właściciela (czy pytać graczy po premierze, czy tylko w wersji testowej).

### 8. Drobne napisy na zewnętrznym Foldzie poza paskiem bitwy
- **Cel:** domknąć znane drobiazgi z dnia 4 (`docs/reports/day4-review.md`, „Do decyzji” p. 3):
  przyciski menu pauzy z napisem 8 dp, „Odstęp 2 / 3” 7 dp w porównaniu jednostek Kodeksu
  (po niemiecku nachodzi na „0.9 s / 0.7 s”), zakładki Kodeksu częściowo schowane bez znaku
  przewijania. Minimum 10 sp i znak przewijania (`ScrollCue`) jak w innych listach.
- **Dla gracza:** na wąskim ekranie (zewnętrzny Fold, część tanich telefonów 20:9) czyta pauzę
  i porównanie jednostek bez mrużenia oczu.
- **Pliki:** `screens/BattleScreen.kt` (tylko układ menu pauzy), `screens/CodexComparePanel.kt`,
  `screens/CodexScreen.kt`, testy `PrereleasePolish*`, `Day2UxTest`.
- **Rozmiar:** S. **Ryzyko:** niskie. Pasek `compactTop` bez zmian.
- **Zależności:** brak.

---

## Kolejność dla 1–2 programistów (≈ 2 dni)

| Dzień | Programista A | Programista B |
|---|---|---|
| 1 | R11-01 krok 1 (M) | R11-03 (S–M), potem pozycja 8 (S) |
| 2 | R11-01 krok 2 (S–M), potem pozycja 6 (S–M) | R11-04 (M) |
| po decyzjach | R11-02 (M, przed AAB dla testerów) | pozycje 5 i 7 (S–M, S) |

Konflikty: `L.kt` (własne części `part196`–`part201`; po scaleniu liczba `putAll(part` =
liczba `private fun part`), `CampaignScreen.kt` (R11-01 drzewo, pozycja 6 panel i węzły;
najpierw R11-01), `GameAccessibility.kt` (R11-01 i R11-03 komunikat; R11-03 dopisuje tylko
zdarzenie), `BattleScreen.kt` (R11-03 wstęga i pauza, pozycja 8 układ pauzy; najpierw R11-03),
`MainActivity.kt` (R11-04, inne miejsca niż samouczek z `claude/dzien-20-samouczek`),
`Profile.kt` (R11-04 tryb bez zapisu, pozycja 7 flaga). R11-02 zmienia `:sim`, więc scalać go
osobno, z regeneracją złotych powtórek. Najbezpieczniej: R11-03 → R11-01 → R11-04 → R11-02 →
reszta.

## Decyzje dla właściciela

1. **Druga godzina (R11-02): które etapy skrócić?** (a) etapy 8–12, ok. 9–10 min
   (rekomendacja: zamyka klif po pierwszej godzinie, reszta kampanii bez zmian); (b) 8–18,
   wszystkie ok. 10 min (krótsza cała pierwsza połowa kampanii, większy pomiar balansu);
   (c) bez zmian (etapy od 8 nadal ok. 13 min).
2. **Muzyka (pozycja 5): jak przerwać powtarzanie?** (a) po ok. 2,5 min ściszenie do tła na
   20–40 s i powrót (rekomendacja: najmniejsza zmiana brzmienia); (b) pełna cisza na 20–40 s
   (jak w wielu grach strategicznych); (c) rotacja z utworami sąsiednich epok, bez przerw.
3. **Ankieta testera (pozycja 7): komu ją pokazywać?** (a) tylko w wersjach testowych, przed
   premierą (rekomendacja); (b) też po premierze, z linkiem do opinii w sklepie dla ocen 4–5;
   (c) nie robić, wystarczą liczniki raportu testera.
4. **Pętla testowa (R11-04): czy włączyć ją w Play Console?** (a) tak, w raporcie
   przedpremierowym dla każdego wydania testowego (rekomendacja: bez kosztów, kilka
   prawdziwych telefonów); (b) tylko ręcznie przed AAB dla testerów zamkniętych;
   (c) nie (wtedy pozycja 4 spada z TOP).
5. **TalkBack (R11-01): czy Kodeks i Statystyki muszą wejść przed testami zamkniętymi?**
   (a) nie, najpierw ścieżka do bitwy: kampania, tryby, Badania, Misje, Potyczka
   (rekomendacja); (b) tak, wszystkie ekrany w jednym zleceniu (ok. 1 dzień więcej).

## Źródła zewnętrzne

- [Pomoc Play Console: raport przedpremierowy, gry i pętla gry](https://support.google.com/googleplay/android-developer/answer/9842757)
- [Firebase Test Lab: testy Game Loop, intencja `com.google.intent.action.TEST_LOOP`](https://firebase.google.com/docs/test-lab/android/game-loop)
- [Google Play: rozszerzenie testów przedpremierowych (robot na płótnie OpenGL klika losowo)](https://medium.com/googleplaydev/google-play-expands-its-pre-launch-testing-capabilities-9b51a23077cf)
