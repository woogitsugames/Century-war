package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Defs
import com.centurywar.game.EndlessBosses
import com.centurywar.game.Weather
import kotlin.math.max
import kotlin.math.min

internal class BattleTopBar(private val screen: BattleScreen) {
    private var enemyEraShown = -1
    private var enemyEraLanguage = -1
    private var enemyEraText = ""
    private var goldShown = -1
    private var goldText = ""
    private var capKey = -1
    private var capText = ""
    private var hpP = -1
    private var hpPText = ""
    private var hpE = -1
    private var hpEText = ""

    private var timeSec = -1
    private var timeText = ""
    private var siegeKey = -1
    private var siegeText = ""
    private var waveShown = -1
    private var waveText = ""
    private var nextThemeShown = -1
    private var nextThemeText = ""
    private var bossShownKind = -1
    private var bossShownSecondary = -1
    private var bossTitleText = ""
    private var bossDescText = ""
    private var bossSecondDescText = ""

    /** Formatowanie raz na sekundę zamiast w każdej klatce (String.format alokuje). */
    private fun timeStr(t: Float): String {
        val s = t.toInt()
        if (s != timeSec) { timeSec = s; timeText = "%d:%02d".format(s / 60, s % 60) }
        return timeText
    }


    private var armyP = -1
    private var armyE = -1
    private var armyPText = ""
    private var armyEText = ""

    /** Lewy blok drugiego rzędu: żywe jednostki gracza i wroga (przy mgle liczba wroga ukryta). */
    private fun drawArmyCounts(c: Canvas, p: com.centurywar.game.Side, e: com.centurywar.game.Side, x: Float, cy: Float) {
        with(screen) {
        val ec = if (battle.fog) -2 else e.aliveCount
        if (p.aliveCount != armyP) { armyP = p.aliveCount; armyPText = armyP.toString() }
        if (ec != armyE) { armyE = ec; armyEText = if (ec < 0) "?" else ec.toString() }
        Icons.draw(c, Icons.CROSSED_SWORDS, x + 10 * dp, cy, 20 * dp, dp = dp)
        ui.textO(c, armyPText, x + 46 * dp, cy + 5 * dp, 14f, Ui.PLAYER, Paint.Align.RIGHT, 1.8f)
        ui.textO(c, ":", x + 54 * dp, cy + 5 * dp, 14f, Ui.TEXT, Paint.Align.CENTER, 1.8f)
        ui.textO(c, armyEText, x + 62 * dp, cy + 5 * dp, 14f, Ui.ENEMY, Paint.Align.LEFT, 1.8f)
    
        }
    }


    fun draw(c: Canvas) {
        with(screen) {
        val p = battle.player
        val e = battle.enemy
        if (e.era != enemyEraShown || enemyEraLanguage != L.lang) {
            enemyEraShown = e.era
            enemyEraLanguage = L.lang
            enemyEraText = L.f("battle84_enemy_era", L[Defs.eraKeys[e.era]])
        }
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
            // Pogoda przy minimapie (miejsce przed przyciskiem pauzy).
            val secondRow = compactTop
            val weatherName = L[battle.weather.key]
            // W drugim rzędzie: liczebność armii i pogoda na wysokości środka minimapy,
            // każda wyśrodkowana w wolnym polu po swojej stronie.
            val wy = if (secondRow) mini.centerY() - 11 * dp else mini.top - 13 * dp
            val wx = if (secondRow) {
                val tw = ui.measure(weatherName, 12f, true)
                val block = 21 * dp + 4 * dp + tw
                val free = (w - g.insetR - 8 * dp - mini.right) - 4 * dp
                if (block <= free) mini.right + 2 * dp + (free - block) * 0.5f + 10 * dp
                else mini.right + 14 * dp
            } else mini.right + 14 * dp
            if (secondRow) drawArmyCounts(c, p, e, (l + mini.left) * 0.5f - 38 * dp, wy + 11 * dp)
            Icons.draw(c, Icons.weatherIcon(battle.weather), wx, wy + 11 * dp, 21 * dp, dp = dp)
            // Nazwa pogody tylko w miejscu wolnym od nazwy epoki wroga (rysowanej przy pauzie);
            // gdy brak miejsca, zostaje sama ikona.
            // W drugim rzędzie (wąski ekran) nazwa epoki wroga jest wyżej, więc miejsce sięga krawędzi.
            val eraLeft = if (secondRow) w - g.insetR - 6 * dp
                else pauseBtn.r.left - 10 * dp - ui.measure(L[Defs.eraKeys[e.era]], 12f, true) - 4 * dp
            val wAvail = eraLeft - (wx + 14 * dp) - 6 * dp
            if (wAvail >= 30 * dp) {
                val size = ui.fit(weatherName, 12f, wAvail, 8f)
                if (ui.measure(weatherName, size, true) <= wAvail)
                    ui.textO(c, weatherName, wx + 14 * dp, wy + 15 * dp, size, Ui.TEXT)
            }
            if (!compactTop && battle.weather == Weather.WIND) {
                val dir = battle.windDirection().toFloat()
                val mx = wx + 24 * dp
                val my = wy + 33 * dp
                ui.stroke.color = Ui.GOLD
                ui.stroke.strokeWidth = 2 * dp
                c.drawLine(mx - 11 * dp * dir, my, mx + 11 * dp * dir, my, ui.stroke)
                c.drawLine(mx + 11 * dp * dir, my, mx + 5 * dp * dir, my - 5 * dp, ui.stroke)
                c.drawLine(mx + 11 * dp * dir, my, mx + 5 * dp * dir, my + 5 * dp, ui.stroke)
            }
            val hw = max(24 * dp, mini.width() * 0.5f - 14 * dp)
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
                if (battle.fog && !p.fogSight && u.side == 1 && u.x - p.front > 650f) continue
                val x = r.worldToMini(u.x, mini.left, mini.width())
                val rad = 2.6f * dp * u.def.size.coerceAtMost(1.4f)
                ui.fill.color = Ui.OUTLINE
                c.drawCircle(x, mini.centerY(), rad + 1 * dp, ui.fill)
                ui.fill.color = if (u.side == 0) Ui.PLAYER else Ui.ENEMY
                c.drawCircle(x, mini.centerY(), rad, ui.fill)
            }
            val pi = p.baseHp.toInt()
            if (pi != hpP) { hpP = pi; hpPText = pi.toString() }
            ui.textO(c, hpPText, mini.left, mini.bottom + 14 * dp,
                ui.fit(hpPText, 11f, mini.width() * 0.28f, 8f),
                Ui.PLAYER, Paint.Align.LEFT, 1.8f)
            val rightLabel = if (battle.endless) {
                if (battle.wave != waveShown) { waveShown = battle.wave; waveText = L.f("wave", battle.wave) }
                waveText
            } else {
                val ei = e.baseHp.toInt()
                if (ei != hpE) { hpE = ei; hpEText = ei.toString() }
                hpEText
            }
            ui.textO(c, rightLabel, mini.right, mini.bottom + 14 * dp,
                ui.fit(rightLabel, 11f, mini.width() * 0.28f, 8f),
                if (battle.endless) Ui.GOLD else Ui.ENEMY, Paint.Align.RIGHT, 1.8f)
            val sp = battle.siegePressure()
            if (sp > 1f) {
                val k = kotlin.math.round(sp * 10f).toInt()
                if (k != siegeKey) { siegeKey = k; siegeText = L.f("siege_pressure", sp) }
                ui.textO(c, siegeText, mini.centerX(), mini.bottom + 14 * dp,
                    ui.fit(siegeText, 11f, mini.width() * 0.40f, 8f),
                    Ui.BAD, Paint.Align.CENTER, 1.8f)
            } else {
                val clock = timeStr(battle.time)
                ui.textO(c, clock, mini.centerX(), mini.bottom + 14 * dp,
                    ui.fit(clock, 11f, mini.width() * 0.40f, 8f),
                    Ui.TEXT, Paint.Align.CENTER, 1.8f)
            }
        }
        if (battle.endless && battle.pendingCards == null && battle.pendingChoice == null) {
            val boss = battle.currentBoss()
            if (boss != null) {
                val bw = min(310 * dp, w - g.insetL - g.insetR - 30 * dp)
                val bx = (w - bw) * 0.5f
                if (boss.bossKind != bossShownKind || boss.bossSecondaryKind != bossShownSecondary) {
                    bossShownKind = boss.bossKind
                    bossShownSecondary = boss.bossSecondaryKind
                    bossTitleText = L["eb_boss"] + " — " + L[EndlessBosses.nameKeys[bossShownKind]]
                    bossDescText = L[EndlessBosses.descriptionKeys[bossShownKind]]
                    bossSecondDescText = if (bossShownSecondary == EndlessBosses.NONE) "" else
                        L[EndlessBosses.descriptionKeys[bossShownSecondary]]
                    if (bossShownSecondary != EndlessBosses.NONE)
                        bossTitleText += " + " + L[EndlessBosses.nameKeys[bossShownSecondary]]
                }
                ui.textO(c, bossTitleText, w * 0.5f, topBar.bottom + 16 * dp,
                    ui.fit(bossTitleText, 12f, bw, 9f), Ui.ENEMY, Paint.Align.CENTER, 1.7f)
                ui.textO(c, bossDescText, w * 0.5f, topBar.bottom + 32 * dp,
                    ui.fit(bossDescText, 10f, bw, 7f), Ui.TEXT, Paint.Align.CENTER, 1.5f)
                if (bossSecondDescText.isNotEmpty()) ui.textO(c, bossSecondDescText,
                    w * 0.5f, topBar.bottom + 46 * dp,
                    ui.fit(bossSecondDescText, 10f, bw, 7f), Ui.TEXT, Paint.Align.CENTER, 1.5f)
                ui.bar(c, bx, topBar.bottom + (if (bossSecondDescText.isEmpty()) 40f else 54f) * dp, bw, 8 * dp,
                    boss.hp / boss.maxHp, Ui.ENEMY)
            }
            if (battle.wave != nextThemeShown) {
                nextThemeShown = battle.wave
                val nextKind = battle.nextBossKind
                nextThemeText = if (nextKind != EndlessBosses.NONE) {
                    val second = battle.nextBossSecondaryKind
                    L["next_wave"] + " " + L["eb_boss"] + " — " +
                        L[EndlessBosses.nameKeys[nextKind]] +
                        (if (second != EndlessBosses.NONE) " + " + L[EndlessBosses.nameKeys[second]] else "")
                } else L["next_wave"] + " " + L[battle.nextWaveKey]
            }
            ui.textO(c, nextThemeText, w * 0.5f,
                topBar.bottom + (if (boss == null) 20f else
                    if (boss.bossSecondaryKind == EndlessBosses.NONE) 64f else 78f) * dp,
                ui.fit(nextThemeText, 11f, w - g.insetL - g.insetR - 26 * dp, 8f),
                Ui.GOLD, Paint.Align.CENTER, 1.7f)
        }
        // Epoka wroga w osobnej linii na szerokim HUD; nie nachodzi na pogodę.
        val ex = pauseBtn.r.left - 10 * dp
        val enemyWidth = if (compactTop) ex - evolveBtn.r.right - 10 * dp
            else ex - mini.right - 12 * dp
        val enemyY = t + (if (compactTop) 15f else 49f) * dp
        ui.textO(c, enemyEraText, ex, enemyY,
            ui.fit(enemyEraText, if (compactTop) 12f else 11f, enemyWidth, 7f),
            Ui.ENEMY, Paint.Align.RIGHT, 2f)
        if (p.doctrines.isNotEmpty()) {
            val isz = 15 * dp
            var x = ex - isz / 2
            var i = p.doctrines.size - 1
            while (i >= 0) {
                if (x < (if (mini.width() > 0f) mini.right + 10 * dp else evolveBtn.r.right + 10 * dp)) break
                Icons.draw(c, doctrineIcon(p.doctrines[i]), x, t + 31 * dp, isz, dp = dp)
                x -= isz + 3 * dp
                i--
            }
        }
    
        }
    }


    fun invalidateLanguage() {
        waveShown = -1
        nextThemeShown = -1
        bossShownKind = -1
        siegeKey = -1
        enemyEraLanguage = -1
    }
}
