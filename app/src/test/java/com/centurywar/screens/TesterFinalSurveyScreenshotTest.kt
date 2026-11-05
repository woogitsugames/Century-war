package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.BuildConfig
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.meta.PlaytestLog
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * D57: zrzuty ankiety końcowej z 10. dnia (telefon 720p i zewnętrzny ekran Folda, PL i EN) do
 * `build/screenshots/d57_*.png`: baner (jeden, w pasie powiadomień), polecenie 0–10 i pytanie
 * „co najbardziej się podoba”.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TesterFinalSurveyScreenshotTest {
    private val day = 86_400_000L
    private val t0 = 20_402L * day + 12L * 3600L * 1000L
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    @Test
    fun finalSurveyPhoneAndFoldOuter() {
        val old = L.lang
        val out = File("build/screenshots").apply { mkdirs() }
        try {
            class Device(val name: String, val w: Int, val h: Int, val d: Float, val insetT: Int)
            for (dev in listOf(Device("phone720", 1600, 720, 2f, 0), Device("foldOuter", 904, 2316, 2.7f, 88))) {
                for (lang in arrayOf("pl", "en")) {
                    val dir = Files.createTempDirectory("cw-d57-shot").toFile()
                    val p = Profile(File(dir, "profile.json"))
                    p.lang = lang
                    p.tutorialDone = true; p.hintsEnabled = false; p.eraIntrosEnabled = false
                    p.battles = 6; p.whatsNewSeenVersion = BuildConfig.VERSION_NAME
                    val g = Game(host, p, Quality.HIGH).apply {
                        density = dev.d
                        resize(dev.w, dev.h, dev.w, dev.h)
                        if (dev.insetT > 0) setInsets(0, dev.insetT, 0, 0)
                        testerSurveyEnabled = true
                        clock = { t0 + 9 * day }
                    }
                    g.playtest = PlaytestLog(File(dir, PlaytestLog.FILE_NAME)).also {
                        it.startSession(t0, 0L); it.surveySkipped(t0)
                    }
                    g.clearNotices()
                    val m = MenuScreen(g)
                    g.setScreen(m)
                    g.finishScreenTransition()
                    val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    var t = 0f
                    while (t < 2.2f) { g.frame(c, 1f / 30f); t += 1f / 30f }
                    assertTrue("${dev.name} $lang: baner", m.reportReminder.visible && m.reminderFinal)
                    save(bmp, File(out, "d57_banner_${dev.name}_$lang.png"))
                    m.onClick(m.reportReminder)
                    g.frame(c, 1f / 30f)
                    assertTrue(m.surveyStep == 3)
                    save(bmp, File(out, "d57_recommend_${dev.name}_$lang.png"))
                    m.onClick(m.finalNps[8])
                    g.frame(c, 1f / 30f)
                    assertTrue(m.surveyStep == 4)
                    save(bmp, File(out, "d57_liked_${dev.name}_$lang.png"))
                    bmp.recycle()
                }
            }
        } finally {
            L.lang = old
        }
    }

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
