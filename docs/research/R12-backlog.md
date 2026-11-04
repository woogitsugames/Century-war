# R12 — przegląd przed pierwszym AAB dla testerów zamkniętych (runda 12, 4.11.2026)

Baza: `integ/tura10` (3af9b299: scalone etapy 13–18, PROTOCOL 0.8.8, ankieta testera,
napisy Kodeksu na Foldzie). Dokument nie zmienia kodu gry. Gradle w tym kontenerze nie działa
(sieć blokuje dl.google.com), więc nic nie jest tu zmierzone na nowo. Liczby pochodzą
z `docs/BALANCE.md`, z kodu (`Campaign`, `Research`, `BattleScreen.settleRewards`, `Missions`,
`LostBattle`, `Daily`/`Weekly`, `ReleaseChecklist`) i z prostego modelu w pythonie
(wzory przepisane 1:1 z kodu, opis niżej).

**Obowiązujące decyzje (nie proponuję zmian):** koniec prac specjalnie dla niewidomych
(istniejący TalkBack zostaje), układ `compactTop` i celowanie ostrzału bez zmian, powiadomienia
nigdy na wynikach bitwy, `Daily.RELEASE_DAY` = 2027-12-01 bez zmian, CI tylko przy tagu, PR
i ręcznie.

**Nie dubluje:** R11-01…04 i R11 poz. 5, 7, 8 (zrobione), R5 (serwer, czeka na właściciela),
Kodeks/Statystyki z TalkBack (decyzja: nie robić).

Rozmiar: S ≤ pół dnia jednego programisty, M ≈ 1 dzień, L > 1 dzień. TOP 8 to razem ok. 5 dni
pracy jednej osoby, czyli ok. 2,5–3 dni dla dwóch. Teksty: wolne części od **`part201`**
(najwyższa zajęta to `part200`; 195, 197 i 199 też są wolne, ale lepiej ich nie używać, żeby nie
mylić z dawnymi rezerwacjami).

## Najważniejsze wnioski

- **Od strony kodu do testów prawie nic nie brakuje, ale nie da się dziś zbudować AAB z CI.**
  Zadanie `release` w `android.yml` (tylko tag `v*`) wymaga `CW_SERVER_URL` i `CW_HMAC_KEY`
  i nie pomija żadnej kontroli. `releaseChecklist` blokuje też `Daily.RELEASE_DAY` dalej niż
  60 dni naprzód (dziś 392 dni). Przy testach bez serwera i niezmienionym `RELEASE_DAY` tag
  **zawsze się wywróci**. Trzeba osobnej, ręcznej ścieżki „wydanie testowe” z jawnym
  `-PskipCheck=releaseDay,serverUrl,hmacKey` (pozycja 2). Bez kluczy właściciela (podpis upload,
  84 ID Play Games, 5 pól wydawcy, adres polityki) nie zbuduje się nic: wyjątku `playGames` nie
  ma dla generatora zasobów release, a `ownerInput` można pominąć tylko w smoke-teście CI.
- **Testerzy grają wyzwanie dnia i oblężenie tygodnia na zasadach sprzed premiery.** Przed
  `RELEASE_DAY` `Daily.config` używa `legacyConfig`: bez wydarzeń dnia, bez pogody, bez
  dowódcy (`Battle.commanderAvailable` dla DAILY wymaga `seed >= RELEASE_DAY`), z dawną pulą
  modyfikatorów i epokami 0–7. `Weekly` bez zestawów tematycznych (pierwszy w tygodniu premiery
  + 3). Zawartość D6 (zestawy tygodnia, wydarzenia dnia) jest więc w testach niewidoczna,
  a osiągnięcia „dziewięć różnych wydarzeń” i „Sześć oblicz wojny” są nie do zdobycia.
  Scenariusz testera (krok 12) i opis sklepu tego nie mówią. `RELEASE_DAY` zostaje, więc
  rozwiązaniem jest oznaczenie i komunikacja (pozycja 5, pytanie 3).
- **Ekonomia badań się nie rozjeżdża, ale za etapem 18 jest nowy klif.** Nagroda za wygraną
  w kampanii nie zależy od czasu bitwy (`20 + 6·etap + 5·★ + 12·nowe ★`), więc na etap gracz
  dostaje tyle samo medali co przed skróceniem: +1,6% do etapu 19 dzięki wyższym ★ w 8–18.
  Medale zależne od czasu (logowanie, misje, wyzwanie dnia) rozkładają się na mniej dni, więc
  realny gracz dochodzi do etapu 19 z ok. 5% **mniejszą** liczbą medali (ok. 39 zamiast 41
  poziomów badań). Problemem nie jest więc inflacja, tylko krok trudności: `aiPower` etapu 18 to
  0,996, a etapu 19 1,612 (+62%: początek rampy drugiej połowy i korekta 1,265). Profil
  „medale” ma na etapie 18 81% wygranych i 10,4 min, a na etapie 19 49%, 13,5 min i oczekiwany
  czas przejścia **26,6 min** (2× dłużej). To ten sam klif, który R11 znalazł na etapie 8,
  przesunięty o 11 etapów (pozycja 1).
- **Obecny pomiar nie pokaże skutku skrócenia na etapach 19+.** Profile `BalanceReport` liczą
  badania z numeru etapu, nie z czasu gry („medale” zakłada stałe 2 ★ i zero medali spoza
  kampanii), więc etapy 19+ wychodzą identycznie przed i po (w `BALANCE.md` to kontrola
  determinizmu). Trzeba profilu, który uwzględnia czas (pozycja 1, pomiar M2).
- **Teksty sklepu i dokumenty wydania są z 3.11.** „Co nowego” w 10 plikach `listing_*.md`
  obiecuje „TalkBack… (bitwa w przygotowaniu)”, a kontrolna linia każe dopisać „pełną obsługę
  bitwy”, co przeczy decyzji o niewidomych. W `RELEASE.md` jest „W toku: dzień 5”, A156-13
  „czeka na gałęzi gpt/163” (scalone), a krok 21 scenariusza obiecuje bitwę z TalkBack „w następnej
  wersji”. `GDD.md` podaje presję „po 8 minutach”. Kadr 05 grafik sklepu (etap 11, 7:45) był
  wyeksportowany przed R11-02, a od tego czasu na etapie 11 presja startuje w 5:00 (pozycje 3 i 4).

---

## 1. Co jeszcze blokuje wydanie testowe

### Po stronie właściciela (twarde blokady, bez nich nie ma AAB)

| Co | Gdzie w kodzie blokuje | Uwagi |
|---|---|---|
| Konto Play Console (organizacja, D-U-N-S, weryfikacja) | — (Play) | najdłuższe, do 30 dni |
| Klucz upload + 4 sekrety w GitHub | `release` w `android.yml`, `releaseChecklist` (niepomijalne) | |
| 84 ID Play Games w `docs/store/play_games_ids.properties` | generator `GeneratePlayGamesIdsTask` (release) | `-PskipCheck=playGames` **nie** wyłącza generatora |
| 5 pól wydawcy w `docs/store/OWNER_INPUT.md` (w tym `release_date`) | `ownerInputProblems` | pomijalne tylko w smoke-teście CI |
| Publiczny adres polityki (`CW_PRIVACY_URL`) | `releaseChecklist` | da się pominąć, ale do testów zamkniętych Play i tak wymaga polityki |
| Decyzja „testy bez serwera” (R5) | `serverUrl`, `hmacKey` | wtedy wydanie testowe z jawnym `skipCheck` (pozycja 2) |
| Deklaracje: Data safety, IARC, grupa wiekowa, karta sklepu | — (Play) | odpowiedzi gotowe w `docs/store/` |
| Lista testerów (≥ 12 osób) | — (Play) | |

### Po stronie technicznej

| Co | Stan | Pozycja |
|---|---|---|
| Ścieżka zbudowania testowego AAB w CI (bez serwera, `RELEASE_DAY` poza oknem 60 dni) | **brak**: tag zawsze się wywróci | 2 |
| Próba release po zmianach 3–4.11 (R8: pętla testowa w procesie `:testloop`, ankieta, nowe pola profilu i `playtest.json`, `MusicBreath`) | ostatnia próba 3.11 rano | 2 |
| Ewentualna korekta etapu 19 przed AAB (zmienia PROTOCOL, potem unieważnia powtórki testerów) | niezmierzone | 1 |
| Grafiki sklepu zgodne z wydaniem (kadr 05 przed R11-02, część kadrów sprzed kamieni milowych Endless) | do ponownego eksportu | 4 |
| Teksty sklepu („Co nowego”, dostępność) i scenariusz testera | nieaktualne | 3 |
| Niestabilne testy (`WelcomeBack193Test` z prawdziwym zegarem) — tag `v*` wymaga zielonych `fast-tests` | znane | 6 |
| `releaseVersionCode=1`, `last_version_code.txt=0` | OK dla pierwszego AAB | — |
| 16 KB, targetSdk 36 | OK (bez `.so`) | — |

---

## 2. Balans etapów 19+ po skróceniu etapów 2–18

### Co się zmieniło w czasie

Suma oczekiwanych czasów przejścia (profil docelowy, `BALANCE.md`) plus 1,5 min przerwy po
bitwie:

| Odcinek | Przed dniem 1 | Teraz (PROTOCOL 0.8.8) |
|---|---:|---:|
| etap 1 | 15,9 | 15,9 |
| etapy 2–7 | 90,5 | 54,4 |
| etapy 8–12 | 87,9 | 64,3 |
| etapy 13–18 | 113,4 | 93,4 |
| przerwy (17 × 1,5) | 25,5 | 25,5 |
| **do etapu 19** | **ok. 333 min (5,6 h)** | **ok. 254 min (4,2 h)** |

Cała droga do etapu 19 jest ok. **1,31×** krótsza (2–7: 1,66×, 8–12: 1,37×, 13–18: 1,21×).
Liczba „1,35×” z R11-02 dotyczy tylko etapów 8–12.

### Skąd są medale (z kodu)

- **Wygrana w kampanii** (`BattleScreen.settleRewards`): `(20 + 6·etap + 5·★ + 12·max(0, ★ − stare ★)) × mnożnik poziomu`
  (Łatwy 0,75, Normalny 1, Trudny 1,3). **Nie zależy od czasu bitwy.**
- **Porażka** (`LostBattle.lossReward`): `(5 + 2·etap)·min(1, czas/240 s) + min(20, zabici/8)`.
  Pełna po 4 min, więc skrócenie prawie jej nie zmienia (trochę mniej zabitych).
- **Zależne od czasu gry:** logowanie 5–40 + gwiazdki/16 dziennie (średnio ok. 22), 3 misje dnia
  po `10 + 2·bonus + ★/12` (ok. 48 przy komplecie), 3 tygodniowe po `34 + 4·bonus + ★/3`
  (ok. 23 na dzień), rankingowe wyzwanie dnia `min(90, wynik/350) + 10` (ok. 50).
  Razem ok. **140 medali dziennie** u aktywnego gracza. Do tego jednorazowe osiągnięcia.
- **Badania** (`Research.defs`): 110 poziomów, łącznie 44 789 medali; najtańszy następny poziom
  przy etapie 19 kosztuje ok. 150–200.

### Model (python, wzory z kodu; plik roboczy poza repo)

Kampania jak w `BalanceReport.medalLevels` (pierwsza wygrana na 2 ★, od etapu 7 jedna porażka,
zakup najtańszego poziomu), plus zmiana średnich ★ z `BALANCE.md` (2–7: −0,31 ★, 8–12: +0,44 ★,
13–18: +0,31 ★; jedna ★ przy pierwszej wygranej = 5 + 12 medali), plus 140 medali dziennie przy
45 min gry dziennie.

| Do etapu 19 | Przed skróceniem | Teraz |
|---|---:|---:|
| medale z kampanii | 2 346 | 2 383 (+1,6%) |
| dni gry (45 min/dzień) | 7,4 | 5,6 |
| medale zależne od czasu | ok. 1 036 | ok. 790 |
| **razem** | **ok. 3 380** | **ok. 3 170 (−6%)** |
| poziomy badań (z 110) | 41 | 39 |
| medale na godzinę gry | ok. 610 | ok. 750 (+23%) |

**Wnioski:**
1. **Na etap ekonomia się nie rozjeżdża.** Gracz przychodzi na etap 19 z ok. 2 poziomami badań
   mniej (−5%), nie więcej. Medale „szybciej” znaczą „szybciej na godzinę”, a nie „więcej
   na etap”. Etapy 19+ są strojone pod profil 40% badań (44 poziomy). Realny gracz ma ok. 36%
   (39 poziomów), profil „medale” ok. 30% (33 poziomy).
2. **Klif 18 → 19 jest realny dla gracza bez farmy.** `aiPower` 0,996 → 1,612. Profil „medale”:
   etap 18 81% / 10,4 min, etap 19 49% / 13,5 min / oczekiwany czas 26,6 min. Profil docelowy
   (przeskok z 10% na 40% badań) ma 60% / 10,1 min, więc pomiar „docelowy” klifu nie widzi.
3. **Farma powtórek opłaca się bardziej.** Powtórka wygranego etapu 18 (bez nowych ★) daje
   20 + 102 + 10 = 132 medale za ok. 10,4 min, czyli ok. 12,7 medala/min. Wcześniej przy
   13,4 min było to ok. 9,9/min. Etap 19 daje ok. 10,2/min, a Endless ok. 6/min. Gracz
   zatrzymany na 19 ma więc dobry wentyl: 2–3 powtórki etapów 13–18 dają poziom badań.
   Nic tu nie psuje gry, ale trzeba to wpisać do opisu balansu: najlepszą farmą są teraz
   etapy 13–18, nie Endless.
4. Misja `MINUTES` (12 min dziennie, 100 tygodniowo) wymaga teraz ok. 1,3× więcej bitew.
   To drobiazg.

### Pomiar do uruchomienia później

Wszystko przez `flock /tmp/claude-0/gradle.lock`, z `--rerun` (zmienne środowiskowe nie są
wejściem testu), wyniki w `app/build/balance/`.

**M1. Klif 18 → 19 na obecnych profilach (bez zmian w kodzie, ok. 30–40 min):**

```bash
for P in medale target; do
  BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=$P BALANCE_STAGE_FROM=16 BALANCE_STAGE_TO=24 \
  BALANCE_TAG=r12-$P flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun \
    --tests '*BalanceReport.campaignReport*' --console=plain --no-configuration-cache
done
BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=target BALANCE_COMMANDER=on BALANCE_STAGE_FROM=16 \
BALANCE_STAGE_TO=24 BALANCE_TAG=r12-target-cmd flock /tmp/claude-0/gradle.lock ./gradlew \
  :app:testDebugUnitTest --rerun --tests '*BalanceReport.campaignReport*' --console=plain --no-configuration-cache
```

Odczyt: kolumny `expected` i `stars` dla 16–24. Klif potwierdzony, jeśli oczekiwany czas
etapu 19 > 2× etapu 18 albo wygrane „medali” spadają o > 25 pp.

**M2. Profil „medale-czas” (mała zmiana kodu testu, bez gry):** w `BalanceReport.medalLevels`
dodać wariant `BALANCE_PROFILE=medale-czas`. Medale kampanii liczy z rzeczywistych średnich ★
(z tabel `BALANCE.md` albo z bieżącego przebiegu), a do tego dolicza
`BALANCE_DAILY_MEDALS` (domyślnie 140) × liczba dni = suma oczekiwanych czasów etapów przed
danym etapem / `BALANCE_MINUTES_PER_DAY` (domyślnie 45). Tabela czasów „przed” (PROTOCOL 0.8.4)
i „teraz” (0.8.8) jako stałe w teście, wybór przez `BALANCE_PACE=before|now`. Potem:

```bash
for PACE in before now; do
  BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=medale-czas BALANCE_PACE=$PACE \
  BALANCE_STAGE_FROM=19 BALANCE_STAGE_TO=30 BALANCE_TAG=r12-czas-$PACE \
  flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun \
    --tests '*BalanceReport.campaignReport*' --console=plain --no-configuration-cache
done
```

Odczyt: różnica wygranych 19–30 „before” vs „now”. Model przewiduje −1…−3 pp (2 poziomy badań
mniej). Ponad −5 pp na którymś etapie oznacza, że trzeba go lekko złagodzić.

**M3. Warianty korekty etapu 19 (tylko jeśli M1 potwierdzi klif):**

```bash
for S in 0.94 0.90 0.86; do
  BALANCE_RUN=true BALANCE_RUNS=100 BALANCE_PROFILE=medale BALANCE_STAGES=19 BALANCE_POWER_SCALE=$S \
  BALANCE_TAG=r12-19-$S flock /tmp/claude-0/gradle.lock ./gradlew :app:testDebugUnitTest --rerun \
    --tests '*BalanceReport.campaignReport*' --console=plain --no-configuration-cache
done
```

Cel: „medale” na etapie 19 ≥ 60%, oczekiwany czas ≤ 1,6× etapu 18, profil docelowy ≤ 75%
(dziś 60%). To samo z `BALANCE_PROFILE=target` i `BALANCE_COMMANDER=on`.

---

## 3. Spójność tekstów i UX po ostatnich zmianach (z kodu)

| # | Co | Gdzie | Waga |
|---|---|---|---|
| T1 | „Co nowego” w 10 językach: „TalkBack w menu i ustawieniach (bitwa w przygotowaniu)” to obietnica sprzeczna z decyzją. Nie ma krótszych etapów 8–18, kamieni milowych Endless, oddechu muzyki ani powitania po przerwie. „ok. 8 min” dotyczy tylko 2–7 | `docs/store/listing_*.md` | wysoka |
| T2 | Linia kontrolna „po scaleniu dnia 5 zmień na pełną obsługę bitwy” przeczy decyzji o niewidomych | `listing_*.md` („Kontrola przed publikacją”) | wysoka |
| T3 | „Kandydat do testów zamkniętych”: stan z 3.11, „W toku: dzień 5”, A156-13 „czeka na gpt/163”, brak R11 i dni 17–27 w „Zrobione” | `docs/RELEASE.md` | średnia |
| T4 | Scenariusz testera: krok 4 (7–9 min, OK), krok 6 bez długości etapów 8–18, krok 11 bez kamieni milowych, krok 12 nie mówi o zasadach sprzed premiery, krok 21 obiecuje bitwę z TalkBack „w następnej wersji”. Brakuje ankiety (pojawia się sama) i prośby o raport w 3. i 10. dniu (jest tylko w `OWNER_CHECKLIST.md`) | `docs/RELEASE.md` | średnia |
| T5 | Presja „po 8 minutach, +20%/min” — w etapach 2–18 jest 4/5/6 min i +80/50/40%/min | `docs/GDD.md:60` | niska |
| T6 | Komentarz „[SIEGE_RAMP] poza etapami 2–12” (jest 2–18) | `sim/.../Battle.kt` przy `siegeRamp` | niska (komentarz) |
| T7 | Kadr 05 sklepu: opis „7:45… przed presją oblężniczą (8:00)” na etapie 11, a presja startuje tam w 5:00. Eksport z 4.11 08:26 jest sprzed R11-02 (16:29) | `StoreGraphicsTest` (`D7_RESULT_SECONDS`), `docs/store/assets/D7/` | średnia (zrzut musi odpowiadać wydaniu) |
| T8 | Wyzwanie dnia w testach: bez dowódcy, pogody i wydarzeń. Osiągnięcia wydarzeń i zestawów tygodnia nie do zdobycia przed `RELEASE_DAY`, a ekran osiągnięć tego nie mówi | `Daily.config`, `Battle.commanderAvailable`, `Weekly.ruleSet`, `AchievementsScreen` | średnia |
| T9 | Drobne napisy < 10 sp na zewnętrznym Foldzie: „Wymaga: Doktryna elit”, legenda porównania jednostek, zakładki przy 130% | Badania / Kodeks | niska |
| T10 | „Co nowego” w grze (linia 0.1) kończy się na `wn_185`. Pierwsze AAB trafi do nowych graczy (`shouldShow` tylko dla istniejącego profilu), więc nikt tego automatycznie nie zobaczy. Przycisk w menu pokazuje jednak listę bez skrócenia etapów, Endless i powitania | `WhatsNewCatalog`, `L.kt` | niska (opcjonalnie w pozycji 3) |

Sprawdzone i w porządku: dymek i wskazówka presji używają `battle.siegeStart`
(`HintOverlay`, `BattleAlerts`, `tip_siege`), a teksty `hint_siege`/`tip_siege` są bez liczby
minut. Ankieta ma okres ciszy i nie pojawia się nad modalem (`MenuScreen.updateSurvey`,
`blocksNotices`). `L.kt`: 75 `putAll(part` = 75 `private fun part`. Liczba osiągnięć w opisie
(79) zgadza się z grą.

---

## TOP 8

### 1. Klif 18 → 19 i ekonomia etapów 19+ — pomiar i ewentualna korekta
- **Cel:** uruchomić M1 i M3 (sekcja 2) oraz dopisać profil `medale-czas` (M2, tylko kod testu).
  Jeśli klif się potwierdzi: łagodniejszy etap 19 (`stageRamp` 1,265 → wartość z M3, np. ok. 1,15),
  ewentualnie etap 20 dla wyrównania krzywej, i PROTOCOL 0.8.8 → 0.8.9 z regeneracją złotych
  powtórek. Wpis w `BALANCE.md`: tabela przed/po, opis ekonomii (medale na etap vs na godzinę,
  farma etapów 13–18).
- **Dla gracza:** po 4 godzinach gry etap 19 nie jest nagle dwa razy dłuższy i dwa razy
  trudniejszy. Mniej testerów odpada w 5.–6. dniu testu, a Google ocenia właśnie zaangażowanie.
- **Pliki:** `app/src/test/.../game/BalanceReport.kt` (profil `medale-czas`, `BALANCE_PACE`),
  `sim/.../Modes.kt` (`Campaign.stageRamp`), `sim/.../SimVersion.kt`, złote powtórki,
  `SecondHourControlTest` (kontrola determinizmu jest na etapie 19 — przenieść na 21),
  `docs/BALANCE.md`.
- **Rozmiar:** M. **Ryzyko:** średnie. Zmiana `:sim` oznacza PROTOCOL i powtórki. Etap 19
  nie może zrobić się łatwiejszy niż 18 dla profilu docelowego (dziś 60%, górna granica 75%).
- **Zależności:** pytanie 4 (zgoda na korektę). Zrobić **przed** pierwszym AAB, bo potem
  zmiana PROTOCOL unieważnia powtórki rankingowe testerów.

### 2. Ścieżka wydania testowego: ręczny workflow i próba release na sucho
- **Cel:** (a) w `android.yml` ręczne uruchomienie (`workflow_dispatch` z wejściem
  `test_release: true`), które buduje podpisany AAB i APK z jawnym
  `-PskipCheck=releaseDay,serverUrl,hmacKey`, z `TESTER_SURVEY=true` i z artefaktem na 90 dni.
  Tag `v*` dalej nie pomija niczego (produkcja). Bez nowych wyzwalaczy przy push.
  (b) Lokalna próba jak 3.11 (syntetyczne ID, podpis debug tylko do próby): R8 i
  `shrinkResources`, kontrola pól zapisu po R8 dla nowych danych (stan ankiety w `playtest.json`,
  pola powitania, kamieni milowych i utraconej bitwy w profilu), uruchomienie `TestLoopActivity`
  w procesie `:testloop` w buildzie release (`tools/r8-sim-smoke` + intencja `TEST_LOOP` przez
  `adb`, jeśli będzie telefon), rozmiar AAB, brak `.so`. Wynik w `RELEASE.md`.
- **Dla gracza:** pierwszy AAB da się zbudować jednym kliknięciem, gdy właściciel wpisze
  sekrety. Nie trzeba ruszać `RELEASE_DAY` ani stawiać serwera.
- **Pliki:** `.github/workflows/android.yml` (zadanie `release`), `docs/RELEASE.md`
  („Kiedy wysyłamy testerom”, „Automatyczna blokada Gradle”), `docs/CI.md`,
  ewentualnie `app/proguard-rules.pro`.
- **Rozmiar:** M. **Ryzyko:** średnie. Sekrety podpisu: ścieżka testowa nie może dopuścić
  podpisu debug ani pominąć `ownerInput`. Trzeba sprawdzić, że `skipCheck` jest tylko
  w gałęzi testowej workflow.
- **Zależności:** pytanie 2 (testy bez serwera). Do faktycznego AAB: sekrety, 84 ID PGS,
  `OWNER_INPUT.md`, adres polityki.

### 3. Teksty sklepu i dokumenty wydania po 4.11
- **Cel:** poprawić T1–T6 (i opcjonalnie T10). „Co nowego” (≤ 500 znaków, 10 języków): wódz,
  stopnie wież, muzyka, krótsze bitwy kampanii (ok. 8–11 min), cele w Oblężeniu bez końca,
  premia za powrót, poprawki Folda. W linii dostępności tylko to, co jest: palety, kontrast,
  większy tekst, mniej błysków, tempo ×½, czytnik ekranu w menu. Bez „w przygotowaniu”.
  Usunąć linię kontrolną o „pełnej obsłudze bitwy”. W `RELEASE.md` odświeżyć sekcję
  „Kandydat”, scenariusz testera (kroki 6, 11, 12, 21, dopisać ankietę i raport w 3./10. dniu)
  i datę. Poprawić `GDD.md` (presja) i komentarz `Battle.siegeRamp`.
- **Dla gracza:** opis sklepu i notatka testowa mówią prawdę. Tester wie, czego się spodziewać,
  i nie zgłasza „błędów”, które są zasadą (wyzwanie dnia bez dowódcy przed premierą).
- **Pliki:** `docs/store/listing_{pl,en,de,es,fr,it,nl,pt,tr,uk}.md`, `docs/RELEASE.md`,
  `docs/GDD.md`, `sim/.../Battle.kt` (tylko komentarz), opcjonalnie `WhatsNewCatalog.kt` + `L.kt`
  `part201`.
- **Rozmiar:** S. **Ryzyko:** niskie. Same teksty. Pilnować limitów znaków Play
  („Co nowego” ≤ 500 znaków na język; nie ma testu długości, policzyć skryptem).
- **Zależności:** po pozycji 1 (jeśli zmieni czasy etapów 19+, nie wpisywać liczb dla 19+).
  Zdanie o wyzwaniu dnia zależy od pytania 3.

### 4. Grafiki sklepu D7 i przegląd zrzutów po wszystkich zmianach
- **Cel:** kadr 05 przenieść na moment przed presją etapu 11 (np. 4:30) albo na etap bez
  skrócenia, poprawić komentarz `D7_RESULT_SECONDS`, wyeksportować `generateD7` w 10 językach
  i przejrzeć stykówki. Pełny przegląd zrzutów (telefon 720p i 2400×1080, Fold zewnętrzny
  904×2316 i wewnętrzny, pl/en/de) po dniach 17–27: wstęga kamieni milowych Endless, ankieta
  w menu, powitanie po przerwie, okno „Gra zaktualizowana”, mapa kampanii po etapach 8–18.
  Raport `docs/reports/r12-zrzuty.md` z listą usterek.
- **Dla gracza:** zrzuty w sklepie pokazują grę, którą dostanie. Przegląd łapie kolizje nowych
  elementów (wstęga, ankieta, powitanie), zanim zobaczą je testerzy.
- **Pliki:** `app/src/test/.../screens/StoreGraphicsTest.kt`, `docs/store/assets/D7/**`,
  `docs/store/STORE_GRAPHICS.md`, testy zrzutów (`ScreenshotTest`, `FirstHourJourney*`),
  raport.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Nie ruszać `compactTop`. Poprawki układu tylko
  z listy usterek, osobnym commitem.
- **Zależności:** po pozycji 1 (jeśli zmieni etap 19+, a kadr pokazuje późną kampanię) i po 3
  (podpisy kadrów).

### 5. Zasady sprzed premiery w wersjach testowych: oznaczenia zamiast zmian
- **Cel:** w wersjach testowych (`BuildConfig.TESTER_SURVEY` albo osobna flaga z tego samego
  warunku 0.x) i tylko przed `RELEASE_DAY`: na ekranie osiągnięć dwa osiągnięcia (wydarzenia dnia,
  zestawy tygodnia) z dopiskiem „Dostępne od premiery”, a w zakładce wyzwania dnia i tygodnia
  jedna linia „Wersja testowa: pełne zasady wyzwań od premiery”. `RELEASE_DAY` i `:sim` bez zmian.
  Bez TalkBack (zostaje istniejący opis kontrolki, nic nowego).
- **Dla gracza (testera):** nie poluje na osiągnięcia, których nie da się zdobyć, i nie zgłasza
  braku dowódcy w wyzwaniu dnia jako błędu.
- **Pliki:** `screens/AchievementsScreen.kt`, `screens/ModeScreen.kt`, nowa czysta funkcja
  (np. `meta/PreReleaseRules.kt` + test), `L.kt` `part202`.
- **Rozmiar:** S. **Ryzyko:** niskie. Tylko UI. Napis nie może zniknąć z zapisem profilu ani
  pojawić się w produkcji (test na `versionName` 1.0.0).
- **Zależności:** pytanie 3 (jeśli właściciel woli tylko informację w scenariuszu, pozycja
  spada i zostaje zdanie w pozycji 3).

### 6. Stabilne testy przed tagiem
- **Cel:** wstrzyknąć zegar do `WelcomeBack193Test` (i do logiki, która czyta
  `System.currentTimeMillis()` w `WelcomeBack`/`Game.checkWelcomeBack`, przez parametr
  z domyślną wartością). Przejrzeć inne testy z prawdziwym zegarem (`TesterSurvey.due` w 3. dniu,
  `Missions` z `nowMillis`, `DailyReminder*`). 3× pełne testy z rzędu bez czerwieni, czas
  w `TEST_SPEED.md`.
- **Dla gracza:** pośrednio. Zadanie `release` na tagu wymaga zielonych `fast-tests` i zrzutów,
  więc losowa czerwień blokuje wydanie testowe i kosztuje minuty CI.
- **Pliki:** `meta/WelcomeBack.kt`, `engine/Game.kt` (tylko sygnatura), `WelcomeBack193Test`,
  ewentualnie `TesterSurvey*Test`, `docs/TEST_SPEED.md`.
- **Rozmiar:** S. **Ryzyko:** niskie. Bez zmiany zachowania gry.
- **Zależności:** brak. Najlepiej przed pozycją 2.

### 7. „Nowość na tym etapie” na mapie i w panelu etapu (R11 poz. 6)
- **Cel:** jak w R11: wiersz „Nowość” w `CampaignScreen.drawInfo` przy pierwszym wystąpieniu
  nowej epoki wroga, modyfikatora, pogody albo dowódcy (etap 8), plus mała iskra na węźle do
  pierwszej wygranej. Dodatkowo na etapie 19: „Silniejszy przeciwnik — warto zajrzeć do Badań”
  (stała reguła: pierwszy etap drugiej połowy kampanii), jeśli pozycja 1 nie usunie klifu
  całkiem. Statycznie z `Campaign.stages`, bez `:sim`. Bez prac TalkBack.
- **Dla gracza:** w środku kampanii widzi, że co 1–3 etapy czeka coś nowego, a przed etapem 19
  dostaje uczciwe ostrzeżenie zamiast nagłej serii porażek.
- **Pliki:** nowy `meta/StageNovelty.kt` (czysta logika + test), `screens/CampaignScreen.kt`
  (`drawInfo`, `drawNode`), `L.kt` `part203`.
- **Rozmiar:** S–M. **Ryzyko:** niskie. Na niskim panelu 720p wiersz dzieli linię
  z gwiazdkami jak korona R10-02.
- **Zależności:** po pozycji 1 (czy etap 19 dostaje ostrzeżenie).

### 8. Ostatnie drobne napisy na zewnętrznym Foldzie (T9)
- **Cel:** „Wymaga: Doktryna elit” w Badaniach, legenda porównania jednostek i zakładki Kodeksu
  przy tekście 130% — minimum 10 sp albo skrót z pełnym tekstem po dotknięciu, znak przewijania
  (`ScrollCue`) przy zakładkach. Test napisów w 10 językach jak w dniu 26.
- **Dla gracza:** na wąskim ekranie i z większym tekstem wszystko da się przeczytać.
- **Pliki:** `screens/ResearchScreen.kt`, `screens/CodexComparePanel.kt`,
  `screens/CodexScreen.kt`, testy `PrereleasePolish*`/`Day2UxTest`.
- **Rozmiar:** S. **Ryzyko:** niskie. Pasek bitwy (`compactTop`) bez zmian.
- **Zależności:** najlepiej przed pozycją 4 (zrzuty raz).

---

## Kolejność dla 1–2 programistów (≈ 2,5–3 dni)

| Dzień | Programista A | Programista B |
|---|---|---|
| 1 | pozycja 1: M1 + M2 (M), decyzja, ewentualnie M3 | pozycja 6 (S), potem pozycja 8 (S) |
| 2 | pozycja 1: korekta + PROTOCOL + powtórki (jeśli trzeba) | pozycja 2 (M) |
| 3 | pozycja 7 (S–M) | pozycja 3 (S), potem 5 (S), na końcu 4 (S–M) |

Konflikty: `L.kt` (część 201 = pozycja 3, 202 = pozycja 5, 203 = pozycja 7; po scaleniu liczba
`putAll(part` = liczba `private fun part`), `docs/RELEASE.md` (pozycje 2, 3, 5 — najpierw 3, inni
dopisują tylko swoje sekcje), `CampaignScreen.kt` (tylko pozycja 7), `StoreGraphicsTest.kt`
(tylko pozycja 4), `Modes.kt` (tylko pozycja 1; scalać osobno, z regeneracją złotych powtórek).
Najbezpieczniej: 6 → 1 → 8 → 2 → 3 → 5 → 7 → 4 (zrzuty na końcu, po wszystkich zmianach UI).

## Pytania do właściciela

1. **Data premiery do `OWNER_INPUT.md`.** Kontrola wydania wymaga daty premiery już przy
   pierwszym AAB testowym (trafia do polityki prywatności). To **nie** zmienia
   `Daily.RELEASE_DAY`. (a) Podaj planowaną datę, nawet przybliżoną, zmienimy ją przed premierą
   (rekomendacja: polityka i tak będzie aktualizowana); (b) czekamy z AAB, aż data będzie pewna.
2. **Testy bez serwera rankingu?** (a) Tak: pierwsze AAB bez rankingu globalnego, rankingi
   tylko z Play Games, osobny przycisk „wydanie testowe” w GitHub z jawnym wyjątkiem
   (rekomendacja: oszczędza 5–7 EUR/mies. i tydzień pracy); (b) najpierw serwer (R5, 6 decyzji),
   potem AAB.
3. **Testerzy zobaczą wyzwanie dnia i tygodnia w starej wersji.** Przez stałą datę premiery
   (2027-12-01, nie zmieniamy) wyzwanie dnia w testach nie ma dowódcy, pogody ani wydarzeń dnia,
   a oblężenie tygodnia nie ma nowych zestawów. Dwa osiągnięcia są nie do zdobycia.
   (a) Zostawić zasady, w grze napisać „pełne zasady wyzwań od premiery”, a dwa osiągnięcia
   oznaczyć „od premiery” (rekomendacja: zero ryzyka dla rankingów i powtórek); (b) tylko
   zdanie w scenariuszu testera, bez zmian w grze; (c) nic nie robić.
4. **Etap 19 może być ścianą.** Po skróceniu etapów 2–18 gracz dochodzi do etapu 19 po ok. 4 h,
   a ten etap jest wyraźnie mocniejszy (u typowego gracza ok. 50% wygranych i dwa razy dłuższe
   przejście niż etap 18). (a) Najpierw pomiar, a jeśli potwierdzi ścianę, lekko osłabić etap 19
   przed pierwszym AAB (rekomendacja: jedna liczba, potem zmiana unieważniłaby powtórki
   testerów); (b) skrócić też bitwy etapów 19–27 jak 13–18 (większa zmiana, ok. 1 dzień więcej);
   (c) zostawić, niech testerzy powiedzą.
5. **Pierwsze wgranie: najpierw testy wewnętrzne?** (a) Tak, AAB najpierw na tor testów
   wewnętrznych (do 100 osób, bez przeglądu Google), 1–2 dni na Twoim telefonie i Foldzie,
   potem ten sam AAB na testy zamknięte (rekomendacja: raport przedpremierowy i pętla testowa
   ruszą już przy wewnętrznych); (b) od razu testy zamknięte.
6. **Numer pierwszego wydania.** (a) `v0.1.0`, `versionCode 1` (rekomendacja: numer 0.x sam
   włącza ankietę testera, a „Co nowego” w grze jest przypisane do linii 0.1); (b) inny numer
   (podaj jaki; przy 1.0.0 ankietę trzeba włączyć ręcznie).

## Źródła

- Kod: `sim/.../Modes.kt` (`Campaign`, `Daily`, `Weekly`), `sim/.../Research.kt`,
  `sim/.../Battle.kt` (`commanderAvailable`), `app/.../screens/BattleScreen.kt`
  (`settleRewards`), `app/.../meta/LostBattle.kt`, `app/.../meta/Missions.kt`,
  `app/.../meta/Achievements.kt`, `app/.../meta/WhatsNewCatalog.kt`,
  `buildSrc/.../ReleaseChecklist.kt`, `app/build.gradle.kts`, `.github/workflows/android.yml`,
  `app/src/test/.../BalanceReport.kt`, `app/src/test/.../StoreGraphicsTest.kt`.
- Dokumenty: `century-war.md` (stan 4.11), `docs/COORDINATOR_HANDOVER.md`, `docs/RELEASE.md`,
  `docs/OWNER_CHECKLIST.md`, `docs/BALANCE.md` (dzień 1, R11-02, etapy 13–18, tura 9–10),
  `docs/research/R10-backlog.md`, `docs/research/R11-backlog.md`, `docs/store/listing_*.md`,
  `docs/GDD.md`.
