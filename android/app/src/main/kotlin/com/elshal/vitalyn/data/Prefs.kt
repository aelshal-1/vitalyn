package com.elshal.vitalyn.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.elshal.vitalyn.core.TodaySnapshot
import java.time.LocalDate

/** Plain app settings (the iOS `UserDefaults.standard`). Widgets run in the app's process and read the same file. */
class Prefs(context: Context) {
    val raw: SharedPreferences = context.getSharedPreferences("vitalyn", Context.MODE_PRIVATE)

    fun string(key: String): String? = raw.getString(key, null)
    fun setString(key: String, value: String?) = raw.edit { if (value == null) remove(key) else putString(key, value) }

    fun int(key: String, default: Int = 0): Int = raw.getInt(key, default)
    fun setInt(key: String, value: Int) = raw.edit { putInt(key, value) }

    fun bool(key: String, default: Boolean = false): Boolean = raw.getBoolean(key, default)
    fun setBool(key: String, value: Boolean) = raw.edit { putBoolean(key, value) }

    fun has(key: String): Boolean = raw.contains(key)

    fun stringSet(key: String): Set<String>? = raw.getStringSet(key, null)
    fun setStringSet(key: String, value: Set<String>) = raw.edit { putStringSet(key, value) }
}

/** A small summary of the day that the app writes and the widgets read. */
object SnapshotStore {
    private const val KEY = "today-snapshot"

    fun save(prefs: Prefs, snapshot: TodaySnapshot) {
        prefs.setString(KEY, VitalynStore.json.encodeToString(TodaySnapshot.serializer(), snapshot))
    }

    fun load(prefs: Prefs): TodaySnapshot? = prefs.string(KEY)?.let {
        runCatching { VitalynStore.json.decodeFromString(TodaySnapshot.serializer(), it) }.getOrNull()
    }
}

/** Today's water glasses, shared by the app and the interactive water widget. */
object WaterStore {
    private fun key(date: LocalDate) = "water-${date.year}-${date.monthValue}-${date.dayOfMonth}"

    fun glasses(prefs: Prefs, date: LocalDate = LocalDate.now()): Int = prefs.int(key(date))

    fun add(prefs: Prefs, count: Int = 1, date: LocalDate = LocalDate.now()): Int {
        val value = maxOf(0, prefs.int(key(date)) + count)
        prefs.setInt(key(date), value)
        SnapshotStore.load(prefs)?.let { snapshot ->
            if (snapshot.date.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == date) SnapshotStore.save(prefs, snapshot.copy(waterGlasses = value))
        }
        return value
    }
}
