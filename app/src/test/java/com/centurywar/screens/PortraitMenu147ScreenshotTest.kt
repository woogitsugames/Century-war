package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zlecenie 147: geometria i zrzuty pionowego menu oraz ekranów wejściowych. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PortraitMenu147ScreenshotTest {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun game(width: Int, height: Int, density: Float): Game {
        val file = File.createTempFile("cw-menu147", ".json").apply { delete() }
        val profile = Profile(file).apply {
            lang = "pl"
            tutorialDone = true
            hintsSeen = Profile.HINT_VALID_MASK
        }
        return Game(host, profile, Quality.LOW).also {
            it.density = density
            it.resize(width, height, width, height)
            it.setInsets((8 * density).toInt(), (14 * density).toInt(),
                (12 * density).toInt(), (18 * density).toInt())
        }
    }

    private fun menuButtons(screen: MenuScreen) =
        screen.buttons.filter { it.id in 1..9 }.sortedBy { it.r.top }

    private fun checkMenu(screen: MenuScreen, g: Game, language: String) {
        val tiles = menuButtons(screen)
        val d = g.ui.dp
        assertTrue("Pionowe menu $language", screen.portraitMenu)
        assertEquals(9, tiles.size)
        assertTrue("Lista mieści się pomiędzy insetami", screen.portraitTop >= g.insetT)
        assertTrue(screen.portraitBottom <= g.height - g.insetB)
        assertTrue(screen.portraitBottom > screen.portraitTop)
        for (i in tiles.indices) {
            val b = tiles[i]
            assertTrue("$language: przycisk ${b.id} ma 48 dp szerokości",
                b.r.width() >= 48 * d)
            assertTrue("$language: przycisk ${b.id} ma 48 dp wysokości",
                b.r.height() >= 48 * d)
            assertTrue("$language: przycisk ${b.id} poza lewym insetem",
                b.r.left >= g.insetL)
            assertTrue("$language: przycisk ${b.id} poza prawym insetem",
                b.r.right <= g.width - g.insetR)
            if (i > 0) assertTrue("$language: kolizja kafelków",
                tiles[i - 1].r.bottom + 2 * d <= b.r.top)
            if (b.visible) {
                assertTrue("$language: widoczny kafelek poza obszarem listy",
                    b.r.bottom > screen.portraitTop && b.r.top < screen.portraitBottom)
            }
            // Ui.button: przy szerokim przycisku ikona zajmuje 0,88 wysokości,
            // po 8 dp zostaje po obu stronach. Sprawdzamy faktyczny rozmiar fit().
            val textWidth = b.r.width() - b.r.height() * 0.88f - 16 * d
            val textSize = g.ui.fit(b.label, 17f, textWidth)
            assertTrue("$language: ${b.label} ma tylko ${textSize * g.ui.textScale} sp",
                textSize * g.ui.textScale >= 12f)
            assertTrue("$language: napis ${b.label} wystaje z kafelka",
                g.ui.measure(b.label, textSize, true) <= textWidth + 1 * d)
        }
    }

    private fun shot(g: Game, name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 1f / 60f)
            File(dir, "$name.png").outputStream().use {
                assertTrue("$name: błąd zapisu PNG",
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bmp.recycle()
        }
    }

    @Test fun menuGeometryAllLanguagesAndScroll() {
        val oldLanguage = L.lang
        try {
            for ((tag, d) in linkedMapOf(
                "720x1280" to floatArrayOf(720f, 1280f, 2f),
                "1080x1600" to floatArrayOf(1080f, 1600f, 3f),
                "904x2316" to floatArrayOf(904f, 2316f, 2.7f),
            )) {
                val g = game(d[0].toInt(), d[1].toInt(), d[2])
                val menu = MenuScreen(g)
                g.setScreen(menu)
                for (lang in L.CODES) {
                    L.setCode(lang)
                    menu.layout()
                    menu.update(0f)
                    checkMenu(menu, g, "$tag/$lang")
                    menu.onDrag(0f, -100000f)
                    assertEquals("Dolna granica przewijania", menu.portraitMaxScroll,
                        menu.portraitScroll, 0.1f)
                    assertTrue("Kodeks dostępny po przewinięciu",
                        menu.buttons.first { it.id == 8 }.visible)
                    checkMenu(menu, g, "$tag/$lang (koniec)")
                    menu.onDrag(0f, 100000f)
                    assertEquals("Górna granica przewijania", 0f, menu.portraitScroll, 0.1f)
                }
                L.setCode("pl")
                menu.layout()
                shot(g, "menu147_${tag}_top")
                menu.onDrag(0f, -100000f)
                shot(g, "menu147_${tag}_bottom")
            }
        } finally {
            L.lang = oldLanguage
        }
    }

    @Test fun menuNavigationAfterSwipe() {
        val g = game(720, 1280, 2f)
        val menu = MenuScreen(g)
        g.setScreen(menu)
        assertTrue(menu.isDragScreen())
        menu.onDrag(0f, -100000f)
        val settings = menu.buttons.first { it.id == 7 }
        assertTrue(settings.visible)
        for (action in intArrayOf(TouchEvent.DOWN, TouchEvent.UP)) {
            menu.handle(TouchEvent().apply {
                this.action = action
                x = settings.r.centerX()
                y = settings.r.centerY()
                pointer = 0
            })
        }
        assertTrue("Ustawienia dostępne po przesunięciu menu", g.current is SettingsScreen)

        // Po zmianie orientacji ekran wraca do niezmodyfikowanej siatki poziomej.
        val horizontal = game(1600, 720, 2f)
        val horizontalMenu = MenuScreen(horizontal)
        horizontal.setScreen(horizontalMenu)
        assertFalse(horizontalMenu.portraitMenu)
        val campaign = horizontalMenu.buttons.first { it.id == 1 }
        val endless = horizontalMenu.buttons.first { it.id == 2 }
        assertEquals(campaign.r.top, endless.r.top, 0.1f)
        assertTrue(campaign.r.right < endless.r.left)
    }

    @Test fun portraitEntryScreens() {
        val oldLanguage = L.lang
        try {
            L.setCode("pl")
            for ((tag, d) in linkedMapOf(
                "720x1280" to floatArrayOf(720f, 1280f, 2f),
                "1080x1600" to floatArrayOf(1080f, 1600f, 3f),
            )) {
                val g = game(d[0].toInt(), d[1].toInt(), d[2])
                val screens: Array<Pair<String, Screen>> = arrayOf(
                    "campaign" to CampaignScreen(g),
                    "skirmish" to SkirmishSetupScreen(g),
                    "endless" to ModeScreen(g, Mode.ENDLESS),
                    "daily" to ModeScreen(g, Mode.DAILY),
                    "research" to ResearchScreen(g),
                    "settings" to SettingsScreen(g),
                )
                for ((name, screen) in screens) {
                    g.setScreen(screen)
                    shot(g, "menu147_${tag}_${name}")
                    assertTrue("Ekran $tag/$name bez kafelków", screen.buttons.isNotEmpty())
                }
            }
        } finally {
            L.lang = oldLanguage
        }
    }
}
