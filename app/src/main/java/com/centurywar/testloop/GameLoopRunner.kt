package com.centurywar.testloop

import com.centurywar.engine.Game
import com.centurywar.game.Ai
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.CampaignScreen
import com.centurywar.screens.CodexScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.ResearchScreen
import com.centurywar.screens.SettingsScreen

/**
 * R11-04: czasy scenariuszy pętli gry. [scale] skraca wszystkie fazy (testy), [limitS] to twardy
 * limit całego scenariusza (po nim wynik „timeout”).
 */
internal class GameLoopTiming(val scale: Float = 1f, val limitS: Float = LIMIT_S) {
    fun ns(seconds: Float): Long = (seconds * scale * 1_000_000_000.0).toLong()

    companion object {
        const val MENU_S = 20f
        const val SCREEN_S = 5f
        const val BACK_S = 2f
        const val BATTLE_S = 180f
        const val RESULTS_S = 5f
        const val RESUME_BATTLE_S = 30f
        const val SNAPSHOT_WAIT_S = 10f
        /** Każdy scenariusz kończy się sam najpóźniej po 4 minutach. */
        const val LIMIT_S = 240f
        /** Stałe ziarna: na każdym telefonie ta sama bitwa. */
        const val SEED_CAMPAIGN = 8_008L
        const val SEED_ENDLESS = 3_003L
        const val SEED_RESUME = 4_004L
        /** Etap 8 kampanii (indeks 7): środek gry, wódz dostępny. */
        const val CAMPAIGN_STAGE = 7
        /** Endless od 4. epoki: tak wygląda fala 20–30 zwykłej gry (epoka co 6 fal). */
        const val ENDLESS_START_ERA = 3
    }
}

/** R11-04: wynik scenariusza (dane do JSON, bez danych osobowych). */
internal class GameLoopReport(val scenario: Int) {
    var result = "ok"
    var reason = ""
    var qualityStart = 0
    var qualityMin = 0
    var reductionMax = 0
    var frames = FrameStats()
    val screens = LinkedHashMap<String, FrameStats>()
    var discardedSteps = 0L
    var pssStartKb = 0L
    var pssEndKb = 0L
    var startupMs = -1L
    var battleTimeS = -1f
    var winner = -2
    var wave = -1
    var snapshotSaved: Boolean? = null
    var snapshotLoaded: Boolean? = null
    var snapshotLoadMs = -1L
    var resumedAtS = -1f
    var elapsedS = 0f
}

/**
 * R11-04: scenariusz pętli gry wykonywany na wątku gry. Uruchamiany przez [start]; potem sam
 * dokłada się do kolejki [Game.post], więc działa raz na klatkę (początek klatki) i mierzy
 * rzeczywiste odstępy między klatkami. Nie dotyka plików ani sieci: zrzut bitwy idzie do
 * [Game.snapshots] (w pętli: katalog pętli), wynik oddaje przez [onDone] (zapis robi Activity).
 */
internal class GameLoopRunner(
    private val game: Game,
    val scenario: Int,
    private val timing: GameLoopTiming = GameLoopTiming(),
    private val clock: () -> Long = System::nanoTime,
    private val pssKb: () -> Long = { 0L },
    private val startupMs: () -> Long = { -1L },
    private val io: (Runnable) -> Unit = { r ->
        Thread(r, "testloop-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    },
    private val onDone: (GameLoopReport) -> Unit,
) : Runnable {

    private class Phase(
        val label: String?,
        val seconds: Float,
        val enter: () -> Unit,
        val done: () -> Boolean = { false },
    )

    val report = GameLoopReport(scenario)
    @Volatile var finished = false
        private set
    private var phases: List<Phase> = emptyList()
    private var phaseIndex = -1
    private var phaseStart = 0L
    private var startNs = 0L
    private var lastNs = 0L
    private var battle: BattleScreen? = null
    private var discardedAtStart = 0L
    @Volatile private var restored: RestoredBattle? = null
    @Volatile private var loadDone = false

    /** Wątek gry (albo przed startem pętli klatek). */
    fun start() {
        phases = when (scenario) {
            1 -> menuScenario()
            2 -> battleScenario {
                Campaign.config(Campaign.stages[GameLoopTiming.CAMPAIGN_STAGE], Bonuses(),
                    GameLoopTiming.SEED_CAMPAIGN)
            }
            3 -> battleScenario {
                BattleConfig(Mode.ENDLESS, GameLoopTiming.SEED_ENDLESS, 1f,
                    startEra = GameLoopTiming.ENDLESS_START_ERA)
            }
            4 -> resumeScenario()
            else -> emptyList()
        }
        if (phases.isEmpty()) {
            fail("unknown_scenario")
            return
        }
        game.post(this)
    }

    override fun run() {
        if (finished) return
        try {
            tick()
        } catch (e: Exception) {
            fail("exception:" + e.javaClass.simpleName)
        }
        if (!finished) game.post(this)
    }

    private fun tick() {
        val now = clock()
        if (startNs == 0L) {
            startNs = now
            lastNs = now
            report.pssStartKb = pssKb()
            report.qualityStart = game.quality.level
            report.qualityMin = game.quality.level
            report.startupMs = if (scenario == 1) startupMs() else -1L
            nextPhase(now)
            return
        }
        val interval = now - lastNs
        lastNs = now
        val phase = phases[phaseIndex]
        if (phase.label != null) {
            report.frames.add(interval)
            if (scenario == 1) report.screens[phase.label]?.add(interval)
        }
        val q = game.quality
        if (q.level < report.qualityMin) report.qualityMin = q.level
        if (q.reduction > report.reductionMax) report.reductionMax = q.reduction
        driveBattle()
        when {
            now - startNs >= timing.limitNs() -> finish("timeout", now)
            phase.done() || now - phaseStart >= timing.ns(phase.seconds) -> nextPhase(now)
        }
    }

    private fun GameLoopTiming.limitNs(): Long = (limitS * 1_000_000_000.0).toLong()

    private fun nextPhase(now: Long) {
        phaseIndex++
        if (phaseIndex >= phases.size) {
            finish(if (report.reason.isEmpty()) "ok" else "error", now)
            return
        }
        phaseStart = now
        phases[phaseIndex].enter()
    }

    private fun fail(reason: String) {
        report.reason = reason
        finish("error", clock())
    }

    private fun finish(result: String, now: Long) {
        if (finished) return
        finished = true
        report.result = result
        report.elapsedS = if (startNs == 0L) 0f else (now - startNs) / 1_000_000_000f
        battle?.let { bs ->
            report.battleTimeS = bs.battle.time
            report.winner = bs.battle.winner
            if (bs.battle.cfg.mode == Mode.ENDLESS) report.wave = bs.battle.wave
            report.discardedSteps = game.frameMetrics.totalDroppedSteps - discardedAtStart
        }
        report.pssEndKb = pssKb()
        onDone(report)
    }

    // ------------------------------------------------------------ scenariusz 1: start i menu

    private fun menuScenario(): List<Phase> {
        for (label in arrayOf("menu", "campaign", "research", "codex", "settings"))
            report.screens[label] = FrameStats()
        val list = ArrayList<Phase>()
        list.add(Phase("menu", GameLoopTiming.MENU_S, {
            if (game.current !is MenuScreen) game.setScreen(MenuScreen(game))
        }))
        fun visit(label: String, open: () -> Unit) {
            list.add(Phase(label, GameLoopTiming.SCREEN_S, open))
            list.add(Phase("menu", GameLoopTiming.BACK_S, { game.pop() }))
        }
        visit("campaign") { game.push(CampaignScreen(game)) }
        visit("research") { game.push(ResearchScreen(game)) }
        visit("codex") { game.push(CodexScreen(game)) }
        visit("settings") { game.push(SettingsScreen(game)) }
        return list
    }

    // ------------------------------------------------------------ scenariusze 2 i 3: bitwa AI

    private fun battleScenario(config: () -> BattleConfig): List<Phase> = listOf(
        Phase("battle", GameLoopTiming.BATTLE_S, { startBattle(BattleScreen(game, config()), 3) },
            done = { battle?.finished == true }),
        Phase(null, GameLoopTiming.RESULTS_S, {}),
    )

    private fun startBattle(bs: BattleScreen, speed: Int) {
        game.setScreen(bs)
        bs.battle.playerAi = Ai(bs.battle, 0)
        bs.paused = false
        bs.speed = speed
        if (battle == null) discardedAtStart = game.frameMetrics.totalDroppedSteps
        battle = bs
    }

    /** Wybory po awansie i co piątą falę: ta sama decyzja AI, jaką podjąłby BattleController. */
    private fun driveBattle() {
        val bs = battle ?: return
        if (game.current !== bs || bs.finished) return
        val b = bs.battle
        val ai = b.playerAi ?: return
        b.pendingVariant?.let { bs.commands.variant(ai.chooseVariant(it)) }
        b.pendingChoice?.let { options -> bs.commands.doctrine(options.indexOf(ai.chooseDoctrine(options))) }
        b.pendingCards?.let { options -> bs.commands.card(ai.chooseCard(options)) }
        // Pauza z cyklu życia (np. rozwinięty pasek powiadomień) nie zatrzymuje pomiaru.
        if (bs.paused && phases.getOrNull(phaseIndex)?.label != null) bs.paused = false
    }

    // ------------------------------------------------------------ scenariusz 4: przerwanie i wznowienie

    private fun resumeScenario(): List<Phase> = listOf(
        Phase("battle", GameLoopTiming.RESUME_BATTLE_S, {
            startBattle(BattleScreen(game, Campaign.config(
                Campaign.stages[GameLoopTiming.CAMPAIGN_STAGE], Bonuses(), GameLoopTiming.SEED_RESUME)), 1)
        }, done = { battle?.finished == true }),
        Phase(null, GameLoopTiming.SNAPSHOT_WAIT_S, { pauseAndReload() }, done = { loadDone }),
        Phase("battle", GameLoopTiming.RESUME_BATTLE_S, { resumeFromSnapshot() },
            done = { restored == null || battle?.finished == true }),
    )

    /** Ta sama ścieżka co MainActivity.onPause: zrzut z ekranu bitwy, profil, potem odczyt w tle. */
    private fun pauseAndReload() {
        val bs = battle
        val store = game.snapshots
        if (bs == null || store == null || bs.finished) {
            report.snapshotSaved = false
            loadDone = true
            return
        }
        report.resumedAtS = bs.battle.time
        bs.onPause()
        game.profile.save()
        val settled = game.profile.lastSettledBattle
        io(Runnable {
            val saved = store.awaitWrites(5_000L)
            val begin = clock()
            val loaded = try { store.loadBattle(settled) } catch (_: Exception) { null }
            val ms = (clock() - begin) / 1_000_000L
            game.post {
                report.snapshotSaved = saved
                report.snapshotLoaded = loaded != null
                report.snapshotLoadMs = ms
                restored = loaded
                loadDone = true
            }
        })
    }

    private fun resumeFromSnapshot() {
        val offer = restored
        if (offer == null) {
            if (report.reason.isEmpty()) report.reason = "snapshot_not_loaded"
            return
        }
        startBattle(BattleScreen(game, offer.battle.cfg, offer.rankedDaily, offer), 1)
    }
}
