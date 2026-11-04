package com.centurywar.meta

import com.centurywar.game.Daily

/**
 * R12 (pozycja 5): zasady sprzed premiery w wersjach testowych — oznaczenia zamiast zmian.
 *
 * Przed [Daily.RELEASE_DAY] wyzwanie dnia nie ma dowódcy, pogody ani wydarzeń dnia, a oblężenie
 * tygodnia nie ma zestawów tematycznych ([com.centurywar.game.Weekly.firstRuleSetWeek]).
 * Zasad nie zmieniamy (rankingi, powtórki, `:sim`); w wersji testowej gra tylko o tym mówi:
 * dopisek „Dostępne od premiery” przy osiągnięciach nie do zdobycia i jedna linia w wyzwaniu.
 *
 * Czysta logika bez Androida: flagę wersji testowej podaje wywołujący (w grze
 * `BuildConfig.TESTER_SURVEY`, ten sam warunek co ankieta testera), a dzień — zegar ekranu.
 * Nic nie trafia do profilu, więc napis nie zależy od zapisu ani synchronizacji.
 */
object PreReleaseRules {

    /** Napisy tylko w wersji testowej i tylko przed dniem premiery (sam dzień premiery: już nie). */
    fun active(testerBuild: Boolean, day: Long, releaseDay: Long = Daily.RELEASE_DAY): Boolean =
        testerBuild && day < releaseDay

    /**
     * Ten sam warunek co `testerSurveyRelease` w `app/build.gradle.kts` (release bez
     * `-PtesterSurvey`): wersja przedpremierowa to `0.x.y` albo numer z sufiksem.
     * Produkcja `1.0.0` i nowsze bez sufiksu: false.
     */
    fun isTestVersionName(versionName: String): Boolean =
        versionName.startsWith("0.") || versionName.contains('-')

    /** Metryki, których przed premierą nie da się zwiększyć (brak wydarzeń dnia i zestawów tygodnia). */
    fun lockedBeforeRelease(metric: AchievementMetric): Boolean =
        metric == AchievementMetric.DAILY_EVENTS || metric == AchievementMetric.WEEKLY_RULESETS

    /**
     * Czy przy osiągnięciu pokazać „Dostępne od premiery”. Zdobyte (np. z kopii z chmury)
     * i ukryte osiągnięcia nie dostają dopisku.
     */
    fun achievementNote(
        def: AchievementDef, unlocked: Boolean, testerBuild: Boolean, day: Long,
        releaseDay: Long = Daily.RELEASE_DAY,
    ): Boolean = active(testerBuild, day, releaseDay) && !unlocked && !def.hidden &&
        lockedBeforeRelease(def.metric)
}
