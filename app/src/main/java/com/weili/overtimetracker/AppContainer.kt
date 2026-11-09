package com.weili.overtimetracker

import android.content.Context
import com.weili.overtimetracker.data.AppDatabase
import com.weili.overtimetracker.data.OvertimeRepository
import com.weili.overtimetracker.data.PhotoStore
import com.weili.overtimetracker.data.ScheduleSettingsRepository

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val photoStore = PhotoStore(context)
    val overtimeRepository = OvertimeRepository(
        database.overtimeEntryDao(),
        database.dayOverrideDao(),
        database.photoAttachmentDao(),
        photoStore,
    )
    val settingsRepository = ScheduleSettingsRepository(context)
}
