# Audyt 166 — przegląd częściowy

Data: 2026-11-06. Baza `origin/main`: `f17c6e25abf7b3c1b679a77a0a1fbe457df7a3f7`.
**NIE URUCHAMIANO GRADLE ANI TESTÓW.** Wyniki pochodzą z czytania kodu; awarii nie odtwarzano na urządzeniu. Zlecenie dokumentowe nie wymaga budowania. Audyt jest częściowy.

## Podsumowanie dla właściciela

Wykryto trzy braki w kodzie i dwa podejrzenia wymagające odtworzenia. Prośba o usunięcie danych własnego rankingu może zostać wykonana po ponownym uruchomieniu, mimo wcześniejszego komunikatu o niepowodzeniu. Ranking ten nie jest zaplanowany na bieżące testy zamknięte. Nietypowo uszkodzony zapis bitwy z poprawną sumą kontrolną może zawierać nieprawidłowe liczby. Nie wykazano, że zdarza się to podczas zwykłej gry. Powtarzane otwieranie i zamykanie aplikacji może pozostawiać niepotrzebne wątki powtórek. Potencjalne wyścigi rozliczenia i pętli testowej wymagają kontrolowanych prób. Nie można na podstawie częściowego przeglądu wiarygodnie zatwierdzić wydania dla testerów. Pozostałe obszary i próby urządzeniowe wymagają dokończenia.

## Znaleziska

W tabeli `app/` oznacza `app/src/main/java/com/centurywar/`, a `sim/` — `sim/src/main/kotlin/com/centurywar/game/`. Linie dotyczą wskazanego SHA.

| ID | Ważność | Obszar | Plik:linia | Opis | Scenariusz / test | Proponowana poprawka |
|---|---|---|---|---|---|---|
| A166-01 | Wysoka | Usuwanie danych | `app/meta/ScoreClient.kt:129,138,245`; `app/screens/AboutScreen.kt:241` | Po zapisaniu żądania usunięcia błąd zapisu kolejki wyników zwraca `false`, ale nie cofa żądania. Interfejs pokazuje błąd i nie zmienia tożsamości, a po restarcie żądanie może zostać wysłane. `deletingPlayerId` pozostaje ustawiony i blokuje zgłoszenia w tej instancji. Analiza kodu, bez reprodukcji. | Wymusić błąd zapisu kolejki wyników po udanym `deletions.add`, sprawdzić callback i tożsamość; uruchomić klienta ponownie i obserwować żądanie. | Zapewnić spójny kontrakt i trwałą zmianę tożsamości po przyjęciu żądania; przetestować awarie i restart. |
| A166-02 | Średnia | Odporność snapshotu | `sim/BattleSnapshot.kt:405,455,487` | CRC sprawdza bajty, lecz kamera, pozycja i zdrowie jednostki oraz parametry pocisku są wczytywane bez kontroli `isFinite()` i zakresów. Snapshot z prawidłowym CRC może zawierać NaN. **Podejrzenie skutku:** błędy rysowania lub rozgrywki; nie odtworzono. Zwykłe uszkodzenie wykryte przez CRC nie spełnia tego scenariusza. | Zakodować poprawny snapshot, wstawić NaN do pozycji jednostki, przeliczyć CRC i sprawdzić decode oraz następny krok gry; powtórzyć dla kamery i pocisku. | Odrzucać niepoprawne wartości, z testami starszych formatów; zmiany sim uzgadnia koordynator. |
| A166-03 | Średnia | Równoległe rozliczenia | `app/meta/Profile.kt:839,846` | **Podejrzenie; uzupełnienie A156-10:** kontrola identyfikatora odbywa się pod blokadą, ale `apply()` wykonuje się po jej zwolnieniu, przed zapisaniem identyfikatora. Dwa równoległe wywołania mogą oba naliczyć nagrodę. Nie wykazano, że rzeczywista aplikacja może je tak wywołać. | Dwie nitki z tym samym ID zatrzymać barierą wewnątrz `apply` i zmierzyć liczbę naliczeń; osobno zbadać osiągalność przy pauzie i obrocie. | Jeden właściciel rozliczenia albo atomowa rezerwacja ID z obsługą błędu; regresja wielowątkowa i testy przerwania procesu. |
| A166-04 | Średnia | Cykl życia TestLoop | `app/testloop/TestLoopActivity.kt:89,100,117,219` | **Podejrzenie:** `onDestroy` zwalnia `running`, nie czekając na wątek startowy. Szybki nowy start może użyć tego samego katalogu, zanim stary wątek wykona `deleteRecursively()`. Prawdziwy profil jest poza tym katalogiem. | Wstrzymać stary wątek przed czyszczeniem, zamknąć Activity, uruchomić nową pętlę, zapisać znacznik i zwolnić stary wątek. Sprawdzić znacznik i wynik nowej pętli. | Osobny katalog na uruchomienie albo wyłączne posiadanie katalogu do zakończenia starej inicjalizacji; kontrola generacji. |
| A166-05 | Średnia | Wątki / cykl życia | `app/meta/ReplayStore.kt:23,101`; `app/MainActivity.kt:283,555–575` | Nowy magazyn ma własny `newSingleThreadExecutor`, który startuje po pierwszym zadaniu, ale klasa nie udostępnia zamknięcia i `onDestroy` go nie zamyka. Po ponownym otwarciu Activity w tym samym procesie może pozostać kolejny bezczynny wątek `replay-io`. To uzupełnia wcześniejszy `audit-156.md:282`: nie twierdzę, że sam ten fakt dowodzi zatrzymania Activity w pamięci. | W tej samej instancji procesu otworzyć listę powtórek, zamknąć Activity, powtórzyć kilka razy i policzyć żywe wątki `replay-io`; po poprawce poczekać na wykonanie istniejących zapisów i sprawdzić ich zakończenie. | Dodać zamknięcie executora po zapisach, wywołać je w `onDestroy`; zapobiec przyjmowaniu zadań po zamknięciu i przetestować cykl życia. |

## Sprawdzone, bez zastrzeżeń w podanym zakresie

- `ReplayImport.kt:10–19` ogranicza rozmiar obcego strumienia przed dekodowaniem; `ReplayStore.kt:192–195` ustala limit 1 MiB. To nie jest pełna ocena importu.
- `ProfileBackup.kt:48–87` ogranicza wejście i sprawdza format, wersję, długość oraz sumę przed przekazaniem profilu.
- `BattleSnapshot.kt:340–349,447–450,477–480,613–615` sprawdza nagłówek, długość, CRC, liczby obiektów i ordinale. A166-02 dotyczy innych pól.
- `Profile.kt:839–854` i `BattleScreen.kt:1444` wiążą usunięcie migawki z potwierdzeniem zapisu wyniku; nie dowodzi to braku A166-03.
- `GameView.kt:102–125` nie uruchamia pętli bez wznowionej Activity i powierzchni; `MainActivity.kt:564–574` zwalnia wskazane zasoby. Nie wykonano próby powolnej klatki ani Folda.
- `ThermalWatch.kt:35–49,66–69` usuwa callback i słuchacza przy zatrzymaniu. Nie badano fizycznego telefonu.
- `app/proguard-rules.pro:3–27` zachowuje wskazane komponenty i enumy; to nie zastępuje uruchomienia release/R8.

## Ograniczenia

Nie ukończono pełnej analizy wszystkich ścieżek nagród, awarii zapisu, starszych i nowszych wersji, Folda i TestLoop, dźwięku, Play Games ani instrumentowanych testów R8. `Profile.load` czyta lokalny profil bez limitu rozmiaru (`Profile.kt:1322,1338`), ale opisano to już w `audit-156.md`, więc nie jest ponownie zgłaszane jako nowe znalezisko. Wcześniejsze audyty i zlecenia są punktem odniesienia, nie dowodem, że wszystkie ich ustalenia zostały naprawione.

## Proponowane paczki naprawcze

1. **Kolejka usuwania — A166-01:** kontrakt awarii i restart; `ScoreClient.kt`, `PendingDeletions.kt`, `AboutScreen.kt` oraz testy rankingu.
2. **Walidacja snapshotu — A166-02:** wartości specjalne i testy zgodności; `BattleSnapshot.kt` oraz testy `:sim`, po uzgodnieniu z koordynatorem.
3. **Atomowe rozliczenie — A166-03:** najpierw test osiągalności i dwóch wątków, potem ewentualna poprawka; `Profile.kt` i testy profilu.
4. **Izolacja TestLoop — A166-04:** test przeplotu i poprawka; `TestLoopActivity.kt` i jego testy.
5. **Żywotność magazynu powtórek — A166-05:** zamknięcie i test wątków; `ReplayStore.kt`, `MainActivity.kt` i testy cyklu życia. Paczki mają rozłączne pliki produkcyjne; po integracji potrzebne są wspólne testy.
