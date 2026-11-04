# D35 — etap 19 lżejszy o 6% (PROTOCOL 0.8.9)

Baza: `integ/tura10` + `claude/dzien-33-pomiar-19`. Gałąź: `claude/dzien-35-etap-19`.
Decyzja właściciela: złagodzić etap 19 mnożnikiem 0,94 (pomiar wstępny: `d33-pomiar-19.md`).
Szczegóły pomiaru: `docs/BALANCE.md`, sekcja „Etap 19 — łagodniejszy klif 18 → 19”.

## Zmiany

| Plik | Zmiana |
|---|---|
| `sim/.../Modes.kt` | `stageRamp`: `19 -> 1.265f` → **`19 -> 1.1891f`** (+ komentarz) |
| `sim/.../SimVersion.kt` | `PROTOCOL` 0.8.8 → **0.8.9** (KDoc „ranking 0.8.9”) |
| `sim/src/test/.../SecondHourControlTest.kt` | kontrola determinizmu z etapu 19 na **21** (`stageTwentyOneUnchanged`, `STAGE_21_BEFORE`, seed 91021, zapisane przy 0.8.8) |
| `app/src/test/.../ReleaseConfigTest.kt` | `GAME_VERSION` == "0.8.9" (`ScoreClient.GAME_VERSION = SimVersion.PROTOCOL`) |
| `server/src/test/...` (5 plików) | literały `gameVersion` / wersja weryfikatora "0.8.8" → "0.8.9" (jak przy 0.8.7 → 0.8.8) |
| `server/README.md` | przykład `gameVersion` i „obecnie `0.8.9`” |
| `docs/BALANCE.md` | wpis przed/po i linia regeneracji złotych powtórek |

**Dlaczego 1,1891, a nie 1,189:** klucz `19` w `when (i + 1)` to etap 19 (indeks 18, pierwszy
etap `midStart = 36 / 2`), a `BALANCE_POWER_SCALE_STAGES=19` sprawdza `stage.index + 1`, czyli ten
sam etap. Mnożnik w teście działa na gotowe `aiPower` (`base.aiPower * 0.94f`), korekta w kodzie
wchodzi w `stageRamp`. 1,265 × 0,94 = 1,1891 dokładnie; `1.1891f` daje `aiPower` **1,5150243**,
bitowo tyle co `1.611728f * 0.94f`. `1.189f` dałoby 1,5148969 — inny Float, inne bitwy. Pomiar
potwierdził: etap 19 po zmianie powtarza M3 0,94 z d33 co do bitwy (65 / 76 / 72 / 73%).
Siła AI etapu nie wpływa na nic poza `Battle` (`Campaign.difficulty` porównuje z `stages[i].aiPower`,
więc działa dalej dla nowych bitew).

## Złote powtórki i testy (poza Gradle)

Gradle nie działa (sieć blokuje dl.google.com). Tak jak w d33: `:sim` main+test skompilowane
kompilatorem Kotlin 2.0.21 z `/opt/gradle-8.14.3/lib` (`-module-name sim`, testy z
`-Xfriend-paths`), JUnit 4.13.2 + hamcrest uruchomiony bezpośrednio, katalog roboczy `sim/`.
Narzędzia tymczasowe tylko w `/tmp/claude-0/d35/` (nic w repo).

- `GoldenFixtures --regenerate` (to, co robi `:sim:regenerateGolden`): **10 plików bez zmian
  bajt w bajt** — kampania w powtórkach to etapy 1 i 29, wersja protokołu nie jest w JSON.
  Wpis powodu do `BALANCE.md` (`doLast` zadania) dopisany ręcznie, tym samym formatem.
- Wszystkie testy `:sim` (51 klas `*Test` + `BattleRulesTestVisual`): przed zmianą OK 315,
  po zmianie **OK 317** (315 + 2 testy `BattleRulesTestVisual`, które dołączyłem przy drugim
  przebiegu). Zawiera `GoldenReplayTest`, `SecondHourControlTest` (etap 21 = wynik z 0.8.8).
- Testów `:app` i `:server` nie uruchamiałem (Android/zależności Gradle).

## Pomiar z kodu, N=100, Normalny, etapy 16–24

`BalanceReport.campaignReport` skompilowany poza Gradle z zaślepką `meta.Profile` (jak d33),
zmienne jak w `tools/measure_stage19.sh` (`BALANCE_PROFILE=medale|target|medale-czas`,
`BALANCE_COMMANDER=on`, `BALANCE_PACE=now`), bez `BALANCE_POWER_SCALE`. Przebieg „przed” na
kodzie 0.8.8 powtarza liczby d33/`BALANCE.md` co do bitwy.

| Profil | Wygrane 18 → 19 przed → po | Oczek. 19 [min] przed → po | Iloraz 19/18 przed → po | Ocena przed → po |
|---|---:|---:|---:|---|
| medale | 81 → 49% → **65%** | 26,6 → **19,7** | 2,06× → **1,53×** | ściana → ostrzegawcza (−16 pp) |
| medale-czas, teraz | 90 → 71% → **76%** | 13,3 → **12,1** | 1,63× → **1,49×** | ostrzegawcza → **płynnie** |
| docelowy | 60 → 60% → **72%** | 15,9 → **12,0** | 1,03× → 0,78× | płynnie |
| docelowy + dowódca | 62 → 61% → **73%** | 15,4 → **12,2** | 1,01× → 0,80× | płynnie |

Etapy 16–18 i 20–24 identyczne przed i po we wszystkich czterech profilach.

**Cele z d33:** medale na 19 ≥ 60% ✓ (65%), iloraz medale-czas ≤ 1,6× ✓ (1,49×), docelowy
≤ 75% ✓ (72%), z dowódcą ≤ 75% ✓ (73%, 2 pp zapasu). **Niespełniony** formalnie warunek
„docelowy nie łatwiejszy niż na 18”: 72% vs 60% (oczek. 12,0 vs 15,5 min). d33 zaliczył 0,94
mimo to; wynika to z przeskoku profilu docelowego z 10% na 40% badań na etapie 19 (realny gracz
w `medale-czas` ma 37 → 39 poziomów i nadal widzi etap 19 jako trudniejszy od 18). „Medale”
zostaje w strefie ostrzegawczej tylko przez spadek −16 pp (próg 15 pp, ±10 pp szumu przy N=100).

## Do sprawdzenia przez koordynatora pełnym Gradle

1. `./gradlew :sim:test` (oraz `fastTest`/`balanceTest`) — powinno dać to samo co tutaj.
2. `ORG_GRADLE_PROJECT_goldenReason=... ./gradlew :sim:regenerateGolden -PserverOnly=true
   --no-configuration-cache` — oczekiwane: brak zmian w `sim/src/test/resources/golden/`. Zadanie
   dopisze drugą linię powodu do `BALANCE.md`; wtedy usunąć jedną z dwóch (moja jest ręczna).
3. `:server:test` (zmienione literały wersji) i `:app:testDebugUnitTest` (`ReleaseConfigTest`
   0.8.9, reszta `:app` niezależna od `stageRamp`, ale mogą być testy przypinające wyniki kampanii
   etapu 19 — nie znalazłem, `grep` po `stages[18]`/`stage_19` nic nie dał).
4. `tools/measure_stage19.sh` (M1 + M2, bez M3) przez Gradle na tej gałęzi — wiążące potwierdzenie
   liczb z tabeli (tagi `d33-*` w nazwach CSV zostają; etap 19 powinien wyjść jak wyżej).
5. Przy scalaniu: `century-war.md` (linia „Wersje”) → PROTOCOL 0.8.9; AAB dopiero po scaleniu
   (zmiana PROTOCOL unieważnia powtórki rankingowe sprzed 0.8.9).
