package com.centurywar.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NickModerationTest {
    private val player = "a16036d5-800a-4898-aea3-11b6548de7b4"

    @Test fun detectsProfanityInAllTenLanguages() {
        val samples = listOf(
            "K.U.R.W.@" ,  // pl
            "F.U.C.K",     // en
            "Sch3iße",    // de
            "P3NDEJ0",    // es
            "Mérdè",      // fr
            "Str0nz0",    // it
            "P0RR@",      // pt
            "ХУЙ",        // uk
            "K@NKER",     // nl
            "S!KTIR",     // tr
        )
        for (nick in samples) {
            assertTrue(NickModeration.forbiddenNick(nick), "Nie wykryto: $nick")
            assertEquals(NickModeration.fallbackNick(player), NickModeration.publicNick(nick, player))
        }
    }

    @Test fun catchesSeparatedWordsAndForeignLookalikeLetters() {
        assertTrue(NickModeration.forbiddenNick("xx f u c k yy"))
        assertTrue(NickModeration.forbiddenNick("kυrwa"))
        assertTrue(NickModeration.forbiddenNick("Kаnkеr")) // mieszanka łaciny i cyrylicy
        assertTrue(NickModeration.forbiddenNick("kurwa99"))
        assertFalse(NickModeration.forbiddenNick("Classical"))
        assertFalse(NickModeration.forbiddenNick("Computadora"))
        assertFalse(NickModeration.forbiddenNick("Łucja_2026"))
    }

    @Test fun fallbackIsStableValidAndDoesNotLeakPlayerId() {
        val other = "b16036d5-800a-4898-aea3-11b6548de7b4"
        val nickname = NickModeration.fallbackNick(player)
        assertTrue(Regex("^Dowódca-[A-F0-9]{4}$").matches(nickname))
        assertTrue(validNick(nickname))
        assertEquals(nickname, NickModeration.publicNick("f@ck", player))
        assertEquals(nickname, NickModeration.publicNick("<script>", player))
        assertEquals(nickname, NickModeration.publicNick("", player))
        assertEquals(nickname, NickModeration.fallbackNick(player))
        assertNotEquals(nickname, NickModeration.fallbackNick(other))
        assertEquals("Łucja_2026", NickModeration.publicNick("Łucja_2026", player))
    }
}
