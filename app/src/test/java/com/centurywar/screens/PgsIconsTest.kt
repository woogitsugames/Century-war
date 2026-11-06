package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.centurywar.engine.Icons
import com.centurywar.engine.Ui
import com.centurywar.meta.AchievementCategory
import com.centurywar.meta.Achievements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot

/**
 * R9: ikony 512×512 osiągnięć i rankingów Play Games, rysowane kodem w stylu gry (bez tekstu i cyfr).
 *
 * Bez flagi test sprawdza kilka ikon i kompletność mapowania. `PGS_ICONS=true` zapisuje wszystkie
 * 83 + 5 ikon do `build/pgs-icons/` (z `AchievementsIconsMappings.csv` i stykówką), sprawdza je
 * wszystkie i kopiuje stykówki oraz mapowanie do `docs/store/assets/pgs/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PgsIconsTest {
    /** Symbol, stopień serii (0 = pojedyncze, 1–4 = brąz, srebro, złoto, platyna), znaczek w rogu. */
    private data class Spec(val symbol: Int, val tier: Int = 0, val badge: Int = -1)

    /** Jedna tabela id → symbol. Stopnie serii: gwiazdki i kolor obwódki, bez cyfr. */
    private val specs: Map<String, Spec> = linkedMapOf(
        "win_1" to Spec(Icons.CROSSED_SWORDS, 1),
        "win_10" to Spec(Icons.CROSSED_SWORDS, 2),
        "win_100" to Spec(Icons.CROSSED_SWORDS, 3),
        "win_500" to Spec(Icons.CROSSED_SWORDS, 4),
        "battles_25" to Spec(Icons.FLAG, 1),
        "battles_200" to Spec(Icons.FLAG, 2),
        "battles_1000" to Spec(Icons.FLAG, 3),
        "stars_24" to Spec(Icons.STAR, 1),
        "stars_48" to Spec(Icons.STAR, 2),
        "stars_96" to Spec(Icons.STAR, 3),
        "stages_3" to Spec(Icons.MAP, 1),
        "stages_16" to Spec(Icons.MAP, 2),
        "stages_32" to Spec(Icons.MAP, 3),
        "wave_10" to Spec(Icons.WALL, 1),
        "wave_25" to Spec(Icons.WALL, 2),
        "wave_50" to Spec(Icons.WALL, 3),
        "wave_100" to Spec(Icons.WALL, 4),
        "orbital" to Spec(Icons.GLOBE),
        "without_turrets" to Spec(Icons.TOWER, 0, Icons.CLOSE),
        "elite_survivors" to Spec(Icons.HELMET, 0, Icons.HEART),
        "perfect_1" to Spec(Icons.SHIELD, 1),
        "perfect_10" to Spec(Icons.SHIELD, 2),
        "kills_100" to Spec(Icons.SKULL, 1),
        "kills_1000" to Spec(Icons.SKULL, 2),
        "kills_10000" to Spec(Icons.SKULL, 3),
        "kills_100000" to Spec(Icons.SKULL, 4),
        "first_research" to Spec(Icons.FLASK, 1),
        "research_10" to Spec(Icons.FLASK, 2),
        "research_50" to Spec(Icons.FLASK, 3),
        "research_110" to Spec(Icons.FLASK, 4),
        "research_maxed" to Spec(Icons.BOOK, 0, Icons.STAR),
        "daily_1" to Spec(Icons.CALENDAR, 1),
        "daily_7" to Spec(Icons.CALENDAR, 2),
        "daily_30" to Spec(Icons.CALENDAR, 3),
        "insane_win" to Spec(Icons.FLAME),
        "all_doctrines" to Spec(Icons.BANNER),
        "armory_10" to Spec(Icons.ANVIL),
        "first_turret" to Spec(Icons.TOWER, 1),
        "turrets_50" to Spec(Icons.TOWER, 2),
        "barrages_100" to Spec(Icons.BOMB),
        "elites_100" to Spec(Icons.HELMET, 0, Icons.CHEVRONS),
        "first_evolve" to Spec(Icons.EVOLVE, 1),
        "evolutions_50" to Spec(Icons.EVOLVE, 2),
        "fast_win" to Spec(Icons.STOPWATCH),
        "steam_twins" to Spec(Icons.GEAR),
        "silicon_twins" to Spec(Icons.MONITOR),
        "rain_victory" to Spec(Icons.RAIN),
        "wind_victory" to Spec(Icons.WIND),
        "night_victory" to Spec(Icons.MOON),
        "barrage_eight" to Spec(Icons.METEOR),
        "boss_knockback" to Spec(Icons.SIEGE_HAMMER, 0, Icons.SKULL),
        "boss_aura" to Spec(Icons.FLAME, 0, Icons.SKULL),
        "boss_shield" to Spec(Icons.SHIELD, 0, Icons.SKULL),
        "boss_recruiter" to Spec(Icons.PERSON, 0, Icons.SKULL),
        "boss_rage" to Spec(Icons.BOLT, 0, Icons.SKULL),
        "weekly_gold" to Spec(Icons.TROPHY),
        "custom_three" to Spec(Icons.DICE),
        "replay_record" to Spec(Icons.PLAY, 0, Icons.STAR),
        "card_medics_30" to Spec(Icons.FIELD_MEDICS),
        "card_spark_30" to Spec(Icons.FINAL_SPARK),
        "card_guard_30" to Spec(Icons.FIRST_GUARD),
        "card_recall_30" to Spec(Icons.BOSS_RECALL),
        "card_new_50" to Spec(Icons.CHEST),
        "weekly_rules_3" to Spec(Icons.MISSIONS, 1),
        "weekly_rules_6" to Spec(Icons.MISSIONS, 2),
        "hard_perfect" to Spec(Icons.SHIELD, 0, Icons.FLAME),
        "daily_events_6" to Spec(Icons.SUN, 1),
        "daily_events_9" to Spec(Icons.SUN, 2),
        "card_tools_30" to Spec(Icons.WRENCH),
        "modifier_victories_10" to Spec(Icons.CROSSHAIR),
        "doctrine_victories_13" to Spec(Icons.BANNER, 0, Icons.TROPHY),
        "campaign_hard" to Spec(Icons.MAP, 0, Icons.FLAME),
        "commander_first" to Spec(Icons.CROWN, 1),
        "commander_survivor" to Spec(Icons.CROWN, 0, Icons.HEART),
        "commander_pulses_50" to Spec(Icons.COMMANDER_PULSE),
        "commander_wins_10" to Spec(Icons.CROWN, 2),
        "turret_tier_four" to Spec(Icons.TOWER_TIERS),
        "late_no_turrets" to Spec(Icons.TOWER, 0, Icons.FLAG),
        // Po D6: „Wygrany zakład” — kość (karta Zakład) z pucharem.
        "wager_40" to Spec(Icons.DICE, 0, Icons.TROPHY),
        // D65: Rozkazy sztabu — flaga kafla Rozkazów ze znaczkiem; obrona bez wież: tarcza z flagą.
        "orders_perfect_1" to Spec(Icons.FLAG, 0, Icons.STAR),
        "orders_all_9" to Spec(Icons.FLAG, 0, Icons.CHECK),
        "orders_stars_27" to Spec(Icons.FLAG, 0, Icons.CROWN),
        "orders_hold_no_turrets" to Spec(Icons.SHIELD, 0, Icons.FLAG),
    )

    /** Rankingi: symbol trybu jak w menu, znaczek pucharu (Rankingi). */
    private val boards: Map<String, Spec> = linkedMapOf(
        "endless" to Spec(Icons.TOWER, 0, Icons.TROPHY),
        "campaign" to Spec(Icons.MAP, 0, Icons.TROPHY),
        "skirmish" to Spec(Icons.CROSSED_SWORDS, 0, Icons.TROPHY),
        "daily" to Spec(Icons.CALENDAR, 0, Icons.TROPHY),
        // Dzień 62: Rozkazy sztabu (ikona kafla w menu).
        "orders" to Spec(Icons.FLAG, 0, Icons.TROPHY),
    )

    private val size = 512
    private val half = size / 2f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun categoryColor(c: AchievementCategory?): Int = when (c) {
        AchievementCategory.COMBAT -> Color.rgb(196, 52, 46)
        AchievementCategory.CAMPAIGN -> Color.rgb(52, 150, 72)
        AchievementCategory.ENDLESS -> Color.rgb(122, 68, 182)
        AchievementCategory.RESEARCH -> Color.rgb(48, 120, 210)
        AchievementCategory.DAILY -> Color.rgb(228, 120, 36)
        AchievementCategory.TACTICS -> Color.rgb(196, 152, 40)
        null -> Color.rgb(70, 82, 104) // rankingi: łupkowy, jak przyciski gry
    }

    private fun rimColor(tier: Int): Int = when (tier) {
        1 -> Color.rgb(205, 127, 50)   // brąz
        2 -> Color.rgb(214, 222, 232)  // srebro
        4 -> Color.rgb(170, 236, 246)  // platyna (chłodny połysk)
        else -> Ui.GOLD                // złoto i osiągnięcia pojedyncze
    }

    private fun scale(color: Int, k: Float): Int = Color.rgb(
        (Color.red(color) * k).toInt().coerceIn(0, 255),
        (Color.green(color) * k).toInt().coerceIn(0, 255),
        (Color.blue(color) * k).toInt().coerceIn(0, 255))

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    /** Względna luminancja 0..1 (sRGB w przybliżeniu liniowym, jak w wersji szarej Google). */
    private fun lum(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    /** Średnia luminancja symbolu narysowanego osobno (do wyboru jasnego lub ciemnego tła). */
    private fun symbolLuminance(symbol: Int): Float {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        Icons.draw(Canvas(bmp), symbol, 64f, 64f, 110f)
        var sum = 0f; var n = 0
        val px = IntArray(128 * 128)
        bmp.getPixels(px, 0, 128, 0, 0, 128, 128)
        for (p in px) if (Color.alpha(p) > 200) { sum += lum(p); n++ }
        bmp.recycle()
        return if (n == 0) 0.5f else sum / n
    }

    /**
     * Tarcza w kolorze kategorii (cieniowana jak karty gry), złota lub stopniowa obwódka,
     * symbol na środku, gwiazdki stopnia na dole, znaczek w prawym górnym rogu.
     * [withSymbol] = false rysuje samo tło (do kontroli kontrastu).
     */
    private fun render(spec: Spec, category: AchievementCategory?, withSymbol: Boolean = true): Bitmap =
        render(spec, category, withSymbol, lightDisc(spec, category))

    private val lightCache = HashMap<Pair<Spec, AchievementCategory?>, Boolean>()

    /**
     * Jasna czy ciemna tarcza: wybieramy wariant o większym kontraście symbolu w szarości
     * (żółty księżyc czy kolba mają średnią jasność, więc sama jasność symbolu nie wystarcza).
     */
    private fun lightDisc(spec: Spec, category: AchievementCategory?): Boolean = lightCache.getOrPut(spec to category) {
        val dark = contrast(render(spec, category, true, false), render(spec, category, false, false))
        val light = contrast(render(spec, category, true, true), render(spec, category, false, true))
        light > dark + 0.03 || (abs(light - dark) <= 0.03 && symbolLuminance(spec.symbol) < 0.42f)
    }

    /** Średnia różnica luminancji pikseli symbolu względem tego samego miejsca na samym tle. */
    private fun contrast(full: Bitmap, bg: Bitmap): Double {
        val px = pixels(full); val bgPx = pixels(bg)
        full.recycle(); bg.recycle()
        var sum = 0.0; var n = 0
        for (i in px.indices) if (px[i] != bgPx[i]) {
            val dl = abs(lum(px[i]) - lum(bgPx[i]))
            if (dl > 0.02f) { sum += dl; n++ }
        }
        return if (n < size * size / 40) 0.0 else sum / n
    }

    private fun render(spec: Spec, category: AchievementCategory?, withSymbol: Boolean, lightBg: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val base = categoryColor(category)
        // Ciemny symbol dostaje jasną tarczę, jasny — ciemną (kontrast także w szarości).
        val darkSymbol = lightBg
        val top = if (darkSymbol) mix(base, Color.WHITE, 0.62f) else scale(base, 0.62f)
        val bottom = if (darkSymbol) mix(base, Color.WHITE, 0.32f) else scale(base, 0.26f)
        val rim = rimColor(spec.tier)
        val rOuter = 0.485f * size
        val rDisc = 0.44f * size

        // Cień pod tarczą (jak cień kart), mieści się w kole przycięcia toastu.
        fill.shader = null
        fill.color = Color.argb(110, 0, 0, 0)
        c.drawCircle(half, half + 0.008f * size, rOuter, fill)
        // Obwódka: ciemny kontur, metal z połyskiem.
        fill.color = Icons.OUTLINE
        c.drawCircle(half, half, rOuter, fill)
        fill.shader = LinearGradient(0f, half - rOuter, 0f, half + rOuter,
            mix(rim, Color.WHITE, 0.35f), scale(rim, 0.62f), Shader.TileMode.CLAMP)
        c.drawCircle(half, half, rOuter - 0.012f * size, fill)
        fill.shader = null
        fill.color = Icons.OUTLINE
        c.drawCircle(half, half, rDisc + 0.012f * size, fill)
        // Tarcza: jaśniejsza góra, ciemniejsza dolna krawędź (styl ui.card).
        fill.shader = LinearGradient(0f, half - rDisc, 0f, half + rDisc, top, bottom, Shader.TileMode.CLAMP)
        c.drawCircle(half, half, rDisc, fill)
        // Miękki blask pod symbolem.
        fill.shader = RadialGradient(half, half - 0.04f * size, rDisc * 0.8f,
            Color.argb(if (darkSymbol) 90 else 60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawCircle(half, half, rDisc, fill)
        fill.shader = null
        // Lamówka wewnętrzna w kolorze obwódki (jak złota lamówka paneli).
        stroke.color = Color.argb(150, Color.red(rim), Color.green(rim), Color.blue(rim))
        stroke.strokeWidth = 0.008f * size
        c.drawCircle(half, half, rDisc - 0.03f * size, stroke)

        if (withSymbol) {
            val symbolY = if (spec.tier > 0) half - 0.05f * size else half
            Icons.draw(c, spec.symbol, half, symbolY, if (spec.tier > 0) 0.44f * size else 0.47f * size)
        }
        // Gwiazdki stopnia (1–4) na dolnym łuku.
        if (spec.tier > 0) {
            val n = spec.tier
            val step = 0.112f * size
            val y = half + 0.29f * size
            // Ciemna belka pod gwiazdkami: stopień czytelny także po pomniejszeniu i w szarości.
            fill.shader = null
            fill.color = Color.argb(150, 20, 16, 26)
            val bw = (n - 1) * step / 2f + 0.07f * size
            c.drawRoundRect(half - bw, y - 0.062f * size, half + bw, y + 0.062f * size,
                0.062f * size, 0.062f * size, fill)
            for (i in 0 until n) {
                val x = half + (i - (n - 1) / 2f) * step
                Icons.draw(c, Icons.STAR, x, y, 0.125f * size)
            }
        }
        // Znaczek: mała tarcza z obwódką w prawym górnym rogu, w kole r = 0,42 boku.
        if (spec.badge >= 0) {
            val bx = half + 0.235f * size
            val by = half - 0.215f * size
            val br = 0.085f * size
            fill.color = Icons.OUTLINE
            c.drawCircle(bx, by, br + 0.012f * size, fill)
            fill.color = rim
            c.drawCircle(bx, by, br + 0.004f * size, fill)
            fill.color = if (darkSymbol) scale(base, 0.5f) else mix(base, Color.WHITE, 0.6f)
            c.drawCircle(bx, by, br - 0.008f * size, fill)
            Icons.draw(c, spec.badge, bx, by, br * 1.45f)
        }
        return bmp
    }

    private fun categoryOf(id: String) = Achievements.byId(id)!!.category

    private fun png(bmp: Bitmap): ByteArray = ByteArrayOutputStream().use {
        check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
    }

    private fun pixels(bmp: Bitmap) = IntArray(bmp.width * bmp.height).also {
        bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    private fun sha(px: IntArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = java.nio.ByteBuffer.allocate(px.size * 4)
        buf.asIntBuffer().put(px)
        return md.digest(buf.array()).joinToString("") { "%02x".format(it) }
    }

    /** Kontrole z R9 dla jednej ikony; zwraca listę problemów. */
    private fun check(name: String, spec: Spec, category: AchievementCategory?, bmp: Bitmap): List<String> {
        val problems = ArrayList<String>()
        if (bmp.width != size || bmp.height != size) problems.add("$name: ${bmp.width}×${bmp.height}")
        val bytes = png(bmp).size
        if (bytes >= 1_000_000) problems.add("$name: PNG $bytes B ≥ 1 MB")
        val px = pixels(bmp)
        // Koło przycięcia toastu: r = 0,42 boku, ≥ 90% nieprzezroczystych pikseli.
        val r = 0.42f * size
        var inside = 0; var opaque = 0; var outsideContent = 0
        for (y in 0 until size) for (x in 0 until size) {
            val d = hypot(x + 0.5f - half, y + 0.5f - half)
            val a = Color.alpha(px[y * size + x])
            if (d <= r) { inside++; if (a >= 250) opaque++ }
            else if (d > 0.5f * size && a > 0) outsideContent++
        }
        if (opaque < 0.9f * inside) problems.add("$name: w kole r=0,42 tylko ${100 * opaque / inside}% nieprzezroczystych")
        if (outsideContent > 0) problems.add("$name: $outsideContent pikseli w rogach poza kołem")
        // Kontrast w szarości: piksele symbolu (różne od samego tła) vs to samo miejsce na tle.
        val diff = contrast(bmp.copy(Bitmap.Config.ARGB_8888, false), render(spec, category, withSymbol = false))
        if (diff == 0.0) problems.add("$name: symbol prawie niewidoczny")
        else if (diff < 0.25) problems.add("$name: kontrast w szarości ${"%.2f".format(diff)} < 0,25")
        return problems
    }

    private fun allIcons(): List<Triple<String, Spec, AchievementCategory?>> =
        Achievements.all.map { Triple("ach_${it.id}", specs.getValue(it.id), it.category) } +
            boards.map { (k, v) -> Triple("lb_$k", v, null) }

    private fun docsDir(vararg parts: String): File {
        val root = sequenceOf(File(".."), File(".")).first { File(it, "docs/store").isDirectory }
        return File(root, "docs/" + parts.joinToString("/"))
    }

    /** `<nazwa pl z importu>,ach_<id>.png` w kolejności wierszy AchievementsMetadata.csv. */
    private fun mappings(): List<String> {
        val metadata = docsDir("store", "pgs_import", "AchievementsMetadata.csv").readLines().filter { it.isNotBlank() }
        val sheet = docsDir("store", "play_games_sheet.csv").readLines().drop(1)
            .map { it.split(',') }
            .filter { it.size > 3 && it[1] == "osiągnięcie" }
            .sortedBy { it[0].toInt() }
        assertEquals("arkusz i import mają różną liczbę osiągnięć", metadata.size, sheet.size)
        return metadata.zip(sheet).map { (meta, row) ->
            val id = row[2].removePrefix("cw_")
            val order = meta.split(',').last().trim()
            assertEquals("kolejność importu ≠ arkusza dla $id", row[0], order)
            meta.substringBefore(',') + ",ach_$id.png"
        }
    }

    @Test
    fun mappingCoversCatalogAndSampleIconsPassChecks() {
        assertEquals("tabela symboli ≠ katalog", Achievements.all.map { it.id }.toSet(), specs.keys)
        assertTrue(specs.values.all { it.tier in 0..4 })
        val map = mappings()
        assertEquals(Achievements.all.size, map.size)
        assertEquals(Achievements.all.map { "ach_${it.id}.png" }.toSet(), map.map { it.substringAfter(',') }.toSet())
        // Unikalność specyfikacji (symbol, stopień, znaczek, kategoria) — tanie sprawdzenie bez renderu.
        val keys = allIcons().map { (_, s, c) -> listOf(s.symbol, s.tier, s.badge, c) }
        assertEquals("dwie ikony o tej samej specyfikacji", keys.size, keys.toSet().size)
        val sample = allIcons().filter {
            it.first in setOf("ach_win_1", "ach_kills_100000", "ach_barrages_100", "ach_boss_rage",
                "ach_first_research", "ach_night_victory", "lb_endless")
        }
        val problems = ArrayList<String>()
        val hashes = HashSet<String>()
        for ((name, spec, cat) in sample) {
            val bmp = render(spec, cat)
            problems += check(name, spec, cat, bmp)
            if (!hashes.add(sha(pixels(bmp)))) problems.add("$name: powtórzona ikona")
            bmp.recycle()
        }
        assertTrue("Ikony PGS — problemy:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun generate() {
        assumeTrue("Generator tylko przy PGS_ICONS=true", System.getenv("PGS_ICONS") == "true")
        val out = File("build/pgs-icons").apply { deleteRecursively(); mkdirs() }
        val problems = ArrayList<String>()
        val hashes = HashMap<String, String>()
        val icons = allIcons()
        val cols = 10
        val cell = 128
        val rows = (icons.size + cols - 1) / cols
        val contact = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
        val cc = Canvas(contact)
        cc.drawColor(Color.rgb(48, 48, 52)) // ciemnoszare tło jak lista rankingów Google
        val scaled = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for ((i, icon) in icons.withIndex()) {
            val (name, spec, cat) = icon
            val bmp = render(spec, cat)
            problems += check(name, spec, cat, bmp)
            val h = sha(pixels(bmp))
            hashes.put(h, name)?.let { problems.add("$name: identyczna z $it") }
            File(out, "$name.png").writeBytes(png(bmp))
            val x = (i % cols) * cell; val y = (i / cols) * cell
            cc.drawBitmap(bmp, null, android.graphics.RectF(x + 4f, y + 4f, x + cell - 4f, y + cell - 4f), scaled)
            bmp.recycle()
        }
        val gray = Bitmap.createBitmap(contact.width, contact.height, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(contact, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        })
        File(out, "contact.png").writeBytes(png(contact))
        File(out, "contact_gray.png").writeBytes(png(gray))
        val mapping = mappings()
        File(out, "AchievementsIconsMappings.csv").writeText(mapping.joinToString("\n", postfix = "\n"))
        contact.recycle(); gray.recycle()

        val files = out.listFiles()!!.filter { it.name.endsWith(".png") && (it.name.startsWith("ach_") || it.name.startsWith("lb_")) }
        if (files.size != Achievements.all.size + boards.size) problems.add("plików ${files.size} ≠ ${Achievements.all.size + boards.size}")
        for (line in mapping) if (!File(out, line.substringAfter(',')).isFile) problems.add("mapowanie bez pliku: $line")
        assertTrue("Ikony PGS — problemy:\n" + problems.joinToString("\n"), problems.isEmpty())

        val docs = docsDir("store", "assets", "pgs").apply { mkdirs() }
        for (name in listOf("contact.png", "contact_gray.png", "AchievementsIconsMappings.csv"))
            File(out, name).copyTo(File(docs, name), overwrite = true)
    }
}
