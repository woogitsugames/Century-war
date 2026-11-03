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
        fun pictures() = field.get(s) as Array<*>
        val before = pixels(s, g)
        val original = pictures()[0] as Picture

        g.trimMemory(Game.TRIM_UI_HIDDEN)
        assertTrue("Obrazy epok zwolnione", pictures().all { it == null })

        val after = pixels(s, g)
        assertTrue("Obrazy epok odbudowane przy render()", pictures().all { it != null })
        assertNotSame(original, pictures()[0])
        assertArrayEquals("Mapa po powrocie z tła taka sama jak przed", before, after)
        // Kolejne klatki nie nagrywają obrazów ponownie.
        val rebuilt = pictures()[0]
        pixels(s, g)
        assertSame(rebuilt, pictures()[0])
    }

    @Test
    fun oldRunningLevelsAreTreatedLikeUiHidden() {
        val g = game()
        val s = CampaignScreen(g)
        g.setScreen(s)
        val field = CampaignScreen::class.java.getDeclaredField("regionPictures").apply { isAccessible = true }
        g.trimMemory(10) // TRIM_MEMORY_RUNNING_LOW (Android 13 i starsze)
        assertTrue((field.get(s) as Array<*>).all { it == null })
        pixels(s, g)
        assertTrue((field.get(s) as Array<*>).all { it != null })
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
