package com.weili.overtimetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weili.overtimetracker.domain.DayType
import com.weili.overtimetracker.domain.MonthlySummary
import com.weili.overtimetracker.domain.OvertimeCalculator
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.PunchInferenceEngine
import com.weili.overtimetracker.domain.formatMinutes
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: OvertimeUiState,
    summary: MonthlySummary,
    calculateMinutes: (OvertimeEntry) -> Long,
    onPunch: (LocalDateTime) -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (OvertimeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now().withNano(0)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now().withNano(0)
            delay(1_000)
        }
    }

    val proposal = remember(now.withSecond(0), state.schedule, state.overrides) {
        PunchInferenceEngine.infer(now, state.schedule, state.overrideMap)
    }
    val existingForServiceDate = proposal?.let { candidate ->
        state.entries.firstOrNull { it.serviceDate == candidate.serviceDate }
    }
    val expectedMinutes = proposal?.let { candidate ->
        calculateMinutes(
            OvertimeEntry(
                serviceDate = candidate.serviceDate,
                startAt = candidate.startAt,
                endAt = candidate.endAt,
                source = com.weili.overtimetracker.domain.EntrySource.AUTO_PUNCH,
            ),
        )
    } ?: 0
    val dayType = OvertimeCalculator.resolveDayType(
        now.toLocalDate(),
        state.schedule,
        state.overrideMap[now.toLocalDate()],
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        when (dayType) {
                            DayType.WORKDAY -> "工作日"
                            DayType.DAY_OFF -> "休息日"
                            DayType.FULL_DAY_OVERTIME -> "全天加班"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            proposal?.let { "本次从 ${it.startAt.toLocalTime().displayTime()} 起计算" }
                                ?: "当前处于正常工作时段",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        when {
                            existingForServiceDate != null -> "该归属日期已有记录，可直接修改"
                            proposal != null -> "若现在打卡，预计 ${formatMinutes(expectedMinutes)}"
                            else -> "需要记录其他时段时，请使用补卡"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onPunch(now) },
                enabled = proposal != null && existingForServiceDate == null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(
                    if (existingForServiceDate == null) Icons.Rounded.AccessTime else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                )
                Spacer(Modifier.padding(4.dp))
                Text(if (existingForServiceDate == null) "结束加班" else "归属日已记录")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddEntry,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("补卡")
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("${YearMonth.from(now).displayMonth()}汇总", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCell("总加班", "${formatDecimalHours(summary.totalMinutes)} 小时", Modifier.weight(1f))
                MetricCell("标准工作日", String.format(Locale.CHINA, "%.2f 天", summary.standardDays), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCell("工作日", "${formatDecimalHours(summary.workdayMinutes)} 小时", Modifier.weight(1f))
                MetricCell("周末/休息日", "${formatDecimalHours(summary.restDayMinutes)} 小时", Modifier.weight(1f))
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("最近记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (state.entries.isEmpty()) {
            item {
                Text("还没有加班记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(state.entries.take(3), key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    minutes = calculateMinutes(entry),
                    onEdit = { onEditEntry(entry) },
                    onDelete = {},
                    showDelete = false,
                )
            }
        }
    }
}
