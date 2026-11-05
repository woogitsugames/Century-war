# Dzień 45 — lejek kampanii i dowódca w raporcie (R13, TOP 5 poz. 2)

Baza: `integ/tura10`. Gałąź: `claude/dzien-45-lejek`. Gradle niedostępny (dl.google.com
zablokowany): testy JUnit napisane, **nieuruchomione**. Python: `python3 -m unittest discover -s tests`
→ **67 testów OK** (1 nowy, 2 zmienione).

## Zmiany

| Plik | Zmiana |
|---|---|
| `meta/PlaytestLog.kt` | Lejek: 5 stałych `IntArray(STAGE_COUNT)` (próby, wygrane, kapitulacje, sekundy bitew, bitwy z dowódcą); `STAGE_COUNT = Campaign.stages.size` (36). `battleSettled` dostał parametry z wartościami domyślnymi `stage = -1, battleSeconds = 0, commander = false` (stare wywołania kompilują się bez zmian). `stageStats()` zwraca kopię dla raportu. Zapis/odczyt pola `stages`. |
| `screens/BattleScreen.kt` (`settleRewards`) | Przekazuje `cfg.stageIndex`, `battle.time.toInt()` i dowódcę: `achievementEvents.commanderSummoned \|\| AchievementBattleTracker.commanderOnField(battle)` (tracker widzi każdy krok, stan końcowy jest zapasem jak w `Achievements`). |
| `crash/PlaytestReportText.kt` | Nowa sekcja `[Campaign stages]` między `[Progress]` a `[Play time]` (w `SECTIONS` na pozycji 2, dalsze indeksy przesunięte). `Input.stages` z wartością domyślną. `MAX_STAGE_LINES = 15`. |
| `tools/playtest_merge.py` | `STAGE_LINE` dopasowany do prawdziwego formatu, nowe `FUNNEL_SECTION`, `STAGE_MORE`; sekcja opcjonalna (raport bez niej nie jest „uszkodzony”); wklejka jednowierszowa; 2.10 ma uwagę z lejkiem i dowódcą na S19. |
| testy | `PlaytestLogTest` (+3), `PlaytestReportTextTest` (+2), `tests/test_playtest_merge.py` (`build_report(stages=…)`, nowy `test_campaign_funnel_section`, strażnik etykiet lejka w `test_constants_match_game_code`). |

`:sim`, wyniki bitew, profil, chmura, powtórki, `L.kt`, Data safety: bez zmian. Brak kodu testera
i czegokolwiek identyfikującego gracza. Nic nie jest wysyłane automatycznie.

## Format

### `playtest.json`

`VERSION` zostaje `1`. Pole opcjonalne, tylko etapy z próbami, pomijane gdy lejek pusty:

```json
"stages": [[18, 4, 1, 1, 3120, 3], [0, 1, 1, 0, 59, 0]]
```

Wpis: `[indeks etapu od 0, próby, wygrane, kapitulacje, sekundy bitew, bitwy z dowódcą]`.

- Stary plik bez `stages` wczytuje się jak dotąd (lejek pusty).
- Uszkodzony lejek (nie tablica, zły typ, za krótki wpis) zeruje **tylko lejek**, reszta dziennika
  zostaje; bez ostrzeżenia.
- Przycinanie: liczby 0…10 000 000, wygrane ≤ próby, kapitulacje ≤ próby − wygrane,
  dowódca ≤ próby; etap spoza 0…35 pomijany.
- Liczone są wszystkie bitwy kampanii, także samouczkowe (etap 1). Kapitulacja to próba
  i `surr`, nie wygrana. Czas to `battle.time` tej bitwy (po wznowieniu z migawki: całość).

### Raport dla testera

```
[Campaign stages]
S19: 4 tries, 1 win, 1 surr, 52m, cmd 3
S1: 1 try, 1 win, 0 surr, 0m, cmd 0
```

- Etapy od 1, od najwyższego, najwyżej 15 wierszy, potem `(+21 lower stages)`.
- Lejek pusty: `-`.
- `try/tries`, `win/wins`; czas bitew w pełnych minutach; `cmd` = liczba bitew z przywołanym dowódcą.
- Teksty stałe po angielsku, bez nowej części `L.kt`. Opis `pt186_desc` („postęp”) już to obejmuje.
- Długość: najgorszy przypadek (36 etapów z liczbami 10⁷, 10 ostatnich bitew, 5 sesji wydajności
  z ogromnymi liczbami) mieści się w `MAX_CHARS` 6000 (symulacja w Pythonie: ok. 3400 znaków). Test sprawdza, że raport kończy się
  `Battle speed x1/2:`, czyli nie jest obcięty. Ankieta zostaje pierwsza.
- Brak linii `Report format: 2`: to należy do poz. 1 (R13: wspólny format 2). Narzędzie rozpoznaje
  lejek po sekcji, nie po numerze formatu.

## Ryzyka kompilacji (Gradle nie działał)

Sygnatury sprawdzone grepem:
- `Campaign.stages` (sim, `com.centurywar.game.Campaign`);
- `BattleConfig.stageIndex`;
- `Battle.time: Float`;
- `AchievementBattleTracker.commanderSummoned` (publiczny getter) i `companion.commanderOnField(Battle)`;
- import `AchievementBattleTracker` już jest w `BattleScreen.kt`;
- `BattleHistory.Record(date, mode, stage, seconds)` z wartościami domyślnymi.

Miejsca do sprawdzenia przy pierwszym `./gradlew testDebugUnitTest`:
1. `PlaytestLogTest.corruptFunnelKeepsRestOfLog`: `listOf<Any>(…)` i `JSONObject.put(String, Any)` (przeciążenie `Object`).
2. `PlaytestReportTextTest.campaignFunnelStaysWithinLimit`: zakłada `Battle speed x1/2: no` (domyślny profil, TalkBack wyłączony w Robolectric).
3. Przesunięte indeksy `SECTIONS[3..6]` w `build`: sprawdzone ręcznie, kolejność sekcji w tekście = kolejność w tablicy.

## Do decyzji / dalej

- Poz. 1 (kod testera, `Report format: 2`) czeka na decyzję właściciela. Lejek działa bez niej.
- Narzędzie zgłasza w 2.10 3 etapy z największą liczbą nieudanych prób i dowódcę na S19.
  Na decyzję K19 trzeba porównać to z d37.
