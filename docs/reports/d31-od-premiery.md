# Dzień 31: zasady sprzed premiery w wersjach testowych (R12, pozycja 5)

Gałąź `claude/dzien-31-od-premiery` (baza `integ/tura10` + `claude/research-12`).
Wariant zatwierdzony przez właściciela: zasady bez zmian, w grze tylko dopiski.
W tym środowisku nie da się zbudować projektu (sieć blokuje dl.google.com). Kod nie był
kompilowany ani uruchamiany, sygnatury sprawdziłem grepem.

## Zmiany

| Plik | Co |
|---|---|
| `app/src/main/java/com/centurywar/meta/PreReleaseRules.kt` (nowy) | Czysta funkcja: `active(testerBuild, day)` = wersja testowa i `day < Daily.RELEASE_DAY`; `isTestVersionName` (warunek z `app/build.gradle.kts`: `0.` albo sufiks); `lockedBeforeRelease(metric)` (DAILY_EVENTS, WEEKLY_RULESETS); `achievementNote(def, unlocked, …)` |
| `app/src/test/java/com/centurywar/meta/PreReleaseRulesTest.kt` (nowy) | Okno czasowe (dzień przed premierą: tak, dzień premiery: nie), produkcja `1.0.0` nigdy, zbiór oznaczonych osiągnięć, dowód z `:sim`, że przed premierą nie ma wydarzeń dnia ani zestawów tygodnia (120 dni), teksty w 10 językach |
| `engine/Game.kt` | `var preReleaseNotesEnabled = BuildConfig.TESTER_SURVEY`: ten sam warunek co ankieta, a testy mogą go wyłączyć |
| `screens/AchievementsScreen.kt` | Złoty dopisek „Dostępne od premiery” w wierszu postępu, na prawo od „0/6”. Napis ma zawsze 11 sp, a gdy się nie mieści, kończy się „…” (`RowNote.ellipsize`). Tekst jest liczony w `layout()`, a warunek sprawdzany w klatce |
| `screens/ModeScreen.kt` | W wyzwaniu dnia i w zakładce tygodnia jedna linia pod przyciskiem Graj, w pustym pasie 24 dp (`panel.bottom − 8 dp`), w kolorze DIM, 11 sp. Gdy pełne zdanie się nie mieści, pokazuje krótszą wersję („Pełne zasady od premiery”), a w ostateczności dodaje wielokropek. Warunek zależy od dnia na ekranie, więc podgląd dnia po premierze nie ma tej linii |
| `engine/L.kt` | Nowa część `part203` z kluczami `t203_ach_release`, `t203_rules_note` i `t203_rules_note_short` w 10 językach, bez placeholderów. Liczba `putAll(part` i `private fun part` jest równa: 76 = 76 |
| `screens/StoreGraphicsTest.kt` | `g.preReleaseNotesEnabled = false`: kadry sklepu (także zakładka tygodnia) pokazują wersję produkcyjną |

Bez zmian: `Daily.RELEASE_DAY`, `:sim`, rankingi, powtórki, profil (nic nie jest zapisywane),
czytnik ekranu (opisy kontrolek), powiadomienia, `compactTop`.

## Odstępstwo do decyzji koordynatora

Zlecenie wymienia **dwa** osiągnięcia. Z kodu wynika, że przed premierą nie da się zdobyć
**czterech**. Wydarzeń dnia nie ma przed `RELEASE_DAY`, a zestawy tygodnia zaczynają się
od `weekNumber(RELEASE_DAY) + 3`. Dlatego żadna z tych metryk nie rośnie:
- `daily_events_6` „Sześć wydarzeń” i `daily_events_9` „Kalendarz wojny” (DAILY_EVENTS),
- `weekly_rules_3` „Trzy próby tygodnia” i `weekly_rules_6` „Sześć oblicz wojny” (WEEKLY_RULESETS).

Dopisek jest przypisany do metryki, więc dostają go wszystkie cztery osiągnięcia, zgodnie
z celem pozycji („tester nie poluje na osiągnięcia, których nie da się zdobyć”). Jeśli mają
zostać tylko dwa, wystarczy w `PreReleaseRules.lockedBeforeRelease` sprawdzać `def.id`
zamiast metryki i poprawić oczekiwany zbiór w teście.

Uwaga: zestawy tygodnia ruszają dopiero 3 tygodnie po premierze. Dopisek „od premiery”
znika w dniu premiery, czyli trochę wcześniej, niż da się zdobyć oba „weekly_rules_*”.
To zgodne z zatwierdzonym tekstem, ale warto o tym wiedzieć.

## Ryzyka kompilacji i testów (do sprawdzenia przy budowie)

- `RowNote.ellipsize` ma widoczność `internal` w companion (pakiet `engine`). Wywołanie
  z `screens` działa, bo to ten sam moduł `app`.
- Dodałem `private companion object { const val NOTE_SP = 11f }` w `AchievementsScreen`
  i `ModeScreen`. Żadna z tych klas nie miała wcześniej companion.
- `PreReleaseRulesTest` używa `Bonuses()` (domyślne argumenty), `Daily.config(...).dailyEvent`,
  `Weekly.ruleSet(Weekly.weekNumber(day))`, `L.raw` i `L.CODES`. Sygnatury sprawdziłem.
- Testy zrzutów ekranu (nie sklepu) w debug pokażą nowe dopiski, bo `TESTER_SURVEY = true`
  i dzisiejsza data jest przed premierą. Jeśli któryś test porównuje piksele albo szuka
  nakładania tekstu w dolnym pasie panelu wyzwania lub w wierszu postępu osiągnięć, trzeba
  w nim ustawić `preReleaseNotesEnabled = false`.
- Przy skali tekstu 130% górna krawędź linii pod „Graj” ma ok. 2 dp zapasu do przycisku.
  Trzeba to sprawdzić na zrzucie 720p i na zewnętrznym Foldzie (904 px).
