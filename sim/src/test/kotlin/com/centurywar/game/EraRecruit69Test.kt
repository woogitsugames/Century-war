package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dzień 69 (R171): warianty epok zmieniające jedną rolę i ich cechy. */
class EraRecruit69Test {
    private val expected = mapOf(
        EraArt.TRIBAL to Pair(Role.HEAVY, Trait.SHIELD),
        EraArt.BRONZE to Pair(Role.HEAVY, Trait.DASH),
        EraArt.IRON to Pair(Role.ELITE, Trait.RAM),
        EraArt.POWDER to Pair(Role.RANGED, Trait.BRACE),
    )

    private fun skirmish(era: Int, seed: Long = 6901L) = BattleConfig(Mode.SKIRMISH, seed, 1f,
        startEra = era, maxEra = era, playerBonuses = Bonuses(eliteUnlocked = true, rallyUnlocked = true),
        eraRecruits = true)

    @Test
    fun fourRecruitsReplaceOneRoleWithStableIds() {
        val oldCount = Defs.ERA_COUNT * Role.entries.size +
            Defs.specs.sumOf { it.variants.size } * Role.entries.size
        assertEquals(oldCount + 4, Defs.allUnits.size)
        for ((art, rt) in expected) {
            val era = Defs.eraOf(art)
            val recruit = assertNotNull(Defs.recruitUnit(era)).let { Defs.recruitUnit(era)!! }
            assertTrue(recruit.id >= oldCount)
            assertEquals(recruit, Defs.allUnits[recruit.id])
            assertEquals(rt.first, recruit.role)
            assertEquals(rt.second, recruit.trait)
            assertEquals(-1, recruit.variant)
            assertEquals(art, recruit.art)
            assertArrayEquals(intArrayOf(-1, 0), Defs.variantChoices(era))
            for (role in Role.entries) {
                val base = Defs.unit(era, role)
                assertEquals(Trait.NONE, base.trait)
                val chosen = Defs.unit(era, role, 0)
                if (role == rt.first) assertEquals(recruit, chosen) else assertEquals(base, chosen)
            }
            // Wieża, ostrzał i styl pozostają z epoki.
            assertEquals(Defs.turret(era), Defs.turret(era, 0))
            assertEquals(Defs.barrage(era), Defs.barrage(era, 0))
            assertEquals(art, Defs.sideArt(era, 0))
            assertEquals(-1, Defs.styleVariant(era, 0))
        }
        // Epoki z pełnymi wariantami bez zmian, pozostałe bez wyboru.
        for (era in 0 until Defs.ERA_COUNT) {
            if (Defs.spec(era).variants.isNotEmpty())
                assertArrayEquals(intArrayOf(0, 1), Defs.variantChoices(era))
            else if (Defs.spec(era).recruit == null) assertEquals(0, Defs.variantChoices(era).size)
        }
    }

    @Test
    fun recruitChoiceOnlyInSkirmish() {
        val tribal = Defs.eraOf(EraArt.TRIBAL)
        assertEquals(tribal, Battle(skirmish(tribal)).pendingVariant)
        assertNull(Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f, startEra = tribal, maxEra = tribal,
            variantsEnabled = false, eraRecruits = true)).pendingVariant)
        // Bez flagi (np. stare zapisy potyczek) potyczka gra podstawowym składem.
        assertNull(Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f, startEra = tribal, maxEra = tribal)).pendingVariant)
        for (mode in arrayOf(Mode.CAMPAIGN, Mode.ENDLESS, Mode.DAILY, Mode.ORDERS)) {
            val b = Battle(BattleConfig(mode, 6902L, 1f, startEra = tribal, maxEra = tribal, eraRecruits = true))
            assertNull("$mode", b.pendingVariant)
            assertFalse("$mode", b.variantsOffered(tribal))
            assertEquals(-1, b.player.eraVariant)
            assertEquals(-1, b.enemy.eraVariant)
        }
        val campaign = Battle(Campaign.config(Campaign.stages[0], Bonuses(), 6903L))
        assertNull(campaign.pendingVariant)
        // Pełne warianty epoki pary w kampanii działają jak dotąd.
        val steam = Defs.eraOf(EraArt.STEAM)
        assertTrue(Battle(BattleConfig(Mode.CAMPAIGN, 6904L, 1f, startEra = steam, maxEra = steam)).variantsOffered(steam))
    }

    @Test
    fun cardZeroIsBaseAndCardOneIsRecruit() {
        for (art in expected.keys) {
            val era = Defs.eraOf(art)
            val base = Battle(skirmish(era))
            assertTrue(base.chooseVariant(0))
            assertEquals(-1, base.player.eraVariant)
            val recruit = Battle(skirmish(era))
            assertTrue(recruit.chooseVariant(1))
            assertEquals(0, recruit.player.eraVariant)
            assertFalse(Battle(skirmish(era)).chooseVariant(2))
            assertEquals(Defs.recruitUnit(era), recruit.player.unit(Defs.recruitUnit(era)!!.role))
            // Wieża kupiona z nową jednostką ma styl epoki (wariant -1).
            recruit.player.gold = 1e6f
            assertTrue(recruit.buyTurret(recruit.player, 0))
            assertEquals(-1, recruit.player.turretVariant[0])
            // Dowódca zostaje podstawową ciężką jednostką.
            assertEquals(Defs.unit(era, Role.HEAVY).cost * 2, recruit.commanderCost(recruit.player))
        }
    }

    @Test
    fun aiPicksBothCardsAcrossSeeds() {
        for (art in expected.keys) {
            val era = Defs.eraOf(art)
            val picks = IntArray(2)
            for (seed in 0L until 60L) {
                val b = Battle(skirmish(era, 5000L + seed))
                picks[b.enemy.eraVariant + 1]++
            }
            assertTrue("$art: ${picks.toList()}", picks[0] > 5 && picks[1] > 5)
        }
    }

    private fun place(b: Battle, side: Int, def: UnitDef, x: Float): Fighter {
        assertTrue(b.spawn(b.sides[side], def))
        val f = b.units.last { it.alive && it.def === def && it.side == side }
        f.x = x
        f.prevX = Float.NaN
        return f
    }

    private fun quiet(era: Int): Battle {
        val b = Battle(skirmish(era))
        b.chooseVariant(1)
        b.ai = null
        for (s in b.sides) { s.slots = 0; s.gold = 0f }
        return b
    }

    @Test
    fun shieldReducesDamageForTwoSecondsAfterFirstHit() {
        val era = Defs.eraOf(EraArt.TRIBAL)
        val b = quiet(era)
        val shield = place(b, 0, Defs.recruitUnit(era)!!, 900f)
        val enemy = place(b, 1, Defs.unit(era, Role.MELEE), 900f + shield.radius + 20f)
        enemy.cd = 0f
        var hits = 0
        var lastHp = shield.hp
        val reductions = ArrayList<Float>()
        repeat(60 * 6) {
            b.step(); b.fx.count = 0
            if (shield.hp < lastHp) {
                hits++
                reductions += lastHp - shield.hp
                if (hits == 1) {
                    assertTrue(shield.traitUsed)
                    assertEquals(Trait.SHIELD_TIME, shield.traitT, 0.02f)
                }
                lastHp = shield.hp
            }
        }
        assertTrue("trafienia: $hits", hits >= 3)
        // Drugi cios (w czasie osłony) słabszy o 20%, późniejszy już pełny.
        assertEquals(reductions[0] * Trait.SHIELD_MUL, reductions[1], reductions[0] * 0.01f)
        assertEquals(reductions[0], reductions.last(), reductions[0] * 0.01f)
        assertEquals(1, b.player.traitUses)
    }

    @Test
    fun cartIsFasterUntilFirstShot() {
        val era = Defs.eraOf(EraArt.BRONZE)
        val b = quiet(era)
        val cart = place(b, 0, Defs.recruitUnit(era)!!, 400f)
        val x0 = cart.x
        b.step()
        val dashSpeed = (cart.x - x0) / Battle.STEP
        assertEquals(Defs.recruitUnit(era)!!.speed * Trait.DASH_MUL, dashSpeed, 0.5f)
        assertFalse(cart.traitUsed)
        place(b, 1, Defs.unit(era, Role.MELEE), 1000f)
        var guard = 0
        while (!cart.traitUsed && guard++ < 60 * 20) { b.step(); b.fx.count = 0 }
        assertTrue(cart.traitUsed)
        assertEquals(1, b.player.traitUses)
    }

    @Test
    fun ramBoostsOnlyItsFirstBaseHit() {
        val era = Defs.eraOf(EraArt.IRON)
        val b = quiet(era)
        val ram = place(b, 0, Defs.recruitUnit(era)!!, b.enemy.baseFront - 40f)
        ram.cd = 0f
        val hits = ArrayList<Float>()
        var last = b.enemy.baseHp
        repeat(60 * 6) {
            b.step(); b.fx.count = 0
            if (b.enemy.baseHp < last) { hits += last - b.enemy.baseHp; last = b.enemy.baseHp }
        }
        assertTrue("uderzenia: ${hits.size}", hits.size >= 2)
        assertEquals(hits[1] * Trait.RAM_MUL, hits[0], hits[1] * 0.01f)
        assertTrue(ram.traitUsed)
        assertEquals(1, b.player.traitUses)
    }

    @Test
    fun bracedShotAfterTwoSecondsStill() {
        val era = Defs.eraOf(EraArt.POWDER)
        val b = quiet(era)
        // Postawa obrony: strzelec za linią utrzymania stoi bez celu.
        b.player.hold = true
        val gunner = place(b, 0, Defs.recruitUnit(era)!!, 700f)
        repeat((Trait.BRACE_TIME / Battle.STEP).toInt() + 30) { b.step(); b.fx.count = 0 }
        assertFalse(gunner.moving)
        assertTrue(gunner.traitT >= Trait.BRACE_TIME)
        val target = place(b, 1, Defs.unit(era, Role.HEAVY), 700f + Defs.recruitUnit(era)!!.range - 20f)
        target.hp = 1e7f; target.maxHp = 1e7f
        gunner.cd = 0f
        var guard = 0
        while (b.player.traitUses == 0 && guard++ < 60 * 3) { b.step(); b.fx.count = 0 }
        assertEquals(1, b.player.traitUses)
        assertEquals(0f, gunner.traitT, 0.05f)
    }

    private fun snapshotTwin(a: Battle): Battle {
        val bytes = BattleSnapshot.encode(a)
        val r = assertNotNull(BattleSnapshot.decode(bytes)).let { BattleSnapshot.decode(bytes)!! }
        assertArrayEquals(bytes, BattleSnapshot.encode(r.battle))
        return r.battle
    }

    private fun run(b: Battle, n: Int) {
        var i = 0
        while (i++ < n && !b.over) { b.step(); b.fx.count = 0 }
    }

    @Test
    fun resumeDuringActiveShieldMatchesUninterruptedBattle() {
        val era = Defs.eraOf(EraArt.TRIBAL)
        val a = Battle(skirmish(era, 6911L))
        assertTrue(a.chooseVariant(1))
        a.playerAi = Ai(a, 0, AiPersonality.BALANCED)
        var guard = 0
        while (a.units.none { it.alive && it.def.trait == Trait.SHIELD && it.traitT > 0f } && guard++ < 60 * 600) {
            a.step(); a.fx.count = 0
        }
        val active = a.units.first { it.alive && it.def.trait == Trait.SHIELD && it.traitT > 0f }
        assertTrue(active.traitUsed)
        val b = snapshotTwin(a)
        val twin = b.units[a.units.indexOf(active)]
        assertTrue(twin.traitUsed)
        assertEquals(active.traitT, twin.traitT)
        assertEquals(a.player.traitUses, b.player.traitUses)
        run(a, 60 * 120)
        run(b, 60 * 120)
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
    }

    @Test
    fun resumeWithBracedGunnersAndRamsMatches() {
        for (art in arrayOf(EraArt.POWDER, EraArt.IRON, EraArt.BRONZE)) {
            val era = Defs.eraOf(art)
            val a = Battle(skirmish(era, 6920L + era))
            assertTrue(a.chooseVariant(1))
            a.playerAi = Ai(a, 0, AiPersonality.BALANCED)
            run(a, 60 * 150)
            val b = snapshotTwin(a)
            run(a, 60 * 90)
            run(b, 60 * 90)
            assertArrayEquals("$art", BattleSnapshot.encode(a), BattleSnapshot.encode(b))
        }
    }

    @Test
    fun olderFormatWithoutTraitsStillLoads() {
        val era = Defs.eraOf(EraArt.STEAM)
        val a = Battle(BattleConfig(Mode.SKIRMISH, 6930L, 1f, startEra = era, maxEra = era))
        assertTrue(a.chooseVariant(1))
        run(a, 60 * 40)
        val old = BattleSnapshot.encode(a, false, 0f, 0L, 23)
        val restored = assertNotNull(BattleSnapshot.decode(old)).let { BattleSnapshot.decode(old)!!.battle }
        assertTrue(restored.units.filter { it.active }.all { !it.traitUsed && it.traitT == 0f })
        assertEquals(0, restored.player.traitUses)
        run(a, 60 * 30)
        run(restored, 60 * 30)
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(restored))
        // Wersja 24 jest zarezerwowana dla równoległej zmiany formatu: nieobsługiwana tutaj.
        val v24 = BattleSnapshot.encode(a, false, 0f, 0L, 23).copyOf().also { it[7] = 24 }
        assertNull(BattleSnapshot.decode(v24))
    }

    @Test
    fun recruitReplayIsDeterministic() {
        val era = Defs.eraOf(EraArt.TRIBAL)
        fun play(): Battle {
            val b = Battle(BattleConfig(Mode.SKIRMISH, 6940L, 1f, startEra = era, maxEra = Defs.eraOf(EraArt.POWDER),
                playerBonuses = Bonuses(eliteUnlocked = true, rallyUnlocked = true), eraRecruits = true))
            val c = BattleController(b)
            b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
            run(b, 60 * 60 * 12)
            if (!b.over) assertTrue(c.surrender())
            return b
        }
        val x = play()
        val y = play()
        assertArrayEquals(BattleSnapshot.encode(x), BattleSnapshot.encode(y))
        val log = x.commandController!!.log()
        assertTrue(log.commands.any { it.kind == Replay.Kind.VARIANT })
        val replayed = Replay.verify(x.cfg, Replay.encode(log))
        assertEquals(x.score(), replayed.score)
        assertEquals(x.stepCount, replayed.steps)
    }
}
