package com.centurywar.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicVarietyTest {
    @Test fun intensityDistinguishesCalmClashAndBaseDanger() {
        val calm = BattleMusicIntensity.level(250f, 2350f, 190f, 2800f, 1f)
        val clash = BattleMusicIntensity.level(1120f, 1300f, 190f, 2800f, 1f)
        val danger = BattleMusicIntensity.level(250f, 250f, 190f, 2800f, 0.5f)
        assertTrue("Spokój powinien mieć niski poziom", calm < 0.25f)
        assertTrue("Starcie powinno uruchamiać warstwę walki", clash >= 0.55f)
        assertTrue("Zagrożenie bazy powinno przeważać nad starciem", danger > clash)
        assertEquals(0.30f, BattleMusicIntensity.level(
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 190f, 2800f, 0f
        ), 0.0001f)
        assertEquals(0f, BattleMusicIntensity.level(
            100f, 300f, 190f, 0f, 1f
        ), 0f)
    }

    @Test fun nineErasHaveOwnAudibleArrangements() {
        val buffer = FloatArray(4096)
        var previous: FloatArray? = null
        for (era in 0..8) {
            val synth = MusicSynth(103)
            synth.setEra(era)
            synth.skipFade()
            synth.setIntensity(0.6f)
            synth.render(buffer)
            previous?.let { assertFalse("Motyw epoki $era jest identyczny", it.contentEquals(buffer)) }
            previous = buffer.copyOf()
        }
    }

    @Test fun deterministicSeedAndChunkSizes() {
        val first = MusicSynth(103)
        val second = MusicSynth(103)
        first.setEra(8); second.setEra(8)
        first.skipFade(); second.skipFade()
        first.setIntensity(0.97f); second.setIntensity(0.97f)
        val large = FloatArray(4096)
        val small = FloatArray(1024)
        repeat(20) {
            first.render(large)
            for (chunk in 0..3) {
                second.render(small)
                for (i in small.indices) assertEquals(large[chunk * small.size + i], small[i], 0f)
            }
        }
    }

    @Test fun sixBitPhraseMarkersPreventShortLiteralLoop() {
        val synth = MusicSynth(103)
        for (era in MusicSynth.MENU..8) {
            val seconds = synth.shortestFormRepeatSeconds(era)
            assertTrue("Epoka $era: powrót frazy po ${seconds}s", seconds >= 120f)
        }
    }

    @Test fun pcmStaysFiniteAndWithinHeadroomAtAllTensionLevels() {
        val buffer = FloatArray(2048)
        for (era in MusicSynth.MENU..8) {
            for (tension in floatArrayOf(0f, 0.55f, 1f)) {
                val synth = MusicSynth(103)
                synth.setEra(era)
                synth.skipFade()
                synth.setIntensity(tension)
                repeat(24) {
                    synth.render(buffer)
                    for (sample in buffer) {
                        assertTrue("Niepoprawna próbka $era: $sample", sample.isFinite())
                        assertTrue("Przesterowanie $era: $sample", sample in -1f..1f)
                    }
                }
            }
        }
    }

    @Test fun audioThreadRenderDoesNotAllocatePerPcmBlock() {
        // Klasy JVM pobierane refleksją: android.jar nie eksportuje java.lang.management.
        val management = Class.forName("java.lang.management.ManagementFactory")
        val bean = management.getMethod("getThreadMXBean").invoke(null)
        val metric = Class.forName("com.sun.management.ThreadMXBean")
        assertTrue("JVM nie obsługuje licznika alokacji",
            metric.getMethod("isThreadAllocatedMemorySupported").invoke(bean) as Boolean)
        if (!(metric.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean) as Boolean)) {
            metric.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType)
                .invoke(bean, true)
        }
        val getBytes = metric.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val id = Thread.currentThread().id
        fun count(): Long = (getBytes.invoke(bean, id) as Number).toLong()

        val synth = MusicSynth(103)
        synth.setEra(8)
        synth.setIntensity(1f)
        synth.skipFade()
        val buffer = ShortArray(2048)
        repeat(400) { synth.renderPcm(buffer) } // rozgrzanie JIT i tablic
        val overheadStart = count()
        val overhead = count() - overheadStart
        val before = count()
        repeat(200) { synth.renderPcm(buffer) }
        val allocated = count() - before - overhead
        assertTrue("Render PCM alokuje pamięć: $allocated B w 200 blokach", allocated <= 2048L)
    }
}
