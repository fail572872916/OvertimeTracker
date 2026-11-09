package com.weili.overtimetracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import com.weili.overtimetracker.data.PhotoStore
import com.weili.overtimetracker.domain.EntrySource
import com.weili.overtimetracker.domain.OvertimeCalculator
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.PhotoAttachment
import com.weili.overtimetracker.domain.PunchInferenceEngine
import com.weili.overtimetracker.domain.WorkSchedule
import com.weili.overtimetracker.domain.formatMinutes
import java.time.LocalDate
import java.time.LocalDateTime
import java.io.File
import androidx.compose.ui.platform.LocalContext
import com.weili.overtimetracker.ui.camera.WatermarkCameraDialog
import com.weili.overtimetracker.ui.camera.WatermarkPhotoProcessor

private enum class EditorPicker {
    SERVICE_DATE,
    START_DATE,
    START_TIME,
    END_DATE,
    END_TIME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorSheet(
    existing: OvertimeEntry?,
    existingPhotos: List<PhotoAttachment>,
    schedule: WorkSchedule,
    overrides: Map<LocalDate, com.weili.overtimetracker.domain.DayOverride>,
    onDismiss: () -> Unit,
    onSave: (OvertimeEntry, List<Uri>, Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    val defaultInterval = remember(now, schedule, overrides) {
        val proposal = PunchInferenceEngine.infer(now, schedule, overrides)
        if (proposal != null) {
            proposal.startAt to proposal.endAt
        } else {
            val fallbackDate = now.toLocalDate().minusDays(1)
            val type = OvertimeCalculator.resolveDayType(fallbackDate, schedule, overrides[fallbackDate])
            val start = fallbackDate.atTime(
                if (type == com.weili.overtimetracker.domain.DayType.WORKDAY) {
                    schedule.normalEnd
                } else {
                    schedule.weekendDefaultStart
                },
            )
            start to start.plusHours(1)
        }
    }
    var serviceDate by remember(existing?.id) { mutableStateOf(existing?.serviceDate ?: defaultInterval.first.toLocalDate()) }
    var startAt by remember(existing?.id) { mutableStateOf(existing?.startAt ?: defaultInterval.first) }
    var endAt by remember(existing?.id) {
        mutableStateOf(existing?.endAt ?: defaultInterval.second)
    }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var picker by remember { mutableStateOf<EditorPicker?>(null) }
    var pendingPhotoUris by remember(existing?.id) { mutableStateOf<List<Uri>>(emptyList()) }
    var removedPhotoIds by remember(existing?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var watermarkCameraOpen by remember { mutableStateOf(false) }
    val visibleExistingPhotos = existingPhotos.filter { it.id !in removedPhotoIds }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(OvertimeViewModel.MAX_PHOTOS_PER_ENTRY),
    ) { uris ->
        val available = OvertimeViewModel.MAX_PHOTOS_PER_ENTRY - visibleExistingPhotos.size - pendingPhotoUris.size
        val additions = uris.filterNot { it in pendingPhotoUris }.take(available.coerceAtLeast(0))
        pendingPhotoUris = pendingPhotoUris + additions
    }
    val dismissEditor = {
        pendingPhotoUris.forEach { WatermarkPhotoProcessor.deleteIfTemporary(context, it) }
        onDismiss()
    }

    val draft = OvertimeEntry(
        id = existing?.id ?: 0,
        serviceDate = serviceDate,
        startAt = startAt,
        endAt = endAt,
        source = EntrySource.MANUAL,
        startWasInferred = false,
        note = note.trim(),
    )
    val validRange = endAt.isAfter(startAt)
    val minutes = if (validRange) OvertimeCalculator.calculateMinutes(draft, schedule, overrides) else 0

    ModalBottomSheet(onDismissRequest = dismissEditor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (existing == null) "补录某天加班" else "修改加班记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text("归属日期", style = MaterialTheme.typography.labelLarge)
            androidx.compose.material3.OutlinedButton(
                onClick = { picker = EditorPicker.SERVICE_DATE },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(serviceDate.toString())
            }

            DateTimeSelector(
                label = "开始时间",
                value = startAt,
                onDateClick = { picker = EditorPicker.START_DATE },
                onTimeClick = { picker = EditorPicker.START_TIME },
            )
            DateTimeSelector(
                label = "结束时间",
                value = endAt,
                onDateClick = { picker = EditorPicker.END_DATE },
                onTimeClick = { picker = EditorPicker.END_TIME },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("跨天", style = MaterialTheme.typography.labelLarge)
                    Text("结束日期设为次日", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = endAt.toLocalDate().isAfter(startAt.toLocalDate()),
                    onCheckedChange = { checked ->
                        endAt = endAt.withYear(startAt.year)
                            .withMonth(startAt.monthValue)
                            .withDayOfMonth(startAt.dayOfMonth)
                            .plusDays(if (checked) 1 else 0)
                    },
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(100) },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("工作证明照片", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${visibleExistingPhotos.size + pendingPhotoUris.size}/${OvertimeViewModel.MAX_PHOTOS_PER_ENTRY}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (visibleExistingPhotos.isNotEmpty() || pendingPhotoUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visibleExistingPhotos.forEach { photo ->
                            PhotoThumbnail(
                                uri = Uri.fromFile(File(context.filesDir, "${PhotoStore.PHOTO_DIRECTORY}/${photo.relativePath}")),
                                contentDescription = photo.displayName,
                                onRemove = { removedPhotoIds = removedPhotoIds + photo.id },
                            )
                        }
                        pendingPhotoUris.forEach { uri ->
                            PhotoThumbnail(
                                uri = uri,
                                contentDescription = "待添加的工作证明",
                                onRemove = {
                                    WatermarkPhotoProcessor.deleteIfTemporary(context, uri)
                                    pendingPhotoUris = pendingPhotoUris - uri
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val canAddPhoto = visibleExistingPhotos.size + pendingPhotoUris.size <
                        OvertimeViewModel.MAX_PHOTOS_PER_ENTRY
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = canAddPhoto,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("选择照片")
                    }
                    OutlinedButton(
                        onClick = { watermarkCameraOpen = true },
                        enabled = canAddPhoto,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("水印相机")
                    }
                }
            }

            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最终可计加班", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (validRange) formatMinutes(minutes) else "时间范围无效",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (minutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "正常工作时段会自动排除",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onSave(draft, pendingPhotoUris, removedPhotoIds) },
                enabled = validRange && minutes > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("保存记录")
            }
        }
    }

    when (picker) {
        EditorPicker.SERVICE_DATE -> AppDatePickerDialog(
            initialDate = serviceDate,
            onDismiss = { picker = null },
            onConfirm = { serviceDate = it; picker = null },
        )
        EditorPicker.START_DATE -> AppDatePickerDialog(
            initialDate = startAt.toLocalDate(),
            onDismiss = { picker = null },
            onConfirm = { date ->
                startAt = LocalDateTime.of(date, startAt.toLocalTime())
                serviceDate = date
                picker = null
            },
        )
        EditorPicker.START_TIME -> AppTimePickerDialog(
            initialTime = startAt.toLocalTime(),
            title = "选择开始时间",
            onDismiss = { picker = null },
            onConfirm = { startAt = LocalDateTime.of(startAt.toLocalDate(), it); picker = null },
        )
        EditorPicker.END_DATE -> AppDatePickerDialog(
            initialDate = endAt.toLocalDate(),
            onDismiss = { picker = null },
            onConfirm = { endAt = LocalDateTime.of(it, endAt.toLocalTime()); picker = null },
        )
        EditorPicker.END_TIME -> AppTimePickerDialog(
            initialTime = endAt.toLocalTime(),
            title = "选择结束时间",
            onDismiss = { picker = null },
            onConfirm = { endAt = LocalDateTime.of(endAt.toLocalDate(), it); picker = null },
        )
        null -> Unit
    }

    if (watermarkCameraOpen) {
        WatermarkCameraDialog(
            onDismiss = { watermarkCameraOpen = false },
            onPhotoCaptured = { uri ->
                val canAddPhoto = visibleExistingPhotos.size + pendingPhotoUris.size <
                    OvertimeViewModel.MAX_PHOTOS_PER_ENTRY
                if (canAddPhoto) {
                    pendingPhotoUris = pendingPhotoUris + uri
                } else {
                    WatermarkPhotoProcessor.deleteIfTemporary(context, uri)
                }
                watermarkCameraOpen = false
            },
        )
    }
}
