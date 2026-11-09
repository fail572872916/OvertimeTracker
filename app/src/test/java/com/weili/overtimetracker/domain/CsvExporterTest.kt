package com.weili.overtimetracker.domain

import com.weili.overtimetracker.data.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test
    fun `csv contains all imported entries and corrected overtime`() {
        val csv = CsvExporter.create(SampleEntries.july2026, WorkSchedule(), emptyMap())
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()

        assertEquals(19, lines.size)
        assertTrue(lines.first().startsWith("\uFEFF归属日期"))
        assertTrue(csv.contains("\"2026-07-06\",\"2026-07-06 09:00\",\"2026-07-07 03:00\",\"18.00\",\"9.00\""))
    }
}
