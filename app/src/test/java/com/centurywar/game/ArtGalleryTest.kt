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
 * Galeria grafiki do przeglądu: wszystkie 32 jednostki w dużym powiększeniu
 * (ten sam kod co na polu bitwy, przez drawUnitIcon) oraz bazy 8 epok z wieżami.
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
        val bmp = Bitmap.createBitmap(cols * cell, 8 * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(196, 214, 228))
        val ground = Paint().apply { color = Color.rgb(150, 126, 84) }
        for (era in 0 until 8) for (i in 0 until 4) {
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

    @Test
    fun bases() {
        for (q in intArrayOf(Quality.HIGH, Quality.LOW)) {
            val pw = 1100; val ph = 760
            val bmp = Bitmap.createBitmap(pw * 2, ph * 4, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val g = game(pw, ph, q)
            for (era in 0 until 8) {
                val b = Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f, startEra = era, maxEra = 7))
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
