package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.centurywar.BuildConfig
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.json.JSONObject
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

/** Regresja: uruchomienie, zamknięcie, zapis i wygląd na telefonie/Foldzie. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WhatsNewScreenshotTest {
    private fun file(): File = File(Files.createTempDirectory("cw-whatsnew").toFile(), "profile.json")

    private fun game(profile: Profile, width: Int = 1600, height: Int = 720,
                     density: Float = 2f, quality: Int = Quality.LOW): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val g = Game(host, profile, quality)
        g.density = density
        g.resize(width, height, width, height)
        return g
    }

    @Test fun firstInstallSkipsNewsAndStoresBaseline() {
        val f = file()
        val p = Profile(f).also { it.load() }
        assertFalse(p.hasExistingSave)
        val g = game(p)
        assertTrue(g.current is MenuScreen)
        Profile.awaitHintWrites()
        val loaded = Profile(f).also { it.load() }
        assertTrue(loaded.hasExistingSave)
        assertEquals(BuildConfig.VERSION_NAME, loaded.whatsNewSeenVersion)
    }

    @Test fun oldProfileShowsOnceBeforeMenu() {
        val f = file()
        Profile(f).apply { tutorialDone = true; save() }
        Profile.awaitHintWrites()
        // Symulujemy profil z wydania sprzed dodania znacznika aktualizacji.
        f.writeText(JSONObject(f.readText()).apply { remove("whatsNewSeenVersion") }.toString())
        val p = Profile(f).also { it.load() }
        assertTrue(p.hasExistingSave)
        assertEquals("", p.whatsNewSeenVersion)

        val g = game(p)
        assertTrue(g.current is WhatsNewScreen)
        g.back()
        assertTrue(g.current is MenuScreen)
        Profile.awaitHintWrites()

        val loaded = Profile(f).also { it.load() }
        assertEquals(BuildConfig.VERSION_NAME, loaded.whatsNewSeenVersion)
        assertTrue(game(loaded).current is MenuScreen)
    }

    @Test fun invalidVersionIsSanitizedAndCloudDoesNotOverwriteLocalState() {
        val f = file()
        val profile = Profile(f)
        profile.whatsNewSeenVersion = "0.1.0-dev"
        val remote = Profile(file()).apply { medals = 100 }
        profile.fromJson(remote.toCloudJson())
        assertEquals("0.1.0-dev", profile.whatsNewSeenVersion)
        assertFalse(JSONObject(profile.toCloudJson()).has("whatsNewSeenVersion"))

        val bad = JSONObject(profile.toJson()).put("whatsNewSeenVersion", "../bad version")
        f.writeText(bad.toString())
        val loaded = Profile(f).also { it.load() }
        assertEquals("", loaded.whatsNewSeenVersion)
    }

    @Test fun historyFromAboutDoesNotAlterSeenVersion() {
        val p = Profile(file())
        val g = game(p)
        p.whatsNewSeenVersion = "0.0.9"
        g.push(AboutScreen(g))
        val about = g.current as AboutScreen
        about.onClick(about.buttons.first { it.id == 5 }) // 5 = Co nowego (4 to usuwanie danych po 93)
        assertTrue(g.current is WhatsNewScreen)
        g.back()
        assertTrue(g.current === about)
        assertEquals("0.0.9", p.whatsNewSeenVersion)
    }

    @Test fun screenshotsOn720pAndFold() {
        val originalLang = L.lang
        val out = File("build/screenshots").apply { mkdirs() }
        try {
            L.lang = L.PL
            val cases = listOf(
                Pair("phone720", intArrayOf(1600, 720, 2, Quality.LOW)),
                Pair("foldOpen", intArrayOf(2184, 1968, 3, Quality.HIGH)),
            )
            for ((tag, dims) in cases) {
                val p = Profile(file()).apply { tutorialDone = true; lang = "pl" }
                val g = game(p, dims[0], dims[1], dims[2].toFloat(), dims[3])
                for (automatic in listOf(true, false)) {
                    g.setScreen(WhatsNewScreen(g, automatic))
                    g.finishScreenTransition()
                    val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    g.frame(canvas, 1f / 60f)
                    assertTrue(bmp.getPixel(g.width / 2, g.height / 2) != Color.TRANSPARENT)
                    val name = "whatsnew106_${tag}_${if (automatic) "automatic" else "history"}.png"
                    val image = File(out, name)
                    image.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    assertTrue(image.isFile && image.length() > 3000L)
                    bmp.recycle()
                }
            }
        } finally {
            L.lang = originalLang
        }
    }
}
