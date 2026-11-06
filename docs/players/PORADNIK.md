# Century War — poradnik gracza

## Spis treści

- [Pierwsza bitwa](#pierwsza-bitwa)
- [Złoto, doświadczenie i awans](#złoto-doświadczenie-i-awans)
- [Jednostki i kontry](#jednostki-i-kontry)
- [Wieże i zdolności](#wieże-i-zdolności)
- [Dowódca i doktryny](#dowódca-i-doktryny)
- [Badania i medale](#badania-i-medale)
- [Tryby gry](#tryby-gry)
- [Pogoda i modyfikatory](#pogoda-i-modyfikatory)
- [Ustawienia i bateria](#ustawienia-i-bateria)
- [Gdy coś idzie nie tak](#gdy-coś-idzie-nie-tak)

## Pierwsza bitwa

Twoim zadaniem jest obrona własnej bazy i pokonanie przeciwnika. Armie spotykają się na wspólnym polu bitwy. Nie sterujesz każdym wojownikiem osobno: wybierasz, kogo wyszkolić, kiedy rozbudować obronę i jak wykorzystać dostępne zdolności. W większości bitew zwyciężasz, niszcząc bazę wroga. Niektóre tryby stawiają inne cele, dlatego przed rozpoczęciem przeczytaj opis wyzwania.

Na początku wybierz **Zagraj** i przejdź samouczek. Wskazówki pokażą sterowanie podczas prawdziwej bitwy. Jeśli potrzebujesz chwili, otwórz **Pauza**; przycisk **Wznów** pozwoli wrócić do walki. Samouczek możesz później ponowić przez **Ustawienia → Podpowiedzi i samouczek → Otwórz → Powtórz samouczek**. Przydatne informacje o jednostkach, zasadach i zdolnościach znajdziesz także w **Kodeksie**.

Nie przejmuj się przegraną. Obejrzyj wynik i zastanów się, czy zabrakło ci obrony, dochodu, właściwych jednostek czy miejsca w armii. Potem możesz zagrać ponownie albo wrócić do menu i wydać zdobyte medale na badania. Nie każdą decyzję trzeba podjąć od razu: cierpliwa obrona i stopniowa rozbudowa też są sposobem na wygraną.

## Złoto, doświadczenie i awans

**Złoto** wydajesz w bieżącej bitwie na szkolenie, wieże i inne dostępne zakupy. Napływa w czasie walki, a pokonywanie przeciwników daje dodatkowe środki. Gdy wydasz wszystko na jeden rodzaj wojsk, możesz nie mieć czym odpowiedzieć na zmianę armii wroga. Zostawienie rezerwy pozwala szybko dostosować skład lub obronić bazę.

**XP** to doświadczenie potrzebne do rozwoju w trakcie bitwy. Zdobywasz je podczas walki, a także stopniowo w jej toku. Kiedy uzbierasz wymaganą ilość i dany etap pozwala na dalszy rozwój, możesz użyć awansu epoki. Przycisk nie zawsze będzie dostępny: znaczenie mają zgromadzone XP oraz granice ustawione dla konkretnego starcia.

Awans zużywa XP i otwiera kolejną epokę z innym uzbrojeniem. Nie oznacza automatycznego zwycięstwa: ważne są również istniejąca armia, stan bazy oraz złoto na nowe oddziały. Warto rozważyć, czy przed awansem nie trzeba odeprzeć nadchodzącego ataku. Z drugiej strony zbyt długie odkładanie awansu może utrudnić walkę z bardziej rozwiniętym wrogiem.

Gra obejmuje dziewięć epok: **Plemiona**, **Epoka brązu**, **Epoka żelaza**, **Feudalizm**, **Epoka prochu**, **Epoka pary**, **Epoka stali**, **Epoka krzemu** i **Era orbitalna**. Nie każda bitwa prowadzi przez wszystkie; dozwolony zakres zobaczysz przed startem. Źródło kolejności i liczby: [definicje epok](../../sim/src/main/kotlin/com/centurywar/game/Defs.kt#L190).

Przy niektórych awansach pojawia się wybór wariantu epoki. Przeczytaj jego opis, zanim zdecydujesz: wariant wpływa na dostępne jednostki i uzbrojenie. Wybór warto dopasować do tego, czy potrzebujesz lepiej utrzymać linię, szybko dotrzeć do przeciwnika czy wzmocnić ofensywę. Jeśli nie znasz różnic, sprawdź **Kodeks** i porównaj dostępne opisy zamiast wybierać wyłącznie po wyglądzie.

## Jednostki i kontry

Każda epoka ma cztery podstawowe role: walczących wręcz, dystansowych, ciężkich i elitarnych. Ich nazwy oraz wygląd zmieniają się wraz z epoką. Rola pomaga zrozumieć zadanie oddziału, ale nie zastępuje obserwacji sytuacji: pozycja, liczebność, poziom ulepszenia i wsparcie innych jednostek nadal mają znaczenie. W niektórych bitwach określony rodzaj wojska może być niedostępny. [Źródło ról](../../sim/src/main/kotlin/com/centurywar/game/Defs.kt#L4).

Walka wręcz dobrze odpowiada na jednostki dystansowe, jeżeli wojownicy zdołają do nich dotrzeć. Strzelcy są przydatni przeciw ciężkim przeciwnikom, ale potrzebują osłony przed napierającą piechotą. Ciężkie jednostki pomagają przełamywać oddziały walczące wręcz. Elita jest kosztowna i nadaje się do nacisku na bazę, lecz samo jej wyszkolenie nie gwarantuje zwycięstwa. Zależności między rolami zapisano w [macierzy kontr](../../sim/src/main/kotlin/com/centurywar/game/Defs.kt#L167).

Przy silnej grupie strzelców spróbuj utrzymać ich za linią swoich wojsk. Gdy wróg wystawia ciężkie oddziały, sprawdź, czy masz dość własnych strzelców i czasu, by zadawać obrażenia z dystansu. Jeśli twoi dystansowi znikają, zanim zdążą strzelać, dołóż jednostki osłaniające. Przeciw mieszanej armii zwykle warto wystawić kilka ról zamiast polegać tylko na jednej.

Możesz też ulepszać rodzaje jednostek podczas bitwy. Wyższy stopień wzmacnia dany rodzaj, lecz wymaga złota, które mogłoby posłużyć do szkolenia lub obrony. Ulepszenie jest szczególnie użyteczne, gdy zamierzasz wystawiać tę rolę wielokrotnie. Jeśli sytuacja zmieniła się na twoją niekorzyść, najpierw sprawdź, czy ważniejsza nie jest natychmiastowa rekrutacja.

Elitarne jednostki mogą wymagać odpowiedniego badania. Jeżeli przycisk jest niedostępny, sprawdź opis w grze i warunki bieżącej bitwy; ograniczenie trybu lub brak zasobów również mogą wpływać na dostępność. Na polu obserwuj limit armii oraz kolejkę szkolenia: gdy są pełne, następny zakup może wymagać oczekiwania.

## Wieże i zdolności

Wieże wspierają obronę bazy, ale kosztują złoto i zajmują dostępne miejsca. Otwórz panel wież, sprawdź cenę i wybierz miejsce zakupu. Rozbudowa pozwala wzmocnić istniejącą wieżę, a sprzedaż zwalnia miejsce. Liczba dostępnych miejsc zależy od sytuacji i badań; gra przewiduje maksymalnie cztery miejsca ([limit w definicjach](../../sim/src/main/kotlin/com/centurywar/game/Defs.kt#L162)).

Wieża jest najprzydatniejsza, gdy przeciwnik zbliży się do jej zasięgu. Nie zastąpi armii, która ma utrzymywać linię i atakować. Jeśli wróg blokuje twoje oddziały daleko od bazy, dalsze inwestycje w samą obronę mogą nie rozwiązać problemu. W wyzwaniach z zasadą **Bez wież** przygotuj plan oparty na wojskach i zdolnościach, bo zakupy wież będą niedostępne.

**Ostrzał** to zdolność ofensywna epoki. Gdy jest gotowy, dotknij jego przycisku i wskaż cel zgodnie z podpowiedzią na ekranie. Najwięcej może dać przeciw skupionej grupie lub w chwili, gdy przeciwnik próbuje przełamać obronę. Po użyciu musi się odnowić, więc zastanów się, czy wydać go od razu, czy zachować na groźniejszy atak. W niektórych wyzwaniach użycie zdolności może być ograniczone.

**Zryw** wzmacnia natarcie twojej armii i także wymaga odnowienia. Dobrze użyć go wtedy, gdy na polu masz już oddziały zdolne wykorzystać okazję. Aktywowanie go przy pustej armii daje mniej możliwości niż wsparcie przygotowanej grupy. Dostępność Zrywu poza bitwą może zależeć od badań, a w danym trybie także od jego zasad.

Postawa **Trzymaj** pomaga zatrzymać armię bliżej własnej strony. Wykorzystaj ją, kiedy chcesz zebrać siły lub chronić strzelców. Gdy sytuacja się poprawi, przełącz oddziały na natarcie. Zwracaj uwagę na front: samo oczekiwanie nie niszczy wrogiej bazy, a przeciwnik nie musi czekać, aż skończysz przygotowania.

## Dowódca i doktryny

**Dowódca** jest silną jednostką wspierającą pobliskich sojuszników. Możesz go przywołać, gdy jest dostępny, masz wystarczająco złota oraz wolne miejsce w armii. Walczy na polu, więc nie jest niezniszczalny. Osłaniaj go innymi wojskami, zamiast wysyłać samotnie na całą armię wroga. Po jego utracie ponowne przywołanie wymaga odczekania czasu wskazanego w grze.

Zwracaj uwagę także na wrogiego dowódcę. Usunięcie go może osłabić wsparcie jego armii, ale próba dotarcia do niego nie powinna całkowicie odsłaniać twoich strzelców. Jeśli dowódca nie jest dostępny w danej bitwie, przeczytaj jej warunki; niektóre tryby mają własne ograniczenia.

**Doktryny** to wybory wzmacniające wybrany styl walki. Mogą wspierać ekonomię, szkolenie, obronę, zdolności albo oblężenie. Czytaj opis proponowanych opcji: korzyść z większego dochodu jest inna niż korzyść z silniejszych ataków na bazę. Dobra doktryna pomaga rozwiązać aktualny problem, a nie tylko zwiększa wartość, która już jest wysoka.

W zwykłym awansie wybierasz jedną z trzech przedstawionych doktryn ([definicja wyboru](../../sim/src/main/kotlin/com/centurywar/game/Modes.kt#L3)). Gdy potrzebujesz szybko zbudować armię, zwróć uwagę na opcje gospodarcze lub szkoleniowe. Jeśli utrzymujesz front, ale nie możesz przebić się do bazy, rozważ wsparcie oblężenia. W Endless zamiast zwykłego wyboru przy awansie korzystasz z ofert kart; czytaj ich opisy, bo część może działać inaczej niż podobna doktryna.

## Badania i medale

Medale są nagrodą profilu, którą wydajesz w menu **Badania**. Różnią się od złota: złoto służy bieżącej bitwie, a zakupione badania rozwijają profil na przyszłość. Po wejściu w **Badania** przeczytaj opis i cenę następnego poziomu; koszt rozwoju zależy od danego badania i jego poziomu ([definicje badań](../../sim/src/main/kotlin/com/centurywar/game/Research.kt#L5)).

Na początku możesz zainteresować się odblokowaniem elity lub Zrywu, a następnie sprawdzić badania pomagające w dochodzie, przetrwaniu i szkoleniu. Nie musisz kupować wszystkiego w określonej kolejności. Jeżeli często brakuje ci wojsk, spróbuj poprawić gospodarkę albo tempo szkolenia; jeżeli przegrywasz po dotarciu wroga do bazy, rozważ obronę. Porównuj koszt z tym, co najbardziej przeszkadzało w ostatniej bitwie.

Medale możesz otrzymywać z różnych aktywności, a warunki nagród zależą od trybu. Przed zakupem sprawdź również **Misje** i osiągnięcia: pokazują cele oraz dostępne nagrody. Część nagród odbiera się przez odpowiedni ekran, dlatego warto do niego wrócić po spełnieniu celu. Nie zakładaj, że wynik pokazany na ekranie bitwy i saldo w menu oznaczają dokładnie ten sam rodzaj zasobu.

Badania nie działają we wszystkich trybach. W wyzwaniu dnia i oblężeniu tygodnia gra pokazuje wspólne zasady bez badań. Dzięki temu ważniejsze stają się decyzje podejmowane w samej bitwie. Zawsze sprawdź opis wybranego trybu przed startem zamiast zakładać, że zwykłe bonusy profilu będą aktywne.

## Tryby gry

**Kampania** prowadzi przez kolejne etapy. Wybierz odblokowany etap, przeczytaj zakres epok, siłę przeciwnika i ewentualne specjalne zasady, a potem wybierz **Start**. Możesz zmienić poziom trudności. Po zwycięstwie gra zapisuje postęp i gwiazdki; jeżeli kolejny etap sprawia trudność, wróć do wcześniejszych bitew lub rozważ zakupy w badaniach. Zestaw etapów jest budowany z epok i ich kolejnych części ([kampania](../../sim/src/main/kotlin/com/centurywar/game/Modes.kt#L190)).

**Potyczka** pozwala rozegrać osobne starcie. Sprawdź ustawienia przeciwnika, epok i dodatkowych zasad. To dobre miejsce do eksperymentów z nową taktyką, bez konieczności powtarzania tego samego etapu kampanii. Informacja na ekranie powie, czy wybrana konfiguracja jest standardowa, czy stanowi własną bitwę poza rankingiem.

**Wyzwanie dnia** oferuje wspólne zasady dnia. Przed rozpoczęciem sprawdź modyfikatory, epoki i informację o dostępności próby rankingowej. Po wykorzystaniu próby możesz ćwiczyć dalej w treningu. Dzień wyzwania jest liczony według czasu UTC, który może różnić się od lokalnej daty w telefonie ([obliczanie dnia](../../sim/src/main/kotlin/com/centurywar/game/Modes.kt#L637)).

**Oblężenie tygodnia** znajdziesz na karcie **Tydzień** w ekranie wyzwania. Każdy tydzień ma własny zestaw warunków oraz kartę startową; przejrzyj je przed walką. Starasz się osiągnąć jak najwyższą falę i przekroczyć widoczne progi. Możesz próbować ponownie, aby poprawić wynik. Tydzień rozpoczyna się w poniedziałek według UTC ([zasady tygodnia](../../sim/src/main/kotlin/com/centurywar/game/Modes.kt#L441)).

**Oblężenie bez końca** polega na przetrwaniu kolejnych fal. Rozbudowuj armię, wybieraj oferowane karty i obserwuj zapowiedzi trudniejszych przeciwników. Karta, która pomaga w danym składzie wojsk, może być cenniejsza niż premia niezwiązana z twoim planem. Gdy pobijesz własny wynik, wróć do niego później i porównaj decyzje w powtórce.

**Rozkazy sztabu** proponują odrębne zadania z warunkami określonymi przed bitwą. Otwórz **Rozkazy**, wybierz dostępne zadanie, przeczytaj cel i dozwolone działania, a następnie wybierz **Graj**. W trakcie walki kieruj się opisanym celem, nie zakładaj, że każda bitwa wymaga tego samego. Zawartość i szczegóły rozkazów mogą się zmieniać.

## Pogoda i modyfikatory

Przed bitwą sprawdź pogodę. **Deszcz** ogranicza zasięg oraz wpływa na poruszanie się i podpalenia. **Wiatr** zmienia zasięg pocisków w zależności od kierunku. **Noc** ogranicza widoczny zasięg części uzbrojenia. Obowiązujące efekty opisano w [definicjach pogody](../../sim/src/main/kotlin/com/centurywar/game/Modes.kt#L42); ich znaczenie zależy od składu obu armii.

Modyfikatory zmieniają reguły starcia. Na przykład **Podwójny przychód** zwiększa tempo zdobywania złota, **Kruche bazy** zmniejszają ich odporność, **Szybkie wojska** przyspieszają jednostki, a **Mgła wojny** utrudnia obserwację przeciwnika. Zasada **Bez wież** wymusza inny plan obrony. Nazwy i dostępne warianty są wyszczególnione w [definicjach modyfikatorów](../../sim/src/main/kotlin/com/centurywar/game/Modes.kt#L24).

Nie traktuj pogody i modyfikatora jako ozdoby. Jeśli wybrana taktyka opiera się na strzelcach lub wieżach, ograniczenie zasięgu może zmienić moment, w którym zaczną działać. Przy kruchych bazach opóźniona reakcja na przełamanie frontu bywa szczególnie kosztowna. W nieznanej konfiguracji rozpocznij ostrożnie i obserwuj zachowanie wroga.

## Ustawienia i bateria

W **Ustawieniach** zmienisz między innymi język, grafikę, limit płynności, dźwięk i wibracje. Jeśli obraz zacina się lub urządzenie wyraźnie się nagrzewa, spróbuj niższej jakości grafiki albo niższego limitu FPS. Automatyczna jakość może dostosować szczegóły do warunków pracy; ręczny wybór pozwala sprawdzić inne ustawienie. Efekty zależą od telefonu i jego systemu.

Niższa płynność lub prostsza grafika może ograniczyć obciążenie, ale nie gwarantuje określonego czasu pracy na baterii. Długie sesje, jasność ekranu, temperatura otoczenia i inne aplikacje również mają znaczenie. Jeśli telefon robi się nieprzyjemnie gorący, przerwij grę i pozwól mu ostygnąć. Podczas porównywania ustawień warto zwrócić uwagę, czy bitwa nadal jest czytelna i czy sterowanie reaguje wygodnie.

W ustawieniach znajdziesz także opcje powiadomień i godzinę przypomnienia. Ich działanie zależy od zgody w systemie Android i ustawień urządzenia. Możesz je wyłączyć, jeżeli nie chcesz otrzymywać przypomnień. Zmiana języka wpływa na teksty gry; jeśli wybierzesz ustawienie automatyczne, znaczenie ma język urządzenia.

## Gdy coś idzie nie tak

Jeśli musisz przerwać, przejdź do **Pauza** i opuść grę. Po powrocie gra może zaproponować **Wznowić bitwę?**. Wybierz **Wznów**, aby kontynuować, albo **Porzuć**, jeśli nie chcesz jej kończyć. Po aktualizacji nie każdy starszy zapis bitwy musi być zgodny; czytaj komunikaty i zachowaj profil.

Po zakończonych bitwach możesz wejść w **Rekordy → Powtórki**, aby obejrzeć dostępny zapis. Powtórka z innej wersji gry może nie dać się odtworzyć. Oglądając walkę, szukaj momentu utraty frontu albo chwili, gdy przeciwnik zmienił skład. To często pomaga bardziej niż pojedynczy wynik punktowy.

W **Ustawienia → Zaawansowane** znajdziesz zapis i odczyt profilu z pliku. Eksport utwórz w prywatnym miejscu. Import zastępuje bieżący postęp, więc przed potwierdzeniem dokładnie przeczytaj komunikat i wybierz właściwy plik. Jeżeli pojawi się problem z zapisem, sprawdź wolne miejsce i zgłoś, co robiłeś przed komunikatem.

W menu **O grze** dostępne są opcje zgłoszenia problemu oraz raportu dla testera. Udostępnienie wymaga twojego działania i wyboru odbiorcy. Przed wysłaniem przeczytaj raport, usuń zrzuty zawierające prywatne informacje i opisz wersję gry, model telefonu oraz kolejne kroki prowadzące do problemu.
