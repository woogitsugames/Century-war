package com.centurywar.screens

import com.centurywar.engine.Button
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Trait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dzień 69 (R171): okno wyboru „podstawowy skład / nowa jednostka” i nowe sylwetki
 * na polu bitwy, na tanim telefonie 720p i zewnętrznym ekranie Folda, tekst 130%.
 * Pliki: build/screenshots/recruit69_*.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EraRecruits69ScreenshotTest : ScreenshotTestBase() {
    @Test
    fun recruitChoiceAndSilhouettes() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )) for (lang in arrayOf("pl", "de", "fr", "pt", "tr", "uk")) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = lang
                g.profile.textScale = 130
                g.profile.hintsEnabled = false
                g.onLanguageChanged()
                g.applyAccessibility()
                for (era in 0 until Defs.ERA_COUNT) {
                    val recruit = Defs.recruitUnit(era) ?: continue
                    val key = "$tag/$lang/130%/era$era"
                    val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 6900L + era, 1f,
                        startEra = era, maxEra = era,
                        playerBonuses = Bonuses(eliteUnlocked = true, rallyUnlocked = true),
                        eraRecruits = true))
                    g.setScreen(bs)
                    val battle = field(bs, "battle").get(bs) as Battle
                    assertEquals(key, era, battle.pendingVariant)
                    shot(g, "recruit69_${tag}_${lang}_era${era}_choice", 1)
                    assert74Variants(g, bs, key)
                    for (i in 0..1) {
                        val text = bs.variantAccessibilityText(i)
                        assertTrue("$key: pusty opis karty $i", text.length > 10)
                        assertTrue("$key: brak nazwy w opisie", "%1\$" !in text && "\$s" !in text)
                    }
                    if (lang != "pl") continue
                    // Karta 1: nowa jednostka; kilka sztuk obu stron na polu bitwy.
                    @Suppress("UNCHECKED_CAST")
                    val cards = field(bs, "variantBtn").get(bs) as Array<Button>
                    bs.onClick(cards[1])
                    assertEquals(key, 0, battle.player.eraVariant)
                    battle.enemy.eraVariant = 0
                    battle.ai = null
                    for (k in 0 until 3) {
                        battle.spawn(battle.player, recruit)
                        battle.spawn(battle.enemy, recruit)
                    }
                    battle.spawn(battle.player, Defs.unit(era, com.centurywar.game.Role.MELEE))
                    var n = 0
                    for (u in battle.units) if (u.alive) {
                        // Rozstawienie w zasięgu kamery startowej: gracz z lewej, wróg naprzeciwko.
                        u.x = if (u.side == 0) 330f + n * 70f else 760f + n * 60f
                        u.prevX = Float.NaN
                        n++
                    }
                    shot(g, "recruit69_${tag}_era${era}_battle", 20)
                    assertTrue(key, battle.units.any { it.alive && it.def.trait != Trait.NONE })
                }
            }
        } finally {
            L.lang = originalLang
        }
    }
}
