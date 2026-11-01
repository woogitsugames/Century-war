package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Defs
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.ReplayPlayer
import com.centurywar.game.ReplayRecord
import com.centurywar.meta.Achievements
import com.centurywar.meta.Missions

/** Odtwarzacz bez komend gracza; całe sterowanie dotyczy wyłącznie osi czasu i kamery. */
class ReplayScreen(
    g: Game,
    private val replayId: String? = null,
    initial: ReplayRecord.Record? = null,
) : Screen(g) {
    override val crashName get() = "ReplayScreen"
    override val blocksNotices: Boolean get() = true
    constructor(g: Game, bytes: ByteArray) : this(g, null,
        try { ReplayRecord.decode(bytes) } catch (_: Exception) { null })

    constructor(g: Game, id: String?, bytes: ByteArray) : this(g, id,
        try { ReplayRecord.decode(bytes) } catch (_: Exception) { null })

    /** Wykres z właśnie zakończonej bitwy nie wymaga ponownej symulacji. */
    internal constructor(g: Game, id: String?, bytes: ByteArray, samples: BattleSummarySamples) : this(g, id, bytes) {
        battleSummary = samples
        timelineReady = true
        resultChart.prepare(battleSummary, chartArea, dp)
    }

    private var player: ReplayPlayer? = null
    private var renderer: BattleRenderer? = null
    private var renderedBattle: Battle? = null
    private var battleSummary = BattleSummarySamples()
    private val resultChart = BattleSummaryChart()
    private val chartArea = RectF()
    private var showChart = false
    private var timelineReady = false
    private var timelineFailed = false
    private var timelineReplay: ReplayPlayer? = null
    private var chartLoadingText = L["bs104_loading"]
    private var state = LOADING
    private var paused = false
    private var follow = true
    private var speed = 1
    private var acc = 0f
    private var frameDt = 0f
    private var watchedSeconds = 0f
    private var replayAchievementChecked = false
    private val imported = replayId?.startsWith("friend_") == true
    private var draggingTime = false
    private var watchedClaimed = false
    private var preparedPlayerEra = -1
    private var preparedEnemyEra = -1
    private var preparedPlayerVariant = -2
    private var preparedEnemyVariant = -2

    private val back = btn(1)
    private val pause = btn(2)
    private val slower = btn(3)
    private val faster = btn(4)
    private val backward = btn(5)
    private val forward = btn(6)
    private val camera = btn(7)
    private val graph = btn(8)
    private val controls = arrayOf(backward, slower, pause, faster, forward, camera, graph)
    private val progressBar = RectF()
    private val minimap = RectF()
    private val hud = RectF()
    private val footer = RectF()
    private val box = RectF()
    private val markStep = IntArray(Replay.MAX_COMMANDS + 256)
    private val markKind = IntArray(Replay.MAX_COMMANDS + 256)
    private var markCount = 0
    private var timeKey = -1
    private var timeText = ""
    private var statusTimer = 0.25f
    private var statusPlayerEra = -1
    private var statusEnemyEra = -1
    private var statusPlayerGold = Int.MIN_VALUE
    private var statusEnemyGold = Int.MIN_VALUE
    private var statusPlayerHp = Int.MIN_VALUE
    private var statusEnemyHp = Int.MIN_VALUE
    private var statusWave = -1
    private val statusBuffer = StringBuilder(128)
    private var statusText = ""
    private var speedText = "×1"
    private var doctrineState = ""
    private var doctrineHash = -1
    private val cardDefs = EndlessCard.values()
    private var lastProgress = -1
    private var loadingText = ""

    init {
        if (initial != null) install(initial)
        else if (replayId != null && g.replays != null) {
            g.replays?.loadAsync(replayId) { bytes ->
                val record = try { bytes?.let { ReplayRecord.decode(it) } } catch (_: Exception) { null }
                g.post { if (record != null) install(record) else state = BROKEN }
            }
        } else state = BROKEN
    }

    private fun install(record: ReplayRecord.Record) {
        val log = record.log
        if (!record.playable || log == null) {
            state = OLD
            return
        }
        val replay = try { ReplayPlayer(record.cfg, log) } catch (_: Exception) {
            state = BROKEN; return
        }
        if (replay.broken) { state = BROKEN; return }
        player = replay
        // Ten sam motyw co w oryginalnej bitwie (ziarno muzyki jak w BattleScreen).
        g.music?.setThemeSeed((record.cfg.seed xor (record.cfg.seed ushr 32)).toInt())
        for (cmd in log.commands) {
            val type = when (cmd.kind) {
                Replay.Kind.EVOLVE -> 0
                Replay.Kind.DOCTRINE, Replay.Kind.CARD -> 1
                Replay.Kind.BARRAGE, Replay.Kind.BARRAGE_AT -> 2
                else -> -1
            }
            if (type >= 0) addMark(cmd.step, type)
        }
        state = READY
        rebuildRenderer()
        prepareEras(replay.battle)
        if (g.current === this) layout()
    }

    /** Także odtwarzana bitwa może startować w różnych epokach i wybierać wariant. */
    private fun prepareEras(battle: Battle) {
        val p = battle.player
        val e = battle.enemy
        if (preparedPlayerEra != p.era || preparedPlayerVariant != p.eraVariant) {
            BattleSfx.prepareEra(g, p.era, p.eraVariant)
            preparedPlayerEra = p.era
            preparedPlayerVariant = p.eraVariant
        }
        if (preparedEnemyEra != e.era || preparedEnemyVariant != e.eraVariant) {
            BattleSfx.prepareEra(g, e.era, e.eraVariant)
            preparedEnemyEra = e.era
            preparedEnemyVariant = e.eraVariant
        }
    }

    private fun addMark(step: Int, kind: Int) {
        if (markCount < markStep.size) {
            markStep[markCount] = step
            markKind[markCount++] = kind
        }
    }

    private fun rebuildRenderer() {
        val p = player ?: return
        if (renderedBattle === p.battle) return
        val oldCam = renderer?.camX
        val next = BattleRenderer(g, p.battle, bannerId = 0)
        renderer = next
        renderedBattle = p.battle
        next.layout(w, h, g.insetT + 63 * dp, 114 * dp + g.insetB)
        if (oldCam != null) { next.camX = oldCam; next.clampCam() }
        else next.follow(1f)
    }

    override fun layout() {
        val left = g.insetL + 10 * dp
        val right = w - g.insetR - 10 * dp
        val top = g.insetT + 6 * dp
        back.label = L["back"]; back.iconId = Icons.BACK
        back.set(left, top, minOf(100 * dp, (right - left) * 0.22f), 48 * dp)
        hud.set(0f, 0f, w, g.insetT + 56 * dp)
        val gap = 5 * dp
        // Pełne strefy 48 dp; na węższych ekranach dwa rzędy.
        val stacked = right - left < (controls.size * 48 + (controls.size - 1) * 5) * dp
        val perRow = if (stacked) (controls.size + 1) / 2 else controls.size
        val footerH = if (stacked) 170 * dp else 112 * dp
        footer.set(0f, h - g.insetB - footerH, w, h)
        progressBar.set(left + 12 * dp, footer.top + 18 * dp, right - 12 * dp, footer.top + 29 * dp)
        val bw = minOf(100 * dp, (right - left - (perRow - 1) * gap) / perRow)
        val start = (left + right - (perRow * bw + (perRow - 1) * gap)) / 2f
        for (i in controls.indices) {
            val row = i / perRow
            val column = i % perRow
            controls[i].set(start + column * (bw + gap), footer.top + (47 + 56 * row) * dp, bw, 48 * dp)
        }
        backward.label = "-10s"; backward.iconId = Icons.BACK
        forward.label = "+10s"; forward.iconId = Icons.PLAY
        slower.label = "−"; slower.iconId = -1
        faster.label = "+"; faster.iconId = -1
        pause.label = L["replay_pause"]; pause.iconId = Icons.PAUSE
        camera.label = L["replay_follow"]; camera.iconId = Icons.CROSSHAIR
        graph.label = L["bs104_chart"]
        minimap.set(right - 172 * dp, top + 4 * dp, right - 53 * dp, top + 20 * dp)
        chartArea.set(left, hud.bottom + 10 * dp, right, footer.top - 10 * dp)
        resultChart.prepare(battleSummary, chartArea, dp)
        renderer?.layout(w, h, g.insetT + 63 * dp, footerH + 2 * dp + g.insetB)
    }

    override fun update(dt: Float) {
        frameDt = dt
        val p = player ?: return
        if (p.broken) { state = BROKEN; return }
        if (p.seeking) {
            // Mierzony budżet CPU, zamiast szacowania czasu jednego kroku na urządzeniu.
            val startNs = System.nanoTime()
            do {
                p.seekStep(12_000L)
            } while (p.seeking && !p.broken && System.nanoTime() - startNs < SEEK_BUDGET_NS)
            rebuildRenderer()
        } else if (!paused && !p.finished) {
            acc += dt * speed
            var n = 0
            while (acc >= Battle.STEP && n < 20 && !p.finished && !p.broken) {
                p.advance(1, keepEffects = speed == 1)
                acc -= Battle.STEP
                n++
            }
            if (n == 20) acc = 0f
        }
        if (p.broken) { state = BROKEN; return }
        if (showChart) rebuildSummarySamples()
        // Samo otwarcie ani przewinięcie osi czasu nie oznacza obejrzenia (misja po 5 s, osiągnięcie po 10 s).
        if (!imported && (!watchedClaimed || !replayAchievementChecked) &&
            !paused && !p.seeking && !draggingTime &&
            !p.finished && p.step > 0) {
            watchedSeconds += dt.coerceIn(0f, 0.1f)
            if (!watchedClaimed && watchedSeconds >= 5f) {
                watchedClaimed = true
                Missions(g.profile).onReplayWatched()
            }
            if (!replayAchievementChecked && watchedSeconds >= 10f) {
                replayAchievementChecked = true
                g.showAchievements(Achievements.onReplayWatched(g.profile, replayId, g.onlineAchievements))
            }
        }
        if (p.finished) paused = true
        val r = renderer ?: return
        prepareEras(p.battle)
        g.music?.setEra(p.battle.player.era)
        g.music?.setIntensity(0f)
        if (follow) r.follow(dt)
        if (!p.seeking && !paused && speed == 1) BattleSfx.play(g, p.battle, r.camX, r.worldVisible)
        else p.battle.fx.count = 0
        refreshLabels(p, dt)
    }

    /** Budowa próbek z oryginalnych komend jest niezależna od przewijania oglądanej powtórki. */
    private fun rebuildSummarySamples() {
        val sampler = timelineReplay ?: return
        val started = System.nanoTime()
        var batches = 0
        while (!sampler.broken && !sampler.finished && batches < 8 &&
            (batches == 0 || System.nanoTime() - started < 3_000_000L)) {
            // Każdy krok jest obserwowany osobno: znaczniki awansów trafiają w dokładny krok.
            var advanced = 0
            var count = 0
            while (count < 60 && !sampler.broken && !sampler.finished) {
                if (sampler.advance(1) == 0) break
                battleSummary.capture(sampler.battle)
                advanced++
                count++
            }
            batches++
            if (advanced == 0) break
        }
        if (sampler.finished || sampler.broken) {
            timelineReady = sampler.finished && !sampler.broken
            timelineFailed = !timelineReady
            if (timelineReady) battleSummary.finish(sampler.battle)
            timelineReplay = null
        }
        resultChart.prepare(battleSummary, chartArea, dp)
    }

    private fun refreshLabels(p: ReplayPlayer, dt: Float) {
        statusTimer = (statusTimer + dt).coerceAtMost(0.25f)
        val sec = p.battle.time.toInt()
        if (sec != timeKey) {
            timeKey = sec
            timeText = clock(sec) + " / " + clock(p.totalSteps / 60)
        }
        val b = p.battle
        val playerGold = b.player.gold.toInt()
        val enemyGold = b.enemy.gold.toInt()
        val playerHp = b.player.baseHp.toInt()
        val enemyHp = b.enemy.baseHp.toInt()
        if (statusTimer >= 0.25f && (statusPlayerEra != b.player.era ||
                statusEnemyEra != b.enemy.era || statusPlayerGold != playerGold ||
                statusEnemyGold != enemyGold || statusPlayerHp != playerHp ||
                statusEnemyHp != enemyHp || statusWave != b.wave)) {
            statusTimer = 0f
            statusPlayerEra = b.player.era
            statusEnemyEra = b.enemy.era
            statusPlayerGold = playerGold
            statusEnemyGold = enemyGold
            statusPlayerHp = playerHp
            statusEnemyHp = enemyHp
            statusWave = b.wave
            statusBuffer.setLength(0)
            statusBuffer.append(L[Defs.eras[b.player.era].nameKey]).append(' ').append(playerGold)
                .append("  ").append(L[Defs.eras[b.enemy.era].nameKey]).append(' ').append(enemyGold)
                .append("  HP ").append(playerHp).append('/').append(enemyHp)
            if (b.cfg.mode == Mode.ENDLESS) statusBuffer.append("  ").append(L.f("wave", b.wave))
            statusText = statusBuffer.toString()
        }
        var dh = 13
        for (i in b.player.doctrines.indices) dh = dh * 31 + b.player.doctrines[i].ordinal
        for (i in b.player.cardStacks.indices) dh = dh * 31 + b.player.cardStacks[i]
        if (dh != doctrineHash) {
            doctrineHash = dh
            val parts = ArrayList<String>()
            for (d in b.player.doctrines) parts.add(L[d.key])
            for (i in b.player.cardStacks.indices) if (b.player.cardStacks[i] > 0) {
                parts.add(L[cardDefs[i].textKey] + " ×" + b.player.cardStacks[i])
            }
            doctrineState = if (parts.isEmpty()) "" else
                L["replay_doctrines"] + ": " + parts.joinToString(", ")
        }
        pause.label = if (paused) L["replay_play"] else L["replay_pause"]
        pause.iconId = if (paused) Icons.PLAY else Icons.PAUSE
        camera.label = if (follow) L["replay_follow"] else L["replay_camera"]
        graph.label = if (showChart) L["bs104_battle"] else L["bs104_chart"]
        if (p.seeking) {
            val percent = (100f * p.progress).toInt()
            if (percent != lastProgress) {
                lastProgress = percent
                loadingText = L["replay_loading"] + " " + percent + "%"
            }
        }
    }

    private fun clock(sec: Int): String = (sec / 60).toString() + ":" +
        (sec % 60).toString().padStart(2, '0')

    override fun render(c: Canvas) {
        if (state != READY) {
            c.drawColor(Color.rgb(19, 25, 36))
            val message = L[when (state) {
                LOADING -> "replay_loading"
                OLD -> "replay_old_version"
                else -> "replay_broken"
            }]
            ui.textO(c, message, w / 2, h / 2, 18f, Ui.TEXT, Paint.Align.CENTER)
            ui.button(c, back, 14f)
            return
        }
        val p = player ?: return
        val r = renderer ?: return
        r.lerp = if (paused || p.seeking) 1f else (acc / Battle.STEP).coerceIn(0f, 1f)
        r.render(c, if (!paused && speed == 1 && !p.seeking) frameDt else 0f, w, h)
        ui.fill.color = Color.argb(218, 26, 32, 48); c.drawRect(hud, ui.fill)
        ui.fill.color = Color.argb(222, 26, 32, 48); c.drawRect(footer, ui.fill)
        ui.button(c, back, 13f)
        ui.textO(c, L["replay"], w * 0.5f, g.insetT + 21 * dp, 16f, Ui.GOLD, Paint.Align.CENTER)
        ui.textO(c, statusText, w * 0.5f, g.insetT + 46 * dp,
            ui.fit(statusText, 12f, w - g.insetL - g.insetR - 22 * dp), Ui.TEXT, Paint.Align.CENTER)
        drawTimeline(c, p)
        for (b in controls) ui.button(c, b, 12f)
        ui.text(c, timeText, footer.centerX(), footer.top + 13 * dp, 11f, Ui.TEXT, Paint.Align.CENTER)
        ui.text(c, speedText, footer.right - g.insetR - 14 * dp,
            footer.top + 13 * dp, 11f, Ui.GOLD, Paint.Align.RIGHT)
        if (p.seeking) ui.textO(c, loadingText, footer.centerX(), footer.top - 12 * dp, 13f, Ui.GOLD, Paint.Align.CENTER)
        drawMini(c, p, r)
        if (showChart) {
            ui.panel(c, chartArea, Color.argb(247, 26, 32, 48), 12f)
            resultChart.draw(c, ui, battleSummary, chartArea)
            if (!timelineReady) ui.textO(c,
                if (timelineFailed) L["replay_broken"] else chartLoadingText,
                chartArea.centerX(), chartArea.bottom - 22 * dp, 11f, Ui.GOLD, Paint.Align.CENTER)
        }
        // Lista doktryn i kart jest buforowana przy zmianie stanu, a nie składana na klatkę.
        if (doctrineState.isNotEmpty()) {
            ui.text(c, doctrineState, g.insetL + 14 * dp, footer.top - 12 * dp,
                ui.fit(doctrineState, 11f, w - g.insetL - g.insetR - 28 * dp), Ui.GOLD)
        }
    }

    private fun drawTimeline(c: Canvas, p: ReplayPlayer) {
        ui.fill.color = Ui.OUTLINE
        c.drawRoundRect(progressBar, 5 * dp, 5 * dp, ui.fill)
        box.set(progressBar.left + 2 * dp, progressBar.top + 2 * dp,
            progressBar.left + 2 * dp + (progressBar.width() - 4 * dp) * p.progress,
            progressBar.bottom - 2 * dp)
        ui.fill.color = Ui.PLAYER
        c.drawRoundRect(box, 3 * dp, 3 * dp, ui.fill)
        val span = progressBar.width() / p.totalSteps.coerceAtLeast(1)
        for (i in 0 until markCount) {
            val x = progressBar.left + markStep[i] * span
            ui.fill.color = when (markKind[i]) {
                0 -> Ui.GOLD
                1 -> Ui.XP
                2 -> Ui.BAD
                else -> Ui.GOOD
            }
            c.drawRect(x - dp, progressBar.top - 3 * dp, x + dp, progressBar.bottom + 3 * dp, ui.fill)
        }
        ui.fill.color = Ui.GOOD
        for (i in 0 until p.bossMarkerCount) {
            val xBoss = progressBar.left + p.bossMarkerStep(i) * span
            c.drawRect(xBoss - 2 * dp, progressBar.top - 4 * dp,
                xBoss + 2 * dp, progressBar.bottom + 4 * dp, ui.fill)
        }
        val x = progressBar.left + p.progress * progressBar.width()
        ui.fill.color = Ui.TEXT
        c.drawCircle(x, progressBar.centerY(), 7 * dp, ui.fill)
    }

    private fun drawMini(c: Canvas, p: ReplayPlayer, r: BattleRenderer) {
        if (minimap.width() <= 0f) return
        ui.fill.color = Ui.OUTLINE
        c.drawRoundRect(minimap, 4 * dp, 4 * dp, ui.fill)
        val front = p.battle.player.front / Defs.LANE
        ui.fill.color = Ui.PLAYER
        c.drawRect(minimap.left, minimap.top, minimap.left + minimap.width() * front, minimap.bottom, ui.fill)
        ui.fill.color = Ui.ENEMY
        c.drawRect(minimap.left + minimap.width() * front, minimap.top, minimap.right, minimap.bottom, ui.fill)
        val a = minimap.left + r.camX / Defs.LANE * minimap.width()
        val b = minimap.left + (r.camX + r.worldVisible) / Defs.LANE * minimap.width()
        ui.stroke.color = Ui.TEXT; ui.stroke.strokeWidth = 2 * dp
        c.drawRect(maxOf(a, minimap.left), minimap.top, minOf(b, minimap.right), minimap.bottom, ui.stroke)
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (state != READY) return false
        if (e.action == TouchEvent.DOWN && e.y in (progressBar.top - 18 * dp)..(progressBar.bottom + 18 * dp)) {
            draggingTime = true
            seekAt(e.x)
            return true
        }
        if (!draggingTime) return false
        if (e.action == TouchEvent.MOVE || e.action == TouchEvent.UP) seekAt(e.x)
        if (e.action == TouchEvent.UP || e.action == TouchEvent.CANCEL) draggingTime = false
        return true
    }

    private fun seekAt(x: Float) {
        val p = player ?: return
        paused = true
        acc = 0f
        val fraction = ((x - progressBar.left) / progressBar.width()).coerceIn(0f, 1f)
        p.requestSeek((fraction * p.totalSteps).toInt())
        rebuildRenderer()
    }

    override fun onClick(b: Button) {
        val p = player
        when (b.id) {
            1 -> g.pop()
            2 -> if (p != null) { paused = !paused; acc = 0f }
            3 -> { speed = when (speed) { 8 -> 3; 3 -> 1; else -> 1 }; refreshSpeed() }
            4 -> { speed = when (speed) { 1 -> 3; 3 -> 8; else -> 8 }; refreshSpeed() }
            5 -> p?.let { it.requestSeek(it.step - 600); paused = true; acc = 0f; rebuildRenderer() }
            6 -> p?.let { it.requestSeek(it.step + 600); paused = true; acc = 0f; rebuildRenderer() }
            7 -> follow = !follow
            8 -> {
                showChart = !showChart
                if (showChart && !timelineReady && !timelineFailed && timelineReplay == null && p != null) {
                    val rebuild = ReplayPlayer(p.cfg, p.log)
                    if (rebuild.broken) timelineFailed = true else {
                        timelineReplay = rebuild
                        battleSummary.capture(rebuild.battle)
                        resultChart.prepare(battleSummary, chartArea, dp)
                    }
                }
            }
        }
    }

    private fun refreshSpeed() {
        speedText = when (speed) { 1 -> "×1"; 3 -> "×3"; else -> "×8" }
    }

    override fun onLanguageChanged() {
        timeKey = -1
        statusTimer = 0.25f
        statusPlayerEra = -1
        doctrineHash = -1
        lastProgress = -1
        chartLoadingText = L["bs104_loading"]
        resultChart.refreshLanguage()
        super.onLanguageChanged()
    }

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        val r = renderer ?: return
        follow = false
        r.camX -= dx / r.k
        r.clampCam()
    }

    override fun onTap(x: Float, y: Float) {
        val r = renderer ?: return
        if (minimap.contains(x, y)) {
            follow = false
            r.camX = (x - minimap.left) / minimap.width() * Defs.LANE - r.worldVisible / 2
            r.clampCam()
        }
    }

    override fun onBack(): Boolean { g.pop(); return true }

    private companion object {
        const val LOADING = 0
        const val READY = 1
        const val OLD = 2
        const val BROKEN = 3
        const val SEEK_BUDGET_NS = 6_000_000L
    }
}
