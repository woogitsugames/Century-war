# Integracja powtórek z ekranem bitwy

**Nie zastosowano poniższego diffu:** `screens/BattleScreen.kt` jest plikiem chronionym,
modyfikowanym równolegle. Patch należy nanieść po połączeniu prac.

```diff
diff --git a/app/src/main/java/com/centurywar/screens/BattleScreen.kt b/app/src/main/java/com/centurywar/screens/BattleScreen.kt
--- a/app/src/main/java/com/centurywar/screens/BattleScreen.kt
+++ b/app/src/main/java/com/centurywar/screens/BattleScreen.kt
@@
 import com.centurywar.game.Battle
+import com.centurywar.game.BattleController
 import com.centurywar.game.BattleConfig
@@
     private val battle = Battle(cfg)
+    private val commands = BattleController(battle)
     private val r = BattleRenderer(g, battle)
@@
     override fun onClick(b: Button) {
         val p = battle.player
         when (b.id) {
-            in UNIT0..UNIT0 + 3 -> battle.queueUnit(p, Role.values()[b.id - UNIT0])
+            in UNIT0..UNIT0 + 3 -> commands.queue(Role.values()[b.id - UNIT0])
             TURRET -> showTurrets = !showTurrets
             CLOSE -> showTurrets = false
-            ARMORY -> battle.buyArmory(p)
-            BARRAGE -> battle.castBarrage(p)
-            RALLY -> battle.castRally(p)
-            STANCE -> p.hold = !p.hold
+            ARMORY -> commands.armory()
+            BARRAGE -> commands.barrage()
+            RALLY -> commands.rally()
+            STANCE -> commands.stance(!p.hold)
             SPEED -> speed = when (speed) { 1 -> 3; 3 -> 5; else -> 1 }
-            EVOLVE -> if (battle.evolve(p)) g.haptic(40)
+            EVOLVE -> if (commands.evolve()) g.haptic(40)
             PAUSE -> paused = true
             RESUME -> paused = false
             RESTART, RESTART + 100 -> g.setScreen(BattleScreen(g, cfg.copyWithSeed(), false))
             QUIT -> { paused = false; battle.surrender() }
             CONTINUE -> g.setScreen(nextScreen())
-            in DOCTRINE0..DOCTRINE0 + 2 -> battle.chooseDoctrine(b.id - DOCTRINE0)
+            in DOCTRINE0..DOCTRINE0 + 2 -> commands.doctrine(b.id - DOCTRINE0)
             in SLOT_BUY0 until SLOT_BUY0 + Defs.MAX_TURRET_SLOTS -> {
                 val i = b.id - SLOT_BUY0
-                if (p.turretEra[i] < 0) battle.buyTurret(p, i) else battle.upgradeTurret(p, i)
+                if (p.turretEra[i] < 0) commands.buyTurret(i) else commands.upgradeTurret(i)
             }
-            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> battle.sellTurret(p, b.id - SLOT_SELL0)
+            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> commands.sellTurret(b.id - SLOT_SELL0)
         }
     }
```

Jeżeli później pojawi się przycisk anulowania kolejki, należy wywołać
`commands.cancel()`, nie `battle.cancelLast(p)`.

Po **naturalnym** zakończeniu bitwy, przed wysłaniem wyniku na serwer:

```kotlin
val encodedLog = Replay.encode(commands.log())
val localResult = Replay.verify(cfg, encodedLog) // kosztowne; poza wątkiem renderowania
// Serwer powinien otrzymać log i odtworzyć go ze swoim, zaufanym BattleConfig.
```

## Istotne ograniczenia

- `QUIT` nadal wywołuje `battle.surrender()`; format v1 **nie zawiera** komendy poddania.
  Takie bitwy należy wykluczyć z weryfikowanych rankingów, w tym z rankingu oblężenia.
  Alternatywnie dodać komendę poddania w następnej wersji formatu.
- `BattleController` przechowuje najwyżej 32 768 komend, a `Replay` obsługuje
  maksymalnie 216 000 kroków (60 min). Po osiągnięciu limitu nie należy zgłaszać wyniku
  jako zweryfikowanego.
- Format v1 nie zawiera całego `BattleConfig`. Serwer musi sam ustalić wiarygodny seed,
  modyfikatory, mnożniki, bonusy i zasady trybu; dane klienta nie są autorytatywne.
- Każda modyfikacja balansu lub kolejności wywołań RNG powinna być powiązana
  z wersją silnika symulacji po stronie serwera. Wersja kodowania nie zastępuje wersji silnika.
- Nie serializować i nie wysyłać danych w metodzie `update()` na każdej klatce.
