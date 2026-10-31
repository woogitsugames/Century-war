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
import com.centurywar.engine.Sound
import com.centurywar.game.FxBuffer
import com.centurywar.game.Proj
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.BattleSnapshot
import com.centurywar.game.RestoredBattle
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.EndlessRarity
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import kotlin.math.max
import kotlin.math.min

/**
 * [restored] != null: bitwa wznowiona ze zrzutu (aplikacja została zabita w tle).
 * Startuje wtedy w pauzie, a [cfg] musi być konfiguracją odtworzonej bitwy.
 */
class BattleScreen(
    g: Game,
    private val cfg: BattleConfig,
    private val rankedDaily: Boolean = false,
    restored: RestoredBattle? = null,
) : Screen(g) {

    private val battle = restored?.battle ?: Battle(cfg)
    private val r = BattleRenderer(g, battle)
    private var acc = 0f
    private var speed = 1
    private var paused = restored != null
    /** Czas bitwy ostatniego autozapisu. */
    private var lastAutosave = battle.time
    /** Ekran porzucony (restart): nie zapisuj już jego zrzutu. */
    private var closed = false
    private var manualCam = 0f
    private var showTurrets = false
    private var finished = false
    private var resultTime = 0f


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
        /** Autozapis co tyle sekund czasu bitwy (zapis pliku w tle). */
        const val AUTOSAVE_EVERY = 30f
    }

    /** Role.values() alokuje nową tablicę przy każdym wywołaniu, więc trzymamy kopię. */
    private val roles = Role.values()
    private val unitBtn = Array(4) { i -> btn(UNIT0 + i) }
    private val turretBtn = btn(TURRET)
    private val armoryBtn = btn(ARMORY)
    private val barrageBtn = btn(BARRAGE)
    private val rallyBtn = btn(RALLY)
    private val stanceBtn = btn(STANCE)
    private val speedBtn = btn(SPEED)
    private val evolveBtn = btn(EVOLVE)
    private val pauseBtn = btn(PAUSE)
    private val pauseMenu = listOf(btn(RESUME), btn(RESTART), btn(QUIT))
    private val continueBtn = btn(CONTINUE)
    private val restartEnd = btn(RESTART + 100)
    private val doctrineBtn = Array(3) { btn(DOCTRINE0 + it) }
    private val slotBuy = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_BUY0 + it) }
    private val slotSell = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_SELL0 + it) }
    private val closeBtn = btn(CLOSE)

    private val topBar = RectF()
    private val mini = RectF()
    private val goldRect = RectF()
    private val xpRect = RectF()
    private val fieldRect = RectF()
    private val panel = RectF()
    private var hudH = 0f
    private var topH = 0f

    init {
        for (i in 0..3) {
            val role = roles[i]
            unitBtn[i].icon = { c, rr ->
                val def = Defs.unit(battle.player.era, role)
                r.drawUnitIcon(c, def, rr.centerX(), rr.bottom - 18 * dp, rr.height() - 26 * dp, Ui.PLAYER)
            }
        }
        r.camX = restored?.camX ?: -120f
    }

    /** Interaktywny samouczek (tylko etap 1 kampanii, dopóki gracz go nie ukończy/pominie). */
    private val tut: TutorialOverlay? =
        if (restored == null && cfg.tutorial && cfg.mode == Mode.CAMPAIGN && !g.profile.tutorialDone) TutorialOverlay(g, battle, TutorialOverlay.Targets(
            unitBtn, evolveBtn, turretBtn, barrageBtn, slotBuy, doctrineBtn,
            arrayOf(unitBtn[0], unitBtn[1], unitBtn[2], unitBtn[3], turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn, evolveBtn),
            goldRect, xpRect, mini, fieldRect, { showTurrets }, { showTurrets = false },
        )) else null

    init {
        // przyciski samouczka na początku listy: mają pierwszeństwo przed HUD pod spodem
        tut?.let { buttons.add(0, it.nextBtn); buttons.add(0, it.skipBtn) }
    }

    override fun layout() {
        val pad = 6 * dp
        topH = 52 * dp
        val l = g.insetL + pad
        val rr = w - g.insetR - pad
        val avail = rr - l
        val n = 10
        val gap = 5 * dp
        val size = min(70 * dp, (avail - gap * (n - 1) - 10 * dp) / n)
        hudH = size + pad * 2 + 4 * dp + g.insetB
        val y = h - g.insetB - pad - size
        var x = l
        for (b in unitBtn) { b.set(x, y, size, size); x += size + gap }
        x += 10 * dp
        for (b in actionBtns) { b.set(x, y, size, size); x += size + gap }

        topBar.set(0f, 0f, w, topH + g.insetT)
        val t = g.insetT + 6 * dp
        evolveBtn.set(l + 196 * dp, t, 118 * dp, 40 * dp)
        pauseBtn.set(rr - 46 * dp, t, 46 * dp, 40 * dp)
        mini.set(evolveBtn.r.right + 16 * dp, t + 13 * dp, pauseBtn.r.left - 112 * dp, t + 29 * dp)
        if (mini.width() < 60 * dp) mini.set(0f, 0f, 0f, 0f)
        goldRect.set(l + 2 * dp, t - 4 * dp, l + 110 * dp, t + 26 * dp)
        xpRect.set(l, t + 22 * dp, l + 186 * dp, t + 44 * dp)
        fieldRect.set(0f, topH + g.insetT, w, h - hudH)

        r.layout(w, h, topH + g.insetT + 10 * dp, hudH)

        val bw = min(270 * dp, w * 0.6f)
        val bh = 50 * dp
        val pmH = bh * 3 + 12 * dp * 2 + 96 * dp
        pausePanel.set(w / 2 - bw / 2 - 28 * dp, h / 2 - pmH / 2, w / 2 + bw / 2 + 28 * dp, h / 2 + pmH / 2)
        for ((i, b) in pauseMenu.withIndex()) b.set(w / 2 - bw / 2, pausePanel.top + 52 * dp + i * (bh + 12 * dp), bw, bh)
        layoutResults()
        val cw = min(230 * dp, (w - g.insetL - g.insetR - 60 * dp) / 3)
        val chH = min(218 * dp, (h - g.insetT - g.insetB) * 0.48f)
        for (i in 0..2) doctrineBtn[i].set(w / 2 - cw * 1.5f - 14 * dp + i * (cw + 14 * dp), h * 0.5f - chH * 0.4f, cw, chH)
        layoutTurretPanel()
    }

    private fun layoutResults() {
        val rw = min(460 * dp, w - g.insetL - g.insetR - 40 * dp)
        val rh = min(h - g.insetT - g.insetB - 40 * dp, 400 * dp)
        resultPanel.set(w / 2 - rw / 2, h / 2 - rh / 2 + 16 * dp, w / 2 + rw / 2, h / 2 + rh / 2 + 16 * dp)
        val bw = (rw - 66 * dp) / 2
        val bh = 50 * dp
        restartEnd.set(resultPanel.left + 24 * dp, resultPanel.bottom - bh - 22 * dp, bw, bh)
        continueBtn.set(resultPanel.right - 24 * dp - bw, resultPanel.bottom - bh - 22 * dp, bw, bh)
    }

    private fun layoutTurretPanel() {
        val pw = min(480 * dp, w - 40 * dp)
        val rowH = 46 * dp
        turretSlotsLaid = battle.player.slots
        val ph = 62 * dp + rowH * turretSlotsLaid + 10 * dp
        panel.set(w / 2 - pw / 2, (h - hudH - ph) / 2 + topH / 2, w / 2 + pw / 2, (h - hudH - ph) / 2 + topH / 2 + ph)
        val bw = min(140 * dp, pw * 0.3f)
        val sw = min(116 * dp, pw * 0.25f)
        for (i in 0 until Defs.MAX_TURRET_SLOTS) {
            val y = panel.top + 50 * dp + i * rowH
            slotSell[i].set(panel.right - 20 * dp - sw, y, sw, rowH - 8 * dp)
            slotBuy[i].set(slotSell[i].r.left - 8 * dp - bw, y, bw, rowH - 8 * dp)
        }
        closeBtn.set(panel.right - 30 * dp, panel.top - 12 * dp, 40 * dp, 40 * dp)
    }


    // ---------------------------------------------------------------- logika

    private var frameDt = 0f

    override fun update(dt: Float) {
        frameDt = dt
        val tutScale = tut?.timeScale ?: 1f
        if (!paused && !finished) {
            tut?.update(dt)
            if (battle.pendingChoice == null && battle.pendingCards == null) {
                acc += dt * speed * tutScale
                var steps = 0
                while (acc >= Battle.STEP && steps < 20) { battle.step(); acc -= Battle.STEP; steps++ }
                if (steps == 20) acc = 0f
            }
            if (battle.over) onFinished()
            else if (battle.time - lastAutosave >= AUTOSAVE_EVERY) {
                // Bajty powstają tu, na wątku gry (spójny stan); plik zapisuje wątek w tle.
                lastAutosave = battle.time
                g.snapshots?.saveAsync(snapshotBytes())
            }
        }
        playSounds()
        if (finished) resultTime += dt
        if (manualCam > 0f) manualCam -= dt else if (!finished) r.follow(dt)
        refreshButtons()
    }

    /** Dźwięki ze zdarzeń symulacji. Renderer czyta ten sam bufor w render() i go czyści. */
    private fun playSounds() {
        val fx = battle.fx
        if (fx.count == 0 || g.sound == null) return
        val center = r.camX + r.worldVisible / 2
        val range = r.worldVisible * 0.9f
        for (i in 0 until fx.count) {
            val vol = (1f - kotlin.math.abs(fx.x[i] - center) / range).coerceIn(0.15f, 1f)
            when (fx.type[i]) {
                FxBuffer.HIT -> g.sfx(Sound.HIT, vol * 0.5f, 60)
                FxBuffer.DEATH -> g.sfx(Sound.DEATH, vol * 0.6f, 90)
                FxBuffer.EXPLOSION -> g.sfx(Sound.EXPLOSION, vol * min(1f, fx.value[i]), 120)
                FxBuffer.MUZZLE -> {
                    val p = fx.value[i].toInt()
                    when (p) {
                        Proj.BEAM, Proj.PLASMA -> g.sfx(Sound.LASER, vol * 0.5f, 70)
                        Proj.BULLET, Proj.SHELL, Proj.BALL, Proj.ROCKET -> g.sfx(Sound.SHOT, vol * 0.55f, 55)
                        else -> g.sfx(Sound.ARROW, vol * 0.45f, 70)
                    }
                }
                FxBuffer.BASE_HIT -> g.sfx(Sound.THUMP, vol * 0.6f, 120)
                FxBuffer.EVOLVE -> g.sfx(Sound.EVOLVE, if (fx.side[i] == 0) 1f else 0.5f, 300)
                FxBuffer.GOLD -> g.sfx(Sound.COIN, 0.35f, 110)
                FxBuffer.BARRAGE -> g.sfx(Sound.EXPLOSION, 0.8f, 200, 0.8f)
            }
        }
    }

    private var turretSlotsLaid = -1

    private fun refreshButtons() {
        val p = battle.player
        if (p.slots != turretSlotsLaid) layoutTurretPanel()
        val modal = paused || finished || battle.pendingChoice != null || battle.pendingCards != null
        for (i in 0..3) {
            val role = roles[i]
            val def = Defs.unit(p.era, role)
            val b = unitBtn[i]
            b.visible = !modal
            val locked = role == Role.ELITE && !p.eliteUnlocked
            b.enabled = !locked && battle.canQueue(p, role)
            b.sub = if (locked) "" else costStr(i, def.cost)
            b.subIcon = if (locked) Icons.LOCK else Icons.COIN
            b.progress = if (p.queueSize > 0 && p.queue[0] == def.id) p.trainT / def.trainTime else -1f
        }
        turretBtn.visible = !modal; turretBtn.label = L["turrets"]; turretBtn.enabled = p.slots > 0
        turretBtn.iconId = Icons.TOWER; turretBtn.iconTint = Ui.PLAYER
        turretBtn.selected = showTurrets
        armoryBtn.visible = !modal
        armoryBtn.iconId = Icons.ANVIL
        if (armoryLv != p.armory) { armoryLv = p.armory; armoryBtn.label = p.armory.toString() }
        armoryBtn.sub = costStr(4, battle.armoryCost(p))
        armoryBtn.subIcon = Icons.COIN
        armoryBtn.enabled = p.gold >= battle.armoryCost(p) && p.armory < 15
        barrageBtn.visible = !modal
        barrageBtn.label = ""
        barrageBtn.iconId = Icons.METEOR
        barrageBtn.enabled = battle.abilityReady(p, 0)
        barrageBtn.progress = if (p.abilityCd[0] > 0f) 1f - p.abilityCd[0] / (Battle.BARRAGE_CD * p.abilityCdMul) else -1f
        rallyBtn.visible = !modal && p.rallyUnlocked
        rallyBtn.label = ""
        rallyBtn.iconId = Icons.FLAG; rallyBtn.iconTint = Ui.PLAYER
        rallyBtn.sub = ""
        rallyBtn.enabled = battle.abilityReady(p, 1)
        rallyBtn.progress = if (p.abilityCd[1] > 0f) 1f - p.abilityCd[1] / (Battle.RALLY_CD * p.abilityCdMul) else -1f
        rallyBtn.highlight = p.rallyT > 0f
        stanceBtn.visible = !modal
        stanceBtn.label = if (p.hold) L["hold"] else L["advance"]
        stanceBtn.iconId = if (p.hold) Icons.SHIELD else Icons.ADVANCE
        stanceBtn.iconTint = Ui.PLAYER
        stanceBtn.highlight = p.hold
        speedBtn.visible = !modal
        speedBtn.iconId = Icons.SPEED
        speedBtn.label = speedLabels[speed]
        evolveBtn.visible = !modal
        val canEvo = battle.canEvolve(p)
        evolveBtn.enabled = canEvo
        evolveBtn.highlight = canEvo
        evolveBtn.iconId = if (p.era >= cfg.maxEra) Icons.CHEVRONS else Icons.EVOLVE
        evolveBtn.label = if (p.era >= cfg.maxEra) L.f("rank", p.rank) else L["evolve"]
        if (p.era >= cfg.maxEra) evolveBtn.enabled = false
        pauseBtn.visible = !finished
        pauseBtn.iconId = Icons.PAUSE
        pauseBtn.label = ""

        for (b in pauseMenu) b.visible = paused && !finished
        pauseMenu[0].label = L["resume"]; pauseMenu[1].label = L["restart"]; pauseMenu[2].label = L["quit"]
        pauseMenu[0].iconId = Icons.PLAY; pauseMenu[1].iconId = Icons.RESTART; pauseMenu[2].iconId = Icons.WHITE_FLAG
        pauseMenu[0].highlight = true; pauseMenu[2].color = Ui.BTN_RED
        continueBtn.visible = finished && resultTime > 0.8f
        continueBtn.label = L["continue"]
        continueBtn.iconId = Icons.PLAY
        continueBtn.highlight = true
        restartEnd.visible = finished && resultTime > 0.8f
        restartEnd.label = L["restart"]
        restartEnd.iconId = Icons.RESTART

        val choiceOpen = battle.pendingChoice != null || battle.pendingCards != null
        for (i in 0..2) {
            doctrineBtn[i].visible = choiceOpen && !paused
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
                slotBuy[i].label = "${L["buy"]} ${p.turretCost()}"
                slotBuy[i].iconId = Icons.COIN
                slotBuy[i].enabled = p.gold >= p.turretCost()
                slotBuy[i].highlight = slotBuy[i].enabled
            } else {
                val uc = battle.upgradeCost(p, i)
                slotBuy[i].label = if (uc < 0) "" else "${L["upgrade"]} $uc"
                slotBuy[i].iconId = if (uc < 0) Icons.CHECK else Icons.EVOLVE
                slotBuy[i].enabled = uc >= 0 && p.gold >= uc
                slotBuy[i].highlight = slotBuy[i].enabled
            }
            slotSell[i].label = "${L["sell"]} ${if (te >= 0) p.turretCost(te) / 2 else 0}"
            slotSell[i].iconId = Icons.COIN
            slotSell[i].color = Ui.BTN_RED
        }
        closeBtn.visible = tp
    }

    /** Teksty kosztów są cache'owane, żeby nie tworzyć nowych Stringów w każdej klatce. */
    private val costCache = IntArray(5) { -1 }
    private val costText = Array(5) { "" }
    private var armoryLv = -1
    /** Indeks = mnożnik prędkości (1, 3, 5; zob. onClick SPEED). */
    private val speedLabels = arrayOf("", "×1", "×2", "×3", "×4", "×5")

    private fun costStr(slot: Int, v: Int): String {
        if (costCache[slot] != v) { costCache[slot] = v; costText[slot] = v.toString() }
        return costText[slot]
    }


    override fun onClick(b: Button) {
        val p = battle.player
        val t = tut
        if (t != null && (t.onClick(b) || !t.allows(b))) return
        when (b.id) {
            in UNIT0..UNIT0 + 3 -> battle.queueUnit(p, roles[b.id - UNIT0])
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
            RESTART, RESTART + 100 -> {
                closed = true
                g.snapshots?.delete()
                g.setScreen(BattleScreen(g, cfg.copyWithSeed(), false))
            }
            QUIT -> { paused = false; battle.surrender() }
            CONTINUE -> g.setScreen(nextScreen())
            in DOCTRINE0..DOCTRINE0 + 2 -> {
                if (battle.pendingCards != null) battle.chooseEndlessCard(b.id - DOCTRINE0)
                else battle.chooseDoctrine(b.id - DOCTRINE0)
            }
            in SLOT_BUY0 until SLOT_BUY0 + Defs.MAX_TURRET_SLOTS -> {
                val i = b.id - SLOT_BUY0
                if (p.turretEra[i] < 0) battle.buyTurret(p, i) else battle.upgradeTurret(p, i)
            }
            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> battle.sellTurret(p, b.id - SLOT_SELL0)
        }
    }

    private fun BattleConfig.copyWithSeed() = BattleConfig(mode, if (mode == Mode.DAILY) seed else seed + 1, aiPower, startEra, maxEra, modifiers, playerBonuses, stageIndex,
        tutorial && !g.profile.tutorialDone)

    private fun nextScreen(): Screen = when (cfg.mode) {
        Mode.CAMPAIGN -> CampaignScreen(g)
        Mode.ENDLESS, Mode.DAILY, Mode.SKIRMISH -> ModeScreen(g, cfg.mode)
    }

    override fun onDrag(dx: Float, dy: Float) {
        if (finished || paused) return
        r.camX -= dx / r.k
        r.clampCam()
        manualCam = 4f
        tut?.onCamera()
    }

    override fun onTap(x: Float, y: Float) {
        if (mini.width() > 0f && x in mini.left..mini.right && y in (mini.top - 10 * dp)..(mini.bottom + 10 * dp)) {
            r.camX = (x - mini.left) / mini.width() * Defs.LANE - r.worldVisible / 2
            r.clampCam(); manualCam = 5f
            tut?.onCamera()
            return
        }
        if (showTurrets && !panel.contains(x, y)) showTurrets = false
    }

    override fun onBack(): Boolean {
        if (finished) { g.setScreen(nextScreen()); return true }
        if (showTurrets) { showTurrets = false; return true }
        paused = !paused
        return true
    }

    /**
     * Pauza aplikacji (albo zmiana ekranu). Zrzut zapisujemy synchronicznie: MainActivity
     * czeka na to, zanim zatrzyma wątek gry, bo proces może zaraz zostać zabity.
     */
    override fun onPause() {
        if (finished || closed || battle.over) return
        paused = true
        g.snapshots?.saveNow(snapshotBytes())
    }

    private fun snapshotBytes(): ByteArray = BattleSnapshot.encode(battle, rankedDaily, r.camX)

    // ---------------------------------------------------------------- koniec bitwy

    private fun onFinished() {
        finished = true
        // Najpierw usuń zrzut, potem przyznaj nagrody: wznowienie nie może dać ich drugi raz.
        g.snapshots?.delete()
        val pr = g.profile
        val won = battle.winner == 0
        pr.battles++
        if (won) pr.wins++
        pr.totalKills += battle.player.kills
        pr.playSeconds += battle.time.toLong()
        val day = Daily.dayNumber()
        val score = battle.score()
        // Nagroda bazowa za porażkę/trening rośnie z czasem bitwy (pełna po 4 min symulacji),
        // inaczej natychmiastowa kapitulacja w pętli byłaby najszybszym sposobem na medale.
        val effort = (battle.time / 240f).coerceIn(0f, 1f)
        when (cfg.mode) {
            Mode.CAMPAIGN -> {
                val stage = cfg.stageIndex
                val old = pr.stars[stage]
                val st = battle.stars()
                reward = if (won) 20 + stage * 6 + st * 5 + max(0, st - old) * 12 else ((5 + stage * 2) * effort).toInt() + min(20, battle.player.kills / 8)
                if (st > old) {
                    pr.stars[stage] = st
                    newRecord = pr.submit(Profile.BOARD_CAMPAIGN, pr.totalStars().toLong(), day)
                    g.online.submit(Profile.BOARD_CAMPAIGN, pr.totalStars().toLong())
                }
                if (won) pr.tutorialDone = true
            }
            Mode.SKIRMISH -> {
                reward = if (won) 15 + pr.skirmishDiff * 14 else (4 * effort).toInt() + battle.player.kills / 12
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
                } else reward = (5 * effort).toInt()
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
        g.sfx(if (won || cfg.mode == Mode.ENDLESS) Sound.VICTORY else Sound.DEFEAT, 1f, 0)
    }

    // ---------------------------------------------------------------- rysowanie

    private val pausePanel = RectF()
    private val resultPanel = RectF()
    private val tmpR = RectF()
    private val actionBtns get() = actionArr
    private val actionArr = arrayOf(turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn)
    private var goldShown = -1
    private var goldText = ""
    private var capKey = -1
    private var capText = ""
    private var hpP = -1
    private var hpPText = ""
    private var hpE = -1
    private var hpEText = ""

    private fun doctrineIcon(d: Doctrine) = when (d) {
        Doctrine.ECONOMY -> Icons.COINS
        Doctrine.SIEGE -> Icons.BOMB
        Doctrine.BULWARK -> Icons.SHIELD
        Doctrine.SWIFT -> Icons.BOLT
        Doctrine.VETERAN -> Icons.CHEVRONS
        Doctrine.FURY -> Icons.FLAME
        Doctrine.ARSENAL -> Icons.TOWER
        Doctrine.TACTICS -> Icons.HOURGLASS
        Doctrine.PLUNDER -> Icons.BAG
    }


    private fun endlessIcon(card: EndlessCard): Int = when (card) {
        EndlessCard.ECONOMY, EndlessCard.EXTRA_GOLD -> Icons.COINS
        EndlessCard.SIEGE, EndlessCard.AUTO_BARRAGE -> Icons.BOMB
        EndlessCard.BULWARK, EndlessCard.FORTRESS, EndlessCard.FRONTLINE_PLATE -> Icons.SHIELD
        EndlessCard.SWIFT, EndlessCard.MARCH, EndlessCard.DRILL_SERGEANT -> Icons.BOLT
        EndlessCard.VETERAN, EndlessCard.ELITE_SCHOOL -> Icons.CHEVRONS
        EndlessCard.FURY, EndlessCard.BERSERK -> Icons.FLAME
        EndlessCard.ARSENAL, EndlessCard.TOWER_SLOT, EndlessCard.RAPID_TOWERS -> Icons.TOWER
        EndlessCard.TACTICS, EndlessCard.RALLY_MASTER -> Icons.HOURGLASS
        EndlessCard.PLUNDER, EndlessCard.BLOOD_GOLD, EndlessCard.SCAVENGER -> Icons.BAG
        EndlessCard.FREE_ELITE, EndlessCard.MOBILIZATION -> Icons.HELMET
        EndlessCard.BURNING_TOWERS -> Icons.METEOR
        EndlessCard.CLONING -> Icons.PERSON
        EndlessCard.MEDIC, EndlessCard.REPAIR_CREW -> Icons.HEART
        EndlessCard.SHARPSHOOTERS -> Icons.CROSSHAIR
        EndlessCard.LAST_STAND -> Icons.CROWN
    }

    private fun rarityLabel(r: EndlessRarity): String = L[when (r) {
        EndlessRarity.COMMON -> "er_common"
        EndlessRarity.UNCOMMON -> "er_uncommon"
        EndlessRarity.RARE -> "er_rare"
        EndlessRarity.LEGENDARY -> "er_legendary"
    }]

    override fun render(c: Canvas) {
        r.render(c, if (paused || battle.pendingChoice != null || battle.pendingCards != null) 0f else frameDt * speed * (tut?.timeScale ?: 1f), w, h)
        drawTop(c)
        drawHudBack(c)
        for (b in actionArr) ui.button(c, b, 13f)
        for (b in unitBtn) ui.button(c, b, 13f)
        drawQueue(c)
        ui.button(c, evolveBtn, 15f)
        ui.button(c, pauseBtn, 15f)
        if (showTurrets && battle.pendingChoice == null && battle.pendingCards == null && !paused && !finished) drawTurretPanel(c)
        if ((battle.pendingChoice != null || battle.pendingCards != null) && !finished) drawChoices(c)
        if (!finished) tut?.draw(c)
        if (paused && !finished) {
            c.drawColor(Color.argb(150, 0, 0, 0))
            ui.panel(c, pausePanel)
            ui.ribbon(c, pausePanel.centerX(), pausePanel.top + 4 * dp, L["paused"], 22f, Ui.RIBBON, pausePanel.width() * 0.6f)
            for (b in pauseMenu) ui.button(c, b, 18f)
        }
        if (finished) drawResults(c)
    }

    private fun drawTop(c: Canvas) {
        val p = battle.player
        val e = battle.enemy
        // pasek: ciemne tło, ciemny kontur i złota lamówka na dole
        ui.fill.color = Color.argb(200, 16, 20, 32)
        c.drawRect(topBar, ui.fill)
        ui.fill.color = Ui.OUTLINE
        c.drawRect(0f, topBar.bottom, w, topBar.bottom + 2.5f * dp, ui.fill)
        ui.fill.color = Color.argb(160, 222, 170, 70)
        c.drawRect(0f, topBar.bottom - 1.5f * dp, w, topBar.bottom, ui.fill)
        val l = g.insetL + 8 * dp
        val t = g.insetT + 6 * dp
        // złoto
        val gi = p.gold.toInt()
        if (gi != goldShown) { goldShown = gi; goldText = gi.toString() }
        ui.badge(c, l, t + 11 * dp, Icons.COIN, goldText, 15f, Ui.GOLD, minTextW = ui.measure("0000", 15f, true))
        // armia
        val ck = p.aliveCount * 100 + p.cap
        if (ck != capKey) { capKey = ck; capText = "${p.aliveCount}/${p.cap}" }
        val ax = l + 118 * dp
        Icons.draw(c, Icons.HELMET, ax + 8 * dp, t + 10 * dp, 17 * dp, dp = dp)
        ui.textO(c, capText, ax + 19 * dp, t + 15 * dp, 13f, if (p.aliveCount >= p.cap) Ui.BAD else Ui.TEXT, Paint.Align.LEFT, 1.8f)
        // XP: klejnot + pasek z nazwą epoki
        val need = if (p.era < cfg.maxEra) Defs.evolveXp[p.era].toFloat() else battle.rankXp(p)
        val bx = l + 22 * dp
        val bw = 160 * dp
        ui.bar(c, bx, t + 28 * dp, bw, 11 * dp, p.xp / need, Ui.XP)
        Icons.draw(c, Icons.XP, l + 10 * dp, t + 33.5f * dp, 19 * dp, dp = dp)
        ui.textO(c, L[Defs.eraKeys[p.era]], bx + bw / 2, t + 37 * dp, 10f, Ui.TEXT, Paint.Align.CENTER, 1.8f)
        // HP baz po bokach minimapy
        if (mini.width() > 0f) {
            val hw = mini.width() * 0.5f - 14 * dp
            val hy = mini.top - 11 * dp
            ui.bar(c, mini.left + 12 * dp, hy, hw, 7 * dp, p.baseHp / p.baseMaxHp, Ui.PLAYER)
            Icons.draw(c, Icons.TOWER, mini.left + 4 * dp, hy + 3 * dp, 16 * dp, Ui.PLAYER, dp = dp)
            if (!battle.endless) {
                ui.bar(c, mini.right - 12 * dp - hw, hy, hw, 7 * dp, e.baseHp / e.baseMaxHp, Ui.ENEMY)
                Icons.draw(c, Icons.TOWER, mini.right - 4 * dp, hy + 3 * dp, 16 * dp, Ui.ENEMY, dp = dp)
            }
            // ramka minimapy
            tmpR.set(mini.left - 2 * dp, mini.top - 2 * dp, mini.right + 2 * dp, mini.bottom + 2 * dp)
            ui.fill.color = Ui.OUTLINE
            c.drawRoundRect(tmpR, 6 * dp, 6 * dp, ui.fill)
            ui.fill.color = Color.rgb(34, 46, 40)
            c.drawRoundRect(mini, 4 * dp, 4 * dp, ui.fill)
            ui.fill.color = Color.rgb(54, 72, 50)
            c.drawRect(mini.left + 2 * dp, mini.centerY() + 2 * dp, mini.right - 2 * dp, mini.bottom - 2 * dp, ui.fill)
            ui.fill.color = Ui.PLAYER
            c.drawRect(mini.left + 2 * dp, mini.top + 3 * dp, mini.left + 5 * dp, mini.bottom - 3 * dp, ui.fill)
            if (!battle.endless) {
                ui.fill.color = Ui.ENEMY
                c.drawRect(mini.right - 5 * dp, mini.top + 3 * dp, mini.right - 2 * dp, mini.bottom - 3 * dp, ui.fill)
            }
            // widoczny obszar
            ui.stroke.color = Color.argb(220, 255, 236, 170)
            ui.stroke.strokeWidth = 1.5f * dp
            val vl = mini.left + (r.camX / Defs.LANE) * mini.width()
            val vr = mini.left + ((r.camX + r.worldVisible) / Defs.LANE) * mini.width()
            c.drawRoundRect(max(mini.left, vl), mini.top + 1 * dp, min(mini.right, vr), mini.bottom - 1 * dp, 3 * dp, 3 * dp, ui.stroke)
            for (u in battle.units) {
                if (!u.alive) continue
                if (battle.fog && u.side == 1 && u.x - p.front > 650f) continue
                val x = r.worldToMini(u.x, mini.left, mini.width())
                val rad = 2.6f * dp * u.def.size.coerceAtMost(1.4f)
                ui.fill.color = Ui.OUTLINE
                c.drawCircle(x, mini.centerY(), rad + 1 * dp, ui.fill)
                ui.fill.color = if (u.side == 0) Ui.PLAYER else Ui.ENEMY
                c.drawCircle(x, mini.centerY(), rad, ui.fill)
            }
            val pi = p.baseHp.toInt()
            if (pi != hpP) { hpP = pi; hpPText = pi.toString() }
            ui.textO(c, hpPText, mini.left, mini.bottom + 14 * dp, 11f, Ui.PLAYER, Paint.Align.LEFT, 1.8f)
            val rightLabel = if (battle.endless) {
                if (battle.wave != waveShown) { waveShown = battle.wave; waveText = L.f("wave", battle.wave) }
                waveText
            } else {
                val ei = e.baseHp.toInt()
                if (ei != hpE) { hpE = ei; hpEText = ei.toString() }
                hpEText
            }
            ui.textO(c, rightLabel, mini.right, mini.bottom + 14 * dp, 11f, if (battle.endless) Ui.GOLD else Ui.ENEMY, Paint.Align.RIGHT, 1.8f)
            val sp = battle.siegePressure()
            if (sp > 1f) {
                val k = kotlin.math.round(sp * 10f).toInt()
                if (k != siegeKey) { siegeKey = k; siegeText = L.f("siege_pressure", sp) }
                ui.textO(c, siegeText, mini.centerX(), mini.bottom + 14 * dp, 11f, Ui.BAD, Paint.Align.CENTER, 1.8f)
            } else ui.textO(c, timeStr(battle.time), mini.centerX(), mini.bottom + 14 * dp, 11f, Ui.TEXT, Paint.Align.CENTER, 1.8f)
        }
        if (battle.endless && battle.pendingCards == null && battle.pendingChoice == null) {
            val boss = battle.currentBoss()
            if (boss != null) {
                val bw = min(260 * dp, w - g.insetL - g.insetR - 30 * dp)
                val bx = (w - bw) * 0.5f
                ui.textO(c, if (boss.bossKind == 1) L["boss_knockback"] else L["boss_aura"],
                    w * 0.5f, topBar.bottom + 18 * dp, 12f, Ui.ENEMY, Paint.Align.CENTER, 1.7f)
                ui.bar(c, bx, topBar.bottom + 23 * dp, bw, 8 * dp,
                    boss.hp / boss.maxHp, Ui.ENEMY)
            }
            if (battle.wave != nextThemeShown) {
                nextThemeShown = battle.wave
                nextThemeText = L["next_wave"] + " " + L[battle.nextWaveKey]
            }
            ui.textO(c, nextThemeText, w * 0.5f,
                topBar.bottom + (if (boss == null) 20f else 47f) * dp,
                11f, Ui.GOLD, Paint.Align.CENTER, 1.7f)
        }
        // przeciwnik (epoka) + doktryny gracza jako ikony
        val ex = pauseBtn.r.left - 10 * dp
        ui.textO(c, L[Defs.eraKeys[e.era]], ex, t + 15 * dp, 12f, Ui.ENEMY, Paint.Align.RIGHT, 2f)
        if (p.doctrines.isNotEmpty()) {
            val isz = 15 * dp
            var x = ex - isz / 2
            for (i in p.doctrines.indices.reversed()) {
                if (x < (if (mini.width() > 0f) mini.right + 10 * dp else evolveBtn.r.right + 10 * dp)) break
                Icons.draw(c, doctrineIcon(p.doctrines[i]), x, t + 31 * dp, isz, dp = dp)
                x -= isz + 3 * dp
            }
        }
    }

    private var timeSec = -1
    private var timeText = ""
    private var siegeKey = -1
    private var siegeText = ""
    private var waveShown = -1
    private var waveText = ""
    private var nextThemeShown = -1
    private var nextThemeText = ""

    /** Formatowanie raz na sekundę zamiast w każdej klatce (String.format alokuje). */
    private fun timeStr(t: Float): String {
        val s = t.toInt()
        if (s != timeSec) { timeSec = s; timeText = "%d:%02d".format(s / 60, s % 60) }
        return timeText
    }

    private fun drawHudBack(c: Canvas) {
        val top = h - hudH
        ui.fill.color = Color.argb(190, 16, 20, 32)
        c.drawRect(0f, top, w, h, ui.fill)
        ui.fill.color = Ui.OUTLINE
        c.drawRect(0f, top - 2.5f * dp, w, top, ui.fill)
        ui.fill.color = Color.argb(160, 222, 170, 70)
        c.drawRect(0f, top, w, top + 1.5f * dp, ui.fill)
        // separator między jednostkami a akcjami
        val sx = (unitBtn[3].r.right + turretBtn.r.left) / 2
        ui.fill.color = Color.argb(90, 255, 255, 255)
        c.drawRect(sx - 1 * dp, unitBtn[0].r.top + 6 * dp, sx + 1 * dp, unitBtn[0].r.bottom - 6 * dp, ui.fill)
    }

    private fun drawQueue(c: Canvas) {
        val p = battle.player
        if (p.queueSize == 0 || !unitBtn[0].visible) return
        val y = unitBtn[0].r.top - 10 * dp
        for (i in 0 until p.queueSize) {
            val x = unitBtn[0].r.left + 9 * dp + i * 16 * dp
            ui.fill.color = Ui.OUTLINE
            c.drawCircle(x, y, 6.5f * dp, ui.fill)
            ui.fill.color = if (i == 0) Ui.GOLD else Color.rgb(220, 224, 232)
            c.drawCircle(x, y, 4.5f * dp, ui.fill)
        }
    }

    private fun drawTurretPanel(c: Canvas) {
        val p = battle.player
        ui.panel(c, panel)
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, "${L["turrets"]} — ${L[Defs.eras[p.era].turret.nameKey]}", 16f,
            Ui.RIBBON, panel.width() * 0.5f, Ui.GOLD, panel.width() - 40 * dp)
        for (i in 0 until p.slots) {
            val b = slotBuy[i]
            val te = p.turretEra[i]
            tmpR.set(panel.left + 14 * dp, b.r.top - 2 * dp, panel.right - 14 * dp, b.r.bottom + 2 * dp)
            ui.fill.color = if (i % 2 == 0) Color.argb(34, 255, 255, 255) else Color.argb(14, 255, 255, 255)
            c.drawRoundRect(tmpR, 8 * dp, 8 * dp, ui.fill)
            val cy = b.r.centerY()
            val mr = b.r.height() * 0.5f
            ui.medallion(c, panel.left + 20 * dp + mr, cy, mr, if (te >= 0) Ui.ERA[te] else Color.rgb(60, 64, 78), if (te >= 0) Icons.TOWER else -1, Ui.TRIM, Ui.PLAYER)
            if (te < 0) ui.textO(c, (i + 1).toString(), panel.left + 20 * dp + mr, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            val tx = panel.left + 28 * dp + mr * 2
            val name = if (te >= 0) L[Defs.eras[te].turret.nameKey] else L["empty"]
            val avail = b.r.left - tx - 6 * dp
            ui.textO(c, L.f("slot", i + 1), tx, cy - 3 * dp, 10f, Ui.DIM, Paint.Align.LEFT, 1.5f)
            ui.textO(c, name, tx, cy + 12 * dp, ui.fit(name, 13f, avail), if (te >= 0) Ui.TEXT else Ui.DIM, Paint.Align.LEFT, 1.8f)
            ui.button(c, slotBuy[i], 13f)
            ui.button(c, slotSell[i], 13f)
        }
        ui.closeButton(c, closeBtn)
    }


    private fun drawChoices(c: Canvas) {
        val cards = battle.pendingCards
        val doctrines = battle.pendingChoice
        if (cards == null && doctrines == null) return
        c.drawColor(Color.argb(160, 0, 0, 0))
        ui.ribbon(c, w / 2, doctrineBtn[0].r.top - 34 * dp,
            if (cards == null) L["choose_doctrine"] else L["choose_card"],
            22f, Ui.RIBBON, 300 * dp)
        for (i in 0..2) {
            val b = doctrineBtn[i]
            val off = if (b.pressed) 3 * dp else 0f
            tmpR.set(b.r.left, b.r.top + off, b.r.right, b.r.bottom + off)
            val rarity = cards?.get(i)?.rarity
            val trim = if (rarity == null) Ui.GOLD else Ui.ERA[min(7, rarity.ordinal * 2)]
            ui.panel(c, tmpR, Color.argb(245, 40, 48, 70), 14f)
            val mr = min(33 * dp, min(b.r.width() * 0.2f, b.r.height() * 0.18f))
            val my = tmpR.top + 10 * dp + mr
            val icon = if (cards == null) doctrineIcon(doctrines!![i]) else endlessIcon(cards[i])
            ui.medallion(c, tmpR.centerX(), my, mr, trim, icon)
            val key = if (cards == null) doctrines!![i].key else cards[i].textKey
            val name = L[key]
            ui.textO(c, name, tmpR.centerX(), my + mr + 23 * dp,
                ui.fit(name, 17f, tmpR.width() - 18 * dp), Ui.GOLD, Paint.Align.CENTER)
            if (rarity != null) {
                ui.textO(c, rarityLabel(rarity), tmpR.centerX(), my + mr + 40 * dp,
                    10f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            }
            ui.wrap(c, L[key + "_d"], tmpR.centerX(), my + mr + (if (rarity != null) 56f else 45f) * dp,
                tmpR.width() - 22 * dp, 12f, Ui.TEXT, Paint.Align.CENTER)
        }
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
        val rp = resultPanel
        // wjazd panelu od dołu
        val slide = (1f - a) * 60 * dp
        c.save()
        c.translate(0f, slide)
        ui.panel(c, rp)
        val good = won || cfg.mode == Mode.ENDLESS
        ui.ribbon(c, rp.centerX(), rp.top + 2 * dp, title, 26f, if (good) Color.rgb(206, 140, 30) else Color.rgb(140, 40, 40),
            rp.width() * 0.7f, if (good) Color.rgb(255, 246, 200) else Ui.TEXT, rp.width() + 40 * dp)
        var y = rp.top + 44 * dp
        if (cfg.mode == Mode.CAMPAIGN && won) {
            val st = battle.stars()
            ui.stars(c, rp.centerX(), y + 22 * dp, 46 * dp, st, 3, 1.05f)
            y += 54 * dp
        } else {
            Icons.draw(c, if (good) Icons.TROPHY else Icons.SKULL, rp.centerX(), y + 20 * dp, 40 * dp, dp = dp)
            y += 48 * dp
        }
        val lx = rp.left + 30 * dp
        val rx = rp.right - 30 * dp
        val lh = 28 * dp
        fun row(icon: Int, k: String, v: String, col: Int = Ui.TEXT) {
            if (y + lh > restartEnd.r.top - 8 * dp) return
            tmpR.set(lx - 10 * dp, y, rx + 10 * dp, y + lh - 3 * dp)
            ui.fill.color = Color.argb(30, 255, 255, 255)
            c.drawRoundRect(tmpR, 8 * dp, 8 * dp, ui.fill)
            val cy = tmpR.centerY()
            Icons.draw(c, icon, lx + 8 * dp, cy, 18 * dp, dp = dp)
            ui.textO(c, k, lx + 24 * dp, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.LEFT, 1.6f)
            ui.textO(c, v, rx, cy + 5 * dp, 15f, col, Paint.Align.RIGHT, 1.8f)
            y += lh
        }
        row(Icons.STOPWATCH, L["time"], timeStr(battle.time))
        row(Icons.SKULL, L["kills"], battle.player.kills.toString())
        if (cfg.mode == Mode.ENDLESS) {
            row(Icons.TOWER, L.f("wave", battle.wave).substringBefore(' '), battle.wave.toString())
            row(Icons.CROWN, L["bosses_defeated"], battle.bossesDefeated.toString())
        }
        if (cfg.mode != Mode.CAMPAIGN) row(Icons.STAR, L["score"], battle.score().toString(), Ui.GOLD)
        row(Icons.MEDAL, L["reward"], "+$reward ${L["medals"]}", Ui.GOLD)
        if (newRecord && y + 26 * dp < restartEnd.r.top) {
            ui.textO(c, L["new_record"], rp.centerX(), y + 20 * dp, 18f, Ui.GOOD, Paint.Align.CENTER)
            y += 28 * dp
        }
        if (tipKey.isNotEmpty() && y + 20 * dp < restartEnd.r.top) {
            ui.wrap(c, "${L["why_lost"]}: ${L[tipKey]}", rp.centerX(), y + 18 * dp, rp.width() - 50 * dp, 13f, Ui.TEXT, Paint.Align.CENTER)
        }
        c.restore()
        ui.button(c, continueBtn, 17f)
        ui.button(c, restartEnd, 17f)
    }
}
