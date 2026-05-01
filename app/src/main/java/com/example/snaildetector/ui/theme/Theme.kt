package com.example.snaildetector.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary        = Teal500,
    onPrimary      = SurfaceDark,
    primaryContainer  = Teal700,
    background     = SurfaceDark,
    surface        = SurfaceDark,
    onBackground   = NeutralLight,
    onSurface      = NeutralLight,
    secondary      = Teal200,
    onSecondary    = NeutralDark,
    error          = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary        = Teal700,
    onPrimary      = SurfaceLight,
    primaryContainer  = Teal200,
    background     = SurfaceLight,
    surface        = SurfaceLight,
    onBackground   = NeutralDark,
    onSurface      = NeutralDark,
    secondary      = Teal500,
    onSecondary    = SurfaceLight,
    error          = ErrorRed
)

@Composable
fun SnailDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
