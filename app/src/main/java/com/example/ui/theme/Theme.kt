package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CosmicDarkColorScheme = darkColorScheme(
    primary = CyberEmerald,
    onPrimary = CosmicDarkBg,
    primaryContainer = CosmicSurfaceValue,
    onPrimaryContainer = CyberEmerald,
    secondary = ElectricCyan,
    onSecondary = CosmicDarkBg,
    background = CosmicDarkBg,
    onBackground = TextPrimary,
    surface = CosmicSurface,
    onSurface = TextPrimary,
    surfaceVariant = CosmicSurfaceValue,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = if (primaryColor != null) {
        val sec = secondaryColor ?: primaryColor.copy(alpha = 0.8f)
        // Gorgeous dark background color dynamically blended with 14% of the playing song's artwork color
        val dynamicBg = Color(
            red = (0.14f * primaryColor.red + 0.86f * CosmicDarkBg.red),
            green = (0.14f * primaryColor.green + 0.86f * CosmicDarkBg.green),
            blue = (0.14f * primaryColor.blue + 0.86f * CosmicDarkBg.blue),
            alpha = 1.0f
        )
        val dynamicSurface = Color(
            red = (0.18f * primaryColor.red + 0.82f * CosmicSurface.red),
            green = (0.18f * primaryColor.green + 0.82f * CosmicSurface.green),
            blue = (0.18f * primaryColor.blue + 0.82f * CosmicSurface.blue),
            alpha = 1.0f
        )
        CosmicDarkColorScheme.copy(
            primary = primaryColor,
            onPrimary = CosmicDarkBg,
            primaryContainer = CosmicSurfaceValue,
            onPrimaryContainer = primaryColor,
            secondary = sec,
            onSecondary = CosmicDarkBg,
            background = dynamicBg,
            surface = dynamicSurface,
            surfaceVariant = CosmicSurfaceValue,
            onSurfaceVariant = TextSecondary
        )
    } else {
        CosmicDarkColorScheme
    }

    // Explicitly enforce our custom dark cosmic theme for a cohesive, rich audio experience
    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        content = content
    )
}
