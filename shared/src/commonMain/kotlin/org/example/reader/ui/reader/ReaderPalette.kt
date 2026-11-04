package org.example.reader.ui.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.example.reader.ui.theme.ReaderColors

/**
 * 阅读器配色。
 *
 * 与前端 `utils/readerTheme.ts` 的预设**逐项、同序**对齐：两端切换同一个主题序号
 * 会得到同一种观感，也方便以后把 `reader-themeIndex` 直接同步。
 *
 * 有意偏离 Web 端的一处：Android 暗色主题的正文比 Web 端更亮（0xFFEDEDED 而非
 * 0xFFC8C8C8）。手机多为 OLED + 没有子像素抗锯齿，同样标称对比度下笔画边缘更容易
 * 发灰、发糊，实测同样「够用」的观感需要更高的亮度差；Web 端 `readerTheme.ts`
 * 未同步，属有意为之，不要按「两端一致」把它改回去。
 */
data class ReaderPalette(
    val label: String,
    val background: Color,
    val text: Color,
    val isDark: Boolean = false,
    /** 高对比（护眼）档位标记，只影响次要文字与图标的实度。 */
    private val boosted: Boolean = false,
) {
    /** 工具条/面板底色：在正文底上叠一层淡淡的前景色，避免纯色块压住正文。 */
    val controlSurface: Color get() = lerp(background, text, if (isDark) 0.14f else 0.06f)

    /**
     * 次要文字（章名、计数、功能项标签）。
     *
     * 暗色下用更高的 α（0.78）：暗底上浅色文字的视觉亮度损失比亮底上的深色文字大得多，
     * 统一用 0.66 时暗色主题的章名明显发灰、读不出来（R-21 的直接症状）。
     */
    val secondaryText: Color
        get() = text.copy(
            alpha = when {
                boosted -> 0.90f
                isDark -> 0.78f
                else -> 0.70f
            },
        )

    /** 工具条图标：与正文同色，保证任何主题下都够对比度。 */
    val icon: Color get() = text.copy(alpha = if (boosted) 0.96f else 0.92f)

    /**
     * 护眼模式：**只拉高文字对比度**，不叠任何颜色蒙版。
     *
     * 低蓝光 / 暖色滤镜交给系统「护眼模式」（各家做得比我们好，也不会和屏幕硬件冲突），
     * 应用层只做自己能做好的那件事：让正文更实。
     */
    fun highContrast(): ReaderPalette = copy(
        text = if (isDark) Color.White else Color.Black,
        boosted = true,
    )
}

val ReaderPalettes: List<ReaderPalette> = listOf(
    ReaderPalette("默认", Color(0xFFF5EDE4), Color(0xFF333333)),
    ReaderPalette("纯白", Color(0xFFFFFFFF), Color(0xFF333333)),
    ReaderPalette("琥珀", Color(0xFFF5E6CE), Color(0xFF5B4636)),
    ReaderPalette("薄荷", Color(0xFFE0F0E8), Color(0xFF2D4A3E)),
    ReaderPalette("天蓝", Color(0xFFDCE8F0), Color(0xFF2C3E50)),
    ReaderPalette("粉白", Color(0xFFF5E4E8), Color(0xFF4A2D36)),
    ReaderPalette("浅灰", Color(0xFFEAEAEA), Color(0xFF333333)),
    ReaderPalette("暗夜", Color(0xFF141414), Color(0xFFEDEDED), isDark = true),
)

fun readerPalette(index: Int): ReaderPalette = ReaderPalettes[index.coerceIn(ReaderPalettes.indices)]

/**
 * 设置面板配色。
 *
 * 面板是独立窗口（ModalBottomSheet），不继承外层组合的阅读主题：
 * 亮色阅读主题下如果继续用应用主题，会出现「米黄正文 + 白面板」的割裂感；
 * 暗色主题下则会出现「黑底正文 + 亮粉面板」。
 * 因此**两个分支都从 [palette] 派生**，品牌色保留。
 *
 * 亮度不再通过调暗面板底色来实现，而是由窗口级 `screenBrightness` 统一压暗
 * （见 `ApplyWindowBrightness`）——所以这里不需要 `dim` 参数，也不需要
 * 为「文字跟着变暗会不会丢对比度」留夹取上限。
 */
@Composable
fun readerSheetColorScheme(palette: ReaderPalette): ColorScheme {
    val base = MaterialTheme.colorScheme
    val surface = palette.background
    // 容器阶梯的方向跟随明暗：亮色主题层级越高越深，暗色主题越高越亮（与 M3 同向）
    val ladder = if (palette.isDark) Color.White else Color.Black
    fun step(factor: Float): Color = lerp(surface, ladder, factor)

    return if (palette.isDark) {
        base.copy(
            surface = surface,
            // 暗色下 Lowest 反而要比 surface 更暗（对话框底衬）
            surfaceContainerLowest = lerp(surface, Color.Black, 0.05f),
            surfaceContainerLow = step(0.02f),
            surfaceContainer = step(0.045f),
            surfaceContainerHigh = step(0.08f),
            surfaceContainerHighest = step(0.125f),
            surfaceVariant = step(0.16f),
            // 跟随正文前景色：暗夜主题下等于 0xFFEDEDED，护眼（高对比）时是纯白
            onSurface = palette.text,
            onSurfaceVariant = Color(0xFFB4B4B4),
            outline = Color(0xFF8C8C8C),
            outlineVariant = Color(0xFF474747),
            // 品牌粉在暗底上要提亮，否则按钮文字对比度不足
            primary = Color(0xFFF3B9CB),
            onPrimary = Color(0xFF3B1020),
            secondaryContainer = Color(0xFF3B2A31),
            onSecondaryContainer = Color(0xFFF2D9E1),
        )
    } else {
        base.copy(
            surface = surface,
            // 亮色下 Lowest 比 surface 更亮（近白），往上逐级加深
            surfaceContainerLowest = lerp(surface, Color.White, 0.5f),
            surfaceContainerLow = step(0.02f),
            surfaceContainer = step(0.045f),
            surfaceContainerHigh = step(0.08f),
            surfaceContainerHighest = step(0.125f),
            surfaceVariant = step(0.10f),
            // 文字取纸张前景色：米黄纸上的深棕字，比应用主题的灰黑字更贴
            onSurface = palette.text,
            onSurfaceVariant = lerp(palette.text, surface, 0.35f),
            outline = lerp(palette.text, surface, 0.50f),
            outlineVariant = lerp(palette.text, surface, 0.80f),
            // 品牌色必须取**浅色** token，不能取 base（base 可能是应用暗色主题：
            // 系统暗色 + 纸色阅读主题时 base.primary 是浅粉，落在米黄底上几乎看不见）
            primary = ReaderColors.LightPrimary,
            onPrimary = ReaderColors.LightOnPrimary,
            primaryContainer = ReaderColors.LightPrimaryContainer,
            onPrimaryContainer = ReaderColors.LightOnPrimaryContainer,
            secondaryContainer = ReaderColors.LightSecondaryContainer,
            onSecondaryContainer = ReaderColors.LightOnSecondaryContainer,
            error = ReaderColors.LightError,
            onError = ReaderColors.LightOnError,
        )
    }
}
