# R171 — propozycje wariantów epok

Stan źródeł: `origin/main` f17c6e25, 6.11.2026. To koncepcje, nie implementacja ani wynik pomiaru. Żaden z proponowanych wariantów nie ma jeszcze potwierdzonego balansu. `Defs.kt:4`, `:129` i `:190` określają cztery role, dane wariantu i epoki; aktualnie istnieją dwie pary dla Pary i Krzemu (`Defs.kt:221`, `:249`). Wersje w aktualnym kodzie to protokół 0.8.13 (`SimVersion.kt:5`), zapis 23 (`BattleSnapshot.kt:104`) i powtórka 7 (`Replay.kt:11`).

Zlecenie 28 wprowadziło pary, a pomiar opisany w `docs/BALANCE.md:79` porównuje wariant A z B, nie z jednostką podstawową. Po strojeniu 163 odnotowano 194:206 i 204:196, po 400 pojedynkach bez patów (`docs/BALANCE.md:120`); nie są to wyniki nowych pomysłów. Przy zwycięstwach 45–55% na 400 starć próg jest praktycznym filtrem, nie dowodem równości sił we wszystkich stylach gry.

## Wspólne założenia wdrożenia i testów

Wszystkie propozycje zmieniają **jedną** z czterech ról i dodają **jedną** cechę, uruchamianą tylko przez tę jednostkę. Podane liczby są punktami startowymi do strojenia, nie gwarancją. Powinny używać istniejących celów i widocznych zdarzeń, bez dodatkowego źródła losowości; jeśli losowanie będzie konieczne, wyłącznie `Battle.rng`. Zmiana reguł symulacji wymaga aktualizacji `SimVersion.PROTOCOL` przez koordynatora. Zdarzenia zależne od licznika lub czasu wymagają deterministycznego stanu w `Fighter` i serializacji w `BattleSnapshot`, zwiększenia wersji i testu wznowienia; cecha obliczana wyłącznie z już zapisanego stanu może uniknąć nowego pola, ale wymaga testu zgodności. Istniejące ID wariantu już trafia do zapisu (`BattleSnapshot.kt:540`) i komenda do powtórki (`Replay.kt:183`); rozszerzenie tylko katalogu wariantów nie musi zmieniać formatu replay, natomiast zmiana kodowania komend wymaga wersjonowania. Odcisk definicji w zapisie także należy uwzględnić (`BattleSnapshot.kt:148`).

Obecny ekran ma `Array(2)` przycisków (`BattleScreen.kt:328`, `:2022`), a AI dokonuje wyboru (`Ai.kt:424`). W epokach bez pary wdrożenie wymaga decyzji o drugim wariancie albo zaprojektowania jawnego wyboru podstawowego składu jako drugiej opcji; nie zakładać, że samo dopisanie jednego wpisu daje prawidłowy ekran. Nie proponuję trzeciego przycisku w epokach z istniejącą parą. Nowe nazwy i opisy wymagałyby osobnego, zatwierdzonego zadania lokalizacyjnego dla 10 języków; ten raport nie zmienia `L.kt`.

Dla każdego pomysłu: rozszerzyć `BalanceReport.variantDuel` o jawny wariant `-1` (podstawowy) przeciw nowemu, tak by wybór podstawowego składu nie był mylony z zerowym wariantem; 200 tych samych seedów × zamiana stron, `BALANCED` po obu stronach, identyczne zasoby i konfiguracja, limit 30 min, zgodnie z `docs/BALANCE.md:120`. Raportować wygrane, remisy i przekroczenia limitu oddzielnie, medianę czasu oraz wykorzystanie cechy; cel 180–220 zwycięstw nowego wariantu na 400 i zero patów, bez ukrywania timeoutów. Dodatkowo przetestować 2 osobowości, 2 poziomy zasobów, kampanię oraz interakcje z zasadami dozwolonych jednostek. Regresje: ta sama seria komend i seed daje identyczny wynik; zapis w trakcie efektu i wznowienie daje ten sam wynik; zgodność powtórek i serwera. Żadnych nowych zmian w Rozkazach sztabu w ramach niniejszego zadania.

Sylwetki są projektowane z dużych płaskich brył, konturów i dominującego koloru drużyny; możliwe do zbudowania prymitywami `Pen.kt:104–162`, z odpowiednią geometrią postaci lub machin w `Soldiers.kt` i `Machines.kt:19`. Czytelność 720p i zewnętrznego Folda 904×2316 trzeba dopiero sprawdzić na zrzutach oraz w ruchu, nie zakładam, że opis jest potwierdzeniem.

## 1. Krąg osłon — epoka plemienna

**Dla gracza.** W miejsce osiłka rusza tarczownik niosący dużą, drewnianą osłonę. Wspiera prowadzącego natarcie, lecz wymaga osłony przed strzelcami.

**Jednostka i cecha.** `HEAVY` zamiast `u_brute` (`Defs.kt:191`): wolniejszy, z mniejszym atakiem; jednorazowo po pierwszym otrzymanym trafieniu na 2 s ogranicza obrażenia własne o 20%. To jedna cecha osobista, bez aury, sumowania i ochrony bazy. `EraVariant` i fabryka `Defs.unit` potrzebują pola cechy, a krok trafienia w `Battle` aktywacji i licznika w `Fighter`; zapis licznika, protokół i testy jak wyżej.

**Gra i kontra.** Wysłać go przed strzelcem; przeciwnik może przeczekać efekt tanią piechotą, potem użyć dystansu kontrującego ciężkich (`Defs.kt:169`). **Tryby:** najpierw potyczka z włączonymi wariantami; kampania po osobnym doborze etapów, Endless i wyzwania po testach kombinacji; Rozkazy tylko po sprawdzeniu `enemyAllowed` i bez zmian trybu w tej pracy. **Koszt:** M. **Ryzyko:** przetrwanie pierwszej salwy i długie paty. **Pomiar:** wspólne 400, dodatkowo czas do śmierci i zużycie tarczy oraz udział zwycięstw przy przewadze dystansu. **Sylwetka:** duży pionowy owal przed tułowiem, szeroki ciemny rant i pojedynczy poziomy pas barwy drużyny; dwa kształty muszą pozostać widoczne na 720p i Foldzie.

## 2. Zwiad wydm — epoka brązu

**Dla gracza.** Lekki wóz zastępuje rydwan i otwiera walkę szybkim podejściem. Po pierwszym starciu zwalnia, więc nadaje się do zajmowania przestrzeni, a nie samotnego przełamywania obrony.

**Jednostka i cecha.** `HEAVY` zamiast `u_chariot` (`Defs.kt:196`): mniej HP; do pierwszego ataku lub otrzymania obrażeń +25% szybkości, potem normalna. `EraVariant`/`UnitDef` opisują cechę, `Fighter` zapisuje zużycie, `Battle` wyłącza je przy obu zdarzeniach; wymagany protokół i zapis stanu.

**Gra i kontra.** Wysyłać wóz przed droższą falą, ale nie bez wsparcia; tania jednostka kontaktowa odbiera przyspieszenie, a strzelcy kontrują ciężką rolę. **Tryby:** potyczka; później kampania w epokach brązu, Endless i wyzwania po ocenie presji początkowej, Rozkazy po sprawdzeniu ograniczeń składu. **Koszt:** M. **Ryzyko:** szybkie trafienie bazy zanim powstanie obrona. **Pomiar:** wspólne 400, czas pierwszego kontaktu, obrażenia bazy do 60 s i liczba wozów na froncie. **Sylwetka:** dwa duże koła, niska skośna platforma z trójkątną płachtą drużyny, mały jeździec; koła i płachta rozdzielone ciemnym konturem na obu ekranach.

## 3. Kuźnia taranów — epoka żelaza

**Dla gracza.** W miejsce skorpiona powstaje opancerzony taran, który jest groźny po dotarciu do umocnień. Musi jednak przeżyć drogę bez wsparcia dalekiego ostrzału dawnej elity.

**Jednostka i cecha.** `ELITE` zamiast `u_iron_scorpion` (`Defs.kt:201`): walka z bliska, niższa prędkość; pierwsze uderzenie w bazę zadaje dodatkowe 25% obrażeń bazowych tej jednostki. Cecha jednorazowa nie dotyczy jednostek ani wież. Nowy parametr w `UnitDef`/`EraVariant`, bit zużycia w `Fighter`, obsługa uderzenia w `Battle`, zapis bitu i protokół.

**Gra i kontra.** Torować drogę piechotą; przeciwnik zatrzymuje taran dystansem przed podejściem i kontratakiem na eskortę. **Tryby:** potyczka; później kampania, Endless i wyzwania tylko przy dozwolonej elicie, Rozkazy nie zmieniają swoich ograniczeń. **Koszt:** M. **Ryzyko:** zbyt duży skok obrażeń bazy lub bezużyteczność w zatłoczonym froncie. **Pomiar:** wspólne 400, liczba pierwszych trafień bazy, czas dojścia i udział elit w kosztach. **Sylwetka:** niski prostokątny dach na dwóch kołach z dużym wysuniętym klinem, pas barwy drużyny na dachu; klin wyraźny na 720p i Foldzie.

## 4. Chorągiew marszu — epoka feudalna

**Dla gracza.** Posłaniec zastępuje zwykłego piechura i nagradza zgrane natarcie. Jest kruchy, a jego wsparcie kończy się szybko.

**Jednostka i cecha.** `MELEE` zamiast `u_footman` (`Defs.kt:206`): mniejsza wytrzymałość; po pierwszym własnym trafieniu nadaje najbliższemu sojusznikowi w określonym promieniu 10% szybkości przez 2 s, bez odświeżania i kumulacji. Deterministyczny wybór przy równych odległościach według stałego porządku puli; nowy parametr w `UnitDef`/`EraVariant`, stan zużycia i czasu odbiorcy w `Fighter`, serializacja i protokół.

**Gra i kontra.** Działa, gdy piechur i sojusznik spotkają się na froncie; eliminacja posłańca na dystans albo rozdzielenie fali ogranicza efekt. **Tryby:** potyczka; później kampania, Endless i wyzwania po testach gęstych fal, Rozkazy wyłącznie po kontroli dozwolonych jednostek. **Koszt:** L. **Ryzyko:** kolejność wyboru celu, kumulacja i wzrost liczby obliczeń. **Pomiar:** wspólne 400 oraz liczba aktywacji, liczba odbiorców i dystans frontu po 90 s; test identycznych odległości i limitu efektu. **Sylwetka:** wąska postać z wysokim prostokątnym proporcem, jednolitym kolorem drużyny i grubym obrysem; proporzec nie może zasłaniać paska zdrowia na obu ekranach.

## 5. Straż ładunków — epoka prochu

**Dla gracza.** Zamiast muszkietera pojawia się strzelec z ciężką osłoną, który po zatrzymaniu przygotowuje celny pierwszy strzał. Silny na ustawionej linii, jest słabszy podczas szybkiego odwrotu i w starciu wręcz.

**Jednostka i cecha.** `RANGED` zamiast `u_musketeer` (`Defs.kt:211`): wolniejszy trening i niższe stałe obrażenia; po 2 s bez ruchu następny strzał zadaje 15% więcej, po czym licznik wraca do zera. `UnitDef`/`EraVariant` przechowują parametry, `Fighter` czas postoju, `Battle` resetuje go po ruchu lub strzale; deterministyczny czas symulacji i zapis.

**Gra i kontra.** Stawiać przed nim obronę; przeciwnik może skrócić dystans jednostkami wręcz, które mają premię przeciw dystansowym (`Defs.kt:170`). **Tryby:** potyczka, następnie kampania i Endless po testach obrony, wyzwania i Rozkazy po kontroli dostępnych ról. **Koszt:** M. **Ryzyko:** zakleszczenie frontu i nienaturalne zachęty do postoju. **Pomiar:** wspólne 400, odsetek wzmocnionych strzałów, mediana czasu walki i próby ruch–postój–strzał. **Sylwetka:** przysadzista postać, wysoka prostokątna osłona przed korpusem i wystająca długa lufa; kolor drużyny na osłonie, rozpoznawalny kształt na obu ekranach.

## 6. Brama stalowa — epoka stali

**Dla gracza.** Wóz osłonowy zajmuje miejsce półgąsienicowego transportera. Jego pierwsze trafienie przeciwnika utrudnia mu marsz, ale sam wóz nie zastąpi silnej linii strzelców.

**Jednostka i cecha.** `HEAVY` zamiast `u_halftrack` (`Defs.kt:239`): niższe obrażenia; pierwsze trafienie przeciwnika spowalnia ten cel o 20% na 2 s, nie działa na bazę i nie kumuluje się. Parametry w `EraVariant`/`UnitDef`, znacznik zużycia i ograniczony czas efektu w `Fighter`, zastosowanie w `Battle`; zapis, protokół i test deterministyczny. Wariant nie tworzy jednostek ani obiektów w każdej klatce.

**Gra i kontra.** Użyć do zatrzymania jednej szybkiej jednostki, a następnie wesprzeć ogniem; przeciwnik może poświęcić tanią pierwszą jednostkę i wykorzystać strzelców przeciw ciężkiej roli. **Tryby:** potyczka, następnie kampania i Endless po ocenie dużych fal; wyzwania oraz Rozkazy tylko tam, gdzie skład na to pozwala. **Koszt:** L. **Ryzyko:** nakładanie efektów z wielu wozów, spowolnienie elit lub utknięcie fali. **Pomiar:** wspólne 400, liczba skutecznych spowolnień, opóźnienie dojścia do frontu i wynik przy wielu wozach. **Sylwetka:** szeroki niski kadłub z dużą pionową płytą na nosie i dwiema widocznymi gąsienicami; płyta w kolorze drużyny, rozpoznawalna także na 720p i Foldzie.

## Priorytety i decyzje właściciela

1. **Zwiad wydm** — czytelna decyzja o szybkim początku i wyraźna kontra; efekt ma prostą granicę czasową przez pierwsze zdarzenie.
2. **Kuźnia taranów** — zmiana sposobu zdobywania bazy jest widoczna, ale wymaga szczególnej kontroli gwałtownych zakończeń.
3. **Krąg osłon** — łatwy do rozpoznania na małym ekranie i uczy osłaniania strzelców; trzeba sprawdzić paty.

Pytania do właściciela:

1. Czy w nowej epoce gracz ma wybierać między podstawowym składem a jednym nowym? **Rekomendacja:** tak, na początek; mieści się to w obecnym wyborze dwóch kart, choć wymaga jawnej obsługi podstawowego składu.
2. Czy najpierw sprawdzić nowe warianty w potyczce, a dopiero potem w kampanii? **Rekomendacja:** tak, by zebrać wyniki bez zmiany trudności istniejących etapów.
3. Czy jednostki mogą mieć krótkie efekty widoczne w walce? **Rekomendacja:** tak, pojedyncze i proste, z wyraźną sylwetką oraz bez zasłaniania pola walki.

## Weryfikacja i zakres

Przeanalizowano definicje, wybór i zapis oraz istniejącą procedurę pomiaru. Nie zmieniono kodu ani danych gry; nie uruchomiono Gradle ani symulacji, ponieważ jest to raport koncepcyjny. Brak wyników 45–55% i zrzutów to otwarte warunki przed ewentualnym wdrożeniem, nie potwierdzone kryteria.
