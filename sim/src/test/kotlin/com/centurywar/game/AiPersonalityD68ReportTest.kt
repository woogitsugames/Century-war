package com.centurywar.game

import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Dzień 68 (R170, „Wspólne zasady”): pomiar nowych stylów przeciwnika na parowanych seedach.
 * Każdy styl gra te same seedy w scenariuszach 0→3 i 2→2 przeciw graczowi BALANCED; wynik,
 * przedział Wilsona 95%, sygnatury stylów z R170 (do 60 i 90 s osobno) i odróżnienie od
 * najbliższego istniejącego stylu. Długi pomiar, tylko na żądanie:
 * AI_D68_RUN=true [AI_D68_SEEDS=100] ./gradlew :sim:test --tests '*AiPersonalityD68ReportTest*'
 * Wynik: sim/build/ai-d68-report.md (i .tsv).
 */
class AiPersonalityD68ReportTest {
    private companion object {
        const val LIMIT_SECONDS = 1800f
        const val SAMPLE_STEPS = 300 // 5 s przy 60 Hz
        const val CONTACT = 260f
    }

    private class Stats(val p: AiPersonality, val start: Int) {
        var games = 0; var enemyWins = 0; var playerWins = 0; var draws = 0
        val roles60 = LongArray(Role.entries.size)
        val roles90 = LongArray(Role.entries.size)
        val rolesAll = LongArray(Role.entries.size)
        // Osłona i strzała
        var pairSamples90 = 0; var samples90 = 0; var firstPairSum = 0.0; var firstPairGames = 0
        // Krótkie uderzenia
        var releases90 = 0; var releasesAll = 0; var releaseUnits = 0.0
        var releaseGapSum = 0.0; var releaseGaps = 0; var secondReleaseSum = 0.0; var secondReleaseGames = 0
        // Zryw w kontakcie
        var contact90 = 0; var ralliesAll = 0; var ralliesContact = 0; var ralliesMarch = 0
        var rallyDelaySum = 0.0; var rallyDelays = 0; var rallies90 = 0
        // Wspólne (jak AiPersonalityProfileTest)
        var turrets = 0.0; var barrages = 0.0; var alive = 0.0; var gold = 0.0
        var holdSamples = 0; var samples = 0; var duration = 0.0; var upgrades = 0.0

        fun run(seed: Long) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f,
                startEra = start, maxEra = if (start == 0) 3 else 2,
                aiPersonality = p), commanderEnabled = false)
            b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
            BattleController(b)
            val e = b.enemy
            var steps = 0
            var wasHolding = e.hold
            var lastRelease = -1f
            var releases = 0
            var firstPair = -1f
            var firstContact = -1f
            var rallies = e.ralliesUsed
            val limit = (LIMIT_SECONDS / Battle.STEP).toInt()
            while (!b.over && steps < limit) {
                b.step(); b.fx.count = 0; steps++
                if (wasHolding && !e.hold) {
                    releases++; releasesAll++; releaseUnits += gathered(b)
                    if (b.time <= 90f) releases90++
                    if (lastRelease >= 0f) { releaseGapSum += b.time - lastRelease; releaseGaps++ }
                    if (releases == 2) { secondReleaseSum += b.time; secondReleaseGames++ }
                    lastRelease = b.time
                }
                wasHolding = e.hold
                val inContact = contact(b)
                if (inContact && firstContact < 0f) {
                    firstContact = b.time
                    if (b.time <= 90f) contact90++
                }
                if (e.ralliesUsed > rallies) {
                    rallies = e.ralliesUsed
                    ralliesAll++
                    if (b.time <= 90f) rallies90++
                    if (inContact) ralliesContact++ else ralliesMarch++
                    if (firstContact >= 0f && rallyDelays < games + 1 && rallyDelaySum >= 0.0 &&
                        ralliesAll == ralliesContact + ralliesMarch) {
                        // Opóźnienie liczymy raz na bitwę: pierwszy Zryw po pierwszym kontakcie.
                    }
                }
                if (firstContact >= 0f && rallyMarkGame != games && e.ralliesUsed > 0 &&
                    rallies == e.ralliesUsed && lastRallyTimeCheck(b, firstContact)) {
                    rallyMarkGame = games
                }
                if (b.time.toInt() == 60 && steps % 60 == 0) copy(e, roles60)
                if (b.time.toInt() == 90 && steps % 60 == 0) copy(e, roles90)
                if (steps % SAMPLE_STEPS == 0) {
                    samples++
                    alive += e.aliveCount; gold += e.gold
                    if (e.hold) holdSamples++
                    if (b.time <= 90f) {
                        samples90++
                        if (pair(b)) {
                            pairSamples90++
                            if (firstPair < 0f) firstPair = b.time
                        }
                    }
                }
            }
            if (b.time < 60f) copy(e, roles60)
            if (b.time < 90f) copy(e, roles90)
            for (r in rolesAll.indices) rolesAll[r] += e.spawnedByRole[r].toLong()
            games++
            when {
                !b.over -> draws++
                b.winner == 1 -> enemyWins++
                else -> playerWins++
            }
            if (firstPair >= 0f) { firstPairSum += firstPair; firstPairGames++ }
            turrets += e.turretsBuilt; barrages += e.barragesUsed
            for (tier in e.unitTier) upgrades += tier - 1
            duration += b.time
        }

        private var rallyMarkGame = -1
        private var firstRallyAfterContact = -1f

        /** Pierwszy Zryw po pierwszym kontakcie: opóźnienie w sekundach (raz na bitwę). */
        private fun lastRallyTimeCheck(b: Battle, firstContact: Float): Boolean {
            if (firstRallyAfterContact >= 0f) return false
            firstRallyAfterContact = b.time
            rallyDelaySum += (b.time - firstContact).coerceAtLeast(0f)
            rallyDelays++
            firstRallyAfterContact = -1f
            return true
        }

        /** Jednostki przy bazie (przed linią postoju z marginesem), czyli wychodząca grupa. */
        private fun gathered(b: Battle): Int {
            val e = b.enemy
            val line = e.baseFront + e.dir * (Battle.HOLD_DIST + 60f)
            var n = 0
            for (u in b.units) if (u.alive && u.side == 1 && (u.x - line) * e.dir <= 0f) n++
            return n
        }

        private fun copy(e: Side, into: LongArray) {
            for (r in into.indices) into[r] += e.spawnedByRole[r].toLong()
        }

        /** Wróg gracza (strona 0) w zasięgu kontaktu frontu przeciwnika (prawda symulacji). */
        private fun contact(b: Battle): Boolean {
            val e = b.enemy
            if (e.aliveCount == 0) return false
            for (u in b.units) if (u.alive && u.side == 0 && abs(u.x - e.front) < CONTACT) return true
            return false
        }

        /** Para: żywa wręcz przed żywym strzelcem tej samej strony. */
        private fun pair(b: Battle): Boolean {
            val e = b.enemy
            var meleeAhead = Float.NEGATIVE_INFINITY
            var rangedAhead = Float.NEGATIVE_INFINITY
            for (u in b.units) {
                if (!u.alive || u.side != 1) continue
                val d = (u.x - e.baseFront) * e.dir
                when (u.def.role) {
                    Role.MELEE -> meleeAhead = maxOf(meleeAhead, d)
                    Role.RANGED -> rangedAhead = maxOf(rangedAhead, d)
                    else -> {}
                }
            }
            return meleeAhead > Float.NEGATIVE_INFINITY && rangedAhead > Float.NEGATIVE_INFINITY &&
                meleeAhead > rangedAhead
        }

        fun share(roles: LongArray, role: Role): Double {
            val total = roles.sum()
            return if (total == 0L) 0.0 else roles[role.ordinal].toDouble() / total
        }

        /** Odsetek wygranych gracza BALANCED (remis = pół). */
        val playerRate: Double get() = (playerWins + 0.5 * draws) / games

        /** Miary do testu odróżnienia (te same co w AiPersonalityProfileTest, bez epok). */
        fun profile(): DoubleArray = doubleArrayOf(
            share(rolesAll, Role.MELEE), share(rolesAll, Role.RANGED), share(rolesAll, Role.HEAVY),
            share(rolesAll, Role.ELITE), turrets / games, barrages / games, ralliesAll.toDouble() / games,
            if (samples == 0) 0.0 else alive / samples, if (samples == 0) 0.0 else gold / samples,
            if (samples == 0) 0.0 else holdSamples.toDouble() / samples, upgrades / games,
            if (releasesAll == 0) 0.0 else releaseUnits / releasesAll,
            share(roles90, Role.RANGED), releases90.toDouble() / games,
            if (ralliesAll == 0) 0.0 else ralliesMarch.toDouble() / ralliesAll,
        )
    }

    private fun wilson(successes: Double, n: Int): Pair<Double, Double> {
        if (n == 0) return 0.0 to 0.0
        val z = 1.96
        val p = successes / n
        val denom = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / denom
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / denom
        return (centre - half) to (centre + half)
    }

    private fun f(v: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)

    @Test
    fun report() {
        assumeTrue(System.getenv("AI_D68_RUN") == "true")
        val seeds = (System.getenv("AI_D68_SEEDS")?.toIntOrNull() ?: 100).coerceAtLeast(20)
        // AI_D68_ONLY="RAIDS,BALANCED": tylko wybrane style (szybkie strojenie).
        val only = System.getenv("AI_D68_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val all = AiPersonality.entries.filter { only == null || it.name in only }
        val results = Array(2) { sc -> Array(all.size) { Stats(all[it], if (sc == 0) 0 else 2) } }
        for (sc in 0..1) for (s in results[sc]) {
            val start = if (sc == 0) 0 else 2
            for (i in 0 until seeds) s.run(30001L + 7919L * i + start)
            println("D68 ${s.start} ${s.p} player=${f(100 * s.playerRate)}%")
        }
        val md = StringBuilder("# D68: pomiar stylów przeciwnika\n\n")
            .append("Gracz: BALANCED, przeciwnik: styl z wiersza, potyczka bez dowódcy, ")
            .append("$seeds tych samych seedów na scenariusz, limit ${LIMIT_SECONDS.toInt()} s ")
            .append("(remis = pół wygranej). Przedział: Wilson 95%.\n\n")
        md.append("## Wyniki gracza BALANCED\n\n| Styl | 0→3 wygrane gracza | 95% | 2→2 wygrane gracza | 95% | Razem | Remisy |\n")
            .append("|---|---:|---:|---:|---:|---:|---:|\n")
        val tsv = StringBuilder("styl\tscen\tgracz_pct\tremisy\tzasieg_60\tzasieg_90\tpara_90\tpierwsza_para_s\t" +
            "zwolnienia_90\tzwolnienia\tgrupa\todstep_s\tdrugie_s\tkontakt_90\tzrywy\tzrywy_kontakt\tzrywy_marsz\topoznienie_zrywu_s\twieze\tulepszenia\tzloto\tzywi\tpostoj\tczas_s\n")
        for (i in all.indices) {
            val a = results[0][i]; val c = results[1][i]
            val ca = wilson(a.playerWins + 0.5 * a.draws, a.games)
            val cc = wilson(c.playerWins + 0.5 * c.draws, c.games)
            val total = (a.playerWins + c.playerWins + 0.5 * (a.draws + c.draws)) / (a.games + c.games)
            md.append("| ${all[i]} | ${f(100 * a.playerRate)}% | ${f(100 * ca.first)}–${f(100 * ca.second)} | " +
                "${f(100 * c.playerRate)}% | ${f(100 * cc.first)}–${f(100 * cc.second)} | ${f(100 * total)}% | ${a.draws + c.draws} |\n")
        }
        md.append("\n## Sygnatury stylów (średnio na bitwę)\n\n")
            .append("| Styl | Scen. | Dystans do 60 s | Dystans do 90 s | Próbki z parą wręcz+dystans (0–90 s) | Pierwsza para [s] | " +
                "Wyjścia grupy do 90 s | Wyjścia | Grupa przy wyjściu | Odstęp wyjść [s] | Drugie wyjście [s] | " +
                "Kontakt do 90 s | Zrywy | Zrywy w kontakcie | Zrywy na marszu | Zryw po 1. kontakcie [s] | " +
                "Wieże | Ulepszenia | Złoto (śr.) | Żywi (śr.) | Postój | Czas [s] |\n")
            .append("|---|---|" + "---:|".repeat(20) + "\n")
        for (sc in 0..1) for (s in results[sc]) {
            val scen = if (sc == 0) "0→3" else "2→2"
            val row = listOf(
                f(100 * s.share(s.roles60, Role.RANGED)) + "%", f(100 * s.share(s.roles90, Role.RANGED)) + "%",
                f(100.0 * s.pairSamples90 / maxOf(1, s.samples90)) + "%",
                if (s.firstPairGames == 0) "–" else f(s.firstPairSum / s.firstPairGames),
                f(s.releases90.toDouble() / s.games, 2), f(s.releasesAll.toDouble() / s.games, 2),
                if (s.releasesAll == 0) "–" else f(s.releaseUnits / s.releasesAll, 2),
                if (s.releaseGaps == 0) "–" else f(s.releaseGapSum / s.releaseGaps),
                if (s.secondReleaseGames == 0) "–" else f(s.secondReleaseSum / s.secondReleaseGames),
                f(100.0 * s.contact90 / s.games) + "%", f(s.ralliesAll.toDouble() / s.games, 2),
                f(s.ralliesContact.toDouble() / s.games, 2), f(s.ralliesMarch.toDouble() / s.games, 2),
                if (s.rallyDelays == 0) "–" else f(s.rallyDelaySum / s.rallyDelays),
                f(s.turrets / s.games, 2), f(s.upgrades / s.games, 2),
                f(if (s.samples == 0) 0.0 else s.gold / s.samples, 0),
                f(if (s.samples == 0) 0.0 else s.alive / s.samples, 2),
                f(100.0 * (if (s.samples == 0) 0.0 else s.holdSamples.toDouble() / s.samples)) + "%",
                f(s.duration / s.games, 0),
            )
            md.append("| ${s.p} | $scen | " + row.joinToString(" | ") + " |\n")
            tsv.append("${s.p}\t$scen\t${f(100 * s.playerRate)}\t${s.draws}\t" + row.joinToString("\t") + "\n")
        }
        md.append("\n## Odróżnienie od najbliższego istniejącego stylu (2→2, próg 15%)\n\n")
            .append("Miary: udziały ról (4), wieże, ostrzały, Zrywy, żywi, złoto, postój, ulepszenia, grupa przy wyjściu, " +
                "dystans do 90 s, wyjścia do 90 s, odsetek Zrywów na marszu.\n\n| Styl | Najbliższy | Miary różne ≥15% |\n|---|---|---:|\n")
        for (i in all.indices) {
            if (all[i] in AiPersonality.LEGACY || all.none { it in AiPersonality.LEGACY }) continue
            var best = ""; var bestDiff = Int.MAX_VALUE
            for (j in all.indices) {
                if (all[j] !in AiPersonality.LEGACY) continue
                val x = results[1][i].profile(); val y = results[1][j].profile()
                var diff = 0
                for (k in x.indices) {
                    val scale = maxOf(abs(x[k]), abs(y[k]))
                    if (scale > 0.0001 && abs(x[k] - y[k]) >= 0.15 * scale) diff++
                }
                if (diff < bestDiff) { bestDiff = diff; best = all[j].name }
            }
            md.append("| ${all[i]} | $best | $bestDiff |\n")
        }
        println(md)
        File("build").mkdirs()
        File("build/ai-d68-report.md").writeText(md.toString())
        File("build/ai-d68-report.tsv").writeText(tsv.toString())
    }
}
