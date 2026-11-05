package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
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
 * D48: zrzuty banera „wyślij Raport dla testera” w menu (telefon 720p i zewnętrzny ekran Folda,
 * PL i EN) do `build/screenshots/d48_reminder_*.png`. Baner leży w pasie powiadomień: nad
 * kafelkami, pod logo, w bezpiecznym obszarze.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TesterReminderScreenshotTest {
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
    fun reminderBannerPhoneAndFoldOuter() {
        val old = L.lang
        val out = File("build/screenshots").apply { mkdirs() }
        try {
            class Device(val name: String, val w: Int, val h: Int, val d: Float, val insetT: Int)
            for (dev in listOf(Device("phone720", 1600, 720, 2f, 0), Device("foldOuter", 904, 2316, 2.7f, 88))) {
                for (lang in arrayOf("pl", "en")) {
                    val dir = Files.createTempDirectory("cw-d48-shot").toFile()
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
                    val r = m.reportReminder.r
                    assertTrue("${dev.name} $lang: baner widoczny", m.reportReminder.visible)
                    assertTrue("${dev.name} $lang: w ekranie", r.top >= g.insetT && r.right <= g.width && r.left >= 0f)
                    val tiles = m.buttons.filter { it.visible && it !== m.reportReminder && it.label.isNotEmpty() &&
                        it !== m.whatsNew && it !== m.goalBtn }
                    for (b in tiles) assertTrue("${dev.name} $lang: nachodzi na '${b.label}'", !RectF.intersects(r, b.r))
                    val file = File(out, "d48_reminder_${dev.name}_$lang.png")
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                }
            }
        } finally {
            L.lang = old
        }
    }
}
