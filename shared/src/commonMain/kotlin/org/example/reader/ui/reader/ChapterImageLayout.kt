package org.example.reader.ui.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import org.example.reader.core.model.ImageBlock
import kotlin.math.roundToInt

/**
 * 配图块的几何与文字口径。
 *
 * 测量（分页）与渲染必须用同一套数字：分页时按这里算出占位高度，渲染时
 * 用 `aspectRatio` + 同样的说明文字样式把它填满，否则换字号/翻页后图片
 * 会压到下一段或留下一条空白。
 */

/** 说明文字相对正文字号的比例。 */
internal const val IMAGE_CAPTION_SCALE = 0.8f

/** 说明文字最多两行，超出省略；测量与渲染必须用同一个上限。 */
internal const val IMAGE_CAPTION_MAX_LINES = 2

/** 图片与说明文字之间的间距（dp），测量与渲染共用。 */
internal const val IMAGE_CAPTION_GAP_DP = 4

/** 原始宽高比未知时的兜底：按 4:3 横图占位，避免加载前高度为 0 造成跳动。 */
private const val FALLBACK_ASPECT = 0.75f

/** 高宽比下限：极扁的条幅也至少留 0.2 倍宽，否则点不中。 */
private const val MIN_ASPECT = 0.2f

/** 高宽比上限：竖图最多 1.5 倍宽，不能一张图占满两屏。 */
private const val MAX_ASPECT = 1.5f

/** 高宽比（height / width），已夹到可读区间。 */
internal fun imageBlockAspect(block: ImageBlock): Float {
    val raw = if (block.width > 0 && block.height > 0) {
        block.height.toFloat() / block.width
    } else {
        FALLBACK_ASPECT
    }
    return raw.coerceIn(MIN_ASPECT, MAX_ASPECT)
}

/** 图片本体的占位高度（像素）；说明文字另算。 */
internal fun imageBlockHeightPx(contentWidthPx: Int, block: ImageBlock): Int =
    (contentWidthPx * imageBlockAspect(block)).roundToInt()

/** 说明文字样式：与正文同字体，字号 0.8 倍、行高 1.4 倍，不参与首行缩进。 */
internal fun TextStyle.imageCaptionStyle(): TextStyle = copy(
    fontSize = fontSize * IMAGE_CAPTION_SCALE,
    lineHeight = fontSize * IMAGE_CAPTION_SCALE * 1.4f,
    textIndent = TextIndent.None,
)
