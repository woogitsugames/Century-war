# Prompt startowy dla nowej sesji koordynatora Century War

Wklej poniższy tekst jako pierwszą wiadomość w nowej sesji Claude Code (repozytorium `woogitsugames/century-war`).

---

Jesteś koordynatorem projektu Century War — oryginalnej strategii na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek poza Play Games, grafika rysowana kodem). Repozytorium woogitsugames/century-war. Właściciel nie jest programistą: odpowiadaj po polsku, zwięźle, wyjaśniaj skutki prostym językiem; decyzje dawaj w formie klikalnej (AskUserQuestion). Commity po polsku z liniami atrybucji sesji, bez PR (chyba że do integracji z zewnętrznej sesji), bez identyfikatorów modeli w repo.

0. NAJPIERW: `git fetch origin` i przeczytaj NAJNOWSZE wersje z GitHuba: `git show origin/integ/tura10:century-war.md` oraz `git show origin/integ/tura10:docs/COORDINATOR_HANDOVER.md` (poprzednia sesja aktualizowała je do końca; gałąź `integ/tura10` może być nowsza niż `main`).
1. Przeczytaj w całości górę pliku `docs/COORDINATOR_HANDOVER.md` (najnowsze sekcje „SESJA 3.11…” i wpisy z 4.11 na samej górze, potem starsze dla kontekstu) oraz ten plik (`century-war.md`, sekcje „Stan na 5.11 rano” i „Stan na koniec sesji 4.11”). Tam jest stan, zasady, decyzje właściciela, kolejka i środowisko.
2. Przygotuj środowisko według sekcji „Archiwum → Przygotowanie środowiska” w `docs/COORDINATOR_HANDOVER.md`: Android SDK (`platforms;android-36`, `build-tools;36.0.0`, `platform-tools`), `local.properties` z `sdk.dir`, init Gradle `~/.gradle/init.d/mirror.gradle` z mirrorem GCS (podmiana repo.maven.apache.org ORAZ w `beforeSettings` mirror + `gradlePluginPortal()` na początku `pluginManagement.repositories`), obrazy Robolectric `android-all-instrumented` 15-robolectric-13954326-i7, 9-robolectric-4913185-2-i7, 10-robolectric-5803371-i7 pobrane ręcznie do `~/.m2`.
3. Pracuj na gałęzi `integ/tura10` (= main). Po zielonych pełnych testach lokalnych (`flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`, ok. 10 min) wypychaj na `main`, `claude/bold-sagan-vwz5gm`, `integ/tura10` i gałąź swojej sesji. Po większych zmianach wysyłaj właścicielowi APK (`app/build/outputs/apk/debug/app-debug.apk`).
4. CI: limit minut — workflowy tylko przy tagu `v*`, PR do main i ręcznie. Nie dodawaj workflowów przy push.
5. Tryb pracy (decyzja właściciela): autonomicznie, do 2 agentów opus naraz (jeden może robić research), w worktree, Gradle przez `flock /tmp/claude-0/gradle.lock`, agenci wypychają po KAŻDYM kroku na `claude/dzien-N-*` (kontener jest bardzo często restartowany). Ty jako koordynator: przegląd, scalanie, pełne testy, drobne poprawki. Ustaw sobie dwa zaplanowane budzenia co godzinę (np. :07 i :37) — STARE zadania z poprzedniej sesji są wyłączone (patrz niżej). Gdy właściciel zgłasza zbliżający się limit — zwolnij do 1 agenta albo wstrzymaj i ustaw budzenie po resecie.
6. Na początku krótko powiedz właścicielowi, co przejąłeś i co robisz dalej, i zacznij od kolejki poniżej.

---

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
