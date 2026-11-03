package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nagrana muzyka: wybór źródła, przenikanie i zwalnianie odtwarzaczy (atrapa, czysty JVM). */
class RecordedMusicTest {
    private class FakePlayer(val name: String, val looping: Boolean) : TrackPlayer {
        var started = false
        var released = false
        var level = -1f
        var finished = false
        var maintained = 0
        override fun maintain() { check(!released); maintained++ }
        override fun start() { check(!released); started = true }
        override fun setVolume(value: Float) { check(!released) { "głośność po zwolnieniu: $name" }; level = value }
        override fun release() { check(!released) { "podwójne zwolnienie: $name" }; released = true }
        override val isPlaying: Boolean get() = started && !released && !finished
    }

    private class FakeFactory(private val broken: Set<String> = emptySet()) : TrackPlayerFactory {
        val all = ArrayList<FakePlayer>()
        var maxOpen = 0
        @Synchronized override fun open(name: String, looping: Boolean): TrackPlayer? {
            if (name in broken) return null
            val p = FakePlayer(name, looping)
            all += p
            maxOpen = maxOf(maxOpen, openCount)
            return p
        }
        val openCount: Int @Synchronized get() = all.count { !it.released }
        fun live(name: String): FakePlayer? = all.lastOrNull { it.name == name && !it.released }
    }

    private fun catalog(vararg names: String) = MusicCatalog.fromFileNames(names.map { "$it.ogg" }.toTypedArray())

    private fun RecordedMixer.run(seconds: Float, target: Int, intensity: Float = 0f,
                                  volume: Float = 1f, duck: Float = 1f) {
        var t = 0f
        while (t < seconds - 1e-4f) { update(0.04f, target, intensity, volume, duck); t += 0.04f }
    }

    @Test fun loopsAreMaintainedEveryStep() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("era1_calm", "era1_battle", "era2_calm"), f)
        m.run(1f, 1)
        val calm = f.live("era1_calm")!!
        val battle = f.live("era1_battle")!!
        assertEquals(25, calm.maintained)
        assertEquals(25, battle.maintained)
        // W trakcie przenikania epok obie pary dostają krok (łańcuch okrążeń nie może stanąć).
        m.run(1f, 2)
        assertEquals(50, calm.maintained)
        assertTrue(f.live("era2_calm")!!.maintained > 20)
    }

    @Test fun deviceGainsKeepMusicUnderEffects() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("menu", "victory"), f, {}, RecordedMixer.LOOP_GAIN, RecordedMixer.STINGER_GAIN)
        m.run(1f, Music.MENU, volume = 0.5f)
        val menu = f.live("menu")!!
        assertEquals("suwak 0,5 x LOOP_GAIN", 0.05f, menu.level, 1e-4f)
        // -16 LUFS pliku + 20 log10(0,05) = ok. -42 LUFS; efekty bitwy ok. -35 LUFS (README).
        val lufs = -16f + 20f * kotlin.math.log10(menu.level)
        assertTrue("muzyka 6-9 dB pod efektami: $lufs", -35f - lufs in 6f..9f)
        m.run(1f, Music.MENU, volume = 1f)
        assertEquals("suwak działa liniowo", 0.1f, menu.level, 1e-4f)
        m.run(1f, Music.MENU, volume = 0f)
        assertEquals(0f, menu.level, 0f)
        assertTrue(m.playStinger(true))
        m.run(0.2f, Music.MENU, volume = 0.5f)
        assertEquals("przerywnik +6 dB nad pętlą", 0.1f, f.live("victory")!!.level, 1e-4f)
    }

    @Test fun catalogListsAllSunoTracks() {
        assertEquals(21, MusicCatalog.ALL.size)
        assertTrue("menu" in MusicCatalog.ALL && "era8_battle" in MusicCatalog.ALL && "defeat" in MusicCatalog.ALL)
        val c = MusicCatalog.fromFileNames(arrayOf("menu.ogg", "era3_battle.ogg", "era4_calm.wav", "obcy.ogg"))
        assertTrue(c.hasMenu)
        assertTrue(c.covers(Music.MENU))
        assertTrue(c.covers(3))
        assertFalse("tylko .ogg", c.covers(4))
        assertFalse(c.covers(0))
        assertTrue(MusicCatalog.fromFileNames(null).isEmpty)
        assertTrue(MusicCatalog.fromAssets(null).isEmpty)
    }

    @Test fun sourceChoicePerEra() {
        val music = Music(catalog("era2_calm", "era5_battle"), FakeFactory(), null)
        assertFalse("menu bez pliku = synteza", music.usesRecorded)
        music.setEra(2)
        assertTrue(music.usesRecorded)
        music.setEra(3)
        assertFalse(music.usesRecorded)
        music.setEra(5)
        assertTrue(music.usesRecorded)
        music.setEra(Music.MENU)
        assertFalse(music.usesRecorded)

        val withMenu = Music(catalog("menu"), FakeFactory(), null)
        withMenu.setEra(Music.MENU)
        assertTrue(withMenu.usesRecorded)
        withMenu.setEra(0)
        assertFalse(withMenu.usesRecorded)

        // Bez plików (np. testy, Robolectric) wyłącznie synteza, przerywnika nie ma.
        val none = Music()
        none.setEra(4)
        assertFalse(none.usesRecorded)
        assertFalse(none.playResultStinger(true))
    }

    @Test fun crossfadeCurve() {
        assertEquals(0f, RecordedMixer.battleWeight(0f), 0f)
        assertEquals(0f, RecordedMixer.battleWeight(0.25f), 0f)
        assertEquals(0.5f, RecordedMixer.battleWeight(0.5f), 1e-6f)
        assertEquals(1f, RecordedMixer.battleWeight(0.75f), 0f)
        assertEquals(1f, RecordedMixer.battleWeight(1f), 0f)
        assertEquals(0f, RecordedMixer.battleWeight(Float.NaN), 0f)
        var prev = 0f
        for (i in 0..100) {
            val w = RecordedMixer.battleWeight(i / 100f)
            assertTrue("monotoniczna", w >= prev)
            prev = w
        }
    }

    @Test fun calmAndBattleStartTogetherAndFollowSmoothedIntensity() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("era3_calm", "era3_battle"), f)
        m.update(0.04f, 3, 0f, 0.8f, 1f)
        val calm = f.live("era3_calm")!!
        val battle = f.live("era3_battle")!!
        assertTrue(calm.started && battle.started && calm.looping && battle.looping)
        assertTrue("start z ciszy łagodnie (plik nie ma własnego wejścia)", calm.level < 0.3f)
        m.run(RecordedMixer.START_FADE_S, 3, volume = 0.8f)
        assertEquals("po wejściu pełna głośność", 0.8f, calm.level, 1e-4f)
        assertEquals(0f, battle.level, 1e-4f)
        // Skok intensywności: wygładzenie ~1,5 s, w połowie mniej więcej pół na pół.
        m.run(0.76f, 3, intensity = 1f, volume = 0.8f)
        assertEquals(0.5f, m.mix, 0.05f)
        // Równa moc: suma mocy obu wersji stała (liniowo byłoby −3 dB w połowie).
        assertEquals(0.64f, calm.level * calm.level + battle.level * battle.level, 1e-3f)
        m.run(0.8f, 3, intensity = 1f, volume = 0.8f)
        assertEquals(1f, m.mix, 0f)
        assertEquals(0f, calm.level, 1e-4f)
        assertEquals(0.8f, battle.level, 1e-4f)
        assertEquals(2, f.openCount)
    }

    @Test fun singleVersionPlaysAlone() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("era6_battle"), f)
        m.run(2f, 6, intensity = 0f)
        assertEquals(1, f.openCount)
        assertEquals("jedyna wersja gra pełną głośnością także przy spokoju", 1f, f.live("era6_battle")!!.level, 1e-4f)
        assertNull(f.live("era6_calm"))
    }

    @Test fun eraChangeCrossfadesAndReleasesOldPair() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("menu", "era0_calm", "era0_battle", "era1_calm", "era1_battle"), f)
        m.run(1f, Music.MENU)
        val menu = f.live("menu")!!
        m.update(0.04f, 0, 0f, 1f, 1f)
        assertEquals(3, f.openCount)
        m.run(1f, 0)
        assertTrue("w połowie przenikania oba słychać: ${menu.level}", menu.level in 0.3f..0.8f)
        m.run(1.1f, 0)
        assertTrue(menu.released)
        assertEquals(2, f.openCount)
        assertEquals(1f, f.live("era0_calm")!!.level, 1e-4f)
        // Powrót do pary wychodzącej w trakcie przenikania nie otwiera plików ponownie.
        m.update(0.04f, 1, 0f, 1f, 1f)
        val opened = f.all.size
        m.run(0.5f, 1)
        m.update(0.04f, 0, 0f, 1f, 1f)
        assertEquals(opened, f.all.size)
        assertEquals(0, m.currentKey)
    }

    @Test fun noLeaksAfterManyEraChangesAndPauses() {
        val names = MusicCatalog.ALL.toTypedArray()
        val f = FakeFactory()
        val m = RecordedMixer(catalog(*names), f)
        val rng = java.util.Random(142)
        repeat(400) { i ->
            val target = when (rng.nextInt(12)) { 0 -> Music.MENU; 1 -> RecordedMixer.NONE; else -> rng.nextInt(9) }
            m.run(rng.nextInt(80) * 0.04f, target, intensity = rng.nextFloat())
            if (i % 37 == 0) m.playStinger(rng.nextBoolean())
            assertTrue("najwyżej 4 odtwarzacze: ${f.openCount}", f.openCount <= 4)
            assertEquals(f.openCount, m.openPlayers)
            if (i % 50 == 49) {
                m.releaseAll() // pauza Activity
                assertEquals(0, f.openCount)
            }
        }
        assertTrue(f.maxOpen <= 4)
        m.releaseAll()
        assertEquals(0, f.openCount)
        assertTrue(f.all.size > 50)
    }

    @Test fun noneTargetFadesOutAndReleases() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("menu"), f)
        m.run(0.5f, Music.MENU)
        m.run(2.1f, RecordedMixer.NONE)
        assertEquals(0, f.openCount)
    }

    @Test fun stingerMutesLoopAndPlaysOnce() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("era2_calm", "victory"), f)
        m.run(1f, 2)
        assertFalse("brak pliku porażki", m.playStinger(false))
        assertTrue(m.playStinger(true))
        val sting = f.live("victory")!!
        assertFalse(sting.looping)
        m.run(0.6f, 2)
        assertEquals(0f, f.live("era2_calm")!!.level, 1e-4f)
        assertEquals(1f, sting.level, 1e-4f)
        // Zmiana ekranu w trakcie przerywnika nie przekracza limitu odtwarzaczy.
        m.run(0.2f, Music.MENU)
        assertTrue(f.live("era2_calm") == null)
        sting.finished = true
        m.update(0.04f, 2, 0f, 1f, 1f)
        assertTrue(sting.released)
        m.run(RecordedMixer.STINGER_GAP_S - 0.05f, 2)
        assertEquals("krótka cisza po przerywniku", 0f, f.live("era2_calm")!!.level, 1e-4f)
        m.run(RecordedMixer.STINGER_RETURN_S / 2f + 0.05f, 2)
        val mid = f.live("era2_calm")!!.level
        assertTrue("płynny powrót: $mid", mid in 0.35f..0.65f)
        m.run(RecordedMixer.STINGER_RETURN_S / 2f + 0.1f, 2)
        assertEquals("pętla wraca po przerywniku", 1f, f.live("era2_calm")!!.level, 1e-3f)
    }

    @Test fun duckAndVolumeApply() {
        val f = FakeFactory()
        val m = RecordedMixer(catalog("menu"), f)
        m.run(0.7f, Music.MENU, volume = 0.5f)
        assertEquals(0.5f, f.live("menu")!!.level, 1e-4f)
        m.run(0.5f, Music.MENU, volume = 0.5f, duck = 0.4f)
        assertEquals(0.2f, f.live("menu")!!.level, 0.01f)
        m.run(3f, Music.MENU, volume = 0.5f)
        assertEquals(0.5f, f.live("menu")!!.level, 1e-3f)
        m.run(0.1f, Music.MENU, volume = 0f)
        assertEquals(0f, f.live("menu")!!.level, 0f)
    }

    @Test fun brokenFileFallsBackToSynth() {
        val broken = ArrayList<String>()
        val cat = catalog("era4_calm")
        val m = RecordedMixer(cat, FakeFactory(setOf("era4_calm")), { broken += it })
        m.run(0.2f, 4)
        assertEquals(listOf("era4_calm"), broken)
        assertFalse(cat.covers(4))
        assertEquals(0, m.openPlayers)
    }

    @Test fun controlThreadReleasesPlayersOnPause() {
        val f = FakeFactory()
        val rec = RecordedMusic(catalog("menu", "era1_calm", "era1_battle"), f, {}, { 1f })
        rec.target = Music.MENU
        repeat(5) { round ->
            rec.setActive(true)
            val t = rec.workerForTest()!!
            val deadline = System.currentTimeMillis() + 3000
            while (synchronized(f) { f.openCount } == 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
            rec.target = if (round % 2 == 0) 1 else Music.MENU
            Thread.sleep(60)
            rec.setActive(false)
            t.join(3000)
            assertFalse(t.isAlive)
            assertEquals("runda $round", 0, f.openCount)
        }
        assertTrue(f.all.isNotEmpty())
    }
}
