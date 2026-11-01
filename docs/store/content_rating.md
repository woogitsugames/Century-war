# Century War — odpowiedzi do ankiety klasyfikacji treści IARC

**Stan przygotowania:** 1 listopada 2026 r. **Osoba odpowiedzialna za zatwierdzenie formularza i danych wydawcy:** [OWNER_INPUT.md](OWNER_INPUT.md). Wiek docelowy i wynik klasyfikacji (PEGI, ESRB i inne) ustala ankieta IARC; nie wpisujemy z góry konkretnej kategorii.

## Charakter gry i przedstawienie przemocy

Century War jest kreskówkową strategią 2D, w której stylizowane armie walczą na jednym froncie. Jednostki używają broni białej i dystansowej, dział, wież oraz ataków specjalnych. Giną wojownicy i niszczone są bazy, ale grafika nie ma realistycznego charakteru. **W ankiecie zaznacz obecność przemocy:** regularna walka nie staje się „brakiem przemocy” tylko z powodu stylu rysunkowego.

| Kategoria pytania | Odpowiedź przygotowana dla opisanego wydania |
|---|---|
| Rodzaj produktu | Gra — strategia czasu rzeczywistego, rozgrywka w pojedynczym pasie |
| Przemoc / walka | Tak — walki armii w każdej bitwie |
| Forma przemocy | Kreskówkowa, stylizowana, wobec postaci przypominających ludzi i fantastycznych jednostek |
| Broń | Tak — broń biała, dystansowa, wieże i ataki specjalne |
| Krew, gore, okaleczenia | Nie w opisanej obecnej oprawie; upewnij się, że końcowe animacje nie dodają krwi ani realistycznych obrażeń |
| Horror / przerażające sceny | Nie w opisanej zawartości gry |
| Seks, nagość, erotyka | Nie |
| Wulgaryzmy we własnych tekstach gry | Nie; **zewnętrzne pseudonimy** mogą zawierać niedozwolone słowa i nie są tworzone przez redakcję gry |
| Alkohol, tytoń, narkotyki | Nie w przedstawionej zawartości, sprawdź nowe epoki przed wysłaniem ankiety |
| Hazard, zakłady, loot boxy | Nie w aktualnej wersji |
| Reklamy w aplikacji | Nie — brak integracji reklamowej |
| Zakupy w aplikacji | Nie — brak Play Billing |
| Bezpośrednia rozgrywka wieloosobowa | Nie — bitwy są rozgrywane lokalnie, a wynik można porównywać online |
| Czat, prywatne wiadomości, wymiana plików | Nie — brak tych funkcji |
| Publiczne treści użytkowników | **Tak, ograniczone**: nazwy/awatary profili Play Games i wyniki; po uruchomieniu własnego rankingu także nicki samodzielnie wybrane przez graczy |
| Interakcje użytkowników / „Users Interact” | **Ujawnij możliwość zobaczenia treści innych graczy** (pseudonimów i wyników). Gdy pytanie rozróżnia komunikację bezpośrednią, wskaż brak czatu i wiadomości. Nie zaznaczaj automatycznie „brak interakcji”, bo nicki są publiczne |
| Zgłaszanie obraźliwych nicków | **Po wdrożeniu zlecenia 80:** przytrzymanie wpisu własnego rankingu otwiera „Zgłoś nick”; formularz przesyła publiczne ID wpisu i powód zgłoszenia. Zgłoszenia są przeznaczone dla moderatora, nie są publicznym czatem |
| Udostępnianie lokalizacji innym użytkownikom („Shares Location”) | Nie — gra nie udostępnia współrzędnych ani lokalizacji graczy |
| Swobodny dostęp do internetu / przeglądanie stron | Nie — usługi rankingu i zapisów chmurowych nie są przeglądarką internetową |

## Zasady oceny funkcji społecznościowych

**Dwie odrębne usługi rankingowe:**

1. **Google Play Games:** po konfiguracji ID i uwierzytelnieniu widoczne są wyniki oraz nazwa/awatar publicznego profilu Google zgodnie z ustawieniami widoczności. Lokalny pseudonim zapisywany w chmurze nie jest automatycznie publicznym pseudonimem Play Games.
2. **Własny ranking wydawcy:** przy skonfigurowanym serwerze i zgodzie użytkownika możliwe jest wyświetlanie pseudonimu wybranego w grze. Po realizacji zlecenia 80 do publicznego API nie powinien trafiać surowy UUID, a gracze mają mieć możliwość zgłoszenia obraźliwego pseudonimu. Brak integracji zadania 80 w finalnym AAB wymaga przeprowadzenia ankiety według funkcji rzeczywiście dostępnych — nie deklaruj nieistniejącego przycisku zgłaszania.

**Rozróżnienie:** publiczne pseudonimy są ograniczoną treścią tworzoną przez użytkownika, lecz nie oznaczają istnienia czatu. Jeśli aktualny formularz IARC/ESRB pokazuje odrębne pola „Users Interact”, „User Generated Content” lub „Shares Location”, wypełnij je zgodnie z dosłownymi definicjami; zgłoś pseudonimy i możliwość ich moderacji tam, gdzie formularz obejmuje widoczność treści innych graczy.

## Kontrola przed wysłaniem ankiety

1. Obejrzyj najnowszą rozgrywkę ze wszystkich epok, w tym walki, animacje śmierci, bossów i niszczenie baz. Zweryfikuj brak krwi, realistycznych ran, treści wulgarnych i narkotyków.
2. Sprawdź polskie i obcojęzyczne teksty oraz elementy dostarczane przez PGS (nazwa i awatar gracza, publiczne rankingi).
3. W finalnym AAB potwierdź, czy działają PGS i własny ranking. Po scaleniu zlecenia 80 sprawdź faktyczne działanie zgłaszania nicków i moderacji, nie tylko obecność endpointu w repo.
4. Odpowiedz zgodnie z faktycznym zakresem komunikacji i treści użytkowników, także jeśli nie ma wiadomości bezpośrednich.
5. Zapisz rzeczywiste wyniki klasyfikacji dla rynków docelowych i porównaj je z opisem w sklepie. Klasyfikacja IARC nie zastępuje oceny odbiorców dziecięcych i deklaracji Google Play Families.
6. Ponów ankietę po włączeniu własnego rankingu, czatu, PvP, możliwości publikacji swobodnych tekstów, reklam, zakupów albo zmianie zawartości przemocy.

## Źródła

- Google Play — klasyfikacja treści: https://support.google.com/googleplay/android-developer/answer/9859655
- ESRB — „Users Interact”, „Shares Location”: https://www.esrb.org/ratings-guide/
- Google PGS — widoczność profilu i zbieranie danych: https://developer.android.com/games/pgs/data-collection
