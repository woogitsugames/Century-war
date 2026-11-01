package com.centurywar.meta

/** Odblokowania są wyłącznie kosmetyczne i nie zmieniają zasad bitwy. */
object BannerCatalog {
    const val COUNT = 12
    const val DEFAULT = 0
    const val VALID_MASK = (1 shl COUNT) - 1

    /** Aktualny warunek nagrody; odblokowana nagroda pozostaje na stałe w profilu. */
    fun eligible(profile: Profile, id: Int): Boolean = when (id) {
        0 -> true
        1 -> profile.totalStars() >= 12
        2 -> profile.totalStars() >= 36
        3 -> profile.totalStars() >= 72
        4 -> profile.totalStars() >= 108
        5 -> "win_10" in profile.unlockedAchievements
        6 -> "wave_25" in profile.unlockedAchievements
        7 -> "orbital" in profile.unlockedAchievements
        8 -> "without_turrets" in profile.unlockedAchievements
        9 -> profile.bestDailyChallengeStreak >= 3
        10 -> profile.bestDailyChallengeStreak >= 7
        11 -> profile.bestDailyChallengeStreak >= 14
        else -> false
    }
}
