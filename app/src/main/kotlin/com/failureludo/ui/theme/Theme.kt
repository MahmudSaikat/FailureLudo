package com.failureludo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    secondary        = Secondary,
    background       = Background,
    surface          = Surface,
    onSurface        = OnSurface
)

private val DarkColors = darkColorScheme(
    primary          = Secondary,
    onPrimary        = OnSurface,
    secondary        = Secondary,
    background       = OnSurface,
    surface          = OnSurface,
    onSurface        = Surface
)

@Composable
fun FailureLudoTheme(
    darkTheme: Boolean = false,
    forceLightSystemBarIcons: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme && !forceLightSystemBarIcons
                isAppearanceLightNavigationBars = !darkTheme && !forceLightSystemBarIcons
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = LudoTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content     = content
    )
}
