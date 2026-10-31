package com.centurywar.notifications

import com.centurywar.engine.L
import com.centurywar.game.Modifier
import java.util.Locale

/** Powiadomienia używają słownika 10 języków bez zmieniania języka aktywnej gry. */
object DailyReminderTexts {
    private fun languageIndex(code: String): Int =
        L.indexOf(code).takeIf { it >= 0 } ?: L.systemLang()

    fun channelName(lang: String): String = L.text("daily", languageIndex(lang))
    fun title(lang: String): String = L.text("notification_title", languageIndex(lang))
    fun channelDescription(lang: String): String = L.text("notification_channel_desc", languageIndex(lang))
    fun settingNotifications(lang: String): String = L.text("notifications", languageIndex(lang))
    fun settingHour(lang: String): String = L.text("notify_hour", languageIndex(lang))

    fun body(modifiers: Set<Modifier>, lang: String): String {
        val index = languageIndex(lang)
        // Kolejność opisów jest stała, niezależnie od implementacji Set.
        val names = Modifier.values().filter { it in modifiers }
            .joinToString(", ") { L.text(it.key, index) }
        return String.format(Locale.ROOT, L.text("notification_body", index), names)
    }
}
