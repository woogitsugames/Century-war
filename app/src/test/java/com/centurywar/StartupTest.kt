package com.centurywar

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import java.io.File
import org.robolectric.RuntimeEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pierwsze uruchomienie: Application + MainActivity przechodzą cały cykl startu bez wyjątku. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupTest {
    @Test fun coldStartDoesNotCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "w800dp-h400dp-land")
    fun phoneStaysLandscape() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
    }

    @Test @Config(qualifiers = "w840dp-h748dp-land")
    fun unfoldedFoldAllowsAnyOrientation() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, activity.requestedOrientation)
    }

    @Test
    fun corruptHistoryDoesNotBlockColdStart() {
        val file = File(RuntimeEnvironment.getApplication().filesDir, "history.json")
        val backup = if (file.exists()) file.readBytes() else null
        try {
            file.writeText("{\"v\":1,\"entries\":[broken")
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            controller.pause().stop().destroy()
        } finally {
            if (backup == null) file.delete() else file.writeBytes(backup)
        }
    }
}
