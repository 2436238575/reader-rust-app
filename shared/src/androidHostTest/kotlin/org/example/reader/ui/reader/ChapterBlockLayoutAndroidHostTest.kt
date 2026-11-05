package org.example.reader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

        assertEquals(listOf(0, 1), pages.map { it.startParaIndex })
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
        assertEquals(listOf(0, 1, 2), pages.map { it.startParaIndex })
    }

    @Test
    fun productionImageMeasurementIsExplicitlyUnsupported() {
        val failure = assertFailsWith<IllegalStateException> {
            measureChapterBlock(ImageBlock(paraIndex = 1, url = "fixture")) {
                error("image must not reach text measurement")
            }
        }

        assertEquals("ImageBlock measurement is not supported", failure.message)
    }

    @Test
    fun textMeasurementUsesSharedPlainAnnotatedStringWithoutNoneDecoration() {
        val block = TextBlock(
            paraIndex = 0,
            text = "中文🙂<b>",
            spans = listOf(TextSpan(0, 6, TextSpanStyle.NONE)),
        )

        val height = measureChapterBlock(block) { annotated ->
            assertEquals(block.text, annotated.text)
            assertTrue(annotated.spanStyles.isEmpty())
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
        assertEquals(0, empty.single().startParaIndex)
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
}
