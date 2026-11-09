package com.weili.overtimetracker.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weili.overtimetracker.data.TimeSetting
import com.weili.overtimetracker.domain.DayOverride
import com.weili.overtimetracker.domain.DayType
import com.weili.overtimetracker.domain.WorkSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(
    schedule: WorkSchedule,
    overrides: List<DayOverride>,
    onToggleWorkDay: (DayOfWeek) -> Unit,
    onUpdateTime: (TimeSetting, LocalTime) -> Unit,
    onUpdateStandardHours: (Int) -> Unit,
    onSaveOverride: (DayOverride) -> Unit,
    onDeleteOverride: (DayOverride) -> Unit,
    onExportExcel: () -> Unit,
    onExportCsv: () -> Unit,
    onClearEntries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingTime by remember { mutableStateOf<TimeSetting?>(null) }
    var showAddOverride by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("工作制") }
        item {
            Text("工作日", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DayOfWeek.entries) { day ->
                    FilterChip(
                        selected = day in schedule.workDays,
                        onClick = { onToggleWorkDay(day) },
                        label = { Text(day.shortChinese()) },
                    )
                }
            }
        }
        item {
            SettingsTimeRow("正常上班", schedule.normalStart) { editingTime = TimeSetting.NORMAL_START }
            SettingsTimeRow("正常下班", schedule.normalEnd) { editingTime = TimeSetting.NORMAL_END }
            SettingsTimeRow("周末默认开始", schedule.weekendDefaultStart) { editingTime = TimeSetting.WEEKEND_START }
            SettingsTimeRow("凌晨归属截止", schedule.overnightCutoff) { editingTime = TimeSetting.OVERNIGHT_CUTOFF }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("标准工作日", style = MaterialTheme.typography.bodyLarge)
                    Text("仅用于折算天数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onUpdateStandardHours(schedule.standardDayMinutes / 60 - 1) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "减少一小时")
                    }
                    Text("${schedule.standardDayMinutes / 60} 小时", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onUpdateStandardHours(schedule.standardDayMinutes / 60 + 1) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "增加一小时")
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionTitle("特殊日期")
                Button(onClick = { showAddOverride = true }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("添加")
                }
            }
            Text(
                "用于节假日、周末补班或全天值班",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (overrides.isEmpty()) {
            item { Text("暂无特殊日期", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(overrides, key = { it.date }) { override ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(override.date.toString(), fontWeight = FontWeight.SemiBold)
                            Text(override.type.displayName(), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDeleteOverride(override) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除特殊日期")
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            SectionTitle("数据")
            Button(
                onClick = onExportExcel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("导出 Excel（含照片）")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("导出 CSV")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("清空全部加班记录")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "记录与证明照片仅保存在本机。Excel 可导出全部记录或指定月份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingTime?.let { setting ->
        AppTimePickerDialog(
            initialTime = when (setting) {
                TimeSetting.NORMAL_START -> schedule.normalStart
                TimeSetting.NORMAL_END -> schedule.normalEnd
                TimeSetting.WEEKEND_START -> schedule.weekendDefaultStart
                TimeSetting.OVERNIGHT_CUTOFF -> schedule.overnightCutoff
            },
            title = when (setting) {
                TimeSetting.NORMAL_START -> "正常上班时间"
                TimeSetting.NORMAL_END -> "正常下班时间"
                TimeSetting.WEEKEND_START -> "周末默认开始时间"
                TimeSetting.OVERNIGHT_CUTOFF -> "凌晨归属截止时间"
            },
            onDismiss = { editingTime = null },
            onConfirm = {
                onUpdateTime(setting, it)
                editingTime = null
            },
        )
    }

    if (showAddOverride) {
        AddOverrideDialog(
            onDismiss = { showAddOverride = false },
            onSave = {
                onSaveOverride(it)
                showAddOverride = false
            },
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清空全部记录？") },
            text = { Text("该操作无法撤销，工作制和特殊日期设置会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearEntries()
                        showClearConfirmation = false
                    },
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsTimeRow(label: String, time: LocalTime, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(time.displayTime(), fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = "修改$label")
        }
    }
}

@Composable
private fun AddOverrideDialog(
    onDismiss: () -> Unit,
    onSave: (DayOverride) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var type by remember { mutableStateOf(DayType.DAY_OFF) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加特殊日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date.toString())
                }
                Text("日期类型", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DayType.entries) { candidate ->
                        FilterChip(
                            selected = candidate == type,
                            onClick = { type = candidate },
                            label = { Text(candidate.displayName()) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(DayOverride(date, type)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date = it; showDatePicker = false },
        )
    }
}

private fun DayOfWeek.shortChinese(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
