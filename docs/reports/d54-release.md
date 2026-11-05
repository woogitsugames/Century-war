# D54 — lint, próba release po zmianach 3–4.11, złote powtórki

Data: 5 listopada 2026 r. Gałąź `claude/dzien-54-release` (z `origin/main` 7dd64d95).
Backlog: `docs/research/R12-backlog.md`, pozycja 2b.

## 1. `:app:lintDebug`

Przed: **1 błąd**, 13 ostrzeżeń. Po: **0 błędów**, 13 ostrzeżeń.

- Błąd `SuspiciousIndentation` w `BattleScreen.kt` (układ kart doktryn): gałąź `else` bez
  nawiasów z pętlą bez wcięcia. Dodane nawiasy; zachowanie bez zmian.
- Błędów `NewApi` nie było (minSdk 26).

Ostrzeżenia (bez zmian, tylko do wiadomości):

| Id | Gdzie | Uwaga |
| --- | --- | --- |
| `InlinedApi` ×2 | `crash/ExitReasons.kt:60` (`REASON_ANR`, `REASON_CRASH_NATIVE`, API 30) | stałe wkompilowane, kod i tak wołany od API 30 |
| `UseRequiresApi` ×5 | `ExitReasons.kt:72, 113`, `engine/ThermalWatch.kt:53, 65, 72` | `@TargetApi` zamiast `@RequiresApi`; wywołania są za sprawdzeniem `SDK_INT` |
| `UnusedAttribute` ×4 | `res/xml/game_mode_config.xml:5–8` | atrybuty trybu gry działają od API 33, starsze je pomijają |
| `ObsoleteSdkInt` ×1 | `res/mipmap-anydpi-v26` | kwalifikator `-v26` zbędny przy minSdk 26 |
| `NewerVersionAvailable` ×1 | `app/build.gradle.kts:182` | Robolectric 4.17 dostępny (jest 4.16) |

Kompilator Kotlin zgłasza też przestarzałe API `AccessibilityNodeInfo` w
`engine/GameAccessibility.kt` i „Condition is always true” w linii 604 — ostrzeżenia, nie błędy.

## 2. Próba release jak smoke CI (R12 poz. 2b)

Odtworzone kroki z `.github/workflows/release-check.yml`: syntetyczne ID Play Games
(`scripts/ci_synthetic_play_games_ids.py`), znacznik `[DO DECYZJI]` w `data_safety.md`
podmieniony tylko na czas próby, `-PciTestDebugSigning=true` (podpis debug **tylko do próby**),
`-PskipCheck=releaseDay,playGames,serverUrl,privacyUrl,hmacKey,versionCode,ownerInput`,
`--no-configuration-cache`. Flaga podpisu debug wymaga środowiska smoke CI
(`CW_RELEASE_SMOKE=true`, `GITHUB_ACTIONS=true`, `GITHUB_EVENT_NAME=workflow_dispatch`) —
ustawione lokalnie tylko dla tego wywołania. Pliki `docs/store/*` przywrócone, nic z próby
nie trafiło do repo.

| Kontrola | Wynik |
| --- | --- |
| `:app:assembleRelease :app:bundleRelease` (R8 + `shrinkResources`) | **BUILD SUCCESSFUL**, brak `warning:` / `missing class` R8 |
| Bez klucza upload (`assembleRelease` bez `ciTestDebugSigning`) | zablokowane: „Brak prawidłowego podpisu release” |
| `verify_release_mapping.py` | OK: 27 klas (z enumami), 101 pól, bez zmian nazw i usunięć |
| `verify_release_dex.py` | OK: 1 DEX, 749 klas; 10 krytycznych, 13 enumów, 4 nazwy z manifestu |
| Klucze JSON zapisów w DEX (literały `put`/`opt*`/`get*`/`has`) | OK: `Profile` 89, `PlaytestLog` 28, `BattleHistory` 29, `Missions` 16, `ProfileBackup` 7 — żadnego brakującego |
| — nowe pola profilu | `welcomeBackDay` (powitanie), `hardStageWins` (korony), `campaignLossStreak`/`campaignLossStreakStage`, `lastSettledBattle` (utracona bitwa), `tutorialStep`/`tutorialStepBattle` — obecne |
| — `playtest.json` | `code` (kod testera), `stages`, `tutorialSeconds`, `firstWinSeconds`, `dayBits` (lejek, kamienie milowe), `survey`, `surveyRating`, `surveyIssue`, `surveyDay` (ankieta), `crashes`, `anrs`, `reminders` — obecne |
| `tools/r8-sim-smoke/run.sh` | **82/82 OK** przed i po R8 (10 złotych powtórek, zrzuty v16–v20, rekord powtórki); R8 przemianował 62 klasy `com.centurywar` |
| Manifest APK (`aapt dump badging`) | `com.centurywar.game`, `MainActivity` uruchamialna, minSdk 26, targetSdk 36, versionCode 1 |
| `TestLoopActivity` w release | nazwa niezmieniona (mapping, DEX); w manifeście `:testloop`, `exported`, filtr `com.google.intent.action.TEST_LOOP` + `application/javascript`, `com.google.test.loops` = 4, grupy `1,4` i `2,3` |
| Biblioteki natywne `.so` | brak w APK i AAB |
| Profil ART | `assets/dexopt/baseline.prof(m)` w APK, `BUNDLE-METADATA/.../baseline.prof(m)` w AAB |

Rozmiary (`scripts/release_size_report.py`):

| Paczka | 3.11.2026 | 5.11.2026 | Zmiana |
| --- | ---: | ---: | ---: |
| APK | 20 998 247 B | **21 118 507 B** (20,1 MiB) | +117,4 KiB |
| AAB | 21 614 758 B | **21 866 670 B** (20,9 MiB) | +246,0 KiB |
| DEX (surowo) | 1 702 560 B, 695 klas | 1 926 816 B (1 881,7 KiB), 749 klas | +54 klasy |
| Muzyka (21 × `.ogg`) | 19 927 585 B | 19 927 585 B | 0 |

Przyrost to kod (pętla testowa, ankieta, raport v2, lejek, powitanie) i teksty.

### Poprawka narzędzia

`tools/r8-sim-smoke` sprawdzał zrzuty v16/v17/v18 i ponowny zapis w v18, ale po podbiciu
`BattleSnapshot.VERSION` do 20 `encode()` zapisuje v20, więc narzędzie zgłaszało 4 błędy już na
klasach niezminifikowanych (nie był to błąd R8). Teraz sprawdza v16..`VERSION` (dziś v20),
a ponowny zapis i dalszą symulację — w bieżącym formacie.

### Czego próba nie obejmuje

Uruchomienia na urządzeniu (brak telefonu/emulatora): start release, intencja `TEST_LOOP`
przez `adb`, Play Games, import/eksport powtórek i wznawianie zapisów — do pierwszego raportu
przedpremierowego. Profilu (`org.json` z Androida) nie da się uruchomić po R8 bez urządzenia;
kontrola kluczy w DEX + jawne klucze JSON zastępują ją częściowo.

## 3. Złote powtórki

`:sim:regenerateGolden -PgoldenReason="PROTOCOL 0.8.10 (K1)" -PserverOnly=true
--no-configuration-cache`: **żaden z 10 wzorców się nie zmienił**. Zadanie dopisało drugą linię
z tym samym powodem co wpis K1 z 4.11 — usunięta; przy istniejącym wpisie dopisek o ponownej
regeneracji 5.11 bez zmian.
