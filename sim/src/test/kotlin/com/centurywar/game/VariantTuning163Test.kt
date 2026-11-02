package com.centurywar.game

import org.junit.Assert.assertTrue
import org.junit.Test

/** Zabezpieczenia parametrów wariantów po strojeniu zlecenia 163. */
class VariantTuning163Test {
    @Test
    fun combustionHeavyAndEliteStayBehindTheirFasterSupport() {
        val era = Defs.eraOf(EraArt.STEAM)
        val heavy = Defs.unit(era, Role.HEAVY, 0)
        val elite = Defs.unit(era, Role.ELITE, 0)
        val baseHeavy = Defs.unit(era, Role.HEAVY)
        val baseElite = Defs.unit(era, Role.ELITE)
        val galvanicHeavy = Defs.unit(era, Role.HEAVY, 1)
        val galvanicElite = Defs.unit(era, Role.ELITE, 1)

        // Wolniejszy front poprawia wynik lustrzanych starć: wcześniej 0,78/0,74.
        assertTrue("Ciężka spalinowa za szybko wychodzi na front", heavy.speed <= baseHeavy.speed * 0.76f)
        assertTrue("Elita spalinowa za szybko wychodzi na front", elite.speed <= baseElite.speed * 0.73f)
        assertTrue("Ciężka spalinowa nie może utknąć z tyłu", heavy.speed >= baseHeavy.speed * 0.74f)
        assertTrue("Elita spalinowa nie może utknąć z tyłu", elite.speed >= baseElite.speed * 0.71f)
        assertTrue("Galwaniczna ciężka musi pozostać szybsza", heavy.speed < galvanicHeavy.speed)
        assertTrue("Galwaniczna elita musi pozostać szybsza", elite.speed < galvanicElite.speed)
        assertTrue("Spalinowa ciężka zachowuje przewagę HP", heavy.hp > galvanicHeavy.hp)
        assertTrue("Spalinowa elita zachowuje przewagę HP", elite.hp > galvanicElite.hp)
    }

    @Test
    fun swarmHeavyIsAffordableWhileBiotechKeepsTheArmorAdvantage() {
        val era = Defs.eraOf(EraArt.SILICON)
        val base = Defs.unit(era, Role.HEAVY)
        val swarm = Defs.unit(era, Role.HEAVY, 0)
        val biotech = Defs.unit(era, Role.HEAVY, 1)

        // Przed korektą koszt ciężkiej roju wynosił 0,86 kosztu bazowego.
        assertTrue("Ciężka roju nadal zbyt droga", swarm.cost <= (base.cost * 0.84f).toInt())
        assertTrue("Biotech musi pozostać odporniejszy", swarm.hp < biotech.hp)
        assertTrue("Rój musi pozostać tańszy", swarm.cost < biotech.cost)
        assertTrue("Rój musi pozostać szybszy", swarm.speed > biotech.speed)
    }
}
