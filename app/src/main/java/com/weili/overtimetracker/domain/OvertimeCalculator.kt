package com.weili.overtimetracker.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

object OvertimeCalculator {
    fun calculateMinutes(
        entry: OvertimeEntry,
        schedule: WorkSchedule,
        overrides: Map<LocalDate, DayOverride> = emptyMap(),
    ): Long {
        if (!entry.endAt.isAfter(entry.startAt)) return 0

        var cursorDate = entry.startAt.toLocalDate()
        val lastDate = entry.endAt.toLocalDate()
        var total = 0L

        while (!cursorDate.isAfter(lastDate)) {
            val dayStart = cursorDate.atStartOfDay()
            val dayEnd = cursorDate.plusDays(1).atStartOfDay()
            val segmentStart = maxOf(entry.startAt, dayStart)
            val segmentEnd = minOf(entry.endAt, dayEnd)

            if (segmentEnd.isAfter(segmentStart)) {
                total += calculateDaySegment(
                    entry = entry,
                    date = cursorDate,
                    segmentStart = segmentStart,
                    segmentEnd = segmentEnd,
                    schedule = schedule,
                    override = overrides[cursorDate],
                )
            }
            cursorDate = cursorDate.plusDays(1)
        }
        return total
    }

    fun summarize(
        entries: List<OvertimeEntry>,
        month: YearMonth,
        schedule: WorkSchedule,
        overrides: Map<LocalDate, DayOverride> = emptyMap(),
    ): MonthlySummary {
        val monthEntries = entries.filter { YearMonth.from(it.serviceDate) == month }
        var total = 0L
        var workday = 0L
        var restDay = 0L
        var longest = 0L

        monthEntries.forEach { entry ->
            val minutes = calculateMinutes(entry, schedule, overrides)
            total += minutes
            longest = maxOf(longest, minutes)
            if (resolveDayType(entry.serviceDate, schedule, overrides[entry.serviceDate]) == DayType.WORKDAY) {
                workday += minutes
            } else {
                restDay += minutes
            }
        }

        return MonthlySummary(
            totalMinutes = total,
            workdayMinutes = workday,
            restDayMinutes = restDay,
            entryCount = monthEntries.size,
            longestMinutes = longest,
            standardDayMinutes = schedule.standardDayMinutes,
        )
    }

    fun resolveDayType(
        date: LocalDate,
        schedule: WorkSchedule,
        override: DayOverride?,
    ): DayType = override?.type ?: if (date.dayOfWeek in schedule.workDays) {
        DayType.WORKDAY
    } else {
        DayType.DAY_OFF
    }

    private fun calculateDaySegment(
        entry: OvertimeEntry,
        date: LocalDate,
        segmentStart: LocalDateTime,
        segmentEnd: LocalDateTime,
        schedule: WorkSchedule,
        override: DayOverride?,
    ): Long {
        return when (resolveDayType(date, schedule, override)) {
            DayType.DAY_OFF, DayType.FULL_DAY_OVERTIME -> minutesBetween(segmentStart, segmentEnd)
            DayType.WORKDAY -> {
                val normalStart = date.atTime(schedule.normalStart)
                val normalEnd = date.atTime(schedule.normalEnd)
                var minutes = 0L

                // Early-morning time counts only when this record began on a previous date.
                if (entry.startAt.toLocalDate().isBefore(date)) {
                    minutes += overlapMinutes(segmentStart, segmentEnd, date.atStartOfDay(), normalStart)
                }
                minutes += overlapMinutes(segmentStart, segmentEnd, normalEnd, date.plusDays(1).atStartOfDay())
                minutes
            }
        }
    }

    private fun overlapMinutes(
        firstStart: LocalDateTime,
        firstEnd: LocalDateTime,
        secondStart: LocalDateTime,
        secondEnd: LocalDateTime,
    ): Long {
        val start = maxOf(firstStart, secondStart)
        val end = minOf(firstEnd, secondEnd)
        return if (end.isAfter(start)) minutesBetween(start, end) else 0
    }

    private fun minutesBetween(start: LocalDateTime, end: LocalDateTime): Long =
        Duration.between(start, end).toMinutes()
}

fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        remaining == 0L -> "${hours}小时"
        hours == 0L -> "${remaining}分钟"
        else -> "${hours}小时${remaining}分"
    }
}

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
