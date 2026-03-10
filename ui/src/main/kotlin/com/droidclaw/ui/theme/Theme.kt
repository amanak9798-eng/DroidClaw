package com.droidclaw.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrandAi,
    secondary = BrandAgent,
    tertiary = StatusWarning,
    background = BgBase,
    surface = BgSurface,
    error = StatusDanger,
    onPrimary = BgBase,
    onSecondary = TextHigh,
    onTertiary = BgBase,
    onBackground = TextHigh,
    onSurface = TextHigh,
    onError = TextHigh,
    surfaceVariant = BorderSubtle,
    onSurfaceVariant = TextLow
)

@Composable
fun DroidClawTheme(
    // DroidClaw is fundamentally a dark-themed "Terminal" style app, so always dark.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DroidClawTypography,
        content = content
    )
}
