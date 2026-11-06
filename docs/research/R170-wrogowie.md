# R170 — czytelne style przeciwnika

Stan odniesienia: `origin/main` f17c6e25, 6.11.2026. To propozycje do późniejszej decyzji i pomiaru, nie zmiana gry ani wynik wykonanego eksperymentu. W tym zleceniu nie uruchamiano Gradle: zmieniono tylko dokument.

## Co już działa i ograniczenia

`AiPersonality.kt:7` opisuje wagi ról i progi zakupów; istnieją Szturmowiec, Inżynier, Gospodarz, Oblężnik i Zrównoważony (`AiPersonality.kt:35`). AI ma osobne strumienie losowe (`Ai.kt:94`), widzi przeciwników tylko przez `canSeeEnemy` (`Ai.kt:146`), dobiera role z kontrami i uwzględnia ograniczenia (`Ai.kt:370`), a decyzje wykonuje tymi samymi komendami (`Ai.kt:80`). Wagi nie gwarantują składu: złoto, kolejka, kontrreakcja i dostępność zmieniają wynik. Rola to wręcz, dystans, ciężka albo elita (`Defs.kt:5`).

`AiPersonalityProfileTest.kt:9` jest wynikiem zlecenia 22: mierzy 20 seedów, dwa zakresy epok, pierwsze 90 s, wieże, zdolności, postój i odsetek zwycięstw. To metodologia, a nie dowód, że poniższe progi są osiągnięte. W kampanii cztery etapy przypadają na epokę i mają modyfikatory (`Modes.kt:190`); podane numery etapów są kandydatami, nie twierdzeniem o obecnym przypisaniu. Rozkazy filtrują działania przez `enemyAllowed` (`Modes.kt:165`, `Battle.kt:484`); proponowane dopasowania do nich dotyczą ewentualnych przyszłych decyzji, nie zmiany obecnych Rozkazów. `RANGED_HORDE`, brak wież, mgła i inne modyfikatory mogą przesłonić sygnaturę i trzeba je badać osobno. Endless ma fale i odrębną rotację bossów (`EndlessCards.kt:223`, `Battle.kt:1907`), więc osobowość dowódcy nie powinna być obiecywana dla zwykłych fal bez osobnego projektu.

## Wspólne zasady wdrożenia i pomiaru

Wszystkie warianty wyłącznie zmieniają decyzje, nie przychód, statystyki ani informacje. Reakcje na wroga używają tylko `canSeeEnemy`; własne złoto, kolejka, dostępne role i czas są znane dowódcy. Progi są punktami startowymi do strojenia, a nie gwarancją. Każda zmiana decyzji zmienia przebieg symulacji i wymaga decyzji koordynatora o `SimVersion.PROTOCOL`, złotych powtórkach i zgodności serwera. Dodać wartości enuma na końcu (ordinal w `BattleSnapshot.kt:269,325,512,605`), nie zmieniając starych; sam wybór ze zmienionej liczby wartości w `Ai.kt:67` zmieni także stare seedy, więc dla historycznych bitew wymagane jest wersjonowane, zamrożone mapowanie, w tym wyzwań dziennych (`Modes.kt:687`). Jeśli dodany zostanie trwały licznik/czas/faza, zapisać i odczytać go w `BattleSnapshot`, podnieść VERSION i sprawdzić wznowienie; warianty bez nowego stanu mogą korzystać z istniejącego czasu i zapisywanych timerów, ale kompatybilność trzeba potwierdzić testem. Format powtórki zmieniać tylko gdy wymaga dodatkowych danych; sam wynik wymaga nowego protokołu.

Rozszerzyć `AiPersonalityProfileTest` oraz `BalanceReport` o parowane seedy: co najmniej 100 na styl i scenariusz 0→3 i 2→2, BALANCED po stronie gracza, te same seedy, ograniczenia i bonusy; zbierać również wynik dla 20 seedów obecnego testu. Próbkować co 5 s, zapisać przebieg do 60 i 90 s osobno, rozdzielać remisy/niedokończone i zgłaszać 95% przedziały niepewności. Weryfikować widoczność akcji na nagraniach oraz min. dwie cechy różne o 15% od najbliższego istniejącego stylu; różnicę udziałów podawać także w punktach procentowych. Wstępna bramka: 30–70% wygranych z BALANCED, bez regresji etapu >10 p.p. względem tej samej próby bazowej; brak dowodu rozpoznawalności lub zbyt szerokie przedziały = potrzeba większej próby, nie zaliczenie. Uruchomić testy determinizmu, mgły, maski, snapshot/odtworzenie i replay/serwer, a dla kandydatów do Rozkazów osobno `OrdersBalanceReport` (≥40 tych samych seedów, cele 1★/3★ i czas z `docs/reports/d62-rozkazy.md:75`). Przy ograniczeniu kluczowej akcji osobowość zastępuje się BALANCED lub jawnie wybiera dozwoloną rolę; nigdy pętli z zakazaną kolejką. Brak gwarancji, że akcja wystąpi, gdy brakuje zasobów lub celów.

Czytelność wspólna: akcje i sylwetki pozostają istniejące, bez nowych ikon ani zmian `compactTop` i celowania. Na telefonie 720p i zewnętrznym Foldzie 904×2316 trzeba zobaczyć nagrania 100% i 130% tekstu: grupa i czas ruchu powinny być czytelne bez drobnych napisów, koloru jako jedynej wskazówki czy nowych efektów zasłaniających pole. Każda propozycja poniżej ma wskazany sygnał do obserwacji.

## 1. Osłona i strzała

Gracz widzi z przodu jedną–dwie jednostki wręcz, za którymi regularnie pojawiają się strzelcy. Formacja ma słabszy front po utracie osłony i nie powinna udawać nowego typu jednostki.

- **Symulacja:** nowy wpis `AiPersonality`; w `Ai.pickRole` po uwzględnieniu widoczności, kosztu i maski podbić wagę dystansu, dopóki żyje przynajmniej jedna własna jednostka wręcz; bez frontu pierwszeństwo dozwolonej wręcz. Bez dodatkowego stanu; protokół i zgodność zapisu jak wyżej.
- **Kontra:** szybka wręcz na strzelców, ewentualnie ostrzał skupiska; atak samymi ciężkimi pozwala wykorzystać przewagę dystansu.
- **Gdzie/koszt:** kandydat kampania 5 lub 9 i potyczka; przyszły rozkaz tylko z dozwolonymi obiema rolami. Endless i wyzwania rankingowe dopiero po osobnym pomiarze. M.
- **Ryzyko/miara:** kontrreakcje i niedobór złota mogą opóźnić wzorzec; odnotować udział dystansu w 0–90 s, czas pierwszej pary wręcz+dystans i odsetek próbek z taką parą, porównać z BALANCED/ENGINEER. Sygnał na obu ekranach: dwa rozdzielne szeregi sylwetek, sprawdzić gdy jednostki nachodzą na siebie.

## 2. Krótkie uderzenia

Wróg wysyła małe, częste grupy, a nie jedną dużą falę. Między grupami pozostawia graczowi krótkie okna na zebranie obrony.

- **Symulacja:** wpis osobowości i w `managePosture` planowany cykl oparty o `b.time`, istniejący `nextHoldAt`/`holdUntil` i limit dwóch–trzech żywych jednostek; przy zagrożeniu bazy zwolnić postawę. Bez nowych timerów tylko po potwierdzeniu snapshotu; PROTOCOL i zgodność jak wyżej.
- **Kontra:** obrona niewielką mieszaną grupą, ostrzał oszczędzać na jednoczesne cele. Atak bez przerwy może znieść także lukę między falami.
- **Gdzie/koszt:** kampania 7 lub 11, potyczka; przyszły rozkaz obronny z dozwolonymi rolami i bez wymuszenia ciągłego ruchu. Nie zwykłe fale Endless. M.
- **Ryzyko/miara:** zbyt częste przełączanie, podobieństwo do Szturmowca albo brak fal przy małym dochodzie. Liczyć odstępy między zmianami `hold`, wielkość grupy przy zwolnieniu, liczbę zwolnień i czas pierwszych dwóch w 90 s przeciw ASSAULT/SIEGER. Na obu ekranach ocenić wyraźne grupy i odstępy; uniknąć pojedynczych jednostek, których nie da się odróżnić.

## 3. Wymiana straży

Wróg po stracie osłony najpierw odbudowuje przód, a dopiero później znów uzupełnia dalsze szeregi. Gracz widzi nawracającą zmianę kolejności zakupów bez ukrytego wzmocnienia.

- **Symulacja:** w `pickRole` warunek liczby własnych żywych wręcz i jednostek w kolejce: przy braku osłony priorytet dozwolonej wręcz, w innym przypadku równowaga ciężkich i dystansu; przed zwrotem roli sprawdzać maskę oraz koszt. Bez nowego stanu, z konsekwencjami protokołu i zapisu opisanymi wyżej.
- **Kontra:** eliminować przednią straż i uderzać zanim strzelcy dostaną osłonę; ciężcy kontrowani przez dystans. Gracz może też wymusić wydatki ciągłą presją.
- **Gdzie/koszt:** kampania 13 lub 17, potyczka; przyszły rozkaz bez ograniczenia do jednej roli. Endless wymaga odrębnego systemu. S.
- **Ryzyko/miara:** istniejące `pickRole` już wymusza pierwszą wręcz (`Ai.kt:375`), więc nowy styl musi różnić się także później. Mierzyć czas od utraty ostatniej osłony do kolejnego wystawienia wręcz, udział wręcz w pierwszych 90 s i czas bez osłony, kontra BALANCED/ASSAULT. Na obu ekranach rozpoznanie przez przerwy w przednim szeregu i jego odtworzenie; potwierdzić nagraniami, nie samym licznikiem.

## 4. Wczesna reduta

Wróg wcześnie inwestuje w jedną wieżę, ale potem wystawia ruchomą mieszankę jednostek zamiast stale rozbudowywać fortyfikacje. Gracz widzi punkt obrony i może zdecydować, kiedy minąć jego zasięg.

- **Symulacja:** profil w `AiPersonality` i niewielka gałąź w `manageTurrets`: wczesny pierwszy zakup z rezerwą na jednostki, bez priorytetu kolejnych do określonego czasu; maska, sloty i legalność zakupu zawsze obowiązują. Oprzeć na liczbie aktualnych wież i czasie, bez nowego stanu; PROTOCOL i zapis jak wyżej.
- **Kontra:** zbudować grupę przed wejściem w zasięg, używać ciężkich z osłoną; nie karmić wieży pojedynczymi jednostkami.
- **Gdzie/koszt:** kampania 14 lub 18 po sprawdzeniu modyfikatora, potyczka. Nie etap/rozkaz z zakazem wież; w przyszłych rozkazach tylko z `TURRETS`. Endless i wyzwania rankingowe osobno. M.
- **Ryzyko/miara:** zbieżność z ENGINEER oraz zależność od dostępnych slotów i ekonomii. Liczyć czas pierwszej wieży, liczbę do 90 s i udział wydatków na jednostki po zakupie; cel eksperymentalny: pierwsza wieża wcześniej niż BALANCED, mniej kolejnych niż ENGINEER przy 90 s. Na 720p/Foldzie wieża jest większa od jednostek; ocenić jednocześnie widoczność ruchomej armii.

## 5. Zryw w kontakcie

Wróg naciera mieszaną grupą i oszczędza Zryw do zetknięcia z widoczną obroną. Moment przyspieszonej wymiany ciosów jest czytelnym sygnałem, ale gracz zna tę samą zdolność.

- **Symulacja:** nowy profil, w `useAbilities` próg `closeContact` i minimalna liczba własnych jednostek zamiast Zrywu podczas marszu; awaryjna obrona dozwolona. Sprawdzić `abilityReady` i maskę `RALLY`; żadnej wiedzy o niewidocznych wrogach, nowego stanu ani bonusu. PROTOCOL i zapis jak wyżej.
- **Kontra:** nie gromadzić bez osłony dużej grupy na styku; po wykorzystaniu Zrywu zaatakować podczas odnowienia. Przy braku kontaktu wróg nie powinien marnować zdolności.
- **Gdzie/koszt:** kampania 21 lub 25, potyczka; przyszły rozkaz, gdzie Zryw jest dozwolony. Nie obiecywać dla fal Endless. S.
- **Ryzyko/miara:** brak kontaktu w pierwszych 90 s i podobieństwo do BALANCED. Osobno raportować odsetek bitew z kontaktem do 90 s, opóźnienie Zrywu po pierwszym kontakcie, Zrywy na marszu i w zwarciu; porównać z ASSAULT/BALANCED. Sygnał na telefonie i Foldzie: istniejący efekt Zrywu przy linii walki; jeśli efekt ginie w tłumie, uznać czytelność za niepotwierdzoną.

## 6. Odpowiedź na widoczną przewagę

Wróg przez pierwszą minutę wystawia mieszaną armię, a po zobaczeniu powtarzającego się typu jednostki gracza częściej wybiera jej kontrę. Zmianę widać w następnych posiłkach; nie reaguje na niewidoczne wojska za mgłą.

- **Symulacja:** wariant `AiPersonality`, w `pickRole` mocniej ważyć już zliczone przez `observe` widoczne role przy co najmniej dwóch jednostkach jednej roli, zachowując minimum mieszanki oraz legalne role. `Ai.kt:152` i `Ai.kt:383` już obserwują i kontrują, więc dodać jakościowo wyższy, ograniczony parametr; bez nowego stanu. PROTOCOL i zapis jak wyżej.
- **Kontra:** zmieniać skład i nie wystawiać samego dystansu albo samych ciężkich; wymusić kosztowną kontrę, a następnie zagrać inną rolą. We mgle zmiana zaczyna się dopiero po ujawnieniu.
- **Gdzie/koszt:** kampania 22 lub 26, potyczka; przyszłe rozkazy tylko z co najmniej dwiema dozwolonymi rolami. Brak propozycji zmiany fal Endless lub istniejących rankingów. M.
- **Ryzyko/miara:** przypomina BALANCED i zbyt szybka reakcja może wyglądać na czytanie ukrytych zamiarów. W scenariuszach kontrolowanych wysłać identyczne widoczne dwie/trzy jednostki każdej roli, mierzyć udział prawidłowej kontry i czas pierwszego jej wystawienia do 90 s przeciw BALANCED; wykonać parę testów z identycznym polem widzenia i różną ukrytą armią. Na obu ekranach rozpoznawalność zależy od wyraźnie różnych sylwetek kolejnych posiłków; testować zbliżenie i tłok.

## Priorytet do prototypu

1. **Osłona i strzała** — najprostszy wizualny wzorzec grupy i intuicyjna kontra; zmierzyć odróżnienie od Inżyniera.
2. **Krótkie uderzenia** — różnicuje rytm, nie wymaga nowego typu grafiki; największe ryzyko nieosiągnięcia rytmu przy biednej gospodarce.
3. **Zryw w kontakcie** — wykorzystuje znaną zdolność i tworzy czytelny moment decyzji; wymaga potwierdzenia, że kontakt i efekt następują dostatecznie wcześnie.

## Pytania do właściciela

1. Czy wolisz, żeby nowi przeciwnicy byli od razu nazwani przed bitwą, czy żeby gracz rozpoznawał ich po zachowaniu? **Rekomendacja:** krótka nazwa w odprawie, zachowanie widoczne również bez czytania.
2. Czy możemy stopniowo zastępować część obecnych przeciwników w kampanii? **Rekomendacja:** najpierw pojedyncze późniejsze etapy po pomiarze trudności i testach graczy; nie ruszać samouczka.
3. Czy przeznaczyć czas na przeciwników o innym rytmie zamiast nowych bossów? **Rekomendacja:** najpierw trzy powyższe prototypy; nowe jednostki i bossowie to opcja, której ten raport nie wymaga.
