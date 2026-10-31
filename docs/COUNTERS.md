# System kontr jednostek — Century War

## Założenia balansu

Rola atakującego jest w wierszu, rola celu w kolumnie. Wartość `1,00×` oznacza zwykłe obrażenia, a `1,35×` — 35% dodatkowych obrażeń. Mnożnik działa **wyłącznie na obrażenia zadawane jednostkom**, nie zmienia kosztu, szybkości ataku ani nagród za zabójstwo.

| Atakujący → / cel ↓ | Wręcz | Dystansowa | Ciężka | Elitarna |
|---|---:|---:|---:|---:|
| **Wręcz** | 1,00× | **1,35×** | 1,00× | 1,00× |
| **Dystansowa** | 1,00× | 1,00× | **1,30×** | 1,00× |
| **Ciężka** | **1,35×** | 1,00× | 1,00× | 1,00× |
| **Elitarna** | 1,00× | 1,00× | 1,00× | 1,00× |

### Uzasadnienie

- **Wręcz → dystansowa (+35%)**: szybkie i tanie jednostki wręcz karzą delikatnych strzelców, gdy skrócą dystans. Ich dojście do celu nadal pozostaje ryzykiem.
- **Dystansowa → ciężka (+30%)**: zasięg i skupiony ostrzał pomagają przełamać jednostki z dużą pulą HP. Bonus jest nieco niższy, bo dystansowe już mają przewagę pozycyjną.
- **Ciężka → wręcz (+35%)**: ciężkie jednostki powinny zatrzymywać napór taniej piechoty, ale ich wysoki koszt i czas szkolenia ograniczają masową produkcję.
- **Elitarna → jednostki (1,00×)**: elity mają już dużo HP, obrażeń i obrażenia obszarowe; ogólny bonus kontr byłby nadmierny. Ich specjalizacją pozostaje **oblężenie baz** przez istniejący mnożnik `UnitDef.siegeMul = 1,6×` (+60%).
- **Pozostałe relacje (1,00×)**: brak kar poniżej 100% bazowych obrażeń, więc jednostka nigdy nie traci całkowicie skuteczności z powodu złego dopasowania.

### Wieże od Epoki pary

Wieża w epoce `4..7` (Epoka pary, stali, krzemu, era orbitalna) zadaje **+15%** obrażeń celom o roli `HEAVY` pochodzącym z epok `4..7`. Do tej kategorii zaliczają się także kroczące maszyny z Epoki pary (rola `HEAVY` ma tam typ ciała `MECH`), a nie tylko późniejsze pojazdy gąsienicowe. Stara wieża albo zwykła piechota nie otrzymuje tej premii.

Ta premia dotyczy obrażeń **pocisku wieży** i jego eksplozji; nie jest premią przypisaną wszystkim pociskom tej samej epoki. Nie kumuluje się z kontrą rola → rola, bo wieża nie jest jednostką i nie ma roli.

## Implementacja

1. `Defs.counterMultiplier(attacker, defender)` odczytuje statyczną macierz 4×4 bez alokacji w trakcie bitwy. `Defs.turretCounterMultiplier(turretEra, defender)` określa bonus wież.
2. Każda ścieżka `Battle.damageUnit` otrzymuje rolę strzelca i epokę wieży: atak wręcz, trafienie pocisku, bezpośredni atak obszarowy i eksplozja pocisku. Jedna centralna funkcja nalicza odpowiednią premię **dokładnie raz na cel**.
3. `Projectile.shooterRole` przechowuje rolę do chwili trafienia, niezależnie od tego, czy strzelec nadal żyje. `Projectile.turretEra` identyfikuje pociski wieży. Oba pola są nadpisywane przy każdym wystrzale; przy ostrzale specjalnym są zerowane (`null` / `-1`), aby pula pocisków nie przenosiła starego bonusu.
4. Ostrzał specjalny pozostaje neutralny wobec ról. Obrażenia baz nadal podlegają dotychczasowym zasadom `siegeMul`, `siegePressure` i XP.
5. `Ai.pickRole` wykorzystuje rolę jednostki na pierwszej linii wroga. Z prawdopodobieństwem ograniczonym istniejącą liczbą losową preferuje dostępną finansowo kontrę; zachowuje wcześniejsze warunki obrony, elity, modyfikatora Horda strzelców i składu własnej armii.
6. `CounterTexts.roleHint(role, polish)` oraz `CounterTexts.turretHint(polish)` udostępniają gotowe teksty PL/EN. Nie zmieniano równolegle edytowanych plików UI.

## Ograniczenie: wieże nie mają HP

Obecny silnik ma dla wież wyłącznie slot, epokę, czas odnowienia i animację. **Nie obsługuje atakowania ani niszczenia wież** przez jednostki. Istniejący bonus oblężniczy `ELITE.siegeMul = 1,6×` działa więc na **bazy**, nie na wieże. Rozszerzenie go na wieże wymagałoby osobnego projektu (HP wież, wybór celu, usuwanie/odbudowa wieży, zachowanie AI i renderer). Nie wprowadzano ukrytej zmiany tych zasad.

## Testy

`CounterBattleSimTest` jest automatycznie uwzględniany przez filtr `--tests '*BattleSimTest*'`. Obejmuje:

- macierz 4×4 i jej zakres 1,00–1,50;
- trafienie bezpośrednie i atak obszarowy wręcz;
- pocisk i eksplozję dystansową, w tym zapamiętanie roli strzelca;
- premię późnych wież przeciw jednostkom ciężkim oraz brak premii dla innych celów i starszych wież;
- czyszczenie metadanych po ponownym użyciu pocisków do ostrzału specjalnego;
- obrażenia elit wobec bazy, ukończenie kontrolowanej bitwy i identyczny stan dwóch symulacji z tym samym seedem.

Uruchamianie na maszynie z Android SDK 36:

```bash
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --tests '*BattleSimTest*' --console=plain
```

## Zweryfikowane wyniki (GitHub Actions, 31.10.2026)

Uruchomienie: [Walidacja kontr 06 — Actions](https://github.com/woogitsugames/Century-war/actions/runs/41201671953).
Porównano identyczny `BattleSimTest` na `claude/bold-sagan-vwz5gm` (przed zmianą)
i `gpt/06-counters` (po zmianie), ze stałymi seedami zawartymi w teście.
Nowe `CounterBattleSimTest` sprawdzono dodatkowo na nowej gałęzi.

| Parametr | Przed | Po |
|---|---:|---:|
| Zakończone etapy kampanii w 30 min | 25/32 | 29/32 |
| Etapy z `TIMEOUT` | 7 (3, 4, 6, 18, 22, 25, 32) | 3 (4, 6, 14) |
| Zwycięstwa strony 0 | 16 | 16 |
| Zwycięstwa strony 1 | 9 | 13 |
| Pełna potyczka `over` | `true` | `true` |
| Tryb bez końca `over` | `true` | `true` |

**Regresja do obserwacji:** etap 14 kończył się przed zmianą, a po zmianie przekracza 30 min.
Etapy 4 i 6 przekraczają limit w obu wersjach. System kontr zmienia również zwycięzców
niektórych etapów; nie jest zamiennikiem osobnego strojenia balansu.

### Oryginalny stdout: wersja bazowa

```text
stage 1 era 0-0 power 0.72: winner=0 t=12.0min eras=0/0 kills=222/144
stage 2 era 0-0 power 0.78: winner=0 t=25.9min eras=0/0 kills=370/326
stage 3 era 0-1 power 0.84: TIMEOUT t=30.0min eras=1/1 kills=364/384
stage 4 era 0-1 power 0.89: TIMEOUT t=30.0min eras=1/1 kills=425/472
stage 5 era 1-1 power 0.83: winner=0 t=14.2min eras=1/1 kills=262/214
stage 6 era 1-1 power 0.89: TIMEOUT t=30.0min eras=1/1 kills=546/500
stage 7 era 1-2 power 0.95: winner=0 t=9.5min eras=2/2 kills=160/135
stage 8 era 0-2 power 1.01: winner=0 t=11.1min eras=2/2 kills=189/145
stage 9 era 2-2 power 0.94: winner=1 t=4.2min eras=2/2 kills=45/74
stage 10 era 2-2 power 1.00: winner=1 t=4.5min eras=2/2 kills=38/71
stage 11 era 2-3 power 1.06: winner=0 t=8.4min eras=3/3 kills=190/135
stage 12 era 0-3 power 1.12: winner=0 t=7.6min eras=3/3 kills=168/147
stage 13 era 3-3 power 1.06: winner=1 t=6.4min eras=3/3 kills=100/128
stage 14 era 3-3 power 1.11: winner=1 t=7.5min eras=3/3 kills=103/138
stage 15 era 3-4 power 1.17: winner=0 t=6.3min eras=4/4 kills=109/84
stage 16 era 1-4 power 1.23: winner=0 t=11.9min eras=4/4 kills=215/177
stage 17 era 4-4 power 1.17: winner=0 t=8.5min eras=4/4 kills=171/103
stage 18 era 4-4 power 1.23: TIMEOUT t=30.0min eras=4/4 kills=600/553
stage 19 era 4-5 power 1.28: winner=0 t=14.8min eras=5/5 kills=342/258
stage 20 era 2-5 power 1.34: winner=0 t=8.5min eras=4/4 kills=182/109
stage 21 era 5-5 power 1.28: winner=1 t=6.0min eras=5/5 kills=85/110
stage 22 era 5-5 power 1.34: TIMEOUT t=30.0min eras=5/5 kills=555/455
stage 23 era 5-6 power 1.40: winner=1 t=8.9min eras=6/6 kills=135/170
stage 24 era 3-6 power 1.45: winner=0 t=8.2min eras=5/5 kills=178/134
stage 25 era 6-6 power 1.39: TIMEOUT t=30.0min eras=6/6 kills=649/553
stage 26 era 6-6 power 1.45: winner=1 t=14.5min eras=6/6 kills=316/323
stage 27 era 6-7 power 1.51: winner=1 t=5.8min eras=6/7 kills=55/90
stage 28 era 4-7 power 1.57: winner=0 t=5.7min eras=5/5 kills=111/67
stage 29 era 7-7 power 1.50: winner=0 t=7.9min eras=7/7 kills=158/102
stage 30 era 7-7 power 1.56: winner=0 t=13.6min eras=7/7 kills=315/211
stage 31 era 7-7 power 1.62: winner=1 t=6.3min eras=7/7 kills=108/149
stage 32 era 5-7 power 1.68: TIMEOUT t=30.0min eras=7/7 kills=644/592
endless over=true wave=16 t=6.7385345 score=4682 era=2
skirmish over=true winner=0 t=10.61231 eras=3/3 kills=181/137
```

### Oryginalny stdout: system kontr

```text
stage 1 era 0-0 power 0.72: winner=0 t=14.3min eras=0/0 kills=234/184
stage 2 era 0-0 power 0.78: winner=0 t=23.1min eras=0/0 kills=373/294
stage 3 era 0-1 power 0.84: winner=0 t=12.7min eras=1/1 kills=211/196
stage 4 era 0-1 power 0.89: TIMEOUT t=30.0min eras=1/1 kills=528/466
stage 5 era 1-1 power 0.83: winner=0 t=16.0min eras=1/1 kills=292/241
stage 6 era 1-1 power 0.89: TIMEOUT t=30.0min eras=1/1 kills=580/487
stage 7 era 1-2 power 0.95: winner=1 t=9.1min eras=2/2 kills=134/194
stage 8 era 0-2 power 1.01: winner=0 t=11.1min eras=2/2 kills=196/173
stage 9 era 2-2 power 0.94: winner=1 t=6.1min eras=2/2 kills=96/94
stage 10 era 2-2 power 1.00: winner=0 t=12.0min eras=2/2 kills=217/187
stage 11 era 2-3 power 1.06: winner=1 t=9.2min eras=3/3 kills=170/231
stage 12 era 0-3 power 1.12: winner=1 t=7.4min eras=3/3 kills=140/161
stage 13 era 3-3 power 1.06: winner=0 t=17.6min eras=3/3 kills=406/327
stage 14 era 3-3 power 1.11: TIMEOUT t=30.0min eras=3/3 kills=474/534
stage 15 era 3-4 power 1.17: winner=0 t=7.4min eras=4/4 kills=148/96
stage 16 era 1-4 power 1.23: winner=0 t=8.9min eras=4/4 kills=179/123
stage 17 era 4-4 power 1.17: winner=1 t=10.6min eras=4/4 kills=159/237
stage 18 era 4-4 power 1.23: winner=1 t=6.6min eras=4/4 kills=114/154
stage 19 era 4-5 power 1.28: winner=0 t=5.6min eras=5/5 kills=130/108
stage 20 era 2-5 power 1.34: winner=1 t=13.8min eras=5/5 kills=260/256
stage 21 era 5-5 power 1.28: winner=1 t=9.6min eras=5/5 kills=173/203
stage 22 era 5-5 power 1.34: winner=1 t=6.1min eras=5/5 kills=86/116
stage 23 era 5-6 power 1.40: winner=0 t=7.8min eras=6/6 kills=182/119
stage 24 era 3-6 power 1.45: winner=0 t=8.2min eras=5/5 kills=194/133
stage 25 era 6-6 power 1.39: winner=1 t=8.3min eras=6/6 kills=177/163
stage 26 era 6-6 power 1.45: winner=1 t=19.1min eras=6/6 kills=417/394
stage 27 era 6-7 power 1.51: winner=0 t=7.8min eras=7/7 kills=157/108
stage 28 era 4-7 power 1.57: winner=0 t=9.7min eras=6/6 kills=199/127
stage 29 era 7-7 power 1.50: winner=0 t=7.3min eras=7/7 kills=174/108
stage 30 era 7-7 power 1.56: winner=1 t=8.4min eras=7/7 kills=140/195
stage 31 era 7-7 power 1.62: winner=1 t=3.9min eras=7/7 kills=70/84
stage 32 era 5-7 power 1.68: winner=0 t=9.0min eras=7/7 kills=219/200
endless over=true wave=30 t=12.07612 score=8822 era=3
skirmish over=true winner=1 t=9.372891 eras=3/3 kills=142/170
```

### Końcówki logów Gradle

```text
# GitHub Actions, Android SDK 36, JDK 17
> Task :app:assembleDebug
BUILD SUCCESSFUL in 1m 26s

> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 17s

# Kotlin JVM, przed zmianą:
BUILD SUCCESSFUL in 54s

# Kotlin JVM, po zmianie:
BUILD SUCCESSFUL in 23s
```

Nie uruchamiano wolnego `ScreenshotTest`, bo zlecenie nie dotyczy grafiki.

