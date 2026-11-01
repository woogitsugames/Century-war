package com.centurywar.screens

import android.graphics.RectF
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HintFramePlacementTest {
    /** Docelowy panel i wskaźnik mają trzymać się ekranu, nie zasłaniać wskazanej akcji. */
    @Test
    fun panelNeverIntersectsFocusOnPhoneAndFold() {
        val screens = arrayOf(intArrayOf(1600, 720), intArrayOf(1968, 2184))
        val bubble = RectF()
        val pointer = RectF()
        for (screen in screens) {
            val w = screen[0].toFloat()
            val h = screen[1].toFloat()
            val safeL = 8f
            val safeT = 8f
            val safeR = w - 8f
            val safeB = h - 8f
            val targets = arrayOf(
                RectF(15f, 20f, 180f, 75f),
                RectF(w - 190f, 20f, w - 15f, 75f),
                RectF(0f, h * 0.15f, w, h * 0.79f),
                RectF(30f, h * 0.18f, w - 30f, h * 0.31f),
                RectF(w * 0.22f, h * 0.28f, w * 0.48f, h * 0.72f),
                RectF(w * 0.54f, h * 0.30f, w * 0.78f, h * 0.72f),
                RectF(w * 0.33f, h * 0.90f, w * 0.66f, h * 0.96f),
            )
            for (target in targets) {
                val width = min(490f, w - 16f)
                assertTrue("Brak miejsca dla celu $target na $" + "{screen[0]}x$" + "{screen[1]}",
                    HintFrame.place(target, safeL, safeT, safeR, safeB,
                        width, 42f, 6f, bubble, pointer))
                assertFalse("Panel przecina cel: $target, $bubble", RectF.intersects(bubble, target))
                assertTrue(bubble.left >= safeL && bubble.top >= safeT)
                assertTrue(bubble.right <= safeR && bubble.bottom <= safeB)
                assertTrue("Wskaźnik musi kończyć się na krawędzi celu",
                    pointer.right == target.left || pointer.right == target.right ||
                        pointer.bottom == target.top || pointer.bottom == target.bottom)
            }
        }
    }

    @Test
    fun wideMissionCardPrefersSpaceAboveIt() {
        val card = RectF(70f, 150f, 1530f, 330f)
        val bubble = RectF()
        val pointer = RectF()
        assertTrue(HintFrame.place(card, 8f, 8f, 1592f, 712f,
            560f, 65f, 8f, bubble, pointer))
        assertTrue(bubble.bottom <= card.top - 8f)
        assertFalse(RectF.intersects(bubble, card))
    }

    @Test
    fun impossibleLayoutDoesNotCoverTarget() {
        val bubble = RectF()
        val pointer = RectF()
        assertFalse(HintFrame.place(RectF(0f, 0f, 1600f, 720f),
            8f, 8f, 1592f, 712f, 400f, 42f, 6f, bubble, pointer))
        assertTrue(bubble.isEmpty)
    }
}
