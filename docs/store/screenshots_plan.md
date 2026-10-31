# Century War — plan ośmiu zrzutów Google Play i filmu 30 s

**Cel:** osiem prawdziwych, atrakcyjnych ujęć gry na telefon w orientacji poziomej, z podpisami PL/EN. Zdjęcia należy wykonać **z działającego finalnego buildu**, nie z makiet. Obecna wersja ma rankingi lokalne i nie ma reklam ani globalnego multiplayera.

## Zrzuty ekranu — kolejność publikacji

| Nr | Plik roboczy | Co pokazać na autentycznym ekranie | Podpis PL | Caption EN |
|---|---|---|---|---|
| 1 | 01-frontline.png | Główna bitwa: dwie armie, baza, HUD, walka na jednym pasie; maksymalnie czytelny moment starcia. | **Przejmij kontrolę nad frontem** | **Take control of the front line** |
| 2 | 02-eight-eras.png | Bitwa w zaawansowanej epoce, najlepiej widoczne różnice w uzbrojeniu; nie sklejać odrębnych interfejsów udających jedną rozgrywkę. | **Walcz przez 8 epok** | **Battle through 8 eras** |
| 3 | 03-units.png | Wybór jednostek w trakcie bitwy: karta zakupów i kilka różnych rodzajów armii na mapie. | **Dowodź 32 jednostkami** | **Command 32 unique units** |
| 4 | 04-campaign.png | Ekran kampanii z etapami, gwiazdkami i wyróżnioną kolejną misją. | **Pokonaj 32 etapy kampanii** | **Conquer a 32-stage campaign** |
| 5 | 05-research.png | Ekran badań z kilkoma ulepszeniami, kosztami i widocznym saldem medali. | **Rozwijaj armię po każdej bitwie** | **Upgrade your army after battle** |
| 6 | 06-endless.png | Oblężenie bez końca w późniejszej fali, widoczny numer fali i zagrożenie bazy. | **Wytrwaj w oblężeniu bez końca** | **Survive the Endless Siege** |
| 7 | 07-daily.png | Ekran wyzwania dnia: zakres epok, modyfikatory i informacja o jednym podejściu rankingowym. | **Podejmij wyzwanie każdego dnia** | **Take on a new daily challenge** |
| 8 | 08-rankings.png | Ekran z prawdziwymi zapisanymi lokalnymi wynikami; bez sugerowania rozgrywki internetowej. | **Bij własne rekordy offline** | **Beat your personal best offline** |

**Dane do zdjęć:** dla plansz kampanii/badań/rankingu użyj ręcznie przygotowanego stanu testowego w profilu gry, ale nie manipuluj grafiką tak, aby pokazywała nieistniejące funkcje. Dla bitew wykonaj nagranie autentycznej rozgrywki i wybierz czytelną klatkę. Nie pokazuj prawdziwych danych osobowych ani identyfikatorów kont.

## Specyfikacja eksportu i kontrola jakości

- **Telefon:** 8 × obraz z oryginalnego buildu, krajobraz 16:9; zalecana rozdzielczość 1920 × 1080 px albo wyższa, w granicach wymagań Google Play.
- **Format:** PNG 24-bit bez alfa albo JPEG. Zrzuty obrazu z telefonu nie mogą być rozciągane, deformowane ani sztucznie poprawiane. Unikaj przepełnienia tekstem.
- **Pierwsze 3:** wyraźna akcja i mechanika, ponieważ będą najważniejsze dla zainteresowania gracza.
- **Nakładki:** jeśli dodajesz podpis poza interfejsem gry, zastosuj bardzo krótki tekst PL lub EN zgodny z wersją językową sklepu; nie zasłaniaj kluczowego HUD. Przed exportem sprawdź, czy taka stylizacja nadal odpowiada aktualnym zasadom Google Play.
- **Języki:** zrób osobne zestawy PL i EN z właściwymi tekstami interfejsu. Jeżeli UI nie jest przełączone, nie symuluj jego tłumaczenia w edytorze.
- **Duże ekrany / Fold:** osobny test i dodatkowe autentyczne ujęcia z tabletu oraz urządzenia składanego. Nie zastępuj zrzutów telefonu obrazami z rozciągniętym HUD.
- **Tekst alternatywny:** do każdej grafiki dodaj krótki opis tego, co rzeczywiście przedstawia (preferencyjnie do 140 znaków). Nie powtarzaj samego hasła marketingowego.

## Propozycja filmu promocyjnego: 30 sekund, 16:9

Bez narracji zależnej od dźwięku (Google Play może automatycznie odtwarzać film bez dźwięku). Dwie wersje napisów: polska i angielska. Muzyka oryginalna/licencjonowana, z jasno określonymi prawami do użycia.

| Czas | Obraz / akcja z rzeczywistej gry | Tekst ekranowy PL / EN |
|---|---|---|
| **0–4 s** | Dynamiczne wejście do bitwy w epoce plemiennej; na pierwszym planie główny front. | **Jedna linia frontu. Wiele strategii.** / **One front line. Endless tactics.** |
| **4–8 s** | Szkolenie pierwszych jednostek, walka, zdobywanie złota i XP. | **Dowodź armią** / **Lead your army** |
| **8–12 s** | Autentyczne awansowanie epoki; widoczne nowe oddziały i uzbrojenie. | **8 epok • 32 jednostki** / **8 eras • 32 units** |
| **12–16 s** | Szybkie przejście z ekranu podsumowania bitwy do badań; zakup ulepszenia. | **Rozwijaj strategię** / **Build your strategy** |
| **16–20 s** | Ekran kampanii, wybór etapu i gwiazdki. | **32 etapy kampanii** / **32 campaign stages** |
| **20–24 s** | Intensywne starcie Endless Siege i zbliżenie na wybór wyzwania dnia. | **Oblężenie i wyzwania dnia** / **Endless Siege & Daily Challenges** |
| **24–27 s** | Lokalne rankingi oraz ustawienia grafiki; szybka klatka z Foldem tylko jeśli realnie nagrana. | **Graj offline** / **Play offline** |
| **27–30 s** | Tytuł Century War na tle kadru z bitwy, proste wygaszenie. | **Century War — Wojna epok** / **Century War — Era Strategy** |

**Montaż:** krótkie cięcia, bez pseudo-gameplayu; najważniejszy komunikat zrozumiały bez dźwięku. Unikaj elementów interfejsu Google Play, ocen gwiazdkowych, fałszywych opinii, ikon innych gier i materiałów z cudzych marek.

## Kontrola przed przesłaniem do Play Console

1. Zweryfikuj każdy z ośmiu ekranów w wydaniu, które będzie publikowane.
2. Sprawdź poprawność polskich i angielskich etykiet, kadrowanie oraz brak uciętego HUD na małych i dużych ekranach.
3. Zweryfikuj formaty i wymiary zasobów; Google Play dopuszcza do 8 screenów na typ urządzenia.
4. Film umieść na YouTube zgodnie z wymogami Play Console i **wyłącz reklamy na filmie**. Dodaj jego adres w karcie aplikacji; sam plan nie publikuje filmu.
5. Nie dodawaj zrzutów przedstawiających Play Games, pakiet premium lub reklamy — obecna gra tych funkcji nie posiada.

## Źródła Google Play

- https://support.google.com/googleplay/android-developer/answer/9866151
- https://support.google.com/googleplay/android-developer/answer/9859152
