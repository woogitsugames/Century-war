package com.centurywar

import android.view.ViewGroup
import com.centurywar.engine.GameView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regresja: Game wywołuje Host.applyRenderScale jeszcze przed powstaniem GameView. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseStartupTest {

    @Test
    fun activityStartsWithoutUsingGameViewBeforeInitialization() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            val activity = controller.create().get()
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(content.getChildAt(0) is GameView)
        } finally {
            controller.destroy()
        }
    }
}
