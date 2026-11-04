package com.centurywar.notifications

import com.centurywar.engine.L
import com.centurywar.game.Modifier
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** R10-07: przypomnienie podaje cel z „Następnego celu” w języku profilu, z tekstem domyślnym. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyReminderGoalTest {
    private val now = 1_793_461_200_000L
    private fun file(): File = File(Files.createTempDirectory("reminder-goal").toFile(), "profile.json")

    private fun write(f: File, tutorial: Boolean, medals: Int, lang: String) {
        val p = Profile(File(f.parentFile, "source.json"))
        p.tutorialDone = tutorial; p.stars[0] = 3; p.medals = medals; p.lang = lang
        f.writeText(p.toJson())
    }

    @Test fun goalInProfileLanguageWithoutChangingGameLanguage() {
        val old = L.lang
        try {
            L.setCode("pl")
            val f = file()
            write(f, tutorial = true, medals = 10_000, lang = "de")
            val goal = DailyReminderGoal.text(f, "de", now)
            assertTrue(goal, goal!!.startsWith("Forschung bezahlbar: "))
            assertEquals(L.indexOf("pl"), L.lang)
            val short = DailyReminderTexts.shortBody(setOf(Modifier.FAST_UNITS), "de", goal)
            assertEquals("Dein Ziel: $goal", short)
            val long = DailyReminderTexts.longBody(setOf(Modifier.FAST_UNITS), "de", goal)
            assertEquals(short + "\n" + DailyReminderTexts.body(setOf(Modifier.FAST_UNITS), "de"), long)
            // Plik profilu nie został zmieniony ani nie powstały nowe pliki obok.
            assertEquals(setOf("profile.json"), f.parentFile.list()!!.toSet())
        } finally { L.lang = old }
    }

    @Test fun safeDefaultWithoutGoalTutorialOrValidFile() {
        val f = file()
        write(f, tutorial = false, medals = 10_000, lang = "en")
        assertNull(DailyReminderGoal.text(f, "en", now))
        f.writeText("{broken")
        assertNull(DailyReminderGoal.text(f, "en", now))
        assertNull(DailyReminderGoal.text(File(f.parentFile, "missing.json"), "en", now))
        val mods = setOf(Modifier.FAST_UNITS)
        assertEquals(DailyReminderTexts.body(mods, "en"), DailyReminderTexts.shortBody(mods, "en", null))
        assertEquals(DailyReminderTexts.body(mods, "en"), DailyReminderTexts.longBody(mods, "en", ""))
    }
}
