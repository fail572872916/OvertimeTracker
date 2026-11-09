package com.weili.overtimetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.weili.overtimetracker.ui.OvertimeApp
import com.weili.overtimetracker.ui.OvertimeViewModel
import com.weili.overtimetracker.ui.theme.OvertimeTrackerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: OvertimeViewModel by viewModels {
        val container = (application as OvertimeApplication).container
        OvertimeViewModel.Factory(
            repository = container.overtimeRepository,
            settingsRepository = container.settingsRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OvertimeTrackerTheme {
                OvertimeApp(viewModel)
            }
        }
    }
}
