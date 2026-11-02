package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Dowódca na tanim telefonie i obu ekranach Folda, również dla leworęcznych. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CommanderHudScreenshotTest {
    @Test fun commander125HudFormatsAndTouchTargets() {
        val out = File("build/screenshots").apply { mkdirs() }
        val devices = mapOf(
            "phone720" to intArrayOf(720, 1280, 20),
            "foldCover" to intArrayOf(904, 2316, 27),
            "foldOpen" to intArrayOf(2184, 1968, 30),
        )
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        for ((tag, dims) in devices) for (leftHanded in listOf(false, true)) {
            val f = File.createTempFile("commander125", ".json").apply { delete() }
            val g = Game(host, Profile(f).apply {
                tutorialDone = true
                hintsSeen = Profile.HINT_VALID_MASK
                leftHandedHud = leftHanded
            }, Quality.LOW)
            g.density = dims[2] / 10f
            g.resize(dims[0], dims[1], dims[0], dims[1])
            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 125L, 1f))
            bs.battle.player.gold = 10000f
            g.setScreen(bs)
            g.finishScreenTransition()
            val bmp = Bitmap.createBitmap(dims[0], dims[1], Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bmp)
                repeat(3) { g.frame(canvas, 1f / 60f) }
                val b = bs.commanderBtn
                assertTrue("$tag: przycisk musi być widoczny", b.visible)
                assertTrue("$tag: cel powinien mieć co najmniej 48dp",
                    b.r.width() >= 47.5f * g.ui.dp && b.r.height() >= 47.5f * g.ui.dp)
                g.pushTouch(TouchEvent.DOWN, b.r.centerX(), b.r.centerY(), 0)
                g.pushTouch(TouchEvent.UP, b.r.centerX(), b.r.centerY(), 0)
                g.frame(canvas, 1f / 60f)
                assertTrue("$tag: dowódca został przywołany", bs.battle.units.any { it.isCommander && it.alive && it.side == 0 })
                assertEquals(Replay.Kind.SUMMON_COMMANDER, bs.commands.log().commands.last().kind)
                val side = if (leftHanded) "left" else "standard"
                File(out, "commander125_${tag}_${side}.png").outputStream().use {
                    assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bmp.recycle()
            }
        }
    }
}
