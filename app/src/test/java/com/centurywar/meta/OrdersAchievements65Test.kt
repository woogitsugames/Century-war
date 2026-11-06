package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Orders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** D65: cztery osiągnięcia za Rozkazy sztabu (d62) — progi, medale, teksty i warunek obrony bez wież; d67: 18 rozkazów i 54★. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrdersAchievements65Test {
    private val ids = listOf("orders_perfect_1", "orders_all_9", "orders_stars_27", "orders_hold_no_turrets")

    private fun profile() = Profile(File.createTempFile("cw-ach-65-", ".json").apply { delete(); deleteOnExit() })

    private fun won(cfg: BattleConfig): Battle = Battle(cfg).also {
        // Kilka kroków: część rozkazów ustawia bazy dopiero w pierwszych krokach.
        var guard = 0
        while (!it.over && guard++ < 120) {
            // Start w epoce z wariantem (np. Para) czeka na wybór wariantu/doktryny.
            if (it.pendingVariant != null) it.chooseVariant(0)
            if (it.pendingChoice != null) it.chooseDoctrine(0)
            it.enemy.baseHp = 0f
            it.step()
        }
        assertTrue("${cfg.stageIndex}: wygrana", it.over && it.winner == 0)
    }

    private fun record(p: Profile, b: Battle): Set<String> =
        Achievements.onBattleEnd(p, b, b.cfg, b.winner == 0).map { it.id }.toSet()

    @Test fun definitionsTextsAndThresholds() {
        assertEquals(ids, Achievements.all.takeLast(4).map { it.id })
        for (id in ids) {
            val a = Achievements.byId(id)!!
            assertEquals("cw_$id", a.pgsKey)
            assertFalse(a.hidden)
            assertEquals(10, L.raw("ach_$id")?.size)
            assertEquals(10, L.raw("ach_${id}_d")?.size)
            assertTrue(L.raw("ach_$id")!!.all { it.isNotBlank() })
        }
        // D67 (decyzja właściciela 6.11): progi liczą WSZYSTKIE rozkazy. Próg rośnie sam z
        // Orders.COUNT, ale opisy w L.kt (part210) i arkusz PGS mają liczby wpisane na sztywno —
        // stąd sprawdzenie, że każdy z 10 opisów zawiera bieżący próg. Gdy dojdzie partia 3,
        // ten test padnie i trzeba poprawić teksty oraz przegenerować tools/pgs_sheet.py.
        val all = Achievements.byId("orders_all_9")!!
        val stars = Achievements.byId("orders_stars_27")!!
        assertEquals(Orders.COUNT.toLong(), all.threshold)
        assertEquals((Orders.COUNT * Orders.MAX_STARS).toLong(), stars.threshold)
        assertEquals(18L, all.threshold)
        assertEquals(54L, stars.threshold)
        assertEquals(300, all.medalReward)
        assertEquals(900, stars.medalReward)
        for (a in listOf(all, stars)) {
            val n = a.threshold.toString()
            for ((i, d) in L.raw("ach_${a.id}_d")!!.withIndex())
                assertTrue("${a.id}[$i] bez liczby $n: $d", Regex("(^|\\D)$n(\\D|$)").containsMatchIn(d))
        }
    }

    @Test fun starAchievementsFollowRecordedOrderStarsAndPayMedals() {
        val p = profile()
        // Wygrana rozkazu 1 na 2★: nic z trzech osiągnięć gwiazdkowych.
        p.recordOrder(0, 2, 2000)
        var got = record(p, won(Orders.config(Orders.all[0], 1L)))
        assertTrue(got.none { it.startsWith("orders_") && it != "orders_hold_no_turrets" })
        // Pierwszy rozkaz na 3★.
        p.recordOrder(0, 3, 3400)
        val medals = p.medals
        got = record(p, won(Orders.config(Orders.all[0], 2L)))
        assertTrue("orders_perfect_1" in got)
        assertTrue(p.medals - medals >= 40)
        // Wszystkie 18 wykonane (≥ 1★), ale nie 54★.
        for (i in 1 until Orders.COUNT) p.recordOrder(i, 1, 1000)
        got = record(p, won(Orders.config(Orders.all[1], 3L)))
        assertTrue("orders_all_9" in got)
        assertFalse("orders_stars_27" in got)
        assertEquals(3L + (Orders.COUNT - 1), Achievements.progress(p, Achievements.byId("orders_stars_27")!!))
        // Komplet gwiazdek.
        for (i in 0 until Orders.COUNT) p.recordOrder(i, 3, 3500)
        got = record(p, won(Orders.config(Orders.all[2], 4L)))
        assertTrue("orders_stars_27" in got)
        // Jednorazowo.
        assertTrue(record(p, won(Orders.config(Orders.all[2], 5L))).none { it.startsWith("orders_") })
    }

    @Test fun holdWithoutTurretsOnlyWhereTurretsAllowed() {
        val p = profile()
        // Rozkaz 1 (obrona, wieże zabronione) — nie liczy się.
        assertFalse("orders_hold_no_turrets" in record(p, won(Orders.config(Orders.all[0], 7L))))
        // Rozkaz 2 (atak) bez wież — nie liczy się.
        assertFalse("orders_hold_no_turrets" in record(p, won(Orders.config(Orders.all[1], 8L))))
        // Rozkaz 6 (obrona z wieżami), wieża zbudowana — nie liczy się.
        val built = won(Orders.config(Orders.all[5], 9L))
        built.player.turretsBuilt = 1
        assertFalse("orders_hold_no_turrets" in record(p, built))
        // Przegrana obrona bez wież — nie liczy się.
        val lost = Battle(Orders.config(Orders.all[8], 10L)).also { it.surrender() }
        assertFalse("orders_hold_no_turrets" in record(p, lost))
        // Rozkaz 9 (obrona z wieżami) wygrany bez wież.
        val ok = won(Orders.config(Orders.all[8], 11L))
        assertTrue(ok.player.slots > 0)
        assertTrue("orders_hold_no_turrets" in record(p, ok))
        // Inne tryby nie liczą się.
        val p2 = profile()
        assertFalse("orders_hold_no_turrets" in record(p2, won(BattleConfig(Mode.SKIRMISH, 12L, 1f))))
    }
}
