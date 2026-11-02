package com.centurywar.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regresja A156-22: błędy AudioTrack i kończący się poprzedni worker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MusicLifecycle160Test {
    private fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test fun negativeAudioTrackBufferAllowsNextResumeToRetry() {
        val attempts = AtomicInteger()
        val music = Music(MusicCatalog.fromAssets(null), null, null) { _, _, _ ->
            attempts.incrementAndGet()
            -1
        }
        try {
            music.resume()
            assertTrue(awaitCondition { attempts.get() == 1 && !music.isPlaying })
            music.resume()
            assertTrue(awaitCondition { attempts.get() == 2 && !music.isPlaying })
            assertEquals(2, attempts.get())
        } finally {
            music.release()
        }
    }

    @Test fun oldWorkerCannotClearNewWorkerAfterPauseAndResume() {
        val attempts = AtomicInteger()
        val enteredFirst = CountDownLatch(1)
        val enteredSecond = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        val music = Music(MusicCatalog.fromAssets(null), null, null) { _, _, _ ->
            when (attempts.incrementAndGet()) {
                1 -> {
                    enteredFirst.countDown()
                    var done = false
                    while (!done) {
                        try { done = releaseFirst.await(3, TimeUnit.SECONDS) }
                        catch (_: InterruptedException) { /* Stary worker czeka na test. */ }
                    }
                }
                2 -> {
                    enteredSecond.countDown()
                    releaseSecond.await(3, TimeUnit.SECONDS)
                }
            }
            -1
        }
        try {
            music.resume()
            assertTrue(enteredFirst.await(3, TimeUnit.SECONDS))
            music.pause()
            music.resume()
            assertTrue(enteredSecond.await(3, TimeUnit.SECONDS))
            releaseFirst.countDown()
            // Kończy się stary worker; nowy wciąż czeka w getMinBufferSize.
            assertTrue(music.isPlaying)
            releaseSecond.countDown()
            assertTrue(awaitCondition { !music.isPlaying })
            assertFalse(music.isPlaying)
        } finally {
            releaseFirst.countDown()
            releaseSecond.countDown()
            music.release()
        }
    }
}
