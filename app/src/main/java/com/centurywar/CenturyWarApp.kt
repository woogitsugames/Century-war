package com.centurywar

import android.app.Application
import android.util.Log
import com.centurywar.meta.PlayGamesLeaderboards
import com.google.android.gms.games.PlayGamesSdk

/** SDK musi zostać uruchomione przed Activity, ale tylko po uzupełnieniu ID. */
class CenturyWarApp : Application() {
    var playGamesReady = false
        private set

    override fun onCreate() {
        super.onCreate()
        if (!PlayGamesLeaderboards.isConfigured(this)) return
        try {
            PlayGamesSdk.initialize(this)
            playGamesReady = true
        } catch (e: RuntimeException) {
            Log.w("CenturyWarPGS", "PGS nie jest dostępne", e)
        }
    }
}
