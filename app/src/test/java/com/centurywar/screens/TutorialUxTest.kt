package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.meta.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Potwierdzenie pominięcia: jedno przypadkowe dotknięcie nie kończy nauki. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TutorialUxTest {
    @Test
    fun skipCanBeCancelledAndRequiresExplicitConfirmation() {
        val file = File.createTempFile("tutorial-85", ".json").apply { delete() }
        val profile = Profile(file).apply { tutorialDone = false }
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val game = Game(host, profile, Quality.LOW)
        game.density = 1.8f
        game.resize(1600, 720, 1600, 720)
        val screen = BattleScreen(game, Campaign.config(Campaign.stages[0], Bonuses(), 85L, tutorial = true))
        game.setScreen(screen)
        val tutorialField = screen.javaClass.getDeclaredField("tut").apply { isAccessible = true }
        val tutorial = tutorialField.get(screen) as TutorialOverlay
        val bitmap = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        fun tap(x: Float, y: Float) {
            game.pushTouch(TouchEvent.DOWN, x, y, 0)
            game.pushTouch(TouchEvent.UP, x, y, 0)
            game.frame(canvas, 1f / 60f)
        }

        try {
            game.frame(canvas, 1f / 60f)
            assertTrue(tutorial.showing)
            assertTrue(tutorial.skipBtn.r.height() + 0.01f >= 48 * game.ui.dp)
            val oldX = tutorial.skipBtn.r.centerX()
            val oldY = tutorial.skipBtn.r.centerY()
            tap(oldX, oldY)
            assertTrue(tutorial.skipConfirmationVisible)
            assertFalse(tutorial.finished)
            assertFalse(profile.tutorialDone)
            assertTrue(tutorial.timeScale == 0f)
            // Powtórne dotknięcie poprzedniego miejsca nie jest potwierdzeniem.
            tap(oldX, oldY)
            assertFalse(tutorial.finished)
            assertFalse(profile.tutorialDone)
            tap(tutorial.nextBtn.r.centerX(), tutorial.nextBtn.r.centerY())
            assertFalse(tutorial.skipConfirmationVisible)
            assertFalse(tutorial.finished)
            assertFalse(profile.tutorialDone)
            tap(tutorial.skipBtn.r.centerX(), tutorial.skipBtn.r.centerY())
            assertTrue(tutorial.skipConfirmationVisible)
            tap(tutorial.skipBtn.r.centerX(), tutorial.skipBtn.r.centerY())
            assertTrue(tutorial.finished)
            assertTrue(profile.tutorialDone)
            Profile.awaitHintWrites()
            assertTrue(file.exists())
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
