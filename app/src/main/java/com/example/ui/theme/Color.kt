package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val CosmicDarkBg = Color(0xFF1C1B1F)
val CosmicSurface = Color(0xFF2B2930)
val CosmicSurfaceValue = Color(0xFF49454F)

val CyberEmerald = Color(0xFFD0BCFF)
val ElectricCyan = Color(0xFFEADDFF)
val ElectricCyanAlt = Color(0xFF381E72)

val LocalCyberEmerald = androidx.compose.runtime.compositionLocalOf { CyberEmerald }
val LocalElectricCyan = androidx.compose.runtime.compositionLocalOf { ElectricCyan }
val LocalTrackColors = androidx.compose.runtime.compositionLocalOf { listOf(CyberEmerald, ElectricCyan, ElectricCyanAlt) }

val TextPrimary = Color(0xFFE6E1E5)
val TextSecondary = Color(0xFF938F99)
val TextMuted = Color(0xFF6750A4)

val ErrorRed = Color(0xFFF2B8B5)
val SuccessGreen = Color(0xFF21005D)

