package com.weili.overtimetracker.data

import android.net.Uri
import com.weili.overtimetracker.domain.DayOverride
import com.weili.overtimetracker.domain.OvertimeEntry
import com.weili.overtimetracker.domain.PhotoAttachment
import com.weili.overtimetracker.domain.SampleEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OvertimeRepository(
    private val entryDao: OvertimeEntryDao,
    private val overrideDao: DayOverrideDao,
    private val photoDao: PhotoAttachmentDao,
    private val photoStore: PhotoStore,
) {
    val entries: Flow<List<OvertimeEntry>> = entryDao.observeAll().map { rows ->
        rows.map(OvertimeEntryEntity::toDomain)
    }

    val overrides: Flow<List<DayOverride>> = overrideDao.observeAll().map { rows ->
        rows.map(DayOverrideEntity::toDomain)
    }

    val attachments: Flow<List<PhotoAttachment>> = photoDao.observeAll().map { rows ->
        rows.map(PhotoAttachmentEntity::toDomain)
    }

    suspend fun seedInitialEntries() {
        if (entryDao.count() == 0) {
            entryDao.insertAll(SampleEntries.july2026.map(OvertimeEntry::toEntity))
        }
    }

    suspend fun save(entry: OvertimeEntry): Long {
        if (entry.id == 0L) return entryDao.insert(entry.toEntity())
        entryDao.update(entry.toEntity())
        return entry.id
    }

    suspend fun addPhotos(entryId: Long, uris: List<Uri>): Int {
        var failures = 0
        uris.forEach { uri ->
            runCatching {
                val attachment = photoStore.import(uri, entryId)
                try {
                    photoDao.insert(attachment.toEntity())
                } catch (error: Throwable) {
                    photoStore.delete(attachment)
                    throw error
                }
            }.onFailure { failures += 1 }
        }
        return failures
    }

    suspend fun removePhotos(attachments: List<PhotoAttachment>) {
        attachments.forEach { attachment ->
            photoDao.delete(attachment.toEntity())
            photoStore.delete(attachment)
        }
    }

    suspend fun readPhoto(attachment: PhotoAttachment): ByteArray = photoStore.readBytes(attachment)

    suspend fun delete(entry: OvertimeEntry) {
        val attachments = photoDao.findByEntryId(entry.id).map(PhotoAttachmentEntity::toDomain)
        entryDao.delete(entry.toEntity())
        attachments.forEach { photoStore.delete(it) }
    }

    suspend fun deleteAll() {
        val attachments = photoDao.findAll().map(PhotoAttachmentEntity::toDomain)
        entryDao.deleteAll()
        attachments.forEach { photoStore.delete(it) }
    }

    suspend fun saveOverride(override: DayOverride) = overrideDao.upsert(override.toEntity())

    suspend fun deleteOverride(override: DayOverride) = overrideDao.delete(override.toEntity())
}
