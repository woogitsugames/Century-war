package com.centurywar.notifications

import com.centurywar.engine.L
import com.centurywar.meta.NextGoalKind
import com.centurywar.meta.NextGoals
import com.centurywar.meta.Profile
import java.io.File

/**
 * R10-07: konkretny cel do dziennego przypomnienia (R2-01 „Następny cel”), w języku profilu
 * i bez zmiany języka gry. Profil jest tylko czytany do osobnego obiektu (nigdy zapisywany);
 * każdy błąd daje null, a przypomnienie wraca do tekstu domyślnego.
 */
internal object DailyReminderGoal {
    fun text(file: File, lang: String, nowMillis: Long): String? = try {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val source = if (file.isFile) file else if (tmp.isFile) tmp else null
        if (source == null) null else {
            // Obiekt wskazuje nieistniejący plik i nigdy nie woła save(): odczyt niczego nie nadpisze.
            val p = Profile(File(file.parentFile, file.name + ".reminder-readonly"))
            p.fromJson(source.readText(Charsets.UTF_8))
            pick(p, lang, nowMillis)
        }
    } catch (_: Exception) {
        null
    }

    fun pick(p: Profile, lang: String, nowMillis: Long): String? {
        if (!p.tutorialDone) return null
        val idx = L.indexOf(lang).takeIf { it >= 0 } ?: L.systemLang()
        val goal = NextGoals.pick(p, nowMillis, null, lang = idx)
        return if (goal.kind == NextGoalKind.NONE || goal.text.isBlank()) null else goal.text
    }
}
