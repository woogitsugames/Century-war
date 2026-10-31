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
import com.centurywar.engine.TouchEvent
import com.centurywar.game.FxBuffer
import com.centurywar.game.Proj
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleController
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.BattleSnapshot
import com.centurywar.game.ReplayRecord
import com.centurywar.game.RestoredBattle
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.EndlessRarity
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.meta.Achievements
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.Missions
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
    private val commands = battle.commandController ?: BattleController(battle)
    private val r = BattleRenderer(g, battle)
    private var acc = 0f
    private var speed = 1
    private var paused = restored != null
    /** Czas bitwy ostatniego autozapisu. */
    private var lastAutosave = battle.time
    /** Ekran porzucony (restart): nie zapisuj już jego zrzutu. */
    private var closed = false
    private var manualCam = 0f
    /** Stan wyłącznie interfejsu, nie wchodzi do zapisu bitwy. */
    private var aimingBarrage = false
    private var aimWorldX = 0f
    private var aimScreenX = 0f
    private var aimDownX = 0f
    private var aimDownY = 0f
    private var aimStartedInField = false
    private var aimMoved = false
    private var showTurrets = false
    /** Indeks roli z otwartym panelem ulepszenia (-1 = zamknięty). */
    private var showUpgrade = -1
    private var finished = false
    private var surrendered = false
    private var resultTime = 0f
    private var replayBytes: ByteArray? = null
    private var replayId: String? = null


    // wynik
    private var reward = 0
    private var rewardText = ""
    private var newRecord = false
    private var tipKey = ""
    private var tipText = ""
    private var tipIcon = Icons.BOLT

    private companion object {
        /** Kolory rzadkości kart Endless: własna paleta, niezależna od liczby epok. */
        val CARD_RARITY = intArrayOf(
            android.graphics.Color.rgb(150, 104, 60), android.graphics.Color.rgb(84, 112, 150),
            android.graphics.Color.rgb(140, 112, 64), android.graphics.Color.rgb(40, 140, 160),
        )
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
        const val WATCH_REPLAY = 24
        const val DOCTRINE0 = 30 // 30..32
        const val VARIANT0 = 80 // 80..81
        const val SLOT_BUY0 = 40 // 40..43
        const val SLOT_SELL0 = 50 // 50..53
        const val CLOSE = 60
        const val UNIT_UPGRADE0 = 70 // 70..73
        const val UNIT_UPGRADE_BUY = 74
        const val UNIT_UPGRADE_CLOSE = 75
        /** Autozapis co tyle sekund czasu bitwy (zapis pliku w tle). */
        const val AUTOSAVE_EVERY = 30f
    }

    /** Role.values() alokuje nową tablicę przy każdym wywołaniu, więc trzymamy kopię. */
    private val roles = Role.values()
    /** Klucze opisów są składane raz, nie przy każdym renderowaniu modala. */
    private val doctrineDescKeys = Doctrine.values().map { it.key + "_d" }.toTypedArray()
    private val cardDescKeys = EndlessCard.values().map { it.textKey + "_d" }.toTypedArray()
    private val unitBtn = Array(4) { i -> btn(UNIT0 + i) }
    private val unitUpgrade = Array(4) { i -> btn(UNIT_UPGRADE0 + i) }
    private val upgradeBuy = btn(UNIT_UPGRADE_BUY)
    private val upgradeClose = btn(UNIT_UPGRADE_CLOSE)
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
    private val replayEnd = btn(WATCH_REPLAY)
    private val doctrineBtn = Array(3) { btn(DOCTRINE0 + it) }
    private val variantBtn = Array(2) { btn(VARIANT0 + it) }
    private val slotBuy = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_BUY0 + it) }
    private val slotSell = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_SELL0 + it) }
    private val closeBtn = btn(CLOSE)

    private val topBar = RectF()
    private val mini = RectF()
    private val goldRect = RectF()
    private val xpRect = RectF()
    private val fieldRect = RectF()
    private val panel = RectF()
    private val upgradePanel = RectF()
    private val upgradeRoleKeys = arrayOf("unit_upgrade_melee", "unit_upgrade_ranged", "unit_upgrade_heavy", "unit_upgrade_elite")
    private var upgradeRoleShown = -1
    private var upgradeTierShown = -1
    private var upgradeTierText = ""
    private var hudH = 0f
    private var topH = 0f

    init {
        for (i in 0..3) {
            val role = roles[i]
            unitBtn[i].icon = { c, rr ->
                val def = battle.player.unit(role)
                r.drawUnitIcon(c, def, rr.centerX(), rr.bottom - 18 * dp, rr.height() - 26 * dp,
                    Ui.PLAYER, battle.player.unitTier[role.ordinal])
            }
        }
        // Strzałka ma pierwszeństwo przed większym przyciskiem jednostki pod spodem.
        for (i in 3 downTo 0) {
            buttons.remove(unitUpgrade[i])
            buttons.add(0, unitUpgrade[i])
        }
        r.camX = restored?.camX ?: -120f
    }

    /** Interaktywny samouczek (tylko etap 1 kampanii, dopóki gracz go nie ukończy/pominie). */
    private val tut: TutorialOverlay? =
        if (restored == null && cfg.tutorial && cfg.mode == Mode.CAMPAIGN && !g.profile.tutorialDone) TutorialOverlay(g, battle, commands, TutorialOverlay.Targets(
            unitBtn, evolveBtn, turretBtn, barrageBtn, slotBuy, doctrineBtn,
            arrayOf(unitBtn[0], unitBtn[1], unitBtn[2], unitBtn[3], turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn, evolveBtn),
            goldRect, xpRect, mini, fieldRect, { showTurrets }, { showTurrets = false },
            { aimingBarrage }, { x -> r.screenX(x) }, { r.groundY }, { r.k },
        )) else null

    /** Dymek nie wykonuje komend i nie modyfikuje tempa symulacji. */
    private val hints = HintOverlay(g, battle, HintOverlay.Targets(
        unitUpgrade, armoryBtn, rallyBtn, stanceBtn, evolveBtn, turretBtn,
        goldRect, xpRect, mini, fieldRect,
    ), rankedDaily)

    init {
        // OK oraz przyciski samouczka mają pierwszeństwo przed HUD.
        tut?.let { buttons.add(0, it.nextBtn); buttons.add(0, it.skipBtn) }
        buttons.add(0, hints.okBtn)
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
        val arrow = min(24 * dp, size * 0.36f)
        for (i in 0..3) unitUpgrade[i].set(unitBtn[i].r.right - arrow, y, arrow, arrow)
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
        val freeW = w - g.insetL - g.insetR - 50 * dp
        val vcw = min(285 * dp, (freeW - 12 * dp) / 2f)
        val vch = min(305 * dp, (h - g.insetT - g.insetB) * 0.77f)
        for (i in 0..1) variantBtn[i].set(w / 2 - vcw - 6 * dp + i * (vcw + 12 * dp),
            (h + g.insetT - g.insetB - vch) / 2f, vcw, vch)
        layoutTurretPanel()
        layoutUpgradePanel()
    }

    private fun layoutResults() {
        val rw = min(460 * dp, w - g.insetL - g.insetR - 40 * dp)
        val rh = min(h - g.insetT - g.insetB - 40 * dp, 400 * dp)
        resultPanel.set(w / 2 - rw / 2, h / 2 - rh / 2 + 16 * dp, w / 2 + rw / 2, h / 2 + rh / 2 + 16 * dp)
        val bw = (rw - 56 * dp) / 3
        val bh = 50 * dp
        restartEnd.set(resultPanel.left + 16 * dp, resultPanel.bottom - bh - 22 * dp, bw, bh)
        replayEnd.set(restartEnd.r.right + 4 * dp, restartEnd.r.top, bw, bh)
        continueBtn.set(replayEnd.r.right + 4 * dp, restartEnd.r.top, bw, bh)
    }

    private fun layoutUpgradePanel() {
        val pw = min(425 * dp, w - g.insetL - g.insetR - 32 * dp)
        val ph = min(260 * dp, h - g.insetT - g.insetB - 20 * dp)
        val cy = (g.insetT + h - g.insetB) / 2f
        upgradePanel.set(w / 2 - pw / 2, cy - ph / 2, w / 2 + pw / 2, cy + ph / 2)
        upgradeBuy.set(upgradePanel.left + 25 * dp, upgradePanel.bottom - 54 * dp, pw - 50 * dp, 40 * dp)
        upgradeClose.set(upgradePanel.right - 28 * dp, upgradePanel.top - 12 * dp, 38 * dp, 38 * dp)
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

    override fun onLanguageChanged() {
        // Napisy bitwy są buforowane według wartości, nie języka.
        waveShown = -1
        nextThemeShown = -1
        siegeKey = -1
        upgradeRoleShown = -1
        tut?.onLanguageChanged()
        super.onLanguageChanged()
    }

    private var frameDt = 0f

    override fun update(dt: Float) {
        frameDt = dt
        if (aimingBarrage && (paused || finished || battle.over ||
                battle.pendingChoice != null || battle.pendingCards != null ||
                showUpgrade >= 0 || !battle.abilityReady(battle.player, 0))) {
            aimingBarrage = false
        }
        val tutScale = tut?.timeScale ?: 1f
        if (!paused && !finished) {
            tut?.update(dt)
            if (battle.pendingChoice == null && battle.pendingCards == null && battle.pendingVariant == null) {
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
        // Muzyka śledzi faktyczną epokę gracza i zagrożenie przy jego bazie.
        val music = g.music
        if (music != null) {
            music.setEra(battle.player.era)
            var threat = 0f
            if (!paused && !finished && battle.pendingChoice == null) {
                for (u in battle.units) {
                    if (!u.alive || u.side != 1) continue
                    val near = (1f - (u.x - battle.player.baseFront) / (Defs.LANE * 0.35f)).coerceIn(0f, 1f)
                    if (near > threat) threat = near
                }
            }
            music.setIntensity(threat)
        }
        BattleSfx.play(g, battle.fx, r.camX, r.worldVisible)
        if (finished) resultTime += dt
        if (manualCam > 0f) manualCam -= dt else if (!finished) r.follow(dt)
        refreshButtons()
        val criticalDaily = rankedDaily && (battle.player.baseHp <= battle.player.baseMaxHp * 0.18f ||
            battle.enemy.baseHp <= battle.enemy.baseMaxHp * 0.18f)
        hints.update(dt, finished || paused || (tut != null && !tut.finished) ||
            battle.pendingChoice != null || battle.pendingCards != null ||
            showTurrets || showUpgrade >= 0 || criticalDaily)
    }

    private var turretSlotsLaid = -1

    private fun refreshButtons() {
        val p = battle.player
        if (p.slots != turretSlotsLaid) layoutTurretPanel()
        val modal = paused || finished || battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null || showUpgrade >= 0
        for (i in 0..3) {
            val role = roles[i]
            val def = p.unit(role)
            val b = unitBtn[i]
            b.visible = !modal
            val locked = role == Role.ELITE && !p.eliteUnlocked
            b.enabled = !locked && battle.canQueue(p, role)
            b.sub = if (locked) "" else costStr(i, def.cost)
            b.subIcon = if (locked) Icons.LOCK else Icons.COIN
            b.progress = if (p.queueSize > 0 && p.queue[0] == def.id) p.trainT / def.trainTime else -1f
            val up = unitUpgrade[i]
            up.visible = !modal && !locked && (tut == null || tut.finished) && p.unitTier[i] < Defs.MAX_UNIT_TIER
            up.enabled = true // Dostęp do opisu także wtedy, gdy brakuje złota.
            up.iconId = Icons.CHEVRONS
            up.iconTint = Ui.GOLD
            up.highlight = battle.canUpgradeUnit(p, role)
        }
        upgradeBuy.visible = showUpgrade >= 0 && !paused && !finished && battle.pendingChoice == null
        upgradeClose.visible = upgradeBuy.visible
        if (showUpgrade >= 0) {
            val role = roles[showUpgrade]
            val tier = p.unitTier[role.ordinal]
            if (showUpgrade != upgradeRoleShown || tier != upgradeTierShown) {
                upgradeRoleShown = showUpgrade
                upgradeTierShown = tier
                upgradeTierText = L.f("unit_upgrade_tier", tier, min(Defs.MAX_UNIT_TIER, tier + 1))
            }
            val cost = battle.unitUpgradeCost(p, role)
            upgradeBuy.label = if (cost < 0) L["unit_upgrade_max"] else L["upgrade"]
            upgradeBuy.sub = if (cost < 0) "" else costStr(5, cost)
            upgradeBuy.subIcon = if (cost < 0) -1 else Icons.COIN
            upgradeBuy.enabled = battle.canUpgradeUnit(p, role)
            upgradeBuy.highlight = upgradeBuy.enabled
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
        if (modal || !barrageBtn.enabled) aimingBarrage = false
        barrageBtn.selected = aimingBarrage
        barrageBtn.highlight = aimingBarrage
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
        replayEnd.visible = finished && resultTime > 0.8f && replayBytes != null
        replayEnd.label = L["replay"]
        replayEnd.iconId = Icons.PLAY

        val choiceOpen = battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null
        for (i in 0..2) {
            doctrineBtn[i].visible = choiceOpen && battle.pendingVariant == null && !paused
            doctrineBtn[i].label = ""
        }
        for (i in 0..1) {
            variantBtn[i].visible = battle.pendingVariant != null && !paused && !finished
            variantBtn[i].label = ""
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
            slotSell[i].label = "${L["sell"]} ${if (te >= 0) p.turretCost(te, p.turretVariant[i]) / 2 else 0}"
            slotSell[i].iconId = Icons.COIN
            slotSell[i].color = Ui.BTN_RED
        }
        closeBtn.visible = tp
    }

    /** Teksty kosztów są cache'owane, żeby nie tworzyć nowych Stringów w każdej klatce. */
    private val costCache = IntArray(6) { -1 }
    private val costText = Array(6) { "" }
    private var armoryLv = -1
    /** Indeks = mnożnik prędkości (1, 3, 5; zob. onClick SPEED). */
    private val speedLabels = arrayOf("", "×1", "×2", "×3", "×4", "×5")

    private fun costStr(slot: Int, v: Int): String {
        if (costCache[slot] != v) { costCache[slot] = v; costText[slot] = v.toString() }
        return costText[slot]
    }


    override fun onClick(b: Button) {
        if (aimingBarrage && b.id != BARRAGE) aimingBarrage = false
        val p = battle.player
        if (hints.onClick(b)) return
        val t = tut
        if (t != null && (t.onClick(b) || !t.allows(b))) return
        hints.onAction()
        when (b.id) {
            in UNIT0..UNIT0 + 3 -> commands.queue(roles[b.id - UNIT0])
            in UNIT_UPGRADE0..UNIT_UPGRADE0 + 3 -> {
                showTurrets = false
                showUpgrade = b.id - UNIT_UPGRADE0
            }
            UNIT_UPGRADE_BUY -> if (showUpgrade in 0..3 && commands.upgradeUnit(roles[showUpgrade])) g.haptic(35)
            UNIT_UPGRADE_CLOSE -> showUpgrade = -1
            TURRET -> showTurrets = !showTurrets
            CLOSE -> showTurrets = false
            ARMORY -> commands.armory()
            BARRAGE -> {
                if (aimingBarrage) {
                    commands.barrage() // Drugie dotknięcie: szybki ostrzał bez wyboru celu.
                    aimingBarrage = false
                } else if (battle.abilityReady(p, 0)) {
                    showTurrets = false
                    aimingBarrage = true
                    aimScreenX = w * 0.5f
                    aimWorldX = r.worldX(aimScreenX)
                }
            }
            RALLY -> commands.rally()
            STANCE -> commands.stance(!p.hold)
            SPEED -> speed = when (speed) { 1 -> 3; 3 -> 5; else -> 1 }
            EVOLVE -> if (commands.evolve()) g.haptic(40)
            PAUSE -> paused = true
            RESUME -> paused = false
            RESTART, RESTART + 100 -> {
                closed = true
                g.snapshots?.delete()
                g.setScreen(BattleScreen(g, cfg.copyWithSeed(), false))
            }
            QUIT -> { paused = false; surrendered = commands.surrender() }
            CONTINUE -> g.setScreen(nextScreen())
            WATCH_REPLAY -> replayBytes?.let { g.push(ReplayScreen(g, it)) }
            in DOCTRINE0..DOCTRINE0 + 2 -> {
                if (battle.pendingCards != null) commands.card(b.id - DOCTRINE0)
                else commands.doctrine(b.id - DOCTRINE0)
            }
            in VARIANT0..VARIANT0 + 1 -> if (commands.variant(b.id - VARIANT0)) g.haptic(40)
            in SLOT_BUY0 until SLOT_BUY0 + Defs.MAX_TURRET_SLOTS -> {
                val i = b.id - SLOT_BUY0
                if (p.turretEra[i] < 0) commands.buyTurret(i) else commands.upgradeTurret(i)
            }
            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> commands.sellTurret(b.id - SLOT_SELL0)
        }
    }

    private fun BattleConfig.copyWithSeed() = BattleConfig(mode, if (mode == Mode.DAILY) seed else seed + 1, aiPower, startEra, maxEra, modifiers, playerBonuses, stageIndex,
        tutorial && !g.profile.tutorialDone, aiPersonality, variantsEnabled)

    private fun nextScreen(): Screen = when (cfg.mode) {
        Mode.CAMPAIGN -> CampaignScreen(g)
        Mode.ENDLESS, Mode.DAILY, Mode.SKIRMISH -> ModeScreen(g, cfg.mode)
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (aimingBarrage) {
            when (e.action) {
                TouchEvent.DOWN -> {
                    aimDownX = e.x
                    aimDownY = e.y
                    aimMoved = false
                    aimStartedInField = fieldRect.contains(e.x, e.y)
                }
                TouchEvent.MOVE -> if (kotlin.math.abs(e.x - aimDownX) > 10f * dp ||
                    kotlin.math.abs(e.y - aimDownY) > 10f * dp) aimMoved = true
            }
            if ((e.action == TouchEvent.DOWN || e.action == TouchEvent.MOVE) &&
                aimStartedInField && fieldRect.contains(e.x, e.y)) {
                aimScreenX = e.x
                aimWorldX = r.worldX(e.x)
            }
        }
        return false // Ekran nadal rozpoznaje zwykły tap i przeciąganie.
    }

    override fun onDrag(dx: Float, dy: Float) {
        if (finished || paused) return
        r.camX -= dx / r.k
        r.clampCam()
        if (aimingBarrage) aimWorldX = r.worldX(aimScreenX)
        manualCam = 4f
        tut?.onCamera()
    }

    override fun onTap(x: Float, y: Float) {
        if (aimingBarrage) {
            if (!paused && !finished && !aimMoved && aimStartedInField &&
                battle.abilityReady(battle.player, 0) &&
                fieldRect.contains(x, y) && (!showTurrets || !panel.contains(x, y))) {
                commands.barrage(r.worldX(x))
            }
            aimingBarrage = false // Dotknięcie poza polem walki anuluje.
            return
        }
        if (showUpgrade >= 0) {
            if (!upgradePanel.contains(x, y)) showUpgrade = -1
            return
        }
        if (mini.width() > 0f && x in mini.left..mini.right && y in (mini.top - 10 * dp)..(mini.bottom + 10 * dp)) {
            r.camX = (x - mini.left) / mini.width() * Defs.LANE - r.worldVisible / 2
            r.clampCam(); manualCam = 5f
            tut?.onCamera()
            return
        }
        if (showTurrets && !panel.contains(x, y)) showTurrets = false
    }

    override fun onBack(): Boolean {
        if (aimingBarrage) { aimingBarrage = false; return true }
        if (finished) { g.setScreen(nextScreen()); return true }
        if (showUpgrade >= 0) { showUpgrade = -1; return true }
        if (showTurrets) { showTurrets = false; return true }
        paused = !paused
        return true
    }

    /**
     * Pauza aplikacji (albo zmiana ekranu). Zrzut zapisujemy synchronicznie: MainActivity
     * czeka na to, zanim zatrzyma wątek gry, bo proces może zaraz zostać zabity.
     */
    override fun onPause() {
        aimingBarrage = false
        if (finished || closed || battle.over) return
        paused = true
        g.snapshots?.saveNow(snapshotBytes())
    }

    private fun snapshotBytes(): ByteArray = BattleSnapshot.encode(battle, rankedDaily, r.camX)

    // ---------------------------------------------------------------- koniec bitwy

    private fun onFinished() {
        aimingBarrage = false
        finished = true
        // Najpierw usuń zrzut, potem przyznaj nagrody: wznowienie nie może dać ich drugi raz.
        g.snapshots?.delete()
        val pr = g.profile
        val won = battle.winner == 0
        // Statystyki i osiągnięcia rozlicza Achievements po nagrodzie bazowej.
        val day = Daily.dayNumber()
        val score = battle.score()
        // Wynik oraz log kodujemy tylko raz, po zakończeniu bitwy (także wznowionej).
        try {
            val id = g.replays?.newId()
            val now = System.currentTimeMillis()
            val bytes = ReplayRecord.encode(battle.cfg, commands.log(),
                ReplayRecord.Meta(battle.cfg.mode, battle.cfg.stageIndex, battle.winner,
                    score, battle.time, battle.wave, now, pr.name.take(32)))
            replayBytes = bytes
            replayId = id
            if (id != null) g.replays?.saveAsync(id, bytes)
        } catch (_: IllegalArgumentException) {
            replayBytes = null
            replayId = null
        }
        var earnedStars = 0
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
                    earnedStars = st - old
                    pr.stars[stage] = st
                    newRecord = pr.submit(Profile.BOARD_CAMPAIGN, pr.totalStars().toLong(), day, replayId)
                    g.online.submit(Profile.BOARD_CAMPAIGN, pr.totalStars().toLong())
                }
                if (won) pr.tutorialDone = true
            }
            Mode.SKIRMISH -> {
                reward = if (won) 15 + pr.skirmishDiff * 14 else (4 * effort).toInt() + battle.player.kills / 12
                if (won) {
                    newRecord = pr.submit(Profile.BOARD_SKIRMISH, score, day, replayId)
                    g.online.submit(Profile.BOARD_SKIRMISH, score)
                }
            }
            Mode.ENDLESS -> {
                reward = battle.wave * 3 + battle.player.kills / 10
                if (battle.wave > pr.bestWave) pr.bestWave = battle.wave
                newRecord = pr.submit(Profile.BOARD_ENDLESS, score, day, replayId)
                g.online.submit(Profile.BOARD_ENDLESS, score)
            }
            Mode.DAILY -> {
                if (rankedDaily) {
                    reward = min(90, (score / 350).toInt()) + 10
                    newRecord = pr.submit(Profile.dailyBoard(cfg.seed), score, day, replayId)
                    g.online.submit("daily", score)
                } else reward = (5 * effort).toInt()
            }
        }
        rewardText = "+" + L.plural("medals_n", reward.toLong())
        // Przypinamy tylko wynik, który pozostał w lokalnym TOP 20.
        replayId?.let { id ->
            if (pr.boards.values.any { list -> list.any { it.replay == id } }) g.replays?.pinAsync(id)
        }
        pr.medals = (pr.medals.toLong() + reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val finishedAt = System.currentTimeMillis()
        Missions(pr).onBattleEnd(battle, finishedAt, persist = false, newStars = earnedStars)
        g.showAchievements(Achievements.onBattleEnd(pr, battle, cfg, won, g.onlineAchievements))
        tipKey = if (won) "" else when {
            !battle.endless && battle.time >= Battle.SIEGE_START + 35f -> "tip_siege"
            battle.player.aliveCount >= battle.player.cap &&
                battle.player.gold >= battle.armoryCost(battle.player) * 1.3f -> "tip_cap"
            counterRoleLoss() -> "tip_counter"
            battle.player.spawned >= 6 && battle.player.unitTier.all { it == 1 } -> "tip_upgrade"
            battle.enemy.era > battle.player.era -> "tip_era"
            battle.enemy.goldEarned > battle.player.goldEarned * 1.25f -> "tip_eco"
            battle.player.turretEra.none { it >= 0 } || battle.player.armory == 0 -> "tip_turret"
            else -> "tip_ability"
        }
        pr.recordBattle(cfg.mode, won, battle.time.toInt())
        g.history?.add(BattleHistory.capture(battle, finishedAt, reward, tipKey, surrendered))
        tipIcon = when (tipKey) {
            "tip_siege" -> Icons.HOURGLASS
            "tip_cap" -> Icons.HELMET
            "tip_counter" -> Icons.CROSSED_SWORDS
            "tip_upgrade" -> Icons.CHEVRONS
            "tip_era" -> Icons.EVOLVE
            "tip_eco" -> Icons.COINS
            "tip_turret" -> Icons.TOWER
            else -> Icons.BOLT
        }
        tipText = if (tipKey.isEmpty()) "" else L["why_lost"] + ": " + L[tipKey]
        // Sam zapis powtórki i HTTPS wykonuje osobny wątek, nigdy pętla gry.
        // Wyniki z podejść treningowych daily i wczesne kapitulacje pomijamy.
        if (pr.globalRanking && battle.stepCount >= 60 && (cfg.mode != Mode.DAILY || rankedDaily)) {
            g.scores?.submit(cfg, commands.log(), score, rankedDaily, pr.name, pr.playerId,
                pr.researchLevels(), pr.skirmishDiff)
        }
        // onBattleEnd zapisuje profil tylko przy pierwszym rozliczeniu; gwiazdki i nagroda muszą trafić na dysk zawsze.
        pr.save()
        if (won) g.host.onBattleWon()
        g.haptic(if (won) 80 else 160)
        g.sfx(if (won || cfg.mode == Mode.ENDLESS) Sound.VICTORY else Sound.DEFEAT, 1f, 0)
    }

    /** Rozpoznaj kontrę wobec najczęściej szkolonej roli; tylko liczniki z bitwy. */
    private fun counterRoleLoss(): Boolean {
        val own = battle.player.spawnedByRole
        val foe = battle.enemy.spawnedByRole
        var lead = 0
        for (i in 1..2) if (own[i] > own[lead]) lead = i
        if (own[lead] < 6) return false
        val counter = when (lead) { 0 -> 2; 1 -> 0; 2 -> 1; else -> return false }
        return foe[counter] >= 5 && foe[counter] * 2 >= battle.enemy.spawned &&
            Defs.counterMultiplier(roles[counter], roles[lead]) > 1f
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
        // Interpolacja między krokami symulacji (płynny ruch przy 90/120 Hz).
        r.lerp = (acc / Battle.STEP).coerceIn(0f, 1f)
        r.render(c, if (paused || battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null) 0f else frameDt * speed * (tut?.timeScale ?: 1f), w, h)
        if (aimingBarrage) r.drawBarrageAim(c, aimWorldX, fieldRect)
        // HUD zachowuje czytelność i nie zasłania pola gry przy 130%.
        val regularTextScale = ui.textScale
        ui.textScale = min(regularTextScale, 1.15f)
        drawTop(c)
        if (aimingBarrage) {
            ui.ribbon(c, w * 0.5f, fieldRect.top + 22 * dp, L["barrage_aim_hint"],
                15f, Ui.PLAYER, 0f, Ui.TEXT, w - g.insetL - g.insetR - 24 * dp)
        }
        drawHudBack(c)
        for (b in actionArr) ui.button(c, b, 13f)
        for (b in unitBtn) ui.button(c, b, 13f)
        drawUnitTiers(c)
        for (b in unitUpgrade) ui.button(c, b, 9f)
        drawQueue(c)
        ui.button(c, evolveBtn, 15f)
        ui.button(c, pauseBtn, 15f)
        if (showTurrets && battle.pendingChoice == null && battle.pendingCards == null && battle.pendingVariant == null && !paused && !finished) drawTurretPanel(c)
        if (showUpgrade >= 0 && battle.pendingChoice == null && battle.pendingCards == null && battle.pendingVariant == null && !paused && !finished) drawUpgradePanel(c)
        if ((battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null) && !finished) drawChoices(c)
        if (!finished) {
            tut?.draw(c)
            hints.draw(c)
        }
        if (paused && !finished) {
            c.drawColor(Color.argb(150, 0, 0, 0))
            ui.panel(c, pausePanel)
            ui.ribbon(c, pausePanel.centerX(), pausePanel.top + 4 * dp, L["paused"], 22f, Ui.RIBBON, pausePanel.width() * 0.6f)
            for (b in pauseMenu) ui.button(c, b, 18f)
        }
        if (finished) drawResults(c)
        ui.textScale = regularTextScale
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
                val bx = mini.right - 12 * dp - hw
                ui.bar(c, bx, hy, hw, 7 * dp, e.baseHp / e.baseMaxHp, Ui.ENEMY)
                if (g.profile.teamMarkers) {
                    // Pasek wroga: kreski dodatkowo do czerwieni/pomarańczu.
                    val end = bx + hw * (e.baseHp / e.baseMaxHp).coerceIn(0f, 1f)
                    ui.stroke.color = Ui.OUTLINE
                    ui.stroke.strokeWidth = 1.3f * dp
                    var xx = bx + 5f * dp
                    while (xx < end) {
                        c.drawLine(xx, hy, xx, hy + 7f * dp, ui.stroke)
                        xx += 8f * dp
                    }
                }
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

    /** Trzy belki na przycisku; złote oznaczają już kupione stopnie. */
    private fun drawUnitTiers(c: Canvas) {
        val p = battle.player
        for (i in 0..3) {
            val b = unitBtn[i]
            if (!b.visible) continue
            val bw = min(7 * dp, (b.r.width() - 15 * dp) / 3f)
            for (t in 0 until Defs.MAX_UNIT_TIER) {
                val x = b.r.left + 4 * dp + t * (bw + 2 * dp)
                ui.fill.color = if (t < p.unitTier[i]) Ui.GOLD else Ui.FRAME
                c.drawRoundRect(x, b.r.top + 4 * dp, x + bw, b.r.top + 7 * dp, 1.5f * dp, 1.5f * dp, ui.fill)
            }
        }
    }

    private fun drawUpgradePanel(c: Canvas) {
        val idx = showUpgrade
        if (idx !in 0..3) return
        val role = roles[idx]
        val current = battle.player.unitTier[idx]
        val next = min(Defs.MAX_UNIT_TIER, current + 1)
        val def = battle.player.unit(role)
        val pr = upgradePanel
        c.drawColor(Color.argb(158, 0, 0, 0))
        ui.panel(c, pr)
        ui.ribbon(c, pr.centerX(), pr.top + 4 * dp, L["unit_upgrade_title"], 18f,
            Ui.RIBBON, pr.width() * 0.62f, Ui.GOLD, pr.width() - 30 * dp)
        val name = L[def.tierNameKeys[next - 1]]
        ui.textO(c, name, pr.centerX(), pr.top + 66 * dp,
            ui.fit(name, 17f, pr.width() - 36 * dp), Ui.TEXT, Paint.Align.CENTER)
        ui.textO(c, upgradeTierText, pr.centerX(), pr.top + 88 * dp, 13f, Ui.GOLD, Paint.Align.CENTER)
        r.drawUnitIcon(c, def, pr.left + 64 * dp, pr.top + 172 * dp, 80 * dp, Ui.PLAYER, next)
        val tx = pr.left + 122 * dp
        val tw = pr.right - tx - 12 * dp
        val stat = L["unit_upgrade_stats"]
        val trait = L[upgradeRoleKeys[idx]]
        ui.textO(c, stat, tx, pr.top + 116 * dp, ui.fit(stat, 12f, tw), Ui.TEXT)
        ui.textO(c, trait, tx, pr.top + 140 * dp, ui.fit(trait, 12f, tw), Ui.GOLD)
        val note = L["unit_upgrade_future"]
        ui.textO(c, note, tx, pr.top + 162 * dp, ui.fit(note, 11f, tw), Ui.DIM)
        val reset = L["unit_upgrade_reset"]
        ui.textO(c, reset, pr.centerX(), upgradeBuy.r.top - 11 * dp, ui.fit(reset, 10f, pr.width() - 25 * dp),
            Ui.DIM, Paint.Align.CENTER)
        ui.button(c, upgradeBuy, 15f)
        ui.closeButton(c, upgradeClose)
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
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, "${L["turrets"]} — ${L[Defs.turret(p.era, p.eraVariant).nameKey]}", 16f,
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
            val name = if (te >= 0) L[Defs.turret(te, p.turretVariant[i]).nameKey] else L["empty"]
            val avail = b.r.left - tx - 6 * dp
            ui.textO(c, L.f("slot", i + 1), tx, cy - 3 * dp, 10f, Ui.DIM, Paint.Align.LEFT, 1.5f)
            ui.textO(c, name, tx, cy + 12 * dp, ui.fit(name, 13f, avail), if (te >= 0) Ui.TEXT else Ui.DIM, Paint.Align.LEFT, 1.8f)
            ui.button(c, slotBuy[i], 13f)
            ui.button(c, slotSell[i], 13f)
        }
        ui.closeButton(c, closeBtn)
    }


    /** Modal dwóch wariantów: ikonki tych samych jednostek co na polu bitwy. */
    private fun drawVariants(c: Canvas) {
        val era = battle.pendingVariant ?: return
        val variants = Defs.spec(era).variants
        c.drawColor(Color.argb(175, 0, 0, 0))
        ui.ribbon(c, w / 2, variantBtn[0].r.top - 27 * dp,
            L["choose_variant"], 21f, Ui.RIBBON, min(340 * dp, w * 0.7f))
        for (i in variants.indices) {
            val v = variants[i]
            val b = variantBtn[i]
            val off = if (b.pressed) 3 * dp else 0f
            tmpR.set(b.r.left, b.r.top + off, b.r.right, b.r.bottom + off)
            ui.panel(c, tmpR, Color.argb(247, 40, 48, 70), 14f)
            val name = L[v.nameKey]
            ui.textO(c, name, tmpR.centerX(), tmpR.top + 34 * dp,
                ui.fit(name, 19f, tmpR.width() - 16 * dp), Ui.GOLD, Paint.Align.CENTER)
            ui.wrap(c, L[v.descKey], tmpR.centerX(), tmpR.top + 58 * dp,
                tmpR.width() - 18 * dp, 12.5f, Ui.TEXT, Paint.Align.CENTER)
            val imgH = min(72 * dp, tmpR.height() * 0.32f)
            val iy = tmpR.bottom - 28 * dp
            for (role in 0..3) {
                val def = Defs.unit(era, roles[role], i)
                val xx = tmpR.left + (role + 0.5f) * tmpR.width() / 4f
                r.drawUnitIcon(c, def, xx, iy, imgH, Ui.PLAYER)
            }
        }
    }

    private fun drawChoices(c: Canvas) {
        if (battle.pendingVariant != null) { drawVariants(c); return }
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
            val trim = if (rarity == null) Ui.GOLD else CARD_RARITY[rarity.ordinal.coerceIn(0, CARD_RARITY.size - 1)]
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
            val descKey = if (cards == null) doctrineDescKeys[doctrines!![i].ordinal]
                else cardDescKeys[cards[i].ordinal]
            ui.wrap(c, L[descKey], tmpR.centerX(), my + mr + (if (rarity != null) 56f else 45f) * dp,
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
        row(Icons.MEDAL, L["reward"], rewardText, Ui.GOLD)
        if (newRecord && y + 26 * dp < restartEnd.r.top) {
            ui.textO(c, L["new_record"], rp.centerX(), y + 20 * dp, 18f, Ui.GOOD, Paint.Align.CENTER)
            y += 28 * dp
        }
        if (tipKey.isNotEmpty() && y + 20 * dp < restartEnd.r.top) {
            Icons.draw(c, tipIcon, rp.left + 34 * dp, y + 13 * dp, 20 * dp, dp = dp)
            ui.wrap(c, tipText, rp.left + 53 * dp, y + 18 * dp,
                rp.width() - 79 * dp, 13f, Ui.TEXT, Paint.Align.LEFT)
        }
        c.restore()
        ui.button(c, continueBtn, 17f)
        ui.button(c, restartEnd, 17f)
        if (replayEnd.visible) ui.button(c, replayEnd, 15f)
    }
}
