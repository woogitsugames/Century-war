package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import kotlin.math.max
import kotlin.math.min

class BattleScreen(g: Game, private val cfg: BattleConfig, private val rankedDaily: Boolean = false) : Screen(g) {

    private val battle = Battle(cfg)
    private val r = BattleRenderer(g, battle)
    private var acc = 0f
    private var speed = 1
    private var paused = false
    private var manualCam = 0f
    private var showTurrets = false
    private var finished = false
    private var resultTime = 0f
    private var tutorial = if (!g.profile.tutorialDone && cfg.mode == Mode.CAMPAIGN) 0f else -1f

    // wynik
    private var reward = 0
    private var newRecord = false
    private var tipKey = ""

    private companion object {
        const val UNIT0 = 0 // 0..3
        const val TURRET = 10
        const val ARMORY = 11
        const val BARRAGE = 12
        const val RALLY = 13
        const val STANCE = 14
        const val SPEED = 15
        const val EVOLVE = 16
        const val PAUSE = 17
        const val RESUME = 20
        const val RESTART = 21
        const val QUIT = 22
        const val CONTINUE = 23
        const val DOCTRINE0 = 30 // 30..32
        const val SLOT_BUY0 = 40 // 40..43
        const val SLOT_SELL0 = 50 // 50..53
        const val CLOSE = 60
    }

    private val unitBtn = Array(4) { i -> btn(UNIT0 + i) }
    private val turretBtn = btn(TURRET)
    private val armoryBtn = btn(ARMORY)
    private val barrageBtn = btn(BARRAGE)
    private val rallyBtn = btn(RALLY)
    private val stanceBtn = btn(STANCE)
    private val speedBtn = btn(SPEED)
    private val evolveBtn = btn(EVOLVE)
    private val pauseBtn = btn(PAUSE, "II")
    private val pauseMenu = listOf(btn(RESUME), btn(RESTART), btn(QUIT))
    private val continueBtn = btn(CONTINUE)
    private val restartEnd = btn(RESTART + 100)
    private val doctrineBtn = Array(3) { btn(DOCTRINE0 + it) }
    private val slotBuy = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_BUY0 + it) }
    private val slotSell = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_SELL0 + it) }
    private val closeBtn = btn(CLOSE, "✕")

    private val topBar = RectF()
    private val mini = RectF()
    private val panel = RectF()
    private var hudH = 0f
    private var topH = 0f

    init {
        for (i in 0..3) {
            val role = Role.values()[i]
            unitBtn[i].icon = { c, rr ->
                val def = Defs.unit(battle.player.era, role)
                r.drawUnitIcon(c, def, rr.centerX(), rr.bottom - 18 * dp, rr.height() - 26 * dp, Ui.PLAYER)
            }
        }
        r.camX = -120f
    }

    override fun layout() {
        val pad = 6 * dp
        topH = 46 * dp
        val l = g.insetL + pad
        val rr = w - g.insetR - pad
        val avail = rr - l
        val n = 10
        val gap = 5 * dp
        val size = min(70 * dp, (avail - gap * (n - 1) - 10 * dp) / n)
        hudH = size + pad * 2 + g.insetB
        val y = h - g.insetB - pad - size
        var x = l
        for (b in unitBtn) { b.set(x, y, size, size); x += size + gap }
        x += 10 * dp
        for (b in listOf(turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn)) { b.set(x, y, size, size); x += size + gap }

        topBar.set(0f, 0f, w, topH + g.insetT)
        val t = g.insetT + 6 * dp
        evolveBtn.set(l + 170 * dp, t, 90 * dp, 34 * dp)
        pauseBtn.set(rr - 40 * dp, t, 40 * dp, 34 * dp)
        mini.set(evolveBtn.r.right + 12 * dp, t + 8 * dp, pauseBtn.r.left - 180 * dp, t + 26 * dp)
        if (mini.width() < 60 * dp) mini.set(0f, 0f, 0f, 0f)

        r.layout(w, h, topH + g.insetT + 10 * dp, hudH)

        val bw = min(260 * dp, w * 0.6f)
        val bh = 46 * dp
        for ((i, b) in pauseMenu.withIndex()) b.set(w / 2 - bw / 2, h * 0.32f + i * (bh + 12 * dp), bw, bh)
        continueBtn.set(w / 2 + 6 * dp, h * 0.78f, bw * 0.8f, bh)
        restartEnd.set(w / 2 - 6 * dp - bw * 0.8f, h * 0.78f, bw * 0.8f, bh)
        val cw = min(220 * dp, (w - 60 * dp) / 3)
        for (i in 0..2) doctrineBtn[i].set(w / 2 - cw * 1.5f - 12 * dp + i * (cw + 12 * dp), h * 0.34f, cw, 130 * dp)
        layoutTurretPanel()
    }

    private fun layoutTurretPanel() {
        val pw = min(420 * dp, w - 40 * dp)
        val rowH = 42 * dp
        val ph = 54 * dp + rowH * Defs.MAX_TURRET_SLOTS + 16 * dp
        panel.set(w / 2 - pw / 2, (h - hudH - ph) / 2 + topH / 2, w / 2 + pw / 2, (h - hudH - ph) / 2 + topH / 2 + ph)
        for (i in 0 until Defs.MAX_TURRET_SLOTS) {
            val y = panel.top + 50 * dp + i * rowH
            slotBuy[i].set(panel.right - 230 * dp, y, 120 * dp, rowH - 6 * dp)
            slotSell[i].set(panel.right - 104 * dp, y, 92 * dp, rowH - 6 * dp)
        }
        closeBtn.set(panel.right - 40 * dp, panel.top + 8 * dp, 32 * dp, 32 * dp)
    }

    // ---------------------------------------------------------------- logika

    private var frameDt = 0f

    override fun update(dt: Float) {
        frameDt = dt
        if (tutorial >= 0f) tutorial += dt
        if (!paused && !finished) {
            if (battle.pendingChoice == null) {
                acc += dt * speed
                var steps = 0
                while (acc >= Battle.STEP && steps < 20) { battle.step(); acc -= Battle.STEP; steps++ }
                if (steps == 20) acc = 0f
            }
            if (battle.over) onFinished()
        }
        if (finished) resultTime += dt
        if (manualCam > 0f) manualCam -= dt else if (!finished) r.follow(dt)
        refreshButtons()
    }

    private fun refreshButtons() {
        val p = battle.player
        val modal = paused || finished || battle.pendingChoice != null
        for (i in 0..3) {
            val role = Role.values()[i]
            val def = Defs.unit(p.era, role)
            val b = unitBtn[i]
            b.visible = !modal
            val locked = role == Role.ELITE && !p.eliteUnlocked
            b.enabled = !locked && battle.canQueue(p, role)
            b.sub = if (locked) "🔒" else def.cost.toString()
            b.progress = if (p.queueSize > 0 && p.queue[0] == def.id) p.trainT / def.trainTime else -1f
        }
        turretBtn.visible = !modal; turretBtn.label = L["turrets"]; turretBtn.enabled = p.slots > 0
        turretBtn.selected = showTurrets
        armoryBtn.visible = !modal
        armoryBtn.label = "⚒${p.armory}"
        armoryBtn.sub = battle.armoryCost(p).toString()
        armoryBtn.enabled = p.gold >= battle.armoryCost(p) && p.armory < 15
        barrageBtn.visible = !modal
        barrageBtn.label = "☄"
        barrageBtn.enabled = battle.abilityReady(p, 0)
        barrageBtn.progress = if (p.abilityCd[0] > 0f) 1f - p.abilityCd[0] / (Battle.BARRAGE_CD * p.abilityCdMul) else -1f
        rallyBtn.visible = !modal && p.rallyUnlocked
        rallyBtn.label = "⚑"
        rallyBtn.sub = ""
        rallyBtn.enabled = battle.abilityReady(p, 1)
        rallyBtn.progress = if (p.abilityCd[1] > 0f) 1f - p.abilityCd[1] / (Battle.RALLY_CD * p.abilityCdMul) else -1f
        rallyBtn.highlight = p.rallyT > 0f
        stanceBtn.visible = !modal
        stanceBtn.label = if (p.hold) L["hold"] else L["advance"]
        stanceBtn.highlight = p.hold
        speedBtn.visible = !modal
        speedBtn.label = "×$speed"
        evolveBtn.visible = !modal
        val canEvo = battle.canEvolve(p)
        evolveBtn.enabled = canEvo
        evolveBtn.highlight = canEvo
        evolveBtn.label = if (p.era >= cfg.maxEra) L.f("rank", p.rank) else L["evolve"]
        if (p.era >= cfg.maxEra) evolveBtn.enabled = false
        pauseBtn.visible = !finished

        for (b in pauseMenu) b.visible = paused && !finished
        pauseMenu[0].label = L["resume"]; pauseMenu[1].label = L["restart"]; pauseMenu[2].label = L["quit"]
        continueBtn.visible = finished && resultTime > 0.8f
        continueBtn.label = L["continue"]
        restartEnd.visible = finished && resultTime > 0.8f
        restartEnd.label = L["restart"]

        val choice = battle.pendingChoice
        for (i in 0..2) {
            doctrineBtn[i].visible = choice != null && !paused
            doctrineBtn[i].label = ""
        }
        val tp = showTurrets && !modal
        for (i in 0 until Defs.MAX_TURRET_SLOTS) {
            val active = tp && i < p.slots
            slotBuy[i].visible = active
            slotSell[i].visible = active && p.turretEra[i] >= 0
            if (!active) continue
            val te = p.turretEra[i]
            if (te < 0) {
                slotBuy[i].label = L["buy"]; slotBuy[i].sub = ""
                slotBuy[i].label = "${L["buy"]} ${p.turretCost()}"
                slotBuy[i].enabled = p.gold >= p.turretCost()
            } else {
                val uc = battle.upgradeCost(p, i)
                slotBuy[i].label = if (uc < 0) "✓" else "${L["upgrade"]} $uc"
                slotBuy[i].enabled = uc >= 0 && p.gold >= uc
            }
            slotSell[i].label = "${L["sell"]} ${if (te >= 0) p.turretCost(te) / 2 else 0}"
        }
        closeBtn.visible = tp
    }

    override fun onClick(b: Button) {
        val p = battle.player
        when (b.id) {
            in UNIT0..UNIT0 + 3 -> battle.queueUnit(p, Role.values()[b.id - UNIT0])
            TURRET -> showTurrets = !showTurrets
            CLOSE -> showTurrets = false
            ARMORY -> battle.buyArmory(p)
            BARRAGE -> battle.castBarrage(p)
            RALLY -> battle.castRally(p)
            STANCE -> p.hold = !p.hold
            SPEED -> speed = when (speed) { 1 -> 3; 3 -> 5; else -> 1 }
            EVOLVE -> if (battle.evolve(p)) g.haptic(40)
            PAUSE -> paused = true
            RESUME -> paused = false
            RESTART, RESTART + 100 -> g.setScreen(BattleScreen(g, cfg.copyWithSeed(), false))
            QUIT -> { paused = false; battle.surrender() }
            CONTINUE -> g.setScreen(nextScreen())
            in DOCTRINE0..DOCTRINE0 + 2 -> battle.chooseDoctrine(b.id - DOCTRINE0)
            in SLOT_BUY0 until SLOT_BUY0 + Defs.MAX_TURRET_SLOTS -> {
                val i = b.id - SLOT_BUY0
                if (p.turretEra[i] < 0) battle.buyTurret(p, i) else battle.upgradeTurret(p, i)
            }
            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> battle.sellTurret(p, b.id - SLOT_SELL0)
        }
    }

    private fun BattleConfig.copyWithSeed() = BattleConfig(mode, if (mode == Mode.DAILY) seed else seed + 1, aiPower, startEra, maxEra, modifiers, playerBonuses, stageIndex)

    private fun nextScreen(): Screen = when (cfg.mode) {
        Mode.CAMPAIGN -> CampaignScreen(g)
        Mode.ENDLESS, Mode.DAILY, Mode.SKIRMISH -> ModeScreen(g, cfg.mode)
    }

    override fun onDrag(dx: Float, dy: Float) {
        if (finished || paused) return
        r.camX -= dx / r.k
        r.clampCam()
        manualCam = 4f
    }

    override fun onTap(x: Float, y: Float) {
        if (mini.width() > 0f && x in mini.left..mini.right && y in (mini.top - 10 * dp)..(mini.bottom + 10 * dp)) {
            r.camX = (x - mini.left) / mini.width() * Defs.LANE - r.worldVisible / 2
            r.clampCam(); manualCam = 5f
            return
        }
        if (showTurrets && !panel.contains(x, y)) showTurrets = false
        if (tutorial in 0f..60f) tutorial = 61f
    }

    override fun onBack(): Boolean {
        if (finished) { g.setScreen(nextScreen()); return true }
        if (showTurrets) { showTurrets = false; return true }
        paused = !paused
        return true
    }

    override fun onPause() {
        if (!finished) paused = true
    }

    // ---------------------------------------------------------------- koniec bitwy

    private fun onFinished() {
        finished = true
        val pr = g.profile
        val won = battle.winner == 0
        pr.battles++
        if (won) pr.wins++
        pr.totalKills += battle.player.kills
        pr.playSeconds += battle.time.toLong()
        val day = Daily.dayNumber()
        val score = battle.score()
        when (cfg.mode) {
            Mode.CAMPAIGN -> {
                val stage = cfg.stageIndex
                val old = pr.stars[stage]
                val st = battle.stars()
                reward = if (won) 20 + stage * 6 + st * 5 + max(0, st - old) * 12 else 5 + stage * 2 + min(20, battle.player.kills / 8)
                if (st > old) {
                    pr.stars[stage] = st
                    newRecord = pr.submit(Profile.BOARD_CAMPAIGN, pr.totalStars().toLong(), day)
                    g.online.submit(Profile.BOARD_CAMPAIGN, pr.totalStars().toLong())
                }
                if (won) pr.tutorialDone = true
            }
            Mode.SKIRMISH -> {
                reward = if (won) 15 + pr.skirmishDiff * 14 else 4 + battle.player.kills / 12
                if (won) newRecord = pr.submit(Profile.BOARD_SKIRMISH, score, day)
            }
            Mode.ENDLESS -> {
                reward = battle.wave * 3 + battle.player.kills / 10
                if (battle.wave > pr.bestWave) pr.bestWave = battle.wave
                newRecord = pr.submit(Profile.BOARD_ENDLESS, score, day)
                g.online.submit(Profile.BOARD_ENDLESS, score)
            }
            Mode.DAILY -> {
                if (rankedDaily) {
                    reward = min(90, (score / 350).toInt()) + 10
                    newRecord = pr.submit(Profile.dailyBoard(cfg.seed), score, day)
                    g.online.submit("daily", score)
                } else reward = 5
            }
        }
        pr.medals += reward
        tipKey = if (won) "" else when {
            battle.enemy.era > battle.player.era -> "tip_era"
            battle.enemy.goldEarned > battle.player.goldEarned * 1.25f -> "tip_eco"
            battle.player.turretEra.none { it >= 0 } || battle.player.armory == 0 -> "tip_turret"
            else -> "tip_ability"
        }
        pr.save()
        g.haptic(if (won) 80 else 160)
    }

    // ---------------------------------------------------------------- rysowanie

    override fun render(c: Canvas) {
        r.render(c, if (paused || battle.pendingChoice != null) 0f else frameDt * speed, w, h)
        drawTop(c)
        drawHudBack(c)
        for (b in listOf(turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn)) ui.button(c, b, 14f)
        for (b in unitBtn) ui.button(c, b, 13f)
        drawQueue(c)
        ui.button(c, evolveBtn, 14f)
        ui.button(c, pauseBtn, 15f)
        if (showTurrets && battle.pendingChoice == null && !paused && !finished) drawTurretPanel(c)
        if (tutorial in 0f..60f) drawTutorial(c)
        if (battle.pendingChoice != null && !finished) drawDoctrines(c)
        if (paused && !finished) {
            c.drawColor(Color.argb(150, 0, 0, 0))
            ui.text(c, L["paused"], w / 2, h * 0.24f, 30f, Ui.TEXT, Paint.Align.CENTER, true, true)
            for (b in pauseMenu) ui.button(c, b, 17f)
        }
        if (finished) drawResults(c)
    }

    private fun drawTop(c: Canvas) {
        val p = battle.player
        val e = battle.enemy
        ui.fill.color = Color.argb(140, 10, 14, 22)
        c.drawRect(topBar, ui.fill)
        val l = g.insetL + 8 * dp
        val t = g.insetT + 6 * dp
        // złoto + XP
        ui.text(c, "● ${p.gold.toInt()}", l, t + 15 * dp, 15f, Ui.GOLD, Paint.Align.LEFT, true, true)
        val need = if (p.era < cfg.maxEra) Defs.evolveXp[p.era].toFloat() else battle.rankXp(p)
        ui.text(c, "${L[Defs.eraKeys[p.era]]}", l + 80 * dp, t + 13 * dp, 11f, Ui.TEXT, Paint.Align.LEFT, false, true)
        ui.bar(c, l, t + 22 * dp, 160 * dp, 8 * dp, p.xp / need, Ui.XP)
        ui.text(c, "${L["cap"]} ${p.aliveCount}/${p.cap}", l, t + 42 * dp, 10f, Ui.DIM, Paint.Align.LEFT, false, true)
        // HP baz po bokach minimapy
        if (mini.width() > 0f) {
            ui.bar(c, mini.left, mini.top - 7 * dp, mini.width() * 0.48f, 5 * dp, p.baseHp / p.baseMaxHp, Ui.PLAYER)
            if (!battle.endless) ui.bar(c, mini.right - mini.width() * 0.48f, mini.top - 7 * dp, mini.width() * 0.48f, 5 * dp, e.baseHp / e.baseMaxHp, Ui.ENEMY)
            ui.fill.color = Color.argb(150, 0, 0, 0)
            c.drawRoundRect(mini, 4 * dp, 4 * dp, ui.fill)
            // widoczny obszar
            ui.stroke.color = Color.argb(160, 255, 255, 255)
            ui.stroke.strokeWidth = 1.2f * dp
            val vl = mini.left + (r.camX / Defs.LANE) * mini.width()
            val vr = mini.left + ((r.camX + r.worldVisible) / Defs.LANE) * mini.width()
            c.drawRect(max(mini.left, vl), mini.top, min(mini.right, vr), mini.bottom, ui.stroke)
            for (u in battle.units) {
                if (!u.alive) continue
                if (battle.fog && u.side == 1 && u.x - p.front > 650f) continue
                ui.fill.color = if (u.side == 0) Ui.PLAYER else Ui.ENEMY
                val x = r.worldToMini(u.x, mini.left, mini.width())
                c.drawCircle(x, mini.centerY(), 2.6f * dp * u.def.size.coerceAtMost(1.4f), ui.fill)
            }
            ui.text(c, "${p.baseHp.toInt()}", mini.left, mini.bottom + 12 * dp, 10f, Ui.PLAYER, Paint.Align.LEFT, true, true)
            val rightLabel = when {
                battle.endless -> L.f("wave", battle.wave)
                else -> "${e.baseHp.toInt()}"
            }
            ui.text(c, rightLabel, mini.right, mini.bottom + 12 * dp, 10f, Ui.ENEMY, Paint.Align.RIGHT, true, true)
            val sp = battle.siegePressure()
            if (sp > 1f) ui.text(c, L.f("siege_pressure", sp), mini.centerX(), mini.bottom + 12 * dp, 10f, Ui.BAD, Paint.Align.CENTER, true, true)
            else ui.text(c, timeStr(battle.time), mini.centerX(), mini.bottom + 12 * dp, 10f, Ui.DIM, Paint.Align.CENTER, false, true)
        }
        // przeciwnik (epoka + ranga)
        ui.text(c, L[Defs.eraKeys[e.era]], pauseBtn.r.left - 8 * dp, t + 14 * dp, 11f, Ui.ENEMY, Paint.Align.RIGHT, true, true)
        if (p.doctrines.isNotEmpty()) {
            val s = p.doctrines.joinToString(" ") { L[it.key].take(3) }
            ui.text(c, s, pauseBtn.r.left - 8 * dp, t + 30 * dp, 9f, Ui.GOLD, Paint.Align.RIGHT, false, true)
        }
    }

    private fun timeStr(t: Float): String {
        val s = t.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun drawHudBack(c: Canvas) {
        ui.fill.color = Color.argb(120, 10, 14, 22)
        c.drawRect(0f, h - hudH, w, h, ui.fill)
    }

    private fun drawQueue(c: Canvas) {
        val p = battle.player
        if (p.queueSize == 0) return
        val y = unitBtn[0].r.top - 8 * dp
        for (i in 0 until p.queueSize) {
            ui.fill.color = if (i == 0) Ui.GOLD else Color.argb(200, 230, 230, 230)
            c.drawCircle(unitBtn[0].r.left + 8 * dp + i * 14 * dp, y, 5 * dp, ui.fill)
        }
    }

    private fun drawTurretPanel(c: Canvas) {
        val p = battle.player
        ui.panel(c, panel)
        ui.text(c, "${L["turrets"]} — ${L[Defs.eras[p.era].turret.nameKey]}", panel.left + 14 * dp, panel.top + 30 * dp, 16f, Ui.TEXT, Paint.Align.LEFT, true)
        for (i in 0 until p.slots) {
            val y = slotBuy[i].r.centerY() + 5 * dp
            val te = p.turretEra[i]
            val name = if (te >= 0) L[Defs.eras[te].turret.nameKey] else L["empty"]
            ui.text(c, "${L.f("slot", i + 1)}: $name", panel.left + 14 * dp, y, 12f, if (te >= 0) Ui.TEXT else Ui.DIM)
            ui.button(c, slotBuy[i], 12f)
            ui.button(c, slotSell[i], 12f)
        }
        ui.button(c, closeBtn, 14f)
    }

    private fun drawDoctrines(c: Canvas) {
        val ch = battle.pendingChoice ?: return
        c.drawColor(Color.argb(150, 0, 0, 0))
        ui.text(c, L["choose_doctrine"], w / 2, h * 0.27f, 24f, Ui.GOLD, Paint.Align.CENTER, true, true)
        for (i in 0..2) {
            val b = doctrineBtn[i]
            ui.button(c, b)
            ui.text(c, L[ch[i].key], b.r.centerX(), b.r.top + 34 * dp, 18f, Ui.GOLD, Paint.Align.CENTER, true, true)
            ui.wrap(c, L[ch[i].key + "_d"], b.r.centerX(), b.r.top + 64 * dp, b.r.width() - 20 * dp, 13f, Ui.TEXT, Paint.Align.CENTER)
        }
    }

    private fun drawTutorial(c: Canvas) {
        val key = when { tutorial < 12f -> "tut_1"; tutorial < 30f -> "tut_2"; else -> "tut_3" }
        val s = L[key]
        val tw = ui.measure(s, 15f) + 30 * dp
        val y = topH + g.insetT + 20 * dp
        val rr = RectF(w / 2 - tw / 2, y, w / 2 + tw / 2, y + 36 * dp)
        ui.panel(c, rr, Color.argb(220, 40, 30, 10))
        ui.text(c, s, w / 2, y + 23 * dp, 15f, Ui.GOLD, Paint.Align.CENTER, true)
    }

    private fun drawResults(c: Canvas) {
        val a = min(1f, resultTime * 2f)
        c.drawColor(Color.argb((170 * a).toInt(), 0, 0, 0))
        val won = battle.winner == 0
        val title = when {
            cfg.mode == Mode.ENDLESS -> L["siege_over"]
            won -> L["victory"]
            else -> L["defeat"]
        }
        ui.text(c, title, w / 2, h * 0.2f, 34f, if (won || cfg.mode == Mode.ENDLESS) Ui.GOLD else Ui.BAD, Paint.Align.CENTER, true, true)
        if (cfg.mode == Mode.CAMPAIGN && won) {
            val st = battle.stars()
            ui.text(c, "★".repeat(st) + "☆".repeat(3 - st), w / 2, h * 0.3f, 32f, Ui.GOLD, Paint.Align.CENTER, true, true)
        }
        var y = h * 0.4f
        val lx = w / 2 - 120 * dp
        val rx = w / 2 + 120 * dp
        fun row(k: String, v: String, col: Int = Ui.TEXT) {
            ui.text(c, k, lx, y, 15f, Ui.DIM)
            ui.text(c, v, rx, y, 15f, col, Paint.Align.RIGHT, true)
            y += 24 * dp
        }
        row(L["time"], timeStr(battle.time))
        row(L["kills"], battle.player.kills.toString())
        if (cfg.mode == Mode.ENDLESS) row(L.f("wave", battle.wave).substringBefore(' '), battle.wave.toString())
        if (cfg.mode != Mode.CAMPAIGN) row(L["score"], battle.score().toString(), Ui.GOLD)
        row(L["reward"], "+$reward ${L["medals"]}", Ui.GOLD)
        if (newRecord) ui.text(c, L["new_record"], w / 2, y + 8 * dp, 18f, Ui.GOOD, Paint.Align.CENTER, true, true)
        if (tipKey.isNotEmpty()) {
            ui.wrap(c, "${L["why_lost"]}: ${L[tipKey]}", w / 2, y + 30 * dp, min(520 * dp, w - 40 * dp), 13f, Ui.TEXT, Paint.Align.CENTER)
        }
        ui.button(c, continueBtn, 16f)
        ui.button(c, restartEnd, 16f)
    }
}
