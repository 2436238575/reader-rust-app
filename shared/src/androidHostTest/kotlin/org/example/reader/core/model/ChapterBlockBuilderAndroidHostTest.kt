package org.example.reader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterBlockBuilderAndroidHostTest {

    @Test
    fun emptyAndBlankContentProduceNoBlocks() {
        assertTrue(ChapterBlockBuilder.build(ChapterContent(content = "")).isEmpty())
        assertTrue(ChapterBlockBuilder.build(ChapterContent(content = " \n\t\n\r\n")).isEmpty())
    }

    @Test
    fun splitTrimFilterAndNumberingExactlyMatchLegacyParagraphs() {
        val raw = "  第一段  \n\n第二段\n   \n 第三段 \n"
        val legacy = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        val blocks = ChapterBlockBuilder.build(ChapterContent(content = raw))

        assertEquals(legacy, blocks.map { (it as TextBlock).text })
        assertEquals(legacy.indices.toList(), blocks.map { it.paraIndex })
    }

    @Test
    fun consecutiveAndLeadingTrailingNewlinesDoNotCreateEmptyBlocks() {
        val blocks = ChapterBlockBuilder.build(ChapterContent(content = "\n\n甲\n\n\n乙\n\n"))

        assertEquals(listOf("甲", "乙"), blocks.map { (it as TextBlock).text })
        assertEquals(listOf(0, 1), blocks.map { it.paraIndex })
    }

    @Test
    fun chineseEmojiAndLiteralHtmlRemainPlainUtf16Text() {
        val blocks = ChapterBlockBuilder.build(
            ChapterContent(content = " 中文🙂 \n <b>不加粗</b> &amp; "),
        )

        val texts = blocks.map { (it as TextBlock).text }
        assertEquals(listOf("中文🙂", "<b>不加粗</b> &amp;"), texts)
        blocks.forEach { block ->
            val text = (block as TextBlock)
            assertEquals(listOf(TextSpan(0, text.text.length, TextSpanStyle.NONE)), text.spans)
        }
        assertEquals(4, (blocks.first() as TextBlock).spans.single().end)
    }

    @Test
    fun everyPureTextBlockHasExactlyOneNoneSpan() {
        val blocks = ChapterBlockBuilder.build(ChapterContent(content = "甲\n乙\n丙"))

        blocks.forEach { block ->
            val text = block as TextBlock
            assertEquals(1, text.spans.size)
            assertEquals(TextSpanStyle.NONE, text.spans.single().style)
            assertEquals(0, text.spans.single().start)
            assertEquals(text.text.length, text.spans.single().end)
        }
    }

    @Test
    fun commentMarkerIsNotAChapterBlockAndDoesNotConsumeBlockIndex() {
        val marker: ChapterElement = CommentMarker(paraIndex = 0)
        val blocks = ChapterBlockBuilder.build(ChapterContent(content = "甲\n乙"))

        assertFalse(marker is ChapterBlock)
        assertEquals(2, blocks.size)
        assertEquals(listOf(0, 1), blocks.map { it.paraIndex })
    }

    @Test
    fun imageAtRawLineSitsBeforeThatParagraphWithoutChangingTextNumbering() {
        // 原始行号 1 = 第二行「乙」；图片插在它前面
        val blocks = ChapterBlockBuilder.build(
            ChapterContent(content = "甲\n乙\n丙"),
            listOf(ChapterImageInfo(url = "u1", paraIndex = 1, caption = " 说明 ", width = 1400, height = 933)),
        )

        assertEquals(listOf("甲", null, "乙", "丙"), blocks.map { (it as? TextBlock)?.text })
        assertEquals(listOf(0, 1, 1, 2), blocks.map { it.paraIndex })
        val image = blocks[1] as ImageBlock
        assertEquals("u1", image.url)
        assertEquals("说明", image.caption)
        assertEquals(1400, image.width)
        assertEquals(933, image.height)
    }

    @Test
    fun imageOnBlankLineAttachesToNextTextBlockAndOutOfRangeGoesToChapterEnd() {
        // 行 1、2 是空行，都归到「后面第一个非空段」（下标 1）
        val blocks = ChapterBlockBuilder.build(
            ChapterContent(content = "甲\n\n\n乙\n丙"),
            listOf(
                ChapterImageInfo(url = "blank", paraIndex = 2),
                ChapterImageInfo(url = "tailNull", paraIndex = null),
                ChapterImageInfo(url = "tailBig", paraIndex = 99),
                ChapterImageInfo(url = "negative", paraIndex = -3),
            ),
        )

        assertEquals(
            listOf("negative", "甲", "blank", "乙", "丙", "tailNull", "tailBig"),
            blocks.map { (it as? ImageBlock)?.url ?: (it as TextBlock).text },
        )
        assertEquals(listOf(0, 0, 1, 1, 2, 3, 3), blocks.map { it.paraIndex })
    }

    @Test
    fun duplicateImageUrlsAreKeptOnceAndBlankUrlsAreDropped() {
        val blocks = ChapterBlockBuilder.build(
            ChapterContent(content = "甲"),
            listOf(
                ChapterImageInfo(url = "same", paraIndex = 0),
                ChapterImageInfo(url = "same", paraIndex = 0),
                ChapterImageInfo(url = "  ", paraIndex = 0),
            ),
        )

        assertEquals(1, blocks.count { it is ImageBlock })
    }

    @Test
    fun captionAlreadyPresentInBodyIsNotRenderedTwice() {
        val blocks = ChapterBlockBuilder.build(
            ChapterContent(content = "甲\n配图（画师：奈月Oo）\n乙"),
            listOf(
                ChapterImageInfo(
                    url = "u",
                    paraIndex = 1,
                    caption = "配图（画师：奈月Oo）",
                ),
            ),
        )

        val image = blocks[1] as ImageBlock
        assertEquals("", image.caption)
        assertEquals("配图（画师：奈月Oo）", (blocks[2] as TextBlock).text)
    }

    @Test
    fun imageOnlyChapterProducesImagesAnchoredAtZero() {
        val blocks = ChapterBlockBuilder.build(
            ChapterContent(content = ""),
            listOf(ChapterImageInfo(url = "only", paraIndex = 0)),
        )

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is ImageBlock)
        assertEquals(0, blocks.single().paraIndex)
    }

    @Test
    fun coreModelPublicSignaturesDoNotExposeComposeTypes() {
        val modelTypes = listOf(
            ChapterElement::class.java,
            ChapterBlock::class.java,
            TextBlock::class.java,
            ImageBlock::class.java,
            CommentMarker::class.java,
            TextSpan::class.java,
        )

        modelTypes.forEach { type ->
            assertFalse(type.name.startsWith("androidx.compose."))
            type.declaredMethods.forEach { method ->
                assertFalse(method.returnType.name.startsWith("androidx.compose."))
                assertTrue(method.parameterTypes.none { it.name.startsWith("androidx.compose.") })
            }
        }
    }
}
