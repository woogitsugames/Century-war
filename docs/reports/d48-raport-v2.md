# Dzień 48 — Raport v2, przypomnienie o raporcie, utracone wyzwanie dnia

Baza: `integ/tura10`. Gałąź: `claude/dzien-48-raport-v2`. Gradle niedostępny: kompilacja i testy
obejściem `tools/offline-build/` (`CW_OFFLINE_DIR=/tmp/claude-0/d48`, zależności skopiowane z d43).
Decyzje właściciela 5.11: konto firmowe; kod testera w raporcie — tak; przerwane rankingowe
wyzwanie dnia otwarte następnego dnia — medale jak za kapitulację.

## 1. Raport v2 (R13 TOP 5 poz. 1)

Tylko w wersjach testowych (`Game.testerSurveyEnabled` = `BuildConfig.TESTER_SURVEY`, ten sam
warunek co ankieta). Wersja produkcyjna zostaje przy formacie 1, bez nowych linii (test).

```
Language: pl
Report format: 2
Tester code: K7QM
Crashes: 1
ANRs: 0
...
[Progress]
...
Research levels: 12 of 110, medals 340
...
[Play time]
Days played: 7, sessions: 10
Day map (28 days, oldest first, 1 = played): 0000000000000000000011011111
```

- **Kod testera**: 4 znaki z `ACDEFHJKMNPQRTUVWXY3479` (bez 0/O, 1/I/L, 2/Z, 5/S, 6/G, 8/B),
  `SecureRandom`, losowany raz przy starcie sesji w wersji testowej, w `playtest.json` (`code`).
  Nie trafia do profilu, chmury ani powtórek (test: brak w `Profile.toJson`). Zły kod w pliku jest
  odrzucany i losowany od nowa. Reinstalacja = nowy kod (tester podaje pseudonim w formularzu).
- **Awarie i ANR**: po starcie, na wątku `crash-io` po `ExitReasons`, `CrashStore.fatalEvents()`
  (tylko odczyt: `fatal-` = awaria Javy, `exit-fatal-` = ANR albo błąd natywny, rozpoznany po linii
  `Przyczyna: ANR (`). Liczone raz (znacznik `crashSeen`), tylko od pierwszego dnia dziennika.
  Nic nie jest wysyłane. Ograniczenie: rotacja dziennika awarii (10 awarii, 3 zakończenia) przed
  następnym startem mogłaby zgubić zdarzenie — w praktyce liczymy przy każdym starcie.
- **Mapa dni**: `dayBits` (bit k = dzień `lastDay − k`, 64 dni); dziennik sprzed d48 dostaje
  pierwszy i ostatni dzień.
- **Badania**: suma kupionych poziomów z `Profile.researchLevels()` i maksimum z `Research.defs`,
  plus bieżące medale.
- Okno „Raport dla testera” w wersji testowej dopisuje `tr204_code_note` (10 języków) do
  `pt186_desc`; tekst produkcyjny bez zmian.
- `VERSION` dziennika bez zmian (wszystkie pola opcjonalne).

`tools/playtest_merge.py`: wzorce v2 dopasowane do prawdziwego formatu (`Tester code: -` i
`Day map: -` = brak), `Research levels` wymaga pełnej linii, CSV ma `day_map` i `research_levels`,
teksty o formacie 1/2 poprawione. Testy: `build_report(v2=…)`, strażnik etykiet v2 w kodzie Kotlin.
`python3 -m unittest discover -s tests` → **69 OK**.

## 2. Przypomnienie w 3. i 10. dniu (R13 poz. 3)

- `meta/TesterReportReminder.kt`: dzień testu = `today − firstDay + 1` (UTC). Dni 3–9 bez znacznika
  → baner „3”; od 10. dnia → „10” (oznacza oba). Znacznik `reminders` w `playtest.json`, zapis przy
  pokazaniu: raz na próg.
- `MenuScreen`: nieblokujący baner w pasie powiadomień (ten sam prostokąt co baner powiadomień),
  tylko w menu (nigdy nad bitwą ani na wynikach). Czeka na spokojne menu (1,5 s, bez okien,
  przerwanej bitwy, dymków, banerów i bez zaległej ankiety — ankieta ma pierwszeństwo). Chowa się,
  gdy nadejdzie powiadomienie. Dotknięcie otwiera to samo okno co O grze. TalkBack czyta tekst banera.
- `-PtesterFeedback=<e-mail|https://…>` → `BuildConfig.TESTER_FEEDBACK` (walidacja w
  `app/build.gradle.kts`, domyślnie pusty = jak dotąd). E-mail: `EXTRA_EMAIL` w intencji. URL:
  przycisk „Kopiuj i otwórz formularz” (raport do schowka + przeglądarka). Działa tylko w wersjach
  testowych. **Workflow CI bez zmian** (wejście do `test-release` można dodać później ręcznie).
- Teksty: `part204` w `L.kt` (`tr204_code_note`, `tr204_banner` z `%1\$s`, `tr204_form`), 10 języków.

Zrzuty (PL i EN, 10. dzień):
- `/tmp/claude-0/d48/d48_reminder_phone720_pl.png`, `/tmp/claude-0/d48/d48_reminder_phone720_en.png`
- `/tmp/claude-0/d48/d48_reminder_foldOuter_pl.png`, `/tmp/claude-0/d48/d48_reminder_foldOuter_en.png`

## 3. Polityka prywatności i Data safety

`privacy_policy_pl.md` i `privacy_policy_en.md`: jedno zdanie o losowym kodzie testera w raporcie
wysyłanym ręcznie (tylko wersje testowe). `data_safety.md`: wiersz dziennika testu uzupełniony.
Pola OWNER_INPUT bez zmian.

## 4. Utracone rankingowe wyzwanie dnia (d39 poz. 2)

`LostBattle.settle`: gdy `rankedDaily` i `dailyDay < today` (próby nie da się zwrócić), medale
`max(consolation, RANKED_LATE_MEDALS = 10)`. 10 to pewna część nagrody za kapitulację w rankingowym
wyzwaniu (`min(90, wynik/350) + 10`); stary opis zrzutu nie ma wyniku. Raz (przez
`Profile.settleBattle`), bez zwrotu próby, zwykły baner medali. Dzień nieznany (−1) albo w
przyszłości: bez medali. Test: `LostBattleTest.lateRankedDailyGivesSurrenderMedalsOnce` +
poprawiony `settleOnceRestoresOnlyTodaysRankedAttempt`.

## Testy

- Nowe: `crash/PlaytestReportV2Test` (5), `screens/TesterReportReminderTest` (5),
  `screens/TesterReminderScreenshotTest` (kategoria screenshots), `LostBattleTest` (+1).
- Uruchomione obejściem: nowe klasy + `PlaytestReportTextTest`, `PlaytestLogTest`,
  `TesterSurvey200Test` — OK; fastTest: patrz niżej.

fastTest obejściem (`run_fast.sh`, 140 klas w 6 paczkach): **764 testy OK, 0 błędów** (logi `/tmp/claude-0/d48/fast_fast_chunk_0*.log`). Lint, R8 i `./gradlew build` nie były uruchamiane (Gradle zablokowany).
