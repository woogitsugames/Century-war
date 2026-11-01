package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.crash.CrashReports
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.BattleMusicIntensity
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Sound
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.TooltipBubble
import com.centurywar.game.FxBuffer
import com.centurywar.game.Body
import com.centurywar.game.EraArt
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
import com.centurywar.game.EndlessBosses
import com.centurywar.game.EndlessRarity
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import com.centurywar.game.Role
import com.centurywar.game.Skirmish
import com.centurywar.game.Weather
import com.centurywar.meta.Achievements
import com.centurywar.meta.AchievementBattleTracker
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.SkirmishSetup
import com.centurywar.meta.Missions
import kotlin.math.max
import kotlin.math.min

/** Mechaniczne wydzielenie BattleBottomHud; ten sam stan i kolejność operacji co w BattleScreen. */
internal class BattleBottomHud(private val owner: BattleScreen) {

    fun layout(l: Float, rr: Float, avail: Float, pad: Float) {
        with(owner) {
            val n = 10
            // Na okładce Folda i telefonach do 360 dp strzałki ulepszeń potrzebują
            // osobnego rzędu. Inaczej ich cel 48 dp przechwytuje tapnięcia w jednostki.
            threeRowHud = h >= w * 2.2f || avail < 360 * dp
            val gap = if (threeRowHud && avail < 313 * dp) 3 * dp else 5 * dp
            // Pozostałe układy są bez zmian, w tym dwa rzędy leworęczne na Foldzie.
            twoRowHud = threeRowHud || (g.profile.leftHandedHud && avail < 560 * dp)
            val size = if (threeRowHud) min(48 * dp, (avail - gap * 5) / 6f)
                else if (twoRowHud) min(70 * dp, (avail - gap * 5) / 6f)
                else min(70 * dp, (avail - gap * (n - 1) - 10 * dp) / n)
            val rowGap = if (threeRowHud) 3 * dp else gap
            val bottomPad = if (threeRowHud) 4 * dp else pad
            hudH = if (threeRowHud) 3 * size + 2 * rowGap + bottomPad * 2 + 2 * dp + g.insetB
                else (if (twoRowHud) 2 * size + gap else size) + pad * 2 + 4 * dp + g.insetB
            val y = h - g.insetB - bottomPad - size
            var x = l
            for (b in unitBtn) {
                val unitY = if (threeRowHud) y - 2 * (size + rowGap)
                    else if (twoRowHud) y - size - gap else y
                b.set(x, unitY, size, size)
                x += size + gap
            }
            val arrow = min(24 * dp, size * 0.36f)
            for (i in 0..3) {
                val ur = unitBtn[i].r
                if (threeRowHud) {
                    unitUpgrade[i].set(ur.left, y - size - rowGap, size, size)
                    unitUpgrade[i].hitSlop = 0f
                    unitUpgrade[i].hitOffsetX = 0f
                    unitUpgrade[i].hitOffsetY = 0f
                } else {
                    unitUpgrade[i].set(ur.right - arrow, ur.top, arrow, arrow)
                    val target = if (twoRowHud) min(48 * dp, size) else 48 * dp
                    unitUpgrade[i].hitSlop = (target - arrow).coerceAtLeast(0f) / 2f
                    // Powiększony obszar strzałki leży wewnątrz przycisku jednostki.
                    unitUpgrade[i].hitOffsetX = -unitUpgrade[i].hitSlop
                    unitUpgrade[i].hitOffsetY = -unitUpgrade[i].hitSlop
                }
            }
            queueInfoBtn.set(unitBtn[0].r.left, unitBtn[0].r.top - 23 * dp, 92 * dp, 22 * dp)
            queueInfoBtn.hitSlop = 13 * dp
            queueInfoBtn.hitOffsetX = queueInfoBtn.hitSlop
            queueInfoBtn.hitOffsetY = -queueInfoBtn.hitSlop
            if (twoRowHud) x = rr - (6 * size + 5 * gap) else x += 10 * dp
            for (b in actionBtns) { b.set(x, y, size, size); x += size + gap }
        }
    }

    fun refreshButtons() {
        with(owner) {
        val p = battle.player
        if (p.slots != turretSlotsLaid) upgradesView.layoutTurretPanel()
        if (p.era != turretHeadingEra || p.eraVariant != turretHeadingVariant || turretHeadingLanguage != L.lang) {
            turretHeadingEra = p.era
            turretHeadingVariant = p.eraVariant
            turretHeadingLanguage = L.lang
            turretHeading = "${L["turrets"]} — ${L[Defs.turret(p.era, p.eraVariant).nameKey]}"
            // Nie zmieniamy dotychczasowego układu podpisów slotów.
        }
        refreshPauseTexts()
        val modal = paused || finished || battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null || showUpgrade >= 0
        for (i in 0..3) {
            val role = roles[i]
            val def = p.unit(role)
            val b = unitBtn[i]
            b.visible = !modal
            val locked = role == Role.ELITE && !p.eliteUnlocked
            b.enabled = !locked && battle.canQueue(p, role)
            b.sub = if (locked) "" else costStr(i, battle.unitCost(p, role))
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
            if (showUpgrade != upgradeRoleShown || tier != upgradeTierShown || upgradeTierLanguage != L.lang) {
                upgradeRoleShown = showUpgrade
                upgradeTierShown = tier
                upgradeTierLanguage = L.lang
                upgradeTierText = L.f("unit_upgrade_tier", tier, min(Defs.MAX_UNIT_TIER, tier + 1))
            }
            val cost = battle.unitUpgradeCost(p, role)
            upgradeBuy.label = if (cost < 0) L["unit_upgrade_max"] else L["upgrade"]
            upgradeBuy.sub = if (cost < 0) "" else costStr(5, cost)
            upgradeBuy.subIcon = if (cost < 0) -1 else Icons.COIN
            upgradeBuy.enabled = battle.canUpgradeUnit(p, role)
            upgradeBuy.highlight = upgradeBuy.enabled
        }
        queueInfoBtn.visible = !modal && p.queueSize > 0
        queueInfoBtn.enabled = false
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
        barrageBtn.label = L["battle84_barrage_short"]
        barrageBtn.iconId = Icons.METEOR
        barrageBtn.enabled = battle.abilityReady(p, 0)
        barrageBtn.progress = if (p.abilityCd[0] > 0f) 1f - p.abilityCd[0] / (Battle.BARRAGE_CD * p.abilityCdMul) else -1f
        if (modal || !barrageBtn.enabled) aimingBarrage = false
        barrageBtn.selected = aimingBarrage
        barrageBtn.highlight = aimingBarrage
        rallyBtn.visible = !modal && p.rallyUnlocked
        rallyBtn.label = L["battle84_rally_short"]
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
        val shownSpeed = effectiveSpeed.toInt().coerceIn(1, speed)
        speedBtn.label = if (shownSpeed == speed) speedLabels[speed] else limitedSpeedLabels[speed][shownSpeed]
        evolveBtn.visible = !modal
        val canEvo = battle.canEvolve(p)
        evolveBtn.enabled = canEvo
        evolveBtn.highlight = canEvo
        evolveBtn.iconId = if (p.era >= cfg.maxEra) Icons.CHEVRONS else Icons.EVOLVE
        if (shownRank != p.rank || shownRankLanguage != L.lang) {
            shownRank = p.rank; shownRankLanguage = L.lang; shownRankText = L.f("rank", p.rank)
        }
        evolveBtn.label = if (p.era >= cfg.maxEra) shownRankText else L["evolve"]
        if (p.era >= cfg.maxEra) evolveBtn.enabled = false
        pauseBtn.visible = !finished
        pauseBtn.iconId = Icons.PAUSE
        pauseBtn.label = ""

        for (i in pauseMenu.indices) pauseMenu[i].visible = paused && !finished
        pauseMenu[0].label = L["resume"]; pauseMenu[1].label = L["restart"]; pauseMenu[2].label = L["quit"]
        pauseMenu[0].iconId = Icons.PLAY; pauseMenu[1].iconId = Icons.RESTART; pauseMenu[2].iconId = Icons.WHITE_FLAG
        pauseMenu[0].highlight = true; pauseMenu[2].color = Ui.BTN_RED
        pauseVibrationBtn.visible = paused && !finished
        // Te same trzy poziomy i napisy co w Ustawieniach (0 wył., 1 słabe, 2 pełne).
        val vibLevel = g.profile.vibrationLevel
        pauseVibrationBtn.label = L[when (vibLevel) { 0 -> "shake_off"; 1 -> "shake_low"; else -> "shake_full" }]
        pauseVibrationBtn.color = if (vibLevel > 0) Ui.BTN_GREEN else Ui.BTN
        pauseSpeedBtn.visible = paused && !finished
        pauseSpeedBtn.label = speedLabels[speed]
        continueBtn.visible = finished && resultTime > 0.8f
        continueBtn.label = L["continue"]
        continueBtn.iconId = Icons.PLAY
        continueBtn.highlight = true
        restartEnd.visible = finished && resultTime > 0.8f
        restartEnd.label = L["restart"]
        restartEnd.iconId = Icons.RESTART
        replayEnd.visible = finished && resultTime > 0.8f && replayBytes != null
        replayEnd.label = L["battle84_watch_replay"]
        replayEnd.iconId = Icons.WATCH_EYE
        resultStatsTab.visible = finished && resultTime > 0.8f
        resultChartTab.visible = resultStatsTab.visible
        resultStatsTab.label = L["bs104_stats"]
        resultChartTab.label = L["bs104_chart"]
        resultStatsTab.selected = !showResultChart
        resultChartTab.selected = showResultChart

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
                val price = p.turretCost()
                val key = price
                if (slotBuyKey[i] != key || slotLabelLanguage != L.lang) {
                    slotBuyKey[i] = key
                    slotBuy[i].label = "${L["buy"]} $price"
                }
                slotBuy[i].iconId = Icons.COIN
                slotBuy[i].enabled = p.gold >= price
                slotBuy[i].highlight = slotBuy[i].enabled
            } else {
                val uc = battle.upgradeCost(p, i)
                val key = -uc - 2
                if (slotBuyKey[i] != key || slotLabelLanguage != L.lang) {
                    slotBuyKey[i] = key
                    slotBuy[i].label = if (uc < 0) "" else "${L["upgrade"]} $uc"
                }
                slotBuy[i].iconId = if (uc < 0) Icons.CHECK else Icons.EVOLVE
                slotBuy[i].enabled = uc >= 0 && p.gold >= uc
                slotBuy[i].highlight = slotBuy[i].enabled
            }
            val sell = if (te >= 0) p.turretCost(te, p.turretVariant[i]) / 2 else 0
            if (slotSellKey[i] != sell || slotLabelLanguage != L.lang) {
                slotSellKey[i] = sell
                slotSell[i].label = "${L["sell"]} $sell"
            }
            slotSell[i].iconId = Icons.COIN
            slotSell[i].color = Ui.BTN_RED
        }
        closeBtn.visible = tp
        if (tp) slotLabelLanguage = L.lang
        }
    }

    fun drawHudBack(c: Canvas) {
        with(owner) {
        val top = h - hudH
        ui.fill.color = Color.argb(190, 16, 20, 32)
        c.drawRect(0f, top, w, h, ui.fill)
        ui.fill.color = Ui.OUTLINE
        c.drawRect(0f, top - 2.5f * dp, w, top, ui.fill)
        ui.fill.color = Color.argb(160, 222, 170, 70)
        c.drawRect(0f, top, w, top + 1.5f * dp, ui.fill)
        // Separator między jednostkami a akcjami: pionowy lub między dwoma rzędami.
        ui.fill.color = Color.argb(90, 255, 255, 255)
        if (twoRowHud) {
            val sy = ((if (threeRowHud) unitUpgrade[0].r.bottom else unitBtn[0].r.bottom) +
                turretBtn.r.top) * 0.5f
            c.drawRect(g.insetL + 6 * dp, sy - 1 * dp, w - g.insetR - 6 * dp, sy + 1 * dp, ui.fill)
        } else {
            val sx = if (g.profile.leftHandedHud) (unitBtn[3].r.left + turretBtn.r.right) / 2f
                else (unitBtn[3].r.right + turretBtn.r.left) / 2f
            c.drawRect(sx - 1 * dp, unitBtn[0].r.top + 6 * dp,
                sx + 1 * dp, unitBtn[0].r.bottom - 6 * dp, ui.fill)
        }
        }
    }

    /** Trzy belki na przycisku; złote oznaczają już kupione stopnie. */
    fun drawUnitTiers(c: Canvas) {
        with(owner) {
        val p = battle.player
        for (i in 0..3) {
            val b = unitBtn[i]
            if (!b.visible) continue
            val bw = min(7 * dp, (b.r.width() - 15 * dp) / 3f)
            for (t in 0 until Defs.MAX_UNIT_TIER) {
                // Leworęczny HUD ma strzałkę ulepszenia w lewym rogu: belki od prawej.
                val x = if (g.profile.leftHandedHud) b.r.right - 4 * dp - bw - t * (bw + 2 * dp)
                    else b.r.left + 4 * dp + t * (bw + 2 * dp)
                ui.fill.color = if (t < p.unitTier[i]) Ui.GOLD else Ui.FRAME
                c.drawRoundRect(x, b.r.top + 4 * dp, x + bw, b.r.top + 7 * dp, 1.5f * dp, 1.5f * dp, ui.fill)
            }
        }
        }
    }

    fun drawQueue(c: Canvas) {
        with(owner) {
        val p = battle.player
        if (p.queueSize == 0 || !unitBtn[0].visible) return
        val y = unitBtn[0].r.top - 10 * dp
        for (i in 0 until p.queueSize) {
            val x = if (g.profile.leftHandedHud) unitBtn[0].r.right - 9 * dp - i * 16 * dp
                else unitBtn[0].r.left + 9 * dp + i * 16 * dp
            ui.fill.color = Ui.OUTLINE
            c.drawCircle(x, y, 6.5f * dp, ui.fill)
            ui.fill.color = if (i == 0) Ui.GOLD else Color.rgb(220, 224, 232)
            c.drawCircle(x, y, 4.5f * dp, ui.fill)
        }
        }
    }
}
