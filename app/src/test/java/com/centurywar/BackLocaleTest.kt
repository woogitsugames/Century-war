package com.centurywar

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.meta.Profile
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.SettingsScreen
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackLocaleTest {
    private val canvas = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))

    @Test fun backIsInterceptedOnlyWhenGameHandlesIt() {
        val changes = ArrayList<Boolean>()
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
            override fun onWantsBackChanged(wantsBack: Boolean) { changes.add(wantsBack) }
        }
        val file = File.createTempFile("cw-profile", ".json").apply { delete() }
        val game = Game(host, Profile(file).apply { tutorialDone = true }, Quality.LOW)
        game.density = 2f
        game.resize(1600, 720, 1600, 720)

        assertTrue(game.current is MenuScreen)
        assertFalse(game.wantsBack)
        assertTrue(changes.isEmpty())

        game.resumeOffer = RestoredBattle(Battle(BattleConfig(Mode.SKIRMISH, 7L, 1f)), false, -120f)
        game.frame(canvas, 0f)
        assertTrue(game.wantsBack)
        game.back() // Zamyka pytanie o wznowienie, ale nie Activity.
        assertFalse(game.wantsBack)

        game.push(SettingsScreen(game))
        assertTrue(game.wantsBack)
        game.back()
        assertTrue(game.current is MenuScreen)
        assertFalse(game.wantsBack)
        assertEquals(listOf(true, false, true, false), changes)
    }

    @Test fun activityConfigurationRefreshesAutoLocaleButPreservesManualSelection() {
        val oldLocale = Locale.getDefault()
        val oldLanguage = L.lang
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val field = MainActivity::class.java.getDeclaredField("game").apply { isAccessible = true }
            val game = field.get(activity) as Game
            // Zwykłe menu po samouczku (świeży profil pokazuje „Zagraj”, zlecenie 133).
            game.profile.tutorialDone = true
            game.density = 2f
            game.resize(1600, 720, 1600, 720)
            val menu = game.current as MenuScreen
            val campaign = menu.buttons.first { it.id == 1 }

            Locale.setDefault(Locale.ENGLISH)
            game.profile.lang = ""
            game.onLanguageChanged()
            assertEquals("Campaign", campaign.label)

            Locale.setDefault(Locale.FRENCH)
            activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply {
                setLocale(Locale.FRENCH)
            })
            game.frame(canvas, 0f) // Wykonuje game.post z onConfigurationChanged.
            assertEquals(L.indexOf("fr"), L.lang)
            assertEquals("Campagne", campaign.label)
            assertSame(menu, game.current)

            game.profile.lang = "en"
            game.onLanguageChanged()
            Locale.setDefault(Locale.GERMAN)
            activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply {
                setLocale(Locale.GERMAN)
            })
            game.frame(canvas, 0f)
            assertEquals(L.indexOf("en"), L.lang)
            assertEquals("Campaign", campaign.label)
            assertSame(menu, game.current)

            // Także podczas bitwy zmiana locale nie zastępuje ekranu ani stanu gry.
            game.snapshots = null
            val battle = BattleScreen(game, BattleConfig(Mode.SKIRMISH, 31L, 1f))
            game.setScreen(battle)
            game.profile.lang = ""
            Locale.setDefault(Locale.ITALIAN)
            activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply {
                setLocale(Locale.ITALIAN)
            })
            game.frame(canvas, 0f)
            assertEquals(L.indexOf("it"), L.lang)
            assertSame(battle, game.current)
        } finally {
            controller.pause().stop().destroy()
            Locale.setDefault(oldLocale)
            L.lang = oldLanguage
        }
    }
}
