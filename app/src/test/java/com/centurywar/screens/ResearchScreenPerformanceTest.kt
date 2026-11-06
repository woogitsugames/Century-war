package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Method
import java.util.Locale

/** Pomiary wątku JVM/Robolectric, bez pomiaru GPU ani telefonu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ResearchScreenPerformanceTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private class Meter(private val bean: Any, private val getter: Method) {
        fun bytes(): Long = getter.invoke(bean, Thread.currentThread().id) as Long
    }
    private fun meter(): Meter {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("Wymagany ThreadMXBean", api != null && api.isInstance(bean) &&
            api.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true)
        return Meter(bean, api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE))
    }
    @Test fun idleAndScrollingBudget() {
        val meter = meter()
        val errors = StringBuilder()
        for (quality in intArrayOf(Quality.LOW, Quality.HIGH)) for (scrolling in listOf(false, true)) {
            val file = File.createTempFile("research201", ".json").apply { delete() }
            val profile = Profile(file).apply { tutorialDone = true; hintsEnabled = false; hintsSeen = Profile.HINT_VALID_MASK }
            val g = Game(host, profile, quality).apply {
                density = 2f; resize(1600, 720, 1600, 720); finishScreenTransition()
            }
            val screen = ResearchScreen(g)
            g.setScreen(screen)
            val canvas = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
            var frame = 0
            fun step() {
                if (scrolling) screen.onDrag(0f, if (frame++ % 160 < 80) -4f else 4f)
                screen.update(1f / 60f)
                screen.render(canvas)
            }
            repeat(300) { step() }
            val times = LongArray(600)
            val before = meter.bytes()
            for (i in times.indices) {
                val start = System.nanoTime(); step(); times[i] = System.nanoTime() - start
            }
            val allocated = meter.bytes() - before
            val avg = times.sum() / 600_000_000.0
            times.sort()
            println("PERF201 quality=$quality scroll=$scrolling bytes=$allocated avgMs=${String.format(Locale.ROOT, "%.3f", avg)} p95Ms=${String.format(Locale.ROOT, "%.3f", times[569] / 1_000_000.0)}")
            if (allocated >= 16384) errors.append("quality=$quality scroll=$scrolling: $allocated B; ")
        }
        assertTrue(errors.toString(), errors.isEmpty())
    }
}
