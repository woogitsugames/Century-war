# Audyt 166 ? zapis, rozliczenia i cykl ?ycia

Data: 2026-11-06. Badana baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`.
**NIE URUCHAMIANO GRADLE ANI TEST?W** ? raport opiera si? na selektywnym czytaniu kodu; nie odtwarzano awarii na urz?dzeniu. Sam dokument nie wymaga budowania. To nie jest pe?ny audyt wszystkich punkt?w zlecenia.

## Dla w?a?ciciela

Znalaz?em dwa konkretne braki w zabezpieczeniach i jedno podejrzenie wymagaj?ce testu. B??d przy zapisie kolejki mo?e sprawi?, ?e po komunikacie o niepowodzeniu pro?ba o usuni?cie danych rankingu zostanie jednak wykonana po ponownym uruchomieniu. Dotyczy to w?asnego rankingu sieciowego, kt?rego nie zaplanowano dla bie??cych test?w zamkni?tych; nie jest dowodem b??du Play Games. Drugi brak dotyczy nietypowo uszkodzonego lub celowo zmodyfikowanego zapisu bitwy, kt?rego suma kontrolna mimo tego jest prawid?owa. Nie stwierdzi?em na tej podstawie cz?stej awarii typowej gry. Potencjalny wy?cig rozliczenia wymaga jeszcze sprawdzenia, czy rzeczywista aplikacja potrafi wywo?a? t? sam? operacj? r?wnocze?nie. Na podstawie tego cz??ciowego przegl?du nie mo?na wyda? wiarygodnej decyzji o gotowo?ci do test?w zamkni?tych. Przed decyzj? potrzebne s? testy przerwania zapisu i rzeczywistego cyklu ?ycia, a tak?e sprawdzenie pozosta?ych obszar?w wymienionych poni?ej.

## Znaleziska

?cie?ki zaczynaj?ce si? od `app/` oznaczaj? `app/src/main/java/com/centurywar/`, a `sim/` ? `sim/src/main/kotlin/com/centurywar/game/`. Numery linii dotycz? podanego SHA.

| ID | Wa?no?? | Obszar | Plik:linia | Opis | Scenariusz odtworzenia / test | Proponowana poprawka |
|---|---|---|---|---|---|---|
| A166-01 | Wysoka | Usuwanie danych / awaria I/O | `app/meta/ScoreClient.kt:129`, `:138`, `:245`, `app/screens/AboutScreen.kt:241` | Po trwa?ym dodaniu ??dania do kolejki usuni?? b??d zapisu kolejki wynik?w zwraca `callback(false)`, ale nie cofa ju? dodanego ??dania. Interfejs pokazuje niepowodzenie i nie zmienia to?samo?ci; po restarcie `pumpDeletions()` mo?e mimo to usun?? dane tej to?samo?ci. Ponadto `deletingPlayerId` pozostaje ustawiony i blokuje zg?oszenia dla niej w bie??cej instancji. Jest to analiza kodu, nie wykonana reprodukcja. | W te?cie podmie? zapis kolejki wynik?w tak, by zawi?d? po poprawnym `deletions.add`, sprawd? `false`, zrestartuj klienta i obserwuj ??danie usuni?cia oraz to?samo?? profilu. | Nada? ca?ej operacji jednoznaczny kontrakt i sp?jny stan: je?eli ??danie przyj?to trwale, przekaza? to interfejsowi oraz doprowadzi? zmian? to?samo?ci do ko?ca; uwzgl?dni? restart i b??d zapisu bezpiecznym dziennikiem operacji. |
| A166-02 | ?rednia | Odporno?? snapshotu | `sim/BattleSnapshot.kt:405`, `:455`, `:487` | CRC sprawdza bajty, ale `camX`, pozycja i zdrowie jednostki oraz parametry pocisku s? czytane bez kontroli `isFinite()` i zakresu. Snapshot z poprawnym CRC mo?e wi?c zawiera? NaN lub niesko?czono?? w tych polach i zosta? przyj?ty. **Podejrzenie skutku:** mo?liwe b??dy rysowania lub rozgrywki; wp?ywu nie odtworzono. Zwyk?e losowe uszkodzenie wykryte przez CRC nie spe?nia tego scenariusza. | Zakoduj legalny snapshot z aktywn? jednostk?, podmie? `x` na NaN i przelicz CRC; sprawd? `decode`, a nast?pnie krok symulacji i renderowanie. Powt?rz dla `camX` i pocisku. | Odrzuca? niefinitywne warto?ci i ustali? poprawne zakresy stanu, z testami zgodno?ci starszych format?w; zmiany w symulacji i zgodno?ci zatwierdza koordynator. |
| A166-03 | ?rednia | R?wnoleg?e rozliczenia | `app/meta/Profile.kt:839`, `:846` | **Podejrzenie, uzupe?nienie A156-10:** sprawdzenie `isBattleSettled` jest synchronizowane, ale blokada zostaje zwolniona na czas `apply()` i identyfikator jest ustawiany dopiero po nim. Dwa r?wnoleg?e wywo?ania z tym samym ID mog? oba wej?? w `apply`; nie wykazano, ?e normalna ?cie?ka aplikacji rzeczywi?cie je wywo?a r?wnolegle. Poprzednia poprawka rozwi?zuje trwa?? kolejno?? zapisu i kasowania zrzutu, ale nie zapewnia atomowo?ci tego sprawdzenia mi?dzy wywo?aniami. | Test z dwiema nitkami: zatrzyma? obie wewn?trz `apply` barier? dla tego samego ID i sprawdzi? liczb? nalicze?; osobno ustali?, czy onPause/obr?t mog? do tej sekwencji doprowadzi?. | Zagwarantowa? pojedynczego w?a?ciciela rozliczenia lub atomow? rezerwacj? ID i obs?u?y? wyj?tek `apply`; regresja dla r?wnoleg?ych wywo?a? i przerwania procesu. |

## Sprawdzone, bez zastrze?e? w podanym zakresie

- `ReplayImport.kt:10?19` ogranicza rozmiar obcego strumienia przed dekodowaniem, a `ReplayStore.kt:192?195` ustala limit 1 MiB. To nie jest pe?na ocena bezpiecze?stwa importu ani dostawcy URI.
- `ProfileBackup.kt:48?87` ogranicza wej?cie, sprawdza format, wersj?, d?ugo?? i sum? przed przekazaniem zawarto?ci profilowi.
- `BattleSnapshot.kt:340?349,447?450,477?480,613?615` sprawdza nag??wek, d?ugo??, CRC, liczby obiekt?w i odczyty ordinali. A166-02 dotyczy innych p?l.
- `Profile.kt:839?854` oraz `BattleScreen.kt:1444` wi??? kasowanie migawki z potwierdzeniem zapisu wyniku; nie dowodzi to poprawno?ci pod obci??eniem ani braku A166-03.
- `GameView.kt:102?125` nie uruchamia p?tli bez wznowionej Activity i powierzchni; `MainActivity.kt:564?574` zwalnia wskazane zasoby. Nie przeprowadzono testu powolnej klatki ani urz?dzenia Fold.
- `ThermalWatch.kt:35?49,66?69` usuwa callback oraz s?uchacza przy zatrzymaniu. Nie badano zachowania na fizycznym telefonie.
- `app/proguard-rules.pro:3?27` zachowuje wymienione komponenty oraz enumy; analiza regu? nie jest uruchomieniem release/R8.

## Ograniczenia i dalsza kontrola

Nie wykonano pe?nej analizy wszystkich ?cie?ek nagr?d (misje, osi?gni?cia lokalne i online, Endless, tydzie?, utracona bitwa), awarii zapisu, odczytu starszych i nowszych wersji, cyklu Fold i TestLoop, d?wi?ku i Play Games ani instrumentowanych test?w R8. `Profile.load` czyta lokalny profil bez limitu rozmiaru (`Profile.kt:1322,1338`), lecz by?o to ju? opisane w `audit-156.md` i nie jest tu ponownie zg?aszane jako nowe znalezisko. Poprzednie audyty i zlecenia naprawcze s? punktem odniesienia, nie dowodem, ?e wszystkie ich ustalenia s? naprawione w tej wersji.

## Proponowane paczki naprawcze

1. **Kolejka usuwania to?samo?ci ? A166-01:** kontrakt awarii, restart i testy; `ScoreClient.kt`, `PendingDeletions.kt`, `AboutScreen.kt` i testy rankingu.
2. **Walidacja snapshotu ? A166-02:** granice i warto?ci specjalne, testy zgodno?ci; `BattleSnapshot.kt` i testy `:sim`, po uzgodnieniu z koordynatorem symulacji.
3. **Atomowe rozliczenie ? A166-03:** najpierw test osi?galno?ci i dw?ch w?tk?w, potem ewentualna poprawka; `Profile.kt` i testy profilu. Paczki maj? roz??czne pliki produkcyjne; integracj? i wsp?lne testy przeprowadzi? po ich uko?czeniu.
