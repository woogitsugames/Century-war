# Century War — kwestionariusz klasyfikacji treści IARC

**Audyt:** 31 października 2026 r. Dotyczy obecnej zawartości gry, lokalnych przypomnień i kodu integracji Google Play Games. **Ostateczna klasyfikacja wieku jest nadawana przez IARC**, a brzmienie pytań może się zmieniać. W kodzie bazowym identyfikatory PGS pozostają REPLACE_ME — udostępnienie tablic sieciowych wymaga konfiguracji.

## Przemoc i zawartość

Century War to kreskówkowa strategia 2D, w której armie walczą o jeden front. Postacie używają broni białej, dystansowej, wież i ostrzałów specjalnych. Jednostki giną, a bazy mogą zostać zniszczone. Grafika jest stylizowana; brak krwi, okaleczeń i realistycznych obrażeń **trzeba potwierdzić w końcowym buildzie**.

**Nie wybieraj „brak przemocy”**: regularne walki postaci i niszczenie baz stanowią przemoc również bez krwi.

## Odpowiedzi robocze

| Zagadnienie IARC | Odpowiedź i uzasadnienie |
|---|---|
| Rodzaj aplikacji | **Gra** — strategia |
| Przemoc / walka | **Tak** — regularne starcia armii |
| Rodzaj przemocy | **Stylizowana, kreskówkowa** — elementy historyczne i futurystyczne |
| Postacie przypominające ludzi | **Tak** — część jednostek to stylizowani wojownicy |
| Broń i ataki | **Tak** — broń biała, dystansowa, wieże i ataki specjalne |
| Krew, gore, okaleczenia | **Nie**, jeśli końcowe animacje nie dodają takich elementów |
| Horror / sceny przerażające | **Nie**, do kontroli w finalnym wydaniu |
| Seks, nagość, erotyka | **Nie** |
| Wulgaryzmy / mowa nienawiści w treściach gry | **Nie**, po kontroli tłumaczeń; **pseudonimy Google** mogą pochodzić spoza gry |
| Narkotyki, alkohol, tytoń | **Nie**, po kontroli elementów wszystkich epok |
| Hazard i loot boxy | **Nie** w aktualnym projekcie |
| Czat, wiadomości między użytkownikami, wymiana plików | **Nie** — brak takich funkcji w grze |
| Multiplayer / wspólna rozgrywka | **Nie** — wyniki mogą być porównywane, ale bitwy nie są rozgrywane między graczami |
| Rankingi z widocznymi pseudonimami | **Tak, warunkowo:** po uruchomieniu PGS na jego tablicach wyników mogą być widoczne **nazwy graczy z profili Google**, awatary i wyniki, zgodnie z ustawieniami widoczności kont; gracz nie wysyła wiadomości ani swobodnych tekstów przez Century War |
| „Users Interact” / interakcje użytkowników | **[DO DECYZJI]** Dla samego czatu/komunikacji odpowiedź brzmi **Nie**. Jeśli bieżąca ankieta uwzględnia możliwość oglądania nazw i wyników innych graczy lub niekontrolowane treści profili jako interakcję/UGC, należy ujawnić taką funkcję. Nie wpisywać automatycznie „Nie” bez sprawdzenia dokładnego pytania i wytycznych IARC/ESRB |
| Publiczne treści tworzone przez użytkowników | **Ograniczone do profilu Google i wyników PGS, jeśli funkcja włączona**; brak postów, komentarzy, czatu i publikowania własnych materiałów przez aplikację. **[DO DECYZJI]** Czy publiczny pseudonim/awatar PGS wymaga zaznaczenia konkretnego pola UGC w obecnej ankiecie |
| Udostępnianie lokalizacji innym użytkownikom („Shares Location”) | **Nie** — brak udostępniania lokalizacji, brak mapy graczy, brak uprawnienia lokalizacyjnego i brak funkcji wysyłania współrzędnych |
| Swobodny dostęp do internetu / wbudowana przeglądarka | **Nie** — połączenie PGS nie umożliwia przeglądania dowolnych stron |
| Reklamy w aplikacji | **Nie** — brak mechanizmu reklam w kodzie |
| Zakupy w aplikacji | **Nie** — brak Play Billing |

**Ważne rozróżnienie:** internetowy ranking porównawczy jest funkcją społecznościową, ale **nie jest automatycznie czatem**. ESRB opisuje „Users Interact” w kontekście narażenia na niefiltrowane treści użytkowników i komunikacji. Nie da się rozstrzygnąć tej etykiety wyłącznie na podstawie faktu użycia SDK; sprawdź dokładny wariant ankiety pokazany w Play Console. Pseudonim lokalny w kopii chmurowej nie staje się przez to publicznym pseudonimem na tablicy PGS.

## Kontrola przed wysłaniem ankiety

1. Obejrzyj rozgrywkę ze **wszystkich 9 epok**, animacje obrażeń, śmierci, ataków specjalnych, bossów i niszczenia baz — również w aktualizacji po pracach graficznych.
2. Skontroluj etykiety i teksty w 10 językach, a także zewnętrzne elementy PGS (profil gracza, tablice, widoczność pseudonimów).
3. Potwierdź konfigurację PGS w finalnym AAB. Jeśli wydanie nie ma działających rankingów Google, odpowiedzi dotyczące ich publiczności muszą odpowiadać **faktycznie dystrybuowanej wersji**.
4. Przejrzyj bieżące pytania o komunikację, interakcje, treści użytkowników i lokalizację. W razie niejasności zakresu „Users Interact” wybierz interpretację zgodną z wytycznymi IARC i odnotuj uzasadnienie. Sam fakt braku czatu nie eliminuje publicznych nazw profilowych.
5. Nie zakładaj z góry PEGI 3, PEGI 7, ESRB Everyone ani innego wyniku. Zapisz przyznane kategorie dla rynków docelowych i porównaj je z materiałami sklepowymi.
6. **Ponów ankietę po zleceniu 23**, gdy zostanie włączony własny serwer rankingu; ponów też po wdrożeniu czatu, PvP, swobodnej treści od graczy, zmian przemocy, reklam lub zakupów.

**IARC to odrębny proces od wyboru grupy docelowej** i obowiązków dotyczących dzieci w Google Play. Ewentualne reklamy i ograniczenia programu Families należy ocenić osobno, jeśli zostaną wprowadzone.

## Źródła

- IARC i wymogi Google Play: https://support.google.com/googleplay/android-developer/answer/9859655
- Przewodnik ESRB — „Users Interact” i „Shares Location”: https://www.esrb.org/ratings-guide/
- PGS — publiczność profilu i udostępnianie danych: https://developer.android.com/games/pgs/data-collection
