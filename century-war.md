# Prompt startowy dla nowej sesji koordynatora Century War

Wklej poniższy tekst jako pierwszą wiadomość w nowej sesji Claude Code (repozytorium `woogitsugames/century-war`).

---

Jesteś koordynatorem projektu Century War — oryginalnej strategii na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek poza Play Games, grafika rysowana kodem). Repozytorium woogitsugames/century-war. Właściciel nie jest programistą: odpowiadaj po polsku, zwięźle, wyjaśniaj skutki prostym językiem; decyzje dawaj w formie klikalnej (AskUserQuestion). Commity po polsku z liniami atrybucji sesji, bez PR (chyba że do integracji z zewnętrznej sesji), bez identyfikatorów modeli w repo.

0. NAJPIERW: `git fetch origin` i przeczytaj NAJNOWSZE wersje: `git show origin/integ/tura10:century-war.md` (sekcja „Stan na koniec sesji 6.11”) oraz górę `git show origin/integ/tura10:docs/COORDINATOR_HANDOVER.md` (wpisy 4–6.11 na samej górze; starsze dla kontekstu). Tam jest stan, zasady, decyzje właściciela, kolejka i środowisko.
1. Środowisko: Android SDK do `~/android-sdk` (cmdline-tools z dl.google.com → `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`), `local.properties` z `sdk.dir`, init Gradle `~/.gradle/init.d/mirror.gradle` z mirrorem GCS (`https://maven-central.storage-download.googleapis.com/maven2/`: podmiana repo.maven.apache.org + w `beforeSettings` mirror i `gradlePluginPortal()` w `pluginManagement.repositories`), obrazy Robolectric `android-all-instrumented` 15-robolectric-13954326-i7, 9-robolectric-4913185-2-i7, 10-robolectric-5803371-i7 pobrane ręcznie z mirrora do `~/.m2` (szczegóły: `docs/COORDINATOR_HANDOVER.md` → „Przygotowanie środowiska”). Jeśli `dl.google.com` jest zablokowany przez sieć środowiska — poproś właściciela o dopisanie hosta w ustawieniach sieci; do tego czasu działa obejście `tools/offline-build/` (README tam).
2. Pracuj na `integ/tura10` (= main). Pełne testy: `flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun :app:lintDebug :app:assembleDebug --continue --console=plain` (ok. 25–35 min, w tle). **Przed wypchnięciem na main SPRAWDŹ wynik (EXIT=0 i 0 FAILED)** — raz wypchnięto czerwone testy. Po zielonych: push na `main`, `claude/bold-sagan-vwz5gm`, `integ/tura10` i gałąź swojej sesji; APK (`app/build/outputs/apk/debug/app-debug.apk`) dla właściciela.
3. CI: workflowy tylko przy tagu `v*`, PR do main i ręcznie. Nie dodawaj workflowów przy push.
4. Tryb pracy (decyzja właściciela): autonomicznie, do 2 agentów opus naraz, w worktree, Gradle przez flock, agenci wypychają po KAŻDYM kroku na `claude/dzien-N-*` (kontener jest często restartowany; po restarcie sprawdzaj worktree/gałęzie i wznawiaj). Limit 5-godzinny kończy się szybko przy 2 agentach — oszczędzaj; przy limicie ustaw budzenie po resecie. Ustaw jedno budzenie co godzinę (:07); stare zadania poprzedniej sesji są wyłączone.
5. Zewnętrzne modele (GPT itd.): zlecenia w `docs/tasks/` (instrukcja `docs/tasks/EXT-START.md`, paczka `docs/tasks/PACZKA-172-201.md`); wykonawcy wypychają gałęzie `gpt/NNN-*` bez PR — przeglądaj, testuj, scalaj.
6. Na początku krótko powiedz właścicielowi, co przejąłeś i co robisz dalej, i zacznij od kolejki z sekcji „Stan na koniec sesji 6.11”.

---

## Stan na koniec sesji 6.11.2026 (ok. 16:15 UTC)

**Gałęzie:** `main` = `claude/bold-sagan-vwz5gm` = `integ/tura10` = najnowszy commit (pełne testy zielone: app 958, sim 354, server 95; lint bez błędów). Brak agentów w toku, brak otwartych worktree.
**Wersje:** PROTOCOL **0.8.13**, `BattleSnapshot.VERSION` **23**, `ReplayRecord` v3, `Replay.VERSION` 7. **89 ID Play Games** (projekt, 5 rankingów, 83 osiągnięcia; arkusz `docs/store/play_games_sheet.*`, import `docs/store/pgs_import/`, `tools/pgs_sheet.py` — budżet punktów 1650, suma 1605). Teksty `L.kt`: zajęte do **part211**; **part212–215 zarezerwowane dla zleceń GPT 191–194**; następna wolna dla koordynatora: **part216**. Liczba `putAll(part` = liczba `private fun part`. W Kotlinie `%1$s` pisz `%1\$s`.

**Zrobione 4–6.11 (wszystko na main, raporty `docs/reports/d28…d67-*.md`):** „Nowość na tym etapie”; napis „pełne zasady od premiery” w wersjach testowych; etap 19 i K1 (etapy 28, 30–33) łagodniejsze; poprawki napisów na Foldzie; serwer rankingu utwardzony (retencja, usuwanie gracza, kopie); audyt kodu i poprawki wycieków/wyścigów; przegląd tłumaczeń (pt-PT, fr „vous”); Raport dla testera v2 (kod testera, awarie/ANR, mapa dni, lejek etapów), przypomnienia dnia 3/10, ankieta końcowa dnia 10; narzędzie `tools/playtest_merge.py`; plan testów `docs/research/R13-testy.md`; ręczny workflow wydania testowego v0.1.0 (instrukcja `docs/OWNER_CHECKLIST.md` krok 11); grafiki sklepu 10 języków; nowa zawartość (4 karty Endless, 2 zestawy tygodnia, 2 wydarzenia dnia); wydajność (bitwy ~8% szybciej, sfx bez budzenia); szlif wizualny (9 poprawek); 60 FPS poza bitwą; pierwsze 10 minut (6 poprawek); dźwięk (głośność LUFS, bez trzasków, barwy epok); **nowy tryb „Rozkazy sztabu”** (18 rozkazów w 2 partiach, cele: zniszcz/utrzymaj/eskorta/grot/dochód, medale jednorazowo 10/15/20 i 15/20/30, ranking Play Games, 4 osiągnięcia); pasek „Następny cel” w menu.

**Decyzje właściciela (obowiązujące, nowe 4–6.11):** konto Play FIRMOWE (bez wymogu 12 testerów × 14 dni); pierwsze wydanie v0.1.0, najpierw test wewnętrzny, testy BEZ serwera rankingu; kod testera w raporcie — tak; „pełne zasady od premiery” w wersjach testowych (RELEASE_DAY bez zmian); limit 60 FPS poza bitwą; Rozkazy sztabu dla testerów przed premierą, z rankingiem PGS; osiągnięcia Weteran (18 rozkazów) i Marszałek (54★); koordynator sam zatwierdza zrzuty i wypycha po zielonych testach. Wcześniejsze decyzje (niewidomi — koniec prac specjalnych; compactTop i celowanie ostrzału bez zmian; powiadomienia nigdy na wynikach; fokus audio) nadal obowiązują.

**Otwarte u właściciela:** dane do pierwszego AAB (adres na raporty testerów dla `-PtesterFeedback`, sekrety podpisu w GitHub, 89 ID Play Games, dane wydawcy — `docs/OWNER_CHECKLIST.md`); decyzja: budżet punktów PGS 1650 czy powrót do 1600; serwer rankingu na premierę (okresy przechowywania w `docs/reports/d38-serwer.md`).

**Zlecenia dla zewnętrznych modeli (czekają na wykonanie przez właściciela):** 166 audyt kodu, 167 tłumaczenia natywne, 168 marketing, 169 recenzja projektu, 170 research wrogów/osobowości AI, 171 research wariantów epok, paczka 172–201 (6 fal po 5). Gałęzie `gpt/NNN-*` — sprawdzaj `git branch -r | grep gpt/`, przeglądaj, testuj, scalaj.

**Kolejka na następną sesję:**
1. Scalanie wyników GPT (gdy się pojawią) — po każdej gałęzi testy; uwaga na `L.kt` (rezerwacje part212–215).
2. Po 170/171: propozycje do wyboru dla właściciela (AskUserQuestion), potem budowa nowych osobowości AI i wariantów epok (zmiana `:sim` → PROTOCOL, złote powtórki).
3. Gdy właściciel poda dane: wydanie testowe v0.1.0 (ręczny workflow `test-release`, `-PtesterFeedback`).
4. Drobne: `FirstHourImpression61Test.phonePl` bywa niestabilny (ziarno z `nanoTime`); `:app:updateScreenshots` psuje polskie znaki w powodzie (poprawiać `UPDATES.md` ręcznie).

**Środowisko — pułapki:** kontener często restartowany (zadania w tle giną); dwa Gradle naraz = OOM (zawsze flock); dysk: czyść `~/.gradle/caches/build-cache-1` i zakończone worktree, gdy wolne < 5 GB; nie uruchamiaj procesów z `&` (używaj run_in_background). `-P` ze spacjami bywa blokowane — używaj `ORG_GRADLE_PROJECT_nazwa=...`.

**Zaplanowane zadania tej sesji (WYŁĄCZONE przy zamknięciu):** `trig_01XahRKetEe53pBuzDCyR8zq` (:07), `trig_01Y3WGdM7Xqiwj2kbkDwuhYS` (:37). Nowa sesja tworzy własne.

## Stan na 5.11.2026 rano

- **`integ/tura10`** ma całą pracę nocy i poranka (d34–d48, R12, R13): etap 19 łagodniejszy
  (0.8.9), **K1 — etapy 28 i 30–33 łatwiejsze (PROTOCOL 0.8.10)**, Raport dla testera v2 (kod
  testera, awarie/ANR, mapa dni), przypomnienie w 3. i 10. dniu, `-PtesterFeedback`, późne
  rankingowe wyzwanie dnia = 10 medali, R5-hardening serwera (d38), napisy Foldu, tłumaczenia.
  Testy tylko obejściem `tools/offline-build/` (fastTest 764, screenshots, :sim 318, :server 93).
- **Czeka na Gradle** (sieć blokuje `dl.google.com`): `main` nadal **210f868**. Po odblokowaniu:
  pełne testy `--rerun`, `:sim:regenerateGolden` (usunąć zdublowaną linię powodu w `BALANCE.md`),
  lint, `assembleDebug`, wypchnięcie na `main` + `claude/bold-sagan-vwz5gm`, APK dla właściciela.
  Workflow `test-release` nie przekazuje jeszcze `-PtesterFeedback` — dopisać, gdy właściciel poda
  e-mail/formularz.
- **Decyzje właściciela 5.11:** konto Play **firmowe** (bez 12 testerów × 14 dni), K1 tak, kod
  testera tak (+ przypomnienie, zdanie w polityce), przerwane rankingowe wyzwanie dnia — medale
  jak za kapitulację. Dokumenty zaktualizowane (`docs/RELEASE.md`, `docs/OWNER_CHECKLIST.md`,
  `docs/research/R13-testy.md`).
- Teksty `L.kt`: zajęte do **part204**; **następna wolna: part205** (part202 nieużyta).
- Szczegóły i kolejka: góra `docs/COORDINATOR_HANDOVER.md`.

## Stan na koniec sesji 4.11.2026 (ok. 18:00 UTC)

**Gałęzie:** `main` = `claude/bold-sagan-vwz5gm` = `integ/tura10` (wszystko scalone; ostatnie pełne testy zielone — sprawdź `git log` i ewentualne niedokończone gałęzie niżej).
**Wersje:** PROTOCOL **0.8.11** (d58: paczka zawartości 206 — 4 karty Endless, 2 zestawy tygodnia, 2 wydarzenia dnia; wcześniej 0.8.10 = K1, etapy 28 i 30–33), `BattleSnapshot.VERSION` **21**, `Replay.VERSION` 7, **83 osiągnięcia / 89 ID Play Games** (6.11, d62: 5. ranking `leaderboard_orders` — Rozkazy sztabu; PROTOCOL 0.8.12, `BattleSnapshot.VERSION` 22, teksty part209; d65: 4 osiągnięcia Rozkazów, teksty part210, następna wolna part211) (arkusz i import: `docs/store/play_games_sheet.*`, `docs/store/pgs_import/`, `tools/pgs_sheet.py`, ikony: `PgsIconsTest`, stykówki w `docs/store/assets/pgs/`). Teksty `L.kt` (stan 5.11 — part206 zajęta przez d58, **następna wolna part207**): najwyższa część **part198**; następne wolne: **part199** (zajęte tylko gdy R11-04 dodał — nie dodał), part200 (ankieta testera), part201+ wolne. Liczba `putAll(part` = liczba `private fun part`. W tekstach Kotlin `%1$s` pisz `%1\$s`.

**Zrobione w sesji 3–4.11 (wszystko na main):** dni 4–9 (zegar pod minimapą na Foldzie, TalkBack w HUD bitwy, dostępność z ustawień telefonu i tempo ×½, stabilność na tanich telefonach, Raport dla testera, „Następny cel”, pomoc po 2 porażkach), poprawki z przeglądów R6/R10, tłumaczenia R7, ściąga właściciela `docs/OWNER_CHECKLIST.md`, arkusz/import Play Games, grafiki sklepu D7 (odświeżone po D6), D6 nowa zawartość (zewnętrzna sesja, PR 153), osiągnięcia pierwszej godziny + ikony, osiągnięcia po D6, korony za Trudny, tryb gry/oszczędzanie baterii, utracona bitwa po aktualizacji (komunikat + zwrot próby + medale), sygnały dźwiękowe i znak „baza atakowana”, przybliżenie pola bitwy na zewnętrznym Foldzie (flaga `BattleRenderer.NARROW_ZOOM`), powitanie po 3+ dniach przerwy (+30 medali), samouczek wraca po zabiciu procesu, kamienie milowe Endless, TalkBack na ekranach poza bitwą (mapa, tryby, Badania, Misje, listy), pętla testowa Play (`TestLoopActivity`, proces `:testloop`), „oddech” muzyki w bitwie (`MusicBreath`), etapy 8–12 krótsze (~9 min).

**W toku w chwili zamknięcia (sprawdź gałęzie, dokończ albo scal):**
- [SCALONE na integ/tura10 4.11 ~18:30 UTC; pełne testy po scaleniu uruchomione — jeśli `main` != `integ/tura10`, uruchom pełne testy i wypchnij] `claude/dzien-26-ankieta` — ankieta testera (tylko w wersjach testowych, flaga `TESTER_SURVEY`, part200) + drobne napisy Kodeksu na zewnętrznym Foldzie.
- [SCALONE na integ/tura10 4.11 wieczorem, PROTOCOL 0.8.8; pełne testy po scaleniu uruchomione — jeśli `main` != `integ/tura10`, uruchom pełne testy i wypchnij] `claude/dzien-27-etapy-13-18` — etapy 13–18 krótsze (cel 9,5–11 min, PROTOCOL 0.8.8, wpis w `docs/BALANCE.md`).
Jeśli gałąź ma raport/pełne testy w commitach — scal na `integ/tura10`, uruchom pełne testy, wypchnij. Jeśli niedokończona — zleć agentowi dokończenie od ostatniego commita (najpierw `git merge origin/integ/tura10`).

**Decyzje właściciela (obowiązujące, nowe z 3–4.11):**
- Fokus audio: muzyka gry nie przerywa cudzej muzyki. „Co nowego” od najnowszych, tytuł „Co nowego” z menu.
- Osiągnięcia zwykłe (bez paska postępu). „Sześć oblicz wojny” = dowolne 6 z 9 zestawów tygodnia.
- Powiadomienia nie na wynikach bitwy; zatwierdzony układ `compactTop` i celowanie ostrzału — nie zmieniać.
- **Niewidomi: koniec prac specjalnie dla niewidomych** (istniejący TalkBack zostaje; w sklepie nie obiecywać gry dla niewidomych).
- Etapy 8–12 i 13–18 krótsze; muzyka: oddech co ~2,5 min; ankieta tylko w testach; pętla testowa włączona.
- Polityka prywatności: zatwierdzone dopiski o raportach zawieszeń, dzienniku testu i ustawieniach dostępności w raporcie.
- `Daily.RELEASE_DAY` = 2027-12-01 — nie zmieniać bez decyzji.

**Ankieta testera:** w produkcji wyłączyć `-PtesterSurvey=false`, jeśli pierwsze wydanie produkcyjne ma numer 0.x (zob. `docs/RELEASE.md`). Drobne napisy < 10 sp na zewnętrznym Foldzie zostały: „Wymaga: Doktryna elit”, legenda porównania jednostek, zakładki przy 130%.

**Otwarte u właściciela:** dane wydawcy, konto Play (D-U-N-S, weryfikacja), podpis upload, polityka prywatności na stronie, testerzy (lista w `docs/OWNER_CHECKLIST.md`); serwer rankingu — 6 decyzji w `docs/research/R5-server.md` (rekomendacja: testy bez serwera, VPS w UE przy premierze; zlecenie `docs/tasks/R5-server-hardening.md`); Game Stats (Data safety).

**Uwaga:** `WelcomeBack193Test.noWelcomeAfterTwoDaysOrBeforeTutorial` bywa niestabilny w pełnym przebiegu (prawdziwy zegar) — do ustabilizowania (wstrzyknąć zegar). Etap 13 u profilu docelowego ma teraz medianę 8,2 min (bez skoku po etapie 12).

**Kolejka na następną sesję (propozycja, potwierdź z właścicielem):**
1. Dokończyć/scalić dwie gałęzie w toku (wyżej), APK dla właściciela.
2. Nowy research (R12): przegląd całości przed pierwszym AAB dla testerów zamkniętych — co jeszcze blokuje (pozostałe pozycje R11-backlog: 6 „Nowość na tym etapie”; Kodeks/Statystyki z TalkBack — NIE robić, decyzja o niewidomych), pomiar etapów 19+ (medale przychodzą ~1,35× szybciej po skróceniu), przegląd zrzutów po wszystkich zmianach.
3. Wydanie testowe: `docs/RELEASE.md`, release R8 (`assembleRelease` z syntetycznymi ID jak w CI), gdy właściciel poda dane.

**Środowisko — pułapki:** kontener jest często restartowany (zadania w tle giną — sprawdzaj gałęzie agentów po restarcie); dysk: czyść `~/.gradle/caches/build-cache-1` i `git worktree remove --force --force .claude/worktrees/agent-*` zakończonych agentów, gdy wolne < 5 GB; dwa Gradle naraz = OOM (zawsze flock); `RecordedMusicTest`/`WelcomeBack193Test` bywały niestabilne pod obciążeniem (pierwszy poprawiony). Nie uruchamiaj procesów z `&` w Bash — używaj `run_in_background`.

**Zaplanowane zadania poprzedniej sesji (WYŁĄCZONE przy zamknięciu):** `trig_019iEZV9uBTiJzAhkVqdaR1m` (:07) i `trig_012Lu7xBxbPkWWLNivKj4zSE` (:37) — budziły starą sesję. Nowa sesja tworzy własne.
