package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Icons
import com.centurywar.engine.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Znakowe rysowanie złota i zegara musi dawać identyczne piksele. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HudDigits152RenderTest {
    @Test fun goldBadgeIsPixelIdenticalToStringBadge() {
        val ui = Ui().apply { dp = 2f }
        val old = Bitmap.createBitmap(640, 120, Bitmap.Config.ARGB_8888)
        val now = Bitmap.createBitmap(640, 120, Bitmap.Config.ARGB_8888)
        val digits = HudDigits152()
        try {
            for (n in intArrayOf(0, 9, 12345, 1_000_000_000, Int.MAX_VALUE)) {
                old.eraseColor(0)
                now.eraseColor(0)
                digits.setNumber(n)
                val widthOld = ui.badge(Canvas(old), 18f, 50f, Icons.COIN,
                    n.toString(), 15f, Ui.GOLD)
                val widthNew = ui.badge(Canvas(now), 18f, 50f, Icons.COIN,
                    digits.chars, digits.length, 15f, Ui.GOLD)
                assertEquals("Szerokość dla $n", widthOld, widthNew, 0f)
                assertTrue("Rysunek złota $n musi być identyczny", old.sameAs(now))
            }
        } finally {
            old.recycle()
            now.recycle()
        }
    }
}
