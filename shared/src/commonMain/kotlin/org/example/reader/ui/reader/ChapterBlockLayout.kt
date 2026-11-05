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
    /** 本页首块在整章内的下标，是进度与恢复的唯一基准。 */
    val startParaIndex: Int,
)

/** 生产测量分派：文本走 Compose 测量，图片本轮明确不支持。 */
internal fun measureChapterBlock(
    measurer: TextMeasurer,
    block: ChapterBlock,
    bodyStyle: TextStyle,
    widthPx: Int,
): Int = measureChapterBlock(block) { text ->
    measurer.measure(
        text = text,
        style = bodyStyle,
        constraints = Constraints(maxWidth = widthPx),
    ).size.height
}

/** 可注入文本测量的块测量边界，便于主机测试覆盖未启用的图片分支。 */
internal fun measureChapterBlock(
    block: ChapterBlock,
    measureText: (AnnotatedString) -> Int,
): Int = when (block) {
    is TextBlock -> measureText(block.toAnnotatedString())
    is ImageBlock -> error("ImageBlock measurement is not supported")
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
                startParaIndex = consumed,
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
        listOf(ReaderPage(blocks = emptyList(), showTitle = true, startParaIndex = 0))
    }
}

/** 比例恢复出的逻辑块所在页；末块下标等于块数时自然落到最后页。 */
internal fun pageForBlock(pages: List<ReaderPage>, blockIndex: Int): Int {
    if (pages.isEmpty()) return 0
    return pages.indexOfLast { it.startParaIndex <= blockIndex }.coerceIn(0, pages.lastIndex)
}
