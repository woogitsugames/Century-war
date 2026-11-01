package com.centurywar.meta

/**
 * Most do osiągnięć Google Play Games Services.
 * PGS odblokowuje osiągnięcia po identyfikatorze skonfigurowanym w Play Console;
 * pgsKey w katalogu jest kluczem logicznym mapowanym w PlayGamesIds.
 */
interface OnlineAchievements {
    fun unlock(pgsKey: String)

    object NoOp : OnlineAchievements {
        override fun unlock(pgsKey: String) {}
    }
}
