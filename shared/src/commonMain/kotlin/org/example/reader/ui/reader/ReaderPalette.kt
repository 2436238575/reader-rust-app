package org.example.reader.ui.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 阅读器配色。
 *
 * 与前端 `utils/readerTheme.ts` 的预设**逐项、同序**对齐：两端切换同一个主题序号
 * 会得到同一种观感，也方便以后把 `reader-themeIndex` 直接同步。
 */
data class ReaderPalette(
    val label: String,
    val background: Color,
    val text: Color,
    val isDark: Boolean = false,
) {
    /** 工具条/面板底色：在正文底上叠一层淡淡的前景色，避免纯色块压住正文。 */
    val controlSurface: Color get() = lerp(background, text, if (isDark) 0.14f else 0.06f)

    /** 次要文字（章名、计数）。 */
    val secondaryText: Color get() = text.copy(alpha = 0.66f)

    /** 工具条图标：与正文同色，保证任何主题下都够对比度。 */
    val icon: Color get() = text.copy(alpha = 0.86f)
}

val ReaderPalettes: List<ReaderPalette> = listOf(
    ReaderPalette("默认", Color(0xFFF5EDE4), Color(0xFF333333)),
    ReaderPalette("纯白", Color(0xFFFFFFFF), Color(0xFF333333)),
    ReaderPalette("琥珀", Color(0xFFF5E6CE), Color(0xFF5B4636)),
    ReaderPalette("薄荷", Color(0xFFE0F0E8), Color(0xFF2D4A3E)),
    ReaderPalette("天蓝", Color(0xFFDCE8F0), Color(0xFF2C3E50)),
    ReaderPalette("粉白", Color(0xFFF5E4E8), Color(0xFF4A2D36)),
    ReaderPalette("浅灰", Color(0xFFEAEAEA), Color(0xFF333333)),
    ReaderPalette("暗夜", Color(0xFF141414), Color(0xFFC8C8C8), isDark = true),
)

fun readerPalette(index: Int): ReaderPalette = ReaderPalettes[index.coerceIn(ReaderPalettes.indices)]

/**
 * 设置面板配色。
 *
 * 面板是独立窗口（ModalBottomSheet），不会自动跟随正文主题：
 * 暗色阅读主题下如果继续用应用主题，会出现「黑底正文 + 亮粉面板」的割裂感。
 * 这里把品牌色保留、只压暗表面色与文字色。
 */
@Composable
fun readerSheetColorScheme(palette: ReaderPalette): ColorScheme {
    val base = MaterialTheme.colorScheme
    if (!palette.isDark) return base
    return base.copy(
        surface = Color(0xFF1A1A1A),
        surfaceContainerLowest = Color(0xFF0E0E0E),
        surfaceContainerLow = Color(0xFF1E1E1E),
        surfaceContainer = Color(0xFF232323),
        surfaceContainerHigh = Color(0xFF2B2B2B),
        surfaceContainerHighest = Color(0xFF343434),
        surfaceVariant = Color(0xFF3A3A3A),
        onSurface = Color(0xFFEDEDED),
        onSurfaceVariant = Color(0xFFB4B4B4),
        outline = Color(0xFF8C8C8C),
        outlineVariant = Color(0xFF474747),
        // 品牌粉在暗底上要提亮，否则按钮文字对比度不足
        primary = Color(0xFFF3B9CB),
        onPrimary = Color(0xFF3B1020),
        secondaryContainer = Color(0xFF3B2A31),
        onSecondaryContainer = Color(0xFFF2D9E1),
    )
}
