package com.centurywar.game

import com.centurywar.game.art.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test fun wheelsMatchDistanceAndSize() {
        val distance = 48f
        val size = 1.75f
        val radius = 16f
        val walk = distance * Motion.PHASE_PER_WORLD
        assertEquals(distance / size, Motion.distance(walk, size), 0.0001f)
        assertEquals(distance / (size * radius), Motion.wheelAngle(walk, size, radius), 0.0001f)
        assertEquals((distance / size) % 5f, Motion.trackOffset(walk, size, 5f), 0.0001f)
        assertEquals(Motion.wheelAngle(walk, size, radius), Motion.wheelAngle(walk, size, radius), 0f)
    }

    @Test fun stoppedWheelsAndTracksKeepTheirAngle() {
        val lastWalk = 2.46f
        val rotation = Motion.wheelAngle(lastWalk, 1.3f, 13f)
        val offset = Motion.trackOffset(lastWalk, 1.3f, 5f)
        // Wygaśnięcie stride nie wpływa na przebytą drogę ani jej fazę.
        assertEquals(rotation, Motion.wheelAngle(lastWalk, 1.3f, 13f), 0f)
        assertEquals(offset, Motion.trackOffset(lastWalk, 1.3f, 5f), 0f)
    }

    @Test fun mechFootIsPlantedDuringStance() {
        val size = 1.75f
        val phase0 = 0.3f
        val traveled = 5f
        val phase1 = phase0 + traveled * Motion.PHASE_PER_WORLD
        assertEquals(0f, traveled + size * (Motion.mechFootX(phase1, size) -
            Motion.mechFootX(phase0, size)), 0.001f)
        assertEquals(0f, Motion.mechFootLift(phase0), 0f)
        assertTrue(Motion.mechFootLift(Motion.PI * 1.5f) > 0.99f)
        assertEquals(Motion.mechFootX(0f, size), Motion.mechFootX(Motion.TAU, size), 0.0001f)
    }

    @Test fun recoilHasQuickImpulseAndSlowReturn() {
        assertEquals(0f, Motion.recoil(1f), 0f)
        assertEquals(1f, Motion.recoil(0.86f), 0.0001f)
        assertTrue(Motion.recoil(0.65f) > Motion.recoil(0.2f))
        assertEquals(0f, Motion.recoil(0f), 0f)
        assertTrue(Motion.hullTilt(0.86f) > 2f)
        assertTrue(Motion.hullTilt(0.2f) < 0f)
        assertEquals(0f, Motion.hullTilt(0f), 0f)
    }
}
