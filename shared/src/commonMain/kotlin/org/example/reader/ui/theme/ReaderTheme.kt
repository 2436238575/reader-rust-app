package org.example.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = ReaderColors.LightPrimary,
    onPrimary = ReaderColors.LightOnPrimary,
    primaryContainer = ReaderColors.LightPrimaryContainer,
    onPrimaryContainer = ReaderColors.LightOnPrimaryContainer,
    secondary = ReaderColors.LightSecondary,
    onSecondary = ReaderColors.LightOnSecondary,
    secondaryContainer = ReaderColors.LightSecondaryContainer,
    onSecondaryContainer = ReaderColors.LightOnSecondaryContainer,
    tertiary = ReaderColors.LightTertiary,
    onTertiary = ReaderColors.LightOnTertiary,
    tertiaryContainer = ReaderColors.LightTertiaryContainer,
    onTertiaryContainer = ReaderColors.LightOnTertiaryContainer,
    error = ReaderColors.LightError,
    onError = ReaderColors.LightOnError,
    errorContainer = ReaderColors.LightErrorContainer,
    onErrorContainer = ReaderColors.LightOnErrorContainer,
    background = ReaderColors.LightSurface,
    onBackground = ReaderColors.LightOnSurface,
    surface = ReaderColors.LightSurface,
    onSurface = ReaderColors.LightOnSurface,
    surfaceVariant = ReaderColors.LightSurfaceVariant,
    onSurfaceVariant = ReaderColors.LightOnSurfaceVariant,
    outline = ReaderColors.LightOutline,
    outlineVariant = ReaderColors.LightOutlineVariant,
    inverseSurface = ReaderColors.LightInverseSurface,
    inverseOnSurface = ReaderColors.LightInverseOnSurface,
    inversePrimary = ReaderColors.LightInversePrimary,
    surfaceContainerLowest = ReaderColors.LightSurfaceContainerLowest,
    surfaceContainerLow = ReaderColors.LightSurfaceContainerLow,
    surfaceContainer = ReaderColors.LightSurfaceContainer,
    surfaceContainerHigh = ReaderColors.LightSurfaceContainerHigh,
    surfaceContainerHighest = ReaderColors.LightSurfaceContainerHighest,
    scrim = Color(0xFF000000),
)

private val DarkScheme = darkColorScheme(
    primary = ReaderColors.DarkPrimary,
    onPrimary = ReaderColors.DarkOnPrimary,
    primaryContainer = ReaderColors.DarkPrimaryContainer,
    onPrimaryContainer = ReaderColors.DarkOnPrimaryContainer,
    secondary = ReaderColors.DarkSecondary,
    onSecondary = ReaderColors.DarkOnSecondary,
    secondaryContainer = ReaderColors.DarkSecondaryContainer,
    onSecondaryContainer = ReaderColors.DarkOnSecondaryContainer,
    tertiary = ReaderColors.DarkTertiary,
    onTertiary = ReaderColors.DarkOnTertiary,
    tertiaryContainer = ReaderColors.DarkTertiaryContainer,
    onTertiaryContainer = ReaderColors.DarkOnTertiaryContainer,
    error = ReaderColors.DarkError,
    onError = ReaderColors.DarkOnError,
    errorContainer = ReaderColors.DarkErrorContainer,
    onErrorContainer = ReaderColors.DarkOnErrorContainer,
    background = ReaderColors.DarkSurface,
    onBackground = ReaderColors.DarkOnSurface,
    surface = ReaderColors.DarkSurface,
    onSurface = ReaderColors.DarkOnSurface,
    surfaceVariant = ReaderColors.DarkSurfaceVariant,
    onSurfaceVariant = ReaderColors.DarkOnSurfaceVariant,
    outline = ReaderColors.DarkOutline,
    outlineVariant = ReaderColors.DarkOutlineVariant,
    inverseSurface = ReaderColors.DarkInverseSurface,
    inverseOnSurface = ReaderColors.DarkInverseOnSurface,
    inversePrimary = ReaderColors.DarkInversePrimary,
    surfaceContainerLowest = ReaderColors.DarkSurfaceContainerLowest,
    surfaceContainerLow = ReaderColors.DarkSurfaceContainerLow,
    surfaceContainer = ReaderColors.DarkSurfaceContainer,
    surfaceContainerHigh = ReaderColors.DarkSurfaceContainerHigh,
    surfaceContainerHighest = ReaderColors.DarkSurfaceContainerHighest,
    scrim = Color(0xFF000000),
)

/**
 * 应用主题。
 *
 * 不启用 Android 12+ 动态取色：本应用有明确的品牌色（图标粉），
 * 且阅读器另有自绘主题，动态取色会让书架与阅读器视觉割裂。
 */
enum class AppThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

val LocalAppThemeMode = compositionLocalOf { AppThemeMode.System }

@Composable
fun ReaderTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = ReaderTypography,
    ) {
        CompositionLocalProvider(LocalAppThemeMode provides themeMode) {
            content()
        }
    }
}

/** 当前是否暗色（供阅读器等需要自行配色的界面使用）。 */
val isAppInDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun Color.luminance(): Float {
    fun channel(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
