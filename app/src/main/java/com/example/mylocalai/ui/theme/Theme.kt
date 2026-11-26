package com.example.mylocalai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF89B4FA)
val OnPrimaryDark = Color(0xFF11111B)
val PrimaryContainerDark = Color(0xFF1E293B)
val OnPrimaryContainerDark = Color(0xFF93C5FD)
val SuccessGreen = Color(0xFFA6E3A1)
val ErrorRed = Color(0xFFF38BA8)
val BackgroundDark = Color(0xFF0F111A)
val SurfaceDark = Color(0xFF181A26)
val SurfaceVariantDark = Color(0xFF232638)
val OnSurfaceDark = Color(0xFFCDD6F4)
val OnSurfaceVariantDark = Color(0xFFA6ADC8)
val CodeBlockBackground = Color(0xFF11111B)
val CodeBlockHeader = Color(0xFF1E1E2E)
val UserBubbleColor = Color(0xFF2563EB)
val AssistantBubbleColor = Color(0xFF1E1E2E)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SuccessGreen,
    error = ErrorRed,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

@Composable
fun MYLocalAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
