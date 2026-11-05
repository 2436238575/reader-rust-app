package org.example.reader.ui.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.example.reader.core.model.ChapterBlock
import org.example.reader.core.model.ImageBlock
import org.example.reader.core.model.TextBlock
import org.example.reader.core.model.TextSpan
import org.example.reader.core.model.TextSpanStyle

class ChapterBlockLayoutAndroidHostTest {

    @Test
    fun oversizedBlockIsNeverSplitAndOccupiesItsOwnPage() {
        val blocks = textBlocks(3)
        val heights = mapOf(0 to 180, 1 to 20, 2 to 20)

        val pages = paginate(blocks, titleHeightPx = 0, heightPx = 100, gapPx = 10) {
            heights.getValue(it.paraIndex)
        }

        assertEquals(2, pages.size)
        assertEquals(listOf(0), pages[0].blocks.map { it.paraIndex })
        assertEquals(listOf(1, 2), pages[1].blocks.map { it.paraIndex })
    }

    @Test
    fun titleOnlyConsumesFirstPageAndGapOnlyAppliesBetweenBlocks() {
        val pages = paginate(textBlocks(3), titleHeightPx = 40, heightPx = 100, gapPx = 10) { 30 }

        assertEquals(listOf(0, 1), pages.map { it.startBlockIndex })
        assertEquals(listOf(true, false), pages.map { it.showTitle })
        assertEquals(listOf(0), pages[0].blocks.map { it.paraIndex })
        assertEquals(listOf(1, 2), pages[1].blocks.map { it.paraIndex })

        val exactFit = paginate(textBlocks(2), titleHeightPx = 0, heightPx = 100, gapPx = 20) { 40 }
        assertEquals(1, exactFit.size)
    }

    @Test
    fun genericPackingAcceptsTextImageTextThroughInjectedMeasurement() {
        val blocks = listOf<ChapterBlock>(
            textBlock(0),
            ImageBlock(paraIndex = 1, url = "fixture"),
            textBlock(2),
        )

        val pages = paginate(blocks, titleHeightPx = 0, heightPx = 60, gapPx = 10) { 30 }

        assertEquals(3, pages.size)
        assertEquals(listOf(0, 1, 2), pages.flatMap { page -> page.blocks.map { it.paraIndex } })
        assertEquals(listOf(0, 1, 2), pages.map { it.startBlockIndex })
    }

    @Test
    fun imageHeightFollowsAspectRatioAndCaptionParticipatesInMeasurement() {
        // 1400×933（番茄配图实测比例）：宽 500 时高 333，说明文字 + 间距另加
        val landscape = ImageBlock(
            paraIndex = 0,
            url = "a",
            caption = "配图（画师：奈月Oo）",
            width = 1400,
            height = 933,
        )
        val measured = mutableListOf<Pair<String, Int>>()
        val height = measureChapterBlock(landscape, bodyStyle(), widthPx = 500, captionGapPx = 8) { text, style, maxLines ->
            measured += text.text to maxLines
            assertEquals(IMAGE_CAPTION_MAX_LINES, maxLines)
            assertEquals(IMAGE_CAPTION_SCALE, style.fontSize.value / 18f, 0.001f)
            30
        }

        assertEquals(333 + 30 + 8, height)
        assertEquals(listOf("配图（画师：奈月Oo）" to IMAGE_CAPTION_MAX_LINES), measured)

        // 无说明文字时不测文字、不加间距
        val bare = measureChapterBlock(landscape.copy(caption = ""), bodyStyle(), widthPx = 500) { _, _, _ ->
            error("没有说明文字就不该测文字")
        }
        assertEquals(333, bare)

        // 比例未知按 4:3 占位；竖图最多 1.5 倍宽
        assertEquals(375, measureBlockHeight(landscape.copy(caption = "", width = 0, height = 0)))
        assertEquals(750, measureBlockHeight(landscape.copy(caption = "", width = 100, height = 2000)))
    }

    @Test
    fun textMeasurementUsesSharedPlainAnnotatedStringWithoutNoneDecoration() {
        val block = TextBlock(
            paraIndex = 0,
            text = "中文🙂<b>",
            spans = listOf(TextSpan(0, 6, TextSpanStyle.NONE)),
        )

        val height = measureChapterBlock(block, bodyStyle(), widthPx = 500) { annotated, _, maxLines ->
            assertEquals(block.text, annotated.text)
            assertTrue(annotated.spanStyles.isEmpty())
            assertEquals(Int.MAX_VALUE, maxLines)
            42
        }

        assertEquals(42, height)
        assertEquals(block.text, block.toAnnotatedString().text)
        assertTrue(block.toAnnotatedString().spanStyles.isEmpty())
    }

    @Test
    fun emptyContentAndInvalidDimensionsKeepLegacyPageBehavior() {
        val empty = paginate(emptyList(), titleHeightPx = 20, heightPx = 100, gapPx = 10) { 10 }
        assertEquals(1, empty.size)
        assertTrue(empty.single().showTitle)
        assertTrue(empty.single().blocks.isEmpty())
        assertEquals(0, empty.single().startBlockIndex)
        assertTrue(
            paginate(textBlocks(1), titleHeightPx = 0, widthPx = 0, heightPx = 100, gapPx = 10) { 10 }
                .isEmpty(),
        )
        assertFalse(empty.single().blocks.isNotEmpty())
    }

    private fun paginate(
        blocks: List<ChapterBlock>,
        titleHeightPx: Int,
        widthPx: Int = 500,
        heightPx: Int,
        gapPx: Int,
        measure: (ChapterBlock) -> Int,
    ): List<ReaderPage> = paginateBlocks(
        blocks = blocks,
        titleHeightPx = titleHeightPx,
        widthPx = widthPx,
        heightPx = heightPx,
        gapPx = gapPx,
        measureBlock = measure,
    )

    private fun textBlocks(count: Int): List<ChapterBlock> = (0 until count).map(::textBlock)

    private fun textBlock(index: Int): TextBlock =
        TextBlock(paraIndex = index, text = "块$index", spans = emptyList())

    private fun bodyStyle(): TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 32.4.sp)

    /** 只关心几何高度时的简写：说明文字测量固定返回 0。 */
    private fun measureBlockHeight(block: ChapterBlock): Int =
        measureChapterBlock(block, bodyStyle(), widthPx = 500) { _, _, _ -> 0 }
}
