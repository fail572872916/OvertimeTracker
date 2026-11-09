package com.weili.overtimetracker.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PunchProposal(
    val serviceDate: LocalDate,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)

object PunchInferenceEngine {
    fun infer(
        now: LocalDateTime,
        schedule: WorkSchedule,
        overrides: Map<LocalDate, DayOverride> = emptyMap(),
    ): PunchProposal? {
        val serviceDate = if (now.toLocalTime().isBefore(schedule.overnightCutoff)) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
        val dayType = OvertimeCalculator.resolveDayType(serviceDate, schedule, overrides[serviceDate])
        val startTime = if (dayType == DayType.WORKDAY) {
            schedule.normalEnd
        } else {
            schedule.weekendDefaultStart
        }
        val startAt = serviceDate.atTime(startTime)

        if (!now.isAfter(startAt)) return null
        return PunchProposal(serviceDate, startAt, now)
    }
}
