package com.centurywar.game

import org.junit.Assert.assertTrue
import org.junit.Test

/** Zabezpieczenia parametrów wariantów po strojeniu zlecenia 163. */
class VariantTuning163Test {
    @Test
    fun combustionArmorKeepsMobilityWithoutMatchingGalvanicSpeed() {
        val era = Defs.eraOf(EraArt.STEAM)
        val heavy = Defs.unit(era, Role.HEAVY, 0)
        val elite = Defs.unit(era, Role.ELITE, 0)
        val baseHeavy = Defs.unit(era, Role.HEAVY)
        val baseElite = Defs.unit(era, Role.ELITE)
        val galvanicHeavy = Defs.unit(era, Role.HEAVY, 1)
        val galvanicElite = Defs.unit(era, Role.ELITE, 1)

        // Przed korektą ciężka/elita miały tylko 78%/74% bazowej szybkości.
        assertTrue("Ciężka spalinowa nadal zbyt wolna", heavy.speed >= baseHeavy.speed * 0.87f)
        assertTrue("Elita spalinowa nadal zbyt wolna", elite.speed >= baseElite.speed * 0.81f)
        assertTrue("Galwaniczna ciężka musi pozostać szybsza", heavy.speed < galvanicHeavy.speed)
        assertTrue("Galwaniczna elita musi pozostać szybsza", elite.speed < galvanicElite.speed)
        assertTrue("Spalinowa ciężka musi zachować przewagę HP", heavy.hp > galvanicHeavy.hp)
        assertTrue("Spalinowa elita musi zachować przewagę HP", elite.hp > galvanicElite.hp)
    }

    @Test
    fun swarmHeavyRetainsAChanceAgainstBiotechWithoutLosingItsRole() {
        val era = Defs.eraOf(EraArt.SILICON)
        val base = Defs.unit(era, Role.HEAVY)
        val swarm = Defs.unit(era, Role.HEAVY, 0)
        val biotech = Defs.unit(era, Role.HEAVY, 1)

        // Przed korektą rój miał 78% bazowych HP; biotech pozostaje twardszy.
        assertTrue("Ciężka roju nadal zbyt krucha", swarm.hp >= base.hp * 0.82f)
        assertTrue("Biotech musi pozostać odporniejszy", swarm.hp < biotech.hp)
        assertTrue("Rój musi pozostać tańszy", swarm.cost < biotech.cost)
        assertTrue("Rój musi pozostać szybszy", swarm.speed > biotech.speed)
    }
}
