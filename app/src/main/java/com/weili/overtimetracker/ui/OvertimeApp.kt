package com.weili.overtimetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weili.overtimetracker.domain.OvertimeCalculator
import com.weili.overtimetracker.domain.ExcelExportScope
import com.weili.overtimetracker.domain.OvertimeEntry
import java.time.YearMonth
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainSection(val title: String, val icon: ImageVector) {
    HOME("今日", Icons.Rounded.Schedule),
    RECORDS("记录", Icons.Rounded.CalendarMonth),
    STATS("统计", Icons.Rounded.BarChart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OvertimeApp(viewModel: OvertimeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(MainSection.HOME) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<OvertimeEntry?>(null) }
    var excelExportDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingExcelScope by remember { mutableStateOf<ExcelExportScope?>(null) }
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(viewModel.exportCsv().toByteArray(Charsets.UTF_8))
                } ?: error("无法打开导出文件")
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar("CSV 已导出") }
            }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("导出失败，请重试") }
            }
        }
    }
    val excelExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ) { uri ->
        val exportScope = pendingExcelScope
        pendingExcelScope = null
        if (uri != null && exportScope != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            viewModel.exportExcel(stream, exportScope).getOrThrow()
                        } ?: error("无法打开导出文件")
                    }
                }
                result.onSuccess { exported ->
                    snackbarHostState.showSnackbar(
                        "Excel 已导出：${exported.recordCount} 条记录，${exported.photoCount} 张照片",
                    )
                }.onFailure {
                    snackbarHostState.showSnackbar("Excel 导出失败，请重试")
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val selectedSummary = remember(state.entries, state.schedule, state.overrides, selectedMonth) {
        OvertimeCalculator.summarize(
            state.entries,
            selectedMonth,
            state.schedule,
            state.overrideMap,
        )
    }
    val currentMonthSummary = remember(state.entries, state.schedule, state.overrides) {
        OvertimeCalculator.summarize(
            state.entries,
            YearMonth.now(),
            state.schedule,
            state.overrideMap,
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (settingsOpen) "设置" else "加班记") },
                navigationIcon = {
                    if (settingsOpen) {
                        IconButton(onClick = { settingsOpen = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (!settingsOpen) {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!settingsOpen) {
                NavigationBar {
                    MainSection.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == section,
                            onClick = { section = item },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!settingsOpen && section == MainSection.RECORDS) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingEntry = null
                        editorOpen = true
                    },
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    text = { Text("补录某天") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.isReady) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (settingsOpen) {
            SettingsScreen(
                schedule = state.schedule,
                overrides = state.overrides,
                onToggleWorkDay = viewModel::toggleWorkDay,
                onUpdateTime = viewModel::updateTime,
                onUpdateStandardHours = viewModel::updateStandardDayHours,
                onSaveOverride = viewModel::saveOverride,
                onDeleteOverride = viewModel::deleteOverride,
                onExportExcel = { excelExportDialogOpen = true },
                onExportCsv = {
                    csvExportLauncher.launch("加班记录-${YearMonth.now()}.csv")
                },
                onClearEntries = viewModel::clearEntries,
                modifier = Modifier.padding(padding),
            )
        } else {
            when (section) {
                MainSection.HOME -> HomeScreen(
                    state = state,
                    summary = currentMonthSummary,
                    calculateMinutes = viewModel::calculateMinutes,
                    onPunch = viewModel::punch,
                    onAddEntry = {
                        editingEntry = null
                        editorOpen = true
                    },
                    onEditEntry = {
                        editingEntry = it
                        editorOpen = true
                    },
                    modifier = Modifier.padding(padding),
                )
                MainSection.RECORDS -> RecordsScreen(
                    entries = state.entries,
                    attachmentsByEntry = state.attachmentsByEntry,
                    selectedMonth = selectedMonth,
                    onMonthChange = { selectedMonth = it },
                    calculateMinutes = viewModel::calculateMinutes,
                    onEdit = {
                        editingEntry = it
                        editorOpen = true
                    },
                    onDelete = viewModel::deleteEntry,
                    modifier = Modifier.padding(padding),
                )
                MainSection.STATS -> StatsScreen(
                    entries = state.entries,
                    summary = selectedSummary,
                    selectedMonth = selectedMonth,
                    onMonthChange = { selectedMonth = it },
                    calculateMinutes = viewModel::calculateMinutes,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (editorOpen) {
        EntryEditorSheet(
            existing = editingEntry,
            existingPhotos = state.attachmentsByEntry[editingEntry?.id].orEmpty(),
            schedule = state.schedule,
            overrides = state.overrideMap,
            onDismiss = { editorOpen = false },
            onSave = { entry, newPhotos, removedPhotoIds ->
                if (viewModel.saveEntryWithPhotos(entry, newPhotos, removedPhotoIds)) editorOpen = false
            },
        )
    }

    if (excelExportDialogOpen) {
        ExcelExportDialog(
            initialMonth = selectedMonth,
            onDismiss = { excelExportDialogOpen = false },
            onExport = { exportScope ->
                excelExportDialogOpen = false
                pendingExcelScope = exportScope
                val fileName = when (exportScope) {
                    ExcelExportScope.All -> "加班记录-全部-${LocalDate.now()}.xlsx"
                    is ExcelExportScope.Month -> "加班记录-${exportScope.month}.xlsx"
                }
                excelExportLauncher.launch(fileName)
            },
        )
    }
}
