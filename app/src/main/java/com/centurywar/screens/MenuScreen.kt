package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Mode
import com.centurywar.meta.Missions
import kotlin.math.max
import kotlin.math.min

class MenuScreen(g: Game) : Screen(g) {
    override val crashName get() = "MenuScreen"
    /** W menu Wstecz przechwytujemy tylko przy otwartym oknie. */
    val hasOpenModal get() = g.resumeOffer != null
    private val bg = MenuBg()
    private val battleScene = MenuBattleScene()
    private val emblem = MenuEmblem()
    private val campaign = btn(1)
    private val endless = btn(2)
    private val daily = btn(3)
    private val skirmish = btn(4)
    private val research = btn(5)
    private val rankings = btn(6)
    private val missions = btn(9)
    private val settings = btn(7)
    private val codex = btn(8)
    // pytanie o wznowienie przerwanej bitwy (g.resumeOffer)
    private val resumeYes = btn(RESUME_YES)
    private val resumeNo = btn(RESUME_NO)
    private val resumePanel = RectF()
    private var resumeInfo = ""
    private val resumeDescLines = arrayOfNulls<String>(5)
    private var resumeDescCount = 0
    private var resumeDescWidth = -1f
    private var resumeDescScale = -1f
    private var resumeDescLanguage = -1
    private var dt = 0f
    private val missionManager = Missions(g.profile)
    private var missionBadgePoll = 0f
    internal var claimableMissions = 0
        private set
    private var missionBadgeText = ""

    private fun refreshMissionBadge() {
        val now = System.currentTimeMillis()
        val state = g.profile.missionState
        if (state.dailyDay != Missions.utcDay(now) || state.weeklyWeek != Missions.utcWeek(now))
            missionManager.refresh(now)
        val count = if (state.dailyDay == Missions.utcDay(now))
            Missions.pendingClaims(state) else 0
        if (count != claimableMissions) {
            claimableMissions = count
            missionBadgeText = count.toString()
        }
    }

    private companion object {
        const val RESUME_YES = 20
        const val RESUME_NO = 21
    }

    // logo
    private val logoFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val logoLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private var logoSize = 40f
    private var logoY = 0f
    private var subY = 0f
    /** Pionowy pas między podtytułem i pierwszym rzędem menu, dla powiadomień. */
    internal val noticeTop: Float get() = subY + max(10 * dp, logoSize * 0.26f)
    internal val noticeBottom: Float get() = campaign.r.top - 6 * dp
    private val plate = RectF()
    private var plateRight = 0f

    override fun layout() {
        campaign.label = L["campaign"]; endless.label = L["endless"]; daily.label = L["daily"]
        skirmish.label = L["skirmish"]; research.label = L["research"]; rankings.label = L["records"]; settings.label = ""
        campaign.iconId = Icons.MAP; endless.iconId = Icons.TOWER; daily.iconId = Icons.CALENDAR
        skirmish.iconId = Icons.CROSSED_SWORDS; research.iconId = Icons.FLASK; rankings.iconId = Icons.TROPHY
        missions.label = L["ms_missions"]; missions.iconId = Icons.MISSIONS
        settings.iconId = Icons.GEAR
        codex.label = ""; codex.iconId = Icons.CODEX
        settings.tooltipTitle = L["settings"]; settings.tooltipBody = ""
        codex.tooltipTitle = L["codex"]; codex.tooltipBody = ""
        campaign.subIcon = Icons.STAR
        research.subIcon = Icons.MEDAL
        campaign.highlight = true

        val availW = w - g.insetL - g.insetR - 32 * dp
        // Wąski ekran zewnętrzny Folda: pełna, pojedyncza kolumna zamiast miniaturowej siatki.
        val singleColumn = w - g.insetL - g.insetR < 420 * dp &&
            h - g.insetT - g.insetB > 620 * dp
        val gap = (if (singleColumn) 16f else 12f) * dp
        val bw = if (singleColumn) min(310 * dp, availW)
            else min(min(260 * dp, w * 0.36f), (availW - gap) / 2)
        val bh = if (singleColumn) min(64 * dp, (h - g.insetT - g.insetB) * 0.09f)
            else min(56 * dp, (h - g.insetT - g.insetB) * 0.13f)
        val mh = 48 * dp
        val gridH = if (singleColumn) 6 * (bh + gap) + mh else bh * 3 + gap * 3 + mh

        // logo: wielkość wg szerokości i wysokości ekranu
        logoFill.typeface = ui.heavy; logoLine.typeface = ui.heavy
        logoFill.textSize = 100f
        val ratio = logoFill.measureText(L["title"]) / 100f
        val layoutLogoSize = max(26 * dp,
            min(min(availW * 0.62f, 560 * dp) / ratio,
                (h - g.insetT - g.insetB - gridH - 56 * dp) / 2.8f))
        // Zachowujemy wysokość nagłówka, lecz zmniejszamy sam znak:
        // pod podtytułem powstaje miejsce na baner serii logowań.
        val compact = h - g.insetT - g.insetB <= 530 * dp
        logoSize = max(26 * dp, layoutLogoSize * (if (compact) 0.65f else 0.83f))
        logoFill.textSize = logoSize; logoLine.textSize = logoSize

        val headerH = layoutLogoSize * 2.75f
        val freeTop = g.insetT + 8 * dp
        val bottomPad = g.insetB + 22 * dp
        val total = headerH + gridH + 14 * dp
        val startY = freeTop + max(0f, (h - freeTop - bottomPad - total) *
            (if (singleColumn) 0.45f else 0.3f))
        logoY = startY + logoSize * 1.75f
        subY = logoY + logoSize * 0.62f
        val top = startY + headerH + 14 * dp

        logoFill.shader = LinearGradient(0f, logoY - logoSize * 0.75f, 0f, logoY,
            intArrayOf(Color.rgb(255, 248, 196), Color.rgb(255, 206, 60), Color.rgb(232, 132, 28)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

        val cx = w / 2 + (g.insetL - g.insetR) / 2
        if (singleColumn) {
            val left = cx - bw / 2
            campaign.set(left, top, bw, bh)
            endless.set(left, top + (bh + gap), bw, bh)
            daily.set(left, top + 2 * (bh + gap), bw, bh)
            skirmish.set(left, top + 3 * (bh + gap), bw, bh)
            research.set(left, top + 4 * (bh + gap), bw, bh)
            rankings.set(left, top + 5 * (bh + gap), bw, bh)
            missions.set(left, top + 6 * (bh + gap), bw, mh)
        } else {
            campaign.set(cx - bw - gap / 2, top, bw, bh)
            endless.set(cx + gap / 2, top, bw, bh)
            daily.set(cx - bw - gap / 2, top + bh + gap, bw, bh)
            skirmish.set(cx + gap / 2, top + bh + gap, bw, bh)
            research.set(cx - bw - gap / 2, top + (bh + gap) * 2, bw, bh)
            rankings.set(cx + gap / 2, top + (bh + gap) * 2, bw, bh)
            missions.set(cx - bw - gap / 2, top + (bh + gap) * 3, bw * 2 + gap, mh)
        }
        val sb = 48 * dp
        settings.set(w - g.insetR - sb - 12 * dp, g.insetT + 10 * dp, sb, sb)
        codex.set(settings.r.left - sb - 12 * dp, g.insetT + 10 * dp, sb, sb)
        layoutResume(cx)
        refreshMissionBadge()
    }

    private fun layoutResume(cx: Float) {
        resumeYes.label = L["resume_yes"]; resumeYes.iconId = Icons.PLAY; resumeYes.highlight = true
        resumeNo.label = L["resume_no"]; resumeNo.iconId = Icons.CLOSE; resumeNo.color = Ui.BTN_RED
        val pw = min(440 * dp, w - g.insetL - g.insetR - 32 * dp)
        val ph = min(230 * dp, h - g.insetT - g.insetB - 24 * dp)
        val cy = (g.insetT + h - g.insetB) / 2
        resumePanel.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2)
        val gap = 14 * dp
        val bw = (pw - 48 * dp - gap) / 2
        val bh = 50 * dp
        val by = resumePanel.bottom - bh - 20 * dp
        resumeNo.set(resumePanel.left + 24 * dp, by, bw, bh)
        resumeYes.set(resumeNo.r.right + gap, by, bw, bh)
        val descWidth = pw - 48 * dp
        if (resumeDescWidth != descWidth || resumeDescLanguage != L.lang ||
            resumeDescScale != ui.textScale) {
            resumeDescWidth = descWidth
            resumeDescScale = ui.textScale
            resumeDescLanguage = L.lang
            resumeDescCount = HintFrame.wrapInto(ui, L["resume_desc"], descWidth, 14f, resumeDescLines)
        }
        resumeInfo = g.resumeOffer?.let { o ->
            val b = o.battle
            val mode = when (b.cfg.mode) {
                Mode.CAMPAIGN -> L.f("stage_n", b.cfg.stageIndex + 1)
                Mode.ENDLESS -> L["endless"] + " · " + L.f("wave", b.wave)
                Mode.DAILY -> L["daily"]
                Mode.SKIRMISH -> L["skirmish"]
            }
            val s = b.time.toInt()
            mode + " · " + L["time"] + " " + String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
        } ?: ""
        updateResumeVisibility()
    }

    private fun updateResumeVisibility() {
        val show = g.resumeOffer != null
        resumeYes.visible = show; resumeNo.visible = show
    }

    override fun update(dt: Float) {
        this.dt = dt
        missionBadgePoll -= dt
        if (missionBadgePoll <= 0f) {
            missionBadgePoll = 10f
            refreshMissionBadge()
        }
        updateResumeVisibility()
        val pr = g.profile
        val dailyFree = pr.lastRankedDaily != com.centurywar.game.Daily.dayNumber()
        daily.sub = if (dailyFree) L["daily_free"] else ""
        daily.subIcon = if (dailyFree) Icons.STAR else -1
        // Podpisy budujemy tylko po zmianie wartości (bez nowych Stringów w każdej klatce).
        val key = (pr.medals.toLong() shl 32) xor (pr.totalStars().toLong() shl 20) xor pr.bestWave.toLong() xor (L.lang.toLong() shl 56)
        if (key != subKey || research.sub.isEmpty()) {
            subKey = key
            research.sub = L.plural("medals_n", pr.medals.toLong())
            campaign.sub = "${pr.totalStars()}/${pr.stars.size * 3}"
            endless.sub = if (pr.bestWave > 0) "${L["best"]}: ${pr.bestWave}" else ""
        }
    }

    private var subKey = Long.MIN_VALUE
    private var shownMedals = Int.MIN_VALUE
    private var shownStars = Int.MIN_VALUE
    private var medalsText = ""
    private var starsText = ""

    override fun onLanguageChanged() {
        subKey = Long.MIN_VALUE
        super.onLanguageChanged()
    }

    override fun render(c: Canvas) {
        val detail = g.quality.menuDetail
        val animated = g.quality.menuAnimated && !g.profile.reduceFlash
        val time = bg.draw(c, w, h, dt, animated, detail)
        battleScene.draw(c, w, h, time, animated, detail)
        drawLogo(c)
        // Pętla po indeksie: iterator ArrayList byłby alokacją na każdą klatkę.
        for (i in buttons.indices) {
            val b = buttons[i]
            if (b !== resumeYes && b !== resumeNo) ui.button(c, b, if (b.label.isEmpty()) 20f else 17f)
        }
        drawPlayer(c)
        if (claimableMissions > 0) {
            val bx = missions.r.right - 15 * dp
            val by = missions.r.top + 6 * dp
            ui.fill.color = Ui.OUTLINE
            c.drawCircle(bx, by, 15 * dp, ui.fill)
            ui.fill.color = Color.rgb(210, 59, 52)
            c.drawCircle(bx, by, 12.5f * dp, ui.fill)
            ui.textO(c, missionBadgeText, bx, by + 5 * dp, 15f,
                Ui.TEXT, Paint.Align.CENTER)
        }
        ui.textO(c, L["codex"], codex.r.centerX(), codex.r.bottom + 13 * dp,
            ui.fit(L["codex"], 10f, 64 * dp), Ui.TEXT, Paint.Align.CENTER)
        ui.textO(c, L["settings"], settings.r.centerX(), settings.r.bottom + 13 * dp,
            ui.fit(L["settings"], 10f, 64 * dp), Ui.TEXT, Paint.Align.CENTER)
        drawHeldTooltip(c)
        if (g.resumeOffer != null) drawResume(c)
    }

    private fun drawResume(c: Canvas) {
        c.drawColor(Color.argb(160, 0, 0, 0))
        val p = resumePanel
        ui.panel(c, p)
        ui.ribbon(c, p.centerX(), p.top + 4 * dp, L["resume_title"], 22f, Ui.RIBBON, p.width() * 0.6f, Ui.GOLD, p.width() + 20 * dp)
        var y = p.top + 52 * dp
        for (i in 0 until resumeDescCount) {
            ui.text(c, resumeDescLines[i] ?: "", p.centerX(), y, 14f, Ui.TEXT, Paint.Align.CENTER)
            y += 14 * dp * ui.textScale * 1.3f
        }
        if (resumeInfo.isNotEmpty()) {
            ui.textO(c, resumeInfo, p.centerX(), resumeYes.r.top - 16 * dp, ui.fit(resumeInfo, 15f, p.width() - 40 * dp), Ui.GOLD, Paint.Align.CENTER)
        }
        ui.button(c, resumeNo, 17f)
        ui.button(c, resumeYes, 17f)
    }

    override fun onBack(): Boolean {
        // Wstecz tylko chowa pytanie; zrzut zostaje (zapyta przy następnym uruchomieniu).
        if (g.resumeOffer != null) { g.resumeOffer = null; return true }
        return false
    }

    private fun drawLogo(c: Canvas) {
        val cx = w / 2 + (g.insetL - g.insetR) / 2
        val s = logoSize
        val title = L["title"]
        val midY = logoY - s * 0.36f
        // emblemat: skrzyżowane miecze za tarczą, nad napisem
        emblem.draw(c, cx, midY - s * 0.88f, s * 1.15f)
        // cień, gruby kontur, ekstruzja (fazowanie), złote wypełnienie, połysk
        logoLine.color = Color.argb(140, 0, 0, 0)
        logoLine.strokeWidth = s * 0.2f
        c.drawText(title, cx, logoY + s * 0.1f, logoLine)
        logoLine.color = Ui.OUTLINE
        c.drawText(title, cx, logoY + s * 0.06f, logoLine)
        c.drawText(title, cx, logoY, logoLine)
        val shader = logoFill.shader
        logoFill.shader = null
        logoFill.color = Color.rgb(150, 70, 20)
        c.drawText(title, cx, logoY + s * 0.06f, logoFill)
        logoFill.shader = shader
        c.drawText(title, cx, logoY, logoFill)
        logoLine.color = Color.argb(150, 255, 255, 230)
        logoLine.strokeWidth = s * 0.025f
        c.save(); c.clipRect(0f, logoY - s, w, logoY - s * 0.45f)
        c.drawText(title, cx, logoY - s * 0.02f, logoLine)
        c.restore()
        // podtytuł na wstędze
        ui.ribbon(c, cx, subY, L["subtitle"], min(16f, s / dp * 0.3f), Ui.RIBBON, s * 4.2f, Ui.TEXT)
    }

    private fun drawPlayer(c: Canvas) {
        val pr = g.profile
        val l = g.insetL + 12 * dp
        val t = g.insetT + 10 * dp
        val ph = 48 * dp
        val nameW = ui.measure(pr.name, 15f, true)
        val stars = pr.totalStars()
        if (shownMedals != pr.medals) { shownMedals = pr.medals; medalsText = pr.medals.toString() }
        if (shownStars != stars) { shownStars = stars; starsText = stars.toString() }
        val statsW = ui.measure(medalsText, 13f, true) + ui.measure(starsText, 13f, true) + 52 * dp
        val pw = ph + max(nameW, statsW) + 22 * dp
        plate.set(l + ph * 0.4f, t + 3 * dp, l + pw, t + ph - 3 * dp)
        ui.card(c, plate, Color.argb(235, 34, 42, 62), 14f)
        ui.medallion(c, l + ph / 2, t + ph / 2, ph / 2, Ui.PLAYER, Icons.HELMET)
        val tx = l + ph + 8 * dp
        ui.textO(c, pr.name, tx, t + 21 * dp, 15f, Ui.TEXT)
        val iy = t + 33 * dp
        Icons.draw(c, Icons.MEDAL, tx + 7 * dp, iy, 15 * dp, dp = dp)
        ui.textO(c, medalsText, tx + 17 * dp, iy + 4.5f * dp, 13f, Ui.GOLD)
        val sx = tx + 26 * dp + ui.measure(medalsText, 13f, true)
        Icons.draw(c, Icons.STAR, sx + 7 * dp, iy, 15 * dp, dp = dp)
        ui.textO(c, starsText, sx + 17 * dp, iy + 4.5f * dp, 13f, Ui.GOLD)
        plateRight = plate.right
    }

    override fun onClick(b: Button) {
        val offer = g.resumeOffer
        if (offer != null) {
            when (b.id) {
                RESUME_YES -> {
                    g.resumeOffer = null
                    g.setScreen(BattleScreen(g, offer.battle.cfg, offer.rankedDaily, offer))
                }
                RESUME_NO -> {
                    g.resumeOffer = null
                    g.snapshots?.delete()
                }
            }
            return // okno pytania blokuje resztę menu
        }
        when (b.id) {
            1 -> g.push(CampaignScreen(g))
            2 -> g.push(ModeScreen(g, Mode.ENDLESS))
            3 -> g.push(ModeScreen(g, Mode.DAILY))
            4 -> g.push(SkirmishSetupScreen(g))
            5 -> g.push(ResearchScreen(g))
            6 -> g.push(RankingsScreen(g))
            7 -> g.push(SettingsScreen(g))
            8 -> g.push(CodexScreen(g))
            9 -> g.push(MissionsScreen(g))
        }
    }
}
