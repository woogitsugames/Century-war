# Zlecenie 125 — projekt dowódcy

## Mechanika

- **Jedna jednostka dowódcy na każdą stronę jednocześnie**, niezależnie od epoki. Zajmuje jedno miejsce limitu armii. Używa bieżącej definicji jednostki elitarnej jako bazy statystyk, ale nie wymaga badania „Doktryna elit”; nie zmienia istniejących definicji ani cen jednostek.
- Przywołanie kosztuje **2 × cenę bieżącej jednostki ciężkiej**; pierwsze dostępne od początku dopuszczonej bitwy. Po śmierci odnowienie **90 sekund czasu symulacji**. Nie można przywołać drugiego żywego dowódcy ani omijać limitu populacji.
- Dowódca ma 180% HP i 115% obrażeń elity. **Aura**: +10% obrażeń zwykłych sojuszników w odległości do 230 jednostek pasa (bez kumulowania, bez wzmacniania wież i samego dowódcy).
- **Umiejętność — Impuls**: automatycznie, gdy wróg znajduje się do 180 jednostek pasa, raz na 20 sekund zadaje obrażenia obszarowe w promieniu 75 (1,2 × obrażenia dowódcy), z efektem wizualnym. Nie wymaga drugiego przycisku; umiejętność używa istniejącej mechaniki trafień i kontr.
- **Dostępność**: kampania od etapu 8 (indeks 7), potyczka, Endless i cotygodniowe oblężenie. W wyzwaniu dnia dopiero od `Daily.RELEASE_DAY`; historyczne dni pozostają bez tej mechaniki. Dzienna reguła `RAPID_ABILITIES` skraca odnowienie do 45 sekund.
- Przeciwnik w bitwach ekonomicznych płaci tyle samo za dowódcę, bez ukrytej premii. W Endless koszt dowódcy odliczany jest od jawnego budżetu fali; nie jest darmową jednostką.
- AI gracza wydaje rozkaz przez `BattleController`, AI wroga przez reguły symulacji.

## Zapis i kompatybilność

- `Replay.Kind.SUMMON_COMMANDER` dopisany na końcu, `Replay.VERSION` 6→7. Stare powtórki v5/v6 są odtwarzane **z wyłączoną mechaniką dowódcy** (także decyzje AI), zatem nie zmienia się ich przebieg. Serwer weryfikuje nowy rozkaz tylko w v7.
- `BattleSnapshot.VERSION` 15→16. Zapis obejmuje aktywność i zegar impulsu dowódcy, pozostały czas odnowienia stron oraz flagę kompatybilności. Poprzednie snapshoty jak dotychczas są jawnie odrzucane przez nagłówek wersji. Brak źródła zegara systemowego i losowań dla dowódcy.
- Jedyny dodatkowy przycisk HUD pozostaje schowany przed odblokowaniem, by nie przesuwać układu samouczka. Po odblokowaniu liczba akcji rośnie z sześciu do siedmiu; na wąskim ekranie trzyrzędowym dowódca zajmuje piątą pozycję drugiego rzędu obok czterech ulepszeń.

## Weryfikacja

- Testy: dostępność trybów, opłaty, pojedynczy dowódca, śmierć/odnowienie, aura, impuls, obie strony AI, odtwarzanie v7 i starszych wersji, snapshot i determinizm po wznowieniu.
- Android: kompilacja debug, testy :sim i :server, LTest, BattleScreenTest, screenshoty 720p oraz obu ekranów Folda w układzie leworęcznym.
- Balans: raport 36×20 seedów i porównanie Endless 20/100 seedów; wyniki liczbowe należy dołączyć po faktycznym uruchomieniu, bez deklaracji „zaliczone” przed testami.

## Granice

- Brak zmiany dotychczasowych jednostek, ich kosztów oraz kolejności istniejących enumów.
- Efekt aury jest deterministyczny i nie alokuje pamięci w kroku; grafika dowódcy i animacja pierścienia operują na współdzielonych, prealokowanych obiektach Canvas/Paint.
