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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Podróż przez dziewięć epok. W poziomie regiony biegną od lewej do prawej,
 * na pionowym ekranie od góry do dołu. Tła są nagrane w Picture przy layout().
 */
class CampaignScreen(g: Game) : Screen(g) {
    override val crashName get() = "CampaignScreen"
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
    private var selected: Stage? = null
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
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val artPath = Path()
    private val artFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val eraNumbers = Array(Defs.ERA_COUNT) { (it + 1).toString() }
    private val stageNumbers = Array(Campaign.stages.size) { (it + 1).toString() }
    private val starLabels = Array(13) { it.toString() + "/12" }
    private var infoIndex = -1
    private var infoLanguage = -1
    private var infoTitle = ""
    private var infoRange = ""
    private var infoModifiers = ""
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
        infoPower = L.f("ai_power", (s.aiPower * difficulty.powerMultiplier * 100f).toInt())
    }

    override fun isDragScreen() = selected == null

    override fun onLanguageChanged() {
        super.onLanguageChanged()
        for (i in difficultyButtons.indices)
            difficultyButtons[i].label = L[CampaignDifficulty.entries[i].key]
        eraIntro?.onLanguageChanged()
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
            max(270 * dp, viewportH * 0.38f)
        } else {
            max(310 * dp, viewportW * 0.54f)
        }
        // Węzeł (z większym węzłem co czwarty) nie może być szerszy niż odstęp między etapami.
        // W pionie pole dotyku ma 3,1 r wysokości, a para „duży + zwykły” zajmuje 3,31 r,
        // więc na wąskich ekranach (zewnętrzny ekran Folda) potrzebny jest większy zapas.
        nodeR = min(nodeR, regionSpan / 4f / if (portraitMap) 3.45f else 3.3f)
        val visibleSpan = if (portraitMap) viewportH else viewportW
        val previousMax = maxScroll
        val previousScroll = scroll
        maxScroll = max(0f, regionSpan * Defs.ERA_COUNT - visibleSpan)

        for (i in tiles.indices) {
            val era = i / 4
            val step = i % 4
            val progress = (era + (step + 0.5f) / 4f) * regionSpan
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
            val last = g.profile.unlockedStage().coerceIn(0, tiles.lastIndex)
            val position = (last / 4f + (last % 4 + 0.5f) / 4f) * regionSpan
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
            autoScroll = false
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
        if (g.profile.battles == 1 && selected == null && !autoScroll) {
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

        for (e in 0 until Defs.ERA_COUNT) {
            val begin = e * regionSpan - scroll
            val span = if (portraitMap) viewportH else viewportW
            if (begin + regionSpan < 0f || begin > span) continue
            val x = if (portraitMap) viewportL else viewportL + e * regionSpan
            val y = if (portraitMap) top + e * regionSpan else top
            val picture = regionPictures[e] ?: continue
            c.save()
            c.translate(x, y)
            c.drawPicture(picture)
            c.restore()
            drawRegionHeading(c, e, x, y)
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
        val unlocked = g.profile.unlockedStage()
        for (i in tiles.indices) {
            val t = tiles[i]
            if (t.r.right < viewportL - nodeR || t.r.left > viewportR + nodeR ||
                t.r.bottom < top - nodeR || t.r.top > bottom + nodeR) continue
            drawNode(c, t, unlocked)
        }
        c.restore()
        ui.button(c, back, 16f)
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
    }

    /** Każdy obraz epoki jest wektorowy i tworzony tylko przy zmianie geometrii. */
    private fun rebuildPictures() {
        val rw = if (portraitMap) viewportW else regionSpan
        val rh = if (portraitMap) regionSpan else viewportH
        val wi = ceil(rw.toDouble()).toInt().coerceAtLeast(1)
        val hi = ceil(rh.toDouble()).toInt().coerceAtLeast(1)
        // Powrót z bitwy wywołuje layout bez zmiany mapy; nie nagrywamy 9 Picture ponownie.
        if (wi == pictureWidth && hi == pictureHeight && dp == pictureDp &&
            portraitMap == picturePortrait && regionPictures.all { it != null }) return
        for (era in regionPictures.indices) {
            val picture = Picture()
            val art = picture.beginRecording(wi, hi)
            drawRegionArt(art, era, rw, rh)
            picture.endRecording()
            regionPictures[era] = picture
        }
        pictureWidth = wi
        pictureHeight = hi
        pictureDp = dp
        picturePortrait = portraitMap
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
        var y = info.top + 48 * dp
        // Niski panel (telefon 720p w poziomie): ciaśniejsze wiersze, by zmieścić chipy nad trudnością.
        val row = if (info.height() < 352 * dp) 25 * dp else 28 * dp
        val x = info.left + 24 * dp
        val tw = info.width() - 48 * dp
        // gwiazdki
        ui.stars(c, info.centerX(), y + 4 * dp, 30 * dp, g.profile.stars[s.index], 3, 1.05f)
        y += 34 * dp
        val isz = 20 * dp
        Icons.draw(c, Icons.TOWER, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, infoRange, x + isz + 8 * dp, y, 14f, Ui.TEXT)
        y += row
        val pw = s.aiPower * g.profile.campaignDifficulty.powerMultiplier
        Icons.draw(c, if (pw > 1f) Icons.SKULL else Icons.CHECK, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, infoPower, x + isz + 8 * dp, y, 14f, if (pw > 1f) Ui.BAD else Ui.GOOD)
        ui.bar(c, x + tw * 0.62f, y - 10 * dp, tw * 0.38f, 8 * dp, (pw / 1.8f).coerceIn(0f, 1f), if (pw > 1f) Ui.BAD else Ui.GOOD)
        y += row
        Icons.draw(c, Icons.weatherIcon(s.weather), x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, L[s.weather.key], x + isz + 8 * dp, y, 14f, Ui.TEXT)
        y += row
        Icons.draw(c, Icons.FLAG, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, infoModifiers, x + isz + 8 * dp, y, 14f, Ui.GOLD)
        y += 10 * dp
        if (s.modifiers.isEmpty()) {
            ui.text(c, L["none"], x + isz + 8 * dp, y + 16 * dp, 13f, Ui.DIM)
        } else {
            chips(c, s, x, y, tw)
        }
        ui.textO(c, L["campaign126_difficulty"], x, difficultyButtons[0].r.top - 6 * dp,
            12f, Ui.GOLD)
        for (b in difficultyButtons) ui.button(c, b, 13f)
        ui.button(c, start, 18f)
        ui.closeButton(c, close)
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
                g.setScreen(BattleScreen(g, Campaign.config(s, g.profile.bonuses(), System.nanoTime(),
                    tutorial = s.index == 0 && !g.profile.tutorialDone,
                    difficulty = g.profile.campaignDifficulty)))
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

    override fun onBack(): Boolean {
        if (eraIntro?.active == true) { eraIntro?.dismiss(); return true }
        if (selected != null) { selected = null; return true }
        return false
    }
}
