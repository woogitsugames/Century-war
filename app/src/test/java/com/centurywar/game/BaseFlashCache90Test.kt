package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.PorterDuff
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** Wizualna regresja błysku: prawdziwy korpus bazy, stary i nowy sposób kompozycji. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BaseFlashCache90Test {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun picture(): Picture {
        val profile = Profile(File.createTempFile("flash90-", ".json").apply { delete() })
        val g = Game(host, profile, Quality.HIGH)
        g.density = 2f
        g.resize(960, 540, 960, 540)
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 9090L, 1f, startEra = 0, maxEra = 0))
        val renderer = BattleRenderer(g, battle)
        renderer.layout(960f, 540f, 0f, 55f)
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bitmap), 1f / 60f, 960f, 540f)
        val field = BattleRenderer::class.java.getDeclaredField("basePics").apply { isAccessible = true }
        val pic = (field.get(renderer) as Array<*>).firstOrNull { it is Picture } as? Picture
        assertNotNull("Renderer musi nagrać korpus bazy", pic)
        bitmap.recycle()
        return pic!!
    }

    private fun save(bitmap: Bitmap, name: String) {
        val folder = File("build/screenshots").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun baseFlashLooksLikePreviousSaveLayerImplementation() {
        val pic = picture()
        val width = 460
        val height = 580
        val alpha = 84
        val before = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val oldCanvas = Canvas(before)
        oldCanvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        oldCanvas.drawPicture(pic)
        oldCanvas.drawColor(Color.argb(alpha, 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
        oldCanvas.restore()

        val after = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cache = BaseFlashCache(width, height)
        cache.draw(Canvas(after), pic, 0, alpha)
        save(before, "base_flash_90_before_saveLayer.png")
        save(after, "base_flash_90_after_colorFilter.png")

        var rgbDifference = 0L
        var covered = 0
        var alphaDifference = 0L
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val a = before.getPixel(x, y)
                val b = after.getPixel(x, y)
                alphaDifference += abs(Color.alpha(a) - Color.alpha(b))
                if (Color.alpha(a) == 0 && Color.alpha(b) == 0) continue
                rgbDifference += abs(Color.red(a) - Color.red(b))
                rgbDifference += abs(Color.green(a) - Color.green(b))
                rgbDifference += abs(Color.blue(a) - Color.blue(b))
                covered++
            }
        }
        val mean = rgbDifference.toDouble() / (covered * 3).coerceAtLeast(1)
        println("FLASH90: średnia różnica RGB=$mean; suma różnic alfa=$alphaDifference; piksele=$covered")
        assertTrue("Błysk ma zachować przezroczystość (różnica alfa $alphaDifference)",
            alphaDifference < 2500L)
        assertTrue("Błysk różni się wizualnie od saveLayer (średnia $mean)", mean < 5.0)
        before.recycle()
        after.recycle()
    }
}
