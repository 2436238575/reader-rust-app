package org.example.reader.ui.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.example.reader.core.settings.ReaderFont
import java.io.File

/**
 * 字体族映射（Android 侧）。
 *
 * 鸿蒙 / 小米不随包内置：仓库 `fonts/` 存**未修改的原始 TTF**，首次选用时由
 * `FontStore` 下载到应用私有目录，`cachedPath` 为 null 时回退系统字体。
 * 两家许可都禁止修改字体文件（因此不做子集化）并要求标注来源；
 * 下载完成后 `FontStore.revision` 自增，阅读器重组即可解析到真实字面。
 * 只有 Regular 一个字重，字重 700 时不会合成加粗（系统字族才会）。
 */
internal actual fun ReaderFont.toFontFamily(cachedPath: String?): FontFamily = when (this) {
    ReaderFont.System -> FontFamily.Default
    ReaderFont.Heiti -> FontFamily.SansSerif
    ReaderFont.Songti -> FontFamily.Serif
    ReaderFont.Mono -> FontFamily.Monospace
    // 鸿蒙/小米按需下载：没有缓存文件时回退系统字体（UI 显示下载提示），
    // 下载完成后 revision 自增触发重组，这里就能解析到真实字体
    ReaderFont.Harmony, ReaderFont.MiSans -> cachedPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::fileFontFamily)
        ?: FontFamily.Default
}

/**
 * 文件字体族缓存。
 *
 * `Font(File)` 会在构造时就 `Typeface.createFromFile`（解析 7.9MB 字体），
 * 而 `ReaderLineMetrics.from` 在每次重组都会调用；不缓存的话每帧都会重新解析，
 * 滑动直接掉帧。文件下载后内容不再变化，按路径缓存是安全的。
 */
private val fileFontFamilies = mutableMapOf<String, FontFamily>()

private fun fileFontFamily(path: String): FontFamily = synchronized(fileFontFamilies) {
    fileFontFamilies.getOrPut(path) {
        FontFamily(Font(File(path), weight = FontWeight.Normal))
    }
}
