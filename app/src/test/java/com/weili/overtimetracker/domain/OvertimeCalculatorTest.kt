package com.weili.overtimetracker.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class OvertimeCalculatorTest {
    private val schedule = WorkSchedule()

    @Test
    fun `provided July records sum to 130 point 5 raw hours`() {
        val rawMinutes = SampleEntries.july2026.sumOf {
            Duration.between(it.startAt, it.endAt).toMinutes()
        }

        assertEquals(7_830L, rawMinutes)
    }

    @Test
    fun `provided July records sum to 121 point 5 overtime hours`() {
        val summary = OvertimeCalculator.summarize(
            entries = SampleEntries.july2026,
            month = YearMonth.of(2026, 7),
            schedule = schedule,
        )

        assertEquals(7_290L, summary.totalMinutes)
        assertEquals(15.1875, summary.totalMinutes.toDouble() / schedule.standardDayMinutes, 0.0001)
    }

    @Test
    fun `weekday normal hours are excluded from cross day entry`() {
        val entry = OvertimeEntry(
            serviceDate = java.time.LocalDate.of(2026, 7, 6),
            startAt = LocalDateTime.parse("2026-07-06T09:00"),
            endAt = LocalDateTime.parse("2026-07-07T03:00"),
            source = EntrySource.MANUAL,
        )

        assertEquals(540L, OvertimeCalculator.calculateMinutes(entry, schedule))
    }

    @Test
    fun `weekend crossing midnight keeps full duration`() {
        val entry = OvertimeEntry(
            serviceDate = java.time.LocalDate.of(2026, 7, 12),
            startAt = LocalDateTime.parse("2026-07-12T09:00"),
            endAt = LocalDateTime.parse("2026-07-13T01:30"),
            source = EntrySource.MANUAL,
        )

        assertEquals(990L, OvertimeCalculator.calculateMinutes(entry, schedule))
    }
}
