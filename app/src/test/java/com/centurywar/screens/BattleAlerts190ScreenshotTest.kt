package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Bonuses
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R10-03: znak „baza atakowana” przy lewej krawędzi pola na telefonie 720p i zewnętrznym Foldzie,
 * w układzie zwykłym i leworęcznym (pl, en, de). Znak nie nachodzi na HUD, minimapę ani pasek
 * górny, przesuwa kamerę na bazę i znika przy samouczku, dymku, celowaniu ostrzału i pauzie.
 * Zrzuty: build/screenshots/alerts190_<urządzenie>_<układ>_<język>_<scena>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BattleAlerts190ScreenshotTest : PrereleasePolishBase() {
    private val shown = listOf("phone720", "foldOuter")

    private fun save(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val c = TextCanvas(bmp)
            g.frame(c, 0f)
            lastTexts = ArrayList(c.texts)
            File(out, "alerts190_$name.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bmp.recycle() }
    }

    /** Kamera daleko od bazy (ręcznie przytrzymana), baza traci [loss] maks. HP. */
    private fun hitOffscreen(g: Game, s: BattleScreen, loss: Float) {
        field(s, "manualCam").setFloat(s, 60f)
        s.r.camX = 1e6f
        s.r.clampCam()
        val p = s.battle.player
        p.baseHp -= p.baseMaxHp * loss
        tick(g, 2)
    }

    private fun tap(g: Game, x: Float, y: Float) {
        val e = TouchEvent()
        e.x = x; e.y = y; e.pointer = 0
        e.action = TouchEvent.DOWN; g.current!!.handle(e)
        e.action = TouchEvent.UP; g.current!!.handle(e)
    }

    private fun overlap(a: RectF, b: RectF) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test
    fun badgeVisibleClearOfHudAndFocusesBase() {
        val old = L.lang
        try {
            for (device in shown) for (left in listOf(false, true)) for (lang in listOf("pl", "en", "de")) {
                val g = game(devices.getValue(device), lang)
                g.profile.leftHandedHud = left
                val where = "${device}_${if (left) "lewo" else "prawo"}_$lang"
                val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 190L, 1f))
                g.setScreen(s)
                tick(g, 3)
                val dp = g.ui.dp
                val b = s.baseAlertBtn
                require10(!b.visible, "$where: znak na starcie bitwy")

                hitOffscreen(g, s, 0.6f)
                require10(b.visible, "$where: brak znaku, gdy baza obrywa poza kadrem")
                save(g, "${where}_znak")
                // Plakietka 44 dp, cel dotyku ≥ 48 dp, wewnątrz pola bitwy.
                require10(b.r.width() >= 43.9f * dp && b.r.width() + 2 * b.hitSlop >= 47.9f * dp,
                    "$where: za mały znak lub cel dotyku")
                require10(b.r.top >= s.fieldRect.top && b.r.bottom <= s.fieldRect.bottom &&
                    b.r.left >= g.insetL.toFloat(), "$where: znak poza polem bitwy ${b.r} / ${s.fieldRect}")
                val pct = lastTexts.firstOrNull { it.s == "40%" }
                require10(pct != null, "$where: brak HP bazy w % przy znaku")
                val areas = ArrayList<RectF>()
                areas.add(RectF(b.r))
                if (pct != null) areas.add(RectF(pct.r))
                for (other in s.buttons) {
                    if (other === b || !other.visible || other.r.width() <= 0f) continue
                    for (a in areas) require10(!overlap(a, other.r),
                        "$where: znak nachodzi na przycisk ${other.id} ${other.r}")
                }
                for (rect in listOf(s.mini, s.goldRect, s.xpRect, s.weatherRect, s.armyRect, s.topBar)) {
                    if (rect.width() <= 0f) continue
                    for (a in areas) require10(!overlap(a, rect), "$where: znak nachodzi na HUD $rect")
                }
                require10(s.accessibilityLabel(b) == L.f("alert190_base_a11y", 40) &&
                    s.accessibilityLabel(b).contains("40"), "$where: opis TalkBack znaku")

                // Celowanie ostrzału: znak ukryty i nie łapie dotyku.
                s.aimingBarrage = true
                tick(g, 1)
                require10(!b.visible && !b.contains(b.r.centerX(), b.r.centerY()),
                    "$where: znak aktywny podczas celowania ostrzału")
                s.aimingBarrage = false
                // Pauza.
                s.paused = true
                tick(g, 1)
                require10(!b.visible, "$where: znak na pauzie")
                s.paused = false
                hitOffscreen(g, s, 0.02f)
                require10(b.visible, "$where: znak nie wrócił po pauzie")

                // Dotyk znaku: kamera na bazę, automat wstrzymany, znak znika.
                tap(g, b.r.centerX(), b.r.centerY())
                require10(s.r.camX <= -119f, "$where: kamera nie pokazuje bazy (camX=${s.r.camX})")
                require10(field(s, "manualCam").getFloat(s) > 0f, "$where: automatyczna kamera nie wstrzymana")
                tick(g, 1)
                require10(!b.visible, "$where: znak przy widocznej bazie")
                save(g, "${where}_baza")

                // Znika po 2,5 s spokoju.
                hitOffscreen(g, s, 0.02f)
                require10(b.visible, "$where: brak znaku po kolejnym trafieniu")
                tick(g, 85)
                field(s, "manualCam").setFloat(s, 60f)
                tick(g, 1)
                require10(!b.visible, "$where: znak nie zgasł po 2,5 s spokoju")

                // Dymek HintOverlay ma pierwszeństwo.
                val hints = field(s, "hints").get(s) as HintOverlay
                g.profile.hintsEnabled = true
                hitOffscreen(g, s, 0.02f)
                HintOverlay::class.java.getDeclaredMethod("activate", Int::class.javaPrimitiveType, String::class.java)
                    .apply { isAccessible = true }.invoke(hints, HintOverlay.UPGRADE, "hint_upgrade")
                hitOffscreen(g, s, 0.02f)
                require10(hints.activeId >= 0, "$where: dymek się nie pokazał")
                require10(!b.visible, "$where: znak razem z dymkiem")
                if (device == "phone720" && lang == "pl" && !left) save(g, "${where}_dymek")
                g.history?.close()
            }
        } finally { L.lang = old }
        assertTrue("Regresje R10-03:\n$regressions", regressions.isEmpty())
    }

    @Test
    fun badgeHiddenDuringTutorialStep() {
        val old = L.lang
        try {
            for (device in shown) {
                val g = game(devices.getValue(device), "pl")
                g.profile.tutorialDone = false
                val s = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 85L, tutorial = true))
                g.setScreen(s)
                val t = s.tut!!
                var n = 0
                while (!t.showing && n++ < 300) tick(g, 1)
                require10(t.showing, "$device: krok samouczka się nie pokazał")
                hitOffscreen(g, s, 0.3f)
                require10(t.showing && !s.baseAlertBtn.visible, "$device: znak razem z krokiem samouczka")
                save(g, "${device}_samouczek")
                g.history?.close()
            }
        } finally { L.lang = old }
        assertTrue("Regresje R10-03:\n$regressions", regressions.isEmpty())
    }

    @Test
    fun reduceFlashDrawsSteadyBadge() {
        val g = game(devices.getValue("phone720"), "pl")
        g.profile.reduceFlash = true
        val s = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 191L, 1f))
        g.setScreen(s)
        tick(g, 3)
        hitOffscreen(g, s, 0.3f)
        require10(s.baseAlertBtn.visible, "reduceFlash: brak znaku")
        save(g, "phone720_reduceFlash")
        val dp = g.ui.dp
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val probes = IntArray(6)
        for (i in probes.indices) {
            tick(g, 4, 1f / 60f)
            g.frame(android.graphics.Canvas(bmp), 0f)
            val b = s.baseAlertBtn.r
            probes[i] = bmp.getPixel(b.centerX().toInt(), (b.top + 4 * dp).toInt())
        }
        bmp.recycle()
        require10(probes.all { it == probes[0] }, "reduceFlash: znak pulsuje")
        g.history?.close()
        assertTrue("Regresje R10-03:\n$regressions", regressions.isEmpty())
    }
}
