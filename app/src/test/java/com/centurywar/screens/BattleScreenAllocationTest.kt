package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Method

/** Mierzy pełne update + render ekranu bez kosztu tworzenia bitwy i bitmapy. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BattleScreenAllocationTest {
    private class Meter(private val bean: Any, private val getter: Method) {
        fun bytes(): Long = getter.invoke(bean, Thread.currentThread().id) as Long
    }

    private fun meter(): Meter {
        // android.jar nie udostępnia com.sun.management: dostęp tylko przez refleksję.
        val bean = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("Wymagany ThreadMXBean z licznikiem alokacji",
            api != null && api.isInstance(bean) &&
                api.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true)
        return Meter(bean, api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE))
    }

    private class Fixture(val screen: BattleScreen, val battle: Battle, val canvas: Canvas)

    private fun fixture(seed: Long): Fixture {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = File.createTempFile("battle-alloc-", ".json").apply { delete() }
        val profile = Profile(file).apply { tutorialDone = true; hintsEnabled = false }
        val game = Game(host, profile, Quality.LOW)
        game.density = 2f
        game.resize(1600, 720, 1600, 720)
        val screen = BattleScreen(game, BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = 0, maxEra = 0))
        game.setScreen(screen)
        val battle = field(screen, "battle") as Battle
        battle.ai!!.thinkTimer = Float.MAX_VALUE
        battle.player.hold = true
        battle.enemy.hold = true
        // Mały bufor: Canvas wykonuje wszystkie polecenia, ale nie alokuje dużej bitmapy.
        val canvas = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        return Fixture(screen, battle, canvas)
    }

    private fun field(screen: BattleScreen, name: String): Any =
        BattleScreen::class.java.getDeclaredField(name).apply { isAccessible = true }.get(screen)!!

    private fun check(label: String, fixture: Fixture) {
        val meter = meter()
        val screen = fixture.screen
        val canvas = fixture.canvas
        val dt = 1f / 60f
        repeat(300) { screen.update(dt); screen.render(canvas) }
        val before = meter.bytes()
        repeat(600) { screen.update(dt); screen.render(canvas) }
        val allocated = meter.bytes() - before
        if (System.getenv("PERF_REPORT") == "true") println("$label: $allocated B / 600 klatek")
        assertTrue("$label: $allocated B na 600 klatek (limit < 16384 B)", allocated < 16 * 1024)
    }

    @Test fun turretPanelDoesNotAllocatePerFrame() {
        val f = fixture(4701L)
        val p = f.battle.player
        p.turretEra[0] = p.era
        p.turretVariant[0] = p.eraVariant
        f.screen.onClick(field(f.screen, "turretBtn") as Button)
        check("Panel wież (z kupnem i sprzedażą)", f)
        assertFalse(f.battle.over)
        val sell = (field(f.screen, "slotSell") as Array<*>)[0] as Button
        assertTrue("Cena sprzedaży jest wyświetlana", sell.label.isNotEmpty())
    }

    @Test fun upgradePanelDoesNotAllocatePerFrame() {
        val f = fixture(4702L)
        val upgrade = (field(f.screen, "unitUpgrade") as Array<*>)[0] as Button
        f.screen.onClick(upgrade)
        check("Panel ulepszeń jednostki", f)
        assertFalse(f.battle.over)
        assertTrue("Opis stopnia ulepszenia jest buforowany",
            (field(f.screen, "upgradeTierText") as String).isNotEmpty())
    }

    @Test fun resultsWithDefeatTipDoNotAllocatePerFrame() {
        val f = fixture(4703L)
        f.battle.surrender()
        f.screen.update(1f / 60f) // onFinished oblicza wynik i zawija podpowiedź przed pomiarem.
        assertTrue(field(f.screen, "finished") as Boolean)
        assertTrue("Podpowiedź porażki powinna być obecna",
            (field(f.screen, "tipKey") as String).isNotEmpty())
        check("Ekran wyników", f)
    }
}
