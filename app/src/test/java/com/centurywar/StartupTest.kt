package com.centurywar

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pierwsze uruchomienie: Application + MainActivity przechodzą cały cykl startu bez wyjątku. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupTest {
    @Test fun coldStartDoesNotCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.pause().stop().destroy()
    }
}
