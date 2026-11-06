# Century War — scenariusze dla testerów

Wykonuj zadania na swojej wersji testowej, bez pośpiechu i bez potrzeby wygrywania każdej bitwy. Dni poniżej liczymy od rozpoczęcia fali; rozkład wspiera [plan R13](../research/R13-testy.md), w którym raport zbieramy w dniu 3 i 10. Czasy to aktywna praca testera, maksymalnie 10 minut na scenariusz; jeśli bitwa trwa dłużej, zapauzuj ją i dokończ później. Testy mogą korzystać z postępu zdobytego wcześniej. Nie resetuj danych ani nie odinstalowuj gry, żeby zacząć od nowa; S-01 wykonaj na czystej instalacji lub oddzielnym profilu testowym.

| ID i scenariusz | Dzień fali | Czas aktywny |
|---|---:|---:|
| S-01 Pierwszy start | 1 | 3 min |
| S-02 Samouczek | 1 | 8 min |
| S-03 Kampania: pierwszy etap | 1–2 | 8 min |
| S-04 Kampania: etapy 2–4 | 2–7 | 8 min |
| S-05 Pauza i powrót z tła | 2 | 5 min |
| S-06 Przerwanie i wznowienie | 2–4 | 6 min |
| S-07 Obrót i Fold | 2–7 | 5 min |
| S-08 Zmiana języka | 2–7 | 4 min |
| S-09 Wyzwanie dnia | 3–7 | 8 min |
| S-10 Oblężenie tygodnia | 4–9 | 8 min |
| S-11 Oblężenie bez końca | 4–9 | 8 min |
| S-12 Potyczka | 4–9 | 8 min |
| S-13 Powtórki | 5–10 | 5 min |
| S-14 Kopia profilu | 5–10 | 8 min |
| S-15 Rozkazy | 6–12 | 8 min |
| S-16 Raport dla testera | 3 i 10 | 5 min za każdym razem |
| S-17 Przypomnienia | 3–10 | 5 min ustawienia i kontroli |
| S-18 Bateria i praca w tle | 8–14 | 6 min aktywnych |

## Wspólne zasady zgłaszania

Przy problemie zanotuj ID scenariusza, numer wersji z **Ustawienia → O grze → Otwórz**, model telefonu, wersję Androida, orientację i godzinę, kroki, wynik oraz to, czy problem powtórzył się po ponownym uruchomieniu. Jeśli możesz, dołącz zrzut lub krótki film bez prywatnych powiadomień. Gra niczego nie wysyła sama: wyślij materiał wyłącznie kanałem podanym przez organizatora; adres formularza lub odbiorca zostaną przekazane osobno. Nie podawaj haseł, danych konta ani numeru telefonu. Kopie profilu trzymaj prywatnie i nie udostępniaj bez potrzeby. „Raport dla testera” może zawierać kod testera, dane urządzenia, postęp i ustawienia — obejrzyj go przed wysłaniem. Problemy z awarią lub utratą postępu zgłaszaj od razu.

## S-01 — pierwszy start

**Cel:** sprawdzić pierwsze uruchomienie. **Urządzenie/ustawienia:** Android, czysta instalacja testowa, do 3 min.

1. Uruchom grę i zamknij lub przeczytaj widoczne okna przyciskiem **Dalej**, jeśli się pojawią.
2. Obejrzyj menu i wybierz **Zagraj**.

**Oczekiwany wynik:** gra uruchamia się, sterowanie reaguje, zaczyna się pierwszy etap z samouczkiem. **Do zgłoszenia:** ekran, na którym start się zatrzymał, czas oczekiwania i wersja gry.

## S-02 — samouczek

**Cel:** sprawdzić czytelność wskazówek. **Urządzenie/ustawienia:** pierwszy etap lub samouczek przywrócony w **Ustawienia → Podpowiedzi i samouczek → Otwórz → Powtórz samouczek**, do 8 min.

1. Wybierz **Zagraj** albo **Kampania**, etap 1 i **Start**. Wykonuj widoczne polecenia; korzystaj z **Dalej**, gdy jest dostępne.
2. Jeśli do końca zabraknie czasu, użyj **Pauza** i wróć później. Nie musisz pomijać samouczka; jeśli chcesz sprawdzić rezygnację, **Pomiń samouczek** wymaga **Tak, pomiń**.

**Oczekiwany wynik:** wskazówki odpowiadają wykonywanym działaniom; po ukończeniu pojawia się **Do boju!**. **Do zgłoszenia:** numer lub tekst kroku, którego nie udało się wykonać, i zrzut.

## S-03 — kampania: etap 1

**Cel:** poznać pierwszą bitwę i jej wynik. **Urządzenie/ustawienia:** etap 1 rozpoczęty w S-02, do 8 min aktywnie.

1. Kontynuuj bitwę i szkol jednostki; obserwuj, czy złoto, jednostki i bazy się zmieniają.
2. Gdy bitwa się zakończy, obejrzyj ekran wyniku i wybierz **Dalej**. Jeśli potrzeba więcej czasu, użyj **Pauza** i dokończ później.

**Oczekiwany wynik:** wynik odpowiada przebiegowi, a po zwycięstwie etap jest oznaczony w kampanii. Przegrana sama w sobie nie jest błędem. **Do zgłoszenia:** wynik i zrzut, jeśli wynik lub postęp się nie zgadza.

## S-04 — kampania: etapy 2–4

**Cel:** sprawdzić wybór kolejnych etapów i zapis gwiazdek. **Urządzenie/ustawienia:** profil z odblokowanym odpowiednim etapem; do 8 min na tę sesję.

1. W menu wybierz **Kampania**, obejrzyj etap 2, 3 lub 4 i wybierz dostępny etap oraz **Start**.
2. Zagraj do 7 minut; jeśli bitwa się skończy, wybierz **Dalej** i sprawdź mapę. Inaczej zapauzuj i kontynuuj innym razem. Powtórz w kolejnych dniach dla pozostałych dostępnych etapów.

**Oczekiwany wynik:** dostępne etapy można uruchomić; zablokowane pozostają oznaczone; po zwycięstwie wynik jest widoczny po powrocie. **Do zgłoszenia:** numer etapu, poziom trudności, wynik i zrzut mapy.

## S-05 — pauza i tło

**Cel:** sprawdzić wznowienie po przełączeniu aplikacji. **Urządzenie/ustawienia:** trwająca bitwa, do 5 min.

1. Wybierz **Pauza**, zapamiętaj pole bitwy i czas. Wybierz **Wznów**.
2. Przejdź do ekranu głównego Androida, po około minucie wróć do gry i wybierz **Wznów**, jeśli gra jest zapauzowana.

**Oczekiwany wynik:** gra wraca do tej samej bitwy, a w czasie pauzy akcja nie postępuje. **Do zgłoszenia:** przybliżony czas w tle, zrzut przed i po oraz informacja o zmianie stanu.

## S-06 — przerwanie i wznowienie

**Cel:** sprawdzić odzyskanie zapisanej bitwy. **Urządzenie/ustawienia:** trwająca nierankingowa bitwa; do 6 min, bez odinstalowywania.

1. Zagraj chwilę, wróć do ekranu głównego Androida i odczekaj około 20 sekund. Z ustawień Androida wymuś zatrzymanie gry; nazwa tej opcji zależy od urządzenia.
2. Otwórz grę ponownie. Jeśli pojawi się **Wznowić bitwę?**, wybierz **Wznów** i porównaj stan.

**Oczekiwany wynik:** gra proponuje dokończenie przerwanej bitwy i odtwarza ostatni zapisany stan; krótka różnica od chwili wyjścia może wynikać z momentu zapisu. **Do zgłoszenia:** tryb, czas w tle, czy było wymuszone zatrzymanie, ekran oferty i opis różnicy. Nie wybieraj **Porzuć**, jeśli chcesz zachować bitwę.

## S-07 — obrót i Fold

**Cel:** sprawdzić zmianę rozmiaru obrazu. **Urządzenie/ustawienia:** zwykły telefon z automatycznym obrotem albo Fold; do 5 min.

1. W menu i w zapauzowanej bitwie obróć telefon; na Foldzie dodatkowo złóż i rozłóż ekran.
2. Dotknij **Wznów**, sprawdź widoczność pola i reakcji przycisków.

**Oczekiwany wynik:** obraz i dotyk pozostają dopasowane; na telefonie orientacja pozioma może zostać zachowana. **Do zgłoszenia:** model, ustawienie obrotu i zdjęcia obu stanów.

## S-08 — język

**Cel:** sprawdzić zmianę i zachowanie ustawienia. **Urządzenie/ustawienia:** dowolny telefon; do 4 min.

1. Wybierz **Ustawienia → Język**, przełącz na inny język i wróć przyciskiem **Wstecz**.
2. Obejrzyj menu i ponownie otwórz ustawienia. Przywróć swój język.

**Oczekiwany wynik:** widoczne etykiety zmieniają język i wybór utrzymuje się po powrocie. **Do zgłoszenia:** oba języki, ekran oraz ucięty lub błędny napis.

## S-09 — wyzwanie dnia

**Cel:** sprawdzić zasady i oznaczenie próby. **Urządzenie/ustawienia:** dowolny telefon, do 8 min aktywnych; termin dnia jest liczony według UTC.

1. Z menu wybierz **Wyzwanie dnia**, przeczytaj zasady i status **Podejście rankingowe**; wybierz **Graj**.
2. Zagraj kilka minut; wynik dokończ później. Po zakończeniu wróć do **Wyzwanie dnia** i sprawdź, czy kolejna próba jest oznaczona jako trening.

**Oczekiwany wynik:** pierwsza dostępna próba jest oznaczona jako rankingowa, następna po jej wykorzystaniu jako trening. Wynik nie wymaga uruchomienia własnego serwera. **Do zgłoszenia:** dzień UTC, status przed i po, wynik lub zrzut.

## S-10 — oblężenie tygodnia

**Cel:** sprawdzić tygodniowe zasady i progi. **Urządzenie/ustawienia:** dowolny telefon, do 8 min aktywnych.

1. Wybierz **Wyzwanie dnia → Tydzień**; zapisz modyfikatory i widoczną kartę początkową, następnie **Graj**.
2. Zagraj kilka minut i, jeśli potrzebujesz więcej czasu, wróć później. Po wyniku obejrzyj progi i wynik tygodnia.

**Oczekiwany wynik:** ekran ma tytuł **Oblężenie tygodnia**, pokazuje zasady i progi; liczba prób nie jest ograniczona. **Do zgłoszenia:** numer tygodnia, fala, zasady i zrzut wyniku.

## S-11 — oblężenie bez końca

**Cel:** sprawdzić fale i wybór karty. **Urządzenie/ustawienia:** dowolny telefon, do 8 min aktywnych.

1. Wybierz **Oblężenie bez końca → Graj** i obserwuj licznik fal.
2. Jeśli dotrzesz do wyboru karty, wybierz jedną i kontynuuj. Możesz zapauzować przed upływem czasu sesji.

**Oczekiwany wynik:** fale postępują, a po osiągnięciu odpowiedniego progu pojawia się wybór karty; nie trzeba dotrzeć do konkretnej fali w 8 minut. **Do zgłoszenia:** fala, nazwa karty i zrzut, jeśli wybór lub walka utknęły.

## S-12 — potyczka

**Cel:** sprawdzić własne ustawienia bitwy. **Urządzenie/ustawienia:** dowolny telefon, do 8 min aktywnych.

1. Wybierz **Potyczka**, zmień widoczny poziom trudności lub ustawienia przeciwnika i wybierz **Graj**.
2. Zagraj kilka minut; po wyniku sprawdź opis bitwy. Jeśli trwa dłużej, zapauzuj ją.

**Oczekiwany wynik:** bitwa uruchamia się z wybranymi ustawieniami, a po zakończeniu widoczny jest wynik. **Do zgłoszenia:** wybrane ustawienia, przebieg i zrzut.

## S-13 — powtórka

**Cel:** sprawdzić zapis i odtwarzanie. **Urządzenie/ustawienia:** co najmniej jedna zakończona bitwa w tej wersji; do 5 min.

1. Otwórz **Rekordy → Powtórki** i dotknij wiersza ostatniej powtórki poza ikonami po prawej; odtwarzanie ruszy automatycznie.
2. Poczekaj kilkanaście sekund, użyj **Pauza** i następnie **Odtwórz**.

**Oczekiwany wynik:** powtórka odtwarza się i reaguje na pauzę; zapisy ze starszych wersji mogą być oznaczone jako niezgodne. **Do zgłoszenia:** tryb, data bitwy, komunikat i moment problemu.

## S-14 — kopia profilu

**Cel:** sprawdzić eksport i odczyt tej samej kopii. **Urządzenie/ustawienia:** profil testowy, bez trwającej bitwy; do 8 min. Import zastępuje bieżący postęp.

1. Zapisz liczbę medali i etap. Wybierz **Ustawienia → Zaawansowane → Zapis profilu do pliku → Eksport** i zapisz plik w prywatnym miejscu.
2. Bez zmiany postępu wybierz **Odczyt profilu z pliku → Import**, ten sam plik oraz potwierdź **Import**. Po komunikacie wróć do kampanii.

**Oczekiwany wynik:** pojawia się **Profil zapisany do pliku**, a następnie **Profil przywrócony z pliku**; zapisane medale i etap są zachowane. Jeśli importu nie chcesz wykonywać, przerwij po eksporcie i oznacz import jako nieprzetestowany. **Do zgłoszenia:** komunikat i porównanie liczb, bez wysyłania pliku profilu.

## S-15 — rozkaz sztabu

**Cel:** sprawdzić ogólny przepływ rozkazu. **Urządzenie/ustawienia:** profil z dostępnym rozkazem; do 8 min aktywnych.

1. W menu wybierz **Rozkazy**. Wybierz dostępny rozkaz, przeczytaj cel i wybierz **Graj**.
2. Zagraj kilka minut; jeśli wynik pojawi się później, wróć do listy i sprawdź gwiazdki.

**Oczekiwany wynik:** cel jest widoczny, gra uruchamia rozkaz, a wynik po zakończeniu jest czytelny. Jeśli wszystkie są zablokowane, zapisz warunek odblokowania i oznacz bitwę jako niewykonaną. **Do zgłoszenia:** nazwa rozkazu, cel i wynik lub komunikat blokady.

## S-16 — raport dla testera

**Cel:** przekazać informacje z dnia 3 i 10. **Urządzenie/ustawienia:** wersja testowa, do 5 min w każdym z dni.

1. Wybierz **Ustawienia → O grze → Otwórz → Raport dla testera → Udostępnij**.
2. W systemowym oknie wybierz kopiowanie lub aplikację do wysłania. Przeczytaj zawartość i przekaż ją tylko organizatorowi jego wskazanym kanałem.

**Oczekiwany wynik:** pojawia się systemowy wybór sposobu udostępnienia; nic nie jest wysłane bez wyboru testera. **Do zgłoszenia:** dzień 3 albo 10, raport po sprawdzeniu i opis ewentualnego problemu; nie wysyłaj zrzutu z prywatnymi danymi.

## S-17 — przypomnienia

**Cel:** sprawdzić ustawienie i późniejsze działanie przypomnienia. **Urządzenie/ustawienia:** co najmniej jedno zwycięstwo, telefon z włączonymi powiadomieniami; do 5 min aktywnych, oczekiwanie może trwać do następnego dnia.

1. W **Ustawienia** ustaw **Powiadomienia** na **Wł.**, wybierz **Godzina przypomnienia** i w razie prośby systemu zezwól na powiadomienia. Nie zmieniaj zegara telefonu.
2. Zamknij grę w tle i przy najbliższej zaplanowanej godzinie sprawdź, czy pojawiło się przypomnienie; zapisz też przypadek braku. Jeśli termin jeszcze nie nadszedł lub próba dnia została wykorzystana, oznacz kontrolę odbioru jako oczekującą.

**Oczekiwany wynik:** ustawienia zostają zapamiętane; przypomnienie zależy od zgody, dnia, pory i zasad systemu. Prośba o zgodę ani przypomnienie w grze nie powinny pojawiać się na ekranie wyników bitwy. **Do zgłoszenia:** Android, godzina, status zgody i próby dnia, ustawienia oszczędzania baterii i czas faktycznej obserwacji.

## S-18 — bateria i tło

**Cel:** wychwycić zauważalne nagrzewanie, szybki spadek baterii i problemy po powrocie. **Urządzenie/ustawienia:** telefon nienaładowany do przewodu, zwykłe ustawienia, do 6 min aktywnych; obserwacja systemowa może trwać dłużej.

1. Zapisz procent baterii i czy urządzenie jest chłodne, zagraj do 5 minut, następnie użyj **Pauza** i przejdź do innej aplikacji na około minutę.
2. Wróć, wybierz **Wznów**, zapisz odczuwalną temperaturę i procent baterii. Jeśli telefon mocno się nagrzewa, przerwij.

**Oczekiwany wynik:** gra wraca i reaguje; pojedynczy procent baterii nie jest dokładnym pomiarem zużycia ani dowodem usterki. **Do zgłoszenia:** model, poziom baterii przed/po, czas, ustawienia grafiki oraz opis objawów.
