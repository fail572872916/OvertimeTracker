package com.weili.overtimetracker.data

import com.weili.overtimetracker.domain.DayOverride
import com.weili.overtimetracker.domain.EntrySource
import com.weili.overtimetracker.domain.OvertimeCalculator
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.WorkSchedule
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun create(
        entries: List<OvertimeEntry>,
        schedule: WorkSchedule,
        overrides: Map<LocalDate, DayOverride>,
    ): String = buildString {
        append('\uFEFF')
        appendLine("归属日期,开始时间,结束时间,原始小时,应计小时,来源,备注")
        entries.sortedBy(OvertimeEntry::startAt).forEach { entry ->
            val rawMinutes = Duration.between(entry.startAt, entry.endAt).toMinutes()
            val overtimeMinutes = OvertimeCalculator.calculateMinutes(entry, schedule, overrides)
            appendLine(
                listOf(
                    entry.serviceDate.toString(),
                    entry.startAt.format(dateTimeFormatter),
                    entry.endAt.format(dateTimeFormatter),
                    hours(rawMinutes),
                    hours(overtimeMinutes),
                    when (entry.source) {
                        EntrySource.AUTO_PUNCH -> "自动打卡"
                        EntrySource.MANUAL -> "补卡"
                        EntrySource.IMPORTED -> "已导入"
                    },
                    entry.note,
                ).joinToString(",", transform = ::escape),
            )
        }
    }

    private fun hours(minutes: Long): String = String.format(Locale.US, "%.2f", minutes / 60.0)

    private fun escape(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
