package com.weili.overtimetracker.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weili.overtimetracker.data.OvertimeRepository
import com.weili.overtimetracker.data.ScheduleSettingsRepository
import com.weili.overtimetracker.data.TimeSetting
import com.weili.overtimetracker.data.CsvExporter
import com.weili.overtimetracker.data.ExcelExportData
import com.weili.overtimetracker.data.ExcelExportResult
import com.weili.overtimetracker.data.ExcelPhoto
import com.weili.overtimetracker.data.ExcelRow
import com.weili.overtimetracker.domain.DayOverride
import com.weili.overtimetracker.domain.ExcelExportScope
import com.weili.overtimetracker.domain.MonthlySummary
import com.weili.overtimetracker.domain.OvertimeCalculator
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.PhotoAttachment
import com.weili.overtimetracker.domain.PunchInferenceEngine
import com.weili.overtimetracker.domain.WorkSchedule
import com.weili.overtimetracker.data.XlsxExporter
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OvertimeUiState(
    val entries: List<OvertimeEntry> = emptyList(),
    val overrides: List<DayOverride> = emptyList(),
    val attachments: List<PhotoAttachment> = emptyList(),
    val schedule: WorkSchedule = WorkSchedule(),
    val isReady: Boolean = false,
) {
    val overrideMap: Map<LocalDate, DayOverride>
        get() = overrides.associateBy(DayOverride::date)

    val attachmentsByEntry: Map<Long, List<PhotoAttachment>>
        get() = attachments.groupBy(PhotoAttachment::entryId)
}

class OvertimeViewModel(
    private val repository: OvertimeRepository,
    private val settingsRepository: ScheduleSettingsRepository,
) : ViewModel() {
    val uiState = combine(
        repository.entries,
        repository.overrides,
        repository.attachments,
        settingsRepository.schedule,
    ) { entries, overrides, attachments, schedule ->
        OvertimeUiState(
            entries = entries,
            overrides = overrides,
            attachments = attachments,
            schedule = schedule,
            isReady = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OvertimeUiState(),
    )

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (!settingsRepository.initialDataSeeded.first()) {
                repository.seedInitialEntries()
                settingsRepository.markInitialDataSeeded()
            }
        }
    }

    fun summary(month: YearMonth): MonthlySummary {
        val state = uiState.value
        return OvertimeCalculator.summarize(
            entries = state.entries,
            month = month,
            schedule = state.schedule,
            overrides = state.overrideMap,
        )
    }

    fun calculateMinutes(entry: OvertimeEntry): Long {
        val state = uiState.value
        return OvertimeCalculator.calculateMinutes(entry, state.schedule, state.overrideMap)
    }

    fun exportCsv(): String {
        val state = uiState.value
        return CsvExporter.create(state.entries, state.schedule, state.overrideMap)
    }

    suspend fun exportExcel(output: OutputStream, exportScope: ExcelExportScope): Result<ExcelExportResult> =
        runCatching {
            val state = uiState.value
            val entries = when (exportScope) {
                ExcelExportScope.All -> state.entries
                is ExcelExportScope.Month -> state.entries.filter {
                    YearMonth.from(it.serviceDate) == exportScope.month
                }
            }.sortedBy(OvertimeEntry::startAt)
            val attachmentsByEntry = state.attachmentsByEntry
            val rows = entries.map { entry ->
                val photos = attachmentsByEntry[entry.id].orEmpty().take(MAX_PHOTOS_PER_ENTRY).mapNotNull { attachment ->
                    runCatching {
                        ExcelPhoto(
                            displayName = attachment.displayName,
                            mimeType = attachment.mimeType,
                            bytes = repository.readPhoto(attachment),
                        )
                    }.getOrNull()
                }
                ExcelRow(
                    entry = entry,
                    overtimeMinutes = OvertimeCalculator.calculateMinutes(
                        entry,
                        state.schedule,
                        state.overrideMap,
                    ),
                    dayType = OvertimeCalculator.resolveDayType(
                        entry.serviceDate,
                        state.schedule,
                        state.overrideMap[entry.serviceDate],
                    ),
                    photos = photos,
                )
            }
            XlsxExporter.write(
                data = ExcelExportData(
                    scopeLabel = when (exportScope) {
                        ExcelExportScope.All -> "全部记录"
                        is ExcelExportScope.Month -> "${exportScope.month.year}年${exportScope.month.monthValue}月"
                    },
                    rows = rows,
                    schedule = state.schedule,
                    generatedAt = LocalDateTime.now(),
                ),
                output = output,
            )
        }

    fun punch(now: LocalDateTime) {
        val state = uiState.value
        val proposal = PunchInferenceEngine.infer(now, state.schedule, state.overrideMap)
        if (proposal == null) {
            _events.tryEmit("当前不在可自动推断的加班时段，请使用补卡")
            return
        }
        saveEntry(
            OvertimeEntry(
                serviceDate = proposal.serviceDate,
                startAt = proposal.startAt,
                endAt = proposal.endAt,
                source = com.weili.overtimetracker.domain.EntrySource.AUTO_PUNCH,
                startWasInferred = true,
            ),
            successMessage = "打卡已保存",
        )
    }

    fun saveEntry(entry: OvertimeEntry, successMessage: String = "记录已保存"): Boolean {
        return saveEntryWithPhotos(entry, emptyList(), emptySet(), successMessage)
    }

    fun saveEntryWithPhotos(
        entry: OvertimeEntry,
        newPhotoUris: List<Uri>,
        removedPhotoIds: Set<String>,
        successMessage: String = "记录已保存",
    ): Boolean {
        val validationMessage = validate(entry)
        if (validationMessage != null) {
            _events.tryEmit(validationMessage)
            return false
        }
        val currentPhotos = uiState.value.attachmentsByEntry[entry.id].orEmpty()
        val finalPhotoCount = currentPhotos.count { it.id !in removedPhotoIds } + newPhotoUris.size
        if (finalPhotoCount > MAX_PHOTOS_PER_ENTRY) {
            _events.tryEmit("每条记录最多添加 $MAX_PHOTOS_PER_ENTRY 张照片")
            return false
        }
        viewModelScope.launch {
            runCatching {
                val savedId = repository.save(entry)
                repository.removePhotos(currentPhotos.filter { it.id in removedPhotoIds })
                repository.addPhotos(savedId, newPhotoUris)
            }.onSuccess { failedPhotos ->
                if (failedPhotos == 0) {
                    _events.emit(successMessage)
                } else {
                    _events.emit("记录已保存，$failedPhotos 张照片未能添加")
                }
            }.onFailure {
                _events.emit("保存失败，请重试")
            }
        }
        return true
    }

    fun deleteEntry(entry: OvertimeEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            _events.emit("记录已删除")
        }
    }

    fun clearEntries() {
        viewModelScope.launch {
            repository.deleteAll()
            _events.emit("所有加班记录已清空")
        }
    }

    fun updateTime(setting: TimeSetting, time: LocalTime) {
        viewModelScope.launch { settingsRepository.updateTime(setting, time) }
    }

    fun updateStandardDayHours(hours: Int) {
        viewModelScope.launch { settingsRepository.updateStandardDayHours(hours) }
    }

    fun toggleWorkDay(day: DayOfWeek) {
        val current = uiState.value.schedule.workDays
        val updated = if (day in current) current - day else current + day
        if (updated.isEmpty()) {
            _events.tryEmit("至少保留一个工作日")
            return
        }
        viewModelScope.launch { settingsRepository.updateWorkDays(updated) }
    }

    fun saveOverride(override: DayOverride) {
        viewModelScope.launch {
            repository.saveOverride(override)
            _events.emit("特殊日期已保存")
        }
    }

    fun deleteOverride(override: DayOverride) {
        viewModelScope.launch { repository.deleteOverride(override) }
    }

    private fun validate(entry: OvertimeEntry): String? {
        if (!entry.endAt.isAfter(entry.startAt)) return "结束时间必须晚于开始时间"
        if (java.time.Duration.between(entry.startAt, entry.endAt).toDays() >= 7) {
            return "单条记录不能超过 7 天"
        }
        val overlaps = uiState.value.entries.any { existing ->
            existing.id != entry.id &&
                entry.startAt.isBefore(existing.endAt) &&
                entry.endAt.isAfter(existing.startAt)
        }
        if (overlaps) return "该时段与已有记录重叠，请先修改已有记录"
        if (calculateMinutes(entry) <= 0) return "这段时间不包含可统计的加班"
        return null
    }

    class Factory(
        private val repository: OvertimeRepository,
        private val settingsRepository: ScheduleSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OvertimeViewModel(repository, settingsRepository) as T
        }
    }

    companion object {
        const val MAX_PHOTOS_PER_ENTRY = 4
    }
}
