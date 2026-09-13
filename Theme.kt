package com.example.cameraandaudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColors(
    primary = AccentAmber,
    primaryVariant = AccentAmber,
    secondary = AccentAmber,
    background = SurfaceDark,
    surface = SurfaceCard
)

private val LightColors = lightColors(
    primary = Color.Black,
    primaryVariant = Color.Black,
    secondary = Color.Black,
    background = Color.White,
    surface = Color.White
)

@Composable
fun CameraAndAudioManualControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colors = colors,
        content = content
    )
}
