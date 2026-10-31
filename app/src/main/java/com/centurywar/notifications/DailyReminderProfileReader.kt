package com.centurywar.notifications

import android.content.Context
import android.util.JsonReader
import java.io.File

/** Strumieniowy odczyt wyłącznie pól powiadomień z profile.json. */
internal object DailyReminderProfileReader {
    fun read(context: Context): DailyReminderProfile? = read(File(context.filesDir, "profile.json"))

    fun read(file: File): DailyReminderProfile? {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val source = if (file.isFile) file else if (tmp.isFile) tmp else return null
        return try {
            var notifications = true
            var hour = 18
            var wins = 0
            var rankedDay = -1L
            var lang = ""
            JsonReader(source.bufferedReader()).use { json ->
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
