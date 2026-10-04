package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PurpleBlackColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = Color.White,
    primaryContainer = ContainerPurple,
    onPrimaryContainer = OnContainerPurple,
    secondary = NeonViolet,
    onSecondary = ObsidianBlack,
    secondaryContainer = ObsidianSurfaceVariant,
    onSecondaryContainer = LavenderGlow,
    tertiary = CyberCyan,
    onTertiary = ObsidianBlack,
    tertiaryContainer = Color(0xFF0F3044),
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = ObsidianBlack,
    onBackground = PureWhite,
    surface = ObsidianSurface,
    onSurface = PureWhite,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHighest = ObsidianSurfaceVariant,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF2C2145),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFD1DC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force sleek luxury dark purple aesthetic requested by user
    dynamicColor: Boolean = false, // Keep consistent purple & black branding
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PurpleBlackColorScheme,
        typography = Typography,
        content = content
    )
}
