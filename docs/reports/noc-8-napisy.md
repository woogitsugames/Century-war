# Noc 8: drobne napisy (3.11)

Zakres: podpisy w wierszach ustawień (`RowNote`), podpis „Od nowa” w wynikach bitwy (fr) i tytuł
„Historia zmian” (fr, pt) na zewnętrznym Foldzie. Bez zmian w `sim/`, balansie i muzyce. Nowych
kluczy brak, więc `part174()` nie powstał. Jeden tekst poprawiłem w miejscu (uk, niżej).

## 1. `RowNote`: podpis zawsze w granicach wiersza

Dawniej: gdy etykieta „Wstrząsy ekranu” łamała się na dwa wiersze, podpis dostawał jeden wiersz,
a `Ui.fit` mógł go zmniejszyć najwyżej do 8 dp. Dłuższy tekst wchodził wtedy pod przycisk.
Noc 7 obeszła to krótszymi tekstami.

Teraz `RowNote.layout(ui, tekst, szerokość, wysokość)` dostaje prostokąt, który wiersz ma pod etykietą:
- **szerokość** kończy się przed przyciskiem, jak dotąd;
- **wysokość** w Ustawieniach to `rowR`, czyli przycisk ±3 dp, minus etykieta (1 lub 2 wiersze), odstęp 1 dp i zapas 2 dp.
  W Dostępności to przycisk z połową odstępu do sąsiadów (±6 dp) minus etykieta.

Kolejność:
1. Jeden wiersz 10 sp (albo mniej, jeśli wysokość nie pozwala na 10 sp), gdy tekst się mieści.
2. Dwa wiersze (podział na spacji, przy którym dłuższy wiersz jest najkrótszy), od 10 do 9 sp, ale tylko wtedy, gdy oba
   wiersze mieszczą się w wysokości. Jeśli nawet 9 sp nie wystarcza: pierwszy wiersz bierze tyle słów, ile się zmieści, a drugi
   kończy się „…”.
3. W przeciwnym razie jeden wiersz, pomniejszony najwyżej do 9 sp, a reszta tekstu zastąpiona „…”.

Minimum to `RowNote.MIN_DP = 9` (jak w `Ui.fit`: co najmniej 9 dp w pikselach przy każdej skali tekstu).
Bufor obejmuje tekst, szerokość, wysokość, skalę i gęstość. W klatce bez zmian `layout` niczego nie
alokuje (test sprawdza `assertSame` na kolejnej klatce).

Wyniki na prawdziwych tekstach (dziennik `noc8_napisy.txt`):
- Skala 100%: wszystkie 10 języków bez elipsy na obu urządzeniach. Najmniejsze podpisy na Foldzie zewn.:
  es 9,0 sp, fr 9,1 sp, it 9,6 sp (wcześniej es 8,x sp i pod przyciskiem). Pozostałe mają 10 sp.
- **uk `screen_shake_note`**: „Залежить від Доступності” przy 9 sp nie mieścił się w jednym wierszu na Foldzie
  zewn. (wcześniej 8,5 dp). Zmieniłem go w miejscu na „Див. «Доступність»” (10 sp).
- Skala 130% na Foldzie zewn. (ekran ma jedną kolumnę, a etykieta zajmuje 2 wiersze): en, pt, tr, uk dostają elipsę,
  np. „Ignored while “Reduce s…”. Przy tej skali to zamierzone zabezpieczenie.
  **Do decyzji:** podpisów nie ma w opisie TalkBack wiersza (`accessibilityLabel`). Można je tam dopisać, żeby pełny tekst
  był dostępny także po elipsie.

## 2. Wyniki bitwy i tytuł historii

**„Od nowa” (fr „Recommencer”, 8 dp na 1600×720).** W `BattleResultsPanel.layoutResults` wybierany był
układ 26/44/30% z ikoną po lewej. Teraz układ trzech równych przycisków (dotąd tylko dla
za długiej „Powtórki” na Foldzie zewn.) włącza się także wtedy, gdy podpis „Od nowa” obok ikony
zmalałby poniżej 10 sp. Skutki na 1600×720:
- fr „Recommencer” ma 11,8 sp;
- it „Ricomincia” ma 15,3 sp. Wcześniej miał ok. 9 sp i również był poniżej progu, więc it zmienia układ tak samo.

W pozostałych 8 językach układ jest bez zmian (`noc8_wyniki.txt` porównuje go z dawnym warunkiem:
wszędzie „26/44/30”). Na Foldzie zewn. wszystkie języki mają, jak dotąd, równe przyciski. Teksty i klucze
są bez zmian.

**„Historia zmian” na Foldzie zewn.** Na wąskim ekranie wstęga tytułu stoi w osobnym rzędzie
pod „Wstecz”, ale tytuł był dopasowywany do 160 dp, jakby dzielił rząd z przyciskami. Rysowana wstęga
ma i tak 220 dp. Teraz `Chrome.titleMaxW`: w osobnym rzędzie tytuł, który zmalałby poniżej
11 sp, wypełnia wstęgę o szerokości 220 dp (sama wstęga się nie zmienia). Gdyby i tego brakło do 10 sp, wstęga poszerza się
najwyżej do marginesów ekranu. `titleBounds` korzysta z tej samej reguły, więc dymki podpowiedzi
nadal omijają wstęgę. Wyniki:

| Język | Było | Jest |
|---|---|---|
| fr | 9 sp | 13,5 sp |
| pt | 10,6 sp | 16,8 sp |
| nl | 10,4 sp | 16,4 sp |

Tytuły, które miały co najmniej 11 sp (pl, en, de, es, it, uk, tr), zachowują dotychczasowy rozmiar.
Na 1600×720 nic się nie zmienia: tytuł stoi tam w jednym rzędzie z przyciskami.

## 3. Bez zmian (tylko notuję)

Zatwierdzonego górnego paska bitwy nie ruszałem (`BattleTopBar`, `compactTop`): „Welle 5” / „Хвиля 5”
mają 8 dp na Foldzie zewn., to samo dotyczy „Presja oblężnicza”.

## Testy i zrzuty

Nowy test `app/src/test/java/com/centurywar/screens/Noc8NapisyTest.kt`:
- `rowNoteGeometry`: Fold zewn. 904×2316 d=2,7 i 1600×720 d=2, 10 języków, skala 100% i 130%, prawdziwe teksty
  i sztucznie długi tekst (wstrzyknięty do tabeli `L` i przywracany po teście). W Ustawieniach i Dostępności test sprawdza
  na narysowanych napisach, że podpis:
  - nie wchodzi pod przycisk;
  - nie wychodzi poza wiersz;
  - ma co najmniej 9 dp;
  - albo pokazuje cały tekst, albo kończy się „…”.

  Przy skali 100% prawdziwe teksty nie mogą mieć elipsy. Test sprawdza też bufor.
- `rowNoteFallbacks`: logika dwóch wierszy i elipsy bez ekranu.
- `resultsRestartLabel`: „Od nowa” ma co najmniej 10 sp we wszystkich językach na obu urządzeniach.
- `historyTitle`: „Historia zmian” ma co najmniej 10 sp i mieści się w ekranie we wszystkich językach na obu urządzeniach.

Zrzuty kontrolne `app/build/screenshots/noc8_*.png` obejrzałem: Ustawienia fr/es i długi tekst, Dostępność, wyniki fr/de i historia fr/pt.

Wzorce `updateScreenshots -PscreenReason=Noc_8_drobne_napisy` zmieniły się w dwóch plikach:
`phone720_reg100_settings.png` i `foldOpen_reg100_settings.png`. Etykieta i podpis wiersza wstrząsów
przesunęły się o 0,5 dp, bo odstęp 1 dp jest teraz wliczany w centrowanie. Wpis jest w `UPDATES.md`.
