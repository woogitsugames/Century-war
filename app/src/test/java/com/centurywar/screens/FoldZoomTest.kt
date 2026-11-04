package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Role
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Dzień 17: przybliżenie pola bitwy na zewnętrznym ekranie Folda w pionie
 * ([BattleRenderer.NARROW_ZOOM]). Zrzuty przed/po tej samej sceny w tym samym momencie
 * (build/screenshots/foldzoom_before_*.png, foldzoom_after_*.png, stykówka foldzoom_compare.png),
 * geometria (wielkość fizyczna, HUD) i celowanie ostrzału w tym samym punkcie świata.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FoldZoomTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private class Device(val w: Int, val h: Int, val density: Float, val insetT: Int = 0, val insetL: Int = 0)

    private val foldOuter = Device(904, 2316, 2.7f, insetT = 88)
    private val phone720 = Device(1600, 720, 2f)
    private val others = linkedMapOf(
        "phone720" to phone720,
        "phone20x9" to Device(2400, 1080, 2.7f, insetL = 96),
        "foldInner" to Device(2184, 1968, 2.7f, insetT = 70),
        "foldPortrait" to Device(1968, 2184, 2.7f, insetT = 70),
        "tablet" to Device(2560, 1600, 2f),
    )

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    @After fun restore() { BattleRenderer.NARROW_ZOOM = true }

    private fun game(d: Device): Game {
        val f = File.createTempFile("foldzoom", ".json").apply { delete() }
        val profile = Profile(f).apply {
            lang = "pl"; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false; medals = 340
        }
        return Game(host, profile, Quality.HIGH).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(d.insetL, d.insetT, 0, 0)
            history = BattleHistory(File.createTempFile("foldzoomh", ".json").apply { delete() })
            historyLoaded = true
            replays = ReplayStore(File.createTempFile("foldzoomr", "").apply { delete(); mkdirs() })
        }
    }

    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun battleOf(s: BattleScreen) = field(s, "battle").get(s) as Battle
    private fun rendererOf(s: BattleScreen) = field(s, "r").get(s) as BattleRenderer

    private fun open(g: Game, cfg: BattleConfig): BattleScreen {
        val s = BattleScreen(g, cfg)
        g.setScreen(s)
        g.finishScreenTransition()
        g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 0f)
        return s
    }

    private fun render(g: Game, name: String) {
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        g.frame(Canvas(bmp), 0f)
        File(out, "$name.png").outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bmp.recycle()
    }

    /** Ta sama, deterministyczna bitwa w środku: po kilka jednostek każdej roli po obu stronach. */
    private fun midBattle(g: Game): BattleScreen {
        val s = open(g, BattleConfig(Mode.SKIRMISH, 1717L, 1f, startEra = 4, maxEra = 4))
        val b = battleOf(s)
        b.player.gold = 200000f
        b.enemy.gold = 200000f
        val roles = arrayOf(Role.MELEE, Role.RANGED, Role.HEAVY, Role.MELEE, Role.RANGED)
        for (i in 0 until 10) {
            b.spawn(b.player, Defs.unit(4, roles[i % roles.size]))
            b.spawn(b.enemy, Defs.unit(4, roles[(i + 2) % roles.size]))
            repeat(70) { b.step() }
        }
        repeat(240) { b.step() }
        val r = rendererOf(s)
        r.camX = r.followTarget()
        r.clampCam()
        return s
    }

    private fun scenes(g: Game, tag: String) {
        // 1. Początek bitwy: kamera przy własnej bazie, pierwsi żołnierze.
        run {
            val s = open(g, BattleConfig(Mode.SKIRMISH, 1716L, 1f, startEra = 1, maxEra = 1))
            val b = battleOf(s)
            b.player.gold = 5000f
            b.spawn(b.player, Defs.unit(1, Role.MELEE))
            repeat(90) { b.step() }
            b.spawn(b.player, Defs.unit(1, Role.RANGED))
            repeat(150) { b.step() }
            render(g, "foldzoom_${tag}_1_start")
        }
        // 2. Środek bitwy z wieloma jednostkami.
        run {
            midBattle(g)
            render(g, "foldzoom_${tag}_2_mid")
        }
        // 3. Celowanie ostrzału: palec trzyma celownik nad wrogami (przed puszczeniem).
        run {
            val s = midBattle(g)
            val b = battleOf(s)
            val r = rendererOf(s)
            b.player.abilityCd[0] = 0f
            val target = enemyCluster(b)
            r.camX = target - r.worldVisible * 0.5f
            r.clampCam()
            s.aimingBarrage = true
            val y = r.groundY - 30f * g.ui.dp
            val x = r.screenX(target)
            g.pushTouch(TouchEvent.DOWN, x - 40f * g.ui.dp, y, 0)
            g.pushTouch(TouchEvent.MOVE, x, y, 0)
            render(g, "foldzoom_${tag}_3_aim")
        }
        // 4. Endless z bossem (fala 10) w tym samym miejscu świata.
        run {
            val s = open(g, BattleConfig(Mode.ENDLESS, 812L, 1f))
            val b = battleOf(s)
            b.wave = 9
            b.nextBossToSpawn = 10
            b.waveTimer = 0f
            b.step()
            b.pendingCards?.let { b.chooseEndlessCard(0) }
            val boss = b.currentBoss()!!
            boss.x = b.player.baseFront + 420f
            val r = rendererOf(s)
            r.camX = boss.x - r.worldVisible * 0.55f
            r.clampCam()
            render(g, "foldzoom_${tag}_4_boss")
        }
    }

    private fun enemyCluster(b: Battle): Float {
        var best = 0f
        var bestN = -1
        for (u in b.units) {
            if (!u.alive || u.side != 1) continue
            val n = b.units.count { it.alive && it.side == 1 && abs(it.x - u.x) < 120f }
            if (n > bestN) { bestN = n; best = u.x }
        }
        return best
    }

    /** Pary przed/po i stykówka porównawcza (przed | po, zmniejszone o połowę). */
    @Test
    fun beforeAfterShots() {
        for (zoom in booleanArrayOf(false, true)) {
            BattleRenderer.NARROW_ZOOM = zoom
            scenes(game(foldOuter), if (zoom) "after" else "before")
        }
        val names = arrayOf("1_start", "2_mid", "3_aim", "4_boss")
        val cellW = foldOuter.w / 2
        val cellH = foldOuter.h / 2
        val label = 60
        val sheet = Bitmap.createBitmap(cellW * 2 * 2 + 30, (cellH + label) * 2 + 10, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(Color.rgb(24, 24, 28))
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.WHITE; textSize = 30f; isFakeBoldText = true
        }
        for ((i, n) in names.withIndex()) {
            val ox = (i % 2) * (cellW * 2 + 20)
            val oy = (i / 2) * (cellH + label + 10)
            for ((j, kind) in arrayOf("before", "after").withIndex()) {
                val bmp = BitmapFactory.decodeFile(File(out, "foldzoom_${kind}_$n.png").path)
                val x = ox + j * cellW
                c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), Rect(x, oy + label, x + cellW, oy + label + cellH), p)
                c.drawText((if (j == 0) "PRZED  " else "PO  ") + n, x + 12f, oy + 42f, p)
                bmp.recycle()
            }
        }
        File(out, "foldzoom_compare.png").outputStream().use { check(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private class Geo(val k: Float, val groundY: Float, val worldVisible: Float, val fieldTop: Float,
        val fieldBottom: Float, val alertTop: Float, val alertBottom: Float, val zoom: Boolean)

    private fun geometry(d: Device): Geo {
        val g = game(d)
        val s = open(g, BattleConfig(Mode.SKIRMISH, 5L, 1f))
        val r = rendererOf(s)
        val field = field(s, "fieldRect").get(s) as android.graphics.RectF
        val alert = field(s, "baseAlertBtn").get(s) as com.centurywar.engine.Button
        return Geo(r.k, r.groundY, r.worldVisible, field.top, field.bottom, alert.r.top, alert.r.bottom,
            r.narrowZoomActive)
    }

    @Test
    fun narrowScreenGeometry() {
        BattleRenderer.NARROW_ZOOM = false
        val before = geometry(foldOuter)
        BattleRenderer.NARROW_ZOOM = true
        val after = geometry(foldOuter)
        val phone = geometry(phone720)
        assertTrue(after.zoom)
        assertFalse(phone.zoom)
        // Rozmiar fizyczny (px świata na dp) co najmniej 85% telefonu 720p.
        val physFold = after.k / foldOuter.density
        val physPhone = phone.k / phone720.density
        assertTrue("fold ${physFold} vs telefon ${physPhone}", physFold >= physPhone * 0.85f)
        assertTrue("przybliżenie ≥ 1,4×: ${after.k / before.k}", after.k >= before.k * 1.4f)
        // Najwyższa postać (boss ×1,25, maszyna 120 j.) z paskiem HP mieści się pod górnym paskiem.
        val tallest = 120f * 1.25f * 1.3f + 40f
        assertTrue("boss pod górnym HUD", after.groundY - tallest * after.k >= after.fieldTop)
        // Ziemia nad dolnym HUD, z miejscem na pas przedpola.
        assertTrue(after.groundY < after.fieldBottom - 20f * foldOuter.density)
        // Znak „baza atakowana” w polu bitwy, nie na HUD.
        assertTrue(after.alertTop >= after.fieldTop && after.alertBottom <= after.fieldBottom)
        // Mniej pustego nieba: pas nad najwyższą zwykłą jednostką (120 j.) mniejszy niż przed zmianą.
        val skyBefore = before.groundY - 120f * before.k - before.fieldTop
        val skyAfter = after.groundY - 120f * after.k - after.fieldTop
        assertTrue("niebo $skyBefore → $skyAfter", skyAfter < skyBefore * 0.8f)
        println("foldzoom: k ${before.k} → ${after.k}, widok ${before.worldVisible} → ${after.worldVisible}, " +
            "ziemia ${before.groundY} → ${after.groundY}, niebo ${skyBefore} → ${skyAfter}, " +
            "fizycznie ${physFold / physPhone * 100f}% telefonu 720p")
    }

    /** Inne ekrany: flaga nie zmienia nic (telefon, rozłożony Fold, Fold bokiem, tablet). */
    @Test
    fun otherScreensUnchanged() {
        for ((tag, d) in others) {
            BattleRenderer.NARROW_ZOOM = false
            val off = geometry(d)
            BattleRenderer.NARROW_ZOOM = true
            val on = geometry(d)
            assertFalse(tag, on.zoom)
            assertEquals(tag, off.k, on.k, 0f)
            assertEquals(tag, off.groundY, on.groundY, 0f)
            assertEquals(tag, off.worldVisible, on.worldVisible, 0f)
        }
    }

    /** Celowanie przeciągnij-puść trafia w ten sam punkt świata przy obu skalach. */
    @Test
    fun aimingHitsSameWorldPoint() {
        val results = HashMap<Boolean, List<Int>>()
        for (zoom in booleanArrayOf(false, true)) {
            BattleRenderer.NARROW_ZOOM = zoom
            val g = game(foldOuter)
            val shots = ArrayList<Int>()
            // a) skupisko wrogów (przyciąganie), b) puste miejsce bez wrogów, c) obok własnych jednostek.
            for (variant in 0..2) {
                val s = midBattle(g)
                val b = battleOf(s)
                val r = rendererOf(s)
                b.player.abilityCd[0] = 0f
                val target = when (variant) {
                    0 -> enemyCluster(b)
                    1 -> b.player.baseFront + 260f
                    else -> b.units.filter { it.alive && it.side == 0 }.maxOf { it.x }
                }
                r.camX = target - r.worldVisible * 0.5f
                r.clampCam()
                s.aimingBarrage = true
                val y = r.groundY - 30f * g.ui.dp
                val x = r.screenX(target)
                assertEquals(target, r.worldX(x), 0.01f)
                g.pushTouch(TouchEvent.DOWN, x - 40f * g.ui.dp, y, 0)
                g.pushTouch(TouchEvent.MOVE, x, y, 0)
                g.pushTouch(TouchEvent.UP, x, y, 0)
                g.frame(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)), 0f)
                val cmd = s.commands.log().commands.last { it.kind == Replay.Kind.BARRAGE_AT }
                for (u in b.units) if (u.alive && u.side == 0)
                    assertTrue("zoom=$zoom: cel ${cmd.argument} przy własnej ${u.x}",
                        abs(u.x - cmd.argument) >= BarrageAiming.SPREAD - 1f)
                shots.add(cmd.argument)
            }
            results[zoom] = shots
        }
        assertEquals(results[false], results[true])
    }
}
