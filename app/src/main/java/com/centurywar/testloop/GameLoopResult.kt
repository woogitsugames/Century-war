package com.centurywar.testloop

import org.json.JSONObject

/**
 * R11-04: mały plik wyników pętli gry (stały format, pole `v`). Tylko wersja gry, model
 * i API telefonu oraz pomiary; bez profilu, nazw ani identyfikatorów gracza lub urządzenia.
 * Nazwy pól nie pokrywają się ze schematem Firebase (`name`, `frame_stats`, ...).
 */
internal object GameLoopResult {
    const val VERSION = 1

    fun json(
        report: GameLoopReport, versionName: String, versionCode: Int, protocol: String,
        model: String, sdk: Int,
    ): String {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("scenario", report.scenario)
        o.put("result", report.result)
        if (report.reason.isNotEmpty()) o.put("reason", report.reason.take(80))
        o.put("versionName", versionName)
        o.put("versionCode", versionCode)
        o.put("protocol", protocol)
        o.put("device", JSONObject().put("model", model.take(100)).put("sdk", sdk))
        o.put("quality", report.qualityStart)
        o.put("qualityMin", report.qualityMin)
        o.put("reductionMax", report.reductionMax)
        o.put("frames", frames(report.frames))
        if (report.screens.isNotEmpty()) {
            val screens = JSONObject()
            for ((label, stats) in report.screens) screens.put(label, frames(stats))
            o.put("screens", screens)
        }
        o.put("discardedSteps", report.discardedSteps)
        o.put("pssStartKb", report.pssStartKb)
        o.put("pssEndKb", report.pssEndKb)
        if (report.startupMs >= 0) o.put("startupMs", report.startupMs)
        if (report.battleTimeS >= 0f) o.put("battleTimeS", round1(report.battleTimeS))
        if (report.winner >= -1) o.put("winner", report.winner)
        if (report.wave >= 0) o.put("wave", report.wave)
        report.snapshotSaved?.let { o.put("snapshotSaved", it) }
        report.snapshotLoaded?.let { o.put("snapshotLoaded", it) }
        if (report.snapshotLoadMs >= 0) o.put("snapshotLoadMs", report.snapshotLoadMs)
        if (report.resumedAtS >= 0f) o.put("resumedAtS", round1(report.resumedAtS))
        o.put("elapsedS", round1(report.elapsedS))
        return o.toString()
    }

    private fun frames(s: FrameStats): JSONObject = JSONObject()
        .put("count", s.count)
        .put("p50", round1(s.percentileMs(50)))
        .put("p95", round1(s.percentileMs(95)))
        .put("max", round1(s.maxMs))

    private fun round1(v: Float): Double = Math.round(v * 10.0) / 10.0
}
