# Recenzja projektu gry — 169

**Oceniany `origin/main`:** `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7` (6.11.2026). To przegląd dokumentacji i kodu, nie test gry z ludźmi. Materiały: `README.md`, `century-war.md`, `docs/GDD.md`, początek `docs/COORDINATOR_HANDOVER.md`, `docs/BALANCE.md`, `docs/unit-upgrades-balance.md`, `docs/COUNTERS.md`, raporty `d37-balans-19-36`, `d40-k1`, `d58-tresc`, `d33-pomiar-19`, `d35-etap-19`, `noc-5-pierwsza-godzina`, `d45-lejek`, backlogi `R1`, `R10`, `R11`, `R12`, `docs/store/monetization_plan.md`; ponadto definicje, tryby, badania, karty, misje, cele, powrót, nowości etapu i nakładki gry. Liczby historycznych raportów są oznaczone jako takie i nie stanowią nowego pomiaru tej rewizji.

## Dla właściciela

Gra ma mocny, łatwy do opisania rdzeń: budowanie armii, kontry i awans przez epoki prowadzą do zniszczenia bazy. Kampania, badania oraz tryby dzienne i tygodniowe dają kilka powodów, by wracać. Pierwsze ryzyko to długość sesji: część późnych bitew w modelu trwa ponad 10 minut, a kolejne próby wydłużają czas osiągnięcia celu. Drugie to wrażenie niesprawiedliwej ściany na niektórych etapach lub przy korzystaniu z Wodza; dane z symulacji są sygnałem, lecz nie dowodem zachowania prawdziwych graczy. Trzecie to zbyt wiele równoczesnych celów i zasad, przez co nagroda lub powód porażki mogą być trudne do zrozumienia. Istniejąca diagnoza po porażce i wskazówki są dobrą podstawą, ale trzeba sprawdzić, czy gracze naprawdę je wykorzystują. Plan dobrowolnej monetyzacji dobrze szanuje czas gracza, lecz bonus medali może pośrednio dać przewagę w rankingach. W pierwszej kolejności polecam obserwowane testy ludzi, a przed zmianą balansu lub dodaniem kolejnych funkcji — pomiary i jasne kryteria decyzji.

## Ocena obszarów

### Balans i tempo

`Modes.kt:190–214` buduje kampanię jako cztery etapy na epokę, razem 36, i udostępnia poziomy trudności (`Modes.kt:99`). D35 dla 100 seedów po zmianie etapu 19 podaje profil „medale-czas” 76% wygranych i 12,1 min oczekiwanego czasu oraz profil „medale” 65% i 19,7 min (`docs/reports/d35-etap-19.md`, tabela). To model AI z założoną progresją, nie odsetek graczy. Historyczny D37 znalazł 57% na 19 z dowódcą wobec 88% na 18 i 76% bez dowódcy; związek ze sposobem gry jest hipotezą, a nie dowodem przyczynowości (`d37-balans-19-36.md`, „Klify”). K1 zatwierdzono w nowszym `docs/BALANCE.md` (sekcja „Etapy 28 i 30–33”), dlatego nie należy opisywać wcześniejszych 40–45% jako obecnego stanu: w pomiarze K1 profil medale-czas miał 50–65% na zmienionych etapach i 160 min oczekiwanego czasu na etapach 28–36 (`d40-k1.md`, tabela). Ocenić obecny kod i ludzi przed kolejną korektą.

Kontry tworzą czytelną pętlę: wręcz przeciw dystansowi +35%, dystans przeciw ciężkim +30%, ciężcy przeciw wręcz +35% (`Defs.kt:169`, `docs/COUNTERS.md`). Premia wieży przeciw późnym ciężkim to 15% (`Defs.kt:178–181`), nie niszczenie wież; wieże nie mają HP (`COUNTERS.md`). Gracz może nie dostrzec wpływu kontry w zatłoczonej walce — to hipoteza do sprawdzenia, nie potwierdzona wada. Nie ma dowodu na jedną globalnie dominującą strategię: doktryny porównywano w krótkich walkach AI, a dokument zaznacza konieczność kolejnych pomiarów po strojeniu (`BALANCE.md`, zlecenia 69 i 82). Karty Endless mają ograniczenie do ośmiu stosów i malejące przyrosty (`BALANCE.md:469–471`); D58 opisuje także cztery dodatkowe karty i wcześniejszy pomiar, lecz wyniku nie można uogólniać na wszystkie kombinacje ludzi. Wódz może zmienić rytm walki, lecz jego koszt i odpowiedź wroga wymagają obserwacji na 19, 28 i w późnej grze.

### Progresja i cele

Badania za medale dają wymierny wybór wzmacniania, a gwiazdki i korony umożliwiają powrót do wcześniejszych etapów. W historycznym przejściu testowym nagroda pierwszego etapu wyniosła 71 medali, a z osiągnięciami przyrost 196; najtańsze badanie kosztowało 30 (`noc-5-pierwsza-godzina.md`, „Oś czasu” i „Co działa”). To nie gwarantuje identycznej ekonomii na aktualnym HEAD. `Research.kt:6` opisuje koszt rosnący z poziomem, `Missions.kt:16` typy celów, a `NextGoal.kt:34` już wybiera kolejny cel; nie potrzeba kolejnej konkurencyjnej nawigacji. Epoki i pary wariantów dają zmianę składu, lecz warto sprawdzić, czy po zakupie gracz potrafi wskazać, co się zmieniło na polu. Zagrożenie „pustą” progresją to wniosek do zweryfikowania, zwłaszcza dla małych kolejnych poziomów badań, nie stwierdzony ubytek retencji. Nie proponuję zmian osiągnięć ani Play Games.

### Retencja

Dzień 1 może opierać się na powrocie do kampanii i wyzwaniu; dzień 7 na cotygodniowym celu; dzień 30 na różnorodności kombinacji i osobistym wyniku. To propozycja oceny, nie zmierzona retencja. `Weekly` przewiduje nagrody 25/60/120 (`Modes.kt:461`), a `WelcomeBack.kt:17–19` premię 30 po co najmniej 3 dniach. Misje używają dnia i tygodnia UTC (`Missions.kt:165–181`), więc zrozumiałość resetu dla graczy w różnych strefach wymaga testu. Już istniejące powitanie, cele i przypomnienia powinny być oceniane jako zestaw, bez dodawania kolejnych nagród za codzienny przymus. Dane przedpremierowego wyzwania dziennego nie reprezentują pełnej oferty po premierze; daty startu nie zmieniamy.

### Pierwsza godzina

Historyczny `FirstHourJourneyTest` obejmował rzeczywiste dotknięcia samouczka, lecz dalszymi bitwami sterowała AI; na czterech przebiegach telefon/Fold i PL/EN pierwszy etap trwał około 8,5 min, a etap 5 zakończył się około 61. minuty symulacji (`noc-5-pierwsza-godzina.md`). Późniejsze skracanie etapów sprawia, że nie wolno traktować tej osi jako obecnej. Dobry moment „aha” to samodzielne zestawienie typu przeciwnika z kontrą, po którym gracz widzi efekt; obecnie nie wiemy, czy to faktycznie następuje. Samouczek, zapowiedzi i dymki mają reguły unikania nakładania, jednak test nie zastępuje obserwacji człowieka. Zwrócić uwagę na przechodzenie między nagrodą, badaniami i kolejną bitwą oraz pierwszą porażkę.

### Monetyzacja

Obecna gra jest darmowa bez reklam i zakupów (`docs/store/monetization_plan.md`, status); dobrowolny film i kosmetyka pozostają planem. Ten sam plan przyznaje, że podwójne medale przyspieszają badania wpływające na wynik części trybów. Dopóki rankingi rywalizacyjne nie zapewniają identycznych warunków, odłożyć mnożniki medali i rozważać wyłącznie kosmetykę; testować identyczny seed i komendy na profilach z zakupem i bez. Pakiet „bez reklam” musi jasno mówić, że chodzi o dobrowolne oferty, a nie o usuwanie wymuszonych przerw. Bez płatnej przewagi, losowania za pieniądze i reklam wymuszonych.

### Informacja zwrotna

Nagroda po porażce zmniejsza poczucie zmarnowanego czasu (historycznie etap 8: 39 za porażkę, 79 za wygraną w opisanej próbie). Istnieją `LostBattle`, `StageNovelty` i wybór celu, więc zamiast powielać podpowiedzi warto sprawdzić ich zgodność z rzeczywistymi zdarzeniami oraz rozumienie przez graczy. Największe ryzyko to połączenie przyczyny porażki z niepowiązaną poradą i pomylenie nagrody podstawowej z osiągnięciem. Ocenę wyglądu i dotyku trzeba wykonać na rzeczywistych 720p oraz zewnętrznym Foldzie; w tej recenzji nie oglądano aktualnego builda na urządzeniach.

## Propozycje

S ≤ 1 dzień, M 2–4 dni, L tydzień lub więcej. Wszystkie propozycje poniżej dotyczą badań, scenariuszy, walidacji lub redakcji; nie przewidują zmian symulacji. Nie powtarzają projektów nowych funkcji z R1/R10/R11/R12; tamte rozwiązania można osobno utrzymać i ocenić.

| ID | Tytuł | Problem | Propozycja | Priorytet | Koszt | Wpływ na symulację | Ryzyko |
|---|---|---|---|---|---|---|---|
| D169-01 | Próba rozpoznania kontr | Nie wiemy, czy gracz rozumie relacje ról | W teście pierwszych pięciu bitew poprosić o wybór jednostki przeciw ciężkiej i wyjaśnienie decyzji; zanotować bez podpowiadania | Przed testami | S | nie | Mała próba nie mierzy populacji |
| D169-02 | Protokół sesji przerywanej | Czas symulacji nie odpowiada zawsze czasowi gracza | W badaniu mierzyć czas rzeczywisty do decyzji o przerwie, pauzy i liczbę prób; raportować osobno ×1/×3/×5 | Przed testami | S | nie | Obserwacja może zmieniać zachowanie |
| D169-03 | Porównanie informacji po porażce | Diagnoza może wskazywać nieistotną przyczynę | Dla 10 zgłoszonych porażek porównać ekran wyniku, dane bitwy i relację gracza; oznaczyć fałszywą lub niezrozumiałą wskazówkę | Przed testami | M | nie | Zbyt mała liczba obserwacji |
| D169-04 | Próba użycia Wodza | Historyczne 19 z Wodzem ma spadek 88→57% | Obserwować graczy na 18–20, zapisać zakup, moment wezwania, zasoby i wynik; zestawić z lejkiem D45 bez automatycznej wysyłki | Przed testami | M | nie | Korelacja nie dowodzi przyczyny |
| D169-05 | Czytelność zakupu badań | Wzmocnienie może wyglądać na abstrakcyjną liczbę | W badaniu poprosić gracza przed i po zakupie o opis oczekiwanej zmiany; wyniki posłużą do osobnej decyzji redakcyjnej | Przed testami | S | nie | Niewielki efekt może być trudny do zauważenia |
| D169-06 | Wartość ostatnich poziomów | Wysoki koszt może dawać poczucie pustej progresji | Na kopiach profili obliczyć koszt kolejnych poziomów i liczbę typowych nagród potrzebnych do zakupu, osobno dla etapów 8, 19 i 28 | Przed premierą | M | nie | Model dochodów zależy od stylu gry |
| D169-07 | Kontrola skutków jednego typu armii | Dominacja jednej kompozycji nie jest potwierdzona | W istniejącym narzędziu badawczym poza kodem gry porównać cztery polityki składu na tych samych seedach i stronach; wyniki z niepewnością | Przed premierą | M | nie | AI nie odtwarza taktyk człowieka |
| D169-08 | Audyt synergii kart | Średnia fala ukrywa skrajne kombinacje | Przetestować pary kart z tymi samymi seedami i badaniami, raportować medianę, odsetki fal 40/50/60 oraz ucięcia; nie stroić w ramach tego badania | Przed premierą | M | nie | Duża liczba kombinacji; ograniczyć próbę |
| D169-09 | Test zrozumienia resetu | Dzienny i tygodniowy cykl liczony w UTC może zaskakiwać | W scenariuszu testowym podać czas przed i po północy UTC i zapytać o przewidywanie odnowienia; sprawdzić różne strefy | Przed premierą | S | nie | Wynik nie mówi o faktycznej retencji |
| D169-10 | Kontrola uczciwości przyszłych bonusów | Medale mogą pośrednio wpłynąć na ranking | Przygotować macierz rankingów, źródeł badań i warunków startu; warunek dopuszczenia bonusu: równy wynik z identycznego seeda i komend oraz brak szybszego dostępu do przewagi | Przed premierą | M | nie | Test jednego seeda nie dowodzi pełnej uczciwości |
| D169-11 | Sprawdzenie zestawienia nagród | Gracz może pomylić medale bitwy i osiągnięcia | W testach poprosić o wyjaśnienie składników przed przejściem dalej, porównać z rzeczywistym rozliczeniem | Przed premierą | S | nie | Nie stwierdza błędu rachunkowego bez porównania danych |
| D169-12 | Dzień 1/7/30 bez presji | Istnienie trybu nie dowodzi powrotów | W dobrowolnej ankiecie testowej zapytać, co skłoniło do powrotu i co zniechęciło; agregować odpowiedzi bez identyfikatorów | Po premierze | M | nie | Stronniczość osób, które odpowiadają |

## Ograniczenia i weryfikacja

Nie zmieniono kodu, balansu ani innych dokumentów, nie uruchomiono Gradle i nie wykonano nowego pomiaru ani testów na urządzeniach. Wymienione wcześniejsze badania zachowują własne warunki i ograniczenia; przed decyzją o zmianie wartości trzeba powtórzyć pomiar na właściwej rewizji. R1 proponuje m.in. cele i pomoc po porażce, R10 powrót po przerwie, R11 kamienie milowe, a R12 pomiar ekonomii 19+; niniejsza lista koncentruje się na pytaniach i procedurach weryfikacji tych obszarów, bez kopiowania tych funkcji.
