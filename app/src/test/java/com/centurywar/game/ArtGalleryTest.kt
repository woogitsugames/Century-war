package com.centurywar.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Ui
import com.centurywar.meta.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Galeria grafiki do przeglądu: wszystkie jednostki w dużym powiększeniu
 * (ten sam kod co na polu bitwy, przez drawUnitIcon) oraz bazy wszystkich epok z wieżami.
 * Pliki: build/screenshots/gallery_*.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ArtGalleryTest {

    private val out = File("build/screenshots").apply { mkdirs() }

    private fun game(w: Int, h: Int, q: Int): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("profile", ".json").apply { delete() }
        val g = Game(host, Profile(f).apply { tutorialDone = true }, q)
        g.density = 2.5f
        g.resize(w, h, w, h)
        g.setQuality(q)
        return g
    }

    private fun save(bmp: Bitmap, name: String) =
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun unitGrid(q: Int, cell: Int, name: String, team: Int) {
        val g = game(1600, 900, q)
        val b = Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f))
        val r = BattleRenderer(g, b)
        val cols = 4
        val bmp = Bitmap.createBitmap(cols * cell, Defs.ERA_COUNT * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(196, 214, 228))
        val ground = Paint().apply { color = Color.rgb(150, 126, 84) }
        for (era in 0 until Defs.ERA_COUNT) for (i in 0 until 4) {
            val def = Defs.unit(era, Role.values()[i])
            val x = i * cell.toFloat(); val y = era * cell.toFloat()
            c.drawRect(x, y + cell * 0.86f, x + cell, y + cell, ground)
            r.drawUnitIcon(c, def, x + cell / 2f, y + cell * 0.86f, cell * 0.78f, team)
        }
        save(bmp, name)
    }

    @Test
    fun units() {
        unitGrid(Quality.HIGH, 360, "gallery_units_high", Ui.PLAYER)
        unitGrid(Quality.HIGH, 360, "gallery_units_enemy", Ui.ENEMY)
        unitGrid(Quality.LOW, 110, "gallery_units_low_small", Ui.PLAYER)
    }

    /**
     * Klatki cyklu: marsz z zatrzymaniem (krok wygasa), potem odnowienie (napinanie/zamach)
     * i atak (puszczenie/cios). Sąsiednie klatki mają się różnić niewiele: bez skoków pozy.
     */
    @Test
    fun animationStrips() {
        val g = game(1600, 900, Quality.HIGH)
        val r = BattleRenderer(g, Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f)))
        val frames = 16
        val cell = 180
        val rows = listOf(Defs.unit(2, Role.RANGED), Defs.unit(3, Role.MELEE), Defs.unit(4, Role.RANGED), Defs.unit(1, Role.MELEE))
        val bmp = Bitmap.createBitmap(frames * cell, rows.size * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(196, 214, 228))
        val ground = Paint().apply { color = Color.rgb(150, 126, 84) }
        for ((row, def) in rows.withIndex()) {
            val y = row * cell.toFloat()
            c.drawRect(0f, y + cell * 0.86f, frames * cell.toFloat(), y + cell, ground)
            for (f in 0 until frames) {
                // 0-5: marsz i wygaszanie kroku, 6-11: odnowienie, 12-15: atak.
                val walk = f * 0.55f
                val stride = when { f < 3 -> 1f; f < 6 -> 1f - (f - 2) / 3f; else -> 0f }
                val windup = if (f in 6..11) (f - 6) / 5f else if (f >= 12) 0f else -1f
                val atk = if (f >= 12) 1f - (f - 12) / 4f else 0f
                r.drawUnitPose(c, def, f * cell + cell / 2f, y + cell * 0.86f, cell * 0.78f, Ui.PLAYER,
                    if (stride > 0f) walk else 0f, stride, atk, windup)
            }
        }
        save(bmp, "gallery_animation")
    }

    /**
     * Cztery fazy trafienia i osiem faz śmierci: każda sylwetka zachowuje własny ruch.
     * Plik służy do wizualnej kontroli bez uruchamiania bitwy.
     */
    @Test
    fun deathStrips() {
        val g = game(1600, 900, Quality.HIGH)
        val r = BattleRenderer(g, Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f)))
        val frames = 12
        val cell = 188
        val rows = listOf(
            Defs.unit(3, Role.MELEE),   // piechur
            Defs.unit(4, Role.RANGED),  // strzelec
            Defs.unit(3, Role.HEAVY),   // koń z jeźdźcem
            Defs.unit(0, Role.ELITE),   // mamut
            Defs.unit(4, Role.ELITE),   // armata
            Defs.unit(6, Role.ELITE),   // czołg
            Defs.unit(7, Role.ELITE),   // mech
        )
        check(rows[0].body == Body.HUMAN && rows[2].body == Body.BEAST)
        check(rows[4].body == Body.CANNON && rows[5].body == Body.VEHICLE && rows[6].body == Body.MECH)
        val bmp = Bitmap.createBitmap(frames * cell, rows.size * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(196, 214, 228))
        val ground = Paint().apply { color = Color.rgb(150, 126, 84) }
        val hitAges = floatArrayOf(0f, 0.05f, 0.12f, 0.2f)
        for ((row, def) in rows.withIndex()) {
            val y = row * cell.toFloat()
            c.drawRect(0f, y + cell * 0.88f, frames * cell.toFloat(), y + cell, ground)
            for (frame in 0 until frames) {
                val hitAge = if (frame < 4) hitAges[frame] else -1f
                val dying = if (frame >= 4) (frame - 4) / 7f * 0.8f else -1f
                r.drawUnitPose(c, def, frame * cell + cell / 2f, y + cell * 0.88f,
                    cell * 0.8f, Ui.PLAYER, 0f, 0f, 0f, -1f, hitAge, dying)
            }
        }
        save(bmp, "gallery_death")
    }

    @Test
    fun bases() {
        for (q in intArrayOf(Quality.HIGH, Quality.LOW)) {
            val pw = 1100; val ph = 760
            val bmp = Bitmap.createBitmap(pw * 2, ph * ((Defs.ERA_COUNT + 1) / 2), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val g = game(pw, ph, q)
            for (era in 0 until Defs.ERA_COUNT) {
                val b = Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f, startEra = era, maxEra = Defs.ERA_COUNT - 1))
                val s = b.sides[0]
                s.slots = 4
                for (i in 0 until 4) s.turretEra[i] = (era - i).coerceAtLeast(0)
                val r = BattleRenderer(g, b)
                r.layout(pw.toFloat(), ph.toFloat(), 0f, 40f)
                val px = (era % 2) * pw.toFloat(); val py = (era / 2) * ph.toFloat()
                c.save()
                c.translate(px, py)
                c.clipRect(0f, 0f, pw.toFloat(), ph.toFloat())
                // klatka bitwy (niebo, tło, baza), potem kamera ustawiona na bazę gracza
                r.render(c, 0.016f, pw.toFloat(), ph.toFloat())
                r.camX = Defs.BASE_FRONT - (pw * 0.62f) / r.k
                r.render(c, 0.016f, pw.toFloat(), ph.toFloat())
                c.restore()
            }
            save(bmp, if (q == Quality.HIGH) "gallery_bases_high" else "gallery_bases_low")
        }
    }
}
