package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Przyciski okna „Wznowić bitwę?” działają niezależnie od kafelków menu pod spodem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResumeDialogTouchTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
        override fun onWantsBackChanged(wantsBack: Boolean) {}
    }

    private fun tap(screen: com.centurywar.engine.Screen, x: Float, y: Float) {
        for (a in intArrayOf(TouchEvent.DOWN, TouchEvent.UP)) {
            val e = TouchEvent().apply { action = a; this.x = x; this.y = y; pointer = 0 }
            screen.handle(e)
        }
    }

    private fun check(w: Int, h: Int, density: Float, yes: Boolean) {
        val file = File.createTempFile("cw-resume", ".json").apply { delete() }
        val game = Game(host, Profile(file).apply { tutorialDone = true; battles = 3 }, Quality.LOW)
        game.density = density
        game.resize(w, h, w, h)
        game.resumeOffer = RestoredBattle(Battle(BattleConfig(Mode.ENDLESS, 7L, 1f)), false, -120f)
        val canvas = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        game.frame(canvas, 0f)
        val menu = game.current as MenuScreen
        val id = if (yes) 20 else 21 // MenuScreen.RESUME_YES / RESUME_NO
        val b = menu.buttons.first { it.id == id }
        assertTrue("${w}x$h: przycisk widoczny", b.visible)
        tap(menu, b.r.centerX(), b.r.centerY())
        assertNull("${w}x$h: tap w ${if (yes) "Wznów" else "Porzuć"} zamyka pytanie", game.resumeOffer)
        if (yes) assertTrue(game.current is BattleScreen)
    }

    @Test fun abandonWorksOnAllSizes() {
        check(1600, 720, 2f, yes = false)
        check(904, 2316, 2.7f, yes = false)
        check(2184, 1968, 2.6f, yes = false)
        check(2400, 1080, 2.6f, yes = false)
    }

    @Test fun resumeStillWorks() {
        check(1600, 720, 2f, yes = true)
        check(904, 2316, 2.7f, yes = true)
    }
}
