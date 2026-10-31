# Zapis i odtwarzanie komend Century War

W ekranie bitwy oraz samouczku używany jest jeden `BattleController` na bitwę.
Wszystkie akcje gracza (szkolenie, anulowanie, awans, wieże, zbrojownia,
ostrzał, zryw, postawa, doktryna, poddanie) powinny wywoływać kontroler,
nie metody `Battle` bezpośrednio.

## Model czasu

`Battle.stepCount` liczy zakończone kroki symulacji. Komenda oznaczona
krokiem N zostaje wykonana przed kolejnym `Battle.step()` (krokiem N).
`Battle.time = stepCount * Battle.STEP`, bez dryfu od dodawania Float.
Generator decyzji AI jest odseparowany od losowości samej symulacji.

## Powtórka wyniku

Po zakończeniu bitwy można poza wątkiem renderującym wywołać:

```kotlin
val log = battle.commandController?.log() ?: error("Brak rejestratora")
val encoded = Replay.encode(log)
val result = Replay.verify(cfg, encoded)
```

`Replay.Result` zwraca `score`, `winner`, `steps`, `kills`,
`enemyKills`, `time` i `wave`. Nieakceptowane komendy, uszkodzone
dane oraz log z brakującym zakończeniem bitwy powodują odrzucenie.
Poddanie ma własną komendę, również gdy następuje przed kolejnym krokiem.
Automatyczne dofinansowanie samouczka jest logowane osobnym typem
`TUTORIAL_GOLD` i dozwolone wyłącznie w konfiguracji samouczka.

## Snapshoty

Format `BattleSnapshot` v3 zapisuje licznik kroków, oba strumienie RNG AI
oraz dziennik kontrolera. Po wznowieniu `BattleScreen` używa odtworzonego
kontrolera, a kolejne kliknięcia są dopisywane do tego samego dziennika.
Dawne snapshoty v2 są odrzucane jako niezgodne.

## Bezpieczeństwo i ograniczenia

- Serwer ustala autorytatywny `BattleConfig`: seed, bonusy, AI,
  modyfikatory, wersję zasad i `Defs`. Nie ufa konfiguracji z telefonu.
- Powtórka odtwarza wynik, ale nie uwierzytelnia gracza; wynik i tryb
  muszą być porównane z tym, co deklarował klient.
- Limit formatu: 432 000 kroków (120 minut) i 32 768 komend;
  przekroczenie oznacza wynik nieweryfikowalny.
- Wszelka zmiana balansu lub algorytmów wymaga identyfikacji wersji
  silnika po stronie usługi weryfikującej.
- Samouczek nie jest trybem rankingowym; `TUTORIAL_GOLD` nie może
  zostać zaakceptowane w innej konfiguracji.
