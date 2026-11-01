package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testy czterech doktryn bez zależności od renderera. */
class Doctrine69Test {
    private fun battle(seed: Long = 69L, fog: Boolean = false): Battle =
        Battle(BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = 2, maxEra = 2,
            modifiers = if (fog) setOf(Modifier.FOG_OF_WAR) else emptySet(),
            variantsEnabled = false))

    @Test fun historicalOrdinalsRemainUnchanged() {
        assertEquals((0..8).toList(), Doctrine.entries.take(9).map { it.ordinal })
        assertEquals(listOf(Doctrine.BANNER, Doctrine.SUPPLY, Doctrine.SIEGE_MASTERS,
            Doctrine.VIGILANCE), Doctrine.entries.takeLast(4))
        assertEquals(9, EndlessCard.BANNER.ordinal - 21) // 21 kart specjalnych za pierwszymi 9
        assertEquals(30, EndlessCard.BANNER.ordinal)
    }

    @Test fun personalitiesUnderstandNewRoles() {
        val options = arrayOf(Doctrine.BANNER, Doctrine.SUPPLY,
            Doctrine.SIEGE_MASTERS, Doctrine.VIGILANCE)
        val expected = mapOf(
            AiPersonality.ASSAULT to Doctrine.BANNER,
            AiPersonality.ENGINEER to Doctrine.VIGILANCE,
            AiPersonality.ECONOMIST to Doctrine.SUPPLY,
            AiPersonality.SIEGER to Doctrine.SIEGE_MASTERS,
            AiPersonality.BALANCED to Doctrine.BANNER,
        )
        for ((personality, doctrine) in expected) {
            val b = battle()
            val chosen = Ai(b, 0, personality).chooseDoctrine(options)
            assertEquals(personality.name, doctrine, chosen)
        }
    }

    /** Jedno bezpośrednie trafienie od tego samego napastnika. */
    private fun hpAfterHit(banner: Boolean, rally: Boolean): Float {
        val b = battle()
        b.ai!!.thinkTimer = 1000f
        val p = b.player
        val e = b.enemy
        assertTrue(b.spawn(p, p.unit(Role.MELEE)))
        assertTrue(b.spawn(e, e.unit(Role.MELEE)))
        val own = b.units.first { it.alive && it.side == 0 }
        val foe = b.units.first { it.alive && it.side == 1 }
        own.x = p.baseFront + 350f
        foe.x = own.x + 38f
        own.cd = 1000f
        foe.cd = 0f
        if (banner) b.applyDoctrine(p, Doctrine.BANNER)
        if (rally) assertTrue(b.castRally(p))
        b.step()
        return own.hp
    }

    @Test fun bannerProtectsOnlyDuringRally() {
        val noRally = hpAfterHit(banner = false, rally = false)
        val bannerIdle = hpAfterHit(banner = true, rally = false)
        val baseline = hpAfterHit(banner = false, rally = true)
        val protected = hpAfterHit(banner = true, rally = true)
        assertEquals(noRally, bannerIdle, 0.001f)
        assertTrue("Sztandar nie daje pancerza bez Zrywu", baseline < protected)
    }

    private fun goldAfterKill(supply: Boolean): Float {
        val b = battle()
        b.ai!!.thinkTimer = 1000f
        val p = b.player
        assertTrue(b.spawn(p, p.unit(Role.MELEE)))
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val soldier = b.units.first { it.alive && it.side == 0 }
        val victim = b.units.first { it.alive && it.side == 1 }
        soldier.x = p.baseFront + 350f
        victim.x = soldier.x + 38f
        soldier.cd = 0f
        victim.cd = 1000f
        victim.hp = 1f
        if (supply) b.applyDoctrine(p, Doctrine.SUPPLY)
        b.step()
        assertEquals(1, p.kills)
        return p.gold
    }

    @Test fun supplyIncreasesKillBountyAndExpires() {
        val bonus = goldAfterKill(true) - goldAfterKill(false)
        val cost = Defs.unit(2, Role.MELEE).cost
        assertEquals(Battle.SUPPLY_INSTANT_GOLD * Defs.costScale(2) + cost * 1.1f * 0.3f, bonus, 0.05f)

        val b = battle()
        b.ai!!.thinkTimer = 1000f
        b.applyDoctrine(b.player, Doctrine.SUPPLY)
        assertEquals(60f, b.player.supplyUntil, 0f)
        repeat(60 * 61) { b.step() }
        assertTrue(b.time > b.player.supplyUntil)
        b.applyDoctrine(b.player, Doctrine.SUPPLY)
        assertEquals(b.time + 60f, b.player.supplyUntil, 0.001f)
    }

    private fun baseDamage(role: Role, mastery: Boolean): Float {
        val b = battle()
        b.ai!!.thinkTimer = 1000f
        if (mastery) b.applyDoctrine(b.player, Doctrine.SIEGE_MASTERS)
        assertTrue(b.spawn(b.player, b.player.unit(role)))
        val attacker = b.units.first { it.alive }
        attacker.x = b.enemy.baseFront - 18f
        attacker.cd = 0f
        repeat(180) { if (!b.over) b.step() }
        return b.enemy.baseMaxHp - b.enemy.baseHp
    }

    @Test fun siegeMastersAffectOnlyHeavyAttacksOnBases() {
        val heavyNormal = baseDamage(Role.HEAVY, false)
        val heavyEnhanced = baseDamage(Role.HEAVY, true)
        assertTrue("Ciężki musi zadać obrażenia bazie", heavyNormal > 0f)
        assertTrue("Mistrzowie oblężeń nie wzmacniają ciężkich",
            heavyEnhanced > heavyNormal * 1.10f)
        assertEquals(baseDamage(Role.MELEE, false), baseDamage(Role.MELEE, true), 0.05f)
    }

    @Test fun vigilanceExtendsTowerRangeAndRevealsFog() {
        val b = battle(fog = true)
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val hidden = b.units.first { it.alive && it.side == 1 }
        hidden.x = b.player.front + 720f
        val scout = Ai(b, 0, AiPersonality.ENGINEER)
        assertFalse(scout.canSeeEnemy(hidden))
        b.applyDoctrine(b.player, Doctrine.VIGILANCE)
        assertEquals(1.18f, b.player.turretRangeMul, 0.001f)
        assertTrue(b.player.fogSight)
        assertTrue(scout.canSeeEnemy(hidden))
    }

    @Test fun allNewDoctrinesWorkAsEndlessCards() {
        val b = Battle(BattleConfig(Mode.ENDLESS, 69L, 1f))
        for (c in listOf(EndlessCard.BANNER, EndlessCard.SUPPLY,
            EndlessCard.SIEGE_MASTERS, EndlessCard.VIGILANCE)) {
            b.applyEndlessCard(b.player, c)
            assertEquals(1, b.player.cardStacks[c.ordinal])
            assertTrue(c.doctrine in b.player.doctrines)
        }
    }
}
