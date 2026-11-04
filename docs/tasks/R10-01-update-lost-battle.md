# R10-01: Bitwa przerwana aktualizacją — komunikat, zwrot próby dnia, medale pocieszenia

**Gałąź:** `claude/r10-01-utracona-bitwa` z `origin/integ/tura10` (najlepiej po scaleniu D6).
**Teksty:** tylko nowa `part188()` w `engine/L.kt` (10 języków: pl, en, de, es, fr, it, pt, uk, nl, tr;
`putAll(part188())`; liczba `putAll(part` = liczba `private fun part`). **Gradle:** wyłącznie
`flock /tmp/claude-0/gradle.lock ./gradlew ...`. Źródło: `docs/research/R10-backlog.md`, TOP 1.
Commity po polsku, wypychaj często.

## Cel

Zrzut przerwanej bitwy (`filesDir/battle.snap`) z poprzedniej wersji gry jest dziś po
aktualizacji **po cichu** odrzucany, gdy różni się `BattleSnapshot.VERSION` albo odcisk
balansu `defsFingerprint`. Gracz traci bitwę bez słowa. Jeśli było to rankingowe wyzwanie dnia,
traci też jedyną próbę dnia: `ModeScreen.onClick` ustawia `pr.lastRankedDaily = today` już
przy starcie, ok. wiersza 503. W testach zamkniętych aktualizacje przychodzą co kilka dni, więc
ten przypadek będzie się powtarzał.

Po zmianie gracz po aktualizacji widzi w menu: „Gra została zaktualizowana i przerwanej bitwy
(Oblężenie bez końca, fala 23) nie da się wznowić. Otrzymujesz 75 medali.” Dla dzisiejszego
rankingowego wyzwania dnia komunikat brzmi: „…Dzisiejsza próba wyzwania dnia została
przywrócona.”

## Stan w kodzie

- `sim/.../BattleSnapshot.kt`:
  - `object BattleSnapshot`: `VERSION`, `decode(bytes)` (ok. 238–252: magic, wersja, długość,
    CRC). Odrzucenie wersji i odcisku daje `null` bez powodu. `read()` (257) zaczyna od
    `if (i.readInt() != defsFingerprint) return null`.
  - `class SnapshotStore(file)` (474): `saveAsync(bytes, battleId)`, `saveNow`, `deleteAsync`,
    `deleteSettledAsync`, `loadBattle(settledBattleId)` (589–633). Przy niezgodnym zapisie
    „zostaje do diagnostyki” i zwraca `null`. Przy zrzucie z **nowszej** wersji zwraca `null`
    (`snapshot-version`).
- `MainActivity.kt` ok. 139–157: wątek startu → `snapshots.loadBattle(profile.lastSettledBattle)`
  → `finishStartup(...)` → `game.resumeOffer = restored` (216).
- `screens/BattleScreen.kt`: `snapshotBytes()` (1268), zapisy w `init` (387), autozapis w `update`
  (666), `onPause` (1265). `battleId` (71). `onFinished` → `pr.settleBattle(id, …) { settleRewards }`.
  `settleRewards` (ok. 1331–1400) liczy nagrody za porażkę.
- `meta/Profile.kt`: `settleBattle(battleId, onPersisted, apply)` (722, idempotentne przez
  `lastSettledBattle`), `lastRankedDaily` (225).
- `engine/Game.kt`: `showNotice(message, icon)` (283), `resumeOffer` (126).
- `res/xml/backup_rules.xml` i `data_extraction_rules.xml`: `battle.snap` nie trafia do kopii
  (reguła `include` tylko dla profilu i historii). Nowy plik też ma zostać poza kopią, więc
  nie dopisuj go do `include`.

## Zmiany

### 1. Opis bitwy obok zrzutu (`:sim`, bez wpływu na wynik bitwy)
- Nowa klasa w `BattleSnapshot.kt`:
  ```kotlin
  class BattleMeta(val battleId: Long, val mode: Mode, val stageIndex: Int, val weekly: Long,
                   val dailyDay: Long, val rankedDaily: Boolean, val wave: Int, val timeS: Int,
                   val kills: Int, val consolation: Int, val savedAtMillis: Long)
  ```
  Format pliku `battle.meta` musi być **stały i niezależny od** `BattleSnapshot.VERSION`: własny
  magic, `META_VERSION = 1`, pola przez `DataOutputStream`, CRC32, ok. 60 bajtów, zapis atomowy
  przez `SaveGuard.atomicWrite`. `Mode` zapisuj nazwą (`name`), nie liczbą porządkową.
- `SnapshotStore.saveAsync(bytes, battleId, meta: ByteArray? = null)`: meta zapisywana w tym samym
  zadaniu `io` co zrzut, **po** udanym zapisie zrzutu. `deleteAsync`, `deleteSettledAsync`
  i `delete()` usuwają też `battle.meta`.
- `BattleSnapshot.probe(bytes): SnapshotReject` z wartościami `OK`, `CORRUPT` (magic, długość,
  CRC, błąd odczytu), `OLDER_FORMAT`, `NEWER_FORMAT`, `BALANCE_CHANGED`. Logika ma być ta sama co
  w `decode`/`read`, ale bez budowania bitwy. `decode` ma się zachowywać tak jak dziś.
- `SnapshotStore.loadBattle(...)`: gdy żaden kandydat się nie wczytał, a powód to
  `OLDER_FORMAT` lub `BALANCE_CHANGED`, ustaw `lostByUpdate: BattleMeta?` z pliku meta (jeśli jest
  poprawny i `battleId != settledBattleId`). `CORRUPT` i `NEWER_FORMAT` **nie** dają rekompensaty
  (uszkodzenie albo cofnięcie wersji), tylko dotychczasowe `SaveGuard.report`.

### 2. Wypełnianie meta w bitwie (`BattleScreen`)
- Przy każdym `saveAsync(snapshotBytes(), battleId)` dołącz `metaBytes()` (ta sama klatka, wątek
  gry; tablica może być alokowana, bo autozapis to co `AUTOSAVE_EVERY`, a nie co klatkę).
- `consolation` = nagroda, jaką `settleRewards` dałby **teraz za porażkę**. Wydziel czystą funkcję
  `lossReward(cfg, battle, rankedDaily): Int` w nowym `meta/LostBattle.kt` i wywołuj ją z
  `settleRewards` w gałęzi porażki. Wynik `settleRewards` ma się nie zmienić (test niżej).
  Zasady: kampania i potyczka jak przy porażce; Endless jak przy porażce, ale **bez** premii progu
  tygodnia; rankingowe wyzwanie dnia = 0, bo zamiast medali gracz dostaje zwrot próby;
  nierankingowe wyzwanie dnia jak dziś (`5 * effort`).

### 3. Start i menu
- `MainActivity` (wątek startu, obok `loadBattle`): `val lost = snapshots.lostByUpdate`
  → `game.lostBattle = lost` w `finishStartup`.
- `Game`: pole `lostBattle: BattleMeta?`. Rozliczenie na wątku gry przy pierwszym `MenuScreen.update`
  (albo w `finishStartup` przez `game.post`):
  ```
  profile.settleBattle(meta.battleId) {
      medals += meta.consolation
      if (meta.rankedDaily && meta.dailyDay == Daily.dayNumber() && profile.lastRankedDaily == meta.dailyDay)
          profile.lastRankedDaily = -1L
  }
  ```
  Następnie `snapshots.deleteSettledAsync(meta.battleId)` (usuwa zrzut i meta) oraz
  `showNotice(tekst, Icons.CLOSE)`. Gdy `settleBattle` zwróci `false` (już rozliczone), nie dawaj
  ani medali, ani komunikatu.
- Komunikat bez okna modalnego (ta sama ścieżka banerów co `save154_problem`, `MenuScreen.update`
  ok. 431). Nie pokazuj go nad bitwą ani na wynikach (decyzja właściciela o powiadomieniach).
- Gdy jest `resumeOffer`, `lostBattle` nie może jednocześnie istnieć (jeden plik zrzutu). Dodaj
  asercję w teście.

### 4. Teksty `part188()`
- `lost188_title`: „Gra została zaktualizowana”.
- `lost188_body`: „Przerwanej bitwy (%1$s) nie da się wznowić w nowej wersji.”, gdzie `%1$s` to
  tryb i postęp, np. „Kampania, etap 7”, „Oblężenie bez końca, fala 23”.
- `lost188_medals`: „Otrzymujesz %s.” (z `L.plural("medals_n", …)`).
- `lost188_daily`: „Dzisiejsza próba wyzwania dnia wróciła.”
- Baner ma najwyżej 2 wiersze. Złóż `title` + `body` + (`medals` | `daily`) i sprawdź długość
  w 10 językach na zewnętrznym Foldzie.

## Kroki

1. `BattleMeta` + zapis/odczyt + `probe` + testy `:sim`.
2. `lossReward` wydzielony z `settleRewards` + test równoważności.
3. Meta przy zapisach w `BattleScreen`.
4. Start → `Game.lostBattle` → rozliczenie i baner.
5. `part188()`, zrzuty banera (telefon 720p, zewnętrzny Fold; pl, en, de).

## Testy do dopisania

- `sim/src/test/.../SnapshotMetaTest.kt`: zapis i odczyt meta; uszkodzone CRC daje brak meta;
  `probe` dla każdego powodu (zmieniony bajt odcisku = `BALANCE_CHANGED`, wersja − 1 spoza
  obsługiwanych = `OLDER_FORMAT`, wersja + 1 = `NEWER_FORMAT`, obcięty plik = `CORRUPT`);
  `delete*` usuwa oba pliki.
- `app/src/test/.../meta/LostBattleTest.kt`: `lossReward` = wynik gałęzi porażki `settleRewards`
  dla kampanii (Łatwy, Normalny, Trudny), potyczki, Endless, Oblężenia tygodnia (bez premii)
  i wyzwania dnia (rankingowe = 0).
- Test integracyjny w duchu `BattleSettlement156Test` / `LifecycleProfile160Test`:
  1. rankingowe wyzwanie dnia, `onPause` i kopia katalogu, podmiana 4 bajtów odcisku w
     `battle.snap` z poprawieniem CRC (model aktualizacji balansu);
  2. drugi start: brak `resumeOffer`, baner w menu, `lastRankedDaily == -1`, medale bez zmian;
  3. trzeci start: brak banera, medale i `lastRankedDaily` bez zmian (rozliczenie raz).
  To samo dla Endless (medale rosną o `consolation`) i dla uszkodzonego zrzutu (bez medali, bez banera).
- Proces zabity między zapisem profilu a usunięciem plików: kolejny start nie daje medali drugi raz
  (`lastSettledBattle`).
- `LTest`: nowe klucze mają 10 tłumaczeń.

## Kryteria odbioru

- Po zmianie odcisku balansu albo wersji formatu gracz widzi jeden baner i dostaje rekompensatę
  dokładnie raz. Wznowienie zgodnego zrzutu działa jak dotąd (`ResumeDialogTouchTest`,
  `FirstHourJourneyTest.tutorialInterruptedResumedSettlesOnce` zielone).
- Uszkodzony albo nowszy zrzut nie daje medali.
- Bez nowych plików w kopii zapasowej. Zrzuty bitew i złote powtórki bez zmian (PROTOCOL bez
  zmian, bo wynik bitwy się nie zmienia).
- Brak alokacji w pętli klatki poza chwilami autozapisu (`LowEndScreensPerformanceTest`,
  testy alokacji bitwy zielone).
- Pełne testy zielone: `./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`.

## Ryzyka

- Konflikt z D6 w `BattleSnapshot.kt` (D6 podbija `VERSION` i dodaje odczyt v19). Twoje zmiany
  są w `SnapshotStore` i w nowej funkcji `probe`, a `decode`/`read` zostają. Scal D6 najpierw.
- Kolejność zapisów: meta zapisuj po udanym zrzucie, inaczej meta mogłaby opisywać nowszą bitwę
  niż zrzut. Przy rozbieżności `battleId` meta jest ignorowana.

## Czego nie ruszać

- Format i odczyt samego zrzutu (`decode`/`read`/`VERSION`), PROTOCOL, złote powtórki, balans.
- Okno „Wznowić bitwę?” i jego dotyk (`ResumeDialogTouchTest`).
- Reguły kopii zapasowej (nowy plik ma zostać poza kopią).
- Zatwierdzony pasek `compactTop`, celowanie ostrzału, zasada „bez powiadomień na wynikach”.
