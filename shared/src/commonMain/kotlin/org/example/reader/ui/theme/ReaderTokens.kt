package org.example.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 品牌色取自 Vue 前端应用图标（`icon-master-1024.png` 的粉色环 #CF497C），
 * 保证安卓端与 Web 端视觉同源。
 *
 * Material 3 要求对比度：`primary` 在浅色主题下要能压在 `surface` 上达到 4.5:1，
 * 因此浅色主题的 primary 比图标粉更深一档；深色主题反过来用浅粉。
 */
object ReaderColors {
    /** 图标主色，仅用于品牌强调（如启动页、选中态描边），不直接当 primary 用。 */
    val Brand = Color(0xFFCF497C)

    // ── 浅色 ────────────────────────────────────────────────────────────────
    val LightPrimary = Color(0xFFA81E58)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFFFD9E4)
    val LightOnPrimaryContainer = Color(0xFF3F0019)

    val LightSecondary = Color(0xFF74565F)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFFFD9E4)
    val LightOnSecondaryContainer = Color(0xFF2B151C)

    val LightTertiary = Color(0xFF7B5731)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFFFDDBA)
    val LightOnTertiaryContainer = Color(0xFF2B1700)

    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)

    val LightSurface = Color(0xFFFFF8F8)
    val LightOnSurface = Color(0xFF22191C)
    val LightOnSurfaceVariant = Color(0xFF524346)
    val LightSurfaceVariant = Color(0xFFF5DDE2)
    val LightOutline = Color(0xFF847377)
    val LightOutlineVariant = Color(0xFFD6C2C6)
    val LightInverseSurface = Color(0xFF382E31)
    val LightInverseOnSurface = Color(0xFFFFECEF)
    val LightInversePrimary = Color(0xFFFFB1CA)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFFFF0F2)
    val LightSurfaceContainer = Color(0xFFFCEAED)
    val LightSurfaceContainerHigh = Color(0xFFF6E4E7)
    val LightSurfaceContainerHighest = Color(0xFFF0DFE1)

    // ── 深色 ────────────────────────────────────────────────────────────────
    val DarkPrimary = Color(0xFFFFB1CA)
    val DarkOnPrimary = Color(0xFF65002C)
    val DarkPrimaryContainer = Color(0xFF8E0042)
    val DarkOnPrimaryContainer = Color(0xFFFFD9E4)

    val DarkSecondary = Color(0xFFE3BDC6)
    val DarkOnSecondary = Color(0xFF422931)
    val DarkSecondaryContainer = Color(0xFF5A3F47)
    val DarkOnSecondaryContainer = Color(0xFFFFD9E4)

    val DarkTertiary = Color(0xFFECBE8F)
    val DarkOnTertiary = Color(0xFF452B08)
    val DarkTertiaryContainer = Color(0xFF5F401C)
    val DarkOnTertiaryContainer = Color(0xFFFFDDBA)

    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val DarkSurface = Color(0xFF181113)
    val DarkOnSurface = Color(0xFFEDDFE1)
    val DarkOnSurfaceVariant = Color(0xFFD6C2C6)
    val DarkSurfaceVariant = Color(0xFF524346)
    val DarkOutline = Color(0xFF9F8C90)
    val DarkOutlineVariant = Color(0xFF524346)
    val DarkInverseSurface = Color(0xFFEDDFE1)
    val DarkInverseOnSurface = Color(0xFF382E31)
    val DarkInversePrimary = Color(0xFFA81E58)
    val DarkSurfaceContainerLowest = Color(0xFF120C0E)
    val DarkSurfaceContainerLow = Color(0xFF201A1B)
    val DarkSurfaceContainer = Color(0xFF251E20)
    val DarkSurfaceContainerHigh = Color(0xFF30282A)
    val DarkSurfaceContainerHighest = Color(0xFF3B3235)
}

/**
 * 阅读器主题预设：与 Web 端 `utils/readerTheme.ts` 的 8 个色板逐项对齐。
 *
 * 阅读器不跟随 M3 配色——它是独立的一套「纸张」视觉，只有工具栏/面板
 * 弹层才回到 M3 surface 体系。
 */
data class ReaderThemePreset(
    val name: String,
    val background: Color,
    val text: Color,
    val isDark: Boolean,
)

object ReaderThemePresets {
    /** 顺序与 Web 端一致，索引存在 [org.example.reader.core.settings.SettingsKeys.READER_THEME_INDEX]。 */
    val all: List<ReaderThemePreset> = listOf(
        ReaderThemePreset("默认", Color(0xFFF7F3EA), Color(0xFF2B2B2B), false),
        ReaderThemePreset("纯白", Color(0xFFFFFFFF), Color(0xFF1A1A1A), false),
        ReaderThemePreset("琥珀", Color(0xFFFBF0D9), Color(0xFF3B2E18), false),
        ReaderThemePreset("薄荷", Color(0xFFE8F5EC), Color(0xFF1E3327), false),
        ReaderThemePreset("天蓝", Color(0xFFE8F1FA), Color(0xFF1C2C3A), false),
        ReaderThemePreset("粉白", Color(0xFFFCEEF1), Color(0xFF3A2228), false),
        ReaderThemePreset("浅灰", Color(0xFFEDEDED), Color(0xFF2A2A2A), false),
        ReaderThemePreset("暗夜", Color(0xFF17181A), Color(0xFFC8C8C8), true),
    )

    /** 夜间模式恒用最后一档（暗夜）。 */
    val night: ReaderThemePreset get() = all.last()

    fun day(index: Int): ReaderThemePreset = all.getOrElse(index) { all.first() }
}

/**
 * 正文字体。
 *
 * 只提供系统可用的通用族：Web 端的 MiSans 自托管字体不随 App 分发，
 * 移动端用系统字体更稳，也避免首包多几 MB。
 */
enum class ReaderFontFamily(val label: String) {
    System("系统"),
    SansSerif("黑体"),
    Serif("宋体"),
    Monospace("等宽"),
    Cursive("楷体"),
    ;

    fun toCompose(): FontFamily = when (this) {
        System -> FontFamily.Default
        SansSerif -> FontFamily.SansSerif
        Serif -> FontFamily.Serif
        Monospace -> FontFamily.Monospace
        Cursive -> FontFamily.Cursive
    }
}

/**
 * Material 3 字体规格。
 *
 * 中文在小字号下的行高需要比默认 M3 稍大，否则密排正文会粘在一起；
 * 这里只微调 body 系列，title/headline 保持 M3 默认比例。
 */
val ReaderTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
