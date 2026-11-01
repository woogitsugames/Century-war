package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsNotificationsTest {
    @Test fun togglesNotificationsAndChangesReminderTime() {
        val file = File(Files.createTempDirectory("settings").toFile(), "profile.json")
        val profile = Profile(file)
        var changes = 0
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
            override fun onNotificationSettingsChanged() { changes++ }
            override fun requestNotifyHour(current: Int, onResult: (Int) -> Unit) { onResult(7) }
        }
        val screen = SettingsScreen(Game(host, profile, Quality.HIGH))
        screen.onClick(screen.buttons.first { it.id == 9 })
        assertFalse(profile.notifications)
        screen.onClick(screen.buttons.first { it.id == 10 })
        assertEquals(7, profile.notifyHour)
        assertEquals(2, changes)
        val saved = Profile(file).also { it.load() }
        assertFalse(saved.notifications)
        assertEquals(7, saved.notifyHour)
    }

    /** Wiersze nawigacyjne (dostępność, podpowiedzi, o grze) mają podpis na przycisku. */
    @Test fun navigationButtonsHaveLabels() {
        val file = File(Files.createTempDirectory("settings").toFile(), "profile.json")
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val screen = SettingsScreen(Game(host, Profile(file), Quality.HIGH))
        screen.update(0f)
        for (id in intArrayOf(14, 15)) {
            val button = screen.buttons.first { it.id == id }
            assertTrue("Przycisk $id bez podpisu", button.label.isNotBlank())
        }
    }
}
