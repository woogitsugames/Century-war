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
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.ReplayPlayer
import com.centurywar.game.ReplayRecord

/** Odtwarzacz bez komend gracza; całe sterowanie dotyczy wyłącznie osi czasu i kamery. */
class ReplayScreen(
    g: Game,
    private val replayId: String? = null,
    initial: ReplayRecord.Record? = null,
) : Screen(g) {
    constructor(g: Game, bytes: ByteArray) : this(g, null,
        try { ReplayRecord.decode(bytes) } catch (_: Exception) { null })

    private var player: ReplayPlayer? = null
    private var renderer: BattleRenderer? = null
    private var renderedBattle: Battle? = null
    private var state = LOADING
    private var paused = false
    private var follow = true
    private var speed = 1
    private var acc = 0f
    private var frameDt = 0f
    private var draggingTime = false

    private val back = btn(1)
    private val pause = btn(2)
    private val slower = btn(3)
    private val faster = btn(4)
    private val backward = btn(5)
    private val forward = btn(6)
    private val camera = btn(7)
    private val progressBar = RectF()
    private val minimap = RectF()
    private val hud = RectF()
    private val footer = RectF()
    private val box = RectF()
    private val markStep = IntArray(Replay.MAX_COMMANDS + 256)
    private val markKind = IntArray(Replay.MAX_COMMANDS + 256)
    private var markCount = 0
    private var lastBosses = 0
    private var timeKey = -1
    private var timeText = ""
    private var statusKey = -1
    private var statusText = ""
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
        if (!record.playable || record.log == null) {
            state = OLD
            return
        }
        val replay = ReplayPlayer(record.cfg, record.log)
        if (replay.broken) { state = BROKEN; return }
        player = replay
        for (cmd in record.log.commands) {
            val type = when (cmd.kind) {
                Replay.Kind.EVOLVE -> 0
                Replay.Kind.DOCTRINE, Replay.Kind.CARD -> 1
                Replay.Kind.BARRAGE, Replay.Kind.BARRAGE_AT -> 2
                else -> -1
            }
            if (type >= 0) addMark(cmd.step, type)
        }
        lastBosses = 0
        state = READY
        rebuildRenderer()
        if (g.current === this) layout()
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
        val next = BattleRenderer(g, p.battle)
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
        back.set(left, top, minOf(100 * dp, (right - left) * 0.22f), 37 * dp)
        hud.set(0f, 0f, w, g.insetT + 56 * dp)
        footer.set(0f, h - g.insetB - 112 * dp, w, h)
        progressBar.set(left + 12 * dp, footer.top + 18 * dp, right - 12 * dp, footer.top + 29 * dp)
        val gap = 5 * dp
        val size = minOf(86 * dp, (right - left - 6 * gap) / 7f)
        val x0 = (left + right - (7 * size + 6 * gap)) / 2f
        val y = footer.top + 47 * dp
        val controls = arrayOf(backward, slower, pause, faster, forward, camera)
        // 6 przycisków; wolne miejsce po siódmej pozycji zostawia odstęp na dotyk.
        val six = minOf(100 * dp, (right - left - 5 * gap) / 6f)
        val start = (left + right - (6 * six + 5 * gap)) / 2f
        for (i in controls.indices) controls[i].set(start + i * (six + gap), y, six, 43 * dp)
        backward.label = "-10s"; backward.iconId = Icons.BACK
        forward.label = "+10s"; forward.iconId = Icons.PLAY
        slower.label = "×1"; slower.iconId = Icons.SPEED
        faster.label = "×8"; faster.iconId = Icons.SPEED
        pause.label = L["replay_pause"]; pause.iconId = Icons.PAUSE
        camera.label = L["replay_follow"]; camera.iconId = Icons.CROSSHAIR
        minimap.set(right - 172 * dp, top + 4 * dp, right - 53 * dp, top + 20 * dp)
        renderer?.layout(w, h, g.insetT + 63 * dp, 114 * dp + g.insetB)
    }

    override fun update(dt: Float) {
        frameDt = dt
        val p = player ?: return
        if (p.broken) { state = BROKEN; return }
        if (p.seeking) {
            p.seekStep(2_000_000L)
            rebuildRenderer()
        } else if (!paused && !p.finished) {
            acc += dt * speed
            var n = 0
            while (acc >= Battle.STEP && n < 20 && !p.finished && !p.broken) {
                val oldBosses = p.battle.bossesSpawned
                p.advance(1, keepEffects = speed == 1)
                if (p.battle.bossesSpawned > oldBosses &&
                    p.battle.bossesSpawned > lastBosses) {
                    addMark(p.step, 3)
                    lastBosses = p.battle.bossesSpawned
                }
                acc -= Battle.STEP
                n++
            }
            if (n == 20) acc = 0f
        }
        if (p.broken) { state = BROKEN; return }
        if (p.finished) paused = true
        val r = renderer ?: return
        if (follow) r.follow(dt)
        if (!p.seeking && !paused && speed == 1) BattleSfx.play(g, p.battle.fx, r.camX, r.worldVisible)
        else p.battle.fx.count = 0
        refreshLabels(p)
    }

    private fun refreshLabels(p: ReplayPlayer) {
        val sec = p.battle.time.toInt()
        if (sec != timeKey) {
            timeKey = sec
            timeText = clock(sec) + " / " + clock(p.totalSteps / 60)
        }
        val b = p.battle
        val key = b.player.era * 100_000 + b.enemy.era * 10_000 +
            b.player.gold.toInt().coerceIn(0, 9999) + b.enemy.gold.toInt().coerceIn(0, 9999) * 31 +
            b.wave * 17 + b.player.doctrines.size * 53
        if (key != statusKey) {
            statusKey = key
            val wave = if (b.cfg.mode == Mode.ENDLESS) "  ${L["wave"]}: ${b.wave}" else ""
            statusText = "${L[Defs.eras[b.player.era].nameKey]} ${b.player.gold.toInt()}  " +
                "${L[Defs.eras[b.enemy.era].nameKey]} ${b.enemy.gold.toInt()}  " +
                "HP ${b.player.baseHp.toInt()}/${b.enemy.baseHp.toInt()}" + wave
        }
        pause.label = if (paused) L["replay_play"] else L["replay_pause"]
        pause.iconId = if (paused) Icons.PLAY else Icons.PAUSE
        slower.label = when (speed) { 1 -> "×1"; 3 -> "×3"; else -> "×8" }
        faster.label = slower.label
        camera.label = if (follow) L["replay_follow"] else L["replay_camera"]
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
        r.lerp = 1f
        r.render(c, if (!paused && speed == 1 && !p.seeking) frameDt else 0f, w, h)
        ui.fill.color = Color.argb(218, 26, 32, 48); c.drawRect(hud, ui.fill)
        ui.fill.color = Color.argb(222, 26, 32, 48); c.drawRect(footer, ui.fill)
        ui.button(c, back, 13f)
        ui.textO(c, L["replay"], w * 0.5f, g.insetT + 21 * dp, 16f, Ui.GOLD, Paint.Align.CENTER)
        ui.textO(c, statusText, w * 0.5f, g.insetT + 46 * dp,
            ui.fit(statusText, 12f, w - g.insetL - g.insetR - 22 * dp), Ui.TEXT, Paint.Align.CENTER)
        drawTimeline(c, p)
        for (b in arrayOf(backward, slower, pause, faster, forward, camera)) ui.button(c, b, 12f)
        ui.text(c, timeText, footer.centerX(), footer.top + 13 * dp, 11f, Ui.TEXT, Paint.Align.CENTER)
        if (p.seeking) ui.textO(c, loadingText, footer.centerX(), footer.top - 12 * dp, 13f, Ui.GOLD, Paint.Align.CENTER)
        drawMini(c, p, r)
        val doctr = p.battle.player.doctrines.size
        val cards = p.battle.player.cardStacks.sum()
        // Stan doktryn i kart jest wyłącznie odczytywany, nigdy modyfikowany przez ekran.
        if (doctr > 0 || cards > 0) {
            ui.text(c, L["replay_doctrines"], g.insetL + 14 * dp,
                footer.top - 12 * dp, 11f, Ui.GOLD)
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
            3 -> speed = when (speed) { 8 -> 3; 3 -> 1; else -> 1 }
            4 -> speed = when (speed) { 1 -> 3; 3 -> 8; else -> 8 }
            5 -> p?.let { it.requestSeek(it.step - 600); paused = true; rebuildRenderer() }
            6 -> p?.let { it.requestSeek(it.step + 600); paused = true; rebuildRenderer() }
            7 -> follow = !follow
        }
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
    }
}
