package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TutorialHand193Test {
    @Test fun handAvoidsArmyCounterInEveryStepAndLayout() {
        val sizes = listOf(
            Triple(1280, 720, 2f), Triple(1600, 720, 2f),
            Triple(904, 2316, 2.7f), Triple(2184, 1968, 2f),
        )
        for ((width, height, density) in sizes) for (left in listOf(false, true)) {
            val file = File.createTempFile("tutorial-193", ".json").apply { delete() }
            val profile = Profile(file).apply { tutorialDone = false; leftHandedHud = left }
            val host = object : Host {
                override fun vibrate(ms: Long) {}
                override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
                override fun applyRenderScale(scale: Float) {}
                override fun applyFrameRate(fps: Int) {}
                override fun exit() {}
            }
            val game = Game(host, profile, Quality.LOW).apply {
                this.density = density
                resize(width, height, width, height)
            }
            val screen = BattleScreen(game, Campaign.config(Campaign.stages[0], Bonuses(), 193L, tutorial = true))
            game.setScreen(screen)
            val tutorial = screen.tut!!
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            try {
                game.frame(canvas, 1f / 60f)
                val stepField = TutorialOverlay::class.java.getDeclaredField("step").apply { isAccessible = true }
                val showingField = TutorialOverlay::class.java.getDeclaredField("showing").apply { isAccessible = true }
                for (step in 0..TutorialOverlay.S_DONE) {
                    stepField.setInt(tutorial, step)
                    showingField.setBoolean(tutorial, true)
                    for (frame in 0..2) {
                        tutorial.draw(canvas)
                        assertFalse("$width x $height left=$left step=$step hand=${tutorial.handBounds} army=${screen.armyRect}",
                            RectF.intersects(tutorial.handBounds, screen.armyRect))
                        tutorial.update(0.03f)
                        showingField.setBoolean(tutorial, true)
                        stepField.setInt(tutorial, step)
                    }
                    if (step == TutorialOverlay.S_INCOME) {
                        assertTrue("Brak ręki dla złota: $width x $height left=$left", !tutorial.handBounds.isEmpty)
                    }
                }
            } finally {
                bitmap.recycle()
                Profile.awaitHintWrites()
                file.delete()
            }
        }
    }
}
