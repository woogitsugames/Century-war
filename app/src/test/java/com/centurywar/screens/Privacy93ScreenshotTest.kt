package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.BuildConfig
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import com.centurywar.meta.ScoreClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** Ekrany prywatności na 720p i Foldzie oraz żądanie usunięcia bez sieci. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Privacy93ScreenshotTest {
    private val output = File("build/screenshots").apply { mkdirs() }

    private fun game(width: Int, height: Int, density: Float): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val profile = File.createTempFile("privacy93", ".json").apply { delete() }
        return Game(host, Profile(profile).apply { tutorialDone = true }, Quality.LOW).apply {
            this.density = density
            resize(width, height, width, height)
        }
    }

    private fun shot(g: Game, name: String) {
        g.finishScreenTransition()
        val bitmap = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        repeat(3) { g.frame(canvas, 1f / 60f) }
        val file = File(output, name + ".png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        assertTrue("Brak kadru " + name, file.length() > 1_000L)
    }

    @Test fun privacyFallbackOnPhoneAndFold() {
        val previous = L.lang
        try {
            // Debug musi umożliwiać odczyt offline nawet bez ustawionej domeny.
            assertEquals("", BuildConfig.PRIVACY_URL)
            L.setCode("pl")
            for ((label, dimensions) in mapOf(
                "phone720" to intArrayOf(1600, 720, 18),
                "foldOpen" to intArrayOf(2184, 1968, 27),
            )) {
                val g = game(dimensions[0], dimensions[1], dimensions[2] / 10f)
                val about = AboutScreen(g)
                g.setScreen(about)
                shot(g, label + "_privacy93_about")
                about.onClick(about.buttons.first { it.id == 1 })
                assertTrue(g.current is PrivacySummaryScreen)
                shot(g, label + "_privacy93_summary")
            }
        } finally {
            L.lang = previous
        }
    }

    @Test fun deletionIsConfirmedAndQueuedOffline() {
        val dir = Files.createTempDirectory("privacy93-deletion")
        val g = game(1600, 720, 1.8f)
        g.profile.enableGlobalRanking()
        val oldId = g.profile.playerId
        val queue = dir.resolve("scores.json").toFile()
        val client = ScoreClient(queue, "https://127.0.0.1:1", "a".repeat(32), g.profile)
        try {
            g.scores = client
            val about = AboutScreen(g)
            g.setScreen(about)
            val button = about.buttons.first { it.id == 4 }
            about.onClick(button)
            assertEquals("Pierwszy dotyk tylko prosi o potwierdzenie", oldId, g.profile.playerId)
            about.onClick(button)
            val tiny = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(tiny)
            try {
                repeat(300) {
                    if (g.profile.playerId == oldId) {
                        g.frame(canvas, 1f / 60f)
                        Thread.sleep(10)
                    }
                }
            } finally {
                tiny.recycle()
            }
            assertFalse("Brak zmiany identyfikatora po zapisaniu żądania", g.profile.playerId == oldId)
            assertFalse("Nie wysyłamy następnych wyników bez ponownej zgody", g.profile.globalRanking)
            val deletions = File(queue.path + ".deletions")
            assertTrue("Żądanie offline nie trafiło na dysk", deletions.isFile)
            assertTrue("Brak starego ID w kolejce", deletions.readText().contains(oldId))
        } finally {
            client.close()
            dir.resolve("scores.json.deletions").toFile().delete()
            dir.resolve("scores.json").toFile().delete()
            dir.toFile().delete()
        }
    }
}
