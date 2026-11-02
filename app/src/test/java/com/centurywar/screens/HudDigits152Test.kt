package com.centurywar.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HudDigits152Test {
    @Test fun numbersKeepTheOriginalDigits() {
        val buffer = HudDigits152()
        for (n in intArrayOf(Int.MIN_VALUE, -1, 0, 1, 9, 10, 100, 12345678, Int.MAX_VALUE)) {
            buffer.setNumber(n)
            assertEquals(n.toString(), String(buffer.chars, 0, buffer.length))
        }
    }

    @Test fun clocksKeepTheOriginalDigits() {
        val buffer = HudDigits152()
        for (seconds in intArrayOf(0, 1, 9, 10, 59, 60, 61, 599, 3600, 36001, Int.MAX_VALUE)) {
            buffer.setClock(seconds)
            val expected = (seconds / 60).toString() + ":" +
                (if (seconds % 60 < 10) "0" else "") + seconds % 60
            assertEquals(expected, String(buffer.chars, 0, buffer.length))
        }
    }
}
