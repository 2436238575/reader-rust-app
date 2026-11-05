package org.example.reader.ui.reader

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.example.reader.core.model.TextBlock
import org.example.reader.core.settings.ReadMethod

class ReaderPositionAndroidHostTest {

    @Test
    fun zeroAndSingleBlockBoundariesMatchLegacyFormula() {
        assertEquals(0f, blockFraction(3, 0))
        assertEquals(0, blockAtFraction(0.8f, 0))
        assertEquals(0f, blockFraction(0, 1))
        assertEquals(1f, blockFraction(1, 1))
        assertEquals(1, blockAtFraction(1f, 1))
    }

    @Test
    fun sevenBlockBoundsAndOutOfRangeInputsAreClamped() {
        assertEquals(0f, blockFraction(-2, 7))
        assertEquals(1f, blockFraction(9, 7))
        assertEquals(0, blockAtFraction(-1f, 7))
        assertEquals(7, blockAtFraction(2f, 7))
        // 与旧 roundToInt 口径一致：NaN 不被额外归一化，而是明确抛出。
        assertFailsWith<IllegalArgumentException> { blockAtFraction(Float.NaN, 7) }
    }

    @Test
    fun everyBlockIncludingChapterEndRoundTrips() {
        listOf(1, 7).forEach { total ->
            for (block in 0..total) {
                assertEquals(block, blockAtFraction(blockFraction(block, total), total))
            }
        }
    }

    @Test
    fun quantizedServerPositionsRoundTripEveryLogicalBlock() {
        val total = 7
        for (block in 0..total) {
            val quantized = (blockFraction(block, total) * 10000).roundToInt() / 10000f
            assertEquals(block, blockAtFraction(quantized, total))
        }
        for (position in 0..10000) {
            val fraction = position / 10000f
            val block = blockAtFraction(fraction, total)
            assertEquals(block, blockAtFraction(blockFraction(block, total), total))
        }
    }

    @Test
    fun horizontalRestoreTargetsPageContainingLogicalBlock() {
        val pages = listOf(
            ReaderPage(blocks = textBlocks(0, 2), showTitle = true, startParaIndex = 0),
            ReaderPage(blocks = textBlocks(2, 2), showTitle = false, startParaIndex = 2),
            ReaderPage(blocks = textBlocks(4, 1), showTitle = false, startParaIndex = 4),
        )

        assertEquals(2, pageForBlock(pages, blockAtFraction(0.8f, 5)))
        assertEquals(2, pageForBlock(pages, blockAtFraction(1f, 5)))
        assertEquals(0, pageForBlock(pages, blockAtFraction(0f, 5)))
    }

    @Test
    fun repeatedHorizontalReflowsNeverTurnMidPageTargetIntoEarlierPageStart() {
        val session = HorizontalRestoreSession(blockFraction(40, 100))
        val generations = listOf(
            listOf(
                ReaderPage(textBlocks(0, 32), showTitle = true, startParaIndex = 0),
                ReaderPage(textBlocks(32, 18), showTitle = false, startParaIndex = 32),
                ReaderPage(textBlocks(50, 50), showTitle = false, startParaIndex = 50),
            ),
            listOf(
                ReaderPage(textBlocks(0, 30), showTitle = true, startParaIndex = 0),
                ReaderPage(textBlocks(30, 15), showTitle = false, startParaIndex = 30),
                ReaderPage(textBlocks(45, 55), showTitle = false, startParaIndex = 45),
            ),
            listOf(
                ReaderPage(textBlocks(0, 20), showTitle = true, startParaIndex = 0),
                ReaderPage(textBlocks(20, 25), showTitle = false, startParaIndex = 20),
                ReaderPage(textBlocks(45, 55), showTitle = false, startParaIndex = 45),
            ),
        )

        generations.forEach { pages ->
            val targetBlock = session.restoreBlock(100)
            val targetPage = pageForBlock(pages, targetBlock)
            assertEquals(40, targetBlock)
            assertEquals(1, targetPage)
            assertTrue(pages[targetPage].blocks.any { it.paraIndex == 40 })
            // 程序化恢复只滚动到 targetPage，不调用 onUserPage，也不改锚。
            assertEquals(blockFraction(40, 100), session.anchorFraction)
        }
    }

    @Test
    fun horizontalAnchorAdvancesOnlyAfterRealUserPageChange() {
        val session = HorizontalRestoreSession(blockFraction(40, 100))

        assertEquals(blockFraction(40, 100), session.anchorFraction)
        assertEquals(blockFraction(55, 100), session.onUserPage(startBlock = 55, totalBlocks = 100))
        assertEquals(55, session.restoreBlock(100))
    }

    @Test
    fun chapterEndRestoreNormalizesOnceAndNewAnchorDrivesLaterReflow() {
        val session = HorizontalRestoreSession(1f)
        val firstLayout = listOf(
            ReaderPage(textBlocks(0, 80), showTitle = true, startParaIndex = 0),
            ReaderPage(textBlocks(80, 20), showTitle = false, startParaIndex = 80),
        )

        val chapterEnd = session.restoreTarget(firstLayout, totalBlocks = 100)
        assertEquals(1, chapterEnd.pageIndex)
        assertEquals(0.8f, chapterEnd.normalizedFraction)
        assertEquals(1f, session.anchorFraction)

        // 生产路径在 pager.scrollToPage 并建立 settledPage 后才提交归一。
        session.completeRestore(chapterEnd)
        assertEquals(0.8f, session.anchorFraction)

        val nextLayout = listOf(
            ReaderPage(textBlocks(0, 70), showTitle = true, startParaIndex = 0),
            ReaderPage(textBlocks(70, 15), showTitle = false, startParaIndex = 70),
            ReaderPage(textBlocks(85, 15), showTitle = false, startParaIndex = 85),
        )
        val reflow = session.restoreTarget(nextLayout, totalBlocks = 100)
        assertEquals(1, reflow.pageIndex)
        assertNull(reflow.normalizedFraction)
        assertEquals(0.8f, session.anchorFraction)
    }

    @Test
    fun midPageRestoreDoesNotNormalizeTargetBlockToPageStart() {
        val original = 0.4f
        val session = HorizontalRestoreSession(original)
        val pages = listOf(
            ReaderPage(textBlocks(0, 32), showTitle = true, startParaIndex = 0),
            ReaderPage(textBlocks(32, 18), showTitle = false, startParaIndex = 32),
            ReaderPage(textBlocks(50, 50), showTitle = false, startParaIndex = 50),
        )

        val restore = session.restoreTarget(pages, totalBlocks = 100)
        assertEquals(1, restore.pageIndex)
        assertTrue(pages[restore.pageIndex].blocks.any { it.paraIndex == 40 })
        assertNull(restore.normalizedFraction)
        session.completeRestore(restore)
        assertEquals(original, session.anchorFraction)
    }

    @Test
    fun scroll9091KeepsSameLogicalBlockWhenEnteringHorizontal() {
        val scroll9091 = 0.9091f
        val session = HorizontalRestoreSession(scroll9091)
        val pages = listOf(
            ReaderPage(textBlocks(0, 85), showTitle = true, startParaIndex = 0),
            ReaderPage(textBlocks(85, 15), showTitle = false, startParaIndex = 85),
            ReaderPage(textBlocks(100, 10), showTitle = false, startParaIndex = 100),
        )

        val restore = session.restoreTarget(pages, totalBlocks = 110)
        assertEquals(100, blockAtFraction(scroll9091, 110))
        assertEquals(2, restore.pageIndex)
        assertNull(restore.normalizedFraction)
        session.completeRestore(restore)
        assertEquals(scroll9091, session.anchorFraction)
    }

    @Test
    fun explicitScrollChapterEndNormalizesToRealLastPageStartAndReturnsToScrollStably() {
        val total = 97
        val lastBlock = blockFraction(96, total)
        val continuousHandoff = continuousFractionForModeSwitch(
            key = "p:15:96",
            currentChapter = 15,
            totalParagraphs = total,
        )
        assertEquals(lastBlock, continuousHandoff)

        // 连续锚定可能已把内存比例转成 96/97；只有显式章末标记能恢复原始 1f 语义。
        listOf(1f, lastBlock).forEach { continuousSource ->
            val horizontalFraction = modeSwitchFraction(
                sourceMethod = ReadMethod.Scroll,
                currentFraction = continuousSource,
                continuousFraction = continuousHandoff,
                chapterEndRestore = true,
            )
            assertEquals(1f, horizontalFraction)

            val session = HorizontalRestoreSession(horizontalFraction)
            val firstLayout = listOf(
                ReaderPage(textBlocks(0, 90), showTitle = true, startParaIndex = 0),
                ReaderPage(textBlocks(90, 7), showTitle = false, startParaIndex = 90),
            )
            val chapterEnd = session.restoreTarget(firstLayout, total)
            assertEquals(1, chapterEnd.pageIndex)
            assertEquals(blockFraction(90, total), chapterEnd.normalizedFraction)
            assertEquals(9_278, (requireNotNull(chapterEnd.normalizedFraction) * 10_000).roundToInt())

            session.completeRestore(chapterEnd)
            assertEquals(blockFraction(90, total), session.anchorFraction)

            // 后续重排仍以真实页首块90为锚，不重复归一，也不漂到末块96。
            val reflow = session.restoreTarget(
                pages = listOf(
                    ReaderPage(textBlocks(0, 70), showTitle = true, startParaIndex = 0),
                    ReaderPage(textBlocks(70, 20), showTitle = false, startParaIndex = 70),
                    ReaderPage(textBlocks(90, 7), showTitle = false, startParaIndex = 90),
                ),
                totalBlocks = total,
            )
            assertEquals(90, session.restoreBlock(total))
            assertEquals(2, reflow.pageIndex)
            assertNull(reflow.normalizedFraction)

            // 再切 Scroll 时沿用横翻内存真源，恢复同一页首块90。
            val scrollFraction = modeSwitchFraction(
                sourceMethod = ReadMethod.Horizontal,
                currentFraction = session.anchorFraction,
                continuousFraction = continuousHandoff,
                chapterEndRestore = false,
            )
            assertEquals(blockFraction(90, total), scrollFraction)
            assertEquals("p:15:90", restoreAnchorKey(15, scrollFraction, total))
        }
    }

    @Test
    fun ordinaryContinuousPositionsIncludingNaturalLastBlockNeverNormalizeAsChapterEnd() {
        val total = 97
        val pages = listOf(
            ReaderPage(textBlocks(0, 90), showTitle = true, startParaIndex = 0),
            ReaderPage(textBlocks(90, 7), showTitle = false, startParaIndex = 90),
        )

        listOf(0.4f, 0.9091f, blockFraction(96, total)).forEach { visibleFraction ->
            val handoff = modeSwitchFraction(
                sourceMethod = ReadMethod.Scroll,
                currentFraction = visibleFraction,
                continuousFraction = visibleFraction,
                chapterEndRestore = false,
            )
            assertEquals(visibleFraction, handoff)

            val session = HorizontalRestoreSession(handoff)
            val restore = session.restoreTarget(pages, total)
            assertNull(restore.normalizedFraction)
            session.completeRestore(restore)
            assertEquals(visibleFraction, session.anchorFraction)
        }
    }

    @Test
    fun everyFiveModeTransitionUsesTheSourceViewportOrPagerTruth() {
        ReadMethod.entries.forEach { source ->
            ReadMethod.entries.forEach { _ ->
                val actual = modeSwitchFraction(
                    sourceMethod = source,
                    currentFraction = 0.1f,
                    singleChapterFraction = 0.4f,
                    continuousFraction = 0.6f,
                )
                val expected = when (source) {
                    ReadMethod.SlideUp -> 0.4f
                    ReadMethod.Scroll, ReadMethod.Scroll2 -> 0.6f
                    ReadMethod.Horizontal, ReadMethod.DualPage -> 0.1f
                }
                assertEquals(expected, actual, "source=$source")
            }
        }
    }

    @Test
    fun singleChapterHandoffDistinguishesHeaderBodyAndTrailingItems() {
        assertNull(singleChapterParagraphFraction(firstVisibleIndex = null, totalBlocks = 7))
        assertNull(singleChapterParagraphFraction(firstVisibleIndex = 1, totalBlocks = 0))
        assertEquals(0f, singleChapterParagraphFraction(firstVisibleIndex = 0, totalBlocks = 7))
        assertEquals(3f / 7f, singleChapterParagraphFraction(firstVisibleIndex = 4, totalBlocks = 7))
        assertEquals(1f, singleChapterParagraphFraction(firstVisibleIndex = 8, totalBlocks = 7))
    }

    private fun textBlocks(start: Int, count: Int): List<TextBlock> =
        (start until start + count).map { TextBlock(it, "块$it", emptyList()) }
}
