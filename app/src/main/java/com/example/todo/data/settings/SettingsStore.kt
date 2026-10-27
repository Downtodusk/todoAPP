package com.example.todo.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore("settings")
enum class ReminderMode { STANDARD, ALARM_CLOCK }
data class UserSettings(
    val theme: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean? = null,
    val allDayMinute: Int = 540,
    val saveSource: Boolean = false,
    val reminderMode: ReminderMode = ReminderMode.STANDARD,
)

class SettingsStore(context: Context) {
    private val store = context.settingsDataStore
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        UserSettings(it[Theme] ?: "SYSTEM", it[Dynamic] ?: true, it[Motion], it[AllDay] ?: 540, it[SaveSource] ?: false,
            ReminderMode.entries.firstOrNull { mode -> mode.name == it[Reminder] } ?: ReminderMode.STANDARD)
    }
    suspend fun theme(value: String) { require(value in listOf("SYSTEM", "LIGHT", "DARK")); store.edit { it[Theme] = value } }
    suspend fun dynamic(value: Boolean) { store.edit { it[Dynamic] = value } }
    suspend fun motion(value: Boolean?) { store.edit { if (value == null) it.remove(Motion) else it[Motion] = value } }
    suspend fun allDay(minute: Int) { require(minute in 0..1439); store.edit { it[AllDay] = minute } }
    suspend fun saveSource(value: Boolean) { store.edit { it[SaveSource] = value } }
    suspend fun reminderMode(value: ReminderMode) { store.edit { it[Reminder] = value.name } }
    private companion object {
        val Theme = stringPreferencesKey("theme")
        val Dynamic = booleanPreferencesKey("dynamic_color")
        val Motion = booleanPreferencesKey("reduce_motion")
        val AllDay = intPreferencesKey("all_day_minute")
        val SaveSource = booleanPreferencesKey("save_source")
        val Reminder = stringPreferencesKey("reminder_mode")
    }
}
