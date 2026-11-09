package com.weili.overtimetracker.domain

import java.time.LocalDateTime

object SampleEntries {
    val july2026: List<OvertimeEntry> = listOf(
        entry("2026-07-01T18:00", "2026-07-01T21:30"),
        entry("2026-07-02T18:00", "2026-07-02T22:30"),
        entry("2026-07-04T09:00", "2026-07-04T22:30"),
        entry("2026-07-05T08:30", "2026-07-05T23:00"),
        entry("2026-07-06T09:00", "2026-07-07T03:00", "排除工作日 09:00-18:00"),
        entry("2026-07-09T18:00", "2026-07-09T21:00"),
        entry("2026-07-10T18:00", "2026-07-10T21:00"),
        entry("2026-07-11T09:00", "2026-07-11T21:30"),
        entry("2026-07-12T09:00", "2026-07-13T01:30"),
        entry("2026-07-13T18:00", "2026-07-13T20:30"),
        entry("2026-07-14T18:00", "2026-07-15T02:00"),
        entry("2026-07-15T18:00", "2026-07-15T21:00"),
        entry("2026-07-16T18:00", "2026-07-16T22:00"),
        entry("2026-07-18T09:00", "2026-07-18T18:00"),
        entry("2026-07-19T14:00", "2026-07-19T18:00"),
        entry("2026-07-20T18:00", "2026-07-20T21:00"),
        entry("2026-07-21T18:00", "2026-07-21T22:00"),
        entry("2026-07-22T18:00", "2026-07-22T22:00"),
    )

    private fun entry(start: String, end: String, note: String = ""): OvertimeEntry {
        val startAt = LocalDateTime.parse(start)
        return OvertimeEntry(
            serviceDate = startAt.toLocalDate(),
            startAt = startAt,
            endAt = LocalDateTime.parse(end),
            source = EntrySource.IMPORTED,
            note = note,
        )
    }
}
