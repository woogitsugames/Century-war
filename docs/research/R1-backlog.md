# R1 — backlog z researchu (runda 1, 3.11.2026)

Baza: `integ/tura10` (191c7f2). Ten dokument opiera się na kodzie i dokumentach repo oraz na
źródłach zewnętrznych podanych w każdej sekcji. Nie zmienia kodu gry. Wszystkie daty Google
sprawdzono 3.11.2026. Polityki Google zmieniają się często, więc przed wysyłką AAB sprawdź je jeszcze raz.

**Najważniejsze wnioski w skrócie**

- Pod względem technicznym gra jest gotowa na wymagania Google Play 2026: targetSdk 36, AAB, R8,
  brak bibliotek natywnych, więc wymóg 16 KB jej nie dotyczy, `appCategory="game"`, przewidywane
  Wstecz, kopia zapasowa. Luki są głównie **formalne**: status handlowca, deklaracje, dane
  właściciela. Do tego dochodzą **dobrowolne** wymagania programu Play Games „Level Up”.
- Pierwsza godzina jest dobrze przetestowana (`FirstHourJourneyTest`), ale **bitwy trwają 8–13 min**.
  W pierwszej godzinie gracz przechodzi tylko ok. 4 etapy. W mobilnych grach tego gatunku typowa
  sesja jest krótsza. Największy zwrot dadzą: jasny „następny cel”, krótsza ścieżka dla krótkiej
  sesji i miękka pomoc po porażce.
- Dostępność ma solidną podstawę: własny `AccessibilityNodeProvider`, 4 palety, znaczniki drużyn,
  tekst 100/115/130%, tryb leworęczny, redukcja wstrząsów i błysków. Brakuje: TalkBack w HUD bitwy,
  powiązania z ustawieniami systemu (rozmiar czcionki, animacje) i wolniejszego tempa.
- Wydajność jest dopracowana (regulator jakości, testy alokacji, budżet bitmap). Brakuje reakcji
  na sygnały systemu (`onTrimMemory`, termika) i diagnostyki ANR/LMK z `ApplicationExitInfo`.

---

## Kolejność zlecania (R2, 3.11.2026)

Gotowe zlecenia są w `docs/tasks/R2-*.md`. Każde ma własną część tekstów w `L.kt`: 179–183.
Przy 1 agencie programiście na raz kolejność jest taka:

| Krok | Zlecenie | Zależności i powód kolejności |
|---|---|---|
| 1 | Dzień 5: TalkBack HUD (TOP 1, już w kolejce koordynatora) | Wskazówki w sekcji 3.2. R2-03 korzysta z `touchExplorationActive`, więc lepiej po nim albo razem |
| 2 | [R2-03 dostępność z systemu](../tasks/R2-03-system-a11y-settings.md) (`part181`) | Niezależne, małe. Daje tempo ×½ dla graczy z TalkBack z kroku 1 |
| 3 | [R2-05 stabilność](../tasks/R2-05-low-end-stability.md) (`part183`, raczej bez tekstów) | Niezależne. Dotyka `QualityGovernor`, `crash/`, `MainActivity`. **Przed R2-04**, bo oba zmieniają `crash/` i `MainActivity` |
| 4 | [R2-04 raport testera](../tasks/R2-04-tester-report.md) (`part182`) | Po R2-05 (wspólne `crash/`, `onPause`). Musi wejść **przed pierwszym AAB dla testerów zamkniętych** |
| 5 | [R2-01 następny cel](../tasks/R2-01-next-goal.md) (`part179`) | Niezależne od 2–4. Wprowadza ścieżkę „cel w wierszu wskazówki” na wynikach |
| 6 | [R2-02 pomoc po porażkach](../tasks/R2-02-help-after-losses.md) (`part180`) | **Po R2-01**: oba piszą do `tipText` w `BattleResultsPanel` i `BattleScreen.settleRewards` |
| 7 | Dzień 6: nowa zawartość (TOP 10, sekcja 5) | Zmienia `:sim` (PROTOCOL + złote powtórki). Po R2-02, żeby nie scalać zmian `settleRewards` jednocześnie z regeneracją złotych powtórek |
| — | TOP 7 szybka bitwa, TOP 9 Play Games / Level Up | Czekają na decyzje właściciela (balans; udział w Level Up, Game Stats w Data safety) |

Konflikty scalania: `L.kt` (różne części, dopisywane w różnych miejscach `putAll`), `MainActivity.kt`
(R2-03, R2-04, R2-05), `Profile.kt` (R2-02, R2-03; pola w różnych blokach). Po każdym scaleniu
liczba `putAll(part` musi być równa liczbie `private fun part`.

## TOP 10 zadań do zlecenia

Kolejność odpowiada stosunkowi efektu do pracy przy obecnej kolejce koordynatora (dzień 4–7).
Rozmiar: S ≤ pół dnia agenta, M ≈ 1 dzień, L > 1 dzień.

### 1. TalkBack dla przycisków HUD w bitwie (zaplanowane jako dzień 5)
- **Cel:** zamiast jednego węzła „Bitwa, Pauza” udostępnić każdy przycisk HUD jako osobną
  wirtualną kontrolkę z nazwą, ceną i stanem, oraz krótkie komunikaty o ważnych zdarzeniach.
- **Dla gracza:** osoba z czytnikiem ekranu może szkolić jednostki, użyć ostrzału, Zrywu, ewolucji
  i dowódcy bez widzenia ekranu. Usłyszy też „Twoja baza 50%” albo „Nowa epoka”.
- **Pliki:** `engine/GameAccessibility.kt` (rodzaj `BATTLE` z węzłami zamiast jednego),
  `screens/BattleScreen.kt` (`accessibilityLabel`, `accessibilityChecked`, opcjonalnie nowa
  `accessibilityState`), `screens/BattleBottomHud.kt`, `screens/BarrageAiming.kt` (cel bez
  przeciągania), `engine/L.kt` (nowa `part177`, 10 języków), testy w `app/src/test/.../engine/`.
- **Rozmiar:** M. **Ryzyko:** średnie. Złoto zmienia się w każdej klatce, więc bez progowania drzewo
  będzie przebudowywane 60 razy na sekundę (wydajność). Przeciąganie przy ostrzale nie działa z TalkBack.
  Szczegóły techniczne są w sekcji 3.2.

### 2. Pakiet „testy zamknięte”: lokalny lejek i raport testera
- **Cel:** gra liczy lokalnie, bez sieci i bez nowych danych w Data safety, kluczowe kroki: samouczek
  ukończony, najwyższy etap, liczba sesji, dni z grą, porażki z rzędu, tryby użyte, średni czas
  bitwy, p95 klatki, stopnie redukcji jakości. Wynik jest dostępny jako „Raport dla testera” do
  ręcznego udostępnienia, tą samą drogą co obecny raport błędu (`crash/`). W sklepie ustawiamy adres opinii.
- **Dla gracza (testera):** jednym przyciskiem wysyła nam, jak mu szło. My mamy dane do wniosku
  o dostęp do produkcji. Google pyta tam, czy testerzy użyli wszystkich funkcji i jak wyglądało
  ich zaangażowanie. W 2026 zdarzają się odrzucenia za „niewystarczające zaangażowanie”.
- **Pliki:** nowy `meta/PlaytestLog.kt` (liczniki w `profile.json` albo w osobnym pliku poza kopią),
  `crash/CrashReportText.kt` (wspólny format), `screens/AboutScreen.kt` lub Ustawienia (przycisk),
  `engine/FrameMetrics.kt` (odczyt p95), `docs/RELEASE.md` (instrukcja dla testerów), `L.kt`.
- **Rozmiar:** M. **Ryzyko:** niskie. Pliku nie wolno dopisać do reguł kopii zapasowej. Data safety
  zostaje bez zmian, bo dane nie są wysyłane automatycznie (ta sama zasada co przy raportach błędów).

### 3. Dostępność z ustawień systemu i wolne tempo ×0,5
- **Cel:** (a) przy pierwszym uruchomieniu `textScale` przyjmuje wartość z systemowej skali czcionki
  (`Configuration.fontScale` ≥ 1,15 → 115, ≥ 1,3 → 130), a ręczny wybór w Dostępności ma
  pierwszeństwo; (b) gdy w systemie wyłączono animacje (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`)
  albo włączono „usuń animacje”, domyślnie włączyć `reduceShake` i `reduceFlash`; (c) tempo bitwy
  ×0,5 w Dostępności, a gdy TalkBack jest aktywny (`isTouchExplorationEnabled`), proponowane domyślnie.
- **Dla gracza:** osoba z dużą czcionką w telefonie od razu dostaje czytelną grę. Osoba wrażliwa
  na ruch nie musi szukać opcji. Kto gra wolniej albo z czytnikiem, ma czas na decyzje.
- **Pliki:** `meta/Profile.kt` (flaga „ustawione ręcznie”), `engine/Game.kt` (`ui.textScale`),
  `MainActivity.kt` (`onConfigurationChanged`, `fontScale` jest już w `configChanges`),
  `screens/AccessibilityScreen.kt`, `screens/BattleScreen.kt` (`speed`: 1/3/5 → dodać 0,5 tylko
  przy opcji), `L.kt`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Tempo dotyczy tylko odtwarzania, krok symulacji zostaje
  1/60 s, więc powtórki i ranking się nie zmieniają. Trzeba sprawdzić zrzuty przy 130%.

### 4. Formalności Play na 2026–2027: dopisać brakujące punkty do RELEASE/PLAY_CONSOLE — **zrobione w R2**
(`docs/RELEASE.md` → „Formalności Play 2026–2027”, `docs/store/PLAY_CONSOLE.md` → sekcja 8)
- **Cel:** do list w repo dopisać rzeczy, których jeszcze nie ma:
  **deklarację statusu handlowca (trader / non-trader) do 1.02.2027** (handlowiec ma publicznie
  widoczne imię i nazwisko, adres, telefon i e-mail; osoba nie-handlowiec tylko e-mail i kraj),
  **weryfikację tożsamości dewelopera** (instalacja APK spoza Play na certyfikowanych telefonach:
  egzekwowanie od 30.09.2026 w ID/SG/TH, globalnie w 2027; dotyczy też wysyłania APK testerom
  poza Play), **krok kontroli 16 KB** (`zipinfo` AAB: brak `lib/*.so`), **Android vitals** (progi
  1,09% awarii i 0,47% ANR, 8% na model telefonu), **progi pamięci od 02.2027** oraz **targetSdk 37
  do 31.08.2027**.
- **Dla gracza:** nic nie zobaczy. Właściciel nie będzie zaskoczony wymaganiem w konsoli, a jego
  prywatny adres nie trafi do sklepu przez pomyłkę.
- **Pliki:** `docs/RELEASE.md`, `docs/store/PLAY_CONSOLE.md`, `docs/store/OWNER_INPUT.md`.
- **Rozmiar:** S (tylko dokumenty). **Ryzyko:** brak. Wymaga decyzji właściciela (patrz koniec).

### 5. „Następny cel” na wynikach bitwy i w menu
- **Cel:** jedna linia z najbliższym konkretnym celem, wybieranym według priorytetu: nierozliczona
  misja → badanie, które gracza już stać („Stać cię na: Skarbiec II”) → następny etap i jego nagroda
  („Etap 5: pogoda — deszcz”) → próg Oblężenia tygodnia („Brakuje 4 fal do Srebra”).
  Dotknięcie prowadzi prosto do celu.
- **Dla gracza:** po każdej bitwie wie, po co ma zagrać jeszcze raz. To podstawowy wzorzec domykania
  sesji w grach mobilnych („zakończ sesję powodem do powrotu”).
- **Pliki:** `screens/BattleResultsPanel.kt`, `screens/MenuScreen.kt` (pod przyciskiem Graj),
  nowy `meta/NextGoal.kt` (czysta logika, testowalna bez Androida), `meta/Missions.kt`,
  `L.kt`, test w duchu `FirstHourJourneyTest`.
- **Rozmiar:** M. **Ryzyko:** niskie–średnie. Kolizje z banerami i dymkami trzeba objąć istniejącymi
  asercjami pierwszej godziny, a na zewnętrznym Foldzie jest mało miejsca.

### 6. Pomoc po porażkach w kampanii
- **Cel:** po drugiej porażce z rzędu na tym samym etapie okno wyników proponuje: „Spróbuj na
  poziomie Łatwy” (`CampaignDifficulty.EASY`, już istnieje) albo „Badania: stać cię na X”. Po trzeciej
  pokazuje krótką wskazówkę, dlaczego bitwa była przegrana (rozwinięcie istniejącego `why_lost`).
- **Dla gracza:** nie utknie w ścianie, która dziś (BALANCE.md) dla części profili oznacza 37–50%
  wygranych na etapach 11–18. Zrezygnowanie po serii porażek to typowy moment odejścia z gry.
- **Pliki:** `screens/BattleResultsPanel.kt`, `screens/CampaignScreen.kt`, `meta/Profile.kt` (licznik
  porażek na etap, poza zapisem chmury lub w nim, do decyzji), `L.kt`.
- **Rozmiar:** S. **Ryzyko:** niskie. Ranking kampanii liczy się tylko od poziomu Normalny (decyzja
  właściciela), więc trzeba jasno opisać, że na Łatwym etap nie trafi do rankingu.

### 7. Szybka bitwa (ok. 4–5 min) na krótkie sesje
- **Cel:** w kafelku Potyczki gotowy zestaw „Szybka bitwa”: start od wyższej epoki, `SHORT_DAY`
  (presja oblężnicza wcześniej, modyfikator już istnieje), mniejsze HP baz. Bez rankingu albo
  z osobną tablicą. Cel: mediana czasu 4–5 min, sprawdzona przez `BalanceReport`.
- **Dla gracza:** może zagrać pełną bitwę w przerwie albo w tramwaju, zamiast odkładać grę na 10 minut wolnego.
- **Pliki:** `meta/SkirmishSetup.kt`, `screens/SkirmishSetupScreen.kt`, `screens/ModeScreen.kt`,
  `sim/.../Modes.kt` (jeśli potrzebny nowy preset), `docs/BALANCE.md`, `L.kt`.
- **Rozmiar:** M. **Ryzyko:** średnie. Wymaga pomiaru balansu. Jeśli preset zmienia `BattleConfig`,
  trzeba podbić PROTOCOL i wygenerować złote powtórki.

### 8. Stabilność na tanich telefonach: sygnały systemu i diagnostyka
- **Cel:** (a) `onTrimMemory(TRIM_MEMORY_UI_HIDDEN / RUNNING_LOW)` zwalnia `Picture` kampanii,
  cache bitmap i drugi `MediaPlayer`; (b) przy starcie (API 30+) `ActivityManager.getHistoricalProcessExitReasons`
  zapisuje do lokalnych raportów przyczynę poprzedniego zakończenia: ANR, `REASON_LOW_MEMORY`,
  błąd natywny, wraz ze śladem ANR z `getTraceInputStream()`; (c) `PowerManager.addThermalStatusListener`
  (API 29+) i `getThermalHeadroom` (API 30+) jako trzecie wejście `QualityGovernor`; (d) muzyka nie
  przejmuje fokusu audio i nie zatrzymuje Spotify/podcastu, gdy `AudioManager.isMusicActive`
  (efekty grają dalej, muzyka gry milknie).
- **Dla gracza:** gra rzadziej się zacina i nie grzeje telefonu w długim Endless. Po zabiciu przez
  system wiemy, co się stało. Można grać przy własnej muzyce.
- **Pliki:** `MainActivity.kt` / `CenturyWarApp.kt`, `crash/CrashStore.kt`, `crash/CrashReportText.kt`,
  `engine/QualityGovernor.kt`, `engine/Music.kt` (`AudioFocusRequest`), `engine/RecordedMusic.kt`,
  `game/BitmapCacheBudget.kt`.
- **Rozmiar:** M. **Ryzyko:** niskie. Wszystko to API platformy bez nowych bibliotek. W trybie (d)
  nie zmieniać zachowania bez zgody właściciela, bo wpływa na odbiór muzyki.

### 9. Play Games: osiągnięcia z pierwszej godziny, Game Stats, Sidekick (pod „Level Up”)
- **Cel:** (a) sprawdzić testem, że co najmniej **4 osiągnięcia** wpadają w pierwszej godzinie
  każdemu graczowi (warunek „Quests” w Level Up; `FirstHourJourneyTest` już liczy osiągnięcia);
  (b) wysyłać **≥ 5 powtarzalnych statystyk** i 1 statystykę postępu przez `GameStatsClient`, który
  jest w PGS 22.x (np. bitwy, zabici, fale Endless, etapy, wygrane wyzwania); (c) włączyć Sidekick
  w Play Console (dla AAB to przełącznik, bez kodu).
- **Dla gracza:** zadania i nagrody w aplikacji Play Games, lepsza widoczność gry w sklepie.
- **Pliki:** `meta/PlayGamesLeaderboards.kt`, `meta/Achievements.kt`, `docs/PLAY_GAMES_SETUP.md`,
  `docs/store/play_games_ids.properties` (identyfikatory statystyk), `data_safety.md` (statystyki
  to nowy typ danych wysyłanych do Google).
- **Rozmiar:** M. **Ryzyko:** średnie. Level Up jest dobrowolny, a pełny program wymaga też
  sterowania klawiaturą, myszą i padem oraz dystrybucji na PC (sekcja 1.3). To decyzja właściciela.

### 10. Nowa zawartość: 4 karty Endless i 3 zestawy Oblężenia tygodnia (dzień 6)
- **Cel:** dopisać na końcu enumów karty z sekcji 5.1 (Saperzy, Mur tarcz, Skarbiec wojenny,
  Zakład) i zestawy tygodnia z sekcji 5.2, potem zmierzyć Endless fala 60 (cel 5–15/100, dziś 7).
- **Dla gracza:** więcej różnych podejść w Endless i świeże tygodnie w pierwszych miesiącach po premierze.
- **Pliki:** `sim/.../EndlessCards.kt`, `sim/.../Battle.kt` (efekty), `sim/.../Modes.kt`
  (`ChallengeRuleSets`), `screens/` (opisy kart, Kodeks), `meta/Achievements.kt`/`Missions.kt`
  (opcjonalnie), `L.kt`, `docs/BALANCE.md`.
- **Rozmiar:** M–L. **Ryzyko:** średnie. Trzeba podbić PROTOCOL, wygenerować złote powtórki
  i zachować kolejność ordinal w zapisach i powtórkach.

---

## 1. Wymagania Google Play 2026 a stan repo

### 1.1 Co jest spełnione

| Wymaganie | Stan w repo | Źródło |
|---|---|---|
| targetSdk 36 dla nowych aplikacji od 31.08.2026 | `targetSdk = 36`, `compileSdk = 36` | [Google: target API](https://developer.android.com/google/play/requirements/target-sdk), [Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878) |
| 16 KB page size | Brak `.so` w aplikacji i w AAR PGS 22.1.0, `play-services-base`, `basement` (sprawdzone `unzip -l`). Gra w samym Kotlinie jest zgodna | [Google: 16 KB](https://developer.android.com/guide/practices/page-sizes) |
| Android 16: duże ekrany ignorują orientację | `android:appCategory="game"` zwalnia grę z tej zmiany; `resizeableActivity=true`. Android 17 (API 37) usuwa opt-out, ale zwolnienie dla gier zostaje | [Android 16 zmiany](https://developer.android.com/about/versions/16/behavior-changes-16), [Android 17: ignorowane ograniczenia](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored) |
| Edge-to-edge (opt-out wyłączony dla API 36) | `setDecorFitsSystemWindows(false)`, wcięcia z `displayCutout` i `systemGestures` | jw. |
| Przewidywane Wstecz | `enableOnBackInvokedCallback="true"` | jw. |
| AAB, R8, profil ART | Próba release 3.11: APK 20,0 MiB, AAB 20,6 MiB; R8 z kontrolą zapisów | `docs/RELEASE.md` |
| DEX ≥ 25% optymalizacji (od 02.2027, tylko gdy DEX gry > 50 MB) | Nie dotyczy (DEX mały), R8 i tak włączony | [Progi pamięci](https://support.google.com/googleplay/android-developer/answer/17492799) |
| Kopie zapasowe | `allowBackup`, `dataExtractionRules`, tylko `profile.json` i `history.json`; Saved Games PGS z ręcznym rozstrzyganiem konfliktów | `data_safety.md` |
| Data safety, polityka, IARC | Szczegółowe instrukcje w `docs/store/` | `docs/store/PLAY_CONSOLE.md` |
| Testy zamknięte 12/14 | Instrukcja krok po kroku w `RELEASE.md` | [Google: wymagania testów](https://support.google.com/googleplay/android-developer/answer/14151465) |
| PGS v2 | `play-services-games-v2:22.1.0` (najnowsza znana, 15.09.2026), provider usunięty przy braku ID | [Wydania Google Play services](https://developers.google.com/android/guides/releases) |

### 1.2 Luki (co dopisać lub zrobić)

1. **Status handlowca (EOG/UK), termin 1.02.2027.** Każde aktywne konto musi go zadeklarować.
   Handlowiec ma w sklepie publiczne imię i nazwisko, adres, telefon i e-mail. Nie-handlowiec
   tylko e-mail i kraj. Działalność prywatna i niekomercyjna zwykle nie jest „handlem”, ale
   planowana monetyzacja (`monetization_plan.md`) to zmienia. **Decyzja właściciela.**
   [Google: trader status](https://support.google.com/googleplay/android-developer/answer/13628312)
2. **Konto osobiste a organizacja.** Konto osobiste założone po 13.11.2023: 12 testerów z ciągłym
   opt-in przez 14 dni, a potem wniosek z pytaniami o przebieg testów, opinie, grupę docelową,
   wyróżnik gry, szacunek instalacji i wprowadzone zmiany. Google rozpatruje go zwykle do 7 dni
   i może zażądać dalszych testów przy słabym zaangażowaniu. Konto organizacji nie ma progu 12/14,
   ale wymaga D-U-N-S, a jego dane są publiczne. → zadanie TOP 2 daje odpowiedzi na te pytania.
   [Google: wymagania testów](https://support.google.com/googleplay/android-developer/answer/14151465);
   Oficjalna pomoc Play Console potwierdza (R2): powodem dalszego testowania może być
   „insufficient tester engagement during the testing period”.
3. **Weryfikacja tożsamości dewelopera** (Android Developer Verification). Rejestracja otwarta od
   03.2026 (nieoficjalne), egzekwowanie od **30.09.2026** w Indonezji, Singapurze i Tajlandii,
   globalnie w 2027 (oficjalne; Brazylii oficjalna strona nie wymienia). Instalacja przez `adb`
   zostaje dozwolona; konto „limited distribution” do 20 urządzeń bez dowodu tożsamości. Dziś właściciel dostaje APK debug spoza Play. Po wejściu przepisów w Polsce instalacja
   APK od niezweryfikowanego dewelopera na certyfikowanym telefonie będzie utrudniona. Trzeba
   zweryfikować konto zawczasu i przejść na tor testów wewnętrznych Play (do 100 testerów).
   [Google: developer verification](https://support.google.com/android-developer-console/answer/16561738),
   [Android Authority: harmonogram](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/)
4. **Android vitals.** Progi złego działania: 1,09% awarii zauważalnych przez użytkownika, 0,47% ANR,
   8% na model telefonu. Przekroczenie obniża widoczność gry i może dodać ostrzeżenie w karcie sklepu.
   Do `RELEASE.md` dopisać cotygodniowy przegląd vitals w czasie testów zamkniętych.
   [Android vitals](https://developer.android.com/games/optimize/vitals)
5. **Progi pamięci od 02.2027** (gry, p90 anon RSS + swap): telefon 4 GB ≤ 2,25 GB na pierwszym
   planie, 2,0 GB w tle; bitmapy w tle ≤ 200 MB. Gra jest daleko poniżej (budżet bitmap 2–8 MiB).
   Warto jednak zmierzyć `dumpsys meminfo` w Endless po fali 40 i dopisać wynik do `PERFORMANCE.md`.
   [Progi pamięci](https://support.google.com/googleplay/android-developer/answer/17492799),
   [blog Google 08.2026](https://android-developers.googleblog.com/2026/08/app-quality-memory-optimization-secure-onboarding.html)
6. **targetSdk 37 do 31.08.2027** (nowe wersje). Gry są zwolnione z ignorowania orientacji, ale
   użytkownik może wymusić proporcje w ustawieniach systemu. Trzeba sprawdzić, czy gra w pionie
   na Foldzie poprawnie się „letterboxuje”. Mówi o tym też wytyczna LU-LS-GAC.
7. **Kontrola 16 KB w liście wydania:** jedna komenda `zipinfo -1 app-release.aab | grep '\.so$'`
   (ma być pusto). Nowe SDK, np. w ramach monetyzacji, mogą wnieść biblioteki natywne.
8. **Deklaracje App content:** reklamy (nie), grupa docelowa, funkcje finansowe, aplikacje
   zdrowotne, aplikacje rządowe, dostęp recenzenta. Lista zależy od konsoli. `PLAY_CONSOLE.md`
   opisuje część z nich, trzeba ją przejrzeć w konsoli przy zakładaniu aplikacji.
9. **Usunięcie konta.** Polityka Google wymaga ścieżki usunięcia konta w aplikacji i w sieci,
   jeśli aplikacja pozwala je utworzyć. Własny ranking ma UUID i pseudonim, a „Nowa tożsamość”
   wysyła żądanie usunięcia. Do decyzji: czy traktujemy to jako konto. Bezpieczniej opisać
   w polityce adres WWW/e-mail do usuwania danych rankingu (pole `contact_email` już przewidziane).

### 1.3 Program Play Games „Level Up” (dobrowolny)

Zapisy od 1.09.2026. Gry wydane po 31.07.2026 muszą spełnić wytyczne, żeby dostać korzyści
programu. Korzyści to głównie niższe opłaty i promocja, więc bez monetyzacji znaczy on mniej.
Wytyczne ([developer.android.com/games/guidelines](https://developer.android.com/games/guidelines)):

| ID | Wymaganie | Stan |
|---|---|---|
| LU-PA-GAA | PGS v2 inicjowane przy starcie | Jest (gdy są ID) |
| LU-SK-GAA | Sidekick (dla AAB przełącznik w konsoli) | Do włączenia |
| LU-AC-GAA/GAB | ≥ 10 osiągnięć z unikalną nazwą i opisem (zalecane 40+) | 74 |
| LU-AC-GAC | ≥ 4 osiągnięcia osiągalne w pierwszej godzinie (warunek Quests) | Prawdopodobnie tak (pierwszy etap daje kilka); potwierdzić testem |
| LU-GS-GAA/GAB | ≥ 5 powtarzalnych statystyk + 1 statystyka postępu (Game Stats) | Brak (`GameStatsClient` w 22.x) |
| LU-RE-GAC/GAD | Nagrody Play Games: 2 jednorazowe od 30.09.2026, 1 powtarzalna od 03.2027 | Brak (np. sztandary z `BannerCatalog`) |
| LU-CS-GAA/GAB | Zapis w chmurze i polityka konfliktów | Jest (Saved Games, rozstrzyganie konfliktu) |
| LU-LS-GAA..GAC | Wcięcia, zmiany konfiguracji bez utraty stanu, pełny ekran 4:3/16:10/21:9 | Jest (Fold testowany) |
| LU-PC-GAA | Dystrybucja na Google Play Games na PC | Brak; wymaga pkt. niżej |
| LU-IC-GAA | Pełna gra z kontrolerem oraz klawiaturą i myszą | **Brak** obsługi `KeyEvent`/myszy (L) |
| LU-PR-GAA | 60 FPS na urządzeniach referencyjnych | Regulator celuje w 60 |
| LU-MM-GAA | Progi pamięci (od 02.2027) | Spełnione wg szacunku |

Wniosek: Sidekick, osiągnięcia i statystyki to małe kroki (TOP 9). Klawiatura, pad i PC to osobna
duża praca (L) i decyzja właściciela.

---

## 2. Retencja i pierwsza godzina

### 2.1 Co gra już ma (z kodu i raportów)

Samouczek z prawdziwymi dotknięciami (ok. 3 min), dymki co ok. 50 s, zapowiedzi epok w kampanii,
misje dzienne i tygodniowe (40+ typów, losowane według UTC), seria logowań 7 dni
(`Missions.loginRewardFor`), wyzwanie dnia z wydarzeniami, Oblężenie tygodnia z progami
Brąz/Srebro/Złoto, 74 osiągnięcia, badania od etapu 2, dowódca od etapu 8, stopnie wież, doktryny,
karty Endless i bossy, powiadomienie o wyzwaniu dnia po pierwszym zwycięstwie, „dlaczego przegrałem”,
„Co nowego”, wznawianie przerwanej bitwy z rozliczeniem dokładnie raz.

Oś czasu z `docs/reports/noc-5-pierwsza-godzina.md`: etap 1 kończy się w 8:42, etap 2 w 21:29,
etap 4 w 48:34, etap 8 (dowódca) w ok. 1:40–1:55. Po dniu 1 etapy 2–7 mają mieć ok. 8 min.

### 2.2 Wzorce gatunku (ogólne, bez kopiowania gier)

- Pierwsza sesja decyduje o grze: 80–90% graczy nie wraca po pierwszym uruchomieniu. Gracz ma
  szybko dojść do „zabawy”, a sesję zamknąć z powodem do powrotu (cel, nagroda, zapowiedź).
  [Roblox: onboarding](https://create.roblox.com/docs/production/game-design/onboarding),
  [PocketGamer.biz: FTUE](https://www.pocketgamer.biz/news/77289/pgc-digital-how-to-enhance-the-first-time-user-experience/)
- Mechaniki wprowadza się stopniowo, bez wielu okien naraz. Gra już to robi (najwyżej jedna nakładka).
- Pomiar: ukończenie samouczka, długość pierwszej sesji, przejście z sesji 1 do sesji 2, D1/D7.
  Mediana rynku ok. 22% D1 i 4% D7, najlepsze 10% gier ok. 40% i 12%. Strategie według Mistplay
  ok. 25% D1, 8% D7, 3% D30. To kontekst, nie cel.
  [GameAnalytics 2026 (streszczenie)](https://gamedevreports.substack.com/p/gameanalytics-mobile-and-pc-game),
  [Segwise: retencja według gatunku](https://segwise.ai/blog/mobile-gaming-app-user-retention-strategies.md)
- Gry z krótką bitwą (lane battlery) mają zwykle bitwy po kilka minut. Długie bitwy działają, gdy
  gracz może je przerwać i wznowić (gra ma to). Pomaga też krótki tryb na „jedną przerwę”.

### 2.3 Usprawnienia (max 8, według efektu do pracy)

| # | Usprawnienie | Efekt | Praca | Uwagi |
|---|---|---|---|---|
| R1 | **„Następny cel”** na wynikach i w menu (TOP 5) | wysoki | M | Domyka każdą sesję |
| R2 | **Pomoc po 2 porażkach** z rzędu: Łatwy albo badanie (TOP 6) | wysoki | S | Etapy 11–18 i 28–36 to ściany |
| R3 | **Szybka bitwa 4–5 min** (TOP 7) | średni–wysoki | M | Pomiar balansu |
| R4 | **Lokalny lejek i raport testera** (TOP 2) | wysoki (dla testów) | M | Bez sieci, bez zmian Data safety |
| R5 | **Pierwsza nagroda za powrót następnego dnia** widoczna już na wynikach pierwszej bitwy („Jutro: +X medali i nowe wyzwanie dnia”) | średni | S | Seria logowań już jest, brakuje zapowiedzi |
| R6 | **Powiadomienia z treścią celu** (najwyżej 1 dziennie): „Brakuje 4 fal do Srebra w Oblężeniu” zamiast ogólnego przypomnienia; prośba o zgodę na powiadomienia dopiero po wygranej, jak teraz | średni | S–M | `notifications/DailyReminderTexts.kt`, bez FCM |
| R7 | **Powrót po przerwie** (3+ dni): jednorazowa premia medali i podpowiedź, co nowego od ostatniej gry | średni | S | Seria nie może karać za przerwę |
| R8 | **Krótsza pierwsza bitwa po samouczku** (etap 1: 8,5 min → ok. 5–6 min, np. mniejsze HP baz tylko w etapie 1) | średni | S | PROTOCOL + złote powtórki; decyzja właściciela |

Czego nie robić: bez sztucznego „energy” i blokad czasowych. Gra jest offline i bez reklam. Takie
blokady psują zaufanie i nie pasują do decyzji o uczciwym AI.

---

## 3. Dostępność

### 3.1 Stan

| Obszar | Jest | Brakuje |
|---|---|---|
| TalkBack | Własny `AccessibilityNodeProvider` (`engine/GameAccessibility.kt`): menu, Ustawienia, Dostępność, Podpowiedzi, pauza; brak alokacji bez usługi | HUD bitwy (jeden węzeł), panel wież, wybór doktryny i kart, wyniki, kampania, kodeks |
| Dotyk 48 dp | Menu, podpowiedzi, „Co nowego”, przyciski wież; strzałki ulepszeń z `hitSlop` | Węzły TalkBack używają `b.r` zamiast obszaru z `hitSlop`/`hitOffset` |
| Kontrast i daltonizm | 4 palety (standard, błękit/bursztyn, turkus/koral, wysoki kontrast), znaczniki drużyn | Test kontrastu tekstów HUD (np. 4,5:1) na wszystkich paletach w zrzutach |
| Tryb leworęczny | `leftHandedHud` + `LeftHandedHudTest` | — |
| Czcionka | 100/115/130% w grze | Systemowe `fontScale` jest ignorowane (TOP 3) |
| Ruch i błyski | `reduceShake`, `reduceFlash`, `screenShake` 0–2 | Brak odczytu systemowego „usuń animacje” (TOP 3) |
| Czas reakcji | Pauza, ×1/×3/×5 | Brak ×0,5 (TOP 3) |
| Sterowanie | Dotyk | Switch Access tylko tam, gdzie są węzły; brak klawiatury i pada |

Wytyczne: [Game Accessibility Guidelines](https://gameaccessibilityguidelines.com/),
[Android: dostępność własnych widoków](https://developer.android.com/guide/topics/ui/accessibility/custom-views),
[Material: rozmiar celu 48 dp](https://m3.material.io/foundations/designing/structure#dab862b1-e042-4c40-b680-b484b9f077f6).

### 3.2 TalkBack w HUD bitwy: wskazówki techniczne (bez bibliotek)

`ExploreByTouchHelper` jest w `androidx.customview`, czyli w bibliotece. Gra ma już jego
odpowiednik w `GameAccessibility`, więc trzeba go rozbudować, a nie zastępować.

1. **Stałe ID węzłów.** W bitwie używać `Button.id` (UNIT0…, BARRAGE, RALLY, COMMANDER, …) zamiast
   indeksu `i + 1`, bo przycisk dowódcy pojawia się od etapu 8 i przesuwa indeksy. Fokus TalkBack
   przeskakiwałby wtedy na inny przycisk.
2. **Kolejność przechodzenia** = kolejność w liście `nodes`: najpierw węzły informacyjne (bez
   kliknięcia): „Złoto 340”, „Doświadczenie 60% do Epoki brązu”, „Twoja baza 82%, baza wroga 64%”;
   potem jednostki 1–4, akcje, na końcu Pauza. Węzłom informacyjnym dać `className = "android.widget.TextView"`.
3. **Treść węzła jednostki:** `contentDescription = "Łucznik, 75 złota"`, a stan osobno:
   `setStateDescription("za mało złota" / "w kolejce 2")` (API 30+, na 26–29 dopisać do opisu).
   `isEnabled = false`, gdy nie stać gracza. TalkBack czyta wtedy „niedostępne”, a węzeł zostaje
   w drzewie (nie usuwać go).
4. **Ulepszenie jako akcja niestandardowa**, a nie osobny malutki węzeł: na węźle jednostki
   `addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.cw_action_upgrade, "Ulepsz: 120 złota"))`,
   z ID z `res/values/ids.xml` (`<item type="id" name="cw_action_upgrade"/>`). `performAction`
   obsługuje je jak dotknięcie `unitUpgrade[i]`. Strzałka 24 dp nakłada się dziś na przycisk
   jednostki, więc dwa węzły w jednym miejscu myliłyby eksplorację dotykiem.
5. **Ostrzał bez przeciągania:** przy aktywnym TalkBack aktywacja `barrageBtn` nie uruchamia
   celowania palcem. Zamiast tego węzeł ma 2–3 akcje niestandardowe: „Ostrzał: czoło wroga”,
   „Ostrzał: najgęstsza grupa”, „Ostrzał: przed moją bazą”. Pozycję liczy się z `BarrageAiming`,
   a celownik nadal omija własnych żołnierzy (zatwierdzony układ się nie zmienia). Komenda do
   symulacji jest ta sama co po przeciągnięciu, więc powtórki są zgodne.
6. **Bez przebudowy w każdej klatce.** `matches()` porównuje dziś `label/sub`. Jeśli złoto jest
   w napisie, drzewo zmieni się 60 razy na sekundę. W bitwie: (a) porównywać tylko stany dyskretne
   (`enabled`, `selected`, `visible`, kolejka), (b) teksty z liczbami odświeżać najwyżej co 1 s,
   (c) przy zmianie jednego węzła wysyłać `TYPE_WINDOW_CONTENT_CHANGED` z `setSource(view, id)`
   i `contentChangeTypes = CONTENT_CHANGE_TYPE_TEXT` (albo `..._STATE_DESCRIPTION`, API 30+),
   a nie zdarzenie całego okna. Test: przy włączonej usłudze ≤ 2 przebudowy na sekundę
   i 0 alokacji przy wyłączonej (rozszerzyć istniejący test 164).
7. **Komunikaty o zdarzeniach.** `View.announceForAccessibility` jest od API 36 oznaczone jako
   przestarzałe. Lepiej dodać jeden węzeł „Status bitwy” z `setLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)`
   i zmieniać jego tekst przy: awansie epoki gracza lub wroga, bazie poniżej 50/25%, fali bossa,
   gotowości Zrywu lub dowódcy, ofercie kart i doktryn. Limit: 1 komunikat na 3 s, ważniejszy
   zastępuje mniej ważny. [API diff: announceForAccessibility](https://developer.android.com/sdk/api_diff/36/changes/android.view.View)
8. **Okna wyboru** (doktryna, karty Endless, wariant epoki): osobny rodzaj drzewa jak `PAUSE`.
   Gra jest wtedy wstrzymana (`pendingChoice/pendingCards` zatrzymują czas), więc wystarczy
   udostępnić przyciski wyboru z pełnym opisem karty w `contentDescription`. `setPaneTitle(...)`
   (API 28+) przy otwarciu okna.
9. **Granice:** używać obszaru dotyku z `hitSlop` i `hitOffsetX/Y` (≥ 48 dp), przyciętego do widoku.
   Przy trzech rzędach HUD na Foldzie granice nie mogą na siebie nachodzić, inaczej `onHover`
   wybierze pierwszy pasujący węzeł.
10. **Kliknięcie** już idzie przez `game.post` z kontrolą, czy ekran się nie zmienił. W bitwie
    zdjąć warunek `screen.paused` dla nowych węzłów (dziś kliknięcie w bitwie działa tylko dla Pauzy).
11. **Testy ręczne** dopisać do sekcji „Testy ręczne czytnika ekranu (164)” w `RELEASE.md`:
    przejście palcem po HUD na telefonie 720p i Foldzie, akcja Ulepsz, ostrzał z akcji, komunikat
    o epoce, Switch Access na jednostkach.

### 3.3 Pozostałe propozycje (poza TOP)

- Zrzuty z paletą „wysoki kontrast” i tekstem 130% w `PrereleasePolishTest` (S).
- W raporcie przed premierą automatycznie sprawdzać kontrast tekst/tło napisów HUD (płótno testowe
  już zapisuje każdy napis) (M).
- Wibracja jako podwójny sygnał ważnych zdarzeń (baza < 25%), dla osób grających bez dźwięku. Część jest już w `BattleHaptics` (S).

---

## 4. Wydajność i stabilność na tanich telefonach

### 4.1 Stan

Mocne strony: `QualityGovernor` (CPU i GPU, stopnie, skala bufora ≥ 0,60), testy alokacji
(`AllocationTest`, `LowEndScreensPerformanceTest`), prealokowane `Path`/`RectF` w rendererze,
budżet bitmap (2–8 MiB, ≤ 1/32 sterty), `Picture` nagrywane tylko po zmianie geometrii, synteza
muzyki na osobnym wątku, nagrana muzyka przez `MediaPlayer` z nieskompresowanych OGG (19 MiB),
efekty przez `SoundPool` ładowane po pierwszej klatce, `setFrameRate`, StrictMode w debug, profil ART.

### 4.2 Ryzyka i luki

| Ryzyko | Gdzie | Propozycja |
|---|---|---|
| Brak reakcji na presję pamięci | brak `onTrimMemory` w całym kodzie | TOP 8a |
| ANR i zabicia przez system są niewidoczne do czasu danych z vitals | `crash/` łapie tylko wyjątki Javy | TOP 8b: `ApplicationExitInfo` (API 30+) |
| Przegrzanie w długim Endless ×5 | regulator widzi tylko czas klatki | TOP 8c: termika jako wcześniejszy sygnał |
| Dwa `MediaPlayer` (przenikanie) + `SoundPool` + synteza na tanich SoC | `engine/RecordedMusic.kt`, `Music.kt` | Zmierzyć CPU wątku audio na telefonie; przy LOW jeden odtwarzacz i krótkie przejście |
| Fokus audio `AUDIOFOCUS_GAIN` zatrzymuje muzykę użytkownika | `engine/Music.kt:57` | TOP 8d (decyzja) |
| Tryb gry Androida (bateria/wydajność) ignorowany | brak `GameManager` | `res/xml/game_mode_config.xml` (API 33+) + `GameManager.getGameMode()` (API 31+): w trybie oszczędzania baterii limit 30 FPS i LOW (S). [Game Mode API](https://developer.android.com/games/optimize/adpf/gamemode/gamemode-api) |
| Duży bufor na wewnętrznym ekranie Folda | skala bufora | Już zarządzane. Zmierzyć `meminfo` |

### 4.3 Pomiar bez emulatora

1. **Raport przedpremierowy Play Console.** Uruchamia się sam po wgraniu AAB na tor testowy
   (także wewnętrzny). Robot przechodzi grę na prawdziwych urządzeniach i zwraca awarie,
   wydajność, zrzuty i ostrzeżenia dostępności. Za darmo. Pierwszy krok po podpisie.
   Grafika z Canvas jest dla robota jednym widokiem, więc ruch po menu może być ograniczony.
   Tu pomagają węzły dostępności (TOP 1).
   [Pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)
2. **Firebase Test Lab** przez `gcloud firebase test android run --type robo --app app.apk --device model=...`.
   `gcloud` jest w kontenerze. Wymaga projektu Firebase, czyli decyzji właściciela. Plan Spark
   bez opłat: 5 testów dziennie na fizycznych i 10 na wirtualnych urządzeniach (oficjalne, R2).
   [Test Lab: gcloud](https://firebase.google.com/docs/test-lab/android/command-line)
3. **Raport testera** (TOP 2) z p95 klatki, stopniami jakości i termiką z telefonów testerów.
4. **Android vitals** po starcie testów zamkniętych: awarie, ANR, wolne sesje, pamięć według modeli.
5. Telefon właściciela: istniejąca nakładka FPS (`Ustawienia → FPS`) i procedura w `PERFORMANCE.md`.

---

## 5. Zawartość

Zasady zgodności z kodem: nowe wartości enumów **tylko na końcu** (`ordinal` w powtórkach
i zapisach), nowe teksty w `part177+`, zmiana wyniku bitew wymaga podbicia PROTOCOL i regeneracji
złotych powtórek. `Daily.RELEASE_DAY` (2027-12-01) jeszcze nie minął, więc nie ma historycznych
tygodni do zachowania. Po premierze nowe zestawy wolno dodawać tylko od przyszłego tygodnia,
wzorem `firstRuleSetWeek()`.

### 5.1 Karty Endless (pasują do `EndlessCards.kt` i limitów `canOffer`)

| Karta | Rzadkość | Efekt | Wpływ na balans |
|---|---|---|---|
| **SAPPERS** „Saperzy” | UNCOMMON, stałe stosy | +25% obrażeń ciężkich jednostek wobec bossów (także tarczy `SHIELD`) | Pomaga tylko na falach 10, 20, …; od fali 60 są dwaj bossowie, więc mocniej podnosi wynik fali 60. Zalecane: moc ze `stackPower` |
| **SHIELD_WALL** „Mur tarcz” | COMMON, stałe stosy | −10% obrażeń od pocisków dla piechoty i ciężkich | Kontra na `RANGED_SWARM`, `ARTILLERY`, `SKIRMISHERS`; niewielki ogólny wzrost przeżywalności |
| **WAR_CHEST** „Skarbiec wojenny” | RARE, stałe stosy | po fali +4% posiadanego złota (limit 40 × skala epoki) | Nagradza oszczędzanie; dopisać do `economicSynergyCards`; ryzyko kuli śnieżnej ogranicza limit |
| **WAGER** „Zakład” | RARE, jednorazowa (≤ 1) | wrogie fale +10% HP, punkty i medale za fale +25% | Ryzyko za nagrodę dla graczy walczących o ranking; obniża przeżywalność, więc może zbić falę 60 poniżej 5/100. Wymaga jawnego opisu przy wyborze |

Weryfikacja: `BalanceReport` Endless fala 60 przy 100 seedach. Obecnie 7/100, cel 5–15/100.
Zestaw dodaje 3 karty wzmacniające i 1 osłabiającą. Spodziewany wynik ok. 8–11/100.
Ofertę losuje `rng` z seeda, więc zestaw jest deterministyczny dla Oblężenia tygodnia.

### 5.2 Zestawy Oblężenia tygodnia (`ChallengeRuleSets`, dopisać po 6 istniejących)

Pary z puli `RELEASE_MODIFIERS` (bez `NO_TURRETS` i `SHORT_DAY`), różne od istniejących par:

| Klucz | Modyfikatory | Karta startowa | Charakter |
|---|---|---|---|
| `week177_night` „Nocna straż” | FOG_OF_WAR + ARMORED_FOE | VIGILANCE (wymaga slotu wieży; inaczej FORTRESS) | Obrona wieżami w mgle |
| `week177_mercs` „Żołd i łupy” | MERCENARIES + DOUBLE_INCOME | PLUNDER | Szybka gospodarka |
| `week177_bastion` „Kruchy bastion” | EXPENSIVE_TOWERS + FRAGILE_BASES | FRONTLINE_PLATE | Gra jednostkami, mało wież |

Pogoda dla Oblężenia tygodnia (np. „Nocna straż” z `Weather.NIGHT`) to nowy element `Weekly.config`
(dziś zawsze CLEAR). Bezpieczne tylko przed premierą albo od przyszłego tygodnia.
`COUNT` zmieni się z 6 na 9: `forWeek` używa `floorMod(index, COUNT)`, więc **zmienia kolejność
przyszłych tygodni**. Przed premierą nie ma to znaczenia, po premierze trzeba dodać próg tygodnia.

### 5.3 Wydarzenia dnia (`DailyEvent`, symetryczne dla obu stron)

- **HEAVY_DAY** „Dzień ciężkiej piechoty”: ciężkie jednostki −20% kosztu u obu stron. Konflikt:
  brak. Wpływ: dłuższe starcia na froncie, korzyść dla `SIEGE_MASTERS`.
- **TOWER_DUEL** „Pojedynek wież”: +1 slot wieży dla obu stron (do limitu). Konflikt z
  `NO_TURRETS` i `EXPENSIVE_TOWERS` (dopisać do `EVENT_CONFLICTS`).

### 5.4 Warianty epok

Dziś warianty mają tylko STEAM i SILICON. Trzecia para, np. dla epoki żelaza lub średniowiecza, to
zadanie L: grafika z kodu w `game/art/`, mnożniki w `Defs.kt`, pomiar lustrzany 200 seedów × 2
strony (różnica ≤ 10 pp), Kodeks. Lepiej po testach zamkniętych, jeśli testerzy będą korzystać
z wariantów (TOP 2 to pokaże).

### 5.5 Osiągnięcia i misje do nowej zawartości

Po 1 osiągnięciu na kartę rzadką (np. „Wygraj zakład: fala 30 z kartą Zakład”) i misja
tygodniowa „Ukończ Oblężenie tygodnia z nowym zestawem”. Każde nowe osiągnięcie to nowe ID w Play
Games (dziś 79 `REPLACE_ME`), więc lepiej dodać je **przed** konfiguracją Play Console przez właściciela.

---

## Sprawy wymagające decyzji właściciela

1. **Status handlowca** (do 1.02.2027): handlowiec pokazuje w sklepie adres i telefon,
   nie-handlowiec tylko e-mail i kraj. Planowana monetyzacja przemawia za statusem handlowca.
2. **Konto osobiste czy organizacji:** 12 testerów przez 14 dni i wniosek albo D-U-N-S i publiczne dane firmy.
3. **Weryfikacja tożsamości dewelopera** i przejście z APK debug na tor testów wewnętrznych Play.
4. **Program Level Up** (dobrowolny): czy celujemy w niego (Sidekick, statystyki, nagrody,
   a w pełnym programie także klawiatura, pad i PC).
5. **Game Stats w PGS** to nowy typ danych wysyłanych do Google, czyli aktualizacja Data safety.
6. **Muzyka gry przy muzyce użytkownika** (fokus audio): czy gra ma milknąć, gdy gra Spotify.
7. **Krótsza pierwsza bitwa i szybka bitwa:** zmiana balansu, PROTOCOL i złote powtórki.
8. **Firebase Test Lab:** projekt Firebase (opcjonalnie; plan Spark bez opłat daje 5 testów dziennie na fizycznych i 10 na wirtualnych urządzeniach; raport przedpremierowy Play jest darmowy).
9. **Usuwanie danych rankingu** jako „usunięcie konta” w rozumieniu polityki Google: adres WWW lub e-mail w polityce.
