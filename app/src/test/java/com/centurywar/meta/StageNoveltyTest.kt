package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Modifier
import com.centurywar.game.Stage
import com.centurywar.game.Weather
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R11-06: „Nowość na tym etapie” liczona statycznie z kampanii. */
class StageNoveltyTest {
    private var savedLang = 0

    @Before fun lang() { savedLang = L.lang; L.setCode("pl") }
    @After fun restore() { L.lang = savedLang }

    private fun kinds(index: Int) = StageNovelty.of(index).map { it.kind }

    @Test fun oneEntryPerStageAndTutorialHasNothing() {
        assertEquals(Campaign.stages.size, StageNovelty.items.size)
        assertTrue(StageNovelty.of(0).isEmpty())
        assertEquals("", StageNovelty.line(0))
        assertTrue(StageNovelty.of(-1).isEmpty())
        assertTrue(StageNovelty.of(Campaign.stages.size).isEmpty())
    }

    @Test fun everyEnemyEraIsIntroducedExactlyOnceInOrder() {
        val eras = StageNovelty.items.flatten().filter { it.kind == NoveltyKind.ENEMY_ERA }.map { it.era }
        assertEquals((1 until Defs.ERA_COUNT).toList(), eras)
        for ((i, list) in StageNovelty.items.withIndex()) {
            val era = list.firstOrNull { it.kind == NoveltyKind.ENEMY_ERA } ?: continue
            assertEquals(Campaign.stages[i].maxEra, era.era)
            for (k in 0 until i) assertTrue("etap ${k + 1}", Campaign.stages[k].maxEra < era.era)
        }
    }

    @Test fun modifiersAndWeatherAreFirstAppearancesOnly() {
        val mods = HashSet<Modifier>()
        val weather = HashSet<Weather>()
        for ((i, list) in StageNovelty.items.withIndex()) {
            for (item in list) when (item.kind) {
                NoveltyKind.MODIFIER -> {
                    val m = item.modifier!!
                    assertTrue("etap ${i + 1}: $m", m in Campaign.stages[i].modifiers)
                    assertTrue("powtórzony $m", mods.add(m))
                    for (k in 1 until i) assertFalse(m in Campaign.stages[k].modifiers)
                }
                NoveltyKind.WEATHER -> {
                    val w = item.weather!!
                    assertEquals(Campaign.stages[i].weather, w)
                    assertTrue(w != Weather.CLEAR)
                    assertTrue("powtórzona $w", weather.add(w))
                }
                else -> {}
            }
        }
        // Wszystko, co kampania pokazuje po samouczku, jest gdzieś zapowiedziane.
        val used = Campaign.stages.drop(1).flatMap { it.modifiers }.toSet()
        assertEquals(used, mods)
        val usedWeather = Campaign.stages.drop(1).map { it.weather }.filter { it != Weather.CLEAR }.toSet()
        assertEquals(usedWeather, weather)
    }

    @Test fun commanderOnStageEightMatchesBattle() {
        assertEquals(listOf(7), StageNovelty.items.indices.filter { NoveltyKind.COMMANDER in kinds(it) })
        assertEquals(NoveltyKind.COMMANDER, StageNovelty.of(StageNovelty.COMMANDER_STAGE).first().kind)
        val before = Battle(Campaign.config(Campaign.stages[StageNovelty.COMMANDER_STAGE - 1], Bonuses(), 1L))
        val at = Battle(Campaign.config(Campaign.stages[StageNovelty.COMMANDER_STAGE], Bonuses(), 1L))
        assertFalse(before.commanderAvailable)
        assertTrue(at.commanderAvailable)
    }

    @Test fun midCampaignKeepsSomethingNewEveryFewStages() {
        // Od etapu 3 do ostatniej nowej epoki przerwa bez nowości nie przekracza 4 etapów.
        val novel = StageNovelty.items.indices.filter { StageNovelty.isNovel(it) }
        assertTrue(novel.size >= Defs.ERA_COUNT - 1)
        for (k in 1 until novel.size) assertTrue("${novel[k - 1] + 1}→${novel[k] + 1}", novel[k] - novel[k - 1] <= 4)
    }

    @Test fun computeOnCustomStages() {
        val stages = listOf(
            Stage(0, "s1", 0, 0, 1f, emptySet()),
            Stage(1, "s2", 0, 1, 1f, setOf(Modifier.FOG_OF_WAR), Weather.RAIN),
            Stage(2, "s3", 1, 1, 1f, setOf(Modifier.FOG_OF_WAR, Modifier.FAST_UNITS), Weather.RAIN),
            Stage(3, "s4", 0, 1, 1f, emptySet()),
        )
        val r = StageNovelty.compute(stages, commanderStage = 3)
        assertTrue(r[0].isEmpty())
        assertEquals(listOf(NoveltyKind.ENEMY_ERA, NoveltyKind.WEATHER, NoveltyKind.MODIFIER), r[1].map { it.kind })
        assertEquals(1, r[1][0].era)
        assertEquals(Weather.RAIN, r[1][1].weather)
        assertEquals(Modifier.FOG_OF_WAR, r[1][2].modifier)
        assertEquals(listOf(Modifier.FAST_UNITS), r[2].map { it.modifier })
        assertEquals(listOf(NoveltyKind.COMMANDER), r[3].map { it.kind })
    }

    @Test fun sparkOnlyUntilFirstWin() {
        val i = StageNovelty.COMMANDER_STAGE
        assertTrue(StageNovelty.showSpark(i, 0))
        assertFalse(StageNovelty.showSpark(i, 1))
        assertFalse(StageNovelty.showSpark(i, 3))
        assertFalse(StageNovelty.showSpark(0, 0))
    }

    @Test fun lineIsLocalizedAndNamesHeavyUnit() {
        val i = StageNovelty.items.indexOfFirst { list -> list.any { it.kind == NoveltyKind.ENEMY_ERA } }
        val era = StageNovelty.of(i).first { it.kind == NoveltyKind.ENEMY_ERA }.era
        val pl = StageNovelty.line(i)
        assertTrue(pl, pl.startsWith("Nowość: "))
        assertTrue(pl, pl.contains(L[Defs.eraKeys[era]]))
        assertTrue(pl, pl.contains(L[StageNovelty.unitKey(era)]))
        assertEquals(L[Defs.eras[era].units[2].nameKey], L[StageNovelty.unitKey(era)])
        val cmd = StageNovelty.line(StageNovelty.COMMANDER_STAGE)
        assertTrue(cmd, cmd.contains(L["nov201_commander"]))
        for (m in StageNovelty.of(StageNovelty.COMMANDER_STAGE)) assertTrue(cmd, cmd.contains(StageNovelty.describe(m)))
        // Bez surowych kluczy i placeholderów w każdym języku.
        for (code in L.CODES) {
            L.setCode(code)
            for (k in StageNovelty.items.indices) {
                val text = StageNovelty.line(k)
                if (!StageNovelty.isNovel(k)) { assertEquals("", text); continue }
                assertFalse("$code/${k + 1}: $text", text.contains("nov201_") || text.contains('%') ||
                    text.contains("u_") || text.contains("era_") || text.contains("m_") || text.contains("w_"))
            }
        }
        L.setCode("en")
        assertTrue(StageNovelty.line(StageNovelty.COMMANDER_STAGE).startsWith("New: commander"))
    }
}
