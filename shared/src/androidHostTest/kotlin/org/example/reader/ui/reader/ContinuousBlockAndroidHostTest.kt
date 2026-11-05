package org.example.reader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.example.reader.core.model.ImageBlock
import org.example.reader.core.model.TextBlock

class ContinuousBlockAndroidHostTest {

    @Test
    fun bodyKeyComesFromChapterAndBlockIndexWithoutStringPayload() {
        val body = ContinuousItem.Body(
            chapter = 4,
            blockIndex = 7,
            block = TextBlock(paraIndex = 7, text = "正文可任意变化", spans = emptyList()),
        )

        assertEquals("p:4:7", body.key)
        assertEquals("正文可任意变化", (body.block as TextBlock).text)
    }

    @Test
    fun imageAndFollowingParagraphDoNotShareAKey() {
        // 配图锚点与紧随其后的文本块 paraIndex 相同，key 必须用块列表下标，
        // 否则连续列表会抛 "Key p:4:100 was already used"
        val image = ContinuousItem.Body(
            chapter = 4,
            blockIndex = 100,
            block = ImageBlock(paraIndex = 100, url = "u"),
        )
        val text = ContinuousItem.Body(
            chapter = 4,
            blockIndex = 101,
            block = TextBlock(paraIndex = 100, text = "小橙", spans = emptyList()),
        )

        assertEquals("p:4:100", image.key)
        assertEquals("p:4:101", text.key)
        assertEquals(100, text.block.paraIndex)
    }

    @Test
    fun restoreAnchorUsesHeaderAtStartAndLastBlockAtChapterEnd() {
        assertEquals("h:3", restoreAnchorKey(chapter = 3, fraction = 0f, totalParagraphs = 7))
        assertEquals("h:3", restoreAnchorKey(chapter = 3, fraction = 0.001f, totalParagraphs = 7))
        assertEquals("p:3:6", restoreAnchorKey(chapter = 3, fraction = 1f, totalParagraphs = 7))
        assertEquals("h:3", restoreAnchorKey(chapter = 3, fraction = 1f, totalParagraphs = 0))
    }

    @Test
    fun modeSwitchRejectsOtherChapterOutOfRangeAndUnknownKeys() {
        assertNull(continuousFractionForModeSwitch("p:2:1", currentChapter = 3, totalParagraphs = 7))
        assertNull(continuousFractionForModeSwitch("p:3:7", currentChapter = 3, totalParagraphs = 7))
        assertNull(continuousFractionForModeSwitch("unknown", currentChapter = 3, totalParagraphs = 7))
        assertEquals(0f, continuousFractionForModeSwitch("h:3", currentChapter = 3, totalParagraphs = 7))
    }
}
