package com.centurywar.notifications

import android.content.Context
import android.util.JsonReader
import java.io.File

/** Strumieniowy odczyt wyłącznie pól powiadomień z profile.json. */
internal object DailyReminderProfileReader {
    fun read(context: Context): DailyReminderProfile? = read(File(context.filesDir, "profile.json"))

    fun read(file: File): DailyReminderProfile? {
        if (!file.isFile) return null
        return try {
            var notifications = true
            var hour = 18
            var wins = 0
            var rankedDay = -1L
            var lang = ""
            JsonReader(file.bufferedReader()).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "notifications" -> notifications = json.nextBoolean()
                        "notifyHour" -> hour = json.nextInt().coerceIn(0, 23)
                        "wins" -> wins = json.nextInt()
                        "lastRankedDaily" -> rankedDay = json.nextLong()
                        "lang" -> lang = json.nextString()
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            DailyReminderProfile(notifications, hour, wins, rankedDay, lang)
        } catch (_: Exception) {
            // Uszkodzony albo przejściowo niedostępny plik: nie wysyłaj alarmu.
            null
        }
    }
}
