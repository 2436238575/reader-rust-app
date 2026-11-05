package org.example.reader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.example.reader.core.model.ChapterBlockBuilder
import org.example.reader.core.model.ChapterContent

class ChapterBlockSnapshotAndroidHostTest {

    @Test
    fun snapshotRetainsChapterVersionAndCompleteSourceIdentity() {
        val source = ChapterContent(
            content = "正文",
            edited = true,
            versionId = 41,
            versionKind = "user",
        )
        val snapshot = ChapterBlockSnapshot(3, source, ChapterBlockBuilder.build(source))
        val state = ReaderUiState(currentIndex = 3, body = snapshot)

        assertEquals(3, snapshot.chapterIndex)
        assertEquals(41, snapshot.source.versionId)
        assertEquals("user", snapshot.source.versionKind)
        assertEquals(source, snapshot.source)
        assertEquals(3, state.contentIndex)
        assertEquals(snapshot.blocks, state.blocks)
        assertEquals(true, state.isEdited)
    }

    @Test
    fun contentVersionOrChapterChangeProducesDifferentSnapshotKey() {
        val original = ChapterContent(content = "旧正文", versionId = null)
        val snapshot = ChapterBlockSnapshot(2, original, ChapterBlockBuilder.build(original))
        val contentChanged = original.copy(content = "新正文")
        val newContent = ChapterBlockSnapshot(2, contentChanged, ChapterBlockBuilder.build(contentChanged))
        val newVersion = ChapterBlockSnapshot(
            2,
            original.copy(versionId = 9),
            ChapterBlockBuilder.build(original),
        )
        val newChapter = snapshot.copy(chapterIndex = 3)

        assertNotEquals(snapshot, newContent)
        assertNotEquals(snapshot, newVersion)
        assertNotEquals(snapshot, newChapter)
        assertEquals("旧正文", snapshot.source.content)
        assertEquals("新正文", newContent.source.content)
    }

    @Test
    fun sameContentRefreshRevisionChangesSnapshotRestoreIdentity() {
        val content = ChapterContent(content = "完全相同的正文", versionId = 7)
        val blocks = ChapterBlockBuilder.build(content)
        val original = ChapterBlockSnapshot(
            chapterIndex = 23,
            source = content,
            blocks = blocks,
        )
        val refreshed = original.copy(revision = original.revision + 1)

        assertEquals(0, original.revision)
        assertEquals(1, refreshed.revision)
        assertEquals(original.chapterIndex, refreshed.chapterIndex)
        assertEquals(original.source, refreshed.source)
        assertEquals(original.blocks, refreshed.blocks)
        assertNotEquals(original, refreshed)
    }

    @Test
    fun emptyStateDerivedPropertiesHaveNoSecondStorage() {
        val state = ReaderUiState()

        assertEquals(-1, state.contentIndex)
        assertEquals(emptyList(), state.blocks)
        assertFalse(state.isEdited)
        assertNull(state.body)
    }
}
