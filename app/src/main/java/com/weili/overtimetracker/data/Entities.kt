package com.weili.overtimetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.weili.overtimetracker.domain.DayOverride
import com.weili.overtimetracker.domain.DayType
import com.weili.overtimetracker.domain.EntrySource
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.PhotoAttachment
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "overtime_entries")
data class OvertimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceDate: String,
    val startAt: String,
    val endAt: String,
    val source: String,
    val startWasInferred: Boolean,
    val note: String,
)

@Entity(tableName = "day_overrides")
data class DayOverrideEntity(
    @PrimaryKey val date: String,
    val type: String,
    val note: String,
)

@Entity(
    tableName = "photo_attachments",
    foreignKeys = [
        ForeignKey(
            entity = OvertimeEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class PhotoAttachmentEntity(
    @PrimaryKey val id: String,
    val entryId: Long,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: String,
)

fun OvertimeEntryEntity.toDomain() = OvertimeEntry(
    id = id,
    serviceDate = LocalDate.parse(serviceDate),
    startAt = LocalDateTime.parse(startAt),
    endAt = LocalDateTime.parse(endAt),
    source = EntrySource.valueOf(source),
    startWasInferred = startWasInferred,
    note = note,
)

fun OvertimeEntry.toEntity() = OvertimeEntryEntity(
    id = id,
    serviceDate = serviceDate.toString(),
    startAt = startAt.toString(),
    endAt = endAt.toString(),
    source = source.name,
    startWasInferred = startWasInferred,
    note = note,
)

fun DayOverrideEntity.toDomain() = DayOverride(
    date = LocalDate.parse(date),
    type = DayType.valueOf(type),
    note = note,
)

fun DayOverride.toEntity() = DayOverrideEntity(
    date = date.toString(),
    type = type.name,
    note = note,
)

fun PhotoAttachmentEntity.toDomain() = PhotoAttachment(
    id = id,
    entryId = entryId,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    addedAt = LocalDateTime.parse(addedAt),
)

fun PhotoAttachment.toEntity() = PhotoAttachmentEntity(
    id = id,
    entryId = entryId,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    addedAt = addedAt.toString(),
)
