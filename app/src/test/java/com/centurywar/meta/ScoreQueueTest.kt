package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreQueueTest {
    @Test fun overflowKeepsMostRecentTwenty() {
        val q = ScoreQueue()
        val now = 1_800_000_000_000L
        repeat(30) { i -> q.add(ScoreQueue.Entry(now + i, "$i"), now + i) }
        assertEquals(20, q.entries.size)
        assertEquals("10", q.entries.first().payload)
        assertEquals("29", q.entries.last().payload)
    }

    @Test fun oldAndFutureEntriesExpire() {
        val q = ScoreQueue()
        val now = 1_800_000_000_000L
        q.add(ScoreQueue.Entry(now - ScoreQueue.MAX_AGE_MS - 1L, "old"), now)
        assertTrue(q.entries.isEmpty())
        q.add(ScoreQueue.Entry(now + 300_001L, "future"), now)
        assertTrue(q.entries.isEmpty())
        q.add(ScoreQueue.Entry(now - ScoreQueue.MAX_AGE_MS, "recent"), now)
        assertEquals(1, q.entries.size)
        q.prune(now + 1)
        assertTrue(q.entries.isEmpty())
        q.clear()
        assertTrue(q.entries.isEmpty())
    }
}
