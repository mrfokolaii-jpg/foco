package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

private val PolishDarkColorScheme = darkColorScheme(
    primary = PolishPrimaryDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = PolishPrimary,
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = PolishSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = PolishSecondaryContainer,
    tertiary = PolishTertiary,
    onTertiary = Color.White,
    background = PolishDarkBackground,
    onBackground = PolishTextPrimaryDark,
    surface = PolishDarkSurface,
    onSurface = PolishTextPrimaryDark,
    surfaceVariant = PolishDarkSurfaceElevated,
    onSurfaceVariant = PolishTextSecondaryDark,
    outline = PolishDarkBorder,
    error = PolishError,
    onError = Color.White
)

private val PolishLightColorScheme = lightColorScheme(
    primary = PolishPrimary,
    onPrimary = Color.White,
    primaryContainer = PolishPrimaryContainer,
    onPrimaryContainer = PolishOnPrimaryContainer,
    secondary = PolishSecondary,
    onSecondary = Color.White,
    secondaryContainer = PolishSecondaryContainer,
    onSecondaryContainer = PolishOnSecondaryContainer,
    tertiary = PolishTertiary,
    onTertiary = Color.White,
    background = PolishLightBackground,
    onBackground = PolishTextPrimaryLight,
    surface = PolishLightSurface,
    onSurface = PolishTextPrimaryLight,
    surfaceVariant = PolishLightSurfaceElevated,
    onSurfaceVariant = PolishTextSecondaryLight,
    outline = PolishLightBorder,
    error = PolishError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Default to clean Professional Polish light theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PolishDarkColorScheme else PolishLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Force RTL layout direction across the entire app for authentic Persian UX
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
