package com.weili.overtimetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OvertimeEntryDao {
    @Query("SELECT * FROM overtime_entries ORDER BY startAt DESC")
    fun observeAll(): Flow<List<OvertimeEntryEntity>>

    @Query("SELECT COUNT(*) FROM overtime_entries")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: OvertimeEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<OvertimeEntryEntity>)

    @Update
    suspend fun update(entry: OvertimeEntryEntity)

    @Delete
    suspend fun delete(entry: OvertimeEntryEntity)

    @Query("DELETE FROM overtime_entries")
    suspend fun deleteAll()
}

@Dao
interface DayOverrideDao {
    @Query("SELECT * FROM day_overrides ORDER BY date DESC")
    fun observeAll(): Flow<List<DayOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: DayOverrideEntity)

    @Delete
    suspend fun delete(override: DayOverrideEntity)
}

@Dao
interface PhotoAttachmentDao {
    @Query("SELECT * FROM photo_attachments ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<PhotoAttachmentEntity>>

    @Query("SELECT * FROM photo_attachments WHERE entryId = :entryId ORDER BY addedAt ASC")
    suspend fun findByEntryId(entryId: Long): List<PhotoAttachmentEntity>

    @Query("SELECT * FROM photo_attachments ORDER BY addedAt ASC")
    suspend fun findAll(): List<PhotoAttachmentEntity>

    @Insert
    suspend fun insert(attachment: PhotoAttachmentEntity)

    @Delete
    suspend fun delete(attachment: PhotoAttachmentEntity)
}
