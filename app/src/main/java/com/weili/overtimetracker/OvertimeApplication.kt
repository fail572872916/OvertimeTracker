package com.weili.overtimetracker

import android.app.Application

class OvertimeApplication : Application() {
    val container by lazy { AppContainer(this) }
}
