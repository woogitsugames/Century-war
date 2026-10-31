package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.centurywar.R
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Defs
import com.centurywar.game.FxBuffer
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.game.Weather
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.util.Properties

/**
 * Autentyczne ekrany i sceny renderera do Google Play.
 * Test kosztowny: domyślnie pomijany; STORE_GRAPHICS=true włącza eksport.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StoreGraphicsTest {
    private data class Device(val name: String, val width: Int, val height: Int, val density: Float)

    private val devices = listOf(
        Device("phone", 1920, 1080, 2f),
        Device("tablet7", 1920, 1200, 1.8f),
        Device("tablet10", 2560, 1600, 2f),
    )
    private val names = arrayOf(
        "frontline", "barrage", "endless-boss", "night", "era-variants",
        "campaign", "research", "daily-challenge",
    )
    private val roles = Role.values()

    @Test
    fun generate() {
        assumeTrue("Generator tylko przy STORE_GRAPHICS=true", System.getenv("STORE_GRAPHICS") == "true")
        val output = File("build/store").apply { mkdirs() }
        val previousLanguage = L.lang
        try {
            for (language in listOf("pl", "en")) {
                val strings = captions(language)
                val langDir = File(output, language).apply { mkdirs() }
                val manifest = StringBuilder("file\twidth\theight\talt\n")
                for (device in devices) {
                    val g = game(device, language)
                    val folder = File(langDir, device.name).apply { mkdirs() }
                    for (scene in 1..8) {
                        val name = scene.toString().padStart(2, '0') + "-" + names[scene - 1] + ".png"
                        val image = File(folder, name)
                        writePng(image, device.width, device.height, false) { canvas ->
                            when (scene) {
                                in 1..4 -> drawBattle(canvas, g, scene, device.width, device.height)
                                5 -> {
                                    val steam = 5
                                    check(Defs.spec(steam).variants.size == 2)
                                    g.setScreen(BattleScreen(g, BattleConfig(
                                        Mode.SKIRMISH, 5050L, 1f, startEra = steam,
                                        maxEra = Defs.ERA_COUNT - 1,
                                    )))
                                    g.frame(canvas, 0f)
                                }
                                6 -> {
                                    g.setScreen(CampaignScreen(g))
                                    g.frame(canvas, 0f)
                                }
                                7 -> {
                                    g.setScreen(ResearchScreen(g))
                                    g.frame(canvas, 0f)
                                }
                                8 -> {
                                    g.setScreen(ModeScreen(g, Mode.DAILY))
                                    g.frame(canvas, 0f)
                                }
                            }
                            drawCaption(canvas, strings.getProperty("caption." + scene), scene,
                                device.width.toFloat(), device.height.toFloat())
                        }
                        checkPng(image, device.width, device.height, false)
                        manifest.append(device.name).append('/').append(name).append('\t')
                            .append(device.width).append('\t').append(device.height).append('\t')
                            .append(strings.getProperty("alt." + scene)).append('\n')
                    }
                }
                val feature = File(langDir, "feature_1024x500.png")
                writePng(feature, 1024, 500, false) { c ->
                    val g = game(Device("feature", 1024, 500, 1f), language)
                    drawBattle(c, g, 1, 1024, 500, true)
                    drawFeatureTitle(c)
                }
                checkPng(feature, 1024, 500, false)
                manifest.append("feature_1024x500.png\t1024\t500\t")
                    .append(strings.getProperty("alt.feature")).append('\n')

                val icon = File(langDir, "icon_512.png")
                writePng(icon, 512, 512, true) { c -> drawIcon(c) }
                checkPng(icon, 512, 512, true)
                assertTrue("Ikona Google Play musi miec <= 1024 KB", icon.length() <= 1024L * 1024L)
                manifest.append("icon_512.png\t512\t512\t")
                    .append(strings.getProperty("alt.icon")).append('\n')
                File(langDir, "manifest.tsv").writeText(manifest.toString(), Charsets.UTF_8)
            }
        } finally {
            L.lang = previousLanguage
        }
    }

    private fun captions(language: String): Properties {
        val name = "/store/graphics_" + language + ".properties"
        val stream = javaClass.getResourceAsStream(name) ?: error("Brak napisow: " + name)
        val props = Properties()
        InputStreamReader(stream, Charsets.UTF_8).use { props.load(it) }
        for (i in 1..8) {
            check(!props.getProperty("caption." + i).isNullOrBlank())
            check(!props.getProperty("alt." + i).isNullOrBlank())
        }
        check(!props.getProperty("alt.feature").isNullOrBlank())
        check(!props.getProperty("alt.icon").isNullOrBlank())
        return props
    }

    private fun game(device: Device, language: String): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val tmp = File.createTempFile("store-profile-", ".json").apply { deleteOnExit() }
        val profile = Profile(tmp).apply {
            lang = language
            tutorialDone = true
            hintsEnabled = false
            screenShake = 0
            showFps = false
            quality = Quality.HIGH
            medals = 2400
            val progress = intArrayOf(3, 3, 2, 3, 2, 1)
            for (i in progress.indices) stars[i] = progress[i]
            levels["r_unitDmg"] = 4
            levels["r_unitHp"] = 3
            levels["r_income"] = 2
            levels["r_train"] = 2
        }
        val g = Game(host, profile, Quality.HIGH)
        g.density = device.density
        g.resize(device.width, device.height, device.width, device.height)
        // Zniknie powiadomienie o premii logowania, zanim powstanie pierwszy kadr.
        val tiny = Canvas(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        repeat(220) { g.frame(tiny, 1f / 60f) }
        g.onLanguageChanged()
        return g
    }

    private fun writePng(
        file: File, width: Int, height: Int, withAlpha: Boolean, draw: (Canvas) -> Unit,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(27, 42, 65))
            draw(Canvas(bitmap))
            bitmap.setHasAlpha(withAlpha)
            file.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Nie mozna zapisac: " + file.path
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Sprawdzamy tez RGB/RGBA w naglowku PNG, a nie tylko wymiary. */
    private fun checkPng(file: File, width: Int, height: Int, withAlpha: Boolean) {
        assertTrue("Pusty PNG: " + file.path, file.length() > 1024L)
        val bytes = ByteArray(26)
        DataInputStream(file.inputStream()).use { it.readFully(bytes) }
        assertEquals("Sygnatura PNG", 137, bytes[0].toInt() and 255)
        assertEquals("Szerokosc: " + file.name, width, ByteBuffer.wrap(bytes, 16, 4).int)
        assertEquals("Wysokosc: " + file.name, height, ByteBuffer.wrap(bytes, 20, 4).int)
        assertEquals("8 bitow na kanal", 8, bytes[24].toInt() and 255)
        assertEquals("PNG RGB lub RGBA", if (withAlpha) 6 else 2, bytes[25].toInt() and 255)
    }

    /** Jednostki są w ich własnej symulacji, w ustalonych pozycjach do fotografii. */
    private fun sceneBattle(scene: Int): Battle {
        val era = when (scene) { 1 -> 2; 2 -> 6; 3 -> 4; 4 -> 8; else -> 2 }
        val weather = when (scene) { 2 -> Weather.RAIN; 4 -> Weather.NIGHT; else -> Weather.CLEAR }
        val mode = if (scene == 3) Mode.ENDLESS else Mode.SKIRMISH
        val battle = Battle(BattleConfig(
            mode, 50000L + scene, 1f, startEra = era, maxEra = Defs.ERA_COUNT - 1,
            weather = weather, variantsEnabled = false,
        ))
        for (side in battle.sides) {
            side.turretEra[0] = era
            for (i in 0 until 7) {
                val role = roles[i % roles.size]
                check(battle.spawn(side, side.unit(role)))
                val unit = battle.units.first { it.active && it.serial == battle.serialCounter }
                unit.x = if (side.index == 0) 1120f + i * 65f else 1690f - i * 65f
                unit.prevX = unit.x
                unit.anim = i * 0.7f
                unit.prevAnim = unit.anim
                unit.stride = if (i % 2 == 0) 0.65f else 0.1f
                unit.prevStride = unit.stride
                unit.attackAnim = if (i % 3 == 0) 0.6f else 0.1f
                unit.prevAttack = unit.attackAnim
                unit.moving = i % 2 == 0
                unit.hp = unit.maxHp * (if (i % 3 == 0) 0.72f else 1f)
            }
        }

        when (scene) {
            1 -> {
                battle.fx.add(FxBuffer.HIT, 1360f, 48f, 1)
                battle.fx.add(FxBuffer.EXPLOSION, 1440f, 38f, 0, 24f)
            }
            2 -> {
                val commands = BattleController(battle)
                check(commands.barrage(1430f))
                repeat(27) { battle.step() }
                battle.fx.add(FxBuffer.EXPLOSION, 1440f, 65f, 1, 45f)
            }
            3 -> {
                battle.wave = 10
                battle.waveTimer = 120f
                battle.step()
                val boss = battle.currentBoss() ?: error("Nie ma bossa Endless")
                boss.x = 1485f
                boss.prevX = boss.x
                boss.attackAnim = 0.65f
                battle.fx.add(FxBuffer.EXPLOSION, 1430f, 40f, 1, 24f)
            }
            4 -> {
                battle.fx.add(FxBuffer.MUZZLE, 1360f, 55f, 0, 0f)
                battle.fx.add(FxBuffer.HIT, 1430f, 60f, 1)
            }
        }
        return battle
    }

    private fun drawBattle(c: Canvas, g: Game, scene: Int, width: Int, height: Int, feature: Boolean = false) {
        val battle = sceneBattle(scene)
        val renderer = BattleRenderer(g, battle)
        renderer.layout(width.toFloat(), height.toFloat(),
            if (feature) 12f else 92f * g.ui.dp,
            if (feature) 10f else 20f * g.ui.dp)
        renderer.camX = Defs.LANE * 0.5f - renderer.worldVisible * 0.5f
        renderer.clampCam()
        renderer.lerp = 1f
        repeat(3) { renderer.render(c, 1f / 60f, width.toFloat(), height.toFloat()) }
    }

    /** Czytelna nakladka reklamowa, poza kluczowym obszarem gry. */
    private fun drawCaption(c: Canvas, caption: String, scene: Int, width: Float, height: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = minOf(52f, width * 0.032f, height * 0.077f)
        }
        val maxWidth = width * 0.79f
        if (p.measureText(caption) > maxWidth) p.textSize *= maxWidth / p.measureText(caption)
        val left = width * 0.047f
        val top = height * (if (scene <= 4) 0.125f else 0.84f)
        val rect = RectF(left, top, left + p.measureText(caption) + 72f,
            top + p.textSize * 1.65f)
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(226, 23, 34, 52) }
        c.drawRoundRect(rect, 15f, 15f, panel)
        panel.style = Paint.Style.STROKE
        panel.strokeWidth = 3f
        panel.color = Color.rgb(225, 168, 67)
        c.drawRoundRect(rect, 15f, 15f, panel)
        panel.style = Paint.Style.FILL
        c.drawRect(rect.left + 17f, rect.top + 13f, rect.left + 23f, rect.bottom - 13f, panel)
        p.color = Color.rgb(252, 247, 233)
        c.drawText(caption, rect.left + 36f, rect.centerY() + p.textSize * 0.34f, p)
    }

    /** Obie warstwy tej samej ikony, których używa launcher Androida. */
    private fun drawIcon(c: Canvas) {
        val resources = RuntimeEnvironment.getApplication().resources
        val background = resources.getDrawable(R.drawable.ic_launcher_background, null)
        val foreground = resources.getDrawable(R.drawable.ic_launcher_foreground, null)
        background.setBounds(0, 0, 512, 512)
        foreground.setBounds(0, 0, 512, 512)
        background.draw(c)
        foreground.draw(c)
    }

    /** Tylko napis Century War: scena i kolory są z właściwego renderera gry. */
    private fun drawFeatureTitle(c: Canvas) {
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 740f, 0f,
                intArrayOf(0xFA172339.toInt(), 0xDA172339.toInt(), 0x00172339),
                null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 740f, 500f, shade)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.rgb(18, 24, 38)
        }
        val text = Paint(outline).apply { style = Paint.Style.FILL; color = Color.rgb(246, 202, 93) }
        outline.textSize = 71f
        text.textSize = 71f
        c.drawText("CENTURY", 52f, 228f, outline)
        c.drawText("CENTURY", 52f, 228f, text)
        outline.textSize = 94f
        text.textSize = 94f
        c.drawText("WAR", 52f, 324f, outline)
        c.drawText("WAR", 52f, 324f, text)
        val trim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(240, 177, 65)
            strokeWidth = 5f
        }
        c.drawLine(52f, 348f, 385f, 348f, trim)
    }
}
