# R10 — backlog z researchu (runda 10, 4.11.2026)

Baza: `integ/tura10` (3e8436a, po D7). Dokument nie zmienia kodu gry. Źródła: przegląd kodu
(`app/src/main`, `sim/src/main`), zrzuty w `app/src/test/resources/screens/`,
`docs/store/assets/D7/` i ostatnie zrzuty z testów (`app/build/screenshots/` w głównym
katalogu: `day2_*`, `cj_foldOuter_*`, `ux2_*`, dzienniki `pre10_audit_*.txt`), raporty
`docs/reports/` (noc 5, dzień 4, R6-02) i backlogi R1/R6.

**Nie dubluje:** R6-01/R9 (osiągnięcia pierwszej godziny i ikony PGS, `claude/dzien-11-osiagniecia`),
D6 (karty Endless, zestawy Oblężenia, wydarzenia dnia, `claude/d6-tresc`), R5 (serwer), Game Stats,
szybkiej bitwy (R3, czeka na właściciela), zrobionych R2-01…R2-05, R6-02, D5, D7.
Nie rusza układu `compactTop` ani celowania ostrzału.

Rozmiar: S ≤ pół dnia jednego programisty, M ≈ 1 dzień, L > 1 dzień. Osiem pozycji razem to
ok. 4–5 dni pracy jednej osoby, czyli ok. 2 dni dla dwóch. TOP 1–4 mają gotowe zlecenia.

## Najważniejsze wnioski

- **Zapis i wznawianie są solidne** (audyt 156, A156-10, R6-02). Została jedna realna luka:
  zrzut bitwy z poprzedniej wersji, po aktualizacji z inną wersją formatu albo innym odciskiem
  balansu (`BattleSnapshot.defsFingerprint`), jest **po cichu** odrzucany. W testach
  zamkniętych aktualizacje przychodzą co kilka dni, więc tester w trakcie Endless albo wyzwania
  dnia traci bitwę bez słowa. W przypadku wyzwania dnia traci też jedyną próbę rankingową dnia
  (`lastRankedDaily` jest ustawiane już przy starcie, `ModeScreen.kt:503`).
- **Po kampanii** gra ma Endless, Oblężenie tygodnia, wyzwanie dnia, potyczkę i osiągnięcia.
  Ma też poziom Trudny, ale **jego postępu nigdzie nie widać**: `Profile.hardStageWins` czyta
  tylko osiągnięcie `campaign_hard` (36 etapów). Mapa, panel etapu, kafel menu i „Następny
  cel” nie pokazują, które etapy gracz wygrał na Trudnym. To gotowy, tani cel długoterminowy.
- **Dźwięk:** efekty są bogate (56 syntezowanych), ale ważne chwile mają tylko komunikaty
  TalkBack (`GameAccessibility`, klucze `a178_*`): baza 50% i 25%, wróg w nowej epoce, ostrzał
  gotowy. Gracz bez czytnika tego nie słyszy, a uderzenie w bazę (`Sound.THUMP`) cichnie, gdy
  kamera patrzy na front (`BattleSfx`, głośność od odległości). Na ekranie nie ma też znaku
  „twoja baza obrywa” poza kadrem.
- **Zewnętrzny Fold w pionie (904×2316):** pole bitwy zajmuje pas o szerokości 880 jednostek
  świata. Ok. 60% wysokości ekranu to niebo (`cj_foldOuter_*_3_alive.png`,
  `day2_day4_foldOuter_siege_clock_pl.png`). Z `BattleRenderer.layout` wynika
  `k ≈ 904/880 ≈ 1,03 px` na jednostkę przy gęstości 2,7, czyli ok. 0,38 dp. Na telefonie
  720p jest to ok. 0,64 dp. Żołnierze są więc fizycznie ok. 40% mniejsi niż na zwykłym
  telefonie. To szacunek z kodu, do sprawdzenia na urządzeniu.
- **Płynność i rozmiar APK są w porządku.** Muzyka to 20 MB OGG przy `-q:a 2.5` (ok. 80 kb/s),
  a APK ma ok. 25 MB. Dalsze cięcie nie jest warte ryzyka dla jakości dźwięku. Z R6 zostały
  drobne punkty L1–L4 i L6. Gra nie korzysta też z trybu gry Androida (`GameManager`),
  o czym pisze R1 §4.2.
- **Teksty:** dzienniki `pre10_audit_*` mają same znane, zaakceptowane przypadki: listwa zakładek
  Kodeksu przewija się, a „Odstęp 2 / 3” w porównaniu ma 7 dp na zewnętrznym Foldzie. Nowych
  błędów językowych nie znalazłem. Kadr sklepu `05-results` (D7) pokazuje jednak niewiarygodne
  liczby (pozycja 8).

---

## TOP 8

### 1. Bitwa przerwana aktualizacją: uczciwy komunikat i zwrot próby — [R10-01](../tasks/R10-01-update-lost-battle.md)
- **Cel:** przy każdym zapisie zrzutu zapisywać obok mały, stabilny plik opisu bitwy
  (`battle.meta`: tryb, etap, fala, czas, id bitwy, dzień wyzwania, czy rankingowa). Gdy po
  aktualizacji zrzutu nie da się wczytać, menu pokazuje komunikat. Rankingowe wyzwanie dnia
  z tego samego dnia odzyskuje próbę, a gracz dostaje medale jak za kapitulację w tej chwili
  (raz, przez `settleBattle`).
- **Dla gracza:** po aktualizacji gry nie znika mu bez słowa godzina Endless ani dzisiejsze
  wyzwanie. Wie, co się stało, i nie traci jedynej próby dnia.
- **Pliki:** `sim/.../BattleSnapshot.kt` (`SnapshotStore`: zapis/odczyt `battle.meta`,
  `rejected()`), `screens/BattleScreen.kt` (`snapshotBytes` + meta przy `saveAsync`),
  `MainActivity.kt` (start: `snapshots.loadBattle` → `game.lostBattle`), `screens/MenuScreen.kt`
  (`g.showNotice`), `meta/Profile.kt` (`settleBattle`, `lastRankedDaily`), `engine/L.kt` `part188`.
- **Rozmiar:** M. **Ryzyko:** niskie–średnie. Zmienia `:sim`, ale nie wynik bitwy, więc nie
  trzeba podbijać PROTOCOL. Konflikt z D6 jest możliwy w `BattleSnapshot.kt` (D6 podbija
  `VERSION`), ale w innych funkcjach.
- **Zależności:** najlepiej po scaleniu D6. Wtedy test „zrzut v19 po aktualizacji do v20” ma
  realny przypadek.

### 2. Trudna kampania widoczna na mapie i jako cel po kampanii — [R10-02](../tasks/R10-02-hard-campaign-visible.md)
- **Cel:** korona przy węźle etapu wygranego na Trudnym, licznik „Trudny X/4” w nagłówku epoki
  (gdy ma wygraną na Trudnym), wiersz w panelu etapu, licznik w kaflu Kampanii po zdobyciu
  wszystkich gwiazdek. Do tego nowy rodzaj „Następnego celu” `HARD_STAGE` po ukończeniu
  kampanii: „Trudny: wygraj etap 7”.
- **Dla gracza:** po przejściu kampanii widzi następną drabinkę (36 koron) i wie, które etapy
  jeszcze zostały. Dziś postęp na Trudnym widać tylko w pasku jednego osiągnięcia.
- **Pliki:** `screens/CampaignScreen.kt` (`drawNode`, `drawRegionHeading`, `drawInfo`),
  `screens/MenuScreen.kt` (`campaign.sub`, `subKey`), `meta/Profile.kt` (`hardWon(i)`),
  `meta/NextGoal.kt`, `screens/BattleResultsPanel.kt` / `BattleScreen` (akcja celu), `L.kt` `part189`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Tylko UI, bez zmian `:sim` i balansu.
- **Zależności:** brak. Dotyka `NextGoal.kt` po R2-01 (zrobione).

### 3. Ostrzeżenia w bitwie: dźwięk i znak przy krawędzi — [R10-03](../tasks/R10-03-battle-alerts.md)
- **Cel:** krótkie syntezowane sygnały dla 4 zdarzeń: baza gracza spada poniżej 50% i 25%,
  wróg wchodzi w nową epokę, Ostrzał lub Zryw znów gotowy, początek presji oblężniczej. Gdy
  baza obrywa poza kadrem, przy lewej krawędzi pola pojawia się czerwony znak ze strzałką
  (dotknięcie przesuwa kamerę do bazy). Jedna czysta klasa wykrywa zdarzenia, bez alokacji.
- **Dla gracza:** nie przegrywa „po cichu”, kiedy patrzy na front. Słyszy, że może znowu użyć
  Ostrzału. To ważne na małym ekranie, gdzie baza jest zwykle poza kadrem.
- **Pliki:** nowy `screens/BattleAlerts.kt`, `screens/BattleSfx.kt`, `engine/SoundSynth.kt`
  (+3 dźwięki, `VERSION` 3→4, `COUNT`), `engine/Sound.kt`, `screens/BattleScreen.kt`
  (rysowanie znaku nad polem, dotyk), `L.kt` `part190` (opis TalkBack znaku).
- **Rozmiar:** M. **Ryzyko:** niskie–średnie. Sygnały nie mogą zagłuszać muzyki i efektów
  (limity odstępów), a znak nie może kolidować z dymkami i samouczkiem.
- **Zależności:** brak. Nie zmienia komunikatów TalkBack (`GameAccessibility` bez zmian).

### 4. Domknięcie przeglądu R6 i tryb gry Androida — [R10-04](../tasks/R10-04-r6-leftovers-game-mode.md)
- **Cel:** R6-L1 (odczyt śladu ANR nie blokuje startu), L2 (TalkBack włączony skrótem w trakcie
  gry od razu zmienia tempo i drzewo), L3 (automatyczne „mniej wstrząsów i błysków” wraca, gdy
  gracz włączy animacje w systemie), L4 (po `onTrimMemory` mapa kampanii nagrywa tylko widoczne
  epoki), L6 (kliknięcie z czytnika nigdy nie przepada). Do tego `GameManager`: w trybie
  oszczędzania baterii limit 30 FPS i niższa jakość. Poziom trybu gry jest wpisany w raport testera.
- **Dla gracza:** szybszy start po zawieszeniu, mniej grzania i dłuższa bateria w trybie
  oszczędzania, TalkBack działa od razu po włączeniu, mapa kampanii nie przycina się po powrocie.
- **Pliki:** `MainActivity.kt`, `crash/CrashReports.kt` / `ExitReasons.kt`, `engine/Game.kt`,
  `meta/Profile.kt` (flaga „ustawione automatycznie”), `screens/CampaignScreen.kt`,
  `engine/GameAccessibility.kt` (`tap`), nowy `res/xml/game_mode_config.xml`,
  `AndroidManifest.xml`, `crash/PlaytestReportText.kt`. Teksty: `part191` tylko w razie potrzeby.
- **Rozmiar:** S–M. **Ryzyko:** niskie.
- **Zależności:** brak. Wspólny plik `MainActivity.kt` z R10-01 (różne miejsca).

### 5. Przybliżenie pola bitwy na zewnętrznym Foldzie w pionie (decyzja właściciela)
- **Cel:** w `BattleRenderer.layout` dla ekranów wysokich i wąskich (`w / h < 0,6`) widoczna
  szerokość świata ok. 600 zamiast 880 jednostek (dziś `worldW = 880`). Nadmiar wysokości idzie
  do ziemi, jak teraz. Automatyczna kamera i minimapa bez zmian. Celowanie ostrzału liczy się
  przez `sx()`/kamerę, więc mechanika zostaje.
- **Dla gracza:** na zewnętrznym ekranie Folda żołnierze, wieże i paski HP są ok. 1,5 raza
  większe. Mniej pustego nieba, łatwiej rozpoznać jednostki.
- **Pliki:** `game/BattleRenderer.kt` (`layout`, `clampCam`), `screens/BattleScreen.kt`
  (kamera startowa), testy zrzutów `cj_foldOuter_*`, `Day2UxTest`, `BarrageAiming`.
- **Rozmiar:** M. **Ryzyko:** średnie. Widać mniejszą część pola, więc trzeba sprawdzić
  automatyczną kamerę, ostrzał przy krawędzi i dymki samouczka wskazujące jednostki.
- **Zależności:** decyzja właściciela. Zmienia wygląd bitwy na urządzeniu, którego używa
  (pasek górny `compactTop` zostaje bez zmian).

### 6. Samouczek wraca po przerwaniu (zabity proces)
- **Cel:** dziś „Wznów” otwiera bitwę samouczka bez nakładki (`BattleScreen.tut` jest `null`,
  gdy `restored != null`), co potwierdza test `tutorialInterruptedResumedSettlesOnce`. Zapisać
  numer ukończonego kroku w profilu (`tutorialStep`). Przy wznowieniu bitwy z
  `cfg.tutorial && !tutorialDone` tworzyć `TutorialOverlay` od następnego kroku. Kroki czekają
  na warunek bitwy, a wykonane warunki przejdą same.
- **Dla gracza:** nowy gracz, któremu telefon zamknął grę w trakcie nauki (rozmowa, tani
  telefon), nie zostaje w środku bitwy bez instrukcji.
- **Pliki:** `screens/TutorialOverlay.kt` (start od kroku), `screens/BattleScreen.kt` (`tut`),
  `meta/Profile.kt`, `FirstHourJourneyTest`.
- **Rozmiar:** S. **Ryzyko:** niskie. Okno to ok. 3 minuty samouczka, ale dotyczy pierwszego
  wrażenia.
- **Zależności:** brak.

### 7. Powiadomienie z konkretnym celem i powitanie po przerwie (R1 §2.3 R6/R7, niezrobione)
- **Cel:** codzienne przypomnienie (`notifications/DailyReminderTexts.kt`, `DailyReminderLogic`)
  zamiast ogólnego tekstu podaje jeden cel z danych profilu czytanych bez `L` gry: brakujące fale
  do progu tygodnia, nieodebrane misje, dzisiejsze wydarzenie dnia. Po 3+ dniach przerwy menu
  pokazuje jednorazowe „Witaj z powrotem” z tym, co się zmieniło (nowy zestaw tygodnia,
  odnowione misje). Seria logowań nie karze za przerwę.
- **Dla gracza:** powiadomienie mówi, po co wrócić, a nie tylko „zagraj”. Po urlopie gracz od razu
  widzi, co nowego.
- **Pliki:** `notifications/*`, `screens/MenuScreen.kt`, `meta/Profile.kt` (`lastPlayDay`), `L.kt`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Powiadomienia nadal najwyżej 1 dziennie, bez FCM i bez
  pokazywania na wynikach bitwy.
- **Zależności:** decyzja właściciela, czy powitanie daje premię medali (R1 R7).

### 8. Kadr sklepu „Wyniki” z wiarygodnymi liczbami
- **Cel:** `docs/store/assets/D7/*/05-results.png` pokazuje zwycięstwo z 3 gwiazdkami
  w czasie **0:40**, z **9 zabitymi** i **+620 medali za osiągnięcia**. Takiej bitwy nie da się
  rozegrać. Wynik trzeba wygenerować z prawdziwej, rozegranej przez AI bitwy etapu (czas 7–9 min,
  realne medale) albo przynajmniej ustawić stały, realny zegar i liczniki
  (`generateD7`, `StoreGraphicsTest`).
- **Dla gracza:** zrzut w sklepie odpowiada temu, co zobaczy w grze. Google ocenia też, czy
  zrzuty nie wprowadzają w błąd.
- **Pliki:** test/eksport D7 (`StoreGraphicsTest`, zadanie `generateD7`), `docs/store/assets/D7/`.
- **Rozmiar:** S. **Ryzyko:** brak (tylko grafiki).
- **Zależności:** po D6, jeśli kadr 04 ma pokazać nowe karty.

---

## Kolejność dla 1–2 programistów (≈ 2 dni)

| Dzień | Programista A | Programista B |
|---|---|---|
| 1 | R10-01 (M) | R10-02 (S–M), potem pozycja 8 (S) |
| 2 | R10-04 (S–M), potem pozycja 6 (S) | R10-03 (M) |
| później | 5 i 7 po decyzjach właściciela | |

Konflikty: `L.kt` (każde zlecenie ma własną część, `part188`–`part191`; po scaleniu liczba
`putAll(part` musi równać się liczbie `private fun part`); `MainActivity.kt` (R10-01 start,
R10-04 start i `onResume`); `BattleScreen.kt` (R10-01 `snapshotBytes`, R10-03 rysowanie
i dotyk); `CampaignScreen.kt` (R10-02 rysowanie węzłów, R10-04 `onTrimMemory`/`render`); `Profile.kt`
(R10-01, R10-02 tylko odczyt, R10-04 flaga). Najbezpieczniej scalać w kolejności
R10-02 → R10-01 → R10-04 → R10-03.

## Decyzje dla właściciela

1. **R10-01, rekompensata za bitwę utraconą przez aktualizację:** (a) komunikat, zwrot próby
   dnia i medale jak za kapitulację (rekomendacja); (b) sam komunikat i zwrot próby.
2. **Pozycja 5, przybliżenie pola na zewnętrznym Foldzie w pionie:** zgoda na próbę
   (żołnierze ok. 1,5× więksi, widać mniej pola)? Zrzuty przed i po do oceny przed scaleniem.
3. **Pozycja 7, powitanie po przerwie:** z jednorazową premią medali (np. 30) czy bez premii?
4. **R10-03, sygnały dźwiękowe:** włączone domyślnie dla wszystkich (rekomendacja; głośność
   jak efekty) czy osobny przełącznik w Ustawieniach?
5. **Pozycja 8:** czy kadry D7 zostały już wysłane do Play Console? Jeśli tak, podmienić
   `05-results` przed testami zamkniętymi.
