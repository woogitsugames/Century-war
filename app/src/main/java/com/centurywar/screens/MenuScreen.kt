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
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.Ui
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.game.SaveGuard
import com.centurywar.meta.Missions
import com.centurywar.meta.NextGoalKind
import com.centurywar.meta.NextGoals
import com.centurywar.meta.PlaytestLog
import com.centurywar.meta.TesterReportReminder
import com.centurywar.meta.TesterSurvey
import kotlin.math.max
import kotlin.math.min

class MenuScreen(g: Game) : Screen(g) {
    override val crashName get() = "MenuScreen"
    /** W menu Wstecz przechwytujemy tylko przy otwartym oknie. */
    val hasOpenModal get() = g.resumeOffer != null || surveyStep != SURVEY_HIDDEN
    /** R11 (pozycja 7): ankieta zasłania menu, więc banery czekają na jej zamknięcie. */
    override val blocksNotices: Boolean get() = surveyStep != SURVEY_HIDDEN
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
    /** Dzień 62: Rozkazy sztabu — w poziomie obok Misji (ten sam rząd), w pionie osobny kafel. */
    internal val orders = btn(ORDERS)
    /** Dzień 2: „Co nowego” po aktualizacji, dopóki gracz nie otworzy historii zmian. */
    internal val whatsNew = btn(WHATS_NEW).also { it.visible = false }
    private var whatsNewSeenRef: String? = null
    /** R2-01: jedna linia „następnego celu” pod kaflami (po samouczku, gdy jest miejsce). */
    internal val goalBtn = btn(GOAL).also { it.visible = false }
    /** D48 (R13 TOP 5 poz. 3): nieblokujący baner „wyślij Raport dla testera” (3. i 10. dzień). */
    internal val reportReminder = btn(REPORT_REMINDER).also { it.visible = false }
    /** Próg dnia pokazanego banera (3 albo 10); 0 = brak. */
    internal var reminderDay = 0
        private set
    /** D57: baner 10. dnia prowadzi najpierw do ankiety końcowej, potem do raportu (jeden baner). */
    internal var reminderFinal = false
        private set
    private var reminderQuiet = 0f
    private var reminderText = ""
    internal var goalKind = NextGoalKind.NONE
        private set
    /** R10-02: argument celu (indeks etapu dla HARD_STAGE). */
    internal var goalArg = -1
    internal var goalText = ""
        private set
    private var goalSize = 13f
    private val portraitTiles = arrayOf(campaign, endless, daily, skirmish, orders, research,
        rankings, missions, settings, codex)
    private val scrollCue = ScrollCue()
    internal var portraitMenu = false
        private set
    internal var portraitScroll = 0f
        private set
    internal var portraitMaxScroll = 0f
        private set
    internal var portraitTop = 0f
        private set
    internal var portraitBottom = 0f
        private set
    private var portraitLeft = 0f
    private var portraitWidth = 0f
    private var portraitTileHeight = 0f
    private var portraitGap = 0f
    // pytanie o wznowienie przerwanej bitwy (g.resumeOffer)
    private val resumeYes = btn(RESUME_YES)
    private val resumeNo = btn(RESUME_NO)
    private val resumePanel = RectF()
    /** Zlecenie 141: jednorazowy dymek prowadzący do Doktryny elit. */
    private val hints = HintOverlay(g)
    // R11 (pozycja 7): jednorazowa ankieta testera (ocena 1–5, potem jedno pytanie).
    internal val surveyRates = Array(5) { btn(SURVEY_RATE + it) }
    internal val surveyIssues = Array(TesterSurvey.ISSUES) { btn(SURVEY_ISSUE + it) }
    internal val surveySkip = btn(SURVEY_SKIP)
    // D57 (R13 TOP 5 poz. 5): ankieta końcowa — polecenie 0–10, potem dwa pytania z listy.
    internal val finalNps = Array(PlaytestLog.FINAL_NPS_MAX + 1) { btn(SURVEY_NPS + it) }
    internal val finalOptions = Array(TesterSurvey.FINAL_OPTIONS) { btn(SURVEY_OPT + it) }
    internal var surveyStep = SURVEY_HIDDEN
        private set
    internal val surveyPanel = RectF()
    private var surveyQuiet = 0f
    private val surveyNoteLines = arrayOfNulls<String>(4)
    private var surveyNoteCount = 0
    private var surveyTitle = ""
    private var surveyScale = ""
    private var surveyNoteY = 0f
    private var surveyScaleY = 0f
    init {
        // Ankieta leży nad kafelkami: jej przyciski dostają dotyk przed menu.
        for (b in surveyRates) { buttons.remove(b); buttons.add(0, b) }
        for (b in surveyIssues) { buttons.remove(b); buttons.add(0, b) }
        for (b in finalNps) { buttons.remove(b); buttons.add(0, b) }
        for (b in finalOptions) { buttons.remove(b); buttons.add(0, b) }
        buttons.remove(surveySkip); buttons.add(0, surveySkip)
        hideSurveyButtons()
        buttons.add(0, hints.okBtn)
        // Baner przypomnienia leży w pasie powiadomień nad kafelkami: dotyk dostaje pierwszy.
        buttons.remove(reportReminder); buttons.add(0, reportReminder)
        // Okno „Wznowić bitwę?” leży nad kafelkami menu: jego przyciski muszą dostać dotyk pierwsze,
        // inaczej tap w „Porzuć” trafiał w zasłonięty kafelek i był ignorowany.
        buttons.remove(resumeNo); buttons.remove(resumeYes)
        buttons.add(0, resumeNo); buttons.add(0, resumeYes)
    }
    /** Stać gracza na Doktrynę elit, a jej nie ma: plakietka „!” na Badaniach. */
    internal var eliteReady = false
        private set
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
        val now = g.nowMillis()
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

    private fun firstTutorialReady(): Boolean = !g.profile.tutorialDone &&
        g.profile.battles == 0 && g.profile.unlockedStage() == 0

    private companion object {
        const val RESUME_YES = 20
        const val RESUME_NO = 21
        const val WHATS_NEW = 10
        const val GOAL = 11
        const val REPORT_REMINDER = 12
        /** Dzień 62: kafel Rozkazów sztabu. */
        const val ORDERS = 13
        /** Najmniejszy napis celu (sp); przy braku miejsca linia znika zamiast maleć. */
        const val GOAL_MIN_SP = 11f
        const val WHATS_NEW_H = 40f
        const val SURVEY_RATE = 30
        const val SURVEY_ISSUE = 40
        const val SURVEY_SKIP = 45
        /** D57: przyciski 0–10 (46–56) i odpowiedzi z listy (57–62) ankiety końcowej. */
        const val SURVEY_NPS = 46
        const val SURVEY_OPT = 57
        const val SURVEY_LAST = 62
        const val SURVEY_HIDDEN = 0
        const val SURVEY_RATING = 1
        const val SURVEY_QUESTION = 2
        const val SURVEY_FINAL_NPS = 3
        const val SURVEY_FINAL_LIKED = 4
        const val SURVEY_FINAL_ISSUE = 5
        val SURVEY_ISSUE_KEYS = arrayOf("t200_issue_hard", "t200_issue_long", "t200_issue_unclear", "t200_issue_none")
        /** D57: kolejność jak [PlaytestLog.FINAL_LIKED] i [PlaytestLog.FINAL_ISSUES]. */
        val FINAL_LIKED_KEYS = arrayOf("tr205_like_campaign", "tr205_like_battles", "tr205_like_eras",
            "tr205_like_daily", "tr205_like_looks", "tr205_like_none")
        val FINAL_ISSUE_KEYS = arrayOf("t200_issue_hard", "tr205_issue_easy", "t200_issue_long",
            "t200_issue_unclear", "tr205_issue_slow", "t200_issue_none")
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
    internal val noticeBottom: Float get() = (if (portraitMenu) portraitTop else campaign.r.top) - 6 * dp
    private val plate = RectF()
    private var plateRight = 0f

    override fun layout() {
        campaign.label = L[if (firstTutorialReady()) "first_play" else "campaign"]
        endless.label = L["endless"]; daily.label = L["daily"]
        skirmish.label = L["skirmish"]; research.label = L["research"]; rankings.label = L["records"]; settings.label = ""
        campaign.iconId = Icons.MAP; endless.iconId = Icons.TOWER; daily.iconId = Icons.CALENDAR
        skirmish.iconId = Icons.CROSSED_SWORDS; research.iconId = Icons.FLASK; rankings.iconId = Icons.TROPHY
        missions.label = L["ms_missions"]; missions.iconId = Icons.MISSIONS
        orders.label = L["o209_menu"]; orders.iconId = Icons.FLAG
        settings.iconId = Icons.GEAR
        codex.label = ""; codex.iconId = Icons.CODEX
        settings.tooltipTitle = L["settings"]; settings.tooltipBody = ""
        codex.tooltipTitle = L["codex"]; codex.tooltipBody = ""
        campaign.subIcon = if (firstTutorialReady()) -1 else Icons.STAR
        research.subIcon = Icons.MEDAL
        campaign.highlight = true

        // Android może przydzielić pionowe lub bardzo wąskie okno mimo sensorLandscape.
        // Prawie kwadratowy rozłożony Fold (np. 1968x2184) zachowuje zatwierdzony układ kafelków.
        portraitMenu = h > w * 1.3f || w - g.insetL - g.insetR < 520 * dp
        // Dzień 62: licznik gwiazdek sztabu tylko na pionowym kaflu (poziomy rząd ma 48 dp).
        orders.sub = if (portraitMenu)
            "${g.profile.orderStarTotal()}/${com.centurywar.game.Orders.COUNT * com.centurywar.game.Orders.MAX_STARS}" else ""
        orders.subIcon = if (portraitMenu) Icons.STAR else -1
        if (portraitMenu) {
            layoutPortrait()
            layoutResume(w / 2 + (g.insetL - g.insetR) / 2)
            layoutSurvey()
            layoutReminder()
            refreshMissionBadge()
            refreshGoal()
            return
        }
        for (b in portraitTiles) b.visible = true

        val availW = w - g.insetL - g.insetR - 32 * dp
        // Wąski ekran zewnętrzny Folda: pełna, pojedyncza kolumna zamiast miniaturowej siatki.
        val singleColumn = w - g.insetL - g.insetR < 420 * dp &&
            h - g.insetT - g.insetB > 620 * dp
        val gap = (if (singleColumn) 16f else 12f) * dp
        val bw = if (singleColumn) min(310 * dp, availW)
            else min(min(260 * dp, w * 0.36f), (availW - gap) / 2)
        val bh = if (singleColumn) min(64 * dp, (h - g.insetT - g.insetB) * 0.09f)
            // Tura 10: na 720p (360 dp) wychodziło 46,8 dp — dolna granica 48 dp celu dotyku.
            else min(56 * dp, max(48 * dp, (h - g.insetT - g.insetB) * 0.13f))
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

        // Odrębny pas na komunikat logowania; nie zasłania pierwszych przycisków.
        val headerH = layoutLogoSize * 2.75f +
            (if (w - g.insetL - g.insetR < 560 * dp) 24 * dp else 0f)
        val freeTop = g.insetT + 8 * dp
        val bottomPad = g.insetB + 22 * dp
        val total = headerH + gridH + 14 * dp
        val startY = freeTop + max(0f, (h - freeTop - bottomPad - total) *
            (if (singleColumn) 0.45f else 0.3f))
        logoY = startY + logoSize * 1.75f
        subY = logoY + logoSize * 0.62f
        val baseTop = startY + headerH + 14 * dp
        // Na 720p zachowujemy odstęp między wstęgą a kafelkiem Kampania.
        val bannerNeed = subY + max(10 * dp, logoSize * 0.26f) + 24 * dp + 6 * dp - baseTop
        val roomBelow = h - g.insetB - 10 * dp - (baseTop + gridH)
        val top = baseTop + bannerNeed.coerceIn(0f, max(0f, roomBelow))

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
            val half = (bw - gap) / 2
            missions.set(left, top + 6 * (bh + gap), half, mh)
            orders.set(left + half + gap, top + 6 * (bh + gap), bw - half - gap, mh)
        } else {
            campaign.set(cx - bw - gap / 2, top, bw, bh)
            endless.set(cx + gap / 2, top, bw, bh)
            daily.set(cx - bw - gap / 2, top + bh + gap, bw, bh)
            skirmish.set(cx + gap / 2, top + bh + gap, bw, bh)
            research.set(cx - bw - gap / 2, top + (bh + gap) * 2, bw, bh)
            rankings.set(cx + gap / 2, top + (bh + gap) * 2, bw, bh)
            missions.set(cx - bw - gap / 2, top + (bh + gap) * 3, bw, mh)
            orders.set(cx + gap / 2, top + (bh + gap) * 3, bw, mh)
        }
        val sb = 48 * dp
        settings.set(w - g.insetR - sb - 12 * dp, g.insetT + 10 * dp, sb, sb)
        codex.set(settings.r.left - sb - 12 * dp, g.insetT + 10 * dp, sb, sb)
        // W rzędzie Kodeksu i Ustawień, na lewo od Kodeksu (między logo a ikonami).
        layoutWhatsNew(codex.r.left - 12 * dp, landscape = true)
        layoutResume(cx)
        layoutSurvey()
        layoutReminder()
        refreshMissionBadge()
        refreshGoal()
    }

    /**
     * R2-01: cel pod kaflami. Misje mają już czerwoną plakietkę, więc tu następny cel w kolejności.
     * Wołane przy layout(), zmianie języka i co 10 s; nigdy w każdej klatce.
     */
    private fun refreshGoal() {
        val b = goalBtn
        b.visible = false
        goalKind = NextGoalKind.NONE
        if (portraitMenu || !g.profile.tutorialDone || missions.r.isEmpty) return
        val goal = NextGoals.pick(g.profile, g.nowMillis(), null, skipMissions = true)
        if (goal.kind == NextGoalKind.NONE) return
        val top = missions.r.bottom + 6 * dp
        val bottom = h - g.insetB - 4 * dp
        val bh = min(30 * dp, bottom - top)
        if (bh < 20 * dp) return
        // Dzień 62: wiersz celu pod całym rzędem Misji i Rozkazów.
        val bw = orders.r.right - missions.r.left
        val textW = bw - 44 * dp
        val size = ui.fit(goal.text, 13f, textW, 1f)
        if (size * ui.textScale < GOAL_MIN_SP) return
        goalText = goal.text
        goalKind = goal.kind
        goalArg = goal.arg
        goalSize = size
        b.label = ""
        b.set(missions.r.left, top, bw, bh)
        // Cel dotyku 48 dp; w górę nie sięga na kafel Misji (jest wcześniej na liście przycisków).
        b.hitSlop = ((48 * dp - bh) / 2f).coerceAtLeast(0f)
        b.visible = true
    }

    /** Szerokość przycisku „Co nowego”: medalion z ikoną + napis 14 sp. */
    private fun whatsNewWidth(): Float {
        val h = WHATS_NEW_H * dp
        return min(190 * dp, h * 0.88f + 16 * dp + ui.measure(whatsNew.label, 14f, true))
    }

    private fun layoutWhatsNew(right: Float, landscape: Boolean) {
        whatsNew.label = L["menu175_whats_new"]
        whatsNew.iconId = Icons.BOOK
        whatsNew.tooltipTitle = ""
        val bw = whatsNewWidth()
        val bh = WHATS_NEW_H * dp
        // Cel dotyku 48 dp przy niższym (40 dp) przycisku.
        whatsNew.hitSlop = 4 * dp
        if (landscape) {
            whatsNew.set(right - bw, g.insetT + 14 * dp, bw, bh)
            // Gdy logo sięga za daleko w prawo, przycisk schodzi pod Kodeks i Ustawienia.
            val logoRight = w / 2 + (g.insetL - g.insetR) / 2 + logoFill.measureText(L["title"]) / 2
            if (whatsNew.r.left < logoRight + 8 * dp)
                whatsNew.set(settings.r.right - bw, settings.r.bottom + 22 * dp, bw, bh)
        } else {
            // Pion: w rzędzie tabliczki gracza; przy długim imieniu (do 16 znaków) sama ikona
            // z czerwoną kropką (opis TalkBack bez zmian), bo niżej leży logo.
            val rowTop = g.insetT + 14 * dp
            whatsNew.set(right - bw, rowTop, bw, bh)
            if (whatsNew.r.left < playerPlateRight() + 10 * dp) {
                whatsNew.label = ""
                whatsNew.set(right - bh, rowTop, bh, bh)
            }
        }
        refreshWhatsNew(force = true)
    }

    /** Prawa krawędź tabliczki gracza (te same wymiary co w [drawPlayer]). */
    private fun playerPlateRight(): Float {
        val pr = g.profile
        val l = g.insetL + 12 * dp
        val ph = 48 * dp
        val stats = ui.measure(pr.medals.toString(), 13f, true) +
            ui.measure(pr.totalStars().toString(), 13f, true) + 52 * dp
        return l + ph + max(ui.measure(pr.name, 15f, true), stats) + 22 * dp
    }

    /** Widoczność liczymy tylko po zmianie znacznika wersji (porównanie referencji, bez alokacji). */
    private fun refreshWhatsNew(force: Boolean = false) {
        val seen = g.profile.whatsNewSeenVersion
        if (!force && seen === whatsNewSeenRef) return
        whatsNewSeenRef = seen
        whatsNew.visible = WhatsNewScreen.unread(g)
    }

    /** Osobny układ pionowy; istniejąca geometria pozioma pozostaje nietknięta. */
    private fun layoutPortrait() {
        val safeW = w - g.insetL - g.insetR
        val cx = (w + g.insetL - g.insetR) / 2f
        portraitWidth = min(420 * dp, (safeW - 32 * dp).coerceAtLeast(1f))
        portraitLeft = cx - portraitWidth / 2f
        portraitGap = 10 * dp
        portraitTileHeight = max(56f, 56f * ui.textScale) * dp

        logoFill.typeface = ui.heavy
        logoLine.typeface = ui.heavy
        logoFill.textSize = 100f
        val ratio = (logoFill.measureText(L["title"]) / 100f).coerceAtLeast(1f)
        logoSize = min(34 * dp, max(24 * dp, (safeW - 32 * dp) / ratio))
        logoY = g.insetT + 122 * dp
        subY = logoY + logoSize * 0.62f
        logoFill.textSize = logoSize
        logoLine.textSize = logoSize
        logoFill.shader = LinearGradient(0f, logoY - logoSize * 0.75f, 0f, logoY,
            intArrayOf(Color.rgb(255, 248, 196), Color.rgb(255, 206, 60),
                Color.rgb(232, 132, 28)), floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP)

        // Baner powiadomienia ma własne 50 dp między logotypem i przewijaną listą.
        portraitBottom = h - g.insetB - 12 * dp
        portraitTop = min(subY + 69 * dp, portraitBottom - 48 * dp)
        val contentHeight = portraitTiles.size * portraitTileHeight +
            (portraitTiles.size - 1) * portraitGap + 12 * dp
        portraitMaxScroll = max(0f, contentHeight - (portraitBottom - portraitTop))
        portraitScroll = portraitScroll.coerceIn(0f, portraitMaxScroll)

        settings.label = L["settings"]
        codex.label = L["codex"]
        positionPortraitTiles()
        layoutWhatsNew(w - g.insetR - 12 * dp, landscape = false)
    }

    /** Pozycje są przeliczane przy przeciąganiu, bez tworzenia obiektów w klatce. */
    private fun positionPortraitTiles() {
        val step = portraitTileHeight + portraitGap
        val firstY = portraitTop + 6 * dp - portraitScroll
        for (i in portraitTiles.indices) {
            val b = portraitTiles[i]
            b.set(portraitLeft, firstY + i * step, portraitWidth, portraitTileHeight)
            b.visible = b.r.bottom > portraitTop && b.r.top < portraitBottom
        }
    }

    override fun isDragScreen() = portraitMenu

    override fun onDrag(dx: Float, dy: Float) {
        if (!portraitMenu || g.resumeOffer != null) return
        val next = (portraitScroll - dy).coerceIn(0f, portraitMaxScroll)
        if (next == portraitScroll) return
        portraitScroll = next
        positionPortraitTiles()
        hints.onAction()
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (!portraitMenu || hasOpenModal || hints.okBtn.visible) return false
        if (whatsNew.visible && whatsNew.contains(e.x, e.y)) return false
        if (reportReminder.visible && reportReminder.contains(e.x, e.y)) return false
        if (e.action == TouchEvent.DOWN && (e.y < portraitTop || e.y >= portraitBottom))
            return true
        if (e.action == TouchEvent.UP && (e.y < portraitTop || e.y >= portraitBottom))
            for (b in portraitTiles) b.pressed = false
        return false
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
                Mode.ORDERS -> L["o209_title"] + " " + (b.cfg.stageIndex + 1)
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
        if (SaveGuard.takeWarning()) g.showNotice(L["save154_problem"], Icons.CLOSE)
        // R10-01: bitwa przerwana aktualizacją gry (rozliczenie raz, potem baner).
        if (g.lostBattle != null) g.settleLostBattle()
        // R10-07: powitanie po przerwie, zawsze po banerach R10-01.
        g.checkWelcomeBack()
        missionBadgePoll -= dt
        if (missionBadgePoll <= 0f) {
            missionBadgePoll = 10f
            refreshMissionBadge()
            refreshGoal()
        }
        updateResumeVisibility()
        refreshWhatsNew()
        val pr = g.profile
        eliteReady = EliteUnlock.affordable(pr)
        hints.update(dt)
        updateSurvey(dt)
        updateReminder(dt)
        if (eliteReady && !hasOpenModal && research.visible && research.r.width() > 0f)
            hints.showScreen(HintOverlay.ELITE_RESEARCH, research.r)
        val dailyFree = pr.lastRankedDaily != com.centurywar.game.Daily.dayNumber(g.nowMillis())
        daily.sub = if (dailyFree) L["daily_free"] else ""
        daily.subIcon = if (dailyFree) Icons.STAR else -1
        // Podpisy budujemy tylko po zmianie wartości (bez nowych Stringów w każdej klatce).
        val key = (pr.medals.toLong() shl 32) xor (pr.totalStars().toLong() shl 20) xor pr.bestWave.toLong() xor (L.lang.toLong() shl 56) xor
            (pr.hardStagesCleared().toLong() shl 44)
        if (key != subKey || research.sub.isEmpty()) {
            subKey = key
            research.sub = L.plural("medals_n", pr.medals.toLong())
            campaign.sub = if (firstTutorialReady()) L["first_goal"] else
                "${pr.totalStars()}/${pr.stars.size * 3}"
            // R10-02: po komplecie gwiazdek drugi licznik: korony Trudnego.
            val crowns = !firstTutorialReady() && pr.stars.isNotEmpty() && pr.stars.all { it > 0 }
            campaign.sub2 = if (crowns) "${pr.hardStagesCleared()}/${pr.stars.size}" else ""
            campaign.subIcon2 = if (crowns) Icons.CROWN else -1
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
        if (portraitMenu) {
            val saved = c.save()
            c.clipRect(g.insetL, portraitTop, w - g.insetR, portraitBottom)
            for (b in portraitTiles) if (b.visible) ui.button(c, b, 17f)
            drawTileBadges(c)
            c.restoreToCount(saved)
            scrollCue.vertical(c, w - g.insetR - 12 * dp, portraitTop, portraitBottom,
                portraitScroll, portraitMaxScroll, dp)
            drawPlayer(c)
            drawWhatsNew(c)
            drawReminder(c)
        } else {
            // Poziome menu rysujemy w dotychczasowej kolejności (wzorce PNG).
            for (i in buttons.indices) {
                val b = buttons[i]
                if (b !== resumeYes && b !== resumeNo && b !== hints.okBtn && b !== whatsNew && b !== goalBtn &&
                    b !== reportReminder &&
                    b.id !in SURVEY_RATE..SURVEY_LAST)
                    ui.button(c, b, if (b.label.isEmpty()) 20f else 17f)
            }
            drawGoal(c)
            drawReminder(c)
            drawWhatsNew(c)
            drawPlayer(c)
            drawTileBadges(c)
            ui.textO(c, L["codex"], codex.r.centerX(), codex.r.bottom + 13 * dp,
                ui.fit(L["codex"], 10f, 64 * dp), Ui.TEXT, Paint.Align.CENTER)
            ui.textO(c, L["settings"], settings.r.centerX(), settings.r.bottom + 13 * dp,
                ui.fit(L["settings"], 10f, 64 * dp), Ui.TEXT, Paint.Align.CENTER)
        }
        if (!hasOpenModal) hints.draw(c)
        drawHeldTooltip(c)
        if (g.resumeOffer != null) drawResume(c)
        else if (surveyStep != SURVEY_HIDDEN) drawSurvey(c)
    }

    /** R2-01: przyciemniony pasek z ikoną celu, złotym napisem i strzałką „dalej”. */
    private fun drawGoal(c: Canvas) {
        val b = goalBtn
        if (!b.visible) return
        val r = b.r
        ui.fill.color = if (b.pressed) Color.argb(200, 70, 58, 30) else Color.argb(170, 16, 20, 32)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, ui.fill)
        Icons.draw(c, BattleResultsPanel.GOAL_ICONS[goalKind.ordinal], r.left + 18 * dp, r.centerY(),
            17 * dp, dp = dp)
        ui.textO(c, goalText, r.left + 32 * dp, r.centerY() + goalSize * 0.36f * dp * ui.textScale,
            goalSize, Ui.GOLD, Paint.Align.LEFT, 1.6f)
        ui.textO(c, "›", r.right - 12 * dp, r.centerY() + 6 * dp, 18f, Ui.TEXT, Paint.Align.CENTER)
    }

    private fun drawWhatsNew(c: Canvas) {
        if (!whatsNew.visible) return
        ui.button(c, whatsNew, 14f)
        // Czerwona kropka „nieprzeczytane” w rogu, jak plakietki kafelków.
        val bx = whatsNew.r.right - 4 * dp
        val by = whatsNew.r.top + 4 * dp
        ui.fill.color = Ui.OUTLINE
        c.drawCircle(bx, by, 7 * dp, ui.fill)
        ui.fill.color = Color.rgb(210, 59, 52)
        c.drawCircle(bx, by, 5 * dp, ui.fill)
    }

    private fun drawTileBadges(c: Canvas) {
        if (eliteReady && research.visible) {
            val bx = research.r.right - 15 * dp
            val by = research.r.top + 6 * dp
            ui.fill.color = Ui.OUTLINE
            c.drawCircle(bx, by, 15 * dp, ui.fill)
            ui.fill.color = Color.rgb(210, 59, 52)
            c.drawCircle(bx, by, 12.5f * dp, ui.fill)
            ui.textO(c, "!", bx, by + 5 * dp, 15f, Ui.TEXT, Paint.Align.CENTER)
        }
        if (claimableMissions > 0 && missions.visible) {
            val bx = missions.r.right - 15 * dp
            val by = missions.r.top + 6 * dp
            ui.fill.color = Ui.OUTLINE
            c.drawCircle(bx, by, 15 * dp, ui.fill)
            ui.fill.color = Color.rgb(210, 59, 52)
            c.drawCircle(bx, by, 12.5f * dp, ui.fill)
            ui.textO(c, missionBadgeText, bx, by + 5 * dp, 15f,
                Ui.TEXT, Paint.Align.CENTER)
        }
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

    /** Czytnik ekranu: przy otwartym oknie tylko jego przyciski mają węzły. */
    fun accessibilityEligibleButton(b: Button): Boolean = when {
        g.resumeOffer != null -> b === resumeYes || b === resumeNo
        surveyStep != SURVEY_HIDDEN -> b === surveySkip || b in surveyRates || b in surveyIssues ||
            b in finalNps || b in finalOptions
        else -> true
    }

    private fun hideSurveyButtons() {
        for (b in surveyRates) b.visible = false
        for (b in surveyIssues) b.visible = false
        for (b in finalNps) b.visible = false
        for (b in finalOptions) b.visible = false
        surveySkip.visible = false
    }

    /**
     * D48: przypomnienie o raporcie w 3. i 10. dniu testu (tylko wersje testowe). Najpierw ankieta,
     * banery i okna; potem po [TesterReportReminder.QUIET_SECONDS] spokoju baner w pasie powiadomień.
     * Pokazany próg zapisujemy od razu (raz na dzień progowy), baner zostaje do dotknięcia albo
     * wyjścia z menu, a chowa się, gdy nadejdzie powiadomienie albo otworzy się okno.
     */
    private fun updateReminder(dt: Float) {
        if (reminderDay == 0) {
            val log = g.playtest
            val quiet = log != null && g.testerSurveyEnabled && g.resumeOffer == null && g.lostBattle == null &&
                surveyStep == SURVEY_HIDDEN && !hints.okBtn.visible && !g.hasNotices() &&
                !TesterSurvey.due(g.testerSurveyEnabled, log.surveyState, g.profile.tutorialDone,
                    g.profile.battles, log.daysPlayed)
            var day = 0
            var final = false
            if (quiet && log != null) {
                val today = Missions.utcDay(g.nowMillis())
                if (TesterSurvey.finalPending(true, log.finalState, log.firstDay, today, log.reminders)) {
                    // D57: jeden baner 10. dnia (ankieta końcowa, potem raport). Po pierwszej ankiecie
                    // tego samego dnia czekamy do jutra: bez dwóch ankiet i dwóch banerów naraz.
                    final = TesterSurvey.finalDue(true, log.finalState, log.firstDay, today, log.reminders,
                        log.surveyDay)
                    if (final) day = TesterReportReminder.DAY_FINAL
                } else day = TesterReportReminder.due(true, log.firstDay, today, log.reminders)
            }
            if (day == 0) { reminderQuiet = 0f; return }
            reminderQuiet += dt
            if (reminderQuiet < TesterReportReminder.QUIET_SECONDS) return
            reminderQuiet = 0f
            reminderDay = day
            reminderFinal = final
            log!!.reminderShown(TesterReportReminder.mask(day) or (if (final) PlaytestLog.REMINDER_FINAL else 0))
            log.saveAsync()
            layoutReminder()
        }
        reportReminder.visible = reminderDay != 0 && !hasOpenModal && !hints.okBtn.visible &&
            !g.hasNotices() && reportReminder.r.width() > 0f
    }

    /** Ten sam prostokąt co baner powiadomienia w menu ([Game] rysuje go między logo a kafelkami). */
    private fun layoutReminder() {
        if (reminderDay == 0) { reportReminder.visible = false; return }
        reminderText = if (reminderFinal) L["tr205_banner"] else L.f("tr204_banner", reminderDay)
        reportReminder.label = ""
        val pw = min(470 * dp, w - g.insetL - g.insetR - 20 * dp)
        val px = (w + g.insetL - g.insetR - pw) / 2f
        val py = noticeTop
        val ph = min(50 * dp, max(noticeBottom - py, 24 * dp))
        reportReminder.set(px, py, pw, ph)
        reportReminder.hitSlop = ((48 * dp - ph) / 2f).coerceIn(0f, 6 * dp)
    }

    private fun drawReminder(c: Canvas) {
        val b = reportReminder
        if (!b.visible) return
        val r = b.r
        ui.card(c, r, if (b.pressed) Color.rgb(70, 58, 30) else Color.rgb(42, 56, 84), 12f, Ui.GOLD)
        val iconSize = min(26 * dp, r.height() - 10 * dp)
        Icons.draw(c, Icons.BOOK, r.left + 23 * dp, r.centerY(), iconSize, dp = dp)
        val size = ui.fit(reminderText, 14f, r.width() - 80 * dp)
        ui.textO(c, reminderText, r.left + 45 * dp, r.centerY() + 5 * dp, size, Ui.GOLD)
        ui.textO(c, "›", r.right - 16 * dp, r.centerY() + 6 * dp, 18f, Ui.TEXT, Paint.Align.CENTER)
    }

    /** Pytanie dopiero w spokojnym menu: bez okien, dymków, banerów i „Co nowego”. */
    private fun updateSurvey(dt: Float) {
        if (surveyStep != SURVEY_HIDDEN) return
        val log = g.playtest
        val quiet = log != null && g.resumeOffer == null && g.lostBattle == null &&
            !hints.okBtn.visible && !whatsNew.visible && !g.hasNotices() &&
            TesterSurvey.due(g.testerSurveyEnabled, log.surveyState, g.profile.tutorialDone,
                g.profile.battles, log.daysPlayed)
        if (!quiet) { surveyQuiet = 0f; return }
        surveyQuiet += dt
        if (surveyQuiet < TesterSurvey.QUIET_SECONDS) return
        surveyQuiet = 0f
        surveyStep = SURVEY_RATING
        layoutSurvey()
    }

    private fun surveyClick(b: Button) {
        val log = g.playtest
        val now = g.nowMillis()
        when {
            surveyStep == SURVEY_RATING && b.id in SURVEY_RATE until SURVEY_RATE + 5 -> {
                log?.surveyRated(b.id - SURVEY_RATE + 1, now)
                log?.saveAsync()
                surveyStep = SURVEY_QUESTION
                layoutSurvey()
            }
            surveyStep == SURVEY_QUESTION && b.id in SURVEY_ISSUE until SURVEY_ISSUE + TesterSurvey.ISSUES -> {
                log?.surveyIssueChosen(b.id - SURVEY_ISSUE)
                log?.saveAsync()
                closeSurvey(thanks = true)
            }
            surveyStep == SURVEY_FINAL_NPS && b.id in SURVEY_NPS..SURVEY_NPS + PlaytestLog.FINAL_NPS_MAX -> {
                log?.finalRecommend(b.id - SURVEY_NPS, now)
                log?.saveAsync()
                surveyStep = SURVEY_FINAL_LIKED
                layoutSurvey()
            }
            surveyStep == SURVEY_FINAL_LIKED && b.id in SURVEY_OPT until SURVEY_OPT + TesterSurvey.FINAL_OPTIONS -> {
                log?.finalLikedChosen(b.id - SURVEY_OPT)
                log?.saveAsync()
                surveyStep = SURVEY_FINAL_ISSUE
                layoutSurvey()
            }
            surveyStep == SURVEY_FINAL_ISSUE && b.id in SURVEY_OPT until SURVEY_OPT + TesterSurvey.FINAL_OPTIONS -> {
                log?.finalIssueChosen(b.id - SURVEY_OPT)
                log?.saveAsync()
                closeFinal(thanks = true)
            }
            b === surveySkip -> when (surveyStep) {
                SURVEY_RATING -> {
                    log?.surveySkipped(now)
                    log?.saveAsync()
                    closeSurvey(thanks = false)
                }
                SURVEY_FINAL_NPS -> {
                    log?.finalSkipped(now)
                    log?.saveAsync()
                    closeFinal(thanks = false)
                }
                SURVEY_FINAL_LIKED, SURVEY_FINAL_ISSUE -> closeFinal(thanks = true)
                else -> closeSurvey(thanks = true)
            }
        }
    }

    /** D57: koniec ankiety końcowej (odpowiedź albo Pomiń): od razu to samo okno raportu co baner z 3. dnia. */
    private fun closeFinal(thanks: Boolean) {
        closeSurvey(thanks)
        g.host.sharePlaytestReport()
    }

    private fun closeSurvey(thanks: Boolean) {
        surveyStep = SURVEY_HIDDEN
        hideSurveyButtons()
        if (thanks) g.showNotice(L["t200_thanks"], Icons.CHECK)
    }

    /** Panel na środku bezpiecznego obszaru; na wąskim ekranie odpowiedzi w jednej kolumnie. */
    private fun layoutSurvey() {
        hideSurveyButtons()
        if (surveyStep == SURVEY_HIDDEN) return
        val cx = w / 2 + (g.insetL - g.insetR) / 2
        val pw = min(460 * dp, w - g.insetL - g.insetR - 32 * dp)
        val inner = pw - 48 * dp
        val gap = 10 * dp
        val bh = 48 * dp
        surveySkip.label = L["t200_skip"]
        val lineH = 14 * dp * ui.textScale * 1.3f
        val cols = if (pw >= 400 * dp) 2 else 1
        val content: Float
        // D57: polecenie 0–10 w rzędach po 6 (wąski ekran: po 4), przyciski co najmniej 48 dp.
        val npsCols = if (inner >= 6 * 48 * dp + 5 * 8 * dp) 6 else 4
        val npsRows = (finalNps.size + npsCols - 1) / npsCols
        val options = if (surveyStep == SURVEY_QUESTION) surveyIssues else finalOptions
        when (surveyStep) {
            SURVEY_RATING -> {
                surveyTitle = L["t200_title"]
                surveyNoteCount = HintFrame.wrapInto(ui, L["t200_note"], inner, 13f, surveyNoteLines)
                surveyScale = L["t200_scale"]
                content = surveyNoteCount * lineH + 12 * dp + 56 * dp + 8 * dp + 18 * dp * ui.textScale
            }
            SURVEY_FINAL_NPS -> {
                surveyTitle = L["tr205_title"]
                surveyNoteCount = HintFrame.wrapInto(ui, L["tr205_recommend"], inner, 13f, surveyNoteLines)
                surveyScale = L["tr205_scale"]
                content = surveyNoteCount * lineH + 12 * dp + npsRows * bh + (npsRows - 1) * 8 * dp +
                    8 * dp + 18 * dp * ui.textScale
            }
            else -> {
                surveyTitle = L[if (surveyStep == SURVEY_FINAL_LIKED) "tr205_liked" else "t200_question"]
                surveyNoteCount = 0
                surveyScale = ""
                val rows = (options.size + cols - 1) / cols
                content = rows * bh + (rows - 1) * gap
            }
        }
        val ph = min(52 * dp + content + 16 * dp + bh + 20 * dp, h - g.insetT - g.insetB - 24 * dp)
        val cy = (g.insetT + h - g.insetB) / 2
        surveyPanel.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2)
        val left = surveyPanel.left + 24 * dp
        val y = surveyPanel.top + 52 * dp
        if (surveyStep == SURVEY_RATING) {
            surveyNoteY = y + 10 * dp
            val ry = y + surveyNoteCount * lineH + 12 * dp
            val rw = (inner - 4 * 8 * dp) / 5f
            for (i in surveyRates.indices) {
                val b = surveyRates[i]
                b.label = (i + 1).toString()
                b.set(left + i * (rw + 8 * dp), ry, rw, 56 * dp)
                b.visible = true
            }
            surveyScaleY = ry + 56 * dp + 8 * dp + 12 * dp * ui.textScale
        } else if (surveyStep == SURVEY_FINAL_NPS) {
            surveyNoteY = y + 10 * dp
            val ry = y + surveyNoteCount * lineH + 12 * dp
            val ng = 8 * dp
            val rw = (inner - (npsCols - 1) * ng) / npsCols
            for (i in finalNps.indices) {
                val b = finalNps[i]
                val row = i / npsCols
                // Niepełny ostatni rząd na środku.
                val inRow = min(npsCols, finalNps.size - row * npsCols)
                val rowLeft = left + (inner - inRow * rw - (inRow - 1) * ng) / 2f
                b.label = i.toString()
                b.set(rowLeft + (i % npsCols) * (rw + ng), ry + row * (bh + ng), rw, bh)
                b.visible = true
            }
            surveyScaleY = ry + npsRows * bh + (npsRows - 1) * ng + 8 * dp + 12 * dp * ui.textScale
        } else {
            val keys = when (surveyStep) {
                SURVEY_QUESTION -> SURVEY_ISSUE_KEYS
                SURVEY_FINAL_LIKED -> FINAL_LIKED_KEYS
                else -> FINAL_ISSUE_KEYS
            }
            val tw = (inner - (cols - 1) * gap) / cols
            for (i in options.indices) {
                val b = options[i]
                b.label = L[keys[i]]
                b.set(left + (i % cols) * (tw + gap), y + (i / cols) * (bh + gap), tw, bh)
                b.visible = true
            }
        }
        val sw = min(inner, max(160 * dp, ui.measure(surveySkip.label, 16f, true) + 48 * dp))
        surveySkip.set(cx - sw / 2, surveyPanel.bottom - bh - 20 * dp, sw, bh)
        surveySkip.visible = true
    }

    private fun drawSurvey(c: Canvas) {
        c.drawColor(Color.argb(160, 0, 0, 0))
        val p = surveyPanel
        ui.panel(c, p)
        ui.ribbon(c, p.centerX(), p.top + 4 * dp, surveyTitle, 20f, Ui.RIBBON, p.width() * 0.7f,
            Ui.GOLD, p.width() + 20 * dp)
        var y = surveyNoteY
        for (i in 0 until surveyNoteCount) {
            ui.text(c, surveyNoteLines[i] ?: "", p.centerX(), y, 13f, Ui.TEXT, Paint.Align.CENTER)
            y += 14 * dp * ui.textScale * 1.3f
        }
        when (surveyStep) {
            SURVEY_RATING -> for (b in surveyRates) ui.button(c, b, 22f)
            SURVEY_FINAL_NPS -> for (b in finalNps) ui.button(c, b, 20f)
            SURVEY_QUESTION -> for (b in surveyIssues) ui.button(c, b, 15f)
            else -> for (b in finalOptions) ui.button(c, b, 15f)
        }
        if (surveyScale.isNotEmpty()) {
            ui.text(c, surveyScale, p.centerX(), surveyScaleY,
                ui.fit(surveyScale, 12f, p.width() - 48 * dp, 10f), Ui.DIM, Paint.Align.CENTER)
        }
        ui.button(c, surveySkip, 16f)
    }

    override fun onBack(): Boolean {
        // Wstecz tylko chowa pytanie; zrzut zostaje (zapyta przy następnym uruchomieniu).
        if (g.resumeOffer != null) { g.resumeOffer = null; return true }
        // Wstecz w ankiecie = Pomiń (bez ponownego pytania).
        if (surveyStep != SURVEY_HIDDEN) { surveyClick(surveySkip); return true }
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
        if (portraitMenu) {
            val safeW = w - g.insetL - g.insetR - 24 * dp
            ui.ribbon(c, cx, subY, L["subtitle"], subtitleDp(s),
                Ui.RIBBON, min(s * 4.2f, safeW - 28 * dp), Ui.TEXT, safeW)
        } else {
            ui.ribbon(c, cx, subY, L["subtitle"], subtitleDp(s),
                Ui.RIBBON, s * 4.2f, Ui.TEXT)
        }
    }

    /** Dzień 60: podtytuł pod logo nie mniejszy niż 9 dp (na 720p i 20:9 miał 7,8 dp). */
    private fun subtitleDp(s: Float) = min(16f, max(9f, s / dp * 0.3f))

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

    override fun accessibilityLabel(b: Button): String = when (b) {
        settings -> L["settings"]
        codex -> L["codex"]
        whatsNew -> L["menu175_whats_new_desc"]
        surveySkip -> L["t200_skip"]
        goalBtn -> goalText
        reportReminder -> reminderText
        // R10-02: TalkBack czyta też drugi licznik kafla Kampanii („Korony 5/36”).
        in surveyRates -> {
            val n = L.f("t200_rate_n", b.id - SURVEY_RATE + 1)
            if (b === surveyRates[0]) "$surveyTitle. $n" else n
        }
        in surveyIssues -> if (b === surveyIssues[0]) "$surveyTitle. ${b.label}" else b.label
        // D57: TalkBack czyta tytuł i pytanie przy pierwszym przycisku ankiety końcowej.
        in finalNps -> {
            val n = L.f("tr205_nps_n", b.id - SURVEY_NPS)
            if (b === finalNps[0]) "$surveyTitle. ${L["tr205_recommend"]} $n" else n
        }
        in finalOptions -> if (b === finalOptions[0]) "$surveyTitle. ${b.label}" else b.label
        campaign -> if (b.sub2.isEmpty()) super.accessibilityLabel(b)
            else super.accessibilityLabel(b) + ", " + L["hard189_crowns"] + " " + b.sub2
        else -> super.accessibilityLabel(b)
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
        if (surveyStep != SURVEY_HIDDEN) { surveyClick(b); return }
        if (hints.onClick(b)) return
        hints.onAction()
        when (b.id) {
            1 -> if (firstTutorialReady()) {
                // Pierwsza epoka nie zasłania powrotu z samouczka zapowiedzią.
                g.profile.markEraIntroSeen(0)
                g.setScreen(BattleScreen(g, Campaign.config(Campaign.stages[0],
                    g.profile.bonuses(), System.nanoTime(), tutorial = true)))
            } else g.push(CampaignScreen(g))
            2 -> g.push(ModeScreen(g, Mode.ENDLESS))
            3 -> g.push(ModeScreen(g, Mode.DAILY))
            4 -> g.push(SkirmishSetupScreen(g))
            5 -> g.push(ResearchScreen(g))
            6 -> g.push(RankingsScreen(g))
            7 -> g.push(SettingsScreen(g))
            8 -> g.push(CodexScreen(g))
            9 -> g.push(MissionsScreen(g))
            ORDERS -> g.push(OrdersScreen(g))
            WHATS_NEW -> g.push(WhatsNewScreen(g, fromMenu = true))
            REPORT_REMINDER -> {
                val final = reminderFinal && g.playtest?.finalState == PlaytestLog.FINAL_NONE
                reminderDay = 0
                reminderFinal = false
                reportReminder.visible = false
                // D57: 10. dzień — najpierw ankieta końcowa (Pomiń zawsze dostępne), potem okno raportu.
                if (final) {
                    surveyStep = SURVEY_FINAL_NPS
                    layoutSurvey()
                } else g.host.sharePlaytestReport()
            }
            GOAL -> when (goalKind) {
                NextGoalKind.MISSIONS -> g.push(MissionsScreen(g))
                NextGoalKind.RESEARCH -> g.push(ResearchScreen(g))
                NextGoalKind.STAGE -> g.push(CampaignScreen(g))
                NextGoalKind.WEEKLY_TIER -> g.push(ModeScreen(g, Mode.DAILY, true))
                NextGoalKind.NONE -> Unit
                NextGoalKind.HARD_STAGE -> g.push(CampaignScreen(g, goalArg, suggestHard = true))
            }
        }
    }
}
