package org.example.reader.core.model

/** 章节内元素的共同定位身份。[paraIndex] 是过滤空段后的连续正文下标。 */
sealed interface ChapterElement {
    val paraIndex: Int
}

/** 参与正文测量、绘制、分页与进度换算的原子块。 */
sealed interface ChapterBlock : ChapterElement

/** 纯文本正文块。[spans] 使用块内 UTF-16 半开区间。 */
data class TextBlock(
    override val paraIndex: Int,
    val text: String,
    val spans: List<TextSpan>,
) : ChapterBlock

/**
 * 章节配图块。
 *
 * [paraIndex] 指向**紧随其后的那个文本块**（章末图等于块数），因此它可能与相邻
 * 文本块的 `paraIndex` 相同——这是锚点身份，不是列表下标。列表下标由块在
 * `blocks` 里的位置决定，进度与分页用列表下标，定位用 `paraIndex`。
 */
data class ImageBlock(
    override val paraIndex: Int,
    val url: String,
    /** 图片下方的说明文字，可为空。 */
    val caption: String = "",
    /** 原始像素宽高，用于占位与等比缩放；任一项非正数按未知比例处理。 */
    val width: Int = 0,
    val height: Int = 0,
) : ChapterBlock

/** 评论锚点：指向同下标目标块，不占正文块序号、块数、页或列表 key。 */
data class CommentMarker(
    override val paraIndex: Int,
) : ChapterElement

/** 块内文本样式范围，[start]  inclusive、[end] exclusive，单位为 UTF-16 code unit。 */
data class TextSpan(
    val start: Int,
    val end: Int,
    val style: TextSpanStyle = TextSpanStyle.NONE,
)

/** 本轮纯文本只使用 [NONE]，不解释任何 HTML 或装饰标记。 */
enum class TextSpanStyle {
    NONE,
}
