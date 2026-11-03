package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pętla bez przerwy z łańcucha okrążeń (setNextMediaPlayer), na atrapach okrążeń. */
class ChainedLoopTest {
    /** Okrążenie o długości [lengthMs]; czas płynie przez [advance]. */
    private class Seg(val id: Int, val lengthMs: Int = 60_000, val failChain: Boolean = false) : LoopSegment {
        var pos = 0
        var playing = false
        var released = false
        var level = -1f
        var next: Seg? = null
        var loopInPlace = false
        var restarts = 0
        override fun start() { check(!released); playing = true }
        override fun setVolume(value: Float) { check(!released) { "głośność po zwolnieniu $id" }; level = value }
        override val isPlaying: Boolean get() = playing
        override val remainingMs: Int get() = lengthMs - pos
        override fun chain(next: LoopSegment?) {
            check(!released)
            if (failChain) throw IllegalStateException("brak setNextMediaPlayer")
            this.next = next as Seg?
        }
        override fun loopInPlace() { loopInPlace = true }
        override fun restart() { pos = 0; playing = true; restarts++ }
        override fun release() { check(!released) { "podwójne zwolnienie $id" }; released = true; playing = false }

        /** Upływ czasu; na końcu okrążenia system sam startuje następnika (jak MediaPlayerService). */
        fun advance(ms: Int, autoStartNext: Boolean = true) {
            if (!playing) return
            pos += ms
            if (pos >= lengthMs) {
                if (loopInPlace) { pos -= lengthMs; return }
                pos = lengthMs
                playing = false
                if (autoStartNext) next?.start()
            }
        }
    }

    private class Opener(val failChainFrom: Int = Int.MAX_VALUE, val failOpen: Boolean = false) {
        val all = ArrayList<Seg>()
        fun open(): Seg? {
            if (failOpen && all.isNotEmpty()) return null
            val s = Seg(all.size, failChain = all.size >= failChainFrom)
            all += s
            return s
        }
        val live: List<Seg> get() = all.filter { !it.released }
    }

    private fun play(loop: ChainedLoop, o: Opener, seconds: Int, autoStartNext: Boolean = true) {
        repeat(seconds * 25) {
            o.live.filter { it.playing }.forEach { it.advance(40, autoStartNext) }
            loop.maintain()
        }
    }

    @Test fun chainsLapsWithoutGapAndKeepsAtMostTwoPlayers() {
        val o = Opener()
        val loop = ChainedLoop(o.open()!!, 12_000) { o.open() }
        loop.setVolume(0.4f)
        loop.start()
        play(loop, o, 47)
        assertEquals("następnik dopiero pod koniec okrążenia", 1, o.all.size)
        play(loop, o, 2)
        assertEquals(2, o.all.size)
        val first = o.all[0]
        val second = o.all[1]
        assertSame("połączone przez setNextMediaPlayer", second, first.next)
        assertEquals("następnik ma bieżącą głośność", 0.4f, second.level, 0f)
        loop.setVolume(0.2f)
        assertEquals(0.2f, second.level, 0f)
        // 5 minut gry: kolejne okrążenia, zawsze najwyżej dwa odtwarzacze.
        var maxLive = 0
        repeat(300) { play(loop, o, 1); maxLive = maxOf(maxLive, o.live.size) }
        assertEquals(2, maxLive)
        assertTrue("okrążenia: ${loop.laps}", loop.laps >= 5)
        assertFalse(loop.fallback)
        assertTrue("bez seekTo(0)", o.all.all { it.restarts == 0 })
        assertEquals("gra dokładnie jedno okrążenie", 1, o.live.count { it.playing })
        loop.release()
        assertTrue(o.all.all { it.released })
        loop.release()
    }

    @Test fun startsNextItselfWhenSystemDidNot() {
        val o = Opener()
        val loop = ChainedLoop(o.open()!!, 12_000) { o.open() }
        loop.start()
        play(loop, o, 61, autoStartNext = false)
        assertTrue(o.all[0].released)
        assertTrue("następnik uruchomiony ręcznie", o.all[1].playing)
        assertEquals(1, loop.laps)
    }

    @Test fun chainFailureFallsBackToLooping() {
        val o = Opener(failChainFrom = 0)
        val loop = ChainedLoop(o.open()!!, 12_000) { o.open() }
        loop.start()
        play(loop, o, 120)
        assertTrue(loop.fallback)
        assertTrue(o.all[0].loopInPlace)
        assertTrue("gra dalej: zwykła pętla", o.all[0].playing)
        assertTrue("nieudany następnik zwolniony", o.all.drop(1).all { it.released })
        assertEquals(1, o.live.size)
    }

    @Test fun openFailureFallsBackToLooping() {
        val o = Opener(failOpen = true)
        val loop = ChainedLoop(o.open()!!, 12_000) { o.open() }
        loop.start()
        play(loop, o, 130)
        assertTrue(loop.fallback)
        assertTrue(o.all[0].playing)
        assertEquals(1, o.all.size)
    }

    @Test fun notStartedDoesNothing() {
        val o = Opener()
        val loop = ChainedLoop(o.open()!!, 12_000) { o.open() }
        repeat(100) { loop.maintain() }
        assertEquals(1, o.all.size)
        assertNull(o.all[0].next)
        assertTrue(loop.isPlaying)
        loop.release()
        assertFalse(loop.isPlaying)
    }
}
