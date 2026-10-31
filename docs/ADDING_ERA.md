# Dodawanie epoki w Century War

1. W `game/Defs.kt` dodaj jeden `EraSpec` zawierający: klucz epoki, cztery jednostki w kolejności MELEE/RANGED/HEAVY/ELITE, broń i pociski, wieżę, ostrzał, próg XP, `EraArt`, `Terrain`, sześć kolorów tła i barwę UI.
2. `Defs.ERA_COUNT`, `eras`, `allUnits`, `Ui.ERA`, mapa kampanii i kodeks odczytują liczbę epok z danych; cztery etapy na każdą epokę.
3. Dodaj własne detale postaci w `game/art/Soldiers.kt`, machiny w `Machines.kt`, bazę, wieżę i flagę w `Bases.kt`, tło w `BattleRenderer.kt`. Nie twórz obiektów w pętli renderowania. Rozróżniaj epoki przez `EraArt` i `Terrain`, a nie magiczne numery.
4. W `engine/L.kt` dodaj klucze epoki, czterech jednostek, opisów do kodeksu, wieży i ostrzału. Każdy klucz ma 10 przekładów w kolejności pl/en/de/es/fr/it/pt/uk/nl/tr.
5. Dostosuj współczynniki wzrostu statystyk, kosztów, HP baz, przychodu i progi XP. Orientacyjnie: nowy mnożnik = stary mnożnik podniesiony do potęgi (stara liczba przejść / nowa liczba przejść). Testuj pełną potyczkę i zachowaj długość w granicach 15%.
6. W `meta/Profile.kt` podnieś wersję; dla każdego dopisanego bloku czterech etapów przesuń starsze gwiazdki o cztery miejsca. Migruj tylko starsze wersje i sprawdź powtórny zapis. Nie zmieniaj wpisów historycznych rankingu campaign.
7. Zmień `BattleSnapshot.VERSION` przy zmianie kodowania, kolejności lub balansu; dodaj test deterministycznego wznowienia z nową epoką.
8. Zmieniaj losowanie `Daily` dopiero od kolejnej północy UTC. Testuj dzień przed i dzień po dacie granicznej.
9. Uruchom `assembleDebug`, `BattleSimTest`, `BattleSnapshotTest`, `ProfileTest`, `LTest` i `ArtGalleryTest`. Obejrzyj PNG dla obu drużyn i baz; dodaj do PR.
10. Przyszłe epoki: Renesans, Epoka atomowa, Era galaktyczna. Każda kolejna aktualizacja wymaga nowego kroku migracji i dokumentacji wyniku balansu.
