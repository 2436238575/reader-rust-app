package org.example.reader.ui.reader

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 连续阅读恢复状态机的主机测试，不依赖 Android 设备或 Compose 运行帧。 */
class ContinuousTrackerAndroidHostTest {

    private val initialPosition = ContinuousVisiblePosition(key = "p:3:100", index = 120, offset = 40)

    private data class ViewportItem(val key: String, val offset: Int, val size: Int)

    @Test
    fun anchoredIsTerminalAndLateFailureCannotOverwriteSuccess() {
        val tracker = ContinuousTracker(initialChapter = 3)
        val generation = tracker.beginRestore()

        assertTrue(tracker.suppress)
        assertTrue(tracker.markAnchorSucceeded(generation, chapter = 3))
        assertFalse(tracker.suppress)
        assertEquals(ContinuousRestorePhase.ANCHORED, tracker.phase)

        assertFalse(tracker.markAnchorFailed(generation))
        assertFalse(tracker.markAnchorSucceeded(generation, chapter = 3))
        assertEquals(ContinuousRestorePhase.ANCHORED, tracker.phase)
        assertFalse(tracker.suppress)
    }

    @Test
    fun externalRestoreInvalidatesPreviousGenerationImmediately() {
        val tracker = ContinuousTracker(initialChapter = 3)
        val oldGeneration = tracker.beginRestore()

        tracker.requestRestore(chapter = 8)

        assertEquals(8, tracker.expected)
        assertEquals(8, tracker.pendingAnchor)
        assertTrue(tracker.generation > oldGeneration)
        assertTrue(tracker.suppress)
        assertFalse(tracker.markAnchorSucceeded(oldGeneration, chapter = 3))
        assertFalse(tracker.markAnchorFailed(oldGeneration))
        assertEquals(ContinuousRestorePhase.RESTORING, tracker.phase)
    }

    @Test
    fun dragStartOnlyCancelsAnchorAndRealFreshMovementTakesOver() {
        val tracker = ContinuousTracker(initialChapter = 3)
        val generation = tracker.beginRestore()
        tracker.onDragStart(initialPosition)

        assertEquals(ContinuousRestorePhase.DRAG_PENDING, tracker.phase)
        assertTrue(tracker.suppress)
        assertFalse(
            tracker.completeUserTakeOver(
                observed = initialPosition,
                current = initialPosition,
                chapter = 3,
                isScrollInProgress = true,
            ),
        )

        val moved = initialPosition.copy(offset = initialPosition.offset - 36)
        assertFalse(
            tracker.completeUserTakeOver(
                observed = moved,
                current = initialPosition,
                chapter = 3,
                isScrollInProgress = true,
            ),
        )
        assertFalse(
            tracker.completeUserTakeOver(
                observed = moved,
                current = moved,
                chapter = 3,
                isScrollInProgress = false,
            ),
        )
        assertFalse(tracker.markAnchorSucceeded(generation, chapter = 3))
        assertFalse(tracker.markAnchorFailed(generation))

        assertTrue(
            tracker.completeUserTakeOver(
                observed = moved,
                current = moved,
                chapter = 4,
                isScrollInProgress = true,
            ),
        )
        assertEquals(ContinuousRestorePhase.USER_TAKEN_OVER, tracker.phase)
        assertEquals(4, tracker.expected)
        assertFalse(tracker.suppress)
    }

    @Test
    fun failedViewportStaysProtectedUntilUserMovesToAnotherItem() {
        val tracker = ContinuousTracker(initialChapter = 3)
        val generation = tracker.beginRestore()
        val preservedViewport = ContinuousVisiblePosition(key = "p:3:100", index = 120, offset = 40)
        assertTrue(tracker.markAnchorFailed(generation))
        assertTrue(tracker.suppress)
        assertEquals(3, tracker.expected)
        assertEquals(ContinuousRestorePhase.FAILED, tracker.phase)

        tracker.onDragStart(preservedViewport)
        val sameItemOffsetOnly = preservedViewport.copy(offset = preservedViewport.offset - 24)
        assertFalse(
            tracker.completeUserTakeOver(
                observed = sameItemOffsetOnly,
                current = sameItemOffsetOnly,
                chapter = 3,
                isScrollInProgress = true,
            ),
        )
        assertTrue(tracker.suppress)

        val actualBodyPosition = ContinuousVisiblePosition(key = "p:3:101", index = 121, offset = 12)
        assertTrue(
            tracker.completeUserTakeOver(
                observed = actualBodyPosition,
                current = actualBodyPosition,
                chapter = 3,
                isScrollInProgress = true,
            ),
        )
        assertFalse(tracker.suppress)
        assertEquals(ContinuousRestorePhase.USER_TAKEN_OVER, tracker.phase)
    }

    @Test
    fun ordinaryDragAfterSuccessfulAnchorDoesNotSuppressProgressAgain() {
        val tracker = ContinuousTracker(initialChapter = 3)
        val generation = tracker.beginRestore()
        assertTrue(tracker.markAnchorSucceeded(generation, chapter = 3))

        tracker.onDragStart(initialPosition)

        assertEquals(ContinuousRestorePhase.ANCHORED, tracker.phase)
        assertFalse(tracker.suppress)
    }

    @Test
    fun modeSwitchUsesActualVisibleParagraphOfCurrentChapter() {
        assertEquals(
            20f / 22f,
            continuousFractionForModeSwitch(
                key = "p:13:20",
                currentChapter = 13,
                totalParagraphs = 22,
            ),
        )
        assertEquals(
            0f,
            continuousFractionForModeSwitch(
                key = "h:13",
                currentChapter = 13,
                totalParagraphs = 22,
            ),
        )
        assertEquals(
            100f / 110f,
            continuousFractionForModeSwitch(
                key = "p:13:100",
                currentChapter = 13,
                totalParagraphs = 110,
            ),
        )
    }

    @Test
    fun viewportSelectorSkipsClippedTopItemWithoutRequiringLongItemBottom() {
        val items = listOf(
            ViewportItem(key = "p:13:99", offset = -250, size = 346),
            // 底边超出视口不影响它作为正文视口首个位置。
            ViewportItem(key = "p:13:100", offset = 105, size = 3_000),
        )

        val selected = firstContinuousViewportItem(
            items = items,
            viewportStart = 0,
        ) { it.offset }

        assertEquals("p:13:100", selected?.key)
        assertEquals(
            100f / 110f,
            continuousFractionForModeSwitch(
                key = selected?.key,
                currentChapter = 13,
                totalParagraphs = 110,
            ),
        )
    }

    @Test
    fun viewportSelectorHonorsPaddingBoundaryAndHasNoPartialItemFallback() {
        val items = listOf(
            ViewportItem(key = "p:13:99", offset = -97, size = 20),
            ViewportItem(key = "p:13:100", offset = -96, size = 155),
        )
        assertEquals(
            "p:13:100",
            firstContinuousViewportItem(items = items, viewportStart = -96) { it.offset }?.key,
        )
        assertNull(
            firstContinuousViewportItem(
                items = listOf(ViewportItem(key = "p:13:99", offset = -250, size = 346)),
                viewportStart = 0,
            ) { it.offset },
        )
    }

    @Test
    fun modeSwitchRejectsStaleOrInvalidVisibleItems() {
        assertNull(
            continuousFractionForModeSwitch(
                key = "p:12:20",
                currentChapter = 13,
                totalParagraphs = 22,
            ),
        )
        assertNull(
            continuousFractionForModeSwitch(
                key = "p:13:22",
                currentChapter = 13,
                totalParagraphs = 22,
            ),
        )
        assertNull(
            continuousFractionForModeSwitch(
                key = null,
                currentChapter = 13,
                totalParagraphs = 22,
            ),
        )
    }

    @Test
    fun scrollStructureChangeInvalidatesOldGenerationBeforeAnyNewLayoutArrives() {
        val tracker = ContinuousTracker(initialChapter = 14, initialHideReadChapters = false)
        val oldGeneration = tracker.beginRestore()
        assertTrue(tracker.markAnchorSucceeded(oldGeneration, chapter = 14))
        assertFalse(tracker.suppress)

        tracker.requestStructureRestore(chapter = 14, hideReadChapters = true)

        assertTrue(tracker.hideReadChapters)
        assertTrue(tracker.suppress)
        assertEquals(14, tracker.pendingAnchor)
        assertTrue(tracker.generation > oldGeneration)
        assertFalse(tracker.markAnchorSucceeded(oldGeneration, chapter = 19))
        assertFalse(tracker.markAnchorFailed(oldGeneration))
        assertEquals(14, tracker.expected)
    }

    @Test
    fun staleAndPlaceholderSamplesCannotChangeChapterOrProduceSavedPosition() {
        val oldPosition = ContinuousVisiblePosition(key = "p:16:6", index = 82, offset = 0)
        val oldSample = ContinuousPositionSample(oldPosition, total = 100, generation = 3)
        assertNull(
            continuousPositionForSync(
                sample = oldSample,
                currentGeneration = 4,
                currentPosition = oldPosition,
                currentTotal = 100,
            ),
        )

        val pendingPosition = ContinuousVisiblePosition(key = "h:19", index = 4, offset = 0)
        val pendingSample = ContinuousPositionSample(pendingPosition, total = 0, generation = 4)
        assertNull(
            continuousPositionForSync(
                sample = pendingSample,
                currentGeneration = 4,
                currentPosition = pendingPosition,
                currentTotal = 0,
            ),
        )
    }

    @Test
    fun naturalCrossThenLateChapter19SampleCannotPolluteFlushOrReopenPosition() {
        val generation = 4
        val chapter17 = ContinuousVisiblePosition(key = "p:16:40", index = 137, offset = 52)
        var committed = ContinuousReadingPosition(chapter = 15, fraction = 0.2f)

        // 真实上滑到第17章：当前 layout 与 loaded total 一致，允许提交16/0.4。
        continuousPositionForSync(
            sample = ContinuousPositionSample(chapter17, total = 100, generation = generation),
            currentGeneration = generation,
            currentPosition = chapter17,
            currentTotal = 100,
        )?.let { committed = it }
        assertEquals(16, committed.chapter)
        assertEquals(0.4f, committed.fraction)

        val oldChapter19 = ContinuousPositionSample(
            position = ContinuousVisiblePosition(key = "p:18:741", index = 208, offset = 0),
            total = 10_000,
            generation = generation - 1,
        )
        // 旧代际样本被拒绝，不能调用 VM 的 syncContinuousPosition。
        assertNull(
            continuousPositionForSync(
                sample = oldChapter19,
                currentGeneration = generation,
                currentPosition = chapter17,
                currentTotal = 10_000,
            ),
        )

        // 即使迟到样本同代，其排队位置与当前第17章视口不一致，仍必须拒绝。
        assertNull(
            continuousPositionForSync(
                sample = oldChapter19.copy(generation = generation),
                currentGeneration = generation,
                currentPosition = chapter17,
                currentTotal = 10_000,
            ),
        )

        // flushProgress 读取仍是 currentIndex=16 × lastFraction=0.4；重进也从该章恢复。
        val flushedIndex = committed.chapter
        val flushedPosition = (committed.fraction * 10_000).roundToInt()
        assertEquals(16, flushedIndex)
        assertEquals(4_000, flushedPosition)
        assertNotEquals(18, flushedIndex)
        assertNotEquals(741, flushedPosition)
    }

    @Test
    fun pendingToHeaderSameKeyNeedsLoadedTotalAndCurrentLayout() {
        val header = ContinuousVisiblePosition(key = "h:14", index = 0, offset = 48)
        val loadedHeader = ContinuousPositionSample(header, total = 80, generation = 4)

        assertNull(
            continuousPositionForSync(
                sample = loadedHeader,
                currentGeneration = 4,
                currentPosition = header.copy(index = 9),
                currentTotal = 80,
            ),
        )
        assertNull(
            continuousPositionForSync(
                sample = loadedHeader,
                currentGeneration = 4,
                currentPosition = header,
                currentTotal = 0,
            ),
        )
        assertEquals(
            ContinuousReadingPosition(chapter = 14, fraction = 0f),
            continuousPositionForSync(
                sample = loadedHeader,
                currentGeneration = 4,
                currentPosition = header,
                currentTotal = 80,
            ),
        )
    }

    @Test
    fun failedRestoreKeepsDatabaseSuppressedWhileVisiblePositionCanBeHandedOff() {
        val tracker = ContinuousTracker(initialChapter = 13)
        val generation = tracker.beginRestore()
        assertTrue(tracker.markAnchorFailed(generation))
        assertTrue(tracker.suppress)

        // FAILED 不开放连续模式的数据库回传，但切横翻时仍可读取已经可见的真实位置。
        assertEquals(
            20f / 22f,
            continuousFractionForModeSwitch(
                key = "p:13:20",
                currentChapter = 13,
                totalParagraphs = 22,
            ),
        )
        assertTrue(tracker.suppress)
    }
}
