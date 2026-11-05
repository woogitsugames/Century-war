# R13 — plan testów: od pierwszego AAB do premiery (runda 13, 4.11.2026)

Baza: `integ/tura10`, a do tego raporty z gałęzi `claude/dzien-31…42` (d31, d32, d37, d38, d39, d40).
Dokument nie zmienia kodu gry.

**Decyzje, których plan nie rusza:**
- pierwsze wydanie to `v0.1.0` (`versionCode 1`): najpierw test wewnętrzny Play, potem zamknięty;
- w testach nie ma serwera rankingu, ścieżka `test-release` z d32 korzysta z
  `-PskipCheck=releaseDay,serverUrl,hmacKey`;
- `Daily.RELEASE_DAY` = **2027-12-01** zostaje bez zmian;
- prace specjalnie dla niewidomych są zakończone: istniejący TalkBack zostaje, nowych zadań TalkBack nie ma.

**Aktualizacja 5.11.2026 (decyzje właściciela):** konto Play **firmowe** — wymóg 12 testerów ×
14 dni i wniosek o produkcję **nas nie dotyczą**. Plan zostaje (fala 1 ok. 14 dni z 18–20
osobami), ale tydzień 3 („wniosek”) odpada, a próg 2.1 jest celem jakości, nie warunkiem Google.
Zrobione: TOP 5 poz. 1 (Raport v2, kod testera), poz. 2 (lejek, d45), poz. 3 (przypomnienie
w 3./10. dniu + `-PtesterFeedback`) — `docs/reports/d48-raport-v2.md`; K1 (etapy 28, 30–33)
i etap 19 złagodzone (PROTOCOL 0.8.10). Poz. 5 (ankieta końcowa, jeden baner 10. dnia) — `docs/reports/d57-ankieta-koncowa.md`.

**Skąd liczby:**
- dane lokalne: kod `meta/PlaytestLog.kt`, `crash/PlaytestReportText.kt`, `meta/TesterSurvey.kt`,
  `meta/BattleHistory.kt`, `crash/CrashStore.kt`;
- oczekiwane czasy etapów: `docs/research/R12-backlog.md` i d37;
- progi wydajności: `docs/PERFORMANCE.md` („Progi do porównania”);
- progi Play: `docs/RELEASE.md` („Formalności Play 2026–2027”).

Progi testu to **propozycje do zatwierdzenia**. Zależą od 12–20 osób, więc zapisane są jako
liczby osób, a nie procenty z dużej próby.

## Najważniejsze wnioski

- **Premiera jest za 13 miesięcy, a test zamknięty trwa 2–3 tygodnie.** Jeśli konto będzie gotowe
  w grudniu 2026, wniosek o produkcję można złożyć w styczniu 2027. Zostaje ok. 10 miesięcy
  do 2027-12-01. Plan dzieli je na 4 fazy:
  - test zamknięty w falach co 4–6 tygodni, z nową wersją w każdej fali;
  - test otwarty na 8–10 tygodni przed premierą (opcjonalnie), żeby Android vitals miały dość danych;
  - AAB produkcyjny `1.0.0` budowany **nie wcześniej niż 2027-10-02**, bo `releaseChecklist`
    przepuszcza `RELEASE_DAY` najwyżej 60 dni naprzód;
  - stopniowe wdrażanie wokół 1.12.2027.
- **Gra zbiera już prawie wszystko, czego potrzebują kryteria przejścia**: dni i sesje gry, czas,
  samouczek, pierwszą wygraną, etap, serie porażek, kapitulacje, ankietę 1–5, wydajność 5 sesji
  i ustawienia. Brakuje jednak pięciu rzeczy:
  - **nie da się połączyć** raportu z 3. i 10. dnia tej samej osoby, bo raport nie ma żadnego kodu;
  - raport **nie liczy awarii i ANR**;
  - raport **nie pokazuje etapów kampanii** poza 10 ostatnimi bitwami;
  - raport **nie mówi, czy gracz używał dowódcy**. d37 (pytanie 2) wprost czeka na tę informację
    z gry przed decyzją o etapie 19;
  - nic **nie przypomina** testerowi o raporcie w 3. i 10. dniu.

  To pozycje 1–3 i 5 TOP 5. Zrobione przed pierwszym AAB oszczędzają jedną falę testów.
- **Wymóg Google dla nowych kont osobistych:**
  - co najmniej **12 testerów**;
  - nieprzerwany opt-in przez **14 dni**;
  - potem wniosek o produkcję, który Google rozpatruje zwykle do 7 dni.

  Dotyczy kont osobistych założonych po 13.11.2023. Konto organizacji (D-U-N-S) jest zwolnione.
  **Decyzja 5.11: konto organizacji (firmowe) — wymóg nas nie dotyczy.** 14 dni z ≥ 12
  osobami robimy i tak, dla jakości.
- **Android vitals przy 15 testerach niewiele powiedzą.** Progi Google (1,09% awarii, 0,47% ANR)
  dotyczą odsetka użytkowników z 28 dni. Przy kilkunastu osobach jedna awaria to kilka procent,
  a konsola może pokazywać „za mało danych”. W teście zamkniętym liczymy więc **pojedyncze
  zdarzenia** (lista „Awarie i ANR” + lokalny licznik z pozycji 1 TOP 5), a odsetki z vitals
  dopiero w teście otwartym i przy produkcji.

---

## 1. Harmonogram tygodniowy i kryteria przejścia

### Oś czasu

T0 = dzień wgrania pierwszego AAB na test wewnętrzny. Daty w nawiasach to przykład dla
T0 = poniedziałek **7.12.2026**: właściciel potrzebuje realnie 2–5 tygodni na konto, klucz,
ID Play Games i politykę (`OWNER_CHECKLIST.md`). Jeśli T0 się przesunie, przesuwają się
tygodnie 0–6. Daty od fazy F (październik 2027) **nie zależą od T0**.

| Tydzień | Etap | Kto | Co się dzieje | Wyjście |
|---|---|---|---|---|
| −2…−1 (do 6.12) | Przygotowanie | koordynator | TOP 5 poz. 1–3 (przed AAB); scalenie kolejki z HANDOVER; próba R8 lokalnie (R12 poz. 2b); `test-release` z `version_name=0.1.0` | AAB + APK z CI |
| 0 (7–13.12) | **Test wewnętrzny** | właściciel + 2–5 zaufanych osób (limit 100, bez przeglądu Google) | raport przedpremierowy (pętla 4 scenariuszy); scenariusz testera A–E na telefonie i Foldzie; Play Games z prawdziwymi ID; aktualizacja na `versionCode 2`, jeśli była poprawka | **bramka G1** |
| 1 (14–20.12) | **Test zamknięty, fala 1, dni 1–7** | 18–20 zapisanych, cel ≥ 12 do końca | ten sam AAB awansowany na tor zamknięty (pierwsza wersja zamknięta przechodzi przegląd Google: od kilku godzin do kilku dni); link opt-in; dzień 3: raport + ankieta | raporty z 3. dnia: przegląd w dniu 4–5 |
| 2 (21–27.12) | Fala 1, dni 8–14 | ci sami | poprawka tylko przy P0 (awaria, utrata postępu); dzień 10: raport; dzień 14: liczba aktywnych testerów w konsoli | **bramka G2** |
| 3 (28.12–3.01) | ~~Wniosek o produkcję~~ — **odpada (konto firmowe, 5.11)** | — | tor zamknięty dalej aktywny; od razu prace nad 0.2 | — |
| 4–6 | Wersja 0.2 | koordynator | zadania z fali 1 (P1); pomiar balansu po danych (etap 19 z dowódcą, 28–33) | AAB 0.2 |
| co 4–6 tyg. do 09.2027 | **Fale 2…n testu zamkniętego** | fala 1 + nowi (cel 30–50 osób) | każda fala: nowa wersja 0.x, 14 dni, raport w 3. i 10. dniu, ta sama bramka G2 (bez wniosku) | **bramka G3** przed testem otwartym |
| ok. 22.09–24.11.2027 (8–10 tyg.) | **Test otwarty** (opcjonalnie) | każdy z linku/karty sklepu | wersja RC z serwerem rankingu (R5, d38), vitals z większej próby, ostatnie tłumaczenia | **bramka G4** |
| od 2.10.2027 | Budowa `1.0.0` | koordynator | tag `v1.0.0` (`releaseChecklist` bez wyjątków: `RELEASE_DAY` w oknie 60 dni, serwer, HMAC); `TESTER_SURVEY=false` | AAB produkcyjny |
| 23.11–1.12.2027 | **Produkcja** | wszyscy | wdrażanie stopniowe 10% → 50% → 100% w 3–5 dni, pełne 100% najpóźniej 1.12 (od tego dnia wyzwania dnia mają pełne zasady) | premiera |

**Dlaczego test otwarty jest „opcjonalny”:**
- **za:** vitals z setek urządzeń; karta sklepu jest widoczna, a opinie z testu otwartego są prywatne;
- **przeciw:** gra staje się publiczna, a wyzwania dnia przed premierą mają zasady sprzed
  premiery i napis „pełne zasady od premiery” (d31).

Decyzja: pytanie 4.

### Kryteria przejścia (bramki)

Skróty w tabelach: **RT** = „Raport dla testera” (sekcje `[Tester survey]`, `[Progress]`,
`[Play time]`, `[Modes]`, `[Performance]`, `[Settings]`). **PC** = Play Console. **n** = liczba
otrzymanych raportów danego dnia.

#### G1: test wewnętrzny → zamknięty (najwcześniej po 2 dniach, zwykle 5–7)

| # | Kryterium | Źródło | Próg |
|---|---|---|---|
| 1.1 | Raport przedpremierowy: awarie i ANR | PC → Testowanie → Raport przedpremierowy → Stabilność | **0** |
| 1.2 | Pętla gry zadziałała | jw., zrzuty/film | w scenariuszach 2 i 3 widać bitwę AI kontra AI |
| 1.3 | Wydajność w raporcie Play | jw., Wydajność | brak czerwonych ostrzeżeń; ewentualne porównanie z `PERFORMANCE.md` (scen. 2–3: p95 ≤ 25 ms) |
| 1.4 | Ostrzeżenia dostępności | jw., Dostępność | tylko znane, wynikające z rysowania na płótnie (spisać w `RELEASE.md`); 0 nowych |
| 1.5 | Awarie z prawdziwych telefonów | PC → Jakość → Android vitals → Awarie i ANR (filtr wersji 0.1.0) | **0 wpisów** |
| 1.6 | Scenariusz testera A–E (kroki 1–22) na telefonie i Foldzie | właściciel | 0 błędów P0 (awaria, utrata postępu, nie da się przejść dalej) |
| 1.7 | Wydajność urządzeń właściciela | RT `[Performance]`, 5 sesji | każda sesja: worst p95 ≤ **25 ms**, dropped steps = **0**, thermal ≤ **2**, max reduction ≤ **2** |
| 1.8 | Ankieta i raport działają | RT `[Tester survey]` | po 5 bitwach albo w 3. dniu: „Rating” albo „Skipped”, a nie „Not asked yet” |
| 1.9 | Play Games | telefon | logowanie OK, co najmniej 1 osiągnięcie odblokowane na koncie testera |
| 1.10 | Aktualizacja | telefon (jeśli był `versionCode 2`) | profil, medale i przerwana bitwa przetrwały aktualizację ze sklepu |

#### G2: koniec fali testu zamkniętego (dzień 14) → następna wersja (konto firmowe: bez wniosku o produkcję)

Twarde (bez nich nie idziemy dalej):

| # | Kryterium | Źródło | Próg |
|---|---|---|---|
| 2.1 | Testerzy z nieprzerwanym opt-in | PC → Testy zamknięte → Testerzy | **≥ 12 przez 14 dni** — przy koncie firmowym cel jakości, nie wymóg Google; rekrutować **18–20** (odpada zwykle 20–30%) |
| 2.2 | Powtarzalne awarie i ANR | PC Awarie i ANR + lokalny licznik (TOP 5 poz. 1) | **0 otwartych** grup awarii, które dotknęły ≥ 2 testerów albo powtarzają się u jednego |
| 2.3 | Raport przedpremierowy ostatniej wersji | PC | 0 awarii i ANR |
| 2.4 | Zwrot raportów | skrzynka/formularz (sekcja 3) | dzień 3: **n ≥ 10**; dzień 10: **n ≥ 10** |
| 2.5 | Zaangażowanie (raport z 10. dnia) | RT `Days played`, `Battle time`, `sessions` | mediana `Days played` **≥ 6**; **≥ 10** osób z ≥ 4 dniami; mediana `Battle time` **≥ 90 min**; mediana `sessions` **≥ 8** |

Miękkie (niespełnione = zadanie do następnej wersji, wniosek można złożyć):

| # | Kryterium | Źródło | Próg | Skąd próg |
|---|---|---|---|---|
| 2.6 | Samouczek | RT `Tutorial done` | „yes” u **≥ 90%** n | — |
| 2.7 | Czas do końca samouczka | RT `Tutorial done (after …)` | mediana **≤ 20 min** czasu bitew | oczekiwany etap 1 = 15,9 min (R12) |
| 2.8 | Pierwsza wygrana | RT `First win` | mediana ≤ 20 min, u **100%** n z 10. dnia | jw. |
| 2.9 | Postęp kampanii (10. dzień) | RT `Campaign: stage X` | mediana **≥ 8** (wódz); **≥ 3** osoby ≥ 13 | etap 8 po ok. 85 min, 19 po ok. 4,2 h (R12) |
| 2.10 | Ściany trudności | RT `Campaign loss streak (max)` | max ≥ 5 u **≤ 20%** n; przy których etapach — TOP 5 poz. 2 | d37: etapy 19 z dowódcą, 28, 30–33 |
| 2.11 | Kapitulacje | RT `surrenders / Battles` | **≤ 10%** bitew (suma) | — |
| 2.12 | Ankieta: ocena | RT `Rating` | **≥ 10** odpowiedzi; mediana **≥ 4**; każda ocena 1–2 → pytanie zwrotne do testera | — |
| 2.13 | Ankieta: co przeszkadza | RT `Biggest issue` | „too hard”, „battles too long”, „unclear rules”: każda **≤ 25%** odpowiedzi (≤ 3 z 12) | — |
| 2.14 | Wydajność telefonów testerów | RT `[Performance]` | worst p95 ≤ 25 ms w **≥ 80%** sesji; > 33 ms na **≤ 2** urządzeniach; thermal ≥ 3 w **0** sesji na ≥ 80% urządzeń; dropped steps > 60 w ≤ 10% sesji | `PERFORMANCE.md` |
| 2.15 | Tryby | RT `[Modes]`, `Screens visited` | **≥ 50%** n zagrało Endless i wyzwanie dnia | potrzebne do odpowiedzi „jakie funkcje testowano” |

Jeśli twarde kryterium nie przechodzi, wydłużamy falę o 7 dni z poprawioną wersją. Nie
wstrzymujemy toru i nie usuwamy testerów. Przed wnioskiem nie wolno też zmieniać `PROTOCOL`,
bo unieważniłoby to powtórki rankingowe testerów (R12).

#### G3: test zamknięty → otwarty (po fali ≥ 2, nie wcześniej niż 08.2027)

- G2 spełnione w dwóch kolejnych falach, w tym 2.12 (mediana ≥ 4) i 2.10.
- Serwer rankingu wdrożony i sprawdzony wg `SERVER_DEPLOY.md` §13 (d38). Okresy przechowywania
  zatwierdzone przez właściciela, Data safety i polityka po zmianie.
- `PROTOCOL` zamrożony do premiery. Po teście otwartym są już tylko poprawki bez zmian wyników bitew.
- Ostatnia fala: ≥ 25 aktywnych testerów, 0 otwartych P0 i P1.

#### G4: test otwarty → produkcja (tag `v1.0.0`, od 2.10.2027)

| Kryterium | Źródło | Próg (Google / nasz cel) |
|---|---|---|
| Awarie zauważalne przez użytkownika | PC → Android vitals (28 dni, tor otwarty) | < 1,09% / **< 0,5%** |
| ANR zauważalne przez użytkownika | jw. | < 0,47% / **< 0,25%** |
| Pojedynczy model telefonu | jw. | < 8% / brak modelu > 2% |
| Pamięć (gry, p90, od 02.2027) | jw. | progi Google (np. 2,25 GB na 4 GB RAM) / zmierzone w `PERFORMANCE.md` |
| Raport przedpremierowy `1.0.0` | PC | 0 awarii i ANR |
| `releaseChecklist` bez wyjątków | CI (tag) | zielony; `TESTER_SURVEY = false` w `BuildConfig` release |
| Play Games | PC → Play Games Services | konfiguracja **opublikowana** (nie tylko testowa), 84 ID |
| Ankieta ostatniej fali/otwartego | RT, formularz | mediana ≥ 4, „too hard” ≤ 25% |

Jeśli vitals nie mają danych (za mało użytkowników), stosujemy te same progi co w 2.2:
0 powtarzalnych grup awarii.

---

## 2. Wymagania Google Play: test zamknięty dla nowych kont osobistych

**Stan wiedzy i niepewność.** Oficjalna strona Google
([App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465))
**była niedostępna z tej sesji** 4.11.2026 (sieć środowiska blokuje `support.google.com`).
Poniższe opiera się na:
- zapisie z oficjalnej strony sprawdzonej **2.11.2026** (`docs/RELEASE.md`, „Testy zamknięte”, pkt 7–9);
- wyszukiwaniu 4.11.2026. Wyniki to blogi zgodne z oficjalnym zapisem, np.
  [testerscommunity.com (2026)](https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026),
  [extendsclass.com (2026)](https://extendsclass.com/blog/google-plays-closed-testing-requirement-what-developers-need-to-know-in-2026),
  [choicely.com](https://www.choicely.com/blog/google-play-12-tester-rule).

Przed pierwszym AAB koordynator powinien otworzyć oficjalną stronę jeszcze raz, po dopisaniu
`support.google.com` do dozwolonych hostów sieci.

| Zasada | Stan | Pewność |
|---|---|---|
| Kogo dotyczy | osobiste konta deweloperskie założone **po 13.11.2023**; konta organizacji (D-U-N-S) nie | oficjalne (2.11.2026) |
| Ilu testerów | **≥ 12** z aktywnym opt-in (w 2023 było 20, Google obniżyło do 12) | oficjalne: 12; historia 20 → 12 tylko z blogów |
| Jak długo | **14 dni bez przerwy** przed wnioskiem | oficjalne |
| Kto się liczy | tylko osoby, które kliknęły link opt-in i zostały w teście; sam e-mail na liście się nie liczy | oficjalne (strona o testach 9845334) |
| Rezygnacja testera | rezygnacja i ponowny zapis restartują okres **tej osoby** | z `RELEASE.md` (oficjalne 2.11) |
| Nowy AAB w trakcie | nowe wersje na tym samym torze **nie przerywają** okresu; **wstrzymanie toru** może przerwać | tylko blogi — niepewne; dlatego toru nie wstrzymujemy |
| Wniosek | Pulpit → „Apply for production”; pytania o test (jak rekrutowano testerów, ich zaangażowanie, opinie i co zmieniono), o aplikację i gotowość | oficjalne (ogólnie); lista pytań z pamięci i blogów — niepewna |
| Ocena wniosku | zwykle **do 7 dni**; Google może zażądać dalszych testów, m.in. z powodu „insufficient tester engagement” | oficjalne (2.11.2026) |
| Zaangażowanie | Google sprawdza, czy testerzy faktycznie używali aplikacji; blogi piszą o częstszych odrzuceniach w 2026 | oficjalne ogólnie; zaostrzenie 2026 niepotwierdzone |
| Zakres | wymóg na aplikację (każda nowa gra osobno); aktualizacje gry już wydanej go nie wymagają | blogi — niepewne |
| Test wewnętrzny | nie liczy się do 14 dni; do 100 osób, bez przeglądu Google, dostępny w kilka minut | oficjalne (strona 9845334) |

**Co z tego wynika dla nas:**
- rekrutujemy **18–20 osób**;
- toru nie wstrzymujemy aż do decyzji Google;
- odpowiedzi do wniosku budujemy z raportów:
  - 2.5: dni, sesje i minuty;
  - 2.15: użyte tryby;
  - 2.12–2.13: oceny;
  - lista poprawek z sekcji 3.

Koordynator przygotowuje tekst odpowiedzi w `docs/reports/test-fala1.md`.

---

## 3. Zbieranie i scalanie raportów testerów

### Prosty proces dla właściciela (rekomendacja: formularz Google)

**Raz, przed falą 1 (ok. 30 min):**
1. Załóż **Formularz Google** „Century War — raport testera” z polami:
   - pseudonim albo kod testera (krótki tekst, **bez nazwiska i e-maila**);
   - który raport: dzień 3 / dzień 10 / problem / inny;
   - model telefonu (tekst);
   - ile minut dziennie grasz (krótki tekst);
   - co się podobało (długi tekst);
   - co przeszkadzało albo nie działało (długi tekst);
   - **wklej „Raport dla testera” albo „Zgłoś problem”** (długi tekst).

   Ustawienia: **wyłącz zbieranie adresów e-mail**, odpowiedzi do nowego Arkusza.
2. W Play Console, w torze zamkniętym, jako „adres do opinii” (*feedback URL*) wpisz link do
   formularza. Testerzy zobaczą go na stronie opt-in.
3. Link do formularza dopisz na górze „Scenariusza dla testerów”, który dostają testerzy.

**Tester:** Ustawienia → O grze → Raport dla testera → Wyślij → w oknie udostępniania wybiera
**„Kopiuj”**. Potem otwiera link do formularza i wkleja raport. Kto woli, wysyła e-mail na
adres podany w scenariuszu (temat „Century War - raport testera” wstawia gra). Właściciel
wkleja go wtedy do formularza sam: jedna minuta.

**Co tydzień (ok. 5 min):** Arkusz odpowiedzi → **Plik → Pobierz → CSV** → wyślij plik
koordynatorowi w czacie. W dniu 4–5 i 11–12 fali dodatkowo:
- liczba aktywnych testerów z Play Console (Testy zamknięte → Testerzy);
- zrzut ekranu „Awarie i ANR”.

**Prywatność:**
- raport nie zawiera nicku, ID ani konta Play Games (`pt186_desc`);
- formularz nie zbiera e-maili;
- arkusz po premierze plus 90 dni usuwamy;
- dopisek o raporcie testera jest już w polityce (zatwierdzony 3.11);
- formularz prowadzi właściciel, a nie gra, więc Data safety bez zmian.

Wariant zapasowy (pytanie 3b): sama skrzynka e-mail i filtr Gmaila z etykietą „CW raporty”.
Co tydzień trzeba wtedy zaznaczyć wiadomości i użyć „Przekaż jako załącznik” do siebie, a pliki
`.eml` wgrać do czatu. To więcej klikania i łatwiej coś zgubić.

### Jak koordynator przekłada raporty na zadania

1. **Scalenie.** Na start `tools/playtest_merge.py` (TOP 5 poz. 4):
   - czyta CSV z formularza, wycina tekst raportu i parsuje etykiety z `PlaytestReportText`
     (angielskie, stałe);
   - jeden wiersz to tester + dzień;
   - dopasowanie dnia 3 i 10 po kodzie testera (poz. 1), a do tego czasu po pseudonimie
     i modelu telefonu.

   Wynik trafia do `docs/reports/test-faleN.md`:
   - tabela bramki G2 (każde kryterium: wartość, próg, OK/NIE);
   - lista „Biggest issue”;
   - wolne teksty pogrupowane w tematy.
2. **Selekcja (triage) każdej uwagi i awarii:**
   - **P0**: awaria, ANR, utrata postępu albo zablokowany postęp, u kogokolwiek. Poprawka
     i nowa wersja na tym samym torze w **≤ 48 h** (`versionCode` +1, d32 pkt 4);
   - **P1**: dotyczy **≥ 3 testerów** albo przez nią nie przechodzi kryterium G2. Zadanie
     `docs/tasks/T13-xx.md` do następnej wersji;
   - **P2**: pojedyncze uwagi i pomysły. Trafiają do backlogu następnej rundy researchu
     (R14), bez zlecania.
3. **Mapa „sygnał → co zlecić”:**

   | Sygnał w danych | Działanie |
   |---|---|
   | `loss streak` ≥ 5 albo „too hard” ≥ 25% | lejek etapów (poz. 2) → który etap; porównać z d37 (19 z dowódcą, 28, 30–33); pomiar `BalanceReport` `medale-czas` z tym etapem; korekta tylko za zgodą właściciela (PROTOCOL) |
   | dowódca użyty na 19 u ≥ połowy graczy, którzy tam doszli | d37 K19: dodatkowe ×0,96 na etapie 19 — do decyzji |
   | „battles too long” ≥ 25% | czasy z `Recent battles` vs oczekiwane w `BALANCE.md`; skrócenie wg wzoru R11-02/dni 1, 13–18 |
   | „unclear rules” ≥ 25% albo samouczek > 20 min | przegląd pierwszej godziny (wzór noc 5), dymki samouczka |
   | worst p95 > 33 ms albo thermal ≥ 3 na danym modelu | `PERFORMANCE.md`: próba na podobnym urządzeniu, regulator jakości |
   | `Days played` < 4 u wielu osób | prośba do testerów (e-mail od właściciela); przy wniosku opisać przyczynę |
   | grupa awarii w PC | stos z PC + „Zgłoś problem” od testera → P0 |
4. **Komunikacja z właścicielem:** po każdym raporcie z fali właściciel dostaje 3–5 zdań
   (stan bramki, ile P0/P1, decyzje do kliknięcia). Szczegóły zostają w `test-faleN.md`.

---

## 4. Czego brakuje w grze, by mierzyć kryteria: TOP 5

Rozmiar: S ≤ pół dnia, M ≈ 1 dzień (jak w R12). Zmiany dotyczą tylko wersji testowych i raportu.
Nie ruszają `:sim`, `PROTOCOL`, wyników bitew ani `RELEASE_DAY`. Bez prac TalkBack. Teksty
w nowych częściach `L.kt` od pierwszej wolnej. Wg HANDOVER to **part204+**, ale przed
zleceniem trzeba sprawdzić po scaleniu kolejki (d31 zajął part203).

### 1. Raport v2: kod testera, awarie i ANR, mapa dni, badania
- **Cel:**
  - **losowy kod testera** (4 znaki, np. `K7QM`, bez liter mylących), losowany przy pierwszym
    zapisie `playtest.json`, niepowiązany z niczym, w nagłówku raportu;
  - linia `Report format: 2`;
  - **licznik awarii i ANR** od początku dziennika, z `CrashStore` (pliki `fatal-`, `exit-fatal-`)
    i `ExitReasons`. Dziś raport tego nie ma, a lista w PC przy 15 osobach bywa pusta;
  - **mapa dni**: 28 ostatnich dni jako `1101111…` (czy grał danego dnia UTC). Odpowiada na
    pytanie Google o ciągłość zaangażowania. Dziś są tylko `firstDay`, `lastDay` i `daysPlayed`;
  - **liczba kupionych poziomów badań** (z 110) i suma medali. Pozwala porównać realnych
    graczy z profilem `medale-czas` z d37.
- **Mierzy:** 2.2, 2.4 (dopasowanie dnia 3 i 10), 2.5, 2.10 (badania przy ścianie).
- **Pliki:** `meta/PlaytestLog.kt` (pola opcjonalne, `VERSION` bez zmian, jak ankieta),
  `crash/PlaytestReportText.kt`, `crash/CrashStore.kt` (tylko odczyt liczby), testy
  `PlaytestLogTest`, `PlaytestReportTextTest`, `L.kt` (zmiana `pt186_desc` w 10 językach:
  „losowy kod testera, niepowiązany z kontem”).
- **Rozmiar:** S–M.
- **Ryzyko:** niskie–średnie.
  - Dzisiejszy opis obiecuje „bez … ID”. Trzeba zmienić tekst, dopisać zdanie w polityce
    PL/EN (zgoda właściciela, pytanie 5) i dodać test, że kodu nie ma w profilu, chmurze ani
    powtórkach.
  - Reinstalacja daje nowy kod: tester podaje wtedy pseudonim w formularzu.
- **Zależności:** przed pierwszym AAB (inaczej fala 1 bez dopasowania raportów).

### 2. Lejek kampanii i dowódca w raporcie
- **Cel:**
  - w `PlaytestLog`: dla każdego etapu kampanii próby, wygrane, kapitulacje, suma minut oraz
    liczba bitew z przywołanym dowódcą (stała tablica 36 × 5, bez alokacji przy rozliczeniu);
  - w raporcie jedna zwarta linia na etap z próbami, np.
    `S19: 4 tries, 1 win, 0 surr, 52m, cmd 3`;
  - zmieszczenie się w `MAX_CHARS` 6000: pokazać etapy z próbami, od najwyższego, najwyżej 15.
- **Mierzy:**
  - 2.9 i 2.10: na którym etapie stoją testerzy;
  - d37 pytanie 2 (dowódca na 19), wprost na potrzeby decyzji K19;
  - weryfikację K1 (28–33) na ludziach.
- **Pliki:** `meta/PlaytestLog.kt`, `screens/BattleScreen.kt` (`settleRewards` przekazuje
  „dowódca przywołany”: z `Battle`/gracza, sprawdzić nazwę pola), `crash/PlaytestReportText.kt`,
  testy.
- **Rozmiar:** M.
- **Ryzyko:** niskie. Tylko dziennik testu, poza profilem i chmurą. Uwaga na wątki (mutacje na
  wątku gry, jak `battleSettled`) i na obcięcie raportu: ankieta zostaje pierwsza.
- **Zależności:** razem z poz. 1 (jeden format `2`).

### 3. Przypomnienie o raporcie w 3. i 10. dniu + adres odbiorcy
- **Cel:**
  - tylko gdy `BuildConfig.TESTER_SURVEY`: w menu jednorazowy baner „3. dzień testu: wyślij
    Raport dla testera” z przyciskiem otwierającym to samo okno co w O grze. Drugi taki baner
    w 10. dniu. Dzień liczony od `firstDay` dziennika (kalendarz UTC);
  - opcjonalnie właściwość Gradle `-PtesterFeedback=<adres e-mail lub URL formularza>`
    → `BuildConfig`. Przy e-mailu `EXTRA_EMAIL` w intencji, przy URL przycisk „Otwórz formularz”
    po skopiowaniu raportu do schowka.
- **Mierzy:** 2.4. Bez przypomnienia zwrot raportu z 10. dnia będzie zwykle mniej niż połowa.
- **Pliki:** nowa czysta funkcja `meta/TesterReportReminder.kt` + test,
  `screens/MenuScreen.kt` (jak `updateSurvey`, z `blocksNotices` i okresem ciszy),
  `MainActivity.sendPlaytestReport`, `app/build.gradle.kts` (pole + walidacja jak
  `testerSurvey`), `.github/workflows/android.yml` (zadanie `test-release`: wejście),
  `L.kt` (nowa część).
- **Rozmiar:** S–M.
- **Ryzyko:** niskie.
  - Nie może pojawić się w produkcji: test `1.0.0`.
  - Nie może kolidować z ankietą, powitaniem i „Gra zaktualizowana”: kolejka banerów.
  - Workflow: tylko nowe wejście, **bez nowych wyzwalaczy**.
- **Zależności:** pytanie 3 (formularz czy e-mail).

### 4. Narzędzie scalania raportów (`tools/playtest_merge.py`)
- **Cel:**
  - wejście: CSV z formularza albo katalog z plikami `.txt`/`.eml`;
  - wyjście: CSV „tester × dzień” i Markdown z tabelą bramki G2 (progi z sekcji 1 jako stałe
    na górze pliku), rozkładem ocen i „Biggest issue”, wydajnością na model telefonu,
    a po poz. 2 także lejkiem etapów;
  - parsuje format 1 (dzisiejszy) i 2;
  - test na przykładowych raportach generowanych z `PlaytestReportTextTest` (tekst wzorcowy
    w `tests/fixtures/`).
- **Mierzy:** wszystkie kryteria G2 w jednym przebiegu, bez ręcznego liczenia median.
- **Pliki:** `tools/playtest_merge.py`, `tests/test_playtest_merge.py` (dołącza do
  `python3 -m unittest discover -s tests`), opis w `docs/RELEASE.md` (sekcja testów zamkniętych).
- **Rozmiar:** S.
- **Ryzyko:** niskie, poza grą. Etykiety raportu muszą zostać stałe: dopisać do
  `PlaytestReportTextTest` asercję, że etykiety z listy narzędzia istnieją.
- **Zależności:** brak. Najlepiej po poz. 1–2 (format 2), ale działa i na formacie 1.

### 5. Ankieta końcowa (10. dzień) w wersjach testowych
- **Cel:**
  - druga, jednorazowa ankieta po ≥ 10. dniu od `firstDay` i ≥ 6 dniach gry:
    - „Czy zagrasz po premierze?” (tak / może / nie);
    - ponowna ocena 1–5;
  - pola opcjonalne `survey2*` w `playtest.json`, sekcja `[Final survey]` w raporcie zaraz po
    `[Tester survey]`;
  - pierwsza ankieta bez zmian.
- **Mierzy:** zmianę oceny od 3. do 10. dnia (czy gra się nudzi albo robi za trudna) i
  deklarowaną retencję. To najmocniejszy argument we wniosku do Google i sygnał do G3/G4.
- **Pliki:** `meta/TesterSurvey.kt` (`dueFinal`), `meta/PlaytestLog.kt`,
  `screens/MenuScreen.kt` (ten sam komponent ankiety), `crash/PlaytestReportText.kt`, `L.kt`
  (nowa część), testy jak `TesterSurvey200Test`.
- **Rozmiar:** S–M.
- **Ryzyko:** niskie. Ryzykiem jest zmęczenie testera, więc zawsze z „Pomiń” i nie w tym samym
  dniu co przypomnienie z poz. 3: najpierw ankieta, raport następnego wejścia. Bez TalkBack-specyficznych prac.
- **Zależności:** może wejść jako aktualizacja w trakcie fali 1 (przed 10. dniem) albo w 0.2.

**Kolejność:** 1 → 2 → 3 przed pierwszym AAB (razem ok. 2 dni jednej osoby, konflikty tylko
w `PlaytestLog.kt`/`PlaytestReportText.kt`, więc jeden agent). Poz. 4 równolegle (inny agent,
poza `app/`). Poz. 5 może wejść do fali 1 jako aktualizacja.

---

## 5. Pytania do właściciela (prostym językiem, z rekomendacją)

1. **Konto Google Play: osobiste czy firmowe?** W dokumentach jest firmowe (potrzebny numer
   D-U-N-S, do 30 dni czekania, dane firmy publiczne w sklepie). Konto osobiste jest szybsze,
   ale przed wydaniem dla wszystkich Google wymaga, żeby co najmniej 12 osób testowało grę
   przez 14 dni bez przerwy.
   **Rekomendacja:** jeśli firma nie ma jeszcze D-U-N-S, konto osobiste. Do premiery jest
   rok, a 14 dni testu i tak robimy.
   **Decyzja 5.11: konto firmowe.**
2. **Ilu testerów zebrać na pierwszą falę?** Google liczy tylko osoby, które zostaną do końca;
   zwykle co czwarta odpada.
   **Rekomendacja:** 18–20 osób z telefonem z Androidem 8+, w tym 1–2 z dużym ekranem lub Foldem.
3. **Jak testerzy mają przesyłać raporty?**
   (a) Formularz Google: tester wkleja raport, Ty raz w tygodniu pobierasz jeden plik i wysyłasz
   go koordynatorowi;
   (b) e-mail na osobny adres, a Ty przekazujesz wiadomości.
   **Rekomendacja: (a)**, bo jest najmniej klikania i nic nie ginie.
4. **Co robimy między końcem pierwszego testu (styczeń 2027) a premierą (1.12.2027)?**
   (a) Kolejne fale testu zamkniętego co 4–6 tygodni z nową wersją, a na ostatnie 8–10 tygodni
   test otwarty, czyli gra widoczna w sklepie jako „wczesny dostęp”, żeby Google zebrało dane
   o stabilności z setek telefonów;
   (b) tylko test zamknięty do samej premiery.
   **Rekomendacja: (a).** Bez testu otwartego pierwsze prawdziwe dane o awariach przyjdą
   dopiero od graczy.
5. **Czy w „Raporcie dla testera” może być losowy kod testera (np. K7QM)?** Dzięki niemu
   połączymy raport z 3. i 10. dnia tej samej osoby. Kod nie jest powiązany z kontem ani
   telefonem, ale trzeba zmienić zdanie „bez ID” w opisie raportu i dopisać jedno zdanie
   w polityce prywatności.
   **Rekomendacja: tak.**
6. **Czy przed pierwszym AAB dodać 3 drobne pomiary?** Chodzi o kod testera z licznikiem awarii,
   postęp na etapach z informacją o używaniu wodza oraz przypomnienie o raporcie w 3. i 10. dniu.
   To ok. 2 dni pracy. Bez tego pierwsza fala da mniej odpowiedzi, np. na pytanie, czy etap 19
   z wodzem trzeba ułatwić.
   **Rekomendacja: tak, przed AAB.** Ankieta końcowa może dojść w trakcie testu jako aktualizacja.
7. **Poprawki w trakcie testu zamkniętego.**
   **Rekomendacja:** nowa wersja tylko przy poważnym błędzie (awaria, utrata postępu); reszta
   zbiorczo po 14 dniach. Częste aktualizacje męczą testerów, a każda zmiana balansu w trakcie
   unieważnia porównanie danych.

## Źródła

- **Kod:** `app/.../meta/PlaytestLog.kt`, `app/.../crash/PlaytestReportText.kt`,
  `app/.../meta/TesterSurvey.kt`, `app/.../meta/BattleHistory.kt`, `app/.../crash/CrashStore.kt`,
  `app/.../MainActivity.kt` (`sendPlaytestReport`), `engine/L.kt` (`pt186_desc`, `pt182_subject`).
- **Dokumenty:** `docs/COORDINATOR_HANDOVER.md` (4.11 wieczór i noc), `docs/research/R12-backlog.md`,
  `docs/RELEASE.md` („Ankieta testera”, „Raport przedpremierowy i pętla gry”, „Testy zamknięte”,
  „Formalności Play 2026–2027”), `docs/OWNER_CHECKLIST.md`, `docs/PERFORMANCE.md`
  („Progi do porównania”).
- **Raporty z gałęzi:** d31 (`claude/dzien-42-tlumaczenia`: od premiery), d32 (wydanie testowe),
  d37 i d40 (`claude/dzien-40-k1-etapy-28-33`), d38 (`claude/dzien-38-serwer`),
  d39 (`claude/dzien-41-drobne`).
- **Sieć (4.11.2026):** `support.google.com` niedostępne z sesji (blokada hosta); wyszukiwanie:
  [testerscommunity.com](https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026),
  [extendsclass.com](https://extendsclass.com/blog/google-plays-closed-testing-requirement-what-developers-need-to-know-in-2026),
  [choicely.com](https://www.choicely.com/blog/google-play-12-tester-rule),
  [theclosedtest](https://theclosedtest.neerajlovecyber.com/blog/google-play-12-testers-policy)
  (nieoficjalne, zgodne z oficjalnym zapisem z 2.11.2026).
