# R14 — nowy tryb gry: trzy propozycje do wyboru (6.11.2026)

Dokument dla właściciela i koordynatora. Nie zmienia kodu gry. Baza: `origin/main` (b1a5aaf8),
PROTOCOL 0.8.11, `BattleSnapshot.VERSION` 21, `Replay.VERSION` 7.

Przeczytane: `docs/GDD.md`, `century-war.md`, `sim/src/main/kotlin/com/centurywar/game/Modes.kt`,
`docs/BALANCE.md` (metodyka i sekcje dowódcy, Endless, dni 1–2, R11-02), `docs/research/R3-notes.md`
(ocena „szybkiej bitwy”), `docs/research/R13-testy.md`.

## Jak dziś zbudowane są tryby (skrót techniczny)

- `enum class Mode { CAMPAIGN, SKIRMISH, ENDLESS, DAILY }` w `Modes.kt`. Oblężenie tygodnia nie
  jest osobnym `Mode`: to `ENDLESS` z polem `BattleConfig.weekly >= 0`.
- Każda bitwa to jeden `BattleConfig`: tryb, seed, jawna siła AI (`aiPower`), zakres epok,
  modyfikatory, bonusy badań, pogoda, karta tygodnia, wydarzenie dnia. Generatory: `Campaign`,
  `Daily`, `Weekly`, `ChallengeRuleSets`; potyczka: `app/.../meta/SkirmishSetup.kt`.
- Symulacja jest deterministyczna (60 Hz, seed), więc powtórki (`Replay`) i snapshoty
  (`BattleSnapshot`) odtwarzają bitwę co do klatki. `Mode` jest zapisywany w snapshocie po numerze
  (`enumAt(Mode.values(), …)`), a w rankingu jako nazwa (`ScoreWire`: `campaign/skirmish/endless/
  weekly/daily`).
- Tryb dotyka ok. 25 miejsc w `:app` (najwięcej `ModeScreen.kt`, `BattleScreen.kt` z
  `settleRewards`, `LostBattle.kt`, `Achievements.kt`, `Missions.kt`, `BattleResultsPanel.kt`,
  `StatsScreen.kt`) i 4 miejsc w `Battle.kt`.
- Zasada z dotychczasowych zmian: **każda zmiana reguł w `:sim` = PROTOCOL +1 i regeneracja
  złotych powtórek** (`:sim:regenerateGolden`); nowe pola w snapshocie = `BattleSnapshot.VERSION` +1
  z obsługą starych zapisów; nowe wartości enumów dopisuje się **na końcu**.
- Balans mierzymy `BalanceReport` (AI kontra AI, profile badań nowy/początkujący/średni/weteran,
  `BALANCE_RUNS`, `mirrorReport` dla uczciwości 45–55%) oraz `EndlessBalanceReportTest`.

**Czego nowy tryb nie może powtarzać:** Oblężenie bez końca (jedna długa bitwa, fale, karty co 5 fal,
bossowie), Potyczka (jedna bitwa z ustawieniami, AI z osobowością), Wyzwanie dnia i Oblężenie tygodnia
(te same zasady dla wszystkich, ranking). Wszystkie trzy propozycje poniżej działają offline, bez
serwera, bez płatności i mieszczą się w obecnym układzie ekranu bitwy (telefon pionowo/poziomo,
Fold złożony i rozłożony) — nowe są tylko ekrany przed i po bitwie.

---

## Propozycja A — „Wyprawa przez wieki”

**Dla gracza (opis do gry):**
Prowadzisz jedną armię przez serię krótkich bitew — od Plemion aż po Erę orbitalną. Między bitwami
wybierasz drogę na małej mapie: zwykła bitwa, obóz (naprawa bazy), skład zaopatrzenia (złoto na
start), sztab (nowa doktryna) albo twierdza elitarnego wroga. Uszkodzenia bazy i zdobyte doktryny
przechodzą do kolejnej bitwy, więc każda decyzja się sumuje. Jedna przegrana kończy wyprawę, ale
zdobyte trofea zostają.

**Czym się różni od istniejących trybów:**
- Kampania to stała lista etapów; tu trasa i nagrody między bitwami są losowane z seeda wyprawy
  i wybierane przez gracza.
- Endless to jedna nieprzerwana bitwa z falami; tu są oddzielne, krótkie bitwy (3–5 min), a
  „pamięć” między nimi to stan bazy, doktryny i rezerwa złota — nie karty fal.
- Potyczka nie ma żadnych konsekwencji między bitwami; tu cała wartość leży w łańcuchu decyzji.

**Pętla i czas sesji:**
1. Start: wybór długości wyprawy (krótka: 5 bitew / pełna: 9 bitew, jedna na epokę).
2. Mapa: 2–3 rozwidlenia na krok; ikony mówią, co jest dalej (ekran przewijany pionowo, czytelny
   na wąskim ekranie zewnętrznym Folda).
3. Bitwa w zakresie 1–2 epok, z modyfikatorem i pogodą z puli, presja oblężnicza wcześnie (jak
   etapy 2–7), więc bitwa trwa ok. 3–5 min.
4. Po bitwie: HP bazy (procent) przechodzi dalej, nagroda węzła, zapis stanu wyprawy.
- Krótka wyprawa: ok. 20–25 min, pełna: ok. 40–50 min. **Naturalne pauzy po każdej bitwie** —
  wyprawę można przerwać i wrócić następnego dnia (zapis między bitwami, w bitwie działa istniejący
  snapshot).

**Nagrody i odblokowania:**
- Odblokowanie po etapie 8 kampanii (gracz zna już dowódcę i doktryny).
- Medale za każdą wygraną bitwę, przeliczane za minutę gry jak w potyczce (`SkirmishSetup.reward`),
  z dziennym limitem medali z wyprawy — żeby nie stała się najszybszą drogą przez badania.
- Trofea za ukończenie: sztandary (`BannerCatalog`), wpis w Kodeksie, osiągnięcia
  („Ukończ wyprawę bez naprawy w obozie”, „Ukończ pełną wyprawę”). Tylko kosmetyka, zero
  wpływu na siłę.
- Lokalny rekord „najdalszy krok” i „najlepszy czas pełnej wyprawy”; bez rankingu globalnego na
  start.

**Koszt budowy: L** (ok. 4–6 dni pracy agenta + pomiar).
- `:sim`: `Mode.EXPEDITION` dopisany na końcu; w `BattleConfig` dwa nowe pola: procent HP bazy
  gracza na starcie i lista doktryn przyznanych na starcie (dziś doktryny wybiera się tylko przy
  awansie). Obsługa w `Battle.kt` (start bitwy) i `BattleSnapshot.kt`. **PROTOCOL +1, snapshot +1,
  złote powtórki** (stare bitwy bez zmian, bo nowe pola mają domyślne wartości).
- `:sim`: generator `Expedition` (nowy plik obok `Modes.kt`) — z seeda wyprawy buduje mapę węzłów
  i `BattleConfig` dla każdej bitwy; testowalny w JUnit bez Androida.
- `:app`: `meta/ExpeditionState.kt` (stan wyprawy w JSON, zapis atomowy jak `Profile`),
  ekran mapy `screens/ExpeditionScreen.kt`, wpis w `ModeScreen.kt`, rozliczenie w
  `BattleScreen.settleRewards`, `BattleResultsPanel` (przycisk „Dalej w drogę”), `LostBattle.kt`
  (przerwana bitwa = koniec wyprawy czy zwrot?), osiągnięcia, misje, statystyki, teksty `L.kt`
  (nowa część `partNNN`), TalkBack dla mapy (istniejący wzorzec ekranów listy).
- Nowy zapis: `expedition.json` (lub sekcja w profilu) + kopia w `ProfileBackup`.
- Ranking: brak (żadnych zmian w `ScoreWire`, serwerze, Play Games).

**Ryzyka:**
- Spirala porażki: słaba baza po bitwie 3 przegrywa bitwę 4. Trzeba stroić obozy i siłę AI węzłów.
- Zakres: najwięcej nowych ekranów i tekstów z trzech propozycji; łatwo „rozlać” projekt.
- Nagrody: bez limitu dziennego wyprawa przyspieszy badania i rozjedzie balans kampanii.
- Przerwanie w trakcie bitwy (zabicie procesu) — trzeba jasno zdecydować, co się dzieje ze stanem
  wyprawy (propozycja: wznowienie ze snapshotu, jak dziś w kampanii).

**Jak zmierzyć balans na symulacji:**
- Nowy raport `BalanceReport.expeditionReport`: AI gra całe wyprawy (100 seedów × profil
  początkujący/średni/weteran), z prostą polityką wyboru drogi (zawsze obóz poniżej 50% HP, w
  innym razie losowo).
- Cele: krótka wyprawa ukończona w 40–60% (średni profil), pełna w 20–35%; mediana bitwy 3–5 min;
  żadna bitwa poniżej 2 min; odsetek patów 0.
- Kontrola nagród: medale/godzinę wyprawy ≤ medale/godzinę kampanii tego samego profilu
  (porównanie z sekcją „medale-czas” w `docs/BALANCE.md`).

**Czy pasuje do testów zamkniętych przed premierą:** raczej **nie do pierwszej fali**. Duża zmiana
`:sim` i nowe zapisy tuż przed AAB podnoszą ryzyko. Dobry kandydat na pierwszą dużą aktualizację
po premierze albo na drugą falę testów.

---

## Propozycja B — „Rozkazy sztabu”

**Dla gracza (opis do gry):**
Sztab daje ci konkretny rozkaz i konkretne warunki: „Utrzymaj bazę przez 4 minuty bez wież”,
„Zburz bazę wroga tylko piechotą i dowódcą”, „Wygraj w nocy, mając połowę złota”. Każdy rozkaz to
krótka, ręcznie przygotowana bitwa z trzema gwiazdkami za cel główny i dwa cele dodatkowe. Rozkazy
uczą, jak naprawdę działają kontry, pogoda, warianty epok i wieże — i sprawdzają, czy umiesz to
wykorzystać.

**Czym się różni od istniejących trybów:**
- Wyzwanie dnia jest losowane i zmienia się codziennie; Rozkazy to **stała, ułożona ręcznie
  kolekcja** (np. 27 rozkazów: 3 na epokę), zawsze dostępna, do powtarzania po trzy gwiazdki.
- Kampania wymaga zniszczenia bazy; tu cele są różne: przetrwać do czasu, wygrać ograniczonym
  składem, wygrać przed czasem, nie stracić dowódcy, zniszczyć bazę z określonym HP własnej.
- Potyczka daje swobodę ustawień; Rozkazy dają **ograniczenia** — to tryb „łamigłówek taktycznych”
  na krótko.

**Pętla i czas sesji:**
1. Lista rozkazów pogrupowana po epokach (siatka kart; na zewnętrznym ekranie Folda jedna kolumna).
2. Karta rozkazu: cel, ograniczenia, pogoda, nagroda — jedna plansza przed bitwą.
3. Bitwa 2–5 min (twardy limit czasu w każdym rozkazie).
4. Wynik: 1–3 gwiazdki, podpowiedź po porażce (istniejący mechanizm „dlaczego przegrałeś”).
- Sesja: **2–5 min na rozkaz**, idealne „na jedną przerwę”. Pełna kolekcja: ok. 3–5 godzin.

**Nagrody i odblokowania:**
- Rozkazy epoki odblokowują się razem z epoką w kampanii (np. rozkazy Epoki żelaza po etapie 9),
  więc tryb nie wyprzedza nauki.
- Medale **jednorazowo** za każdą nową gwiazdkę (np. 10/15/20), później powtórki bez medali — tryb
  nie da się „farmić”.
- Za komplet gwiazdek w epoce: sztandar epoki i wpis „Mistrz sztabu” w Kodeksie; osiągnięcia
  (np. „27 rozkazów na trzy gwiazdki”).
- Bez rankingu globalnego (lokalny licznik gwiazdek sztabu w Statystykach).

**Koszt budowy: M** (ok. 2–4 dni pracy agenta + pomiar; potem treść dopisywana partiami).
- `:sim`: `Mode.ORDERS` dopisany na końcu; w `BattleConfig` dwa nowe pola: maska dozwolonych
  rodzajów jednostek (wręcz/dystans/ciężka/elita/dowódca) i limit czasu bitwy z regułą
  rozstrzygnięcia (dziś `SHORT_DAY` ma stałe 8 min). Wieże da się już wyłączyć (`NO_TURRETS`),
  pogoda i modyfikatory już są. **PROTOCOL +1, snapshot +1, złote powtórki.** AI nie zmienia się —
  dostaje te same ograniczenia lub nie, zależnie od rozkazu.
- `:sim`: `Orders.kt` — lista rozkazów jako dane (jak `Campaign.stages`), każdy z celami
  sprawdzanymi funkcją na wyniku bitwy (czas, HP bazy, zabici, czy dowódca przeżył). Testowalne w JUnit.
- `:app`: ekran listy `screens/OrdersScreen.kt` (wzorem `CampaignScreen`), karta rozkazu przed
  bitwą, wiersz celów w `BattleResultsPanel`, wpis w `ModeScreen.kt`, `settleRewards`, osiągnięcia,
  teksty `L.kt` (opisy rozkazów to najwięcej tekstu, także tłumaczenia R7).
- Nowy zapis: tablica gwiazdek rozkazów w `Profile` (wzór: `Profile.stars`, ta sama migracja przy
  dodawaniu rozkazów na końcu listy).
- Ranking: brak; serwer i Play Games bez zmian (poza nowymi osiągnięciami w arkuszu PGS, jeśli
  właściciel zechce).

**Ryzyka:**
- **Treść:** każdy rozkaz trzeba ułożyć i sprawdzić. 27 rozkazów to sporo pracy projektowej i tekstów.
  Można zacząć od 9 (po jednym na epokę).
- Rozkaz za trudny przy „uczciwym AI” albo wygrywany przypadkiem — każdy wymaga pomiaru.
- Ograniczenie składu może ujawnić słabe jednostki (to też zaleta: darmowy test balansu).
- Zmiana `:sim` tuż przed AAB — mała (dwa pola z wartościami domyślnymi), ale jednak.

**Jak zmierzyć balans na symulacji:**
- `BalanceReport.ordersReport`: każdy rozkaz × 20 seedów × profil, który ma go odblokowany
  (np. rozkazy żelaza — profil początkujący). AI gracza dostaje te same ograniczenia.
- Cele: cel główny (1★) osiągany przez AI w 50–80%, 3★ w 10–35% (człowiek gra lepiej niż AI, więc
  to dolne oszacowanie, jak w kampanii); mediana 2–5 min; 0 patów.
- Test w JUnit: każdy rozkaz ma osiągalny cel (co najmniej jeden seed z 3★) — zabezpieczenie przed
  rozkazem niemożliwym po przyszłych zmianach balansu.

**Czy pasuje do testów zamkniętych przed premierą:** **tak**, w wersji 9 rozkazów. Krótkie sesje
pasują do testerów, a dziennik testu (`PlaytestLog`) pokaże, które rozkazy porzucano — to także
gotowa informacja o tym, których mechanik gracze nie rozumieją.

---

## Propozycja C — „Echo wodza”

**Dla gracza (opis do gry):**
Każda twoja bitwa zostawia „echo” — zapis twoich rozkazów. W tym trybie stajesz po drugiej stronie
pola walki przeciw własnemu echu: wróg szkoli te same jednostki, w tych samych chwilach, co ty
kiedyś. Możesz też wysłać swoje echo znajomemu jako plik, a on spróbuje je pokonać na swoim
telefonie. Pokonaj siebie sprzed tygodnia i sprawdź, czy naprawdę grasz lepiej.

**Czym się różni od istniejących trybów:**
- W potyczce i kampanii wrogiem jest AI z osobowością; tu wrogiem jest **plan człowieka** —
  twój albo znajomego — odtworzony z zapisu rozkazów.
- Wyzwanie dnia porównuje wyniki w rankingu; Echo to bezpośredni pojedynek z cudzą (albo własną)
  strategią, **bez serwera**: plik idzie przez komunikator lub e-mail (istnieje
  `ReplayShareProvider` i import powtórek `ReplayImport`).
- To lokalna, asynchroniczna rywalizacja — namiastka gry przeciw ludziom bez infrastruktury.

**Pętla i czas sesji:**
1. Wybór echa: ostatnie wygrane bitwy z potyczki (lista z `ReplayStore`) albo plik od znajomego.
2. Bitwa 5–10 min na zasadach tamtej bitwy (te same epoki, modyfikatory, seed), ale gracz stoi
   po stronie przeciwnej.
3. Wynik: wygrana/przegrana + porównanie (czas, straty) z oryginałem; możliwość odesłania
   własnego echa „w odpowiedzi”.
- Sesja: 5–10 min. Rytm: kilka razy w tygodniu, napędzany przez znajomych.

**Nagrody i odblokowania:**
- Odblokowanie po pierwszej wygranej potyczce.
- Niewielkie medale za wygraną z echem (jak potyczka własna, za minutę), limit dzienny.
- Kosmetyka: sztandar „Pogromca echa”, osiągnięcia („Pokonaj echo znajomego”, „Pokonaj własne
  echo z wyższego poziomu trudności”). Bez rankingu.

**Koszt budowy: M/L** (ok. 3–5 dni + duża niepewność techniczna).
- `:sim`: nowy sterownik strony wroga, który zamiast `Ai` wykonuje listę rozkazów z `Replay`
  (rodzaje komend już są: `QUEUE`, `EVOLVE`, `BUY_TURRET`, `BARRAGE_AT`, `SUMMON_COMMANDER`…).
  Rozkazy niewykonalne w danej chwili (brak złota, pełna armia) czekają albo przepadają —
  reguła do zaprojektowania. `Mode.ECHO` na końcu, pole w `BattleConfig` z zakodowanym echem.
  **PROTOCOL +1, snapshot +1 (echo musi być w snapshocie), złote powtórki**, a powtórka bitwy z
  echem zawiera w sobie drugą powtórkę.
- `:app`: wybór echa, import pliku (rozszerzenie `ReplayImport`), ekran porównania,
  `ModeScreen`, `settleRewards`, teksty.
- Nowy zapis: lista ech w `ReplayStore` (już przechowuje powtórki; trzeba oznaczyć „echo”).
- Prywatność: plik echa zawiera tylko rozkazy i nazwę sztandaru — dopisek w polityce prywatności
  o udostępnianiu plików (to decyzja właściciela).

**Ryzyka:**
- **Echo bywa głupie:** rozkazy odtwarzane „na ślepo” nie reagują na sytuację, więc łatwo je
  ograć jedną sztuczką. Jeśli tak, tryb szybko się nudzi.
- Złoto i XP wroga zależą od przebiegu bitwy, więc echo nie powtórzy się wiernie; trzeba
  zdecydować, czy wróg dostaje „złoto oryginału” (wtedy uczciwie), czy zarabia sam.
- Pliki z zewnątrz = wejście niezaufane: potrzebna walidacja jak w `Replay.validate` i limity
  rozmiaru (jest to robione dla powtórek, ale trzeba to powtórzyć dla nowego formatu).
- Najmniej przewidywalny wynik pomiaru z trzech propozycji.

**Jak zmierzyć balans na symulacji:**
- Raport `echoReport`: nagrać 50 bitew AI kontra AI (potyczka, 4 poziomy), potem zagrać AI przeciw
  echu każdej z nich. Cele: wygrane AI-gracza z echem 40–65% (echo nie może być ani bezbronne,
  ani silniejsze niż oryginalne AI); odsetek „rozkazów przepadłych” < 15%; 0 patów.
- Test lustrzany: AI kontra echo tej samej AI na tym samym seedzie powinien dawać wynik zbliżony
  do oryginalnej bitwy (miara wierności echa).

**Czy pasuje do testów zamkniętych przed premierą:** **jako eksperyment — tak, jako funkcja
premierowa — nie**. Testerzy znają się nawzajem, więc wymiana ech między nimi to dobry, tani test
ciekawości trybu. Ale niepewność techniczna jest największa.

---

## Porównanie

| | A. Wyprawa przez wieki | B. Rozkazy sztabu | C. Echo wodza |
|---|---|---|---|
| Co nowego daje | decyzje między bitwami, długi cel | krótkie łamigłówki taktyczne, nauka mechanik | rywalizacja z człowiekiem bez serwera |
| Sesja | 20–50 min (z pauzami) | 2–5 min | 5–10 min |
| Koszt | **L** | **M** | **M/L** |
| Zmiana `:sim` / PROTOCOL | tak (2 pola + generator) | tak (2 pola, małe) | tak (nowy sterownik wroga) |
| Nowy zapis | stan wyprawy | gwiazdki rozkazów | lista ech / pliki |
| Ryzyko balansu nagród | średnie (limit dzienny) | niskie (medale jednorazowe) | niskie |
| Ryzyko „nudy” | niskie | średnie (skończona treść) | wysokie |
| Testy zamknięte przed premierą | nie (po premierze) | **tak (9 rozkazów)** | eksperyment |

## Rekomendacja

**Propozycja B — „Rozkazy sztabu”**, w pierwszej wersji 9 rozkazów (po jednym na epokę), a potem
dopisywane partiami jak paczki zawartości (wzór: paczki 170/184/206).

Dlaczego:
1. **Najtańsza i najbezpieczniejsza przed premierą**: dwie małe zmiany w `:sim` z wartościami
   domyślnymi, medale jednorazowe (nie da się farmić, kampania i badania zostają w balansie).
2. **Najbardziej się różni** od obecnych trybów: jedyny tryb ręcznie ułożony i z innymi celami niż
   „zburz bazę” albo „przetrwaj jak najdłużej”.
3. **Pomaga reszcie gry**: uczy kontr, pogody, wież i wariantów, czyli dokładnie tego, na czym
   gracze odpadają w drugiej połowie kampanii (`docs/BALANCE.md`, klif 18 → 19). Dziennik testera
   pokaże, które mechaniki są niezrozumiałe.
4. **Krótkie sesje** pasują do telefonu i do testerów, którzy grają w przerwach.

Wyprawę (A) warto zaplanować jako pierwszą dużą aktualizację po premierze — daje najwięcej
powtarzalności na tygodnie. Echo (C) można później sprawdzić jako mały eksperyment wśród testerów,
zanim ktokolwiek zainwestuje w pełną wersję.

## Pytania do właściciela

1. Który kierunek wybierasz: krótkie zadania z gwiazdkami (B), długa wyprawa z mapą (A), czy walka
   z zapisem własnej lub cudzej gry (C)? Możesz też powiedzieć „żaden przed premierą”.
2. Czy nowy tryb ma trafić do testerów **przed premierą** (wtedy tylko mała wersja, np. 9 rozkazów),
   czy wolisz zostawić go na pierwszą aktualizację po premierze?
3. Czy za nowy tryb gracz ma dostawać **medale do badań**, czy tylko ozdoby (sztandary, wpisy w
   Kodeksie, osiągnięcia)? Medale bardziej zachęcają, ozdoby nie ruszają balansu.
4. Czy nowy tryb ma mieć **ranking** (porównanie z innymi graczami), czy wystarczą lokalne
   gwiazdki i rekordy? Ranking oznacza więcej pracy i zależność od serwera lub Play Games.
