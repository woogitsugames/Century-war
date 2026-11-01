package com.centurywar.game.art

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.centurywar.engine.Ui
import com.centurywar.meta.BannerCatalog

/**
 * Dwanaście wzorów sztandarów. Wspólne płótno pozostaje niebieskie, a godła
 * są tylko ozdobą. Pędzle i ścieżki są współdzielone przez instancję.
 */
class Banners {
    private val cloth = Path()
    private val motif = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Pen.OUTLINE
    }
    private val accent = 0xFFFFD77D.toInt()
    private val ivory = 0xFFFFF5D7.toInt()

    /** (x,y) to początek płótna przy maszcie; falowanie jest w jednostkach pola bitwy. */
    fun draw(
        c: Canvas, id: Int, x: Float, y: Float, width: Float, height: Float,
        direction: Float = 1f, wave: Float = 0f, wave2: Float = 0f,
        team: Int = Ui.PLAYER,
    ) {
        val number = id.coerceIn(0, BannerCatalog.COUNT - 1)
        if (width <= 0f || height <= 0f) return
        c.save()
        c.translate(x, y)
        c.scale((if (direction < 0f) -1f else 1f) * width / 100f, height / 60f)
        val tip = (wave / height * 60f).coerceIn(-18f, 18f)
        val mid = (wave2 / height * 60f).coerceIn(-18f, 18f)
        cloth.reset()
        cloth.moveTo(0f, 0f)
        cloth.quadTo(48f, mid - 6f, 100f, tip + 5f)
        cloth.lineTo(100f, tip + 59f)
        cloth.quadTo(48f, mid + 54f, 0f, 60f)
        cloth.close()
        fill.color = team
        c.drawPath(cloth, fill)

        // Wzory są umieszczane pod konturem, zawsze zajmują mniejszą część płótna.
        c.save()
        c.clipPath(cloth)
        val lighter = Pen.light(team, 0.34f)
        val darker = Pen.dark(team, 0.24f)
        fill.color = lighter
        when (number % 4) {
            0 -> {
                c.drawRect(10f, -10f, 24f, 75f, fill)
                c.drawRect(78f, -10f, 89f, 75f, fill)
            }
            1 -> {
                fill.color = darker
                c.drawRect(0f, -5f, 17f, 70f, fill)
                fill.color = lighter
                c.drawRect(84f, -5f, 100f, 70f, fill)
            }
            2 -> {
                motif.reset()
                motif.moveTo(0f, 9f); motif.lineTo(36f, 30f); motif.lineTo(0f, 51f)
                motif.lineTo(0f, 66f); motif.lineTo(54f, 30f); motif.lineTo(0f, -6f)
                motif.close()
                c.drawPath(motif, fill)
            }
            else -> {
                c.drawRect(0f, 6f, 102f, 16f, fill)
                c.drawRect(0f, 44f, 102f, 54f, fill)
            }
        }
        c.restore()
        // Jasne godło z ciemnym obrysem jest czytelne nawet w rankingu.
        drawEmblem(c, number)
        c.drawPath(cloth, outline)
        c.restore()
    }

    private fun emblemPath(c: Canvas, color: Int = accent) {
        fill.color = color
        c.drawPath(motif, fill)
        c.drawPath(motif, outline)
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int = ivory) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, outline)
    }

    private fun bar(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int = ivory) {
        outline.strokeWidth = 7f
        outline.color = Pen.OUTLINE
        c.drawLine(x1, y1, x2, y2, outline)
        outline.strokeWidth = 4f
        outline.color = color
        c.drawLine(x1, y1, x2, y2, outline)
        outline.strokeWidth = 3.5f
        outline.color = Pen.OUTLINE
    }

    private fun drawEmblem(c: Canvas, id: Int) {
        motif.reset()
        when (id) {
            0 -> {
                dot(c, 52f, 30f, 13f, accent)
                dot(c, 52f, 30f, 4f, ivory)
            }
            1 -> {
                motif.moveTo(37f, 13f); motif.lineTo(65f, 30f)
                motif.lineTo(37f, 47f); motif.lineTo(46f, 30f); motif.close()
                emblemPath(c)
            }
            2 -> {
                motif.moveTo(52f, 10f); motif.lineTo(70f, 30f)
                motif.lineTo(52f, 50f); motif.lineTo(34f, 30f); motif.close()
                emblemPath(c)
                dot(c, 52f, 30f, 5f)
            }
            3 -> {
                motif.moveTo(36f, 47f); motif.lineTo(36f, 22f)
                motif.lineTo(43f, 22f); motif.lineTo(43f, 14f)
                motif.lineTo(51f, 14f); motif.lineTo(51f, 22f)
                motif.lineTo(58f, 22f); motif.lineTo(58f, 14f)
                motif.lineTo(66f, 14f); motif.lineTo(66f, 47f); motif.close()
                emblemPath(c)
                dot(c, 51f, 36f, 4f, Pen.OUTLINE)
            }
            4 -> {
                motif.moveTo(52f, 9f); motif.lineTo(59f, 23f)
                motif.lineTo(75f, 25f); motif.lineTo(63f, 36f)
                motif.lineTo(66f, 52f); motif.lineTo(52f, 44f)
                motif.lineTo(38f, 52f); motif.lineTo(41f, 36f)
                motif.lineTo(29f, 25f); motif.lineTo(45f, 23f); motif.close()
                emblemPath(c)
            }
            5 -> {
                bar(c, 36f, 14f, 68f, 46f)
                bar(c, 68f, 14f, 36f, 46f)
                dot(c, 52f, 30f, 6f, accent)
            }
            6 -> {
                dot(c, 52f, 30f, 15f, accent)
                motif.moveTo(52f, 18f); motif.lineTo(63f, 30f)
                motif.lineTo(52f, 42f); motif.lineTo(41f, 30f); motif.close()
                emblemPath(c, ivory)
            }
            7 -> {
                motif.moveTo(32f, 22f); motif.lineTo(48f, 25f)
                motif.lineTo(52f, 11f); motif.lineTo(56f, 25f)
                motif.lineTo(72f, 22f); motif.lineTo(62f, 39f)
                motif.lineTo(52f, 49f); motif.lineTo(42f, 39f); motif.close()
                emblemPath(c)
            }
            8 -> {
                motif.moveTo(56f, 9f); motif.lineTo(35f, 35f)
                motif.lineTo(50f, 35f); motif.lineTo(44f, 51f)
                motif.lineTo(73f, 23f); motif.lineTo(56f, 23f); motif.close()
                emblemPath(c)
            }
            9 -> {
                dot(c, 50f, 30f, 16f, accent)
                fill.color = Pen.dark(Ui.PLAYER, 0.07f)
                c.drawCircle(58f, 24f, 14f, fill)
                dot(c, 38f, 19f, 3f)
            }
            10 -> {
                motif.moveTo(52f, 9f); motif.lineTo(73f, 18f)
                motif.lineTo(69f, 40f); motif.lineTo(52f, 53f)
                motif.lineTo(35f, 40f); motif.lineTo(31f, 18f); motif.close()
                emblemPath(c)
                bar(c, 43f, 31f, 61f, 31f, Pen.OUTLINE)
            }
            11 -> {
                motif.moveTo(34f, 43f); motif.lineTo(30f, 18f)
                motif.lineTo(42f, 29f); motif.lineTo(52f, 12f)
                motif.lineTo(62f, 29f); motif.lineTo(74f, 18f)
                motif.lineTo(70f, 43f); motif.close()
                emblemPath(c)
                bar(c, 36f, 46f, 68f, 46f, accent)
                dot(c, 52f, 34f, 4f, ivory)
            }
        }
    }
}
