package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxMixTest {
    @Test fun crowdedBattleStaysBelowSoftCeilingOnBothChannels() {
        val mix = SfxMix(10, 37)
        for (i in 0 until mix.capacity) {
            val slot = mix.reserve(10_000L, 700L, 0.38f, if (i % 2 == 0) -0.6f else 0.6f, 1)
            assertEquals(i, slot)
            mix.attach(slot, i + 1)
            var left = 0f
            var right = 0f
            for (j in 0 until mix.capacity) {
                left += mix.left(j) * SfxMix.SAMPLE_PEAK
                right += mix.right(j) * SfxMix.SAMPLE_PEAK
            }
            assertTrue("Przester kanału L, $i głosów: $left", left <= SfxMix.CEILING + 0.0001f)
            assertTrue("Przester kanału R, $i głosów: $right", right <= SfxMix.CEILING + 0.0001f)
        }
    }

    @Test fun highPriorityCanReplaceQuietVoiceButNotConversely() {
        val mix = SfxMix(2, 73)
        assertEquals(0, mix.reserve(1000, 600, 0.32f, 0f, 3))
        mix.attach(0, 101)
        assertEquals(1, mix.reserve(1000, 600, 0.32f, 0f, 2))
        mix.attach(1, 102)
        assertEquals(-1, mix.reserve(1010, 600, 0.32f, 0f, 1))
        assertEquals(1, mix.reserve(1010, 600, 0.32f, 0f, 3))
        assertEquals(102, mix.displacedStream)
        mix.attach(1, 103)
        assertEquals(0, mix.reserve(2000, 600, 0.32f, 0f, 1))
        assertEquals(0, mix.displacedStream)
    }

    @Test fun stereoPanAndPitchVariationAreBounded() {
        val mix = SfxMix(2, 13)
        val slot = mix.reserve(1000, 200, 0.25f, 1f, 2)
        assertEquals(0f, mix.left(slot), 0f)
        assertEquals(0.25f, mix.right(slot), 0.0001f)
        mix.cancel(slot)
        assertEquals(0f, mix.right(slot), 0f)
        var first = 0f
        var changed = false
        for (i in 0 until 200) {
            val pitch = mix.pitchJitter()
            assertTrue(pitch in 0.984f..1.016f)
            if (i == 0) first = pitch else if (pitch != first) changed = true
        }
        assertTrue(changed)
    }

    @Test fun cooldownCoversSoundFamiliesWithoutMutingRewards() {
        val gate = SfxRateGate(SoundSynth.COUNT)
        assertTrue(gate.allowed(SoundSynth.HIT_WOOD, 1000, 40))
        gate.played(SoundSynth.HIT_WOOD, 1000)
        assertFalse(gate.allowed(SoundSynth.HIT_WOOD, 1010, 40))
        assertFalse(gate.allowed(SoundSynth.HIT_STEEL, 1010, 0))
        assertTrue(gate.allowed(SoundSynth.HIT_STEEL, 1022, 0))
        assertTrue(gate.allowed(SoundSynth.REWARD, 1010, 0))
        assertTrue(gate.allowed(SoundSynth.SHOT_RIFLE, 1010, 0))
        gate.played(SoundSynth.SHOT_RIFLE, 1010)
        assertFalse(gate.allowed(SoundSynth.SHOT_BOW, 1020, 0))
    }

    @Test fun hotPathDoesNotAllocatePerSound() {
        // Dostęp przez refleksję: android.jar nie udostępnia com.sun.management.
        val management = Class.forName("java.lang.management.ManagementFactory")
        val bean = management.getMethod("getThreadMXBean").invoke(null)
        val threadType = Class.forName("com.sun.management.ThreadMXBean")
        if (!threadType.isInstance(bean)) return
        if (threadType.getMethod("isThreadAllocatedMemorySupported").invoke(bean) != true) return
        threadType.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType)
            .invoke(bean, true)
        val counter = threadType.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val threadId = Thread.currentThread().id
        val mix = SfxMix(10, 13)
        val gate = SfxRateGate(SoundSynth.COUNT)
        for (i in 0 until 60_000) {
            val slot = mix.reserve(i.toLong() + 1000, 1, 0.32f, 0.2f, 2)
            mix.attach(slot, 19)
            mix.left(slot); mix.right(slot); mix.pitchJitter()
            gate.allowed(SoundSynth.HIT_STEEL, i.toLong(), 0)
            gate.played(SoundSynth.HIT_STEEL, i.toLong())
        }
        val before = counter.invoke(bean, threadId) as Long
        for (i in 0 until 100_000) {
            val now = i.toLong() + 100_000
            val slot = mix.reserve(now, 1, 0.32f, 0.2f, 2)
            mix.attach(slot, 19)
            mix.left(slot); mix.right(slot); mix.pitchJitter()
            gate.allowed(SoundSynth.HIT_STEEL, now, 0)
            gate.played(SoundSynth.HIT_STEEL, now)
        }
        val allocated = (counter.invoke(bean, threadId) as Long) - before
        assertTrue("Alokacje w gorącej ścieżce: $allocated B", allocated <= 8192)
    }
}
