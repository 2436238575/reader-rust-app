package org.example.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.example.reader.core.settings.ReaderFont
import org.example.reader.core.settings.ReaderSettings

/** 所有阅读容器共享的正文排版口径；标题、系统栏 inset 与章尾不属于这里。 */
data class ReaderLineMetrics(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val paragraphSpacing: TextUnit,
    val firstLineIndent: TextUnit,
    /** 正文字族：只有通用字族（Default/Sans/Serif/Mono），见 [ReaderFont]。 */
    val fontFamily: FontFamily = FontFamily.Default,
    /** 正文字重 100..900。 */
    val fontWeight: FontWeight = FontWeight.Normal,
) {
    /** 正文 TextStyle 的唯一生产入口。 */
    fun bodyStyle(color: Color): TextStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        textIndent = TextIndent(firstLine = firstLineIndent),
    )

    /** 列表布局使用 dp，保留旧实现的 sp.toDp 换算。 */
    fun paragraphGapDp(density: Density): Dp = with(density) { paragraphSpacing.toDp() }

    /** 分页测量使用像素，保留旧实现 toPx 后再 toInt 的截断语义。 */
    fun paragraphGapPx(density: Density): Int = with(density) { paragraphSpacing.toPx() }.toInt()

    companion object {
        /** 从原始设置计算字号、行高、段距与恒为两个字宽的首行缩进。 */
        fun from(settings: ReaderSettings, fontPath: String? = null): ReaderLineMetrics = ReaderLineMetrics(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
            paragraphSpacing = (settings.fontSize * settings.paragraphSpacing).sp,
            firstLineIndent = (settings.fontSize * 2).sp,
            fontFamily = settings.fontFamily.toFontFamily(fontPath),
            // 先夹到设置域（100..900）再构造：FontWeight(Int) 只接受 1..1000，
            // 而且超出设置域的值没有对应的 UI 语义（手改 prefs / 跨端同步都可能出现）
            fontWeight = FontWeight(
                settings.fontWeight.coerceIn(
                    ReaderSettings.MIN_FONT_WEIGHT,
                    ReaderSettings.MAX_FONT_WEIGHT,
                ),
            ),
        )
    }
}

/**
 * 字体族映射。
 *
 * 系统字体用 Compose 的通用字族；鸿蒙/小米是内置的 TTF 资源，只在 Android 侧能解析，
 * 所以本函数是 expect（commonMain 不引用 `R`）。
 */
internal expect fun ReaderFont.toFontFamily(cachedPath: String? = null): FontFamily
