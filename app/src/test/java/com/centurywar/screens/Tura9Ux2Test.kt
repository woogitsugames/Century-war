package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Defs
import com.centurywar.game.Stage
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Tura 9 (UX 2): poprawki z przeglądu nowego gracza na rozmiarach spoza ścieżki
 * NewPlayerJourneyTest — kampania w pionie (rozłożony Fold, podzielony ekran), etykiety
 * Ustawień we wszystkich językach i baner powiadomień przy powiększonym tekście.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Tura9Ux2Test {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    private fun game(w: Int, h: Int, density: Float, insetT: Int = 0, text: Int = 100, lang: String = "pl"): Game {
        val f = File.createTempFile("ux2", ".json").apply { delete() }
        val profile = Profile(f).apply { this.lang = lang; textScale = text }
        return Game(host, profile, Quality.LOW).apply {
            this.density = density
            resize(w, h, w, h)
            setInsets(0, insetT, 0, 0)
            applyAccessibility()
        }
    }

    private fun save(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "ux2_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bmp.recycle() }
    }

    @Test
    fun portraitCampaignNodesStayBelowEraHeadings() {
        val old = L.lang
        try {
            // Rozłożony Fold w pionie, połowa podzielonego ekranu, zewnętrzny ekran Folda.
            for ((tag, size) in listOf("foldInnerPortrait" to intArrayOf(1968, 2184), "split" to intArrayOf(1080, 1968),
                "foldOuter" to intArrayOf(904, 2316))) {
                val g = game(size[0], size[1], 2.7f, 70)
                g.profile.medals = 0
                val cs = CampaignScreen(g)
                g.setScreen(cs)
                repeat(10) { g.frame(tiny, 0.1f) }
                if (cs.blocksNotices) {
                    // Plansza nowej epoki jest oknem modalnym: bez banera powiadomień na tytule.
                    assertTrue("$tag: baner na planszy epoki", g.noticeBanner.isEmpty)
                    g.pushTouch(com.centurywar.engine.TouchEvent.DOWN, g.width / 2f, g.height / 2f, 0)
                    g.pushTouch(com.centurywar.engine.TouchEvent.UP, g.width / 2f, g.height / 2f, 0)
                    repeat(10) { g.frame(tiny, 0.1f) }
                }
                val head = RectF()
                var drags = 0
                while (drags++ < 80) {
                    for (era in 0 until Defs.ERA_COUNT) {
                        cs.headingRect(era, head)
                        if (head.bottom < 0f || head.top > g.height) continue
                        for (b in cs.buttons) if (b.visible && b.tag is Stage && RectF.intersects(b.r, head))
                            throw AssertionError("$tag: etap ${(b.tag as Stage).index + 1} nachodzi na nagłówek epoki ${era + 1} ($head / ${b.r})")
                    }
                    if (drags == 1) save(g, "kampania_$tag")
                    cs.onDrag(0f, -g.height * 0.2f)
                    g.frame(tiny, 0.05f)
                }
            }
        } finally { L.lang = old }
    }

    @Test
    fun settingsLabelsShareOneSizeInEveryLanguage() {
        val old = L.lang
        try {
            for (lang in L.CODES) for ((w, h, d) in listOf(Triple(904, 2316, 2.7f), Triple(1600, 720, 1.8f), Triple(2184, 1968, 2.7f))) {
                val g = game(w, h, d, lang = lang)
                val ss = SettingsScreen(g)
                g.setScreen(ss)
                repeat(3) { g.frame(tiny, 0.1f) }
                // Przewiń całą listę, żeby narysować każdy wiersz.
                repeat(20) { ss.onDrag(0f, -200f); g.frame(tiny, 0.05f) }
                for (rl in ss.rowLabelsDrawn()) assertTrue("$lang ${w}x$h: '${rl.text}' ma ${rl.size} sp",
                    rl.size >= SettingsScreen.LABEL_DP - 1.01f ||
                    // Jedno długie słowo (np. „Benachrichtigungen”) nie ma gdzie się złamać.
                    (!rl.text.trim().contains(' ') && rl.size >= 10f))
            }
        } finally { L.lang = old }
    }

    @Test
    fun noticeBannerLeavesTitleAndBackVisible() {
        val old = L.lang
        try {
            for (text in listOf(100, 130)) for ((w, h, d) in listOf(Triple(904, 2316, 2.7f), Triple(1600, 720, 1.8f),
                    Triple(2184, 1968, 2.7f), Triple(2400, 1080, 2.7f))) {
                val g = game(w, h, d, 60, text)
                val rs = ResearchScreen(g)
                g.setScreen(rs)
                g.frame(tiny, 0.1f)
                // R6-01: przy dymku podpowiedzi Badań baner czeka; tu sprawdzamy sam baner.
                (rs.javaClass.getDeclaredField("hints").apply { isAccessible = true }.get(rs) as HintOverlay).dismiss()
                g.showNotice(L["pgs144_why"])
                g.frame(tiny, 0.1f)
                val banner = RectF(g.noticeBanner)
                assertTrue("${w}x$h: brak banera", !banner.isEmpty)
                val title = RectF().also { Chrome(rs).titleBounds(L["research"], it) }
                val back = rs.buttons.first { it.id == -1 }.r
                assertTrue("${w}x$h tekst $text%: baner $banner na tytule $title", !RectF.intersects(banner, title))
                assertTrue("${w}x$h tekst $text%: baner $banner na Wstecz $back", !RectF.intersects(banner, back))
                assertTrue("${w}x$h tekst $text%: tekst banera ${g.noticeTextSize} sp",
                    g.noticeTextSize * g.ui.textScale >= 11f)
                if (text == 100 && w == 904) save(g, "baner_badania_foldOuter")
            }
        } finally { L.lang = old }
    }
}
