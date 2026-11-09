package com.weili.overtimetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weili.overtimetracker.domain.MonthlySummary
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.formatMinutes
import java.time.YearMonth
import java.util.Locale

@Composable
fun StatsScreen(
    entries: List<OvertimeEntry>,
    summary: MonthlySummary,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    calculateMinutes: (OvertimeEntry) -> Long,
    modifier: Modifier = Modifier,
) {
    val daily = remember(entries, selectedMonth) {
        entries
            .filter { YearMonth.from(it.serviceDate) == selectedMonth }
            .groupBy { it.serviceDate.dayOfMonth }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf(calculateMinutes) }
    }
    val workRatio = if (summary.totalMinutes == 0L) 0f else summary.workdayMinutes.toFloat() / summary.totalMinutes

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            MonthSelector(
                month = selectedMonth,
                onPrevious = { onMonthChange(selectedMonth.minusMonths(1)) },
                onNext = { onMonthChange(selectedMonth.plusMonths(1)) },
            )
        }
        item {
            Text("累计加班", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${formatDecimalHours(summary.totalMinutes)} 小时",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "折合 ${String.format(Locale.CHINA, "%.2f", summary.standardDays)} 个标准工作日",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("工作日晚间")
                        Text("${formatDecimalHours(summary.workdayMinutes)} 小时", fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(
                        progress = { workRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("周末/休息日")
                        Text("${formatDecimalHours(summary.restDayMinutes)} 小时", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Text("每日时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            DailyHoursChart(
                daysInMonth = selectedMonth.lengthOfMonth(),
                dailyMinutes = daily,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1日", style = MaterialTheme.typography.bodySmall)
                Text("${selectedMonth.lengthOfMonth() / 2}日", style = MaterialTheme.typography.bodySmall)
                Text("${selectedMonth.lengthOfMonth()}日", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCell("记录次数", "${summary.entryCount} 次", Modifier.weight(1f))
                MetricCell("最长一次", formatMinutes(summary.longestMinutes), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            MetricCell(
                "平均每次",
                if (summary.entryCount == 0) "0小时" else formatMinutes(summary.totalMinutes / summary.entryCount),
            )
        }
    }
}

@Composable
private fun DailyHoursChart(
    daysInMonth: Int,
    dailyMinutes: Map<Int, Long>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val maxMinutes = maxOf(60L, dailyMinutes.values.maxOrNull() ?: 0L)
    val description = dailyMinutes.entries.sortedBy { it.key }
        .joinToString("，") { "${it.key}日${formatMinutes(it.value)}" }
        .ifBlank { "本月没有加班记录" }

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val baseline = size.height - 2.dp.toPx()
        val chartHeight = size.height - 8.dp.toPx()
        drawLine(gridColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
        drawLine(
            gridColor,
            Offset(0f, chartHeight / 2),
            Offset(size.width, chartHeight / 2),
            strokeWidth = 1.dp.toPx(),
        )
        val slotWidth = size.width / daysInMonth
        val barWidth = maxOf(2.dp.toPx(), slotWidth * 0.56f)
        for (day in 1..daysInMonth) {
            val value = dailyMinutes[day] ?: 0L
            val barHeight = chartHeight * (value.toFloat() / maxMinutes)
            drawRoundRect(
                color = if (value == 0L) Color.Transparent else barColor,
                topLeft = Offset((day - 1) * slotWidth + (slotWidth - barWidth) / 2, baseline - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
        drawRect(gridColor, style = Stroke(width = 1.dp.toPx()))
    }
}
