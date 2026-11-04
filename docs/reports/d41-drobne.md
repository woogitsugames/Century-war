# D41 — trzy drobne poprawki

Gałąź `claude/dzien-41-drobne` (baza `claude/dzien-36-fold-napisy` + scalony `claude/dzien-39-audyt`).
Każda poprawka to osobny commit. Gradle nie działa (sieć blokuje dl.google.com), więc testów `:app`
(Robolectric) nie uruchomiłem. Sygnatury sprawdziłem grepem. `:sim` skompilowałem i przetestowałem
poza Gradle (Kotlin 2.0.21 z `/opt/gradle-8.14.3/lib`, JUnit 4.13.2, jak w d35). Narzędzia są tylko
w `/tmp/claude-0/d41/` (`build_sim.sh`), nie ma ich w repo.

Bez zmian: symulacja, wyniki bitew, `SimVersion.PROTOCOL` (0.8.8), `compactTop`, celowanie ostrzału,
TalkBack. Nie ma nowych tekstów, więc `part204` nie powstał.

## 1. Wyciek wątku `snapshot-io` (`SnapshotStore`)

- **Miejsce:** `sim/src/main/kotlin/com/centurywar/game/BattleSnapshot.kt` (klasa `SnapshotStore`),
  `MainActivity.onDestroy` i `TestLoopActivity.onDestroy`.
- **Błąd:** executor tworzył się leniwie przy pierwszym zapisie albo usunięciu i nikt go nie zamykał.
  Każde wyjście z gry (Wstecz) i ponowne otwarcie w tym samym procesie zostawiało bezczynny wątek.
  Ten sam wzorzec co `PlaytestLog` w d39, pkt 1 i 3.
- **Poprawka:**
  - Nowa metoda `close()` (wywołuje `shutdown`). Zapisy już zlecone, m.in. z `onPause`, kończą się normalnie.
  - `close()` nie tworzy wątku, jeśli ten jeszcze nie powstał.
  - Wywołania po `close()` nie rzucają wyjątku i nic nie przepada. `saveAsync`, `deleteAsync`,
    `deleteSettledAsync`, `saveNow` i `delete` wykonują się wtedy na wątku wołającym. Przykład takiego
    wywołania: `deleteSettledAsync` z wątku profilu albo wywołanie zwrotne Play Games po `onDestroy`.
  - Zadania z kolejki i zadania wykonywane na miejscu mają wspólny zamek I/O (`ioMutex`), więc nie
    zapisują pliku jednocześnie. Kolejność zamków jest zawsze `ioMutex` → `lock`.
  - Numeracja `seq`/`written` się nie zmieniła: starszy zapis z kolejki nadal nie nadpisze nowszego.
  - `awaitWrites` po zamknięciu czeka na dokończenie kolejki (`awaitTermination`).
- **Testy:**
  - `sim/src/test/kotlin/com/centurywar/game/SnapshotStoreCloseTest.kt` (3 testy):
    - `close()` bez użycia nie tworzy wątku;
    - 20 zleconych zapisów kończy się po `close()` (ostatni zrzut i `.meta` są na dysku), a wątek się kończy;
    - wywołania po `close()` (zapis, `saveNow`, trzy rodzaje usuwania) działają i nie rzucają wyjątku.
  - `PlaytestClose39Test.destroyClosesPlaytestWriter` sprawdza dodatkowo `snapshotStore.isClosed`
    po `destroy` (Robolectric, nieuruchomiony).
  - Cały `:sim` poza Gradle: **OK, 318 testów** (w tym złote powtórki).

## 2. Dzień z zegara gry w `AchievementsScreen` i `ModeScreen`

- d34 pkt 4 (po d29): oba ekrany liczyły dzień z `Daily.dayNumber()`, czyli z zegara systemowego.
- Teraz używają `Daily.dayNumber(g.clock())`:
  - `AchievementsScreen.today`: notka „Dostępne od premiery”;
  - `ModeScreen`: dzień i tydzień wyzwania, start wyzwania dnia i tygodnia. Odliczanie do końca
    wyzwania tygodnia (dotąd `System.currentTimeMillis()`) też liczy się z `g.clock()`, bo musi się
    zgadzać z tygodniem.
- `previewDailyDay` (zrzuty) bez zmian. Gra działa jak dotąd, bo `g.clock` domyślnie zwraca czas systemowy.
- **Test:** `app/src/test/java/com/centurywar/screens/GameClockScreens41Test.kt` (Robolectric). Zegar gry
  jest ustawiony na stały dzień (`RELEASE_DAY + 10`). Test sprawdza:
  - dzień na obu ekranach;
  - zmianę dnia w `update`;
  - że start wyzwania dnia zapisuje `lastRankedDaily` na dzień zegara gry.

## 3. Macierz kontr Kodeksu na zewnętrznym Foldzie

- **Problem z d36, sekcja „Zostało”:** nazwy ról w kolumnach i wierszach miały ok. 52 dp. W es, pt i nl
  malały do 6–8 sp, a przy tekście 130% problem dotyczył więcej języków.
- **Zmiana** (`CodexComparePanel`, tylko gdy `stacked`, czyli wąski pion < 420 dp i `h > w`, jak selektory z dnia 26):
  - nazwa roli atakującej stoi w osobnym wierszu nad rzędem komórek: pełna szerokość, 11 sp (minimum 10 sp), złota;
  - komórki zajmują 4 kolumny po ok. 72 dp zamiast 5 kolumn po ok. 57 dp;
  - nagłówki kolumn są poziome (najmniej 10 sp), jeśli w 10 sp mieszczą się wszystkie nazwy ról.
    W przeciwnym razie wszystkie są **obrócone o 90°** (czytane od dołu), mają 11 sp, a wiersz
    nagłówków ma wysokość najdłuższej nazwy;
  - karta macierzy rośnie o `counterShift`, a wykres i `contentHeight` przesuwają się w dół (tak samo jak
    `legendShift` w d36). Wszystko jest liczone w `layout`, nie w klatce.
- **Pomiar przybliżony** (DejaVu Sans Bold ×0,88, 10 sp, 100%):
  - najdłuższe nazwy: es „Cuerpo a cuerpo” ok. 82 dp, pt ok. 69, nl „Nabijgevecht” ok. 65, uk ok. 62 dp,
    przy kolumnie ok. 67 dp;
  - przy 100% nagłówki obracają się w es, pt i prawdopodobnie w nl, uk i tr; pl, en, de, fr i it zostają poziome;
  - przy 130% obrót jest prawie wszędzie;
  - macierz rośnie o ok. 100–190 dp, a lista się przewija.
- Telefon i rozłożony Fold mają dotychczasowy układ (`counterNarrow == false`).
- **Test:** `CodexFoldOuterLabelsTest.counterRoleHeadersReadableOnFoldOuter`. Fold zewnętrzny, 10 języków, 100% i 130%.
  - Dla każdej roli test sprawdza nagłówek kolumny i nazwę wiersza: są narysowane, mają ≥ 10 sp,
    mieszczą się na ekranie i nie nachodzą na żaden inny napis (komórki, inne role, nagłówek).
  - Rejestrator napisów mapuje prostokąty przez macierz płótna (`sr`), więc obrócony tekst jest
    sprawdzany w układzie ekranu. Dotychczasowe asercje dalej używają `r`.
  - Test wymaga też, żeby wystąpiły oba warianty nagłówków (obrócony i poziomy).
  - `otherScreensKeepLayout` sprawdza dodatkowo `!counterNarrowForTest` na telefonie i na rozłożonym Foldzie.
  - Nowe punkty dostępu dla testów: `counterNarrowForTest`, `counterRotatedForTest`, `roleNamesForTest()`,
    `counterTopForTest`, `counterHeightForTest`.

## Do uruchomienia (gdy Gradle zadziała)

```
./gradlew :sim:test
./gradlew :app:testDebugUnitTest --tests '*PlaytestClose39Test*' --tests '*GameClockScreens41Test*' \
  --tests '*CodexFoldOuterLabelsTest*' --tests '*CodexCompareScreenshotTest*' --tests '*ScreenshotGoldenTest*' --console=plain
```

## Ryzyka

- Kod `:app` nie był kompilowany. Do sprawdzenia przy pierwszym buildzie:
  - `@Suppress("DEPRECATION") val m = matrix` w rejestratorze testu (`Canvas.getMatrix()`);
  - etykieta `return@runIo` w lambdach `SnapshotStore`. Ten kod jest w `:sim`, a `:sim` skompilował się poprawnie.
- Wyśrodkowanie obróconego nagłówka (`em * 0,3`) wynika z metryk czcionki, a nie z obejrzanego zrzutu.
  Do obejrzenia: Kodeks → Porównanie na `fold_cover` (`CodexCompareScreenshotTest`), es i nl przy 100% i 130%.
- Po `close()` zapis wykonuje się na wątku wołającym. Jeśli wołającym jest wątek UI po `onDestroy`
  (wywołanie zwrotne Play Games), może to być krótki dostęp do dysku. Zdarza się to rzadko, ale
  zrzut nie przepada.
