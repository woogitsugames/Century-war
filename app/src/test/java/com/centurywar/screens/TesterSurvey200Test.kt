package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.BuildConfig
import com.centurywar.crash.PlaytestReportText
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.SystemA11y
import com.centurywar.meta.PlaytestLog
import com.centurywar.meta.Profile
import com.centurywar.meta.TesterSurvey
import org.json.JSONObject
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R11 (pozycja 7): jednorazowa ankieta testera w menu. Tylko przy włączonej fladze wersji
 * testowej, po samouczku i progu (5 bitew albo 3. dzień), w spokojnym menu (bez banerów,
 * okien, dymków i „Co nowego”). Odpowiedź i pominięcie zapisują się w dzienniku testu,
 * trafiają do raportu testera i nie powodują ponownego pytania. Układ: 10 języków, telefon,
 * oba ekrany Folda, tekst 100% i 130%.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TesterSurvey200Test {
    @get:Rule val folder = TemporaryFolder()
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private val tiny = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private var savedLang = 0

    @Before fun saveLang() { savedLang = L.lang }
    @After fun restoreLang() { L.lang = savedLang }

    private fun game(dir: File = folder.newFolder(), battles: Int = 5, tutorial: Boolean = true,
                     w: Int = 1600, h: Int = 720, d: Float = 2f, insetT: Int = 0,
                     lang: String = "pl", scale: Float = 1f): Game {
        val p = Profile(File(dir, "profile.json"))
        p.lang = lang
        p.tutorialDone = tutorial
        p.hintsEnabled = false
        p.eraIntrosEnabled = false
        p.battles = battles
        p.whatsNewSeenVersion = BuildConfig.VERSION_NAME
        val g = Game(host, p, Quality.LOW).apply {
            density = d
            resize(w, h, w, h)
            if (insetT > 0) setInsets(0, insetT, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
            testerSurveyEnabled = true
        }
        g.playtest = PlaytestLog(File(dir, PlaytestLog.FILE_NAME)).also { it.load() }
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
    fun thresholdTutorialAndFlag() {
        assertFalse(TesterSurvey.due(true, PlaytestLog.SURVEY_NONE, true, 4, 2))
        assertTrue(TesterSurvey.due(true, PlaytestLog.SURVEY_NONE, true, 5, 1))
        assertTrue(TesterSurvey.due(true, PlaytestLog.SURVEY_NONE, true, 0, 3))
        assertFalse("Nie przed samouczkiem", TesterSurvey.due(true, PlaytestLog.SURVEY_NONE, false, 9, 9))
        assertFalse("Wersja produkcyjna", TesterSurvey.due(false, PlaytestLog.SURVEY_NONE, true, 9, 9))
        assertFalse(TesterSurvey.due(true, PlaytestLog.SURVEY_SKIPPED, true, 9, 9))
        assertFalse(TesterSurvey.due(true, PlaytestLog.SURVEY_ANSWERED, true, 9, 9))

        assertEquals(0, menu(game(battles = 4)).surveyStep)
        assertEquals(0, menu(game(tutorial = false, battles = 9)).surveyStep)
        assertEquals(0, menu(game().apply { testerSurveyEnabled = false }).surveyStep)
        assertEquals(0, menu(game().apply { playtest = null }).surveyStep)
        // Debug ma ankietę zawsze (flaga Gradle, chyba że -PtesterSurvey=false).
        assertTrue(BuildConfig.TESTER_SURVEY)
    }

    @Test
    fun waitsForQuietMenuWithoutBanners() {
        val g = game()
        val m = MenuScreen(g)
        g.setScreen(m)
        frames(g, 1f)
        assertEquals("Najpierw chwila spokojnego menu", 0, m.surveyStep)
        g.showNotice("baner", 0)
        frames(g, 2.5f)
        assertEquals("Nie razem z banerem", 0, m.surveyStep)
        g.clearNotices()
        frames(g, 2f)
        assertEquals(1, m.surveyStep)
        assertTrue(m.hasOpenModal)
        assertTrue(m.blocksNotices)
        // Banery podczas ankiety czekają w kolejce (nie rysują się nad oknem).
        g.showNotice("w kolejce", 0)
        frames(g, 0.5f)
        assertTrue(g.noticeBanner.isEmpty)
    }

    @Test
    fun ratingThenIssueGoesToReportOnce() {
        val dir = folder.newFolder()
        val g = game(dir)
        val m = menu(g)
        assertEquals(1, m.surveyStep)
        for (b in m.surveyRates) assertTrue(b.visible)
        for (b in m.surveyIssues) assertFalse(b.visible)
        m.onClick(m.surveyRates[3])
        assertEquals(2, m.surveyStep)
        for (b in m.surveyRates) assertFalse(b.visible)
        for (b in m.surveyIssues) assertTrue(b.visible)
        m.onClick(m.surveyIssues[1])
        assertEquals(0, m.surveyStep)
        assertFalse(m.hasOpenModal)
        assertTrue("Podziękowanie po zamknięciu", g.noticeTexts().contains(L["t200_thanks"]))
        val log = g.playtest!!
        assertEquals(PlaytestLog.SURVEY_ANSWERED, log.surveyState)
        assertEquals(4, log.surveyRating)
        assertEquals(1, log.surveyIssue)
        assertTrue(log.surveyDay >= 0L)

        val text = PlaytestReportText.build("1.0 (1)", "Acme", "15", PlaytestReportText.collect(g))
        assertTrue(text, text.contains("[Tester survey]\nRating: 4/5 ("))
        assertTrue(text, text.contains("Biggest issue: battles too long\n"))

        // Zapis na dysku i brak ponownego pytania po ponownym uruchomieniu.
        assertTrue(log.awaitWrites(5_000L))
        val again = game(dir)
        assertEquals(4, again.playtest!!.surveyRating)
        g.clearNotices()
        assertEquals(0, menu(again, 4f).surveyStep)
    }

    @Test
    fun skipAndBackNeverAskAgain() {
        val dir = folder.newFolder()
        val g = game(dir)
        val m = menu(g)
        assertEquals(1, m.surveyStep)
        m.onClick(m.surveySkip)
        assertEquals(0, m.surveyStep)
        assertTrue("Bez podziękowania po pominięciu", g.noticeTexts().isEmpty())
        assertEquals(PlaytestLog.SURVEY_SKIPPED, g.playtest!!.surveyState)
        frames(g, 4f)
        assertEquals(0, m.surveyStep)
        val text = PlaytestReportText.build("1.0 (1)", "Acme", "15", PlaytestReportText.collect(g))
        assertTrue(text, text.contains("[Tester survey]\nSkipped ("))
        assertTrue(g.playtest!!.awaitWrites(5_000L))
        assertEquals(0, menu(game(dir), 4f).surveyStep)

        // Wstecz = Pomiń; Wstecz po ocenie zostawia ocenę bez odpowiedzi na pytanie.
        val g2 = game()
        val m2 = menu(g2)
        assertTrue(g2.wantsBack)
        assertTrue(m2.onBack())
        assertEquals(PlaytestLog.SURVEY_SKIPPED, g2.playtest!!.surveyState)
        val g3 = game()
        val m3 = menu(g3)
        m3.onClick(m3.surveyRates[1])
        assertTrue(m3.onBack())
        assertEquals(0, m3.surveyStep)
        assertEquals(PlaytestLog.SURVEY_ANSWERED, g3.playtest!!.surveyState)
        assertEquals(2, g3.playtest!!.surveyRating)
        assertEquals(-1, g3.playtest!!.surveyIssue)
        val t3 = PlaytestReportText.build("1.0 (1)", "Acme", "15", PlaytestReportText.collect(g3))
        assertTrue(t3, t3.contains("Biggest issue: skipped\n"))
    }

    @Test
    fun oldLogWithoutSurveyFieldsLoadsAndRoundTrips() {
        val dir = folder.newFolder()
        val f = File(dir, PlaytestLog.FILE_NAME)
        val log = PlaytestLog(f)
        log.load()
        log.startSession(1_790_000_000_000L, 0L)
        val old = JSONObject(log.toJson())
        assertFalse("Bez ankiety plik jak dotąd", old.has("survey"))
        f.writeText(old.toString())
        val reread = PlaytestLog(f).also { it.load() }
        assertFalse(reread.fresh)
        assertEquals(PlaytestLog.SURVEY_NONE, reread.surveyState)
        reread.surveyIssueChosen(2)
        assertEquals("Pytanie tylko po ocenie", -1, reread.surveyIssue)
        reread.surveyRated(5, 1_790_000_000_000L)
        reread.surveyIssueChosen(3)
        f.writeText(reread.toJson())
        val third = PlaytestLog(f).also { it.load() }
        assertEquals(PlaytestLog.SURVEY_ANSWERED, third.surveyState)
        assertEquals(5, third.surveyRating)
        assertEquals(3, third.surveyIssue)
        // Zepsute wartości: ocena spoza 1–5 nie udaje odpowiedzi.
        f.writeText(JSONObject(third.toJson()).put("surveyRating", 9).put("surveyIssue", 7).toString())
        val bad = PlaytestLog(f).also { it.load() }
        assertEquals(PlaytestLog.SURVEY_SKIPPED, bad.surveyState)
        assertEquals(-1, bad.surveyIssue)
    }

    @Test
    fun accessibilityOnlySurveyButtonsWithLabels() {
        val g = game()
        val m = menu(g)
        for (step in 1..2) {
            assertEquals(step, m.surveyStep)
            for (b in m.buttons) {
                val surveyButton = b === m.surveySkip || b in m.surveyRates || b in m.surveyIssues
                if (b.visible) assertEquals("przycisk ${b.id}", surveyButton, m.accessibilityEligibleButton(b))
                if (surveyButton && b.visible) assertTrue("opis ${b.id}", m.accessibilityLabel(b).isNotBlank())
            }
            if (step == 1) {
                assertTrue(m.accessibilityLabel(m.surveyRates[0]).startsWith(L["t200_title"]))
                assertTrue(m.accessibilityLabel(m.surveyRates[2]).contains("3"))
                assertEquals(L["t200_skip"], m.accessibilityLabel(m.surveySkip))
                m.onClick(m.surveyRates[2])
            } else {
                assertTrue(m.accessibilityLabel(m.surveyIssues[0]).startsWith(L["t200_question"]))
            }
        }
    }

    private class Recorder(bmp: Bitmap) : Canvas(bmp) {
        class Drawn(val s: String, val r: RectF, val sizePx: Float)
        val texts = ArrayList<Drawn>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && text.isNotBlank()) {
                val w = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - w / 2f
                    Paint.Align.RIGHT -> x - w
                    else -> x
                }
                val fm = paint.fontMetrics
                texts.add(Drawn(text, RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f),
                    paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    @Test
    fun layoutFitsPhoneAndFoldInAllLanguages() {
        class Device(val name: String, val w: Int, val h: Int, val d: Float, val insetT: Int)
        val devices = listOf(
            Device("phone720", 1600, 720, 2f, 0),
            Device("foldOuter", 904, 2316, 2.7f, 88),
            Device("foldInner", 2184, 1968, 2.7f, 70),
        )
        val problems = StringBuilder()
        for (dev in devices) for (scale in floatArrayOf(1f, 1.3f)) for (lang in L.CODES) {
            val g = game(w = dev.w, h = dev.h, d = dev.d, insetT = dev.insetT, lang = lang, scale = scale)
            val m = menu(g)
            val dp = g.ui.dp
            for (step in 1..2) {
                val where = "${dev.name} $lang ${(scale * 100).toInt()}% krok $step"
                val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val rec = Recorder(bmp)
                g.frame(rec, 1f / 30f)
                val p = m.surveyPanel
                if (p.top < g.insetT - 0.5f || p.bottom > g.height + 0.5f || p.left < 0f || p.right > g.width)
                    problems.append("$where: panel poza ekranem\n")
                val shown = ArrayList<Button>()
                if (step == 1) shown.addAll(m.surveyRates) else shown.addAll(m.surveyIssues)
                shown.add(m.surveySkip)
                for ((i, b) in shown.withIndex()) {
                    val r = b.r
                    if (!p.contains(r)) problems.append("$where: '${b.label}' poza panelem\n")
                    if (r.height() < 48f * dp - 0.5f) problems.append("$where: '${b.label}' niższy niż 48 dp\n")
                    for (j in 0 until i) if (RectF.intersects(r, shown[j].r))
                        problems.append("$where: '${b.label}' nachodzi na '${shown[j].label}'\n")
                    val t = rec.texts.lastOrNull { it.s == b.label && r.contains(it.r.centerX(), it.r.centerY()) }
                    if (t == null) { problems.append("$where: brak napisu '${b.label}'\n"); continue }
                    val sp = t.sizePx / dp / g.ui.textScale
                    if (sp < 10f - 0.05f) problems.append("$where: '${b.label}' ${"%.1f".format(sp)} sp\n")
                    if (t.r.left < r.left || t.r.right > r.right) problems.append("$where: '${b.label}' wychodzi poza przycisk\n")
                }
                // Opis i skala w panelu, nad przyciskami oceny, nie na nich.
                val title = L[if (step == 1) "t200_title" else "t200_question"]
                val start = rec.texts.indexOfFirst { it.s == title }
                if (start < 0) problems.append("$where: brak tytułu\n")
                for (t in rec.texts.subList(start.coerceAtLeast(0), rec.texts.size)) {
                    val inPanel = p.contains(t.r.centerX(), t.r.centerY())
                    if (!inPanel) continue
                    if (shown.any { it.label == t.s }) continue
                    val sp = t.sizePx / dp / g.ui.textScale
                    if (sp < 10f - 0.05f) problems.append("$where: '${t.s}' ${"%.1f".format(sp)} sp\n")
                    if (t.r.left < p.left || t.r.right > p.right) problems.append("$where: '${t.s}' poza panelem\n")
                    for (b in shown) if (RectF.intersects(t.r, b.r)) problems.append("$where: '${t.s}' na przycisku '${b.label}'\n")
                }
                if (step == 1) m.onClick(m.surveyRates[4])
            }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }
}
