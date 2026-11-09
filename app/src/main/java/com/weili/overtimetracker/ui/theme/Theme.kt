package com.weili.overtimetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2D8),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF49647A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE5FA),
    onSecondaryContainer = Color(0xFF061E2D),
    tertiary = Color(0xFF855300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB7),
    background = Color(0xFFF8FAF7),
    surface = Color(0xFFF8FAF7),
    surfaceVariant = Color(0xFFDCE5E0),
    outline = Color(0xFF707975),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D6BC),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005141),
    secondary = Color(0xFFB1C9DE),
    onSecondary = Color(0xFF1B3447),
    tertiary = Color(0xFFFFB95F),
    onTertiary = Color(0xFF472A00),
    background = Color(0xFF111412),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF404945),
    outline = Color(0xFF89938E),
)

@Composable
fun OvertimeTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
