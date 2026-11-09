package com.weili.overtimetracker.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PunchInferenceEngineTest {
    private val schedule = WorkSchedule()

    @Test
    fun `weekday evening starts at 18`() {
        val proposal = PunchInferenceEngine.infer(
            LocalDateTime.parse("2026-07-22T21:30"),
            schedule,
        )

        assertEquals(LocalDateTime.parse("2026-07-22T18:00"), proposal?.startAt)
    }

    @Test
    fun `early morning belongs to previous workday`() {
        val proposal = PunchInferenceEngine.infer(
            LocalDateTime.parse("2026-07-15T03:00"),
            schedule,
        )

        assertEquals(LocalDateTime.parse("2026-07-14T18:00"), proposal?.startAt)
    }

    @Test
    fun `normal work period requires manual entry`() {
        assertNull(
            PunchInferenceEngine.infer(
                LocalDateTime.parse("2026-07-22T15:00"),
                schedule,
            ),
        )
    }
}
