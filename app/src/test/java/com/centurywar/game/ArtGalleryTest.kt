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
        val ground = Paint().apply { color = Color.rgb(150, 126, 84) }
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 35, 46); textSize = 15f; isFakeBoldText = true
        }

        fun strip(rows: List<Pair<String, UnitDef>>, filename: String) {
            val bmp = Bitmap.createBitmap(frames * cell, rows.size * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(196, 214, 228))
            for ((row, entry) in rows.withIndex()) {
                val (name, def) = entry
                val y = row * cell.toFloat()
                c.drawRect(0f, y + cell * 0.86f, frames * cell.toFloat(), y + cell, ground)
                for (f in 0 until frames) {
                    // 0-5: marsz i wygaszenie, 6-11: odnowienie, 12-15: odrzut/cios.
                    // Faza chodu zostaje stała od klatki 5: koła nie mogą wrócić do zera.
                    val walk = minOf(f, 5) * 0.55f
                    val stride = when { f < 3 -> 1f; f < 6 -> 1f - (f - 2) / 3f; else -> 0f }
                    val windup = if (f in 6..11) (f - 6) / 5f else if (f >= 12) 0f else -1f
                    val atk = if (f >= 12) 1f - (f - 12) / 4f else 0f
                    r.drawUnitPose(c, def, f * cell + cell / 2f, y + cell * 0.86f,
                        cell * 0.78f, Ui.PLAYER, walk, stride, atk, windup)
                    c.drawText(name, f * cell + 6f, y + 18f, labels)
                }
            }
            save(bmp, filename)
            bmp.recycle()
        }

        strip(listOf(
            "Łucznik żelaza" to Defs.unit(2, Role.RANGED),
            "Piechur feudalny" to Defs.unit(3, Role.MELEE),
            "Strzelec prochu" to Defs.unit(4, Role.RANGED),
            "Piechur brązu" to Defs.unit(1, Role.MELEE),
        ), "gallery_animation")

        strip(listOf(
            "Koń z jeźdźcem" to Defs.unit(3, Role.HEAVY),
            "Rydwan" to Defs.unit(1, Role.HEAVY),
            "Mamut" to Defs.unit(0, Role.ELITE),
            "Słoń bojowy" to Defs.unit(1, Role.ELITE),
            "Trebusz" to Defs.unit(3, Role.ELITE),
            "Armata" to Defs.unit(4, Role.ELITE),
            "Mech parowy" to Defs.unit(5, Role.HEAVY),
            "Czołg ciężki" to Defs.unit(6, Role.ELITE),
            "Mech bojowy" to Defs.unit(7, Role.ELITE),
            "Poduszkowiec" to Defs.unit(8, Role.HEAVY),
        ), "gallery_animation_machines")
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
                c.save()
                c.clipRect(frame * cell.toFloat(), y, (frame + 1) * cell.toFloat(), y + cell)
                r.drawUnitPose(c, def, frame * cell + cell / 2f +
                    (if (def.body == Body.HUMAN && dying >= 0f) 18f else 0f),
                    y + cell * 0.88f, cell * 0.72f, Ui.PLAYER,
                    0f, 0f, 0f, -1f, hitAge, dying)
                c.restore()
            }
        }
        save(bmp, "gallery_death")
    }


    /** Przegląd wszystkich 11 typów pocisków, dwóch znaczników i czterech faz eksplozji. */
    @Test
    fun projectilesAndExplosions() {
        val labels = arrayOf(
            "STONE", "JAVELIN", "ARROW", "BOLT", "BALL", "BULLET",
            "SHELL", "ROCKET", "PLASMA", "BEAM", "BOULDER",
        )
        for (q in intArrayOf(Quality.HIGH, Quality.LOW)) {
            val g = game(1800, 1100, q)
            g.profile.screenShake = 0
            val b = Battle(BattleConfig(Mode.SKIRMISH, 101L, 1f, startEra = 6))
            val r = BattleRenderer(g, b)
            r.layout(1800f, 1100f, 0f, 85f)
            r.camX = 0f
            for (type in 1..11) {
                val p = b.projs[type - 1]
                p.active = true
                p.type = type
                p.side = 0
                p.era = if (type >= Proj.PLASMA && type <= Proj.BEAM) 8 else 6
                p.x = 130f + (type - 1) % 4 * 260f
                p.h = 120f + (type - 1) / 4 * 130f
                p.vx = 580f
                p.vh = -140f
                p.g = 400f
                p.t = 0.28f
                p.dur = 1.2f
                p.delay = 0f
                p.siegeMul = 1f
                p.splash = 0f
            }
            for (j in 0..1) {
                val p = b.projs[11 + j]
                p.active = true
                p.type = Proj.SHELL
                p.side = 0
                p.era = 6
                p.x = 140f + j * 260f
                p.h = 450f
                p.vx = 310f
                p.vh = -950f
                p.g = 0f
                p.t = if (j == 0) 0.55f else 0f
                p.dur = 0.85f
                p.delay = if (j == 0) 0f else 0.15f
                p.tx = 360f + j * 260f
                p.siegeMul = 0.25f
                p.splash = 60f
            }
            val image = Bitmap.createBitmap(1800, 1480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            r.render(canvas, 1f / 60f, 1800f, 1100f)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 23f
                isFakeBoldText = true
            }
            for (i in labels.indices) {
                val p = b.projs[i]
                val x = (p.x - r.camX) * r.k
                val y = r.groundY - p.h * r.k
                canvas.drawText(labels[i], x - 45f, y + 43f, label)
            }
            canvas.drawText("BARRAGE: ACTIVE / DELAYED", 650f, r.groundY - 32f, label)
            val frames = intArrayOf(1, 6, 14, 24)
            for (i in frames.indices) {
                val small = game(450, 380, q)
                small.profile.screenShake = 0
                val explosion = Battle(BattleConfig(Mode.SKIRMISH, 200L + i, 1f, startEra = 6))
                val er = BattleRenderer(small, explosion)
                er.layout(450f, 380f, 0f, 45f)
                er.camX = 0f
                val part = Bitmap.createBitmap(450, 380, Bitmap.Config.ARGB_8888)
                val partCanvas = Canvas(part)
                explosion.fx.add(FxBuffer.EXPLOSION, 435f, 20f, 0, 1.5f)
                repeat(frames[i]) { er.render(partCanvas, Battle.STEP, 450f, 380f) }
                canvas.drawBitmap(part, i * 450f, 1100f, null)
                canvas.drawText("EXPLOSION " + (i + 1), i * 450f + 25f, 1150f, label)
                part.recycle()
            }
            save(image, if (q == Quality.HIGH) "gallery_projectiles_high" else "gallery_projectiles_low")
            if (q == Quality.HIGH) save(image, "gallery_projectiles")
            image.recycle()
        }
    }

    /** Warianty w osobnych planszach galerii; nazwy PNG zawierają gallery_units_*. */
    @Test
    fun variantUnits() {
        val arts = arrayOf(EraArt.STEAM, EraArt.SILICON)
        for (quality in intArrayOf(Quality.HIGH, Quality.LOW)) {
            val cell = if (quality == Quality.HIGH) 320 else 110
            val g = game(1600, 900, quality)
            val renderer = BattleRenderer(g, Battle(BattleConfig(Mode.SKIRMISH, 8L, 1f)))
            val bmp = Bitmap.createBitmap(cell * 4, cell * 4, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(196, 214, 228))
            val ground = Paint().apply { color = Color.rgb(150, 126, 84) }
            for (i in 0 until 4) {
                val era = Defs.eraOf(arts[i / 2])
                val variant = i % 2
                for (role in Role.entries) {
                    val x = role.ordinal * cell.toFloat()
                    val y = i * cell.toFloat()
                    c.drawRect(x, y + cell * 0.86f, x + cell, y + cell, ground)
                    val def = Defs.unit(era, role, variant)
                    renderer.drawUnitIcon(c, def, x + cell / 2f, y + cell * 0.86f,
                        cell * 0.78f, Ui.PLAYER)
                }
            }
            save(bmp, if (quality == Quality.HIGH) "gallery_units_variants_high"
                else "gallery_units_variants_low")
        }
    }

    /** Sylwetki czterech osobnych baz i wieżyczek wariantowych. */
    @Test
    fun variantBases() {
        val pw = 1100
        val ph = 760
        for (quality in intArrayOf(Quality.HIGH, Quality.LOW)) {
            val bmp = Bitmap.createBitmap(pw * 2, ph * 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val g = game(pw, ph, quality)
            for (idx in 0..3) {
                val art = if (idx < 2) EraArt.STEAM else EraArt.SILICON
                val era = Defs.eraOf(art)
                val variant = idx % 2
                val battle = Battle(BattleConfig(Mode.SKIRMISH, 77L, 1f,
                    startEra = era, maxEra = era))
                check(battle.chooseVariant(variant))
                val side = battle.player
                side.slots = 3
                for (i in 0 until side.slots) {
                    side.turretEra[i] = era
                    side.turretVariant[i] = variant
                }
                val renderer = BattleRenderer(g, battle)
                renderer.layout(pw.toFloat(), ph.toFloat(), 0f, 40f)
                c.save()
                c.translate((idx % 2) * pw.toFloat(), (idx / 2) * ph.toFloat())
                c.clipRect(0f, 0f, pw.toFloat(), ph.toFloat())
                renderer.render(c, 0.016f, pw.toFloat(), ph.toFloat())
                renderer.camX = Defs.BASE_FRONT - (pw * 0.62f) / renderer.k
                renderer.render(c, 0.016f, pw.toFloat(), ph.toFloat())
                c.restore()
            }
            save(bmp, if (quality == Quality.HIGH) "gallery_bases_variants_high"
                else "gallery_bases_variants_low")
        }
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
    /**
     * Jedna plansza wszystkich 9 epok i 4 wariantów, z osobnym podglądem LOW.
     * Obejmuje też niebo i ziemię, więc łatwo porównać czytelność sylwetek.
     */
    @Test
    fun eraBackdropStrips() {
        val entries = ArrayList<Pair<Int, Int>>()
        for (era in 0 until Defs.ERA_COUNT) {
            entries.add(era to -1)
            for (variant in Defs.spec(era).variants.indices) entries.add(era to variant)
        }
        val w = 1200
        val h = 270
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            isFakeBoldText = true
            textSize = 21f
        }
        for (quality in intArrayOf(Quality.HIGH, Quality.LOW)) {
            val g = game(w, h, quality)
            val sheet = Bitmap.createBitmap(w, h * entries.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            for ((row, selection) in entries.withIndex()) {
                val (era, variant) = selection
                val b = Battle(BattleConfig(Mode.SKIRMISH, 56L + row, 1f,
                    startEra = era, maxEra = era))
                if (variant >= 0) check(b.chooseVariant(variant))
                val renderer = BattleRenderer(g, b)
                renderer.layout(w.toFloat(), h.toFloat(), 0f, 30f)
                renderer.camX = 640f
                canvas.save()
                canvas.translate(0f, row * h.toFloat())
                canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
                renderer.render(canvas, 0f, w.toFloat(), h.toFloat())
                val art = Defs.sideArt(era, variant)
                canvas.drawText(art.name, 32f, 38f, label)
                canvas.restore()
            }
            save(sheet, if (quality == Quality.LOW) "gallery_era_backdrops_low"
                else "gallery_era_backdrops_high")
            sheet.recycle()
        }
    }

}
