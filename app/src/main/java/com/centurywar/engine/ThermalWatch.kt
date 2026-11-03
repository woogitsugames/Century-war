package com.centurywar.engine

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.annotation.TargetApi

/**
 * R2-05: sygnał o nagrzewaniu dla [QualityGovernor], zanim system zacznie dławić procesor.
 * Status termiczny (API 29+) przychodzi ze słuchacza platformy; na API 30+ dodatkowo co 10 s
 * w bitwie pytamy o `getThermalHeadroom(10)`. Oba wywołania pracują na wątku UI, nigdy
 * w pętli gry (headroom jest kosztowny i ograniczany przez system). Tylko API platformy.
 */
class ThermalWatch(private val power: PowerManager?, private val game: Game) {
    companion object {
        const val HEADROOM_INTERVAL_MS = 10_000L
        /** Prognoza na 10 s, jak w dokumentacji `getThermalHeadroom`. */
        const val FORECAST_S = 10
    }

    private val handler = Handler(Looper.getMainLooper())
    private var listener: Any? = null
    private var started = false

    private val poll = object : Runnable {
        override fun run() {
            if (!started) return
            if (Build.VERSION.SDK_INT >= 30) sampleHeadroom()
            handler.postDelayed(this, HEADROOM_INTERVAL_MS)
        }
    }

    /** onResume Activity. */
    fun start() {
        if (started) return
        started = true
        val pm = power ?: return
        if (Build.VERSION.SDK_INT >= 29) register(pm)
        if (Build.VERSION.SDK_INT >= 30) handler.postDelayed(poll, HEADROOM_INTERVAL_MS)
    }

    /** onPause Activity. */
    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(poll)
        val pm = power
        if (pm != null && Build.VERSION.SDK_INT >= 29) unregister(pm)
        game.thermalHeadroom = Float.NaN
    }

    @TargetApi(29)
    private fun register(pm: PowerManager) {
        try {
            game.thermalStatus = pm.currentThermalStatus
            val l = PowerManager.OnThermalStatusChangedListener { status -> game.thermalStatus = status }
            pm.addThermalStatusListener(l)
            listener = l
        } catch (_: RuntimeException) {
            listener = null
        }
    }

    @TargetApi(29)
    private fun unregister(pm: PowerManager) {
        val l = listener as? PowerManager.OnThermalStatusChangedListener ?: return
        listener = null
        try { pm.removeThermalStatusListener(l) } catch (_: RuntimeException) { }
    }

    @TargetApi(30)
    private fun sampleHeadroom() {
        val pm = power ?: return
        if (!game.battleActive) {
            game.thermalHeadroom = Float.NaN
            return
        }
        // NaN (brak wsparcia albo zbyt częste wywołanie) regulator ignoruje.
        game.thermalHeadroom = try { pm.getThermalHeadroom(FORECAST_S) } catch (_: RuntimeException) { Float.NaN }
    }
}
