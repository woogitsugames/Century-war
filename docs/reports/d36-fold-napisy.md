# Dzień 36: ostatnie drobne napisy na zewnętrznym Foldzie (R12 poz. 8, R11 poz. 8)

Gałąź `claude/dzien-36-fold-napisy` (baza `claude/dzien-34-kandydat` + `integ/tura10`).
Gradle w tym kontenerze nie działa (sieć blokuje dl.google.com), więc **nic nie zostało skompilowane
ani uruchomione**. Szerokości policzyłem z kodu i z pomiaru tekstów czcionką DejaVu Sans Bold
przeskalowaną do Robota (×0,88). To przybliżenie z dokładnością ok. ±5%.

Bez zmian: `compactTop` w bitwie, celowanie ostrzału, TalkBack (etykiety przycisków zostają te same
albo mają teraz pełną nazwę zamiast pustej), `sim/`, balans. Nowych tekstów nie ma, więc `part204` nie
powstał.

Fold zewnętrzny 904×2316 przy d=2,7 ma ok. 335 dp szerokości, a Kodeks ok. 303 dp treści.
„sp” oznacza tu rozmiar przekazany do `Ui` (w teście: `sizePx / dp / textScale`), jak w `CodexFoldOuterLabelsTest`.

## Co zmieniono

| Miejsce | Było (Fold zewn.) | Jest | Warunek |
|---|---|---|---|
| Kodeks → karta elity: „Wymaga: Doktryna elit” (`CodexScreen.drawCard`) | obok plakietki roli zostawało ok. 96 dp: pl ok. 8,4 sp, es/uk ok. 7 sp (`fit` do 7) | osobne wiersze (1 przy 100%, 2 przy 130%) w miejscu pierwszych wierszy opisu, 11,5 sp (minimum 10 sp), opis przesunięty niżej. Kłódka zostaje obok plakietki. | `narrowStats` (jak w dniu 26). Wysokość wszystkich kart rośnie przez `descriptionExtra`. |
| Kodeks → nagłówek epoki (`drawEraHeader`) | nazwa epoki miała ok. 63 dp obok plakietki awansu. Przy 130% malała do ok. 9 sp i wchodziła na plakietkę. | plakietka „Awans: … XP” stoi w wierszu „Epoka 2/9” po prawej, a nazwa ma ok. 250 dp. Plakietka maleje co 0,5 sp (od 14 do najmniej 10 sp), aż przestanie dotykać numeru epoki. | `narrowStats` |
| Kodeks → Porównanie: legenda „↑ lepiej · ↓ gorzej …” (`CodexComparePanel`) | jeden wiersz z `fit` do 8 sp: de ok. 8,9 sp, przy 130% większość języków ok. 7–8 sp | gdy jeden wiersz miałby < 10 sp: dwa wiersze (podział na spacji, przy którym dłuższy wiersz jest najkrótszy, jak w `RowNote`), 11 sp, minimum 10 sp. Macierz kontr, wykres i wysokość treści przesuwają się o `legendShift` (ok. 4 dp przy 100%, ok. 12 dp przy 130%). | rozmiar jednego wiersza < 10 sp. Na telefonie i rozłożonym Foldzie legenda mieści się w 11 sp, więc nic się tam nie zmienia. |
| Rekordy: 4 zakładki (`RecordsTabs`) | ok. 73 dp na zakładkę, podpis pod ikoną: de „Wiederholungen” bez podpisu (sama ikona), en „Achievements” ok. 9,5 sp. Przy 130% podpisy 7–8 sp albo brak podpisu. | siatka 2 × 2 (ok. 152 dp na zakładkę) bez ikon, pełna nazwa 12 sp. `contentTop` rośnie o 54 dp, a Rankingi, Statystyki, Powtórki i Osiągnięcia układają się od niego jak dotąd. | wąski pion (< 420 dp, `h > w`) i którykolwiek podpis nie mieści się pod ikoną w 10 sp. Przy 100% dotyczy to en, de i pt, przy 130% prawie wszystkich języków. Pozostałe przypadki zostają w jednym rzędzie z ikonami. |

**Zakładki listew Kodeksu przy 130%** (zakładki kategorii i epok): z kodu wynika, że mieszczą się zawsze. Szerokość
każdej zakładki to `measure(etykieta, 12,5/13,5 sp, przy bieżącej skali) + 28 dp`, a `Ui.button` dopasowuje ją do szerokości − 12 dp,
więc etykieta ma pełne 12,5 i 13,5 sp także przy 130%. Znak przewijania (`ScrollCue`) jest od dnia 26. Nie zmieniałem tu nic,
tylko dopisałem asercję (≥ 11,5 sp przy 100% i 130%). Myślę, że notka „zakładki przy 130%” dotyczyła zakładek
Rekordów (powyżej), bo tylko tam znalazłem w kodzie zakładki poniżej 10 sp przy 130%.

## Test

`app/src/test/java/com/centurywar/screens/CodexFoldOuterLabelsTest.kt` (rozszerzony, 2 nowe testy):
- `eliteTagLegendAndRailsReadableOnFoldOuter`: Fold zewn., 10 języków, tekst 100% i 130%:
  - zakładki listew mają ≥ 11,5 sp;
  - nagłówek epoki (nazwa, numer, awans) ma ≥ 10 sp, mieści się na ekranie i nic w nim na siebie nie nachodzi;
  - wymaganie elity ma osobne wiersze, cały tekst, ≥ 10 sp i nie nachodzi na żaden napis (sprawdzane tylko poniżej listew, bo płótno zapisuje też przycięty tekst);
  - legenda ma cały tekst, ≥ 10 sp i nie nachodzi na nagłówek ani na podpis macierzy kontr.
- `recordsTabsReadableOnFoldOuter`: Rankingi, Fold zewn., 10 języków, 100% i 130%. Każda z 4 zakładek ma pełną nazwę,
  ≥ 10 sp, mieści się w przycisku i nie nachodzi na inne zakładki Rekordów ani na zakładki tablic. Na 1600×720
  i 2184×1968 (de, 130%) zakładki są w jednym rzędzie.
- Testy dostępu: `CodexScreen.eliteTagLinesForTest()`, `eraHeaderTextsForTest()`,
  `CodexComparePanel.legendLinesForTest()`, `RecordsTabs.grid`.

Do uruchomienia:
```
./gradlew :app:testDebugUnitTest --tests '*CodexFoldOuterLabelsTest*' --tests '*RecordsUxTest*' \
  --tests '*PrereleasePolish*' --tests '*GameAccessibilityScreensTest*' --console=plain
```
Wzorce `reg100` nie powinny się zmienić: zmiany włączają się tylko na wąskim pionie, a `foldOpen` to ekran wewnętrzny.
Przy 100% legenda na telefonie i na `foldOpen` mieści się w jednym wierszu.

## Zrzuty do obejrzenia (koordynator)

Z `PrereleasePolishTest` / `ScreenshotTestFold` na `foldOuter` albo ręcznie na urządzeniu:
1. Kodeks → Jednostki, pl i es, 100% i 130%: karta elity (czerwone „Wymaga: …” nad opisem, kłódka przy „Elita”).
2. Kodeks → Epoki, de i es przy 130%: nagłówek (plakietka awansu obok „Epoche 2/9”, nic nie dotyka numeru).
3. Kodeks → Porównanie, de i fr przy 100%, dowolny język przy 130%: legenda w 2 wierszach nad „Macierz kontr”.
4. Rekordy → Rankingi, Statystyki, Powtórki i Osiągnięcia w en/de, 100% i 130%: siatka 2 × 2. Sprawdzić, czy pod siatką
   zakładki tablic / pierwszy wiersz panelu nie są za blisko. pl przy 100% powinno zostać w jednym rzędzie z ikonami.

## Ryzyka kompilacji i działania

- Kod nie był kompilowany. Sygnatury sprawdziłem grepem: `Ui.measure(String, Float, Boolean)`, `Ui.fit(String, Float, Float, Float)`,
  `Ui.badge(..., sizeDp, color, alignRight)`, `Screen.ui/w/h/buttons`. `wrapWords` dostał domyślny parametr `bold = false`,
  więc dotychczasowe wywołania się nie zmieniają.
- `lineCount[i] + if (i == 3) eliteTagLineCount else 0` i przypisanie w `if (i == 3) eliteTagLineCount = if (...) ... else 0`
  są poprawne w Kotlinie, ale warto na nie spojrzeć przy pierwszym buildzie.
- Asercje testu oparte na prostokątach napisów mogą dać fałszywe nakładanie, jeśli inny napis Kodeksu leży w tym
  samym miejscu w innej klatce przewijania. Wtedy trzeba przejrzeć zrzut, a nie zmieniać układ.
- `RecordsTabs` w siatce przesuwa treść czterech ekranów Rekordów o 54 dp. Ekran ma ok. 858 dp wysokości, a listy się przewijają.

## Zostaje (poza zakresem, tylko notuję)

- **Kodeks → Porównanie, macierz kontr:** nazwy ról w kolumnach mają ok. 52 dp. „Cuerpo a cuerpo” (es), „Corpo a corpo” (pt)
  i „Nabijgevecht” (nl) mają ok. 6–8 sp przy 100% (`fit` do 7). Na liście właściciela tego nie było.
  Propozycja: na wąskim ekranie nagłówki kolumn w dwóch wierszach albo skróty ról.
- **Rankingi: 5 zakładek tablic** po ok. 56 dp na Foldzie zewn. (ok. 10,5 sp przy 100%, przy 130% mniej albo sama ikona).
  W turze 10 świadomie zostawione w dzienniku audytu. Te zakładki nie mają `tooltipTitle`, więc zmiana `Chrome.tabLabel`
  odebrałaby TalkBack etykietę. Dlatego ich nie ruszałem.
- Pasek bitwy (`compactTop`): bez zmian (decyzja właściciela).
- Notka w `century-war.md` („zostały: …”) do zaktualizowania przez koordynatora po scaleniu i zielonych testach.
