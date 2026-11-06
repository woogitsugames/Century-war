package com.centurywar.game

import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Dzień 62: pomiar Rozkazów sztabu (AI gracza kontra AI wroga, te same ograniczenia).
 * Uruchamianie: `BALANCE_RUN=true ./gradlew :sim:test --tests '*OrdersBalanceReport*'`.
 * Opcjonalnie `ORDERS_RUNS` (domyślnie 40) i `ORDERS_POWER=0:1.2,3:1.1` (strojenie aiPower).
 * Cele (R14): 1★ w 50–80% (przeciętny gracz), 3★ w 10–35%, mediana 2–5 min, 0 patów.
 */
class OrdersBalanceReport {
    @Before fun onlyOnRequest() = assumeTrue(System.getenv("BALANCE_RUN") == "true")

    class Row(val order: Orders.Order, val runs: Int, val stars: IntArray, val medianMinutes: Double,
              val bonusHits: IntArray, val timeouts: Int,
              val enemyDamagePct: Double = 0.0, val playerHpPct: Double = 0.0) {
        fun pct(n: Int) = n * 100.0 / runs
        val atLeast1 get() = runs - stars[0]
        val three get() = stars[3]
    }

    companion object {
        private fun maskEnv(name: String): Map<Int, Int> = System.getenv(name)?.split(',')?.mapNotNull {
            val p = it.split(':'); if (p.size == 2) p[0].trim().toInt() - 1 to p[1].trim().toInt() else null
        }?.toMap() ?: emptyMap()
        /** Strojenie: `ORDERS_ENEMY=2:7` (rozkaz 2, maska wroga 7), `ORDERS_PLAYER=...`. */
        private val enemyOverride = maskEnv("ORDERS_ENEMY")
        private val playerOverride = maskEnv("ORDERS_PLAYER")

        fun seedFor(order: Int, trial: Int): Long = 620_062L + order * 10_007L + trial * 997L

        fun play(order: Orders.Order, seed: Long, power: Float = order.aiPower): Battle {
            val base = Orders.config(order, seed)
            val enemyMask = enemyOverride[order.index] ?: base.enemyAllowed
            val playerMask = playerOverride[order.index] ?: base.playerAllowed
            val cfg = if (power == order.aiPower && enemyMask == base.enemyAllowed &&
                playerMask == base.playerAllowed) base else BattleConfig(
                base.mode, base.seed, power, base.startEra, base.maxEra, base.modifiers,
                base.playerBonuses, base.stageIndex, base.tutorial, base.aiPersonality,
                base.variantsEnabled, base.weather, base.weekly, base.weeklyCard, base.dailyEvent,
                playerMask, enemyMask, base.timeLimitSteps, base.holdWins,
            )
            val b = Battle(cfg)
            b.playerAi = Ai(b, 0)
            var guard = 0
            while (!b.over && guard < Replay.MAX_STEPS) { b.step(); b.fx.count = 0; guard++ }
            return b
        }

        fun measure(order: Orders.Order, runs: Int, power: Float = order.aiPower): Row {
            val stars = IntArray(4)
            val bonus = IntArray(order.bonus.size)
            val minutes = ArrayList<Double>(runs)
            var timeouts = 0
            var enemyDmg = 0.0
            var playerHp = 0.0
            for (t in 0 until runs) {
                val b = play(order, seedFor(order.index, t), power)
                if (!b.over) timeouts++
                stars[Orders.stars(order, b)]++
                for ((i, o) in order.bonus.withIndex()) if (Orders.objectiveDone(o, b)) bonus[i]++
                minutes.add(b.time / 60.0)
                enemyDmg += (b.enemy.baseDamageTaken / b.enemy.baseMaxHp * 100f).coerceAtMost(100f)
                playerHp += (b.player.baseHp / b.player.baseMaxHp * 100f).coerceAtLeast(0f)
            }
            minutes.sort()
            val median = if (runs % 2 == 1) minutes[runs / 2] else (minutes[runs / 2 - 1] + minutes[runs / 2]) / 2
            return Row(order, runs, stars, median, bonus, timeouts, enemyDmg / runs, playerHp / runs)
        }
    }

    /** Strojenie: `ORDERS_SWEEP=0.6,0.8,1.0` i opcjonalnie `ORDERS_ONLY=1,2`. */
    @Test fun ordersSweep() {
        val raw = System.getenv("ORDERS_SWEEP") ?: return
        // „def”: aiPower z definicji rozkazu.
        val sweep = if (raw == "def") listOf(Float.NaN) else raw.split(',').map { it.trim().toFloat() }
        val only = System.getenv("ORDERS_ONLY")?.split(',')?.map { it.trim().toInt() - 1 }?.toSet()
        val runs = System.getenv("ORDERS_RUNS")?.toIntOrNull() ?: 20
        for (order in Orders.all) {
            if (only != null && order.index !in only) continue
            for (p in sweep) {
                val power = if (p.isNaN()) order.aiPower else p
                val r = measure(order, runs, power)
                println("SWEEP-W ${order.index + 1} power=$power " + winnerStats(order, runs, power))
                println(String.format(Locale.US,
                    "SWEEP %d %s power=%.2f >=1*=%.0f%% 2*+=%.0f%% 3*=%.0f%% b1=%.0f%% b2=%.0f%% med=%.2f dmgE=%.0f%% hpP=%.0f%%",
                    order.index + 1, order.key, power, r.pct(r.atLeast1), r.pct(r.stars[2] + r.stars[3]),
                    r.pct(r.three), r.pct(r.bonusHits[0]), r.pct(r.bonusHits[1]), r.medianMinutes,
                    r.enemyDamagePct, r.playerHpPct))
            }
        }
    }

    /** Kwartyle (25/50/75%) wśród wygranych: czas s, straty, zabici, HP bazy %, obrażenia bazy wroga %. */
    private fun winnerStats(order: Orders.Order, runs: Int, power: Float): String {
        val t = ArrayList<Int>(); val l = ArrayList<Int>(); val k = ArrayList<Int>()
        val hp = ArrayList<Int>(); val dmg = ArrayList<Int>()
        for (i in 0 until runs) {
            val b = play(order, seedFor(order.index, i), power)
            if (!Orders.mainDone(b)) continue
            t.add(b.time.toInt()); l.add(b.enemy.kills); k.add(b.player.kills)
            hp.add((b.player.baseHp / b.player.baseMaxHp * 100).toInt())
            dmg.add((b.enemy.baseDamageTaken / b.enemy.baseMaxHp * 100).toInt())
        }
        fun q(a: ArrayList<Int>): String {
            if (a.isEmpty()) return "-"
            a.sort(); return "${a[a.size / 4]}/${a[a.size / 2]}/${a[a.size * 3 / 4]}"
        }
        return "n=${t.size} t=${q(t)} loss=${q(l)} kills=${q(k)} hp=${q(hp)} dmgE=${q(dmg)}"
    }

    @Test fun ordersReport() {
        if (System.getenv("ORDERS_SWEEP") != null) return
        val runs = System.getenv("ORDERS_RUNS")?.toIntOrNull() ?: 40
        val overrides = System.getenv("ORDERS_POWER")?.split(',')?.mapNotNull {
            val p = it.split(':'); if (p.size == 2) p[0].trim().toInt() to p[1].trim().toFloat() else null
        }?.toMap() ?: emptyMap()
        val md = StringBuilder()
        md.append("| Rozkaz | Epoka | Cel | aiPower | N | ≥1★ | 2★+ | 3★ | cel dod. 1 | cel dod. 2 | mediana min | paty |\n")
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        for (order in Orders.all) {
            val power = overrides[order.index] ?: order.aiPower
            val r = measure(order, runs, power)
            val line = String.format(Locale.US,
                "| %d %s | %d | %s %d s | %.2f | %d | %.0f%% | %.0f%% | %.0f%% | %.0f%% | %.0f%% | %.2f | %d |",
                order.index + 1, order.key, order.era, order.goal, order.limitSeconds, power, runs,
                r.pct(r.atLeast1), r.pct(r.stars[2] + r.stars[3]), r.pct(r.three),
                r.pct(r.bonusHits[0]), r.pct(r.bonusHits[1]), r.medianMinutes, r.timeouts)
            println("ORDERS $line")
            md.append(line).append('\n')
        }
        File("build/reports").mkdirs()
        File("build/reports/orders-balance.md").writeText(md.toString())
    }
}
