package com.weili.overtimetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.PhotoAttachment
import java.time.YearMonth

@Composable
fun RecordsScreen(
    entries: List<OvertimeEntry>,
    attachmentsByEntry: Map<Long, List<PhotoAttachment>>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    calculateMinutes: (OvertimeEntry) -> Long,
    onEdit: (OvertimeEntry) -> Unit,
    onDelete: (OvertimeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthEntries = remember(entries, selectedMonth) {
        entries.filter { YearMonth.from(it.serviceDate) == selectedMonth }
    }
    var pendingDelete by remember { mutableStateOf<OvertimeEntry?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonthSelector(
                month = selectedMonth,
                onPrevious = { onMonthChange(selectedMonth.minusMonths(1)) },
                onNext = { onMonthChange(selectedMonth.plusMonths(1)) },
            )
        }
        if (monthEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxHeight(0.65f).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("这个月还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(monthEntries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    minutes = calculateMinutes(entry),
                    photoCount = attachmentsByEntry[entry.id].orEmpty().size,
                    onEdit = { onEdit(entry) },
                    onDelete = { pendingDelete = entry },
                )
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("删除后，本月统计会立即更新。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        pendingDelete = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}
