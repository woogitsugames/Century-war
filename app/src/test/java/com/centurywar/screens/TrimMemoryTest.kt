package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R2-05: `onTrimMemory` zwalnia bufory rysowania (Picture, bitmapy błysku), a ekrany
 * odbudowują je same: kampania po powrocie wygląda tak samo, bitwa dalej rysuje błysk bazy,
 * stan bitwy zostaje nietknięty.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TrimMemoryTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(level: Int = Quality.HIGH): Game {
        val file = File.createTempFile("cw-trim-", ".json").apply { delete() }
        val profile = Profile(file).apply { tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false }
        return Game(host, profile, level).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
            finishScreenTransition()
        }
    }

    private fun pixels(s: com.centurywar.engine.Screen, g: Game): IntArray {
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        s.render(Canvas(bmp))
        return IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
    }

    @Test
    fun campaignRebuildsRegionPicturesAfterTrimAndLooksTheSame() {
        val g = game()
        val s = CampaignScreen(g)
        g.setScreen(s)
        g.finishScreenTransition()
        val field = CampaignScreen::class.java.getDeclaredField("regionPictures").apply { isAccessible = true }
        val scrollField = CampaignScreen::class.java.getDeclaredField("scroll").apply { isAccessible = true }
        val maxField = CampaignScreen::class.java.getDeclaredField("maxScroll").apply { isAccessible = true }
        fun pictures() = field.get(s) as Array<*>
        pixels(s, g)
        val before = pixels(s, g)
        val visible = pictures().indexOfFirst { it != null }
        val original = pictures()[visible] as Picture

        g.trimMemory(Game.TRIM_UI_HIDDEN)
        assertTrue("Obrazy epok zwolnione", pictures().all { it == null })

        // R10-04 L4: pierwsza klatka nagrywa najwyżej 2 obrazy epok, nie wszystkie 9.
        val recorded = s.recordedRegions
        pixels(s, g)
        val first = s.recordedRegions - recorded
        assertTrue("Pierwsza klatka nagrała $first obrazów", first in 1..2)
        assertTrue("Niewidoczne epoki czekają", pictures().any { it == null })
        assertNotSame(original, pictures()[visible])
        val after = pixels(s, g)
        assertArrayEquals("Mapa po powrocie z tła taka sama jak przed", before, after)

        // Przewijanie przez całą mapę nagrywa resztę po kolei, po ≤ 2 na klatkę.
        val start = scrollField.getFloat(s)
        val max = maxField.getFloat(s)
        var pos = 0f
        while (pos <= max) {
            scrollField.setFloat(s, pos)
            val n = s.recordedRegions
            pixels(s, g)
            assertTrue(s.recordedRegions - n <= 2)
            pos += 200f
        }
        scrollField.setFloat(s, max)
        pixels(s, g)
        pixels(s, g)
        assertTrue("Po przewinięciu wszystkie obrazy epok są nagrane", pictures().all { it != null })
        scrollField.setFloat(s, start)
        assertArrayEquals("Po przewinięciu mapa wygląda jak przed", before, pixels(s, g))
        // Kolejne klatki nie nagrywają obrazów ponownie.
        val rebuilt = pictures()[visible]
        val n = s.recordedRegions
        pixels(s, g)
        assertSame(rebuilt, pictures()[visible])
        assertEquals(n, s.recordedRegions)
    }

    @Test
    fun oldRunningLevelsAreTreatedLikeUiHidden() {
        val g = game()
        val s = CampaignScreen(g)
        g.setScreen(s)
        val field = CampaignScreen::class.java.getDeclaredField("regionPictures").apply { isAccessible = true }
        pixels(s, g)
        g.trimMemory(10) // TRIM_MEMORY_RUNNING_LOW (Android 13 i starsze)
        assertTrue((field.get(s) as Array<*>).all { it == null })
        pixels(s, g)
        assertTrue((field.get(s) as Array<*>).any { it != null })
    }

    @Test
    fun battleStillDrawsBaseFlashAfterBackgroundTrim() {
        val g = game()
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7701L, 1f, startEra = 0, maxEra = 0))
        g.setScreen(s)
        g.finishScreenTransition()
        val c = Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        s.battle.player.baseFlash = 1f
        s.render(c)
        assertTrue("Błysk bazy korzysta z bitmapy", s.r.cachedFlashBytes > 0L)
        val hp = s.battle.player.baseHp
        val time = s.battle.time

        g.trimMemory(Game.TRIM_BACKGROUND)
        assertEquals("Bitmapy błysku zwolnione", 0L, s.r.cachedFlashBytes)
        assertEquals("Stan bitwy bez zmian", hp, s.battle.player.baseHp, 0f)
        assertEquals(time, s.battle.time, 0f)

        s.battle.player.baseFlash = 1f
        s.render(c)
        assertTrue("Błysk bazy znów rysowany z bitmapy", s.r.cachedFlashBytes > 0L)
    }

    @Test
    fun battleBackdropIsRecordedAgainAfterTrim() {
        val g = game()
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7702L, 1f, startEra = 0, maxEra = 0))
        g.setScreen(s)
        g.finishScreenTransition()
        val before = pixels(s, g)
        val backdrops = s.r.javaClass.getDeclaredField("backdrops").apply { isAccessible = true }
            .get(s.r) as com.centurywar.game.art.Backdrops
        fun builds() = backdrops.cacheBuilds
        val n = builds()
        g.trimMemory(Game.TRIM_UI_HIDDEN)
        val after = pixels(s, g)
        assertEquals("Warstwy tła nagrane ponownie", n + 1, builds())
        assertNotNull(after)
        assertFalse("Tło po powrocie nie jest puste", after.all { it == after[0] })
        assertEquals(before.size, after.size)
    }
}
