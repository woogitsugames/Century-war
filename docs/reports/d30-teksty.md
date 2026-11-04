# Dzień 30: teksty sklepu i dokumenty wydania (R12 poz. 3)

Gałąź `claude/dzien-30-teksty` (baza `integ/tura10` + `claude/research-12`). Same teksty,
bez zmian logiki gry. `L.kt` i `WhatsNewCatalog` bez zmian: w grze nie ma obietnicy bitwy
z TalkBack, a dopisanie nowych wpisów „Co nowego” (T10) wymagałoby nowej części tekstów.
Nie budowano (Gradle w kontenerze nie działa).

## Co zmieniono

| Plik | Zmiana | R12 |
|---|---|---|
| `docs/store/listing_{pl,en,de,es,fr,it,nl,pt,tr,uk}.md` | „Co nowego”: wódz i stopnie wież, muzyka, krótsze bitwy kampanii w etapach 2–18 (ok. 8–11 min), cele w oblężeniu bez końca, premia za powrót, osiągnięcia i misje, Galaxy Fold. Dostępność: palety dla daltonistów, wysoki kontrast, większy tekst, tempo bitwy ×½, czytnik ekranu w menu. Usunięte „TalkBack… (bitwa w przygotowaniu)” | T1 |
| te same pliki, pełny opis | linia dostępności: dopisane tempo bitwy ×½ (czytnik ekranu dalej tylko „w menu i ustawieniach”) | T1 |
| `listing_pl.md`, `listing_en.md` („Kontrola przed publikacją”) | zamiast „zmień na pełną obsługę bitwy”: lista dozwolonych cech dostępności i zakaz obiecywania gry dla niewidomych (decyzja 4.11); liczby czasu tylko dla etapów 2–18; liczyć długość skryptem | T2 |
| `docs/RELEASE.md`, „Kandydat” | stan 4.11; bez „W toku: dzień 5”; TalkBack: co jest + decyzja właściciela; A156-13 przeniesione do „Zrobione” (gałąź 163 scalona); krótsze etapy 2–18 z czasami; nowości 3–4.11 (powitanie po przerwie, kamienie milowe Endless, oddech muzyki, samouczek wraca); ankieta testera; pętla testowa w procesie `:testloop`; „Nowość na tym etapie” jako gałąź w drodze (`claude/dzien-28-nowosc`) | T3 |
| `docs/RELEASE.md`, scenariusz testera | wstęp: raport w 3. i 10. dniu, ankieta; krok 6: czasy 8–12 / 13–18 i prośba o uwagi o etapie 19; krok 11: brąz/srebro/złoto i licznik rekordu; krok 12: zasady wyzwań sprzed premiery („zasada, nie błąd”); 12a: powitanie po 3+ dniach; krok 21: TalkBack w menu, bitwa w ×½, gra nie jest przygotowana dla niewidomych, bez obietnicy; krok 26: raport z 3. i 10. dnia | T4 |
| `docs/GDD.md` (presja oblężnicza) | czasy z `Campaign`: 2–7 od 4. min +80%/min, 8–12 od 5. min +50%/min, 13–18 od 6. min +40%/min; samouczek i 19+ bez zmian (8 min, +20%/min) | T5 |
| `sim/.../Battle.kt` | tylko KDoc `siegeStart`/`siegeRamp`: „2–12” → „2–18” | T6 |

## Długość „Co nowego” (limit Play 500)

pl 414, en 427, de 472, es 486, fr 490, it 495, nl 474, pt 487, tr 459, uk 466.
Test `StoreGraphicsTest.listingLimits` tej sekcji nie sprawdza (tytuł, krótki i pełny opis
bez zmian długości poza pełnym opisem, który rośnie o kilkanaście znaków, daleko od 4000).

## Uwagi

- Zdanie o wyzwaniach sprzed premiery w kroku 12 to wariant (b) z pytania 3 R12; jeśli
  właściciel wybierze (a), dojdzie jeszcze napis w grze.
- Gdy pozycja 1 R12 zmieni etap 19, krok 6 scenariusza (zdanie o etapie 19) do przejrzenia.
- Po scaleniu `claude/dzien-28-nowosc` przenieść pozycję „W drodze” do „Zrobione” i dopisać
  krok scenariusza o iskrze „Nowość” na mapie.
