package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Zrzuty obu układów na telefonie, wąskim ekranie i rozłożonym Foldzie. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LeftHandedHudScreenshotTest {
    @Test
    fun leftHandedAndStandardHud110() {
        val out = File("build/screenshots").apply { mkdirs() }
        val devices = listOf(
            Pair("phone720", intArrayOf(1600, 720, 2)),
            Pair("narrow", intArrayOf(720, 1280, 2)),
            Pair("fold", intArrayOf(2184, 1968, 3)),
        )
        for ((tag, dims) in devices) {
            for (leftHanded in listOf(false, true)) {
                val host = object : Host {
                    override fun vibrate(ms: Long) {}
                    override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
                    override fun applyRenderScale(scale: Float) {}
                    override fun applyFrameRate(fps: Int) {}
                    override fun exit() {}
                }
                val file = File.createTempFile("hud110-shot", ".json").apply { delete() }
                val g = Game(host, Profile(file).apply {
                    tutorialDone = true
                    hintsSeen = Profile.HINT_VALID_MASK
                    leftHandedHud = leftHanded
                }, Quality.LOW)
                g.density = dims[2].toFloat()
                g.resize(dims[0], dims[1], dims[0], dims[1])
                g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, 110L, 1f)))
                g.finishScreenTransition()
                val bitmap = Bitmap.createBitmap(dims[0], dims[1], Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                repeat(3) { g.frame(canvas, 1f / 60f) }
                val mode = if (leftHanded) "left" else "standard"
                File(out, "hud110_${mode}_${tag}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
}
