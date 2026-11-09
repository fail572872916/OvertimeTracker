package com.weili.overtimetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weili.overtimetracker.domain.WorkSchedule
import com.weili.overtimetracker.domain.toLocalTime
import com.weili.overtimetracker.domain.toMinuteOfDay
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scheduleDataStore by preferencesDataStore(name = "schedule")

class ScheduleSettingsRepository(private val context: Context) {
    private object Keys {
        val workDays = stringPreferencesKey("work_days")
        val normalStart = intPreferencesKey("normal_start")
        val normalEnd = intPreferencesKey("normal_end")
        val weekendStart = intPreferencesKey("weekend_start")
        val overnightCutoff = intPreferencesKey("overnight_cutoff")
        val standardDayMinutes = intPreferencesKey("standard_day_minutes")
        val initialDataSeeded = booleanPreferencesKey("initial_data_seeded")
    }

    val schedule: Flow<WorkSchedule> = context.scheduleDataStore.data.map { preferences ->
        WorkSchedule(
            workDays = preferences[Keys.workDays]
                ?.split(',')
                ?.mapNotNull { value -> runCatching { DayOfWeek.valueOf(value) }.getOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: WorkSchedule().workDays,
            normalStart = (preferences[Keys.normalStart] ?: 9 * 60).toLocalTime(),
            normalEnd = (preferences[Keys.normalEnd] ?: 18 * 60).toLocalTime(),
            weekendDefaultStart = (preferences[Keys.weekendStart] ?: 9 * 60).toLocalTime(),
            overnightCutoff = (preferences[Keys.overnightCutoff] ?: 6 * 60).toLocalTime(),
            standardDayMinutes = preferences[Keys.standardDayMinutes] ?: 8 * 60,
        )
    }

    val initialDataSeeded: Flow<Boolean> = context.scheduleDataStore.data.map { preferences ->
        preferences[Keys.initialDataSeeded] ?: false
    }

    suspend fun markInitialDataSeeded() {
        context.scheduleDataStore.edit { it[Keys.initialDataSeeded] = true }
    }

    suspend fun updateTime(setting: TimeSetting, time: LocalTime) {
        context.scheduleDataStore.edit { preferences ->
            val key = when (setting) {
                TimeSetting.NORMAL_START -> Keys.normalStart
                TimeSetting.NORMAL_END -> Keys.normalEnd
                TimeSetting.WEEKEND_START -> Keys.weekendStart
                TimeSetting.OVERNIGHT_CUTOFF -> Keys.overnightCutoff
            }
            preferences[key] = time.toMinuteOfDay()
        }
    }

    suspend fun updateStandardDayHours(hours: Int) {
        context.scheduleDataStore.edit { it[Keys.standardDayMinutes] = hours.coerceIn(1, 24) * 60 }
    }

    suspend fun updateWorkDays(days: Set<DayOfWeek>) {
        context.scheduleDataStore.edit { it[Keys.workDays] = days.joinToString(",") { day -> day.name } }
    }
}

enum class TimeSetting {
    NORMAL_START,
    NORMAL_END,
    WEEKEND_START,
    OVERNIGHT_CUTOFF,
}
