package com.centurywar.meta

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.centurywar.R
import com.centurywar.engine.Game
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import org.json.JSONObject

/**
 * Rankingi i snapshot PGS v2. Operacje SDK wykonujemy z wątku UI, a zmiany
 * profilu przekazujemy do wątku gry przez Game.post.
 */
class PlayGamesLeaderboards(
    private val activity: Activity,
    private val game: Game,
) : OnlineLeaderboards {

    companion object {
        private const val TAG = "CenturyWarPGS"
        private const val SNAPSHOT_NAME = "century_war_profile_v1"
        private const val LEADERBOARD_REQUEST = 9004
        private const val SAVE_DELAY_MS = 1800L

        /** Nie uruchamiaj SDK z identyfikatorem przykładowym. */
        fun isConfigured(context: Context): Boolean {
            val project = context.getString(R.string.game_services_project_id).trim()
            if (project.isEmpty() || !project.all { it in '0'..'9' }) return false
            return intArrayOf(
                R.string.leaderboard_endless,
                R.string.leaderboard_daily,
                R.string.leaderboard_campaign,
                R.string.leaderboard_skirmish,
            ).all { id ->
                val value = context.getString(id).trim()
                value.isNotEmpty() && !value.contains("REPLACE_ME", ignoreCase = true)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val signInClient = PlayGames.getGamesSignInClient(activity)
    private val boardsClient = PlayGames.getLeaderboardsClient(activity)
    private val snapshotsClient = PlayGames.getSnapshotsClient(activity)
    private val boardIds = mapOf(
        Profile.BOARD_ENDLESS to activity.getString(R.string.leaderboard_endless),
        "daily" to activity.getString(R.string.leaderboard_daily),
        Profile.BOARD_CAMPAIGN to activity.getString(R.string.leaderboard_campaign),
        Profile.BOARD_SKIRMISH to activity.getString(R.string.leaderboard_skirmish),
    )

    @Volatile private var authenticated = false
    @Volatile private var closed = false
    private var checkingAuth = false
    private var cloudBusy = false
    private var initialCloudChecked = false
    private var latestLocalJson = game.profile.toJson()
    private var lastSyncedJson: String? = null
    private val queuedScores = LinkedHashMap<String, Long>()
    private val sentScores = HashMap<String, Long>()
    private var dailyDay = System.currentTimeMillis() / 86_400_000L

    /** Nowe okno UTC nie może dziedziczyć rekordu z dnia poprzedniego. */
    private fun refreshDailyDay() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today == dailyDay) return
        dailyDay = today
        queuedScores.remove("daily")
        sentScores.remove("daily")
    }

    private val delayedSave = Runnable { syncCloud() }

    override val available: Boolean get() = !closed && authenticated

    init {
        game.profile.onSaved = { json ->
            // Zapis lokalny może pochodzić z wątku symulacji.
            handler.post {
                if (!closed) {
                    latestLocalJson = json
                    if (authenticated) {
                        publishSavedScores(json)
                        if (initialCloudChecked && json != lastSyncedJson) scheduleCloudSave()
                    }
                }
            }
        }
    }

    /** Sprawdzenie logowania jest asynchroniczne i nie pokazuje okna. */
    fun refreshSignIn() {
        handler.post {
            if (closed || checkingAuth) return@post
            checkingAuth = true
            try {
                signInClient.isAuthenticated().addOnCompleteListener { task ->
                    checkingAuth = false
                    if (closed) return@addOnCompleteListener
                    authenticated = task.isSuccessful && task.result.isAuthenticated
                    if (authenticated) onAuthenticated()
                }
            } catch (e: RuntimeException) {
                checkingAuth = false
                authenticated = false
                Log.w(TAG, "Nie można sprawdzić logowania PGS", e)
            }
        }
    }

    private fun onAuthenticated() {
        // Konto mogło zmienić się w ustawieniach Google między wznowieniami.
        sentScores.clear()
        refreshDailyDay()
        flushScores()
        publishSavedScores(latestLocalJson)
        syncCloud()
    }

    /** Ręczne logowanie uruchamiaj tylko w odpowiedzi na wybór gracza. */
    private fun signInAndOpen(board: String) {
        try {
            signInClient.signIn().addOnCompleteListener { task ->
                if (closed) return@addOnCompleteListener
                authenticated = task.isSuccessful && task.result.isAuthenticated
                if (authenticated) {
                    onAuthenticated()
                    openBoard(board)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Nie można uruchomić logowania PGS", e)
        }
    }

    /** Mapowanie lokalnych dziennych tablic daily_123 na wspólną tablicę PGS. */
    internal fun boardId(board: String): String? =
        boardIds[if (board == "daily" || board.startsWith("daily_")) "daily" else board]

    override fun submit(board: String, score: Long) {
        if (score < 0) return
        handler.post {
            if (closed) return@post
            refreshDailyDay()
            val key = if (board == "daily" || board.startsWith("daily_")) "daily" else board
            if (!boardIds.containsKey(key)) return@post
            val old = queuedScores[key]
            if (old == null || score > old) queuedScores[key] = score
            flushScores()
        }
    }

    private fun flushScores() {
        if (!authenticated || closed) return
        refreshDailyDay()
        val iterator = queuedScores.entries.iterator()
        while (iterator.hasNext()) {
            val (key, score) = iterator.next()
            if (score <= (sentScores[key] ?: -1L)) {
                iterator.remove()
                continue
            }
            val id = boardIds[key] ?: continue
            try {
                // Fire-and-forget: PGS obsługuje wysyłkę i buforowanie w tle.
                boardsClient.submitScore(id, score)
                sentScores[key] = score
                iterator.remove()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Nie udało się wysłać wyniku " + key, e)
            }
        }
    }

    /** Wznawia wyniki istniejącego profilu, także potyczkę bez wywołania z ekranu. */
    private fun publishSavedScores(json: String) {
        try {
            refreshDailyDay()
            val all = JSONObject(json).optJSONObject("boards") ?: return
            val day = System.currentTimeMillis() / 86_400_000L
            for (key in arrayOf(
                Profile.BOARD_ENDLESS,
                Profile.BOARD_CAMPAIGN,
                Profile.BOARD_SKIRMISH,
                Profile.dailyBoard(day),
            )) {
                val scores = all.optJSONArray(key) ?: continue
                var best = -1L
                for (i in 0 until scores.length()) {
                    best = maxOf(best, scores.optJSONObject(i)?.optLong("s", -1L) ?: -1L)
                }
                if (best >= 0) {
                    val board = if (key.startsWith("daily_")) "daily" else key
                    val previous = queuedScores[board]
                    if (previous == null || best > previous) queuedScores[board] = best
                }
            }
            flushScores()
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się odczytać lokalnych wyników", e)
        }
    }

    override fun showUi(board: String) {
        handler.post {
            if (closed || boardId(board) == null) return@post
            if (!authenticated) signInAndOpen(board) else openBoard(board)
        }
    }

    @Suppress("DEPRECATION")
    private fun openBoard(board: String) {
        val id = boardId(board) ?: return
        val span = if (board == "daily" || board.startsWith("daily_")) {
            LeaderboardVariant.TIME_SPAN_DAILY
        } else {
            LeaderboardVariant.TIME_SPAN_ALL_TIME
        }
        try {
            boardsClient.getLeaderboardIntent(id, span).addOnCompleteListener { task ->
                if (!closed && task.isSuccessful) {
                    try {
                        activity.startActivityForResult(task.result, LEADERBOARD_REQUEST)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Nie można otworzyć rankingu", e)
                    }
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Nie można pobrać rankingu", e)
        }
    }

    private fun scheduleCloudSave() {
        if (closed || !authenticated) return
        handler.removeCallbacks(delayedSave)
        handler.postDelayed(delayedSave, SAVE_DELAY_MS)
    }

    /** Najpierw odczytujemy chmurę; nigdy nie nadpisujemy jej w ciemno po logowaniu. */
    private fun syncCloud() {
        if (!authenticated || closed || cloudBusy) return
        cloudBusy = true
        handler.removeCallbacks(delayedSave)
        try {
            snapshotsClient.open(
                SNAPSHOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL
            ).addOnCompleteListener { task ->
                if (!task.isSuccessful || closed) {
                    finishSync(false)
                } else {
                    handleOpen(task.result, 0)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Nie można otworzyć zapisu chmurowego", e)
            finishSync(false)
        }
    }

    private fun readJson(snapshot: Snapshot): String? = try {
        val bytes = snapshot.snapshotContents.readFully()
        String(bytes, Charsets.UTF_8).takeIf { Profile.isValidJson(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Nie można odczytać snapshotu", e)
        null
    }

    private fun handleOpen(result: SnapshotsClient.DataOrConflict<Snapshot>, attempt: Int) {
        if (closed) {
            finishSync(false)
            return
        }
        if (result.isConflict) {
            if (attempt >= 5) {
                Log.w(TAG, "Zbyt wiele konfliktów snapshotu")
                finishSync(false)
                return
            }
            val conflict = result.conflict
            val first = readJson(conflict.snapshot)
            val second = readJson(conflict.conflictingSnapshot)
            if (first == null && second == null) {
                finishSync(false)
                return
            }
            val winner = if (second != null && (first == null || Profile.preferCloud(first, second))) {
                conflict.conflictingSnapshot
            } else {
                conflict.snapshot
            }
            try {
                snapshotsClient.resolveConflict(conflict.conflictId, winner)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) handleOpen(task.result, attempt + 1)
                        else finishSync(false)
                    }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Nie można rozwiązać konfliktu", e)
                finishSync(false)
            }
            return
        }

        val snapshot = result.data
        val cloudJson = readJson(snapshot)
        // Porównanie z aktualnym profilem wykonujemy na jego wątku właścicielskim.
        game.post {
            val localJson = game.profile.toJson()
            val useCloud = cloudJson != null && Profile.preferCloud(localJson, cloudJson)
            if (useCloud) {
                try {
                    game.profile.fromJson(cloudJson!!)
                    game.profile.save()
                    game.applyLanguage()
                    game.setQuality(game.effectiveQualityLevel())
                    game.applyFrameRate()
                } catch (e: Exception) {
                    Log.w(TAG, "Nie udało się odtworzyć profilu", e)
                }
            }
            handler.post {
                if (useCloud) {
                    discardSnapshot(snapshot, cloudJson)
                } else if (cloudJson == localJson) {
                    discardSnapshot(snapshot, cloudJson)
                } else {
                    commitSnapshot(snapshot, localJson)
                }
            }
        }
    }

    private fun discardSnapshot(snapshot: Snapshot, cloudJson: String?) {
        try {
            snapshotsClient.discardAndClose(snapshot).addOnCompleteListener { task ->
                if (task.isSuccessful) lastSyncedJson = cloudJson
                finishSync(task.isSuccessful)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Nie można zamknąć snapshotu", e)
            finishSync(false)
        }
    }

    private fun commitSnapshot(snapshot: Snapshot, json: String) {
        try {
            if (!snapshot.snapshotContents.writeBytes(json.toByteArray(Charsets.UTF_8))) {
                discardSnapshot(snapshot, null)
                return
            }
            val metadata = SnapshotMetadataChange.Builder()
                .setProgressValue(Profile.progressValue(json))
                .setDescription("Century War - postęp gracza")
                .build()
            snapshotsClient.commitAndClose(snapshot, metadata).addOnCompleteListener { task ->
                if (task.isSuccessful) lastSyncedJson = json
                finishSync(task.isSuccessful)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nie można zapisać snapshotu", e)
            finishSync(false)
        }
    }

    private fun finishSync(success: Boolean) {
        cloudBusy = false
        initialCloudChecked = true
        if (!closed && success) {
            publishSavedScores(latestLocalJson)
            // Lokalny zapis mógł zmienić się podczas pobierania chmury.
            if (latestLocalJson != lastSyncedJson) scheduleCloudSave()
        }
    }

    fun close() {
        closed = true
        authenticated = false
        handler.removeCallbacks(delayedSave)
        game.profile.onSaved = null
    }
}
