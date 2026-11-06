package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Sound
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.Ui
import com.centurywar.game.Campaign
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Defs
import com.centurywar.game.Stage
import com.centurywar.meta.StageNovelty
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Podróż przez dziewięć epok. W poziomie regiony biegną od lewej do prawej,
 * na pionowym ekranie od góry do dołu. Tła są nagrane w Picture przy layout().
 */
class CampaignScreen(
    g: Game,
    /** R2-02: etap, którego okno otwiera się od razu (−1 = sama mapa). */
    private val focusStage: Int = -1,
    /** R2-02: wyróżnij Łatwy w oknie [focusStage] (propozycja po porażkach; wybór należy do gracza). */
    internal val suggestEasy: Boolean = false,
    /** R10-02: obwódka wokół Trudnego w oknie [focusStage] („Następny cel” po kampanii). */
    internal val suggestHard: Boolean = false,
) : Screen(g) {
    override val crashName get() = "CampaignScreen"
    /** Tura 9: baner powiadomień nie przykrywa tytułu planszy nowej epoki (okno modalne). */
    // Tura 10: także okno etapu jest modalne — baner zasłaniał tytuł i wiersz „Epoki”.
    override val blocksNotices: Boolean get() = eraIntro?.active == true || selected != null
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val tiles = Campaign.stages.map { stage -> btn(stage.index).also { it.tag = stage } }
    private val start = btn(-2)
    private val close = btn(-3)
    private val hints = HintOverlay(g)

    init { buttons.add(0, hints.okBtn) }
    private val difficultyButtons = Array(3) { btn(-10 - it) }

    private var portraitMap = false
    private var laidOut = false
    private var moving = false
    private var autoScroll = false
    private var scroll = 0f
    private var scrollTarget = 0f
    private var velocity = 0f
    private var maxScroll = 0f
    private var regionSpan = 0f
    private var viewportW = 0f
    private var viewportH = 0f
    private var viewportL = 0f
    private var viewportR = 0f
    private var top = 0f
    private var bottom = 0f
    private var nodeR = 0f
    private var dt = 0f
    private var time = 0f
    private var selected: Stage? = Campaign.stages.getOrNull(focusStage)
    private var eraIntro: EraIntroOverlay? = null
    private var lastUnlockedEra = -1

    private val info = RectF()
    private val tmp = RectF()
    private val cx = FloatArray(Campaign.stages.size)
    private val cy = FloatArray(Campaign.stages.size)
    private val regionPictures = arrayOfNulls<Picture>(Defs.ERA_COUNT)
    private var pictureWidth = -1
    private var pictureHeight = -1
    private var pictureDp = -1f
    private var picturePortrait = false
    /** Licznik nagrań obrazów epok (testy L4). */
    internal var recordedRegions = 0
        private set
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val artPath = Path()
    /** R11-06: iskra nowości (osobna ścieżka: artPath nagrywa obrazy epok). */
    private val sparkPath = Path()
    private val sparkFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val eraNumbers = Array(Defs.ERA_COUNT) { (it + 1).toString() }
    private val stageNumbers = Array(Campaign.stages.size) { (it + 1).toString() }
    private val starLabels = Array(13) { it.toString() + "/12" }
    /** R10-02: licznik koron Trudnego w nagłówku epoki. */
    private val crownLabels = Array(5) { it.toString() + "/4" }
    private val tileA11y = arrayOfNulls<String>(Campaign.stages.size)
    private val tileA11yKey = IntArray(Campaign.stages.size) { -1 }
    private val eraA11y = Array(Defs.ERA_COUNT) { "" }
    private val eraA11yKey = IntArray(Defs.ERA_COUNT) { -1 }
    private var panelA11y = ""
    private var panelA11yKey = -1
    private var introA11ySource: EraIntroOverlay? = null
    private var introA11y = ""
    private val a11yRect = RectF()
    private var hardLine = ""
    private var hardLineWon = false
    private var hardLineKey = -1
    private var infoIndex = -1
    private var infoLanguage = -1
    private var infoTitle = ""
    private var infoRange = ""
    private var infoModifiers = ""
    private var compactModifiers = ""
    private var infoPower = ""
    private var infoDifficulty = CampaignDifficulty.NORMAL

    private fun refreshInfo(s: Stage) {
        val difficulty = g.profile.campaignDifficulty
        if (infoIndex == s.index && infoLanguage == L.lang && infoDifficulty == difficulty) return
        infoIndex = s.index
        infoLanguage = L.lang
        infoDifficulty = difficulty
        infoTitle = L.f("stage_n", s.index + 1)
        infoRange = L.f("eras_range", L[Defs.eraKeys[s.startEra]], L[Defs.eraKeys[s.maxEra]])
        infoModifiers = L["modifiers"] + ":"
        compactModifiers = if (s.modifiers.isEmpty()) L["none"]
            else s.modifiers.joinToString(" · ") { L[it.key] }
        infoPower = L.f("ai_power", (s.aiPower * difficulty.powerMultiplier * 100f).toInt())
    }

    override fun isDragScreen() = selected == null

    override fun onLanguageChanged() {
        super.onLanguageChanged()
        for (i in difficultyButtons.indices)
            difficultyButtons[i].label = L[CampaignDifficulty.entries[i].key]
        eraIntro?.onLanguageChanged()
        tileA11y.fill(null)
        eraA11yKey.fill(-1)
        panelA11yKey = -1
        introA11ySource = null
        hardLineKey = -1
    }

    /** Wysokość zarezerwowana na nagłówek epoki na początku regionu (tylko w pionie). */
    private var headSpan = 0f

    /** Położenie etapu wzdłuż szlaku; w pionie z pominięciem pasa nagłówka epoki. */
    private fun progressOf(era: Int, step: Int): Float =
        era * regionSpan + headSpan + (step + 0.5f) / 4f * (regionSpan - headSpan)

    /** Nagłówek epoki [era] we współrzędnych ekranu (bez przewijania); do testów nachodzenia. */
    internal fun headingRect(era: Int, out: RectF): RectF {
        val x = if (portraitMap) viewportL else viewportL + era * regionSpan - scroll
        val y = if (portraitMap) top + era * regionSpan - scroll else top
        val name = L[Defs.eraKeys[era]]
        val available = if (portraitMap) viewportW - 110 * dp else regionSpan - 118 * dp
        val tw = ui.measure(name, ui.fit(name, 17f, available.coerceAtLeast(75 * dp)), true)
        out.set(x + 11 * dp, y + 7 * dp, x + 68 * dp + max(tw, 60 * dp), y + 53 * dp)
        if (showCrowns(era)) {
            crownCounter(era, x, y, tmp)
            out.union(tmp)
        }
        return out
    }

    /** R10-02: licznik koron w nagłówku, gdy epoka ma koronę albo kampania ma komplet gwiazdek. */
    internal fun showCrowns(era: Int): Boolean {
        val p = g.profile
        if (p.hardWonInEra(era) > 0) return true
        for (s in p.stars) if (s <= 0) return false
        return true
    }

    /**
     * R10-02: prostokąt korony i „X/4” w nagłówku epoki: obok gwiazdek, a gdy wiersz się nie
     * mieści w regionie (wąski ekran w pionie), w drugim wierszu pod gwiazdkami.
     */
    internal fun crownCounter(era: Int, x: Float, y: Float, out: RectF): RectF {
        val ox = x + 34 * dp
        val oy = y + 30 * dp
        var earned = 0
        for (i in era * 4 until era * 4 + 4) earned += g.profile.stars[i]
        val starsRight = ox + 56 * dp + ui.measure(starLabels[earned.coerceIn(0, 12)], 13f, true)
        val label = crownLabels[g.profile.hardWonInEra(era).coerceIn(0, 4)]
        val width = 18 * dp + ui.measure(label, 13f, true)
        val limit = x + (if (portraitMap) viewportW else regionSpan) - 8 * dp
        val left = starsRight + 12 * dp
        if (left + width <= limit) out.set(left, oy + 8 * dp, left + width, oy + 24 * dp)
        else out.set(ox + 36 * dp, oy + 26 * dp, ox + 36 * dp + width, oy + 42 * dp)
        return out
    }

    override fun layout() {
        chrome.layoutBack(back)
        start.label = L["start"]
        start.iconId = Icons.PLAY
        start.highlight = true
        portraitMap = h > w
        viewportL = g.insetL + 10 * dp
        viewportR = w - g.insetR - 10 * dp
        top = chrome.top + 6 * dp
        bottom = h - g.insetB - 9 * dp
        viewportW = max(1f, viewportR - viewportL)
        viewportH = max(1f, bottom - top)
        nodeR = min(27 * dp, min(viewportW, viewportH) * 0.12f)
        regionSpan = if (portraitMap) {
            max(270 * dp, viewportH * 0.38f) + HEADING_DP * dp
        } else {
            max(310 * dp, viewportW * 0.54f)
        }
        // Węzeł (z większym węzłem co czwarty) nie może być szerszy niż odstęp między etapami.
        // W pionie pole dotyku ma 3,1 r wysokości, a para „duży + zwykły” zajmuje 3,31 r,
        // więc na wąskich ekranach (zewnętrzny ekran Folda) potrzebny jest większy zapas.
        // Tura 9: w pionie węzły zaczynają się pod nagłówkiem epoki (medalion i nazwa zajmują
        // ok. 56 dp od początku regionu), inaczej pierwszy etap epoki nachodzi na „Epoka Brązu”.
        headSpan = if (portraitMap) HEADING_DP * dp else 0f
        nodeR = min(nodeR, (regionSpan - headSpan) / 4f / if (portraitMap) 3.45f else 3.3f)
        val visibleSpan = if (portraitMap) viewportH else viewportW
        val previousMax = maxScroll
        val previousScroll = scroll
        maxScroll = max(0f, regionSpan * Defs.ERA_COUNT - visibleSpan)

        for (i in tiles.indices) {
            val era = i / 4
            val step = i % 4
            val progress = progressOf(era, step)
            if (portraitMap) {
                cx[i] = viewportL + viewportW * 0.52f +
                    sin(i * 1.05f) * min(viewportW * 0.23f, 90 * dp)
                cy[i] = top + progress
            } else {
                cx[i] = viewportL + progress
                cy[i] = top + viewportH * 0.55f +
                    sin(i * 1.05f) * min(viewportH * 0.22f, 62 * dp)
            }
        }
        rebuildPictures()

        if (!laidOut) {
            // Wjazd z sąsiedniego fragmentu szlaku: bez długiego przelotu przez 36 etapów.
            val last = (if (focusStage in tiles.indices) focusStage else g.profile.unlockedStage())
                .coerceIn(0, tiles.lastIndex)
            val position = progressOf(last / 4, last % 4)
            scrollTarget = (position - visibleSpan * 0.53f).coerceIn(0f, maxScroll)
            scroll = (scrollTarget - visibleSpan * 0.72f).coerceAtLeast(0f)
            autoScroll = scroll < scrollTarget
            laidOut = true
        } else {
            scroll = if (previousMax > 0f) {
                (previousScroll / previousMax * maxScroll).coerceIn(0f, maxScroll)
            } else {
                previousScroll.coerceIn(0f, maxScroll)
            }
            // Noc 5: drugi layout() po zmianie ekranu (Game.frame) przy tych samych wymiarach
            // przerywał wjazd do następnego etapu i węzeł etapu 8 zostawał za krawędzią.
            if (previousMax != maxScroll) autoScroll = false
        }
        velocity = 0f
        moving = false

        val iw = min(460 * dp, w - g.insetL - g.insetR - 40 * dp)
        // Dwa rzędy pełnych pól dotyku (48 dp): trudność i start etapu.
        val ih = min(362 * dp, h - g.insetT - g.insetB - 58 * dp)
        info.set(w / 2 - iw / 2, h / 2 - ih / 2 + 22 * dp,
            w / 2 + iw / 2, h / 2 + ih / 2 + 22 * dp)
        // Dolna krawędź startu 12 dp nad złotą lamówką ramki (Ui.PANEL_TRIM_INSET).
        start.set(info.centerX() - 90 * dp, info.bottom - 71 * dp, 180 * dp, 48 * dp)
        start.hitSlop = 2 * dp
        close.set(info.right - 34 * dp, info.top - 18 * dp, 48 * dp, 48 * dp)
        val gap = 6 * dp
        val buttonWidth = (info.width() - 48 * dp - gap * 2) / 3f
        for (i in difficultyButtons.indices) {
            val b = difficultyButtons[i]
            b.label = L[CampaignDifficulty.entries[i].key]
            b.highlight = g.profile.campaignDifficulty == CampaignDifficulty.entries[i]
            b.set(info.left + 24 * dp + i * (buttonWidth + gap),
                start.r.top - 52 * dp, buttonWidth, 48 * dp)
            b.hitSlop = 0f
        }
        place()
        eraIntro?.layout()
    }

    private fun place() {
        val unlocked = g.profile.unlockedStage()
        for (i in tiles.indices) {
            val t = tiles[i]
            val stage = t.tag as Stage
            val x = cx[i] - if (portraitMap) 0f else scroll
            val y = cy[i] - if (portraitMap) scroll else 0f
            val r = nodeR * if (stage.index % 4 == 3) 1.12f else 1f
            t.set(x - r * 1.45f, y - r * 1.42f, r * 2.9f, r * 3.1f)
            // Gwiazdki weterana mogą istnieć za luką po dodaniu nowej epoki.
            t.enabled = stage.index <= unlocked || g.profile.stars[stage.index] > 0
            t.highlight = stage.index == unlocked
            t.visible = selected == null && t.r.left >= viewportL &&
                t.r.right <= viewportR && t.r.top >= top && t.r.bottom <= bottom
        }
        start.visible = selected != null
        close.visible = selected != null
        for (b in difficultyButtons) b.visible = selected != null
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (eraIntro?.active == true) {
            if (e.action == TouchEvent.UP) eraIntro?.dismiss()
            return true
        }
        when (e.action) {
            TouchEvent.DOWN -> {
                if (selected == null) {
                    velocity = 0f
                    autoScroll = false
                }
            }
            TouchEvent.UP -> moving = false
            TouchEvent.CANCEL -> {
                moving = false
                velocity = 0f
            }
        }
        return false
    }

    override fun onDrag(dx: Float, dy: Float) {
        hints.onAction()
        if (selected != null) return
        autoScroll = false
        moving = true
        val delta = if (portraitMap) -dy else -dx
        val next = (scroll + delta).coerceIn(0f, maxScroll)
        val applied = next - scroll
        scroll = next
        velocity = (applied / max(dt, 1f / 90f)).coerceIn(-2600 * dp, 2600 * dp)
        place()
    }

    override fun update(dt: Float) {
        this.dt = dt
        eraIntro?.update(dt)
        val unlockedEra = (g.profile.unlockedStage() / 4).coerceIn(0, Defs.ERA_COUNT - 1)
        if (unlockedEra != lastUnlockedEra) {
            lastUnlockedEra = unlockedEra
            eraIntro = EraIntroOverlay.showIfFirst(g, unlockedEra)
            eraIntro?.layout()
        }
        if (eraIntro?.active == true) return
        time += dt
        if (selected == null) {
            if (autoScroll) {
                scroll += (scrollTarget - scroll) * min(1f, dt * 6.5f)
                if (abs(scrollTarget - scroll) < 0.5f * dp) {
                    scroll = scrollTarget
                    autoScroll = false
                }
            } else if (!moving && abs(velocity) > 0.5f) {
                val next = (scroll + velocity * dt).coerceIn(0f, maxScroll)
                if (next == scroll) velocity = 0f else {
                    scroll = next
                    velocity *= max(0f, 1f - dt * 6.7f)
                }
            }
        }
        place()
        hints.update(dt)
        // R2-02: przy 3. porażce z rzędu dymek przypomina o zmianie poziomu (raz).
        if (easySuggested && g.profile.lossStreakOn(focusStage) >= 3)
            hints.showScreen(HintOverlay.DIFFICULTY, difficultyButtons[0].r)
        // D61: dymek po pierwszej bitwie czeka, aż przewiną się banery osiągnięć (jeden komunikat naraz).
        if (g.profile.battles == 1 && selected == null && !autoScroll && !g.hasNotices()) {
            val next = tiles[g.profile.unlockedStage().coerceIn(0, tiles.lastIndex)]
            hints.showScreen(HintOverlay.FIRST_BATTLE, if (next.visible) next.r else back.r)
        }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt, g.quality.menuAnimated && !g.profile.reduceFlash, g.quality.menuDetail)
        chrome.dim(c, 110)
        c.save()
        c.clipRect(viewportL, top, viewportR, bottom)
        c.translate(if (portraitMap) 0f else -scroll, if (portraitMap) -scroll else 0f)

        // R2-05/R10-04 L4: po onTrimMemory ustalamy tylko geometrię, a brakujące obrazy
        // widocznych epok nagrywamy na miejscu, najwyżej REGION_RECORDS_PER_FRAME na klatkę.
        if (pictureWidth < 0) rebuildPictures()
        var recordBudget = REGION_RECORDS_PER_FRAME
        for (e in 0 until Defs.ERA_COUNT) {
            val begin = e * regionSpan - scroll
            val span = if (portraitMap) viewportH else viewportW
            if (begin + regionSpan < 0f || begin > span) continue
            val x = if (portraitMap) viewportL else viewportL + e * regionSpan
            val y = if (portraitMap) top + e * regionSpan else top
            if (regionPictures[e] == null && recordBudget > 0) {
                recordRegion(e)
                recordBudget--
            }
            val picture = regionPictures[e] ?: continue
            c.save()
            c.translate(x, y)
            c.drawPicture(picture)
            c.restore()
        }
        // Pozostały budżet: niewidoczne epoki w kolejnych klatkach, zanim gracz do nich dojdzie.
        if (recordBudget > 0) {
            for (e in 0 until Defs.ERA_COUNT) {
                if (recordBudget == 0) break
                if (regionPictures[e] != null) continue
                recordRegion(e)
                recordBudget--
            }
        }
        // Najpierw cały szlak, potem punkty etapu, aby drogę zawsze zasłaniała tarcza.
        for (i in 0 until tiles.lastIndex) {
            val a = (if (portraitMap) cy[i] - top else cx[i] - viewportL) - scroll
            val b = (if (portraitMap) cy[i + 1] - top else cx[i + 1] - viewportL) - scroll
            val span = if (portraitMap) viewportH else viewportW
            if (b < -nodeR || a > span + nodeR) continue
            pathPaint.strokeWidth = 12 * dp
            pathPaint.color = Ui.OUTLINE
            c.drawLine(cx[i], cy[i], cx[i + 1], cy[i + 1], pathPaint)
            pathPaint.strokeWidth = 7 * dp
            pathPaint.color = if (i < g.profile.unlockedStage()) Ui.GOLD else Color.rgb(112, 122, 138)
            c.drawLine(cx[i], cy[i], cx[i + 1], cy[i + 1], pathPaint)
        }
        // Tura 9: nagłówki epok nad drogą (w pionie droga przecinała napis „Epoka Brązu”).
        for (e in 0 until Defs.ERA_COUNT) {
            val begin = e * regionSpan - scroll
            val span = if (portraitMap) viewportH else viewportW
            if (begin + regionSpan < 0f || begin > span) continue
            drawRegionHeading(c, e, if (portraitMap) viewportL else viewportL + e * regionSpan,
                if (portraitMap) top + e * regionSpan else top)
        }
        val unlocked = g.profile.unlockedStage()
        for (i in tiles.indices) {
            val t = tiles[i]
            if (t.r.right < viewportL - nodeR || t.r.left > viewportR + nodeR ||
                t.r.bottom < top - nodeR || t.r.top > bottom + nodeR) continue
            drawNode(c, t, unlocked)
        }
        c.restore()
        ui.button(c, back, 16f)
        chrome.titleBounds(L["campaign"], hints.titleRect)
        // Dzień 60: na 720p i 20:9 wstęga okna etapu wchodziła na przyciemniony tytuł „Kampania”
        // (dwie wstęgi jedna na drugiej); wtedy tytułu ekranu pod oknem nie rysujemy.
        if (selected == null || hints.titleRect.bottom < info.top - 16 * dp * ui.textScale)
            chrome.title(c, L["campaign"])
        chrome.badges(c, g.profile.medals, g.profile.totalStars())
        selected?.let { drawInfo(c, it) }
        hints.draw(c)
        eraIntro?.draw(c)
    }

    private fun drawRegionHeading(c: Canvas, era: Int, x: Float, y: Float) {
        val ox = x + 34 * dp
        val oy = y + 30 * dp
        val color = Ui.ERA[era]
        ui.medallion(c, ox, oy, 23 * dp, color, -1)
        ui.textO(c, eraNumbers[era], ox, oy + 7 * dp, 19f, Ui.TEXT, Paint.Align.CENTER)
        val name = L[Defs.eraKeys[era]]
        val available = if (portraitMap) viewportW - 110 * dp else regionSpan - 118 * dp
        ui.textO(c, name, ox + 34 * dp, oy - 3 * dp,
            ui.fit(name, 17f, available.coerceAtLeast(75 * dp)), Ui.GOLD)
        var earned = 0
        for (i in era * 4 until era * 4 + 4) earned += g.profile.stars[i]
        Icons.draw(c, Icons.STAR, ox + 44 * dp, oy + 16 * dp, 16 * dp, dp = dp)
        ui.textO(c, starLabels[earned.coerceIn(0, 12)], ox + 56 * dp, oy + 21 * dp,
            13f, if (earned == 12) Ui.GOLD else Ui.TEXT)
        if (showCrowns(era)) {
            val r = crownCounter(era, x, y, tmp)
            val crowns = g.profile.hardWonInEra(era).coerceIn(0, 4)
            Icons.draw(c, Icons.CROWN, r.left + 8 * dp, r.centerY(), 16 * dp,
                alpha = if (crowns > 0) 255 else 140, dp = dp)
            ui.textO(c, crownLabels[crowns], r.left + 18 * dp, r.bottom - 3 * dp,
                13f, if (crowns == 4) Ui.GOLD else Ui.TEXT)
        }
    }

    private fun drawNode(c: Canvas, t: Button, unlocked: Int) {
        val stage = t.tag as Stage
        val i = stage.index
        val x = cx[i]
        val y = cy[i] + if (t.pressed) 2 * dp else 0f
        val big = i % 4 == 3
        val r = nodeR * if (big) 1.12f else 1f
        val locked = !t.enabled
        val current = i == unlocked && !locked
        if (current) {
            val pulse = 0.5f + 0.5f * sin(time * 4f)
            fill.color = Color.argb((80 + 80 * pulse).toInt(), 255, 210, 80)
            c.drawCircle(x, y, r * (1.28f + 0.12f * pulse), fill)
        }
        val body = when {
            locked -> Color.rgb(84, 88, 102)
            current -> Ui.BTN_HI
            else -> Ui.ERA[i / 4]
        }
        val ring = when {
            locked -> Color.rgb(130, 134, 148)
            big -> Ui.RIBBON
            else -> Ui.TRIM
        }
        fill.color = Color.argb(110, 0, 0, 0); c.drawCircle(x, y + 4 * dp, r, fill)
        fill.color = Ui.OUTLINE; c.drawCircle(x, y, r, fill)
        fill.color = ring; c.drawCircle(x, y, r - 2.2f * dp, fill)
        fill.color = Ui.OUTLINE; c.drawCircle(x, y, r - 5 * dp, fill)
        fill.color = Ui.blend(body, Color.BLACK, 0.3f); c.drawCircle(x, y, r - 6.2f * dp, fill)
        fill.color = body; c.drawCircle(x, y - 2.5f * dp, r - 7.5f * dp, fill)
        fill.color = Color.argb(60, 255, 255, 255)
        c.drawCircle(x - r * 0.25f, y - r * 0.3f, r * 0.28f, fill)
        if (locked) {
            Icons.draw(c, Icons.LOCK, x, y - 2 * dp, r * 0.92f, dp = dp)
        } else {
            ui.textO(c, stageNumbers[i], x, y + 6.5f * dp, if (big) 21f else 19f,
                Ui.TEXT, Paint.Align.CENTER)
        }
        if (big) Icons.draw(c, Icons.FLAG, x - r * 0.93f, y - r * 0.72f,
            r * 0.65f, Ui.RIBBON, dp = dp)
        // Pogoda jest widoczna na mapie bez otwierania panelu szczegółów.
        val wx = x + r * 0.94f
        val wy = y - r * 0.82f
        fill.color = Ui.OUTLINE; c.drawCircle(wx, wy, 12 * dp, fill)
        fill.color = if (locked) Color.rgb(84, 88, 102) else Color.rgb(226, 211, 169)
        c.drawCircle(wx, wy, 10 * dp, fill)
        Icons.draw(c, Icons.weatherIcon(stage.weather), wx, wy, 17 * dp,
            alpha = if (locked) 120 else 255, dp = dp)
        if (stage.modifiers.isNotEmpty()) {
            Icons.draw(c, Icons.BOLT, x - r * 0.94f, y + r * 0.68f, 17 * dp,
                alpha = if (locked) 120 else 255, dp = dp)
        }
        if (!locked) ui.stars(c, x, y + r + 5 * dp, r * 0.58f,
            g.profile.stars[i], 3, 0.95f)
        // R10-02: korona Trudnego w wolnym prawym dolnym rogu węzła.
        if (g.profile.hardWon(i)) {
            val kx = x + r * CROWN_DX + CROWN_OUT_DP * dp
            val ky = y + r * CROWN_DY
            fill.color = Ui.OUTLINE; c.drawCircle(kx, ky, 12 * dp, fill)
            fill.color = CROWN_BG; c.drawCircle(kx, ky, 10 * dp, fill)
            Icons.draw(c, Icons.CROWN, kx, ky, 16 * dp, dp = dp)
        } else if (StageNovelty.showSpark(i, g.profile.stars[i])) {
            // R11-06: iskra „nowość” do pierwszej wygranej, w miejscu korony (korona = już wygrany).
            val kx = x + r * CROWN_DX + CROWN_OUT_DP * dp
            val ky = y + r * CROWN_DY
            val pulse = if (g.profile.reduceFlash) 1f else 0.88f + 0.12f * sin(time * 3f + i)
            drawSpark(c, kx, ky, 9 * dp * pulse, if (locked) 150 else 255)
        }
    }

    /** R10-02: środek korony węzła [i] na ekranie (do testów nachodzenia). */
    internal fun crownCenter(i: Int, out: RectF): RectF {
        val t = tiles[i]
        val x = t.r.centerX()
        val y = t.r.top + nodeR * (if (i % 4 == 3) 1.12f else 1f) * 1.42f
        val r = nodeR * if (i % 4 == 3) 1.12f else 1f
        val kx = x + r * CROWN_DX + CROWN_OUT_DP * dp
        out.set(kx - 12 * dp, y + r * CROWN_DY - 12 * dp, kx + 12 * dp, y + r * CROWN_DY + 12 * dp)
        return out
    }

    /** R2-05: obrazy epok odbudują się przy najbliższym render(). */
    override fun onTrimMemory(level: Int) {
        regionPictures.fill(null)
        pictureWidth = -1
        eraIntro?.trimMemory()
    }

    /**
     * Każdy obraz epoki jest wektorowy i tworzony tylko przy zmianie geometrii. R10-04 L4:
     * tu tylko geometria; obrazy nagrywa [recordRegion] z render() dla widocznych epok.
     */
    private fun rebuildPictures() {
        val rw = if (portraitMap) viewportW else regionSpan
        val rh = if (portraitMap) regionSpan else viewportH
        val wi = ceil(rw.toDouble()).toInt().coerceAtLeast(1)
        val hi = ceil(rh.toDouble()).toInt().coerceAtLeast(1)
        // Powrót z bitwy wywołuje layout bez zmiany mapy; zostawiamy nagrane Picture.
        if (wi == pictureWidth && hi == pictureHeight && dp == pictureDp &&
            portraitMap == picturePortrait) return
        regionPictures.fill(null)
        pictureWidth = wi
        pictureHeight = hi
        pictureDp = dp
        picturePortrait = portraitMap
    }

    /** Nagrywa obraz jednej epoki dla bieżącej geometrii. */
    private fun recordRegion(era: Int) {
        val rw = if (portraitMap) viewportW else regionSpan
        val rh = if (portraitMap) regionSpan else viewportH
        val picture = Picture()
        val art = picture.beginRecording(pictureWidth, pictureHeight)
        drawRegionArt(art, era, rw, rh)
        picture.endRecording()
        regionPictures[era] = picture
        recordedRegions++
    }

    private fun triangle(c: Canvas, x: Float, y: Float, radius: Float, height: Float, color: Int) {
        artPath.reset()
        artPath.moveTo(x - radius, y)
        artPath.lineTo(x, y - height)
        artPath.lineTo(x + radius, y)
        artPath.close()
        artFill.color = color
        c.drawPath(artPath, artFill)
        c.drawPath(artPath, artStroke)
    }

    private fun drawRegionArt(c: Canvas, era: Int, rw: Float, rh: Float) {
        val color = Ui.ERA[era]
        val base = Ui.blend(color, Color.rgb(20, 26, 42), 0.80f)
        artFill.color = Color.argb(236, Color.red(base), Color.green(base), Color.blue(base))
        c.drawRect(0f, 0f, rw, rh, artFill)
        artStroke.color = Color.argb(135, 20, 18, 30)
        artStroke.strokeWidth = 3 * dp
        artFill.color = Color.argb(48, 255, 255, 255)
        val band = if (portraitMap) rh else rw
        for (j in 0 until 6) {
            val u = (j + 0.5f) * band / 6f
            if (portraitMap) c.drawCircle(rw * 0.2f + sin(j.toFloat()) * rw * 0.06f,
                u, 9 * dp, artFill)
            else c.drawCircle(u, rh * 0.20f + sin(j.toFloat()) * rh * 0.05f,
                9 * dp, artFill)
        }
        val groundY = rh * (if (portraitMap) 0.80f else 0.87f)
        artFill.color = Ui.blend(color, Color.rgb(18, 24, 32), 0.52f)
        tmp.set(0f, groundY, rw, rh + 20 * dp)
        c.drawRect(tmp, artFill)
        artFill.color = Color.argb(35, 255, 255, 255)
        c.drawRect(0f, groundY, rw, groundY + 3 * dp, artFill)
        val s = min(rw, rh).coerceAtLeast(1f) * 0.13f
        val mx = rw * if (portraitMap) 0.80f else 0.77f
        val my = groundY - 10 * dp
        val pale = Ui.blend(color, Color.WHITE, 0.32f)
        artStroke.strokeWidth = 3 * dp
        when (era) {
            0 -> { // las i obozowisko
                for (j in -1..1) {
                    val x = mx + j * s * 1.02f
                    artFill.color = Color.rgb(77, 113, 80)
                    c.drawRect(x - 3 * dp, my - s * 0.48f, x + 3 * dp, my, artFill)
                    triangle(c, x, my - s * 0.26f, s * 0.32f, s * 0.96f, pale)
                }
            }
            1 -> { // piramidy epoki brązu
                triangle(c, mx - s * 0.45f, my, s * 0.70f, s * 1.25f, pale)
                triangle(c, mx + s * 0.62f, my, s * 0.62f, s * 0.95f, color)
            }
            2 -> { // palisada epoki żelaza
                artFill.color = pale
                for (j in -3..3) {
                    val x = mx + j * s * 0.27f
                    c.drawRect(x - s * 0.10f, my - s * 0.97f,
                        x + s * 0.10f, my, artFill)
                    triangle(c, x, my - s * 0.93f, s * 0.10f, s * 0.25f, pale)
                }
                c.drawLine(mx - s, my - s * 0.36f, mx + s, my - s * 0.36f, artStroke)
            }
            3 -> { // kamienny zamek
                artFill.color = pale
                c.drawRect(mx - s, my - s * 0.70f, mx + s, my, artFill)
                c.drawRect(mx - s * 0.88f, my - s * 1.28f,
                    mx - s * 0.40f, my, artFill)
                c.drawRect(mx + s * 0.40f, my - s * 1.28f,
                    mx + s * 0.88f, my, artFill)
                c.drawRect(mx - s, my - s * 0.70f, mx + s, my, artStroke)
                c.drawRect(mx - s * 0.88f, my - s * 1.28f,
                    mx - s * 0.40f, my, artStroke)
                c.drawRect(mx + s * 0.40f, my - s * 1.28f,
                    mx + s * 0.88f, my, artStroke)
            }
            4 -> { // armata z epoki prochu
                artFill.color = pale
                c.drawRect(mx - s * 0.85f, my - s * 0.75f,
                    mx + s * 0.95f, my - s * 0.47f, artFill)
                c.drawRect(mx - s * 0.85f, my - s * 0.75f,
                    mx + s * 0.95f, my - s * 0.47f, artStroke)
                c.drawCircle(mx - s * 0.4f, my - s * 0.19f, s * 0.32f, artFill)
                c.drawCircle(mx - s * 0.4f, my - s * 0.19f, s * 0.32f, artStroke)
            }
            5 -> { // kotły i koła epoki pary
                artFill.color = pale
                c.drawRect(mx - s * 0.88f, my - s, mx + s * 0.82f, my, artFill)
                c.drawRect(mx + s * 0.28f, my - s * 1.6f,
                    mx + s * 0.60f, my - s, artFill)
                c.drawCircle(mx - s * 0.35f, my - s * 0.08f, s * 0.34f, artStroke)
                c.drawCircle(mx + s * 0.5f, my - s * 0.08f, s * 0.34f, artStroke)
            }
            6 -> { // fabryczne kominy epoki stali
                artFill.color = pale
                c.drawRect(mx - s, my - s * 0.68f, mx + s, my, artFill)
                for (j in 0..2) {
                    val x = mx - s * 0.65f + j * s * 0.55f
                    c.drawRect(x, my - s * (1.65f + j * 0.15f),
                        x + s * 0.27f, my - s * 0.68f, artFill)
                    c.drawRect(x, my - s * (1.65f + j * 0.15f),
                        x + s * 0.27f, my - s * 0.68f, artStroke)
                }
            }
            7 -> { // procesor i ścieżki krzemu
                artFill.color = pale
                tmp.set(mx - s * 0.80f, my - s * 1.44f,
                    mx + s * 0.80f, my - s * 0.05f)
                c.drawRoundRect(tmp, 9 * dp, 9 * dp, artFill)
                c.drawRoundRect(tmp, 9 * dp, 9 * dp, artStroke)
                for (j in -2..2) {
                    val yy = my - s * 0.72f + j * s * 0.20f
                    c.drawLine(mx - s * 1.10f, yy, mx + s * 1.10f, yy, artStroke)
                }
            }
            else -> { // orbita i księżyc
                artStroke.color = pale
                tmp.set(mx - s * 1.12f, my - s * 1.40f,
                    mx + s * 1.12f, my - s * 0.08f)
                c.drawOval(tmp, artStroke)
                artFill.color = pale
                c.drawCircle(mx, my - s * 0.72f, s * 0.36f, artFill)
                c.drawCircle(mx + s * 1.08f, my - s * 1.03f, s * 0.18f, artFill)
            }
        }
        // Wyraźna granica między regionami niezależnie od kierunku mapy.
        artFill.color = Ui.blend(color, Color.rgb(9, 13, 25), 0.48f)
        if (portraitMap) c.drawRect(0f, rh - 5 * dp, rw, rh, artFill)
        else c.drawRect(rw - 5 * dp, 0f, rw, rh, artFill)
    }

    private fun drawInfo(c: Canvas, s: Stage) {
        refreshInfo(s)
        c.drawColor(Color.argb(150, 0, 0, 0))
        ui.panel(c, info)
        ui.ribbon(c, info.centerX(), info.top + 4 * dp, infoTitle, 20f, Ui.ERA[s.index / 4].let { Ui.blend(it, Ui.RIBBON, 0.35f) }, info.width() * 0.55f)
        val compact = info.height() < 352 * dp
        var y = info.top + (if (compact) 44 else 48) * dp
        // Tylko niski panel: odzyskaj miejsce między modyfikatorami a trudnością (≥ 10 dp także przy 130%).
        val row = if (compact) 21 * dp else 28 * dp
        val x = info.left + 24 * dp
        val tw = info.width() - 48 * dp
        // gwiazdki
        val line = hardInfoLine(s)
        // R11-06: „Nowość: …” zawsze w wierszu gwiazdek (bez dodatkowego wiersza w panelu).
        val novelty = StageNovelty.line(s.index)
        val hardInRow = line.isNotEmpty() && row < 28 * dp
        hardInfoRect.setEmpty()
        noveltyInfoRect.setEmpty()
        noveltyShown = ""
        if (hardInRow || novelty.isNotEmpty()) {
            // R10-02: niski panel (720p): korona i napis w jednym wierszu z gwiazdkami.
            drawStarRow(c, s, y, tw, if (hardInRow) line else "", novelty)
        } else {
            ui.stars(c, info.centerX(), y + 4 * dp, 30 * dp, g.profile.stars[s.index], 3, 1.05f)
        }
        if (!hardInRow && line.isNotEmpty()) {
            // Wyższy panel: korona w osobnym wierszu pod gwiazdkami (jak dotąd).
            y += 30 * dp
            val sz = ui.fit(line, 13f, tw - 24 * dp, 11f)
            val total = 22 * dp + ui.measure(line, sz, true)
            val left = info.centerX() - total / 2f
            hardInfoRect.set(left, y - 6 * dp, left + total, y + 14 * dp)
            drawHardLine(c, line, left, y + 4 * dp, sz)
            y -= 4 * dp
        }
        y += (if (compact) 30 else 34) * dp
        val isz = 20 * dp
        Icons.draw(c, Icons.TOWER, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, infoRange, x + isz + 8 * dp, y,
            ui.fit(infoRange, 14f, tw - isz - 8 * dp), Ui.TEXT)
        y += row
        val pw = s.aiPower * g.profile.campaignDifficulty.powerMultiplier
        Icons.draw(c, if (pw > 1f) Icons.SKULL else Icons.CHECK, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, infoPower, x + isz + 8 * dp, y,
            ui.fit(infoPower, 14f, tw * 0.62f - isz - 12 * dp), if (pw > 1f) Ui.BAD else Ui.GOOD)
        ui.bar(c, x + tw * 0.62f, y - 10 * dp, tw * 0.38f, 8 * dp, (pw / 1.8f).coerceIn(0f, 1f), if (pw > 1f) Ui.BAD else Ui.GOOD)
        y += row
        Icons.draw(c, Icons.weatherIcon(s.weather), x + isz / 2, y - 5 * dp, isz, dp = dp)
        val weather = L[s.weather.key]
        ui.textO(c, weather, x + isz + 8 * dp, y, ui.fit(weather, 14f, tw - isz - 8 * dp), Ui.TEXT)
        y += row
        Icons.draw(c, Icons.FLAG, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, infoModifiers, x + isz + 8 * dp, y, 14f, Ui.GOLD)
        if (compact) {
            // W niskim panelu nazwy stoją w tym samym wierszu; poprzednie chipy nie mieściły się pod nim.
            val tx = x + isz + 14 * dp + ui.measure(infoModifiers, 14f, true)
            val available = (info.right - 24 * dp - tx).coerceAtLeast(1f)
            ui.text(c, compactModifiers, tx, y, ui.fit(compactModifiers, 14f, available, 9f), Ui.TEXT)
        } else if (s.modifiers.isEmpty()) {
            // Tura 10: „brak” w tym samym wierszu; niżej wchodził pod napis „Trudność” (720p).
            ui.text(c, L["none"], x + isz + 14 * dp + ui.measure(infoModifiers, 14f, true), y, 13f, Ui.DIM)
        } else {
            y += 10 * dp
            chips(c, s, x, y, tw)
        }
        ui.textO(c, L["campaign126_difficulty"], x, difficultyButtons[0].r.top - 6 * dp,
            12f, Ui.GOLD)
        for (b in difficultyButtons) ui.button(c, b, 13f)
        drawEasySuggestion(c)
        drawHardSuggestion(c)
        ui.button(c, start, 18f)
        ui.closeButton(c, close)
    }

    /** R2-02: złota obwódka wokół Łatwego, gdy to propozycja po porażkach na tym etapie. */
    internal val easySuggested: Boolean
        get() = suggestEasy && selected?.index == focusStage &&
            g.profile.campaignDifficulty != CampaignDifficulty.EASY

    private fun drawEasySuggestion(c: Canvas) {
        if (!easySuggested) return
        val r = difficultyButtons[0].r
        val pulse = 0.5f + 0.5f * sin(time * 4f)
        pathPaint.strokeWidth = 3 * dp
        pathPaint.color = Color.argb((150 + 105 * pulse).toInt(), 255, 214, 90)
        tmp.set(r.left - 3 * dp, r.top - 3 * dp, r.right + 3 * dp, r.bottom + 3 * dp)
        c.drawRoundRect(tmp, 12 * dp, 12 * dp, pathPaint)
    }

    /** R10-02: obszar wiersza korony w panelu etapu (pusty = brak wiersza); do testów. */
    internal val hardInfoRect = RectF()

    /** Napis wiersza korony; przeliczany tylko po zmianie etapu, języka, poziomu albo korony. */
    private fun hardInfoLine(s: Stage): String {
        val won = g.profile.hardWon(s.index)
        val hard = g.profile.campaignDifficulty == CampaignDifficulty.HARD
        val key = (s.index shl 8) or (L.lang shl 2) or (if (won) 2 else 0) or (if (hard) 1 else 0)
        if (key != hardLineKey) {
            hardLineKey = key
            hardLineWon = won
            hardLine = when {
                won -> L["hard189_won"]
                hard -> L["hard189_todo"]
                else -> ""
            }
        }
        return hardLine
    }

    private fun drawHardLine(c: Canvas, line: String, left: Float, cy: Float, size: Float) {
        Icons.draw(c, Icons.CROWN, left + 9 * dp, cy, 18 * dp, alpha = if (hardLineWon) 255 else 140, dp = dp)
        ui.textO(c, line, left + 22 * dp, cy + size * dp * ui.textScale * 0.36f, size,
            if (hardLineWon) Ui.GOLD else Ui.DIM)
    }

    /** R11-06: obszar wiersza „Nowość” w panelu etapu (pusty = brak wiersza); do testów. */
    internal val noveltyInfoRect = RectF()

    /** R11-06: napis nowości faktycznie narysowany (po ewentualnym skróceniu); do testów. */
    internal var noveltyShown = ""
        private set
    private var noveltyFitSource = ""
    private var noveltyFitSize = -1f
    private var noveltyFitWidth = -1

    /**
     * R10-02 / R11-06: wiersz gwiazdek dzielony z koroną ([hard], tylko niski panel) i nowością.
     * Oba napisy maleją razem do 11 sp; korona zostaje cała, a nowość w razie potrzeby
     * skracamy wielokropkiem albo (gdy zostaje mniej niż [NOVELTY_MIN_DP]) pomijamy w wierszu
     * (pełny opis i tak czyta TalkBack w treści panelu).
     */
    private fun drawStarRow(c: Canvas, s: Stage, y: Float, tw: Float, hard: String, novelty: String) {
        val starSize = 26 * dp
        val starsW = starSize * 1.05f * 2f + starSize
        val slot = 8 * dp + 22 * dp
        var textMax = tw - starsW - (if (hard.isNotEmpty()) slot else 0f)
        if (novelty.isNotEmpty()) textMax -= slot
        val floor = 11f / ui.textScale
        var sz = 13f
        var guard = 0
        while (sz > floor && rowTextWidth(hard, novelty, sz) > textMax && guard++ < 10) {
            sz = max(floor, sz * 0.94f)
        }
        val hardW = if (hard.isNotEmpty()) ui.measure(hard, sz, true) else 0f
        var shown = ""
        if (novelty.isNotEmpty()) {
            val room = textMax - hardW
            if (room >= NOVELTY_MIN_DP * dp) shown = fitNovelty(novelty, sz, room)
        }
        val shownW = if (shown.isNotEmpty()) ui.measure(shown, sz, true) else 0f
        var total = starsW
        if (hard.isNotEmpty()) total += slot + hardW
        if (shown.isNotEmpty()) total += slot + shownW
        val left = info.centerX() - total / 2f
        ui.stars(c, left + starsW / 2f, y + 4 * dp, starSize, g.profile.stars[s.index], 3, 1.05f)
        var cursor = left + starsW + 8 * dp
        if (hard.isNotEmpty()) {
            hardInfoRect.set(cursor, y - 6 * dp, cursor + 22 * dp + hardW, y + 14 * dp)
            drawHardLine(c, hard, cursor, y + 4 * dp, sz)
            cursor += 22 * dp + hardW + 8 * dp
        }
        noveltyShown = shown
        if (shown.isNotEmpty()) {
            noveltyInfoRect.set(cursor, y - 6 * dp, cursor + 22 * dp + shownW, y + 14 * dp)
            drawSpark(c, cursor + 9 * dp, y + 4 * dp, 8 * dp, 255)
            ui.textO(c, shown, cursor + 22 * dp, y + 4 * dp + sz * dp * ui.textScale * 0.36f, sz, Ui.TEXT)
        }
    }

    private fun rowTextWidth(hard: String, novelty: String, size: Float): Float =
        (if (hard.isNotEmpty()) ui.measure(hard, size, true) else 0f) +
            (if (novelty.isNotEmpty()) ui.measure(novelty, size, true) else 0f)

    /** Nowość skrócona wielokropkiem do [width]; wynik zapamiętany do zmiany napisu, rozmiaru lub miejsca. */
    private fun fitNovelty(text: String, size: Float, width: Float): String {
        val w = width.toInt()
        if (text == noveltyFitSource && size == noveltyFitSize && w == noveltyFitWidth) return noveltyShownCache
        noveltyFitSource = text
        noveltyFitSize = size
        noveltyFitWidth = w
        noveltyShownCache = if (ui.measure(text, size, true) <= width) text else {
            var n = text.length - 1
            while (n > 1 && ui.measure(text.substring(0, n).trimEnd() + "…", size, true) > width) n--
            text.substring(0, n).trimEnd() + "…"
        }
        return noveltyShownCache
    }
    private var noveltyShownCache = ""

    /** R11-06: czteroramienna iskra „nowości” (mapa i panel etapu). */
    private fun drawSpark(c: Canvas, x: Float, y: Float, radius: Float, alpha: Int) {
        sparkStar(x, y, radius * 1.3f)
        sparkFill.color = Ui.OUTLINE
        sparkFill.alpha = alpha
        c.drawPath(sparkPath, sparkFill)
        sparkStar(x, y, radius)
        sparkFill.color = Ui.GOLD
        sparkFill.alpha = alpha
        c.drawPath(sparkPath, sparkFill)
        sparkFill.color = Color.WHITE
        sparkFill.alpha = alpha * 3 / 4
        c.drawCircle(x, y, radius * 0.22f, sparkFill)
    }

    private fun sparkStar(x: Float, y: Float, r: Float) {
        val k = r * 0.16f
        sparkPath.reset()
        sparkPath.moveTo(x, y - r)
        sparkPath.quadTo(x + k, y - k, x + r, y)
        sparkPath.quadTo(x + k, y + k, x, y + r)
        sparkPath.quadTo(x - k, y + k, x - r, y)
        sparkPath.quadTo(x - k, y - k, x, y - r)
        sparkPath.close()
    }

    /** R10-02: obwódka wokół Trudnego, gdy okno otworzył cel „korona na etapie”. */
    internal val hardSuggested: Boolean
        get() = suggestHard && selected?.index == focusStage &&
            g.profile.campaignDifficulty != CampaignDifficulty.HARD

    private fun drawHardSuggestion(c: Canvas) {
        if (!hardSuggested) return
        val r = difficultyButtons[2].r
        val pulse = 0.5f + 0.5f * sin(time * 4f)
        pathPaint.strokeWidth = 3 * dp
        pathPaint.color = Color.argb((150 + 105 * pulse).toInt(), 255, 214, 90)
        tmp.set(r.left - 3 * dp, r.top - 3 * dp, r.right + 3 * dp, r.bottom + 3 * dp)
        c.drawRoundRect(tmp, 12 * dp, 12 * dp, pathPaint)
    }

    private fun chips(c: Canvas, s: Stage, x0: Float, y0: Float, width: Float) {
        var x = x0
        var y = y0
        val hh = 24 * dp
        for (m in s.modifiers) {
            val label = L[m.key]
            val cw = ui.measure(label, 12f, true) + 18 * dp
            if (x + cw > x0 + width && x > x0) { x = x0; y += hh + 6 * dp }
            if (y + hh > difficultyButtons[0].r.top - 21 * dp) break
            tmp.set(x, y, x + cw, y + hh)
            ui.card(c, tmp, Color.rgb(84, 60, 110), 10f)
            ui.textO(c, label, x + cw / 2, y + hh * 0.62f, 12f, Ui.TEXT, Paint.Align.CENTER, 1.6f)
            x += cw + 6 * dp
        }
    }

    override fun onClick(b: Button) {
        if (eraIntro?.active == true) return
        if (hints.onClick(b)) return
        hints.onAction()
        when (b.id) {
            -1 -> g.pop()
            -2 -> selected?.let { s ->
                val tutorial = s.index == 0 && !g.profile.tutorialDone
                // Samouczek ma stałą, łagodną siłę AI niezależnie od wybranego poziomu.
                g.setScreen(BattleScreen(g, Campaign.config(s, g.profile.bonuses(), g.newBattleSeed(),
                    tutorial = tutorial,
                    difficulty = if (tutorial) CampaignDifficulty.NORMAL else g.profile.campaignDifficulty)))
            }
            -3 -> selected = null
            in -12..-10 -> {
                g.profile.campaignDifficulty = CampaignDifficulty.entries[-10 - b.id]
                for (i in difficultyButtons.indices)
                    difficultyButtons[i].highlight = g.profile.campaignDifficulty == CampaignDifficulty.entries[i]
                infoIndex = -1
                g.profile.save()
            }
            else -> {
                selected = b.tag as? Stage
                val stage = selected
                if (stage != null && stage.index > 0 && stage.index % 4 == 0 &&
                    stage.index == g.profile.unlockedStage()) {
                    g.sfx(Sound.ERA_UNLOCK, 0.52f, 500)
                }
            }
        }
    }

    /** R2-02: ✕ okna etapu nie ma podpisu; TalkBack czyta „Zamknij okno etapu”. */
    override fun accessibilityLabel(b: Button): String {
        if (b === close) return L["help180_close"]
        val stage = b.tag as? Stage ?: return super.accessibilityLabel(b)
        // R11-01: „Etap 7, Epoka brązu, 2 z 3 gwiazdek, następny do zdobycia” (+ korona R10-02);
        // napis budujemy tylko po zmianie stanu węzła albo języka.
        val i = stage.index
        val key = accessibilityKey(b) or (if (b.enabled) 16 else 0) or (L.lang shl 5)
        val cached = tileA11y[i]
        if (cached != null && tileA11yKey[i] == key) return cached
        val text = StringBuilder(L.f("a196_stage", i + 1, L[Defs.eraKeys[i / 4]]))
        if (!b.enabled) text.append(", ").append(L["a196_locked"])
        else {
            text.append(", ").append(L.f("a196_stars", g.profile.stars[i]))
            if (b.highlight) text.append(", ").append(L["a196_next"])
        }
        if (g.profile.hardWon(i)) text.append(", ").append(L["hard189_a11y"])
        // R11-06: nowość etapu do pierwszej wygranej (jak iskra na mapie).
        if (StageNovelty.showSpark(i, g.profile.stars[i])) text.append(", ").append(StageNovelty.line(i))
        tileA11yKey[i] = key
        return text.toString().also { tileA11y[i] = it }
    }

    // ------------------------------------------------ R11-01: czytnik ekranu

    override fun accessibilityTitle(): String = L["campaign"]

    private val overlayOpen: Boolean get() = eraIntro?.active == true || hints.accessibilityOpen

    override fun accessibilityEligible(b: Button): Boolean = when {
        eraIntro?.active == true -> false
        hints.accessibilityOpen -> b === hints.okBtn
        b === hints.okBtn -> false
        // Okno etapu jest modalne: mapa i Wstecz pod nim nie mają węzłów.
        selected != null -> b === start || b === close || b.id in -12..-10
        else -> b === back || b.tag is Stage
    }

    override fun accessibilityKey(b: Button): Int {
        val stage = b.tag as? Stage ?: return 0
        val i = stage.index
        return g.profile.stars[i] or (if (g.profile.hardWon(i)) 4 else 0) or (if (b.highlight) 8 else 0)
    }

    /** Kolejność wzdłuż szlaku (nie według położenia na ekranie): nagłówek epoki, potem jej etapy. */
    override fun accessibilityOrder(b: Button, index: Int): Float {
        val stage = b.tag as? Stage
        return when {
            stage != null -> 10f + stage.index * 2f
            b.id in -12..-10 -> 1f + (-10 - b.id)
            b === start -> 4f
            b === close -> 5f
            else -> 0f
        }
    }

    override fun accessibilityCheckable(b: Button): Boolean = b.id in -12..-10
    override fun accessibilityChecked(b: Button): Boolean =
        if (b.id in -12..-10) g.profile.campaignDifficulty == CampaignDifficulty.entries[-10 - b.id] else b.selected

    /** Informacje 0..8: nagłówki widocznych epok; 9: okno (zapowiedź epoki, dymek albo panel etapu). */
    override fun accessibilityInfoCount(): Int = Defs.ERA_COUNT + 1

    override fun accessibilityInfoOrder(i: Int): Float =
        if (i == Defs.ERA_COUNT) -1f else 10f + i * 8f - 1f

    override fun accessibilityInfoDismiss(i: Int): Boolean = i == Defs.ERA_COUNT && eraIntro?.active == true

    override fun accessibilityInfo(i: Int): String {
        val intro = eraIntro
        if (i == Defs.ERA_COUNT) {
            val stage = selected
            return when {
                intro != null && intro.active -> {
                    if (introA11ySource !== intro) {
                        introA11ySource = intro
                        introA11y = intro.accessibilityText
                    }
                    introA11y
                }
                hints.accessibilityOpen -> hints.accessibilityBody
                stage != null -> panelText(stage)
                else -> ""
            }
        }
        if (overlayOpen || selected != null || !headingVisible(i)) return ""
        return eraText(i)
    }

    override fun accessibilityInfoBounds(i: Int, out: RectF) {
        if (i == Defs.ERA_COUNT) {
            when {
                eraIntro?.active == true -> out.setEmpty()
                hints.accessibilityOpen -> hints.accessibilityBounds(out)
                else -> out.set(info)
            }
            return
        }
        headingRect(i, out)
        if (!out.intersect(viewportL, top, viewportR, bottom)) out.setEmpty()
    }

    private fun headingVisible(era: Int): Boolean {
        headingRect(era, a11yRect)
        return a11yRect.intersects(viewportL, top, viewportR, bottom)
    }

    private fun eraText(era: Int): String {
        var earned = 0
        for (k in era * 4 until era * 4 + 4) earned += g.profile.stars[k]
        val crowns = if (showCrowns(era)) g.profile.hardWonInEra(era) else -1
        val key = earned or ((crowns + 1) shl 4) or (L.lang shl 8)
        if (eraA11yKey[era] != key) {
            eraA11yKey[era] = key
            val base = L.f("a196_era", L[Defs.eraKeys[era]], earned)
            eraA11y[era] = if (crowns < 0) base else base + ", " + L.f("a196_crowns", crowns)
        }
        return eraA11y[era]
    }

    /** Jedno zdanie panelu etapu: gwiazdki, korona, epoki, siła wroga, pogoda, modyfikatory, poziom. */
    private fun panelText(s: Stage): String {
        val difficulty = g.profile.campaignDifficulty
        val won = g.profile.hardWon(s.index)
        val key = s.index or (difficulty.ordinal shl 6) or (g.profile.stars[s.index] shl 8) or
            ((if (won) 1 else 0) shl 10) or (L.lang shl 11)
        if (key == panelA11yKey) return panelA11y
        panelA11yKey = key
        refreshInfo(s)
        val power = s.aiPower * difficulty.powerMultiplier
        val enemy = L[when {
            power > 1.02f -> "a196_enemy_stronger"
            power < 0.98f -> "a196_enemy_weaker"
            else -> "a196_enemy_equal"
        }]
        val line = hardInfoLine(s)
        val text = StringBuilder(infoTitle).append(". ")
            .append(L.f("a196_stars", g.profile.stars[s.index])).append(". ")
        if (line.isNotEmpty()) text.append(line).append(". ")
        val novelty = StageNovelty.line(s.index)
        if (novelty.isNotEmpty()) text.append(novelty).append(". ")
        text.append(infoRange).append(". ")
            .append(infoPower).append(", ").append(enemy).append(". ")
            .append(L.f("a196_weather", L[s.weather.key])).append(". ")
            .append(infoModifiers).append(' ')
            .append(if (s.modifiers.isEmpty()) L["none"] else s.modifiers.joinToString(", ") { L[it.key] })
            .append(". ")
            .append(L.f("a196_difficulty", L[difficulty.key]))
        panelA11y = text.toString()
        return panelA11y
    }

    override fun accessibilityCanScroll(direction: Int): Boolean {
        if (selected != null || overlayOpen) return false
        return if (direction > 0) scroll < maxScroll - 0.5f else scroll > 0.5f
    }

    /** Ta sama droga co przeciągnięcie palcem: przesunięcie mapy w jej osi o ok. 80% widoku. */
    override fun accessibilityScroll(direction: Int): Boolean {
        if (!accessibilityCanScroll(direction)) return false
        val moved = accessibilityStep(scroll, maxScroll, if (portraitMap) viewportH else viewportW, direction)
        scroll = scrollResult
        scrollTarget = scroll
        autoScroll = false
        velocity = 0f
        moving = false
        place()
        return moved
    }

    /** Tura 9: ostatni komunikat po dotknięciu zablokowanego etapu (do testów). */
    internal var lastNotice = ""

    // Zablokowany etap mówi, co go odblokuje, zamiast milczeć.
    override fun swallowsInactiveTap(b: Button): Boolean = b.tag !is Stage

    override fun onTap(x: Float, y: Float) {
        if (eraIntro?.active == true || selected != null) return
        val t = tiles.firstOrNull { it.contains(x, y) && !it.enabled } ?: return
        val need = g.profile.unlockedStage().coerceAtMost(tiles.lastIndex)
        hints.onAction()
        lastNotice = L.f("cp143_locked", need + 1)
        g.showNotice(lastNotice, Icons.LOCK)
        g.sfx(Sound.ERROR, 0.5f, 150)
    }

    override fun onBack(): Boolean {
        if (eraIntro?.active == true) { eraIntro?.dismiss(); return true }
        if (selected != null) { selected = null; return true }
        return false
    }

    private companion object {
        /** Pas nagłówka epoki w pionie: medalion (7–53 dp) i gwiazdki, plus oddech. */
        const val HEADING_DP = 58f
        /**
         * R10-02: korona węzła w prawym dolnym rogu (wolnym od pogody, flagi i błyskawicy), nieco
         * na zewnątrz: w punkcie (0,94 r; 0,68 r) kółko 12 dp zachodziło na trzecią gwiazdkę.
         */
        const val CROWN_DX = 1.02f
        const val CROWN_OUT_DP = 4f
        const val CROWN_DY = 0.5f
        val CROWN_BG = Color.rgb(176, 124, 36)
        /** R11-06: najmniejsze miejsce (dp) na skrócony napis nowości w wierszu gwiazdek. */
        const val NOVELTY_MIN_DP = 48f
        /** R10-04 L4: najwyżej tyle obrazów epok nagrywa jedna klatka. */
        const val REGION_RECORDS_PER_FRAME = 2
    }
}
