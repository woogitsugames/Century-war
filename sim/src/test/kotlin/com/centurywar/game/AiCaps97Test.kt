package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresje wyborów AI po ograniczeniu stosów i efektów. */
class AiCaps97Test {
    private fun endless(seed: Long) = Battle(BattleConfig(
        Mode.ENDLESS, seed, 1f, playerBonuses = Bonuses(abilityCdMul = 0.05f),
    ))

    @Test fun cappedCardsAreNeverChosenForTwentySeeds() {
        for (seed in 1L..20L) {
            for (personality in AiPersonality.entries) {
                val b = endless(seed)
                val p = b.player
                p.cap = Battle.MAX_UNITS
                p.slots = 0
                p.rallyUnlocked = false
                p.cloneChance = 0.6f
                p.freeElitesPerWave = 4
                p.repairPerWave = 0.3f
                p.autoBarrageInterval = 15f
                val brain = Ai(b, 0, personality)
                val offers = arrayOf(
                    arrayOf(EndlessCard.TACTICS, EndlessCard.RALLY_MASTER, EndlessCard.FURY),
                    arrayOf(EndlessCard.CLONING, EndlessCard.REPAIR_CREW, EndlessCard.FRONTLINE_PLATE),
                    arrayOf(EndlessCard.FREE_ELITE, EndlessCard.AUTO_BARRAGE, EndlessCard.ECONOMY),
                    arrayOf(EndlessCard.MOBILIZATION, EndlessCard.MUSTER_BANNER, EndlessCard.DRILL_SERGEANT),
                    arrayOf(EndlessCard.ARSENAL, EndlessCard.BANNER, EndlessCard.SCAVENGER),
                    arrayOf(EndlessCard.MEDIC, EndlessCard.TOWER_SLOT, EndlessCard.PLUNDER),
                )
                for (options in offers) {
                    assertTrue(options.last().name, EndlessCards.canOffer(options.last(), p))
                    val chosen = options[brain.chooseCard(options)]
                    assertEquals("seed=$seed osobowosc=$personality", options.last(), chosen)
                }
                // Także rzeczywiste losowanie 3 kart nie przywraca wyczerpanych ofert.
                val drawn = Array(3) { EndlessCard.ECONOMY }
                EndlessCards.draw(Rng(seed), p, drawn)
                val chosen = drawn[brain.chooseCard(drawn)]
                assertTrue("seed=$seed wybrano $chosen", EndlessCards.canOffer(chosen, p))
            }
        }
    }

    @Test fun diminishingStacksMakeFreshCardsPreferable() {
        for (seed in 1L..20L) {
            val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))
            val brain = Ai(b, 0, AiPersonality.ASSAULT)
            val offers = arrayOf(EndlessCard.FURY, EndlessCard.FRONTLINE_PLATE)
            assertEquals(EndlessCard.FURY, offers[brain.chooseCard(offers)])
            repeat(7) { b.applyEndlessCard(b.player, EndlessCard.FURY) }
            assertEquals(7, b.player.cardStacks[EndlessCard.FURY.ordinal])
            assertEquals(EndlessCard.FRONTLINE_PLATE, offers[brain.chooseCard(offers)])
        }
    }

    @Test fun nearlyExhaustedEffectsYieldToFullBonuses() {
        for (seed in 1L..20L) {
            val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))
            val p = b.player
            val brain = Ai(b, 0, AiPersonality.ENGINEER)
            p.abilityCdMul = Battle.MIN_ABILITY_CD_MUL + 0.001f
            p.cloneChance = 0.595f
            p.repairPerWave = 0.299f
            p.autoBarrageInterval = 16f
            p.cap = Battle.MAX_UNITS - 1
            p.baseHp = p.baseMaxHp * 0.99f

            val full = EndlessCard.FRONTLINE_PLATE
            for (near in arrayOf(
                EndlessCard.TACTICS, EndlessCard.RALLY_MASTER, EndlessCard.CLONING,
                EndlessCard.REPAIR_CREW, EndlessCard.AUTO_BARRAGE,
                EndlessCard.MOBILIZATION, EndlessCard.MEDIC, EndlessCard.CITADEL_HEART,
            )) {
                assertTrue("Karta $near nadal jest oferowana", EndlessCards.canOffer(near, p))
                val offered = arrayOf(near, full)
                assertEquals("seed=$seed efekt $near", full, offered[brain.chooseCard(offered)])
            }
        }
    }

    @Test fun doctrinesRespectCapsAndPersonalityPreferences() {
        for (seed in 1L..20L) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f))
            val p = b.player
            p.abilityCdMul = Battle.MIN_ABILITY_CD_MUL
            p.slots = Defs.MAX_TURRET_SLOTS
            for (personality in AiPersonality.entries) {
                val brain = Ai(b, 0, personality)
                assertEquals(Doctrine.FURY, brain.chooseDoctrine(
                    arrayOf(Doctrine.TACTICS, Doctrine.ARSENAL, Doctrine.FURY)))
            }

            p.abilityCdMul = 1f
            p.slots = 2
            val options = arrayOf(
                Doctrine.FURY, Doctrine.ECONOMY, Doctrine.SIEGE, Doctrine.VIGILANCE,
            )
            val favored = arrayOf(
                Doctrine.FURY, Doctrine.VIGILANCE, Doctrine.ECONOMY,
                Doctrine.SIEGE, Doctrine.FURY,
            )
            for (i in AiPersonality.entries.indices) {
                val brain = Ai(b, 0, AiPersonality.entries[i])
                assertEquals(AiPersonality.entries[i].name,
                    favored[i], brain.chooseDoctrine(options))
            }
        }
    }
    @Test fun chooseCardConsumesSameAiRngRegardlessOfOffers() {
        for (seed in 1L..20L) {
            val open = endless(seed)
            val capped = endless(seed)
            // Wyczerpana karta w jednej bitwie nie może przesunąć strumienia AI.
            capped.player.cloneChance = 0.6f
            val options = arrayOf(EndlessCard.CLONING, EndlessCard.FURY, EndlessCard.ECONOMY)
            Ai(open, 0, AiPersonality.BALANCED).chooseCard(options)
            Ai(capped, 0, AiPersonality.BALANCED).chooseCard(options)
            repeat(4) {
                assertEquals("seed=$seed", open.rng.nextAiInt(0, 1000), capped.rng.nextAiInt(0, 1000))
            }
        }
    }

    @Test fun newEndlessCardsHavePersonalityFans() {
        val favored = mapOf(
            AiPersonality.ASSAULT to EndlessCard.FINAL_SPARK,
            AiPersonality.ENGINEER to EndlessCard.FIRST_GUARD,
            AiPersonality.ECONOMIST to EndlessCard.FIELD_MEDICS,
            AiPersonality.SIEGER to EndlessCard.BOSS_RECALL,
            AiPersonality.BALANCED to EndlessCard.FIELD_MEDICS,
        )
        for (seed in 1L..20L) for ((personality, card) in favored) {
            val b = endless(seed)
            // Neutralna karta bez doktryny i bez premii danej osobowości.
            val foil = if (personality == AiPersonality.SIEGER) EndlessCard.LAST_STAND
                else EndlessCard.ELITE_SCHOOL
            val offers = arrayOf(foil, card)
            assertEquals("seed=$seed $personality", card,
                offers[Ai(b, 0, personality).chooseCard(offers)])
        }
    }
}
