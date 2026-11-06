package com.centurywar.meta

import com.centurywar.game.Orders
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Dzień 62: gwiazdki i punkty Rozkazów sztabu w profilu; starsze profile bez tych pól. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OrdersProfileTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun medalsOnlyForNewStarsAndBestPointsKept() {
        val p = Profile(File(folder.root, "p.json"))
        assertEquals(25, p.recordOrder(3, 2, 2_300))
        assertEquals(0, p.recordOrder(3, 2, 2_100))
        assertEquals(0, p.recordOrder(3, 1, 1_400))
        assertEquals(20, p.recordOrder(3, 3, 3_250))
        assertEquals(3, p.orderStars[3])
        assertEquals(3_250, p.orderPoints[3])
        assertEquals(0, p.recordOrder(99, 3, 3_000))
        assertEquals(3, p.orderStarTotal())
        assertEquals(3_250L, p.orderPointTotal())
    }

    @Test fun roundTripAndLegacyProfileWithoutOrders() {
        val file = File(folder.root, "profile.json")
        val p = Profile(file)
        p.name = "Sztab"
        p.recordOrder(0, 3, 3_400)
        p.recordOrder(8, 1, 1_050)
        val json = JSONObject(p.toJson())
        file.writeText(json.toString())
        val copy = Profile(file).apply { load() }
        assertArrayEquals(p.orderStars, copy.orderStars)
        assertArrayEquals(p.orderPoints, copy.orderPoints)

        // Profil sprzed dnia 62: bez obu tablic — zera, reszta bez zmian.
        json.remove("orderStars"); json.remove("orderPoints")
        val legacyFile = File(folder.root, "legacy.json").apply { writeText(json.toString()) }
        val legacy = Profile(legacyFile).apply { load() }
        assertEquals("Sztab", legacy.name)
        assertArrayEquals(IntArray(Orders.COUNT), legacy.orderStars)
        assertArrayEquals(IntArray(Orders.COUNT), legacy.orderPoints)

        // Dzień 66: profil z partii 1 (tablice po 9 pozycji) — wartości zostają, partia 2 = zera.
        assertEquals(18, Orders.COUNT)
        json.put("orderStars", JSONArray(listOf(3, 2, 1, 0, 0, 0, 0, 0, 2)))
        json.put("orderPoints", JSONArray(listOf(3400, 2300, 1100, 0, 0, 0, 0, 0, 2050)))
        val nineFile = File(folder.root, "nine.json").apply { writeText(json.toString()) }
        val nine = Profile(nineFile).apply { load() }
        assertEquals(Orders.COUNT, nine.orderStars.size)
        assertArrayEquals(intArrayOf(3, 2, 1, 0, 0, 0, 0, 0, 2) + IntArray(Orders.BATCH), nine.orderStars)
        assertEquals(3400 + 2300 + 1100 + 2050L, nine.orderPointTotal())
        assertEquals(8, nine.orderStarTotal())
        // Dzień 67: partia 2 płaci 15/20/30 za nowe gwiazdki.
        assertEquals(15, nine.recordOrder(9, 1, 1_300))
        assertEquals(50, nine.recordOrder(9, 3, 3_100))
        assertEquals(0, nine.recordOrder(9, 3, 3_200))
        assertEquals(11, nine.orderStarTotal())
        nineFile.writeText(nine.toJson())
        val nineCopy = Profile(nineFile).apply { load() }
        assertEquals(Orders.COUNT, JSONObject(nine.toJson()).getJSONArray("orderStars").length())
        assertEquals(3, nineCopy.orderStars[9])

        // Uszkodzone wartości są przycinane do zakresu.
        json.put("orderStars", JSONArray(listOf(9, -2, 3)))
        val clampedFile = File(folder.root, "clamped.json").apply { writeText(json.toString()) }
        val clamped = Profile(clampedFile).apply { load() }
        assertEquals(3, clamped.orderStars[0])
        assertEquals(0, clamped.orderStars[1])
        assertEquals(3, clamped.orderStars[2])
    }
}
