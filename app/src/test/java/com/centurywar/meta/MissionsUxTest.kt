package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Test

class MissionsUxTest {
    @Test
    fun pendingClaimsCountsOnlyUnclaimedCompletedMissions() {
        val state = MissionState()
        state.daily.add(MissionItem("d_a", MissionType.BATTLES, 2, 10, progress = 2))
        state.daily.add(MissionItem("d_b", MissionType.WINS, 1, 10, progress = 0))
        state.daily.add(MissionItem("d_c", MissionType.KILLS, 5, 10, progress = 5, claimed = true))
        state.weekly.add(MissionItem("w_a", MissionType.ELITES, 3, 30, progress = 3))
        assertEquals(2, Missions.pendingClaims(state))
        state.daily[0].claimed = true
        assertEquals(1, Missions.pendingClaims(state))
        state.weekly[0].claimed = true
        assertEquals(0, Missions.pendingClaims(state))
    }

    @Test
    fun loginTrackDisplaysSameRewardsAsActualClaims() {
        val rewards = intArrayOf(5, 8, 12, 17, 23, 30, 40)
        for (day in 1..7) {
            assertEquals(rewards[day - 1], Missions.loginRewardFor(day, 0))
            assertEquals(rewards[day - 1] + 3, Missions.loginRewardFor(day, 48))
        }
        assertEquals(5, Missions.loginRewardFor(-9, 0))
        assertEquals(40, Missions.loginRewardFor(99, 0))
    }
}
