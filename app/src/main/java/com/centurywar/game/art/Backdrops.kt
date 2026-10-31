package com.centurywar.game.art

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Shader
import com.centurywar.engine.Game
import com.centurywar.engine.Palette
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.Defs
import com.centurywar.game.EraArt
import com.centurywar.game.Terrain
import com.centurywar.game.Weather
import kotlin.math.floor
import kotlin.math.sin

/**
 * Krajobrazy epok. Sylwetki są nagrywane do Picture tylko po zmianie epoki,
 * wariantu, rozmiaru, jakości lub palety. Kamera przesuwa gotowe kafle.
 */
class Backdrops(private val g: Game, private val b: Battle) {
    private companion object {
        const val TILE = 640f
        const val BOTTOM = 390f
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val sky = Paint()
    private val path = Path()
    private val rect = RectF()
    private val pictures = arrayOfNulls<Picture>(3)

    private var cachedEra = -1
    private var cachedVariant = -2
    private var cachedArt: EraArt? = null
    private var cachedPalette = -1
    private var cachedW = -1f
    private var cachedGround = -1f
    private var cachedLayers = -1
    internal var cacheBuilds = 0
        private set
    internal var lastDrawnLayers = 0
        private set
    internal val artInCache: EraArt? get() = cachedArt

    private var skyEra = -1
    private var skyVariant = -2
    private var skyGround = -1f
    private var skyPalette = -1
    private var skyWeather = Weather.CLEAR

    fun invalidate() {
        cachedW = -1f
        skyGround = -1f
    }

    private fun palette(): IntArray {
        val spec = Defs.spec(b.player.era)
        val variant = b.player.eraVariant
        return if (variant >= 0 && variant < spec.variants.size) {
            spec.variants[variant].palette ?: spec.palette
        } else spec.palette
    }

    fun drawSky(c: Canvas, w: Float, h: Float, groundY: Float, time: Float) {
        val era = b.player.era
        val variant = b.player.eraVariant
        val weather = b.weather
        if (era != skyEra || variant != skyVariant || groundY != skyGround ||
            weather != skyWeather || Palette.selected != skyPalette) {
            skyEra = era
            skyVariant = variant
            skyGround = groundY
            skyWeather = weather
            skyPalette = Palette.selected
            val colors = palette()
            val upper = when (weather) {
                Weather.NIGHT -> Ui.blend(colors[0], 0xFF08112C.toInt(), 0.88f)
                Weather.RAIN -> Ui.blend(colors[0], 0xFF415269.toInt(), 0.62f)
                else -> colors[0]
            }
            val lower = when (weather) {
                Weather.NIGHT -> Ui.blend(colors[1], 0xFF11203C.toInt(), 0.82f)
                Weather.RAIN -> Ui.blend(colors[1], 0xFF5B6978.toInt(), 0.58f)
                else -> colors[1]
            }
            // Wysoki kontrast dotyczy przede wszystkim jednostek, nie dekoracji.
            val lo = if (Palette.highContrast) Ui.blend(lower, 0xFF656A79.toInt(), 0.20f) else lower
            sky.shader = LinearGradient(0f, 0f, 0f, groundY.coerceAtLeast(1f),
                upper, lo, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, sky)

        val art = Defs.sideArt(era, variant)
        if (art == EraArt.ORBITAL) {
            fill.color = Color.argb(200, 255, 255, 255)
            for (i in 0 until 60) {
                val x = ((i * 977) % 1000) / 1000f * w
                val y = ((i * 613) % 1000) / 1000f * groundY * 0.7f
                val twinkle = 0.6f + 0.4f * sin(time * 2f + i)
                c.drawCircle(x, y, (1f + i % 3) * g.ui.dp * 0.6f * twinkle, fill)
            }
        }
        if (g.quality.clouds && art != EraArt.ORBITAL) {
            val cloud = if (Defs.spec(era).terrain == Terrain.INDUSTRIAL)
                Color.argb(110, 90, 85, 80) else Color.argb(150, 255, 255, 255)
            val span = w + 400f * g.ui.dp
            val wind = if (weather == Weather.WIND) 27f * b.windDirection() else 0f
            fill.color = cloud
            for (i in 0 until 5) {
                val x = ((i * 397f + time * (8f + i * 3f + wind) * g.ui.dp) %
                    span + span) % span - 200f * g.ui.dp
                val y = groundY * (0.12f + 0.09f * (i % 3))
                val s = (40f + 18f * (i % 3)) * g.ui.dp
                c.drawCircle(x, y, s * 0.6f, fill)
                c.drawCircle(x + s * 0.6f, y + s * 0.1f, s * 0.5f, fill)
                c.drawCircle(x - s * 0.6f, y + s * 0.15f, s * 0.45f, fill)
                rect.set(x - s, y, x + s, y + s * 0.4f)
                c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
            }
        }
    }

    private fun ensure(w: Float, groundY: Float) {
        val era = b.player.era
        val variant = b.player.eraVariant
        val art = Defs.sideArt(era, variant)
        val layers = g.quality.parallaxLayers
        if (cachedEra == era && cachedVariant == variant && cachedArt == art &&
            cachedPalette == Palette.selected && cachedW == w &&
            cachedGround == groundY && cachedLayers == layers) return

        cachedEra = era
        cachedVariant = variant
        cachedArt = art
        cachedPalette = Palette.selected
        cachedW = w
        cachedGround = groundY
        cachedLayers = layers
        val colors = palette()
        for (i in 0..2) {
            if (i < 3 - layers) {
                pictures[i] = null
                continue
            }
            val picture = Picture()
            val rc = picture.beginRecording(TILE.toInt(), BOTTOM.toInt())
            record(rc, art, i, colors)
            picture.endRecording()
            pictures[i] = picture
        }
        cacheBuilds++
    }

    fun drawLandscape(c: Canvas, w: Float, groundY: Float, camX: Float, k: Float) {
        ensure(w, groundY)
        lastDrawnLayers = 0
        if (cachedLayers == 3) drawLayer(c, pictures[0], w, groundY, camX, k, 0.14f)
        if (cachedLayers >= 2) drawLayer(c, pictures[1], w, groundY, camX, k, 0.36f)
        drawLayer(c, pictures[2], w, groundY, camX, k, 0.70f)
    }

    private fun drawLayer(
        c: Canvas, picture: Picture?, w: Float, groundY: Float,
        camX: Float, k: Float, parallax: Float,
    ) {
        if (picture == null) return
        lastDrawnLayers++
        val offset = camX * parallax
        val first = floor(offset / TILE).toInt() - 1
        val last = floor((offset + w / k) / TILE).toInt() + 1
        var i = first
        while (i <= last) {
            c.save()
            c.translate((i * TILE - offset) * k, groundY - BOTTOM * k)
            c.scale(k, k)
            c.drawPicture(picture)
            c.restore()
            i++
        }
    }

    private fun record(c: Canvas, art: EraArt, layer: Int, colors: IntArray) {
        val skyColor = colors[1]
        val saturation = when {
            Palette.highContrast -> 0.68f
            Palette.selected != Palette.STANDARD -> 0.55f
            else -> 0.37f
        }
        val fade = (if (layer == 0) 0.65f else if (layer == 1) 0.47f else 0.28f) +
            saturation * 0.25f
        val body = Ui.blend(colors[layer + 2], skyColor, fade)
        val shadow = Ui.blend(body, 0xFF29303A.toInt(), if (layer == 2) 0.24f else 0.13f)
        val edge = Ui.blend(shadow, skyColor, if (layer == 2) 0.25f else 0.42f)
        val trim = Ui.blend(colors[4], skyColor, (fade + 0.18f).coerceAtMost(0.91f))
        val floorY = if (layer == 0) 345f else if (layer == 1) 365f else 385f
        // Jeden wspólny poziom na granicach kafli: nie widać szwów podczas przesuwania.
        val ridgeHeight = if (layer == 0) 36f else if (layer == 1) 21f else 10f
        path.rewind()
        path.moveTo(0f, BOTTOM)
        path.lineTo(0f, floorY - ridgeHeight)
        for (i in 1..8) {
            val bump = if (i % 4 == 0) 1f else if (i % 3 == 0) 0.3f else 0.65f
            path.lineTo(i * 80f, floorY - ridgeHeight * bump)
        }
        path.lineTo(TILE, BOTTOM)
        path.close()
        solidPath(c, body)

        val scale = if (layer == 0) 0.72f else if (layer == 1) 0.91f else 1.10f
        monument(c, art, 140f, floorY, scale, false, body, shadow, edge, trim)
        monument(c, art, 458f, floorY, scale * 0.83f, true, body, shadow, edge, trim)
    }

    private fun solidPath(c: Canvas, color: Int) {
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun polygon(c: Canvas, color: Int, edge: Int, vararg points: Float) {
        path.rewind()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
        path.close()
        solidPath(c, color)
        outline.color = edge
        c.drawPath(path, outline)
    }

    private fun block(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, edge: Int) {
        fill.color = color
        c.drawRect(l, t, r, b, fill)
        outline.color = edge
        c.drawRect(l, t, r, b, outline)
    }

    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, edge: Int) {
        rect.set(l, t, r, b)
        fill.color = color
        c.drawRoundRect(rect, radius, radius, fill)
        outline.color = edge
        c.drawRoundRect(rect, radius, radius, outline)
    }

    private fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int, edge: Int) {
        fill.color = color
        c.drawCircle(x, y, radius, fill)
        outline.color = edge
        c.drawCircle(x, y, radius, outline)
    }

    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, edge: Int, width: Float = 4f) {
        outline.color = edge
        outline.strokeWidth = width
        c.drawLine(x, y, xx, yy, outline)
        outline.strokeWidth = 3f
    }

    /** Dwa motywy na kafel; każdy wariant otrzymuje własny znak rozpoznawczy. */
    private fun monument(
        c: Canvas, art: EraArt, x: Float, y: Float, scale: Float, second: Boolean,
        body: Int, shadow: Int, edge: Int, trim: Int,
    ) {
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        when (art) {
            EraArt.TRIBAL -> {
                if (!second) {
                    polygon(c, shadow, edge, -45f, 0f, -31f, -135f, -5f, -156f,
                        20f, -112f, 35f, -10f, 46f, 0f)
                    polygon(c, trim, edge, -76f, 0f, -64f, -62f, -47f, -78f, -29f, 0f)
                } else {
                    line(c, 0f, 0f, 0f, -106f, edge, 5f)
                    for (i in 0..4) {
                        val yy = -32f - i * 15f
                        line(c, 0f, yy, -28f - i * 3f, yy - 18f, shadow, 4f)
                        line(c, 0f, yy, 28f + i * 3f, yy - 18f, shadow, 4f)
                    }
                    circle(c, 0f, -110f, 9f, body, edge)
                }
            }
            EraArt.BRONZE -> {
                if (!second) {
                    block(c, -93f, -36f, 93f, 0f, shadow, edge)
                    block(c, -74f, -69f, 74f, -36f, body, edge)
                    block(c, -51f, -101f, 51f, -69f, shadow, edge)
                    block(c, -26f, -128f, 26f, -101f, body, edge)
                    block(c, -10f, -18f, 10f, 0f, trim, edge)
                } else {
                    polygon(c, body, edge, -21f, 0f, -15f, -124f, 0f, -150f, 15f, -124f, 21f, 0f)
                    circle(c, 46f, -123f, 19f, trim, edge)
                }
            }
            EraArt.IRON -> {
                if (!second) {
                    for (i in -4..4) {
                        val xx = i * 19f
                        polygon(c, if (i % 2 == 0) shadow else body, edge,
                            xx - 8f, 0f, xx - 8f, -92f, xx, -113f, xx + 8f, -92f, xx + 8f, 0f)
                    }
                    line(c, -84f, -44f, 84f, -44f, edge, 6f)
                } else {
                    block(c, -29f, -114f, -17f, 0f, body, edge)
                    block(c, 19f, -114f, 31f, 0f, body, edge)
                    block(c, -40f, -129f, 40f, -108f, shadow, edge)
                    polygon(c, trim, edge, -48f, -129f, 0f, -159f, 48f, -129f)
                }
            }
            EraArt.FEUDAL -> {
                if (!second) {
                    block(c, -82f, -88f, 82f, 0f, body, edge)
                    block(c, -91f, -116f, -51f, 0f, shadow, edge)
                    block(c, 51f, -116f, 91f, 0f, shadow, edge)
                    for (i in 0..5) block(c, -87f + i * 31f, -126f, -71f + i * 31f, -105f, trim, edge)
                    round(c, -14f, -47f, 14f, 0f, 13f, shadow, edge)
                } else {
                    polygon(c, body, edge, -35f, 0f, -20f, -125f, 20f, -125f, 35f, 0f)
                    polygon(c, trim, edge, -27f, -125f, 0f, -153f, 27f, -125f)
                    circle(c, 0f, -112f, 7f, shadow, edge)
                    line(c, 0f, -112f, -55f, -155f, edge, 9f)
                    line(c, 0f, -112f, 55f, -155f, edge, 9f)
                    line(c, 0f, -112f, -55f, -69f, edge, 9f)
                    line(c, 0f, -112f, 55f, -69f, edge, 9f)
                }
            }
            EraArt.POWDER -> {
                if (!second) {
                    block(c, -103f, -61f, 103f, 0f, body, edge)
                    polygon(c, shadow, edge, -119f, -61f, -81f, -95f, 82f, -95f, 119f, -61f)
                    for (i in -2..2) block(c, i * 37f - 9f, -88f, i * 37f + 9f, -70f, trim, edge)
                } else {
                    polygon(c, shadow, edge, -96f, -21f, 95f, -21f, 62f, 0f, -67f, 0f)
                    line(c, 0f, -21f, 0f, -175f, edge, 5f)
                    polygon(c, body, edge, 4f, -162f, 4f, -27f, 82f, -37f)
                    polygon(c, trim, edge, -5f, -150f, -5f, -38f, -68f, -42f)
                }
            }
            EraArt.STEAM -> {
                block(c, -100f, -75f, 105f, 0f, body, edge)
                for (i in 0..3) {
                    val xx = -80f + i * 48f
                    polygon(c, shadow, edge, xx, -75f, xx + 25f, -91f, xx + 48f, -75f)
                }
                block(c, if (second) 30f else -55f, -176f,
                    if (second) 54f else -31f, -66f, shadow, edge)
                block(c, if (second) 19f else -66f, -180f,
                    if (second) 65f else -20f, -163f, trim, edge)
                if (!second) for (i in 0..2)
                    circle(c, -29f + i * 17f, -203f - (i % 2) * 17f, 20f, trim, edge)
            }
            EraArt.STEEL -> {
                if (!second) {
                    block(c, -83f, -99f, 64f, 0f, body, edge)
                    for (i in 0..4) for (j in 0..2)
                        block(c, -65f + j * 43f, -90f + i * 18f, -45f + j * 43f,
                            -83f + i * 18f, shadow, edge)
                } else {
                    line(c, -70f, 0f, -55f, -196f, edge, 7f)
                    line(c, -55f, -196f, 91f, -196f, edge, 6f)
                    line(c, -55f, -196f, 12f, -132f, edge, 4f)
                    line(c, 91f, -196f, 91f, -122f, edge, 3f)
                    block(c, 78f, -122f, 101f, -103f, trim, edge)
                }
            }
            EraArt.SILICON -> {
                if (!second) {
                    round(c, -72f, -199f, 15f, 0f, 12f, body, edge)
                    block(c, 15f, -142f, 78f, 0f, shadow, edge)
                    for (i in 0..6) for (j in 0..2)
                        block(c, -56f + j * 23f, -180f + i * 24f,
                            -46f + j * 23f, -169f + i * 24f, trim, edge)
                } else {
                    line(c, 0f, 0f, 0f, -168f, edge, 6f)
                    polygon(c, body, edge, -60f, -111f, 0f, -147f, 60f, -111f, 0f, -126f)
                    circle(c, 0f, -168f, 10f, trim, edge)
                    line(c, -48f, -100f, 48f, -100f, edge, 4f)
                }
            }
            EraArt.ORBITAL -> {
                if (!second) {
                    round(c, -104f, -105f, 104f, 25f, 97f, body, edge)
                    block(c, -110f, -34f, 110f, 0f, shadow, edge)
                    for (i in -2..2) round(c, i * 34f - 10f, -32f, i * 34f + 10f, -5f,
                        9f, trim, edge)
                } else {
                    circle(c, 2f, -160f, 39f, trim, edge)
                    line(c, -58f, -145f, 62f, -174f, shadow, 8f)
                    block(c, -68f, -39f, 68f, 0f, body, edge)
                    round(c, -36f, -70f, 36f, -20f, 23f, shadow, edge)
                }
            }
            EraArt.COMBUSTION -> {
                if (!second) {
                    round(c, -90f, -87f, -25f, 0f, 13f, body, edge)
                    round(c, -7f, -126f, 76f, 0f, 14f, shadow, edge)
                    line(c, -90f, -47f, 76f, -47f, edge, 6f)
                    block(c, 83f, -161f, 102f, 0f, body, edge)
                } else {
                    line(c, -72f, 0f, -10f, -179f, edge, 6f)
                    line(c, -10f, -179f, 72f, -4f, edge, 6f)
                    for (i in 0..4)
                        line(c, -60f + i * 30f, -28f - i % 2 * 22f,
                            44f - i * 11f, -65f - i * 21f, trim, 3f)
                    block(c, -78f, -30f, 80f, 0f, shadow, edge)
                }
            }
            EraArt.GALVANIC -> {
                line(c, -57f, 0f, -25f, -187f, edge, 6f)
                line(c, 57f, 0f, 25f, -187f, edge, 6f)
                line(c, -25f, -187f, 25f, -187f, edge, 5f)
                for (i in 0..4) {
                    val y = -28f - i * 32f
                    line(c, -52f + i * 5f, y, 52f - i * 5f, y, shadow, 4f)
                    line(c, -52f + i * 5f, y, 25f - i * 2f, y - 30f, edge, 3f)
                }
                circle(c, 0f, if (second) -130f else -84f, 24f, trim, edge)
                circle(c, 0f, if (second) -130f else -84f, 11f, shadow, edge)
            }
            EraArt.SWARM -> {
                if (!second) {
                    polygon(c, shadow, edge, -85f, 0f, -61f, -109f,
                        -10f, -186f, 31f, -123f, 81f, 0f)
                    for (i in 0..3) {
                        val yy = -31f - i * 29f
                        polygon(c, trim, edge, -64f + i * 8f, yy,
                            -90f, yy - 16f, -47f + i * 8f, yy - 25f)
                    }
                } else {
                    round(c, -62f, -103f, 62f, 0f, 48f, body, edge)
                    for (i in -1..1) {
                        circle(c, i * 33f, -70f, 18f, shadow, edge)
                        polygon(c, trim, edge, i * 33f - 13f, -98f, i * 33f, -143f,
                            i * 33f + 13f, -98f)
                    }
                }
            }
            EraArt.BIOTECH -> {
                if (!second) {
                    line(c, 0f, 0f, 0f, -161f, edge, 14f)
                    circle(c, -36f, -105f, 34f, body, edge)
                    circle(c, 44f, -131f, 37f, shadow, edge)
                    circle(c, 0f, -176f, 31f, trim, edge)
                    line(c, 0f, -97f, -36f, -105f, edge, 8f)
                    line(c, 0f, -130f, 44f, -131f, edge, 8f)
                } else {
                    polygon(c, body, edge, -89f, 0f, -48f, -85f, 0f, -115f, 48f, -85f, 89f, 0f)
                    for (i in -2..2) {
                        circle(c, i * 31f, -49f - (2 - kotlin.math.abs(i)) * 14f,
                            17f, if (i % 2 == 0) trim else shadow, edge)
                    }
                }
            }
        }
        c.restore()
    }

    /** Podłoże pozostaje związane z kamerą świata, a nie z paralaksą dekoracji. */
    fun drawGround(c: Canvas, w: Float, h: Float, groundY: Float, camX: Float, k: Float) {
        val era = b.player.era
        val colors = palette()
        val ground = colors[5]
        fill.color = ground
        c.drawRect(-8f * g.ui.dp, groundY, w + 8f * g.ui.dp, h + 8f * g.ui.dp, fill)
        fill.color = Ui.blend(ground, Color.WHITE, 0.15f)
        c.drawRect(0f, groundY, w, groundY + 5f * k, fill)
        val step = 90f
        val first = (camX / step).toInt() - 1
        val count = (w / (step * k)).toInt() + 3
        val dark = Ui.blend(ground, Color.BLACK, 0.2f)
        val terrain = Defs.spec(era).terrain
        for (i in first..first + count) {
            val hash = (((i * 4049 + 13) and 0x7fffffff) % 1000) / 1000f
            val x = (i * step + hash * 50f - camX) * k
            val y = groundY + (10f + hash * 40f) * k
            if (y > h) continue
            if (terrain == Terrain.PAVED_HILLS) {
                val bx = (i * step - camX) * k
                fill.color = if (i % 2 == 0) 0xFFAAA28F.toInt() else 0xFF978E80.toInt()
                c.drawRect(bx, groundY + 12f * k, bx + 68f * k, groundY + 22f * k, fill)
                fill.color = 0xFF756E62.toInt()
                c.drawRect(bx + 12f * k, groundY + 24f * k, bx + 70f * k, groundY + 30f * k, fill)
            }
            fill.color = dark
            c.drawCircle(x, y, (2f + hash * 3f) * k, fill)
        }
    }
}
