package com.centurywar.screens

import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessBosses
import com.centurywar.game.EndlessCard
import com.centurywar.game.EndlessRarity
import com.centurywar.game.EndlessWaveTheme
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wykrywa brak opisów Kodeksu po dodaniu nowego elementu gry. */
class CodexLTest {
    @Test
    fun codexCoversAllDefinitions() {
        val required = mutableSetOf(
            "cx62_eras", "cx62_units", "cx62_variants", "cx62_towers",
            "cx62_abilities", "cx62_doctrines", "cx62_weather", "cx62_modifiers",
            "cx62_cards", "cx62_bosses", "cx62_armory_d", "cx62_no_variants_d",
            "cx62_rain_d", "cx62_wind_d", "cx62_night_d", "cx62_waves_d",
            "cx62_rally_d",
        )
        for (era in Defs.specs) {
            required.add(era.nameKey)
            required.add(era.abilityKey)
            required.add(era.turretName)
            required.addAll(era.unitNames)
            for (variant in era.variants) {
                required.add(variant.nameKey)
                required.add(variant.descKey)
                required.add(variant.turretName)
                required.addAll(variant.unitNames)
            }
        }
        for (d in Doctrine.entries) {
            required.add(d.key)
            required.add(d.key + "_d")
        }
        for (type in Weather.entries) required.add(type.key)
        for (m in Modifier.entries) {
            required.add(m.key)
            required.add("sk_moddesc_" + m.ordinal)
        }
        for (card in EndlessCard.entries) {
            required.add(card.textKey)
            required.add(card.textKey + "_d")
        }
        for (r in EndlessRarity.entries) required.add("er_" + r.name.lowercase())
        for (i in 1..EndlessBosses.MAX_KIND) {
            required.add(EndlessBosses.nameKeys[i])
            required.add(EndlessBosses.descriptionKeys[i])
        }
        for (theme in EndlessWaveTheme.entries) {
            required.add(theme.textKey)
            required.add(theme.textKey + "_d")
        }
        val missing = required.filterNot { it in L.keys }
        assertTrue("Kodeks nie ma tłumaczeń: $missing", missing.isEmpty())
    }

    @Test
    fun weatherCodexUsesBattleParameters() {
        val rain = Battle(BattleConfig(Mode.SKIRMISH, 62L, 1f, weather = Weather.RAIN))
        val night = Battle(BattleConfig(Mode.SKIRMISH, 62L, 1f, weather = Weather.NIGHT))
        assertEquals(Weather.RAIN.projectileRange, rain.projectileRangeMul(0), 0f)
        assertEquals(Weather.RAIN.turretRange, rain.projectileRangeMul(0, true), 0f)
        assertEquals(Weather.NIGHT.projectileRange, night.projectileRangeMul(0), 0f)
        assertEquals(Weather.NIGHT.turretRange, night.projectileRangeMul(0, true), 0f)
        assertTrue(Battle.ARMORY_BONUS > 1f)
        assertTrue(Battle.ARMORY_MAX_LEVEL > 0)
    }
}
