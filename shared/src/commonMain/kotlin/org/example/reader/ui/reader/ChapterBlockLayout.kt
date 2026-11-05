package org.example.reader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import org.example.reader.core.model.ChapterBlock
import org.example.reader.core.model.ImageBlock
import org.example.reader.core.model.TextBlock

/** 每页装哪些正文块；[showTitle] 只让本章第一页带标题。 */
internal data class ReaderPage(
    val blocks: List<ChapterBlock>,
    val showTitle: Boolean,
    /**
     * 本页首块在**块列表**里的下标（含图片块），是进度与恢复的唯一基准。
     * 不是 `ChapterBlock.paraIndex`——后者是文本段编号，插入图片后两者不再相等。
     */
    val startBlockIndex: Int,
)

/**
 * 生产测量分派：文本走 Compose 测量，图片按原始宽高比占位（说明文字一并计入）。
 *
 * [captionGapPx] 是图片与说明之间的间距（dp 换算成像素），由调用方按当前 density 给出，
 * 保证与渲染时的 `Spacer(IMAGE_CAPTION_GAP_DP)` 一致。
 */
internal fun measureChapterBlock(
    measurer: TextMeasurer,
    block: ChapterBlock,
    bodyStyle: TextStyle,
    widthPx: Int,
    captionGapPx: Int = 0,
): Int = measureChapterBlock(block, bodyStyle, widthPx, captionGapPx) { text, style, maxLines ->
    measurer.measure(
        text = text,
        style = style,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = widthPx),
    ).size.height
}

/**
 * 可注入文本测量的块测量边界。
 *
 * 图片高度是纯几何计算（与字体无关），说明文字才需要测量；测试通过 [measureText]
 * 注入固定高度，避免依赖真实字体度量。
 */
internal fun measureChapterBlock(
    block: ChapterBlock,
    bodyStyle: TextStyle,
    widthPx: Int,
    captionGapPx: Int = 0,
    measureText: (AnnotatedString, TextStyle, Int) -> Int,
): Int = when (block) {
    is TextBlock -> measureText(block.toAnnotatedString(), bodyStyle, Int.MAX_VALUE)
    is ImageBlock -> {
        val captionHeight = if (block.caption.isBlank()) {
            0
        } else {
            measureText(
                AnnotatedString(block.caption),
                bodyStyle.imageCaptionStyle(),
                IMAGE_CAPTION_MAX_LINES,
            ) + captionGapPx
        }
        imageBlockHeightPx(widthPx, block) + captionHeight
    }
}

/** 标题测量规则保持原样：仅首页计入标题高度，并额外留出三倍段距。 */
internal fun measureChapterTitle(
    measurer: TextMeasurer,
    title: String,
    titleStyle: TextStyle,
    widthPx: Int,
    gapPx: Int,
): Int = if (title.isBlank()) {
    0
} else {
    measurer.measure(
        text = AnnotatedString(title),
        style = titleStyle,
        constraints = Constraints(maxWidth = widthPx),
    ).size.height + gapPx * 3
}

/**
 * 按块整段装箱，不拆超长块。
 *
 * 函数本身不判断块类型；[measureBlock] 由生产 Compose 测量或测试固定高度注入。
 * 标题只影响首页，段距只加在同页相邻两块之间，超长块独占一页。
 */
internal fun paginateBlocks(
    blocks: List<ChapterBlock>,
    titleHeightPx: Int,
    widthPx: Int,
    heightPx: Int,
    gapPx: Int,
    measureBlock: (ChapterBlock) -> Int,
): List<ReaderPage> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()

    val pages = mutableListOf<ReaderPage>()
    var current = mutableListOf<ChapterBlock>()
    var used = titleHeightPx
    var first = true
    var consumed = 0

    fun flush() {
        if (current.isNotEmpty() || first) {
            pages += ReaderPage(
                blocks = current.toList(),
                showTitle = first,
                startBlockIndex = consumed,
            )
            consumed += current.size
        }
        current = mutableListOf()
        used = 0
        first = false
    }

    blocks.forEach { block ->
        val height = measureBlock(block)
        val need = if (current.isEmpty()) height else height + gapPx
        if (current.isNotEmpty() && used + need > heightPx) {
            flush()
        }
        current += block
        used += if (current.size == 1) height else height + gapPx
    }
    if (current.isNotEmpty()) flush()
    return pages.ifEmpty {
        listOf(ReaderPage(blocks = emptyList(), showTitle = true, startBlockIndex = 0))
    }
}

/** 比例恢复出的逻辑块所在页；末块下标等于块数时自然落到最后页。 */
internal fun pageForBlock(pages: List<ReaderPage>, blockIndex: Int): Int {
    if (pages.isEmpty()) return 0
    return pages.indexOfLast { it.startBlockIndex <= blockIndex }.coerceIn(0, pages.lastIndex)
}
