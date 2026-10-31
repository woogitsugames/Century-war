package com.centurywar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Eksport z tych samych VectorDrawable, ktore wykorzystuje launcher. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppIconScreenshotTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val storeDir = File("build/store")
    private val shotDir = File("build/screenshots/icons")

    private fun drawable(id: Int): Drawable = requireNotNull(context.getDrawable(id))

    private fun save(bitmap: Bitmap, name: String) {
        for (dir in arrayOf(storeDir, shotDir)) {
            dir.mkdirs()
            File(dir, name).outputStream().use { out ->
                assertTrue("Nie mozna zapisac $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
        }
    }

    /** Podglad launchera po przycieciu 108dp do widocznych 72dp, z okragla maska. */
    private fun adaptive(size: Int, playStore: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val checkpoint = canvas.save()
        if (!playStore) {
            val clip = Path().apply {
                addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            }
            canvas.clipPath(clip)
        }
        // Launcher kadruje warstwy 108 dp do widocznych 72 dp (18..90).
        // Google Play korzysta z tego samego kadru, ale bez maski kola.
        canvas.scale(size / 72f, size / 72f)
        canvas.translate(-18f, -18f)
        for (id in intArrayOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            drawable(id).apply { setBounds(0, 0, 108, 108); draw(canvas) }
        }
        canvas.restoreToCount(checkpoint)
        return bitmap
    }

    @Test fun launcherSizesAndStoreExport() {
        for (size in intArrayOf(48, 96, 192)) {
            val bitmap = adaptive(size, false)
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
            assertTrue(Color.alpha(bitmap.getPixel(size / 2, size / 2)) > 0)
            save(bitmap, "icon-$size.png")
            bitmap.recycle()
        }
        val store = adaptive(512, true)
        // Wartosci pobrane z pol tarczy: niebieski po lewej, czerwony po prawej.
        val blue = store.getPixel(156, 256)
        val red = store.getPixel(355, 256)
        assertTrue("Lewa polowa nie jest niebieska", Color.blue(blue) > Color.red(blue))
        assertTrue("Prawa polowa nie jest czerwona", Color.red(red) > Color.blue(red))
        assertEquals(255, Color.alpha(store.getPixel(0, 0)))
        save(store, "icon-512.png")
        store.recycle()
    }

    private fun renderSymbol(id: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        drawable(id).apply { setBounds(0, 0, 192, 192); draw(Canvas(bitmap)) }
        return bitmap
    }

    @Test fun themedAndNotificationIconsAreMonochrome() {
        for ((id, name) in arrayOf(
            R.drawable.ic_launcher_monochrome to "icon-monochrome-192.png",
            R.drawable.ic_daily_notification to "icon-notification-192.png",
        )) {
            val bitmap = renderSymbol(id)
            var opaque = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    opaque++
                    assertTrue("Symbol musi byc jednobarwny",
                        Color.red(pixel) == Color.green(pixel) && Color.green(pixel) == Color.blue(pixel))
                }
            }
            assertTrue("Symbol $name jest pusty", opaque > 1000)
            assertTrue("Symbol $name jest calym kwadratem", opaque < 192 * 192 / 2)
            save(bitmap, name)
            bitmap.recycle()
        }
    }

    @Test @Config(sdk = [29])
    fun legacyThemeDisplaysBrandedWindow() {
        val theme = ContextThemeWrapper(context, R.style.Theme_CenturyWar)
        val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        try {
            assertEquals(R.drawable.bg_launch, attributes.getResourceId(0, 0))
        } finally {
            attributes.recycle()
        }
        for ((w, h, filename) in arrayOf(
            Triple(720, 1280, "splash-phone720.png"),
            Triple(1968, 2184, "splash-fold.png"),
        )) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawable(R.drawable.bg_launch).apply { setBounds(0, 0, w, h); draw(Canvas(bitmap)) }
            assertTrue(Color.alpha(bitmap.getPixel(w / 2, h / 2)) > 0)
            assertTrue("Brak logo na splash", bitmap.getPixel(w / 2, h / 2) != bitmap.getPixel(0, 0))
            save(bitmap, filename)
            bitmap.recycle()
        }
    }

    @Test fun android12UsesSystemSplashWithMatchingArt() {
        val theme = ContextThemeWrapper(context, R.style.Theme_CenturyWar)
        val attrs = theme.obtainStyledAttributes(intArrayOf(
            android.R.attr.windowSplashScreenAnimatedIcon,
            android.R.attr.windowSplashScreenBackground,
        ))
        try {
            assertEquals(R.drawable.ic_launcher_foreground, attrs.getResourceId(0, 0))
            assertEquals(R.color.cw_splash_background, attrs.getResourceId(1, 0))
        } finally {
            attrs.recycle()
        }
    }
}
