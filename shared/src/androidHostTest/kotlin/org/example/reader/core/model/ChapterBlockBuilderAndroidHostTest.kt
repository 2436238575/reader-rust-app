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
