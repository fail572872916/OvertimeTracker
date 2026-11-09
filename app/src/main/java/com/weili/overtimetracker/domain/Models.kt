package com.weili.overtimetracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class EntrySource {
    AUTO_PUNCH,
    MANUAL,
    IMPORTED,
}

enum class DayType {
    WORKDAY,
    DAY_OFF,
    FULL_DAY_OVERTIME,
}

data class OvertimeEntry(
    val id: Long = 0,
    val serviceDate: LocalDate,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val source: EntrySource,
    val startWasInferred: Boolean = false,
    val note: String = "",
)

data class PhotoAttachment(
    val id: String,
    val entryId: Long,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: LocalDateTime,
)

sealed interface ExcelExportScope {
    data object All : ExcelExportScope
    data class Month(val month: YearMonth) : ExcelExportScope
}

data class DayOverride(
    val date: LocalDate,
    val type: DayType,
    val note: String = "",
)

data class WorkSchedule(
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
    val normalStart: LocalTime = LocalTime.of(9, 0),
    val normalEnd: LocalTime = LocalTime.of(18, 0),
    val weekendDefaultStart: LocalTime = LocalTime.of(9, 0),
    val overnightCutoff: LocalTime = LocalTime.of(6, 0),
    val standardDayMinutes: Int = 8 * 60,
)

data class MonthlySummary(
    val totalMinutes: Long = 0,
    val workdayMinutes: Long = 0,
    val restDayMinutes: Long = 0,
    val entryCount: Int = 0,
    val longestMinutes: Long = 0,
    val standardDayMinutes: Int = 8 * 60,
) {
    val standardDays: Double
        get() = if (totalMinutes == 0L) 0.0 else totalMinutes.toDouble() / standardDayMinutes
}
