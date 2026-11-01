package com.centurywar

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.centurywar.crash.CrashReports
import com.centurywar.engine.StartupTimings
import com.centurywar.meta.PlayGamesLeaderboards
import com.google.android.gms.games.PlayGamesSdk

/** SDK musi zostać uruchomione przed Activity, ale tylko po uzupełnieniu ID. */
class CenturyWarApp : Application() {
    var playGamesReady = false
        private set
    internal val startupTimings = StartupTimings(SystemClock.elapsedRealtimeNanos())

    override fun onCreate() {
        val begin = SystemClock.elapsedRealtimeNanos()
        super.onCreate()
        CrashReports.install(this)
        if (PlayGamesLeaderboards.isConfigured(this)) {
            try {
                PlayGamesSdk.initialize(this)
                playGamesReady = true
            } catch (e: RuntimeException) {
                Log.w("CenturyWarPGS", "PGS nie jest dostępne", e)
            }
        }
        startupTimings.record(
            StartupTimings.Phase.APPLICATION, begin, SystemClock.elapsedRealtimeNanos()
        )
    }
}
