package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.BattleMeta
import com.centurywar.game.Mode
import com.centurywar.meta.LostBattle
import com.centurywar.meta.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** R10-01: baner bitwy przerwanej aktualizacją (prawdziwe pomiary tekstu, zrzuty pl/en/de). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LostBattleBanner188Test {
    @get:Rule val folder = TemporaryFolder()

    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private inner class Run(dir: File, w: Int, h: Int, d: Float) {
        val profile = Profile(File(dir, "profile.json"))
        val game = Game(host, profile, Quality.LOW).also { g ->
            profile.tutorialDone = true
            profile.hintsEnabled = false
            profile.eraIntrosEnabled = false
            g.density = d
            g.resize(w, h, w, h)
        }

        fun menu() {
            game.setScreen(MenuScreen(game))
            game.frame(canvas, 1f / 60f)
        }
    }

    private fun shot(g: Game, name: String) {
        val out = File("build/screenshots").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bmp.recycle() }
    }

    /** Baner w menu na zewnętrznym ekranie Folda i telefonie 720p: najwyżej 2 czytelne wiersze. */
    @Test
    fun bannerFitsInEveryLanguage() {
        val old = L.lang
        try {
            val metas = listOf(
                BattleMeta(5L, Mode.ENDLESS, 0, -1L, -1L, false, 123, 900, 700, 1375, 0L),
                BattleMeta(6L, Mode.CAMPAIGN, 13, -1L, -1L, false, 0, 900, 700, 45, 0L),
                BattleMeta(7L, Mode.ENDLESS, 0, 3L, -1L, false, 48, 900, 700, 120, 0L),
                BattleMeta(8L, Mode.DAILY, 0, -1L, 1L, true, 0, 900, 700, 0, 0L),
            )
            val bad = mutableListOf<String>()
            for ((w, h, d) in listOf(Triple(904, 2316, 2.7f), Triple(1600, 720, 2f), Triple(720, 1600, 2f))) {
                for (code in L.CODES) {
                    val run = Run(folder.newFolder(), w, h, d)
                    run.profile.lang = code
                    run.game.onLanguageChanged()
                    run.menu()
                    for (m in metas) {
                        val texts = LostBattle.notices(m, LostBattle.Settlement(m.consolation, m.rankedDaily))
                        for (t in texts) {
                            run.game.clearNotices()
                            run.game.showNotice(t)
                            run.game.frame(canvas, 1f / 60f)
                            assertFalse(run.game.noticeBanner.isEmpty)
                            if (run.game.noticeTextSize * run.game.ui.textScale < 11f)
                                bad += "$code ${w}x$h: '$t' ma ${run.game.noticeTextSize} sp"
                            if (code in setOf("pl", "en", "de") && m.mode == Mode.ENDLESS && m.weekly < 0L)
                                shot(run.game, "lost188_${code}_${w}x${h}_${texts.indexOf(t)}")
                        }
                    }
                }
            }
            assertTrue(bad.joinToString("\n"), bad.isEmpty())
        } finally { L.lang = old }
    }
}
