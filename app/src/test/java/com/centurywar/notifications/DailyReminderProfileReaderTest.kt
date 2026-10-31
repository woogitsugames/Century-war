package com.centurywar.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyReminderProfileReaderTest {
    private fun file(): File = File(Files.createTempDirectory("reminder").toFile(), "profile.json")

    @Test fun ignoresUnneededFieldsAndClampsHour() {
        val f = file()
        f.writeText("""{"notifications":false,"notifyHour":48,"wins":3,"lastRankedDaily":12,"lang":"fr","boards":{"daily_12":[{"n":"x","s":1,"d":12}]}}""")
        val p = DailyReminderProfileReader.read(f)!!
        assertFalse(p.notifications)
        assertEquals(23, p.notifyHour)
        assertEquals(3, p.wins)
        assertEquals(12L, p.lastRankedDaily)
        assertEquals("fr", p.lang)
    }

    @Test fun missingMainFileFallsBackToTempFile() {
        val f = file()
        File(f.parentFile, f.name + ".tmp").writeText("""{"wins":2,"notifyHour":9}""")
        val p = DailyReminderProfileReader.read(f)
        assertNotNull(p)
        assertEquals(9, p!!.notifyHour)
        assertEquals(2, p.wins)
    }

    @Test fun malformedOrMissingFileDoesNotSchedule() {
        val f = file()
        assertNull(DailyReminderProfileReader.read(f))
        f.writeText("{incomplete")
        assertNull(DailyReminderProfileReader.read(f))
    }
}
