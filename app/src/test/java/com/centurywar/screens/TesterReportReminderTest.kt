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
import com.centurywar.meta.TesterReportReminder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * D48 (R13 TOP 5 poz. 3): przypomnienie o Raporcie dla testera w 3. i 10. dniu testu, tylko
 * w wersjach testowych, raz na próg, nieblokujące, po ankiecie i banerach, tylko w menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TesterReportReminderTest {
    @get:Rule val folder = TemporaryFolder()
    private val day = 86_400_000L
    /** 10.11.2026 12:00 UTC. */
    private val t0 = 20_402L * day + 12L * 3600L * 1000L
    private var shares = 0
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
        override fun sharePlaytestReport() { shares++ }
    }
    private val tiny = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private var savedLang = 0

    @Before fun saveLang() { savedLang = L.lang }
    @After fun restoreLang() { L.lang = savedLang }

    private fun game(dir: File, now: Long, survey: Boolean = true, test: Boolean = true): Game {
        val p = Profile(File(dir, "profile.json"))
        p.tutorialDone = true; p.hintsEnabled = false; p.eraIntrosEnabled = false
        p.battles = 6
        p.whatsNewSeenVersion = BuildConfig.VERSION_NAME
        val g = Game(host, p, Quality.LOW).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
            testerSurveyEnabled = test
            clock = { now }
        }
        g.playtest = PlaytestLog(File(dir, PlaytestLog.FILE_NAME)).also {
            it.load()
            if (it.firstDay < 0L) it.startSession(t0, 0L)
            if (survey) it.surveySkipped(t0)
        }
        g.clearNotices()
        return g
    }

    private fun menu(g: Game, seconds: Float = 2f): MenuScreen {
        val m = MenuScreen(g)
        g.setScreen(m)
        frames(g, seconds)
        return m
    }

    private fun frames(g: Game, seconds: Float) {
        var t = 0f
        while (t < seconds) { g.frame(tiny, 1f / 30f); t += 1f / 30f }
    }

    @Test
    fun thresholdsOncePerDay() {
        val d3 = PlaytestLog.REMINDER_DAY3
        val d10 = PlaytestLog.REMINDER_DAY10
        assertEquals(0, TesterReportReminder.due(true, 100L, 101L, 0))
        assertEquals(3, TesterReportReminder.due(true, 100L, 102L, 0))
        assertEquals("spóźniony 3. dzień", 3, TesterReportReminder.due(true, 100L, 106L, 0))
        assertEquals(0, TesterReportReminder.due(true, 100L, 106L, d3))
        assertEquals(10, TesterReportReminder.due(true, 100L, 109L, d3))
        assertEquals("12. dzień bez 3.: tylko 10.", 10, TesterReportReminder.due(true, 100L, 111L, 0))
        assertEquals(0, TesterReportReminder.due(true, 100L, 120L, d3 or d10))
        assertEquals("wersja produkcyjna", 0, TesterReportReminder.due(false, 100L, 102L, 0))
        assertEquals("brak dziennika", 0, TesterReportReminder.due(true, -1L, 102L, 0))
        assertEquals("cofnięty zegar", 0, TesterReportReminder.due(true, 100L, 99L, 0))
        assertEquals(d3 or d10, TesterReportReminder.mask(10))
        assertEquals(d3, TesterReportReminder.mask(3))
    }

    @Test
    fun feedbackAddressKinds() {
        assertEquals(TesterReportReminder.FEEDBACK_NONE, TesterReportReminder.feedbackKind(""))
        assertEquals(TesterReportReminder.FEEDBACK_EMAIL, TesterReportReminder.feedbackKind("test@example.com"))
        assertEquals(TesterReportReminder.FEEDBACK_URL, TesterReportReminder.feedbackKind("https://forms.example/x"))
        assertEquals(TesterReportReminder.FEEDBACK_NONE, TesterReportReminder.feedbackKind("http://forms.example/x"))
        assertEquals(TesterReportReminder.FEEDBACK_NONE, TesterReportReminder.feedbackKind("nie adres"))
        // Domyślnie pusty: zachowanie jak dotąd.
        assertEquals("", BuildConfig.TESTER_FEEDBACK)
    }

    @Test
    fun bannerOnDay3ThenNotAgainSameThreshold() {
        val dir = folder.newFolder()
        val g = game(dir, t0 + 2 * day)
        val m = MenuScreen(g)
        g.setScreen(m)
        frames(g, 1f)
        assertEquals("najpierw chwila spokojnego menu", 0, m.reminderDay)
        frames(g, 1f)
        assertEquals(3, m.reminderDay)
        assertTrue(m.reportReminder.visible)
        assertFalse("nieblokujący", m.hasOpenModal)
        assertFalse(m.blocksNotices)
        assertEquals(PlaytestLog.REMINDER_DAY3, g.playtest!!.reminders)
        assertTrue(m.accessibilityLabel(m.reportReminder).contains("3"))
        // Powiadomienie chowa baner (ten sam pas), potem baner wraca.
        g.showNotice("baner", 0)
        frames(g, 0.2f)
        assertFalse(m.reportReminder.visible)
        g.clearNotices()
        frames(g, 0.2f)
        assertTrue(m.reportReminder.visible)
        // Dotyk otwiera to samo okno co „O grze”.
        m.onClick(m.reportReminder)
        assertEquals(1, shares)
        assertFalse(m.reportReminder.visible)
        // Zapis i ponowne wejście tego samego dnia: bez banera.
        g.playtest!!.saveAsync(); g.playtest!!.awaitWrites(2000L)
        val again = game(dir, t0 + 5 * day)
        assertEquals(0, menu(again, 3f).reminderDay)
        // 10. dzień: drugi raz.
        val tenth = game(dir, t0 + 9 * day)
        val m10 = menu(tenth, 3f)
        assertEquals(10, m10.reminderDay)
        assertEquals(PlaytestLog.REMINDER_DAY3 or PlaytestLog.REMINDER_DAY10, tenth.playtest!!.reminders)
    }

    @Test
    fun onlyTestBuildsAndAfterSurvey() {
        assertEquals(0, menu(game(folder.newFolder(), t0 + 2 * day, test = false), 3f).reminderDay)
        assertEquals("przed 3. dniem nic", 0, menu(game(folder.newFolder(), t0 + day), 3f).reminderDay)
        // Ankieta jeszcze nie odbyta: najpierw ankieta, przypomnienie czeka.
        val g = game(folder.newFolder(), t0 + 2 * day, survey = false)
        val m = menu(g, 3f)
        assertEquals(1, m.surveyStep)
        assertEquals(0, m.reminderDay)
        assertEquals(0, g.playtest!!.reminders)
    }

    @Test
    fun bannerTextInAllLanguages() {
        for (code in L.CODES) {
            L.setCode(code)
            for (d in intArrayOf(3, 10)) {
                val s = L.f("tr204_banner", d)
                assertTrue("$code: $s", s.contains(d.toString()))
                assertFalse(s.contains("%"))
            }
            assertTrue(L["tr204_code_note"].isNotBlank())
            assertTrue(L["tr204_form"].isNotBlank())
        }
    }
}
