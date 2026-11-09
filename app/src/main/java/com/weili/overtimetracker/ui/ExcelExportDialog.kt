package com.weili.overtimetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
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
import com.weili.overtimetracker.domain.ExcelExportScope
import java.time.YearMonth

@Composable
fun ExcelExportDialog(
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onExport: (ExcelExportScope) -> Unit,
) {
    var exportAll by remember { mutableStateOf(false) }
    var month by remember(initialMonth) { mutableStateOf(initialMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 Excel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportChoiceRow(
                    selected = exportAll,
                    title = "全部记录",
                    onClick = { exportAll = true },
                )
                ExportChoiceRow(
                    selected = !exportAll,
                    title = "指定月份",
                    onClick = { exportAll = false },
                )
                if (!exportAll) {
                    MonthSelector(
                        month = month,
                        onPrevious = { month = month.minusMonths(1) },
                        onNext = { month = month.plusMonths(1) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onExport(if (exportAll) ExcelExportScope.All else ExcelExportScope.Month(month))
            }) { Text("选择保存位置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ExportChoiceRow(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.padding(start = 8.dp))
    }
}
