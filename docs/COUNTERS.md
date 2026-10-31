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

Po zmianie mechaniki należy też porównać wydruki testu `BattleSimTest.campaignStagesFinish` z gałęzią bazową. Wyników symulacji nie należy uznawać za potwierdzone bez wykonania testów na rzeczywistym środowisku JVM/Android Gradle Plugin.
