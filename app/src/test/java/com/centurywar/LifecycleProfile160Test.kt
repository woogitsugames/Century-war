package com.centurywar

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.GameView
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Music
import com.centurywar.engine.Palette
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Regresje A156-07, 17, 18, 19 i 20. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class LifecycleProfile160Test {
    @get:Rule val folder = TemporaryFolder()

    private class HostStub : Host {
        var fps = -1
        var reminders = 0
        var scales = 0
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) { scales++ }
        override fun applyFrameRate(fps: Int) { this.fps = fps }
        override fun exit() {}
        override fun onNotificationSettingsChanged() { reminders++ }
    }

    private fun game(host: HostStub = HostStub()): Game =
        Game(host, Profile(File(folder.root, "profile-${System.nanoTime()}.json")), Quality.LOW)

    @Test fun pausedActivityAndInvalidSurfaceNeverStartGameLoop() {
        val g = game()
        val view = GameView(RuntimeEnvironment.getApplication(), g)
        val holder = view.holder
        try {
            view.surfaceCreated(holder)
            view.surfaceChanged(holder, 0, 1600, 720)
            assertFalse("surfaceChanged nie może wznowić Activity", view.loopActive)
            view.onResume()
            view.onPause()
            view.surfaceChanged(holder, 0, 1600, 720)
            assertFalse("powierzchnia nie wznawia gry po onPause", view.loopActive)
            view.surfaceDestroyed(holder)
            view.onResume()
            assertFalse("bez powierzchni nie startujemy", view.loopActive)
        } finally {
            view.onPause()
            view.surfaceDestroyed(holder)
        }
    }

    @Test fun backDuringColdStartDoesNotAccessUninitializedGame() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue(activity.isFinishing)
        } finally {
            controller.destroy()
        }
    }

    @Test fun resetRemovesResumeOfferAndInvalidatesQueuedSnapshot() {
        val g = game()
        val file = File(folder.root, "battle.snap")
        val store = SnapshotStore(file)
        g.snapshots = store
        g.profile.medals = 95
        g.resumeOffer = RestoredBattle(Battle(BattleConfig(Mode.SKIRMISH, 7L, 1f)), false, -120f)
        assertTrue(store.saveNow(ByteArray(64) { it.toByte() }))
        assertTrue(file.exists())
        store.saveAsync(ByteArray(64) { (it + 1).toByte() })
        g.resetProgress()
        assertNull(g.resumeOffer)
        assertEquals(0, g.profile.medals)
        assertTrue(store.awaitWrites(5_000L))
        assertFalse("reset musi wyczyścić także plik snapshotu", file.exists())
        Profile.awaitHintWrites()
    }

    @Test fun loadedProfileUpdatesLanguageLayoutAudioAndRemindersTogether() {
        val host = HostStub()
        val g = game(host)
        val oldLang = L.lang
        val oldPalette = Palette.selected
        val music = Music()
        g.music = music
        try {
            g.density = 2f
            g.resize(1600, 720, 1600, 720)
            val imported = Profile(File(folder.root, "import.json"))
            imported.lang = "fr"
            imported.quality = Quality.MEDIUM
            imported.fpsCap = 30
            imported.leftHandedHud = true
            imported.palette = Palette.HIGH_CONTRAST
            imported.textScale = 130
            imported.reduceFlash = true
            imported.sound = false
            imported.sfxVolume = 0.1f
            imported.musicVolume = 0f
            imported.notifications = false
            imported.notifyHour = 7
            g.profile.fromJson(imported.toJson())
            g.applyLoadedProfile()

            assertEquals(L.indexOf("fr"), L.lang)
            assertEquals(Quality.MEDIUM, g.quality.level)
            assertEquals(30, host.fps)
            assertTrue(g.profile.leftHandedHud)
            assertTrue(g.profile.reduceFlash)
            assertEquals(Palette.HIGH_CONTRAST, Palette.selected)
            assertEquals(1.3f, g.ui.textScale, 0.001f)
            assertEquals(0.1f, g.profile.sfxVolume, 0.001f)
            assertFalse(g.profile.sound)
            assertEquals(0f, Music::class.java.getDeclaredField("volume").apply {
                isAccessible = true
            }.getFloat(music), 0.001f)
            assertFalse(g.profile.notifications)
            assertEquals(7, g.profile.notifyHour)
            assertEquals(1, host.reminders)
            assertTrue(host.scales > 0)
        } finally {
            music.release()
            Palette.select(oldPalette)
            L.lang = oldLang
        }
    }

    @Test fun densityUpdateRecalculatesDpWithoutReplacingScreen() {
        val g = game()
        g.density = 2f
        g.resize(2000, 1200, 2000, 1200)
        val before = g.ui.dp
        val screen = g.current
        g.updateDensity(3f)
        assertEquals(3f, g.density, 0.001f)
        assertTrue(g.ui.dp != before)
        assertSame(screen, g.current)
        val current = g.ui.dp
        g.updateDensity(Float.NaN)
        assertEquals(current, g.ui.dp, 0f)
    }
}
