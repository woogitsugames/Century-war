package com.centurywar

import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** R10-04 L1: pierwszy ekran powstaje, zanim skończy się odczyt śladu ANR. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupCrashOrderTest {
    private val original = MainActivity.exitReasonCollector

    @After fun restore() { MainActivity.exitReasonCollector = original }

    @Test fun firstScreenDoesNotWaitForExitReasons() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        MainActivity.exitReasonCollector = {
            started.countDown()
            release.await(20, TimeUnit.SECONDS)
            finished.set(true)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!activity.gameReady && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Gra uruchomiona", activity.gameReady)
        assertTrue("Odczyt startuje po pierwszym ekranie", started.await(20, TimeUnit.SECONDS))
        assertFalse("Pierwszy ekran nie czekał na odczyt", finished.get())
        assertFalse(activity.exitReasonsCollected)
        release.countDown()
        while (!activity.exitReasonsCollected && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(activity.exitReasonsCollected)
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}
