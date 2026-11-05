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
 * D57 (R13 TOP 5 poz. 5): ankieta końcowa w 10. dniu testu, tylko w wersjach testowych.
 * Jeden baner 10. dnia (pytania, potem okno Raportu dla testera), raz, z „Pomiń”, nigdy w dniu
 * pierwszej ankiety; odpowiedzi tylko lokalnie w `playtest.json` i w sekcji `[Final survey]`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TesterFinalSurveyTest {
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

    /** [surveyAt] — dzień pierwszej ankiety (pominięta) jako przesunięcie od t0; null = bez ankiety. */
    private fun game(dir: File = folder.newFolder(), now: Long = t0 + 9 * day, surveyAt: Long? = 0L,
                     test: Boolean = true, w: Int = 1600, h: Int = 720, d: Float = 2f, insetT: Int = 0,
                     lang: String = "pl", scale: Float = 1f): Game {
        val p = Profile(File(dir, "profile.json"))
        p.lang = lang
        p.tutorialDone = true; p.hintsEnabled = false; p.eraIntrosEnabled = false
        p.battles = 6
        p.whatsNewSeenVersion = BuildConfig.VERSION_NAME
        val g = Game(host, p, Quality.LOW).apply {
            density = d
            resize(w, h, w, h)
            if (insetT > 0) setInsets(0, insetT, 0, 0)
            if (scale != 1f) applySystemAccessibility(SystemA11y(scale, false, false))
            testerSurveyEnabled = test
            clock = { now }
        }
        g.playtest = PlaytestLog(File(dir, PlaytestLog.FILE_NAME)).also {
            it.load()
            if (it.firstDay < 0L) it.startSession(t0, 0L)
            if (surveyAt != null) it.surveySkipped(t0 + surveyAt * day)
        }
        g.clearNotices()
        return g
    }

    private fun menu(g: Game, seconds: Float = 3f): MenuScreen {
        val m = MenuScreen(g)
        g.setScreen(m)
        frames(g, seconds)
        return m
    }

    private fun frames(g: Game, seconds: Float) {
        var t = 0f
        while (t < seconds) { g.frame(tiny, 1f / 30f); t += 1f / 30f }
    }

    private fun report(g: Game) = PlaytestReportText.build("1.0 (1)", "Acme", "15", PlaytestReportText.collect(g))

    @Test
    fun dueFromDayTenOnceNotOnFirstSurveyDay() {
        val f = PlaytestLog.REMINDER_FINAL
        val none = PlaytestLog.FINAL_NONE
        assertFalse("9. dzień", TesterSurvey.finalDue(true, none, 100L, 108L, 0, 100L))
        assertTrue("10. dzień", TesterSurvey.finalDue(true, none, 100L, 109L, 0, 102L))
        assertTrue("spóźnione wejście", TesterSurvey.finalDue(true, none, 100L, 130L, 0, -1L))
        assertTrue("aktualizacja po banerze z 10. dnia (d48)",
            TesterSurvey.finalDue(true, none, 100L, 111L, PlaytestLog.REMINDER_DAY3 or PlaytestLog.REMINDER_DAY10, 102L))
        assertFalse("baner już był", TesterSurvey.finalDue(true, none, 100L, 109L, f, 102L))
        assertFalse("pierwsza ankieta dziś", TesterSurvey.finalDue(true, none, 100L, 109L, 0, 109L))
        assertTrue("ale czeka", TesterSurvey.finalPending(true, none, 100L, 109L, 0))
        assertFalse("wersja produkcyjna", TesterSurvey.finalDue(false, none, 100L, 109L, 0, 102L))
        assertFalse(TesterSurvey.finalDue(true, PlaytestLog.FINAL_SKIPPED, 100L, 109L, 0, 102L))
        assertFalse(TesterSurvey.finalDue(true, PlaytestLog.FINAL_ANSWERED, 100L, 109L, 0, 102L))
        assertFalse("brak dziennika", TesterSurvey.finalDue(true, none, -1L, 109L, 0, 102L))
        assertFalse("cofnięty zegar", TesterSurvey.finalDue(true, none, 100L, 99L, 0, 102L))
        assertEquals(PlaytestLog.FINAL_LIKED.size, TesterSurvey.FINAL_OPTIONS)
        assertEquals(PlaytestLog.FINAL_ISSUES.size, TesterSurvey.FINAL_OPTIONS)
    }

    @Test
    fun oneBannerThenQuestionsThenReport() {
        val dir = folder.newFolder()
        val g = game(dir)
        val m = MenuScreen(g)
        g.setScreen(m)
        frames(g, 1f)
        assertEquals("najpierw chwila spokojnego menu", 0, m.reminderDay)
        frames(g, 1f)
        assertEquals(10, m.reminderDay)
        assertTrue(m.reminderFinal)
        assertTrue(m.reportReminder.visible)
        assertFalse("baner nieblokujący", m.hasOpenModal)
        assertEquals(L["tr205_banner"], m.accessibilityLabel(m.reportReminder))
        val log = g.playtest!!
        assertEquals(PlaytestLog.REMINDER_DAY3 or PlaytestLog.REMINDER_DAY10 or PlaytestLog.REMINDER_FINAL, log.reminders)
        // Jeden baner: brak drugiego przypomnienia w tym samym menu.
        frames(g, 3f)
        assertEquals(10, m.reminderDay)

        m.onClick(m.reportReminder)
        assertEquals("najpierw pytania, raport po nich", 0, shares)
        assertFalse(m.reportReminder.visible)
        assertEquals(3, m.surveyStep)
        assertTrue(m.hasOpenModal)
        for (b in m.finalNps) assertTrue(b.visible)
        assertTrue(m.surveySkip.visible)
        m.onClick(m.finalNps[8])
        assertEquals(4, m.surveyStep)
        for (b in m.finalNps) assertFalse(b.visible)
        for (b in m.finalOptions) assertTrue(b.visible)
        assertEquals(L["tr205_like_campaign"], m.finalOptions[0].label)
        m.onClick(m.finalOptions[0])
        assertEquals(5, m.surveyStep)
        assertEquals(L["tr205_issue_easy"], m.finalOptions[1].label)
        m.onClick(m.finalOptions[2])
        assertEquals(0, m.surveyStep)
        assertFalse(m.hasOpenModal)
        assertEquals("okno raportu po ankiecie", 1, shares)
        assertTrue(g.noticeTexts().contains(L["t200_thanks"]))

        assertEquals(PlaytestLog.FINAL_ANSWERED, log.finalState)
        assertEquals(8, log.finalNps)
        assertEquals(0, log.finalLiked)
        assertEquals(2, log.finalIssue)
        assertEquals(20_411L, log.finalDay)
        val text = report(g)
        assertTrue(text, text.contains("[Tester survey]\nSkipped (2025-11-10)\n\n[Final survey]\n" +
            "Recommend (0-10): 8 (2025-11-19)\nLiked most: campaign\nBothered most: battles too long\n\n[Progress]"))

        // Zapis i ponowne wejście: bez banera i bez pytań.
        assertTrue(log.awaitWrites(5_000L))
        val json = JSONObject(File(dir, PlaytestLog.FILE_NAME).readText())
        assertEquals(1, json.getInt("final")); assertEquals(8, json.getInt("finalNps"))
        val again = game(dir, now = t0 + 11 * day, surveyAt = null)
        val m2 = menu(again, 4f)
        assertEquals(0, m2.reminderDay)
        assertEquals(0, m2.surveyStep)
        assertEquals(8, again.playtest!!.finalNps)
    }

    @Test
    fun skipAndBackStillOpenReport() {
        val g = game()
        val m = menu(g)
        m.onClick(m.reportReminder)
        m.onClick(m.surveySkip)
        assertEquals(0, m.surveyStep)
        assertEquals(1, shares)
        assertTrue("bez podziękowania po pominięciu", g.noticeTexts().isEmpty())
        assertEquals(PlaytestLog.FINAL_SKIPPED, g.playtest!!.finalState)
        assertTrue(report(g).contains("[Final survey]\nSkipped (2025-11-19)\n"))

        // Wstecz po poleceniu: ocena zostaje, reszta „skipped”, raport i tak się otwiera.
        val g2 = game()
        val m2 = menu(g2)
        m2.onClick(m2.reportReminder)
        m2.onClick(m2.finalNps[0])
        frames(g2, 0.1f)
        assertTrue(g2.wantsBack)
        assertTrue(m2.onBack())
        assertEquals(0, m2.surveyStep)
        assertEquals(2, shares)
        val log = g2.playtest!!
        assertEquals(PlaytestLog.FINAL_ANSWERED, log.finalState)
        assertEquals(0, log.finalNps)
        assertEquals(-1, log.finalLiked)
        val t2 = report(g2)
        assertTrue(t2, t2.contains("Recommend (0-10): 0 (2025-11-19)\nLiked most: skipped\nBothered most: skipped\n"))
    }

    @Test
    fun waitsAfterFirstSurveyDayAndOnlyTestBuilds() {
        // Pierwsza ankieta dziś (10. dzień): dziś żadnego banera, jutro jeden baner z ankietą.
        val dir = folder.newFolder()
        val g = game(dir, surveyAt = 9L)
        val m = menu(g, 4f)
        assertEquals(0, m.reminderDay)
        assertEquals(0, g.playtest!!.reminders)
        g.playtest!!.saveAsync(); assertTrue(g.playtest!!.awaitWrites(5_000L))
        val next = game(dir, now = t0 + 10 * day, surveyAt = null)
        val mn = menu(next, 3f)
        assertEquals(10, mn.reminderDay)
        assertTrue(mn.reminderFinal)

        // Wersja produkcyjna: nic.
        val prod = game(test = false)
        assertEquals(0, menu(prod, 4f).reminderDay)
        assertFalse(report(prod).contains("[Final survey]"))
        // Przed 10. dniem: zwykły baner z 3. dnia, bez ankiety końcowej.
        val third = menu(game(now = t0 + 2 * day), 3f)
        assertEquals(3, third.reminderDay)
        assertFalse(third.reminderFinal)
        third.onClick(third.reportReminder)
        assertEquals(0, third.surveyStep)
        assertEquals(1, shares)
    }

    @Test
    fun updateAfterDay10ReminderStillAsksOnce() {
        // Tester zaktualizował grę po banerze z 10. dnia (d48): ankieta końcowa raz, potem nic.
        val dir = folder.newFolder()
        val g = game(dir)
        g.playtest!!.reminderShown(PlaytestLog.REMINDER_DAY3 or PlaytestLog.REMINDER_DAY10)
        val m = menu(g)
        assertTrue(m.reminderFinal)
        // Baner zignorowany (wyjście z menu): nie wraca.
        g.playtest!!.saveAsync(); assertTrue(g.playtest!!.awaitWrites(5_000L))
        val again = game(dir, now = t0 + 12 * day, surveyAt = null)
        assertEquals(0, menu(again, 4f).reminderDay)
        assertEquals(PlaytestLog.FINAL_NONE, again.playtest!!.finalState)
        assertTrue(report(again).contains("[Final survey]\nNot asked yet\n"))
    }

    @Test
    fun logRoundTripAndBadValues() {
        val dir = folder.newFolder()
        val f = File(dir, PlaytestLog.FILE_NAME)
        val log = PlaytestLog(f).also { it.load(); it.startSession(t0, 0L) }
        assertFalse("bez ankiety plik jak dotąd", JSONObject(log.toJson()).has("final"))
        log.finalLikedChosen(1)
        assertEquals("pytania tylko po poleceniu", -1, log.finalLiked)
        log.finalRecommend(11, t0)
        assertEquals(PlaytestLog.FINAL_NONE, log.finalState)
        log.finalRecommend(10, t0)
        log.finalRecommend(3, t0)
        assertEquals("raz", 10, log.finalNps)
        log.finalSkipped(t0)
        assertEquals(PlaytestLog.FINAL_ANSWERED, log.finalState)
        log.finalLikedChosen(5); log.finalIssueChosen(4)
        log.reminderShown(PlaytestLog.REMINDER_FINAL)
        f.writeText(log.toJson())
        val back = PlaytestLog(f).also { it.load() }
        assertEquals(PlaytestLog.FINAL_ANSWERED, back.finalState)
        assertEquals(10, back.finalNps); assertEquals(5, back.finalLiked); assertEquals(4, back.finalIssue)
        assertEquals(20_402L, back.finalDay)
        assertEquals(PlaytestLog.REMINDER_FINAL, back.reminders)
        // Zepsute wartości: ocena spoza 0–10 nie udaje odpowiedzi, indeksy spoza listy znikają.
        f.writeText(JSONObject(back.toJson()).put("finalNps", 12).put("finalLiked", 9).put("reminders", 255).toString())
        val bad = PlaytestLog(f).also { it.load() }
        assertEquals(PlaytestLog.FINAL_SKIPPED, bad.finalState)
        assertEquals(-1, bad.finalNps); assertEquals(-1, bad.finalLiked); assertEquals(-1, bad.finalIssue)
        assertEquals(7, bad.reminders)
        assertFalse(bad.fresh)
    }

    @Test
    fun accessibilityOnlySurveyButtons() {
        val g = game()
        val m = menu(g)
        m.onClick(m.reportReminder)
        for (step in 3..5) {
            assertEquals(step, m.surveyStep)
            for (b in m.buttons) {
                val mine = b === m.surveySkip || b in m.finalNps || b in m.finalOptions
                if (b.visible) assertEquals("przycisk ${b.id}", mine, m.accessibilityEligibleButton(b))
                if (mine && b.visible) assertTrue("opis ${b.id}", m.accessibilityLabel(b).isNotBlank())
            }
            when (step) {
                3 -> {
                    assertTrue(m.accessibilityLabel(m.finalNps[0]).startsWith(L["tr205_title"]))
                    assertTrue(m.accessibilityLabel(m.finalNps[0]).contains(L["tr205_recommend"]))
                    assertEquals(L.f("tr205_nps_n", 7), m.accessibilityLabel(m.finalNps[7]))
                    m.onClick(m.finalNps[7])
                }
                4 -> {
                    assertTrue(m.accessibilityLabel(m.finalOptions[0]).startsWith(L["tr205_liked"]))
                    m.onClick(m.finalOptions[3])
                }
                else -> assertTrue(m.accessibilityLabel(m.finalOptions[0]).startsWith(L["t200_question"]))
            }
        }
    }

    @Test
    fun textsInAllLanguages() {
        val keys = listOf("tr205_banner", "tr205_title", "tr205_recommend", "tr205_scale", "tr205_liked",
            "tr205_issue_easy", "tr205_issue_slow") + MenuScreenKeys.liked
        for (code in L.CODES) {
            L.setCode(code)
            for (k in keys) assertTrue("$code $k", L[k].isNotBlank() && L[k] != k && !L[k].contains("%"))
            assertTrue("$code: ${L["tr205_banner"]}", L["tr205_banner"].contains("10"))
            val n = L.f("tr205_nps_n", 7)
            assertTrue("$code: $n", n.contains("7") && n.contains("10") && !n.contains("%"))
        }
    }

    private object MenuScreenKeys {
        val liked = listOf("tr205_like_campaign", "tr205_like_battles", "tr205_like_eras", "tr205_like_daily",
            "tr205_like_looks", "tr205_like_none")
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
            // Baner: w ekranie, napis nie mniejszy niż 10 sp.
            run {
                val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                g.frame(rec, 1f / 30f)
                val r = m.reportReminder.r
                if (!m.reportReminder.visible) problems.append("${dev.name} $lang: brak banera\n")
                val t = rec.texts.lastOrNull { it.s == L["tr205_banner"] }
                if (t == null) problems.append("${dev.name} $lang: brak napisu banera\n")
                else {
                    val sp = t.sizePx / dp / g.ui.textScale
                    if (sp < 10f - 0.05f) problems.append("${dev.name} $lang: baner ${"%.1f".format(sp)} sp\n")
                    if (t.r.left < r.left || t.r.right > r.right) problems.append("${dev.name} $lang: napis poza banerem\n")
                }
            }
            m.onClick(m.reportReminder)
            for (step in 3..5) {
                val where = "${dev.name} $lang ${(scale * 100).toInt()}% krok $step"
                val rec = Recorder(Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888))
                g.frame(rec, 1f / 30f)
                val p = m.surveyPanel
                if (p.top < g.insetT - 0.5f || p.bottom > g.height + 0.5f || p.left < 0f || p.right > g.width)
                    problems.append("$where: panel poza ekranem\n")
                val shown = ArrayList<Button>()
                if (step == 3) shown.addAll(m.finalNps) else shown.addAll(m.finalOptions)
                shown.add(m.surveySkip)
                for ((i, b) in shown.withIndex()) {
                    val r = b.r
                    if (!p.contains(r)) problems.append("$where: '${b.label}' poza panelem\n")
                    if (r.height() < 48f * dp - 0.5f || r.width() < 48f * dp - 0.5f)
                        problems.append("$where: '${b.label}' mniejszy niż 48 dp\n")
                    for (j in 0 until i) if (RectF.intersects(r, shown[j].r))
                        problems.append("$where: '${b.label}' nachodzi na '${shown[j].label}'\n")
                    val t = rec.texts.lastOrNull { it.s == b.label && r.contains(it.r.centerX(), it.r.centerY()) }
                    if (t == null) { problems.append("$where: brak napisu '${b.label}'\n"); continue }
                    val sp = t.sizePx / dp / g.ui.textScale
                    if (sp < 10f - 0.05f) problems.append("$where: '${b.label}' ${"%.1f".format(sp)} sp\n")
                    if (t.r.left < r.left || t.r.right > r.right) problems.append("$where: '${b.label}' wychodzi poza przycisk\n")
                }
                val title = L[when (step) { 3 -> "tr205_title"; 4 -> "tr205_liked"; else -> "t200_question" }]
                val start = rec.texts.indexOfFirst { it.s == title }
                if (start < 0) problems.append("$where: brak tytułu\n")
                for (t in rec.texts.subList(start.coerceAtLeast(0), rec.texts.size)) {
                    if (!p.contains(t.r.centerX(), t.r.centerY())) continue
                    if (shown.any { it.label == t.s }) continue
                    val sp = t.sizePx / dp / g.ui.textScale
                    if (sp < 10f - 0.05f) problems.append("$where: '${t.s}' ${"%.1f".format(sp)} sp\n")
                    if (t.r.left < p.left || t.r.right > p.right) problems.append("$where: '${t.s}' poza panelem\n")
                    for (b in shown) if (RectF.intersects(t.r, b.r)) problems.append("$where: '${t.s}' na przycisku '${b.label}'\n")
                }
                if (step == 3) m.onClick(m.finalNps[9]) else if (step == 4) m.onClick(m.finalOptions[1])
            }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }
}
