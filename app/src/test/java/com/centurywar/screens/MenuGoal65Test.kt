package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.BuildConfig
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * D65: „Następny cel” w menu jako cienki pasek pod logo, nad kaflami — na każdym układzie
 * (phone720, 20:9, rozłożony Fold poziomo i pionowo, zewnętrzny Fold). Sprawdza: pasek widoczny,
 * napis ≥ 11 sp i mieści się (wielokropek), nie nachodzi na kafle ani podtytuł, chowa się przy
 * banerze (jeden komunikat naraz), na foldOuter wszystkie kafle bez przewijania. Zrzuty do
 * `CW_D65_OUT` (domyślnie `build/screenshots/d65`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MenuGoal65Test {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private class Device(val name: String, val w: Int, val h: Int, val d: Float, val insetT: Int)

    private val devices = listOf(
        Device("phone720", 1600, 720, 2f, 0),
        Device("phone20x9", 2400, 1080, 2.7f, 0),
        Device("foldOpen", 2184, 1968, 2.7f, 0),
        Device("foldInner", 1802, 2000, 3.6f, 0),
        Device("foldOuter", 904, 2316, 2.7f, 88),
    )

    private val out = File(System.getenv("CW_D65_OUT") ?: "build/screenshots/d65").apply { mkdirs() }

    private fun game(dev: Device, lang: String, textScale: Int = 100): Game {
        val dir = Files.createTempDirectory("cw-d65").toFile()
        val p = Profile(File(dir, "profile.json"))
        p.lang = lang
        p.tutorialDone = true; p.hintsEnabled = false; p.eraIntrosEnabled = false
        p.battles = 6; p.whatsNewSeenVersion = BuildConfig.VERSION_NAME
        p.stars[0] = 3; p.stars[1] = 2; p.stars[2] = 1
        p.medals = 40
        p.textScale = textScale
        L.setCode(lang)
        return Game(host, p, Quality.HIGH).apply {
            density = dev.d
            resize(dev.w, dev.h, dev.w, dev.h)
            if (dev.insetT > 0) setInsets(0, dev.insetT, 0, 0)
            applyAccessibility()
        }
    }

    private fun frames(g: Game, c: Canvas, seconds: Float) {
        var t = 0f
        while (t < seconds) { g.frame(c, 1f / 30f); t += 1f / 30f }
    }

    @Test
    fun goalBarOnAllLayouts() {
        val old = L.lang
        try {
            for (dev in devices) for (lang in arrayOf("pl", "de", "en")) for (scale in intArrayOf(100, 130)) {
                val g = game(dev, lang, scale)
                g.clearNotices()
                val m = MenuScreen(g)
                g.setScreen(m)
                g.finishScreenTransition()
                val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                frames(g, c, 0.5f)
                val where = "${dev.name} $lang ${scale}%"
                val bar = m.goalBtn
                assertTrue("$where: pasek celu widoczny", bar.visible)
                assertTrue("$where: tekst celu", m.goalText.isNotEmpty())
                val d = g.ui.dp
                val r = bar.r
                assertTrue("$where: w ekranie", r.left >= g.insetL && r.right <= g.width - g.insetR && r.top >= g.insetT)
                assertTrue("$where: pod podtytułem", r.top >= m.noticeTop - 0.5f)
                // Nad wszystkimi kaflami (pierwszy rząd).
                val tiles = m.buttons.filter { it.id in 1..9 || it === m.orders }
                for (b in tiles) {
                    assertFalse("$where: nachodzi na kafel ${b.label}", RectF.intersects(r, b.r))
                    // Kodeks i Ustawienia w poziomie leżą w rogu ekranu (nad logo).
                    if (m.portraitMenu || (b.id != 7 && b.id != 8))
                        assertTrue("$where: pasek nad kaflem ${b.label}", r.bottom <= b.r.top)
                }
                assertTrue("$where: wysokość paska ${r.height() / d} dp", r.height() >= 22 * d - 0.5f)
                val size = field(m, "goalSize").getFloat(m)
                assertTrue("$where: napis ${size * g.ui.textScale} sp", size * g.ui.textScale >= 10.99f)
                assertTrue("$where: napis mieści się",
                    g.ui.measure(m.goalText, size, true) <= r.width() - 44 * d + 1f)
                if (dev.name == "foldOuter") {
                    if (scale == 100) {
                        assertEquals("$where: wszystkie kafle bez przewijania", 0f, m.portraitMaxScroll, 0.01f)
                        for (b in tiles) assertTrue("$where: kafel ${b.label} widoczny", b.visible)
                    }
                }
                if (scale == 100 && lang != "en") {
                    File(out, "menu_${dev.name}_$lang.png").outputStream().use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                // Jeden komunikat naraz: przy banerze pasek znika, a wraca po nim.
                g.showNotice("Test", com.centurywar.engine.Icons.STAR)
                frames(g, c, 0.2f)
                assertFalse("$where: pasek razem z banerem", bar.visible)
                if (scale == 100 && lang == "pl") {
                    File(out, "menu_${dev.name}_${lang}_baner.png").outputStream().use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                frames(g, c, 3.5f)
                assertTrue("$where: pasek wraca po banerze", bar.visible)
                bmp.recycle()
            }
        } finally {
            L.lang = old
        }
    }

    @Test
    fun tapOnGoalBarOpensGoal() {
        val old = L.lang
        try {
            for (dev in devices) {
                val g = game(dev, "pl")
                g.clearNotices()
                val m = MenuScreen(g)
                g.setScreen(m)
                g.finishScreenTransition()
                val c = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
                frames(g, c, 0.3f)
                assertTrue("${dev.name}: pasek widoczny", m.goalBtn.visible)
                val x = m.goalBtn.r.centerX(); val y = m.goalBtn.r.centerY()
                g.pushTouch(com.centurywar.engine.TouchEvent.DOWN, x, y, 0)
                g.pushTouch(com.centurywar.engine.TouchEvent.UP, x, y, 0)
                frames(g, c, 0.5f)
                assertFalse("${dev.name}: dotknięcie celu otwiera ekran", g.current is MenuScreen)
            }
        } finally {
            L.lang = old
        }
    }

    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
