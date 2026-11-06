package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapHint191Test {
    private var savedLang = 0
    @Before fun rememberLanguage() { savedLang = L.lang }
    @After fun restoreLanguage() { L.lang = savedLang; Profile.awaitHintWrites() }

    private data class Device(val width: Int, val height: Int, val density: Float, val insetTop: Int = 0)
    private data class Sample(val lines: Int, val panel: RectF)

    private fun run(device: Device, scale: Int, language: String): Sample {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("map191", ".json").apply { delete() }
        val profile = Profile(file).apply {
            lang = language; battles = 1; wins = 1; stars[0] = 1
            tutorialDone = true; eraIntrosEnabled = false; hintsEnabled = true
            textScale = scale; textScaleManual = true
        }
        val g = Game(host, profile, Quality.LOW)
        g.density = device.density
        g.resize(device.width, device.height, device.width, device.height)
        g.setInsets(0, device.insetTop, 0, 0)
        g.applyLanguage(); g.applyAccessibility()
        val screen = CampaignScreen(g)
        g.setScreen(screen)
        g.clearNotices()
        val hint = screen.javaClass.getDeclaredField("hints").apply { isAccessible = true }.get(screen) as HintOverlay
        repeat(6) { if (hint.activeId < 0) screen.update(.25f) }
        assertEquals("${device.width}x${device.height}/$language/$scale: brak dymka", HintOverlay.FIRST_BATTLE, hint.activeId)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try { screen.render(Canvas(bitmap)) } finally { bitmap.recycle() }
        val bubble = RectF(hint.panelRect)
        val tag = "${device.width}x${device.height}/$language/$scale"
        assertFalse("$tag: dymek nie został ułożony", bubble.isEmpty)
        val first = screen.buttons.first { it.id == 0 }
        val second = screen.buttons.first { it.id == 1 }
        assertTrue("$tag: oba węzły powinny być odsłonięte", first.visible && second.visible)
        assertFalse("$tag: dymek przecina etap 1", RectF.intersects(bubble, first.r))
        assertFalse("$tag: dymek przecina etap 2", RectF.intersects(bubble, second.r))
        val path = RectF(first.r).apply { union(second.r); inset(-6 * g.ui.dp, -6 * g.ui.dp) }
        assertFalse("$tag: dymek przecina drogę między etapami: $bubble / $path", RectF.intersects(bubble, path))
        val count = hint.javaClass.getDeclaredField("lineCount").apply { isAccessible = true }.getInt(hint)
        return Sample(count, bubble)
    }

    @Test fun mapRouteIsClearForAllLanguagesAndScales() {
        val devices = listOf(Device(1280, 720, 2f), Device(1600, 720, 2f),
            Device(904, 2316, 2.7f, 88), Device(2184, 1968, 2.7f))
        for (device in devices) for (scale in listOf(100, 130)) {
            val lines = mutableMapOf<String, Int>()
            for (language in L.CODES) lines[language] = run(device, scale, language).lines
            assertTrue("${device.width}x${device.height}/$scale: en ${lines["en"]} > pl ${lines["pl"]}",
                lines.getValue("en") <= lines.getValue("pl"))
        }
    }
}
