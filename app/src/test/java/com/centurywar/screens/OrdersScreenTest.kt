package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.SystemA11y
import com.centurywar.game.Battle
import com.centurywar.game.Mode
import com.centurywar.game.Orders
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dzień 62: ekran Rozkazów sztabu (lista, karta rozkazu) i wyniki bitwy rozkazu na telefonie,
 * rozłożonym i zewnętrznym Foldzie, przy 100% i 130% tekstu: napisy ≥ 10 sp, bez nachodzenia,
 * w bezpiecznym obszarze; zrzuty pl/en/de do build/screenshots (d62_*).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OrdersScreenTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val devices = linkedMapOf(
        "phone720" to floatArrayOf(1600f, 720f, 2f),
        "foldOuter" to floatArrayOf(904f, 2316f, 2.7f),
        "foldInner" to floatArrayOf(2208f, 1840f, 2.6f),
    )
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Recorder(bmp: Bitmap) : Canvas(bmp) {
        class Drawn(val s: String, val r: RectF, val sizePx: Float)
        val texts = ArrayList<Drawn>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && text.isNotBlank()) {
                val w = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - w / 2f
                    Paint.Align.RIGHT -> x - w
                    else -> x
                }
                val fm = paint.fontMetrics
                val r = RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f)
                // Teksty poza widocznym (przyciętym) obszarem płótna nie są widoczne.
                if (getClipBounds().let { c -> RectF.intersects(RectF(c), r) }) texts.add(Drawn(text, r, paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private fun game(d: FloatArray, lang: String, scale: Float, stars: Int = 36): Game {
        val f = File.createTempFile("orders62", ".json").apply { delete() }
        val profile = Profile(f).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false; medals = 410
            for (i in 0 until stars.coerceAtMost(this.stars.size)) this.stars[i] = 2
            orderStars[0] = 3; orderStars[1] = 1; orderStars[2] = 2
            orderPoints[0] = 3_200; orderPoints[1] = 1_150; orderPoints[2] = 2_210
        }
        return Game(host, profile, Quality.LOW).apply {
            density = d[2]
            resize(d[0].toInt(), d[1].toInt(), d[0].toInt(), d[1].toInt())
            setInsets(0, if (d[1] > d[0]) 88 else 0, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
        }
    }

    private fun frame(g: Game, name: String?): Recorder {
        g.finishScreenTransition()
        val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        repeat(2) { g.frame(tiny, 1f / 60f) }
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val rec = Recorder(bmp)
        g.frame(rec, 1f / 60f)
        if (name != null) File(out, "$name.png").outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return rec
    }

    /** Napisy w obszarze [area]: rozmiar ≥ 10 sp i brak wzajemnego nachodzenia. */
    private fun checkTexts(rec: Recorder, g: Game, area: RectF, where: String, problems: StringBuilder) {
        val dp = g.ui.dp
        val inside = rec.texts.filter { area.contains(it.r.centerX(), it.r.centerY()) }
        for (t in inside) {
            val sp = t.sizePx / dp / g.ui.textScale * g.ui.textScale
            if (t.sizePx / dp < 10f - 0.05f) problems.append("$where: '${t.s}' ${"%.1f".format(sp)} sp < 10\n")
            if (t.r.left < 0f || t.r.right > g.width + 0.5f) problems.append("$where: '${t.s}' poza ekranem\n")
        }
        for (i in inside.indices) for (j in i + 1 until inside.size) {
            val a = inside[i].r
            val b = inside[j].r
            if (inside[i].s == inside[j].s && a == b) continue // cień/obrys tego samego napisu
            val ix = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val iy = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (ix > 1.5f * dp && iy > 1.5f * dp)
                problems.append("$where: '${inside[i].s}' nachodzi na '${inside[j].s}'\n")
        }
    }

    private fun finishedOrderBattle(g: Game, index: Int): BattleScreen {
        val bs = BattleScreen(g, Orders.config(Orders.all[index], 62L))
        g.setScreen(bs)
        val battle = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }.get(bs) as Battle
        repeat(60 * 30) { battle.step(); battle.fx.count = 0 }
        battle.enemy.baseHp = 0f
        battle.step()
        assertTrue(battle.over)
        // Panel wyników po wjeździe (animacja ok. 0,5 s).
        repeat(60) { bs.update(1f / 30f) }
        return bs
    }

    @Test fun listDetailAndResultsReadableWithoutOverlap() {
        val old = L.lang
        val problems = StringBuilder()
        try {
            for ((tag, d) in devices) for (scale in floatArrayOf(1f, 1.3f)) for (lang in arrayOf("pl", "en", "de")) {
                val pct = (scale * 100).toInt()
                val g = game(d, lang, scale)
                val s = OrdersScreen(g)
                g.setScreen(s)
                val shotName = if (tag == "foldInner" && scale != 1f) null else "d62_${tag}_${pct}_${lang}"
                var rec = frame(g, shotName?.let { it + "_list" })
                val content = RectF(0f, g.insetT + 120f * g.ui.dp, g.width.toFloat(), g.height.toFloat())
                checkTexts(rec, g, content, "$tag $pct% $lang lista", problems)
                s.openForTests(1)
                rec = frame(g, shotName?.let { it + "_order" })
                checkTexts(rec, g, content, "$tag $pct% $lang karta", problems)
                s.openForTests(7)
                rec = frame(g, null)
                checkTexts(rec, g, content, "$tag $pct% $lang karta 8", problems)
                val bs = finishedOrderBattle(g, 2)
                rec = frame(g, shotName?.let { it + "_result" })
                val panel = BattleScreen::class.java.getDeclaredField("resultPanel").apply { isAccessible = true }.get(bs) as RectF
                val inner = RectF(panel.left, panel.top + 30f * g.ui.dp, panel.right, panel.bottom)
                checkTexts(rec, g, inner, "$tag $pct% $lang wyniki", problems)
                if (panel.top < g.insetT || panel.bottom > g.height) problems.append("$tag $pct% $lang: wyniki poza ekranem\n")
            }
        } finally {
            L.lang = old
            File(out, "d62_orders_layout.txt").writeText(problems.toString())
        }
        assertTrue("Rozkazy sztabu — układ:\n$problems", problems.isEmpty())
    }

    @Test fun settlementGivesMedalsOnlyForNewStarsAndSubmitsBoard() {
        val g = game(devices.getValue("phone720"), "pl", 1f)
        g.profile.orderStars.fill(0); g.profile.orderPoints.fill(0)
        val before = g.profile.medals
        val bs = finishedOrderBattle(g, 2)
        val stars = g.profile.orderStars[2]
        assertTrue("Wygrana daje co najmniej 1★", stars >= 1)
        val gained = g.profile.medals - before
        assertTrue("Medale za nowe gwiazdki ≥ ${Orders.medalsFor(0, stars)}", gained >= Orders.medalsFor(0, stars))
        assertEquals(g.profile.orderPointTotal(), g.profile.top(Profile.BOARD_ORDERS).first().score)
        assertTrue(bs.orderDone[0])
        // Druga, taka sama wygrana: bez medali za gwiazdki (nie da się farmić).
        val mid = g.profile.medals
        val again = BattleScreen(g, Orders.config(Orders.all[2], 62L))
        g.setScreen(again)
        val battle = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }.get(again) as Battle
        repeat(60 * 30) { battle.step(); battle.fx.count = 0 }
        battle.enemy.baseHp = 0f
        battle.step()
        repeat(3) { again.update(1f / 60f) }
        val starsAgain = g.profile.orderStars[2]
        assertTrue(starsAgain >= stars)
        if (starsAgain == stars) {
            val achievementOnly = g.profile.medals - mid
            assertEquals("Powtórka bez nowych gwiazdek: brak medali za rozkaz", 0, again.reward)
            assertTrue(achievementOnly >= 0)
        }
    }

    @Test fun lockedOrdersFollowCampaignAndBackReturnsToList() {
        val g = game(devices.getValue("foldOuter"), "pl", 1f, stars = 4)
        val s = OrdersScreen(g)
        g.setScreen(s)
        frame(g, null)
        assertTrue(Orders.unlocked(Orders.all[1], g.profile.stars, g.profile.orderStars))
        assertTrue(!Orders.unlocked(Orders.all[2], g.profile.stars, g.profile.orderStars))
        // Dzień 66: partia 2 — rozkaz 10 po 2★ w rozkazie 1 (profil ma 3★), rozkaz 11 zablokowany (1★ w rozkazie 2).
        assertTrue(Orders.unlocked(Orders.all[9], g.profile.stars, g.profile.orderStars))
        assertTrue(!Orders.unlocked(Orders.all[10], g.profile.stars, g.profile.orderStars))
        s.onClick(s.buttons.first { it.id == 110 })
        assertEquals("Zablokowany rozkaz partii 2 nie otwiera karty", -1, s.selectedForTests)
        assertTrue(s.accessibilityLabel(s.buttons.first { it.id == 110 }).contains(L[Orders.all[1].nameKey]))
        s.onClick(s.buttons.first { it.id == 102 })
        assertEquals("Zablokowany rozkaz nie otwiera karty", -1, s.selectedForTests)
        s.onClick(s.buttons.first { it.id == 101 })
        assertEquals(1, s.selectedForTests)
        assertEquals(L[Orders.all[1].nameKey], s.accessibilityTitle())
        assertTrue(s.onBack())
        assertEquals(-1, s.selectedForTests)
        assertEquals(L["o209_title"], s.accessibilityTitle())
        // Standardowe etykiety TalkBack: nazwa rozkazu i podpis z karty.
        val label = s.accessibilityLabel(s.buttons.first { it.id == 100 })
        assertTrue(label.startsWith(L[Orders.all[0].nameKey]))
    }

    /**
     * Dzień 66: lista 18 rozkazów przewinięta do partii 2 i karty nowych rozkazów (eskorta,
     * chuda zima, ostatni grot) — czytelne przy 100% i 130%; zrzuty phone720 i foldOuter pl/en
     * do /tmp/claude-0/d66 (gdy katalog jest dostępny) i build/screenshots (d66_*).
     */
    @Test fun batchTwoListAndCardsReadableWithoutOverlap() {
        val old = L.lang
        val problems = StringBuilder()
        val shots = File("/tmp/claude-0/d66").let { if (it.mkdirs() || it.isDirectory) it else null }
        fun save(name: String) {
            val src = File(out, "$name.png")
            if (shots != null && src.isFile) src.copyTo(File(shots, "$name.png"), overwrite = true)
        }
        try {
            for ((tag, d) in devices) for (scale in floatArrayOf(1f, 1.3f)) for (lang in arrayOf("pl", "en", "de")) {
                val pct = (scale * 100).toInt()
                val g = game(d, lang, scale)
                val s = OrdersScreen(g)
                g.setScreen(s)
                val shot = tag != "foldInner" && lang != "de"
                val base = "d66_${tag}_${pct}_${lang}"
                val content = RectF(0f, g.insetT + 120f * g.ui.dp, g.width.toFloat(), g.height.toFloat())
                frame(g, null)
                s.onDrag(0f, -100_000f)
                var rec = frame(g, if (shot) base + "_list2" else null)
                if (shot) save(base + "_list2")
                checkTexts(rec, g, content, "$tag $pct% $lang lista (partia 2)", problems)
                for (i in intArrayOf(9, 11, 17)) {
                    s.openForTests(i)
                    val name = if (shot) base + "_order${i + 1}" else null
                    rec = frame(g, name)
                    if (name != null) save(name)
                    checkTexts(rec, g, content, "$tag $pct% $lang karta ${i + 1}", problems)
                    s.onBack()
                }
            }
        } finally {
            L.lang = old
            File(out, "d66_orders_layout.txt").writeText(problems.toString())
        }
        assertTrue("Rozkazy sztabu, partia 2 — układ:\n$problems", problems.isEmpty())
    }

    @Test fun menuHasOrdersEntry() {
        for ((tag, d) in devices) {
            val g = game(d, "pl", 1f)
            val m = MenuScreen(g)
            g.setScreen(m)
            frame(g, null)
            val b = m.orders
            assertTrue("$tag: kafel Rozkazów widoczny lub w przewijanej liście", b.r.width() > 0f)
            assertTrue("$tag: kafel ≥ 48 dp", b.r.height() >= 48f * g.ui.dp - 0.5f)
            for (other in m.buttons) if (other !== b && other.visible && other.r.width() > 0f &&
                (m.portraitMenu || other.id in 1..9))
                assertTrue("$tag: kafel Rozkazów nachodzi na ${other.label}", !RectF.intersects(RectF(b.r).apply { inset(1f, 1f) }, other.r))
            m.onClick(b)
            val current = Game::class.java.getDeclaredField("screen").apply { isAccessible = true }.get(g)
            assertTrue(current is OrdersScreen)
        }
    }
}
