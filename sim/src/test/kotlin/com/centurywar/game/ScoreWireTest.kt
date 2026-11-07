package com.centurywar.game

import com.centurywar.meta.ScoreWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Zlecenie 175 (dokończone przez koordynatora): stabilny JSON zgłoszenia wyniku, cytowanie znaków
 * specjalnych i Unicode, wektory podpisu HMAC-SHA256 (liczone niezależnie w Pythonie) i nagłówki.
 */
class ScoreWireTest {
    private val secret = "test-secret".toByteArray(StandardCharsets.UTF_8)
    private val log = Replay.Log(steps = 120, commands = listOf(Replay.Command(3, Replay.Kind.QUEUE, 1)))

    @Test fun signatureMatchesIndependentVectors() {
        assertEquals("3e96cd6d886cbb4ef5b72470a1f59a1872efc137c9619d24dfabbd77ef61a38e",
            ScoreWire.sign(secret, "1700000000", "n-1", "POST", "/v1/score", "{\"a\":1}"))
        assertEquals("e73eea654352900a8674562f4930a12036bdf1b508ac608e7160b22e9f11ea32",
            ScoreWire.sign(secret, "1700000000", "n-1", "POST", "/v1/score", "{\"a\":1}", "inst-9"))
        // Treść podpisywana jako UTF-8, nie w kodowaniu platformy.
        assertEquals("2f580ade9a6f857087f1b7677c3a5392d1b6ff15dfef84f030b0f2de046fc9d4",
            ScoreWire.sign(secret, "1700000000", "n-1", "POST", "/v1/score", "{\"nick\":\"Żółw\"}"))
    }

    @Test fun headersCarrySameSignature() {
        val h = ScoreWire.headers(secret, "1700000000", "n-1", "POST", "/v1/score", "{\"a\":1}", "inst-9")
        assertEquals("1700000000", h.timestamp)
        assertEquals("n-1", h.nonce)
        assertEquals("inst-9", h.installationSecret)
        assertEquals(ScoreWire.sign(secret, "1700000000", "n-1", "POST", "/v1/score", "{\"a\":1}", "inst-9"), h.signature)
        assertNotEquals(h.signature,
            ScoreWire.sign(secret, "1700000000", "n-1", "POST", "/v1/score", "{\"a\":1}"))
    }

    @Test fun endlessSubmissionIsStable() {
        val cfg = BattleConfig(Mode.ENDLESS, 42L, 1f)
        val json = ScoreWire.submission(cfg, log, 1234L, "Nick", "p-1", intArrayOf(1, 0, 2), 0)
        assertEquals("{\"board\":\"endless\",\"player\":\"p-1\",\"nick\":\"Nick\",\"gameVersion\":\"" +
            SimVersion.PROTOCOL + "\",\"seed\":42,\"replay\":\"" + Replay.encode(log) +
            "\",\"score\":1234,\"research\":[1,0,2]}", json)
        assertEquals(json, ScoreWire.submission(cfg, log, 1234L, "Nick", "p-1", intArrayOf(1, 0, 2), 0))
    }

    @Test fun boardSpecificFields() {
        val daily = ScoreWire.submission(BattleConfig(Mode.DAILY, 20400L, 1f), log, 1L, "N", "p", intArrayOf(), 0)
        assertTrue(daily.startsWith("{\"board\":\"daily\""))
        assertTrue(daily.endsWith(",\"research\":[],\"day\":20400}"))
        val weekly = ScoreWire.submission(BattleConfig(Mode.ENDLESS, 7L, 1f, weekly = 2914L), log, 1L, "N", "p", intArrayOf(), 0)
        assertTrue(weekly.startsWith("{\"board\":\"weekly\""))
        assertTrue(weekly.endsWith(",\"day\":2914}"))
        val camp = ScoreWire.submission(BattleConfig(Mode.CAMPAIGN, 7L, 1f, stageIndex = 0, tutorial = true),
            log, 1L, "N", "p", intArrayOf(), 0)
        assertTrue(camp.endsWith(",\"stage\":0,\"tutorial\":true}"))
        val sk = ScoreWire.submission(BattleConfig(Mode.SKIRMISH, 7L, 1f), log, 1L, "N", "p", intArrayOf(), 9)
        assertTrue("trudność przycięta do 0..3", sk.endsWith(",\"difficulty\":3}"))
        assertFalse(sk.contains("\"day\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ordersHaveNoServerBoard() {
        ScoreWire.submission(BattleConfig(Mode.ORDERS, 7L, 1f), log, 1L, "N", "p", intArrayOf(), 0)
    }

    @Test fun nickIsQuotedSafely() {
        val nick = "A\"b\\c\nd\re\tf\b\u000cg\u0001Żółw🙂"
        val json = ScoreWire.submission(BattleConfig(Mode.ENDLESS, 1L, 1f), log, 1L, nick, "p", intArrayOf(), 0)
        assertTrue(json.contains("\"nick\":\"A\\\"b\\\\c\\nd\\re\\tf\\b\\fg\\u0001Żółw🙂\""))
        assertFalse("brak surowych znaków sterujących", json.any { it < ' ' })
    }
}
