package org.example.reader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.reader.core.highlight.KeywordHighlightRule
import org.example.reader.ui.reader.toAnnotatedString

class KeywordHighlighterAndroidHostTest {

    private val yellow = KeywordHighlightRule(id = "a", keyword = "苏桃", color = 0x80FFD54AL)
    private val blue = KeywordHighlightRule(id = "b", keyword = "池小橙", color = 0x807CC4FFL)

    @Test
    fun everyOccurrenceGetsItsOwnBackgroundSpan() {
        val block = text("苏桃看着苏桃，没说话")
        val out = KeywordHighlighter.apply(listOf(block), listOf(yellow)).single() as TextBlock as TextBlock

        assertEquals(block.text, out.text, "高亮不能改文字")
        val spans = out.spans.filter { it.style is TextSpanStyle.Background }
        assertEquals(listOf(0 to 2, 4 to 6), spans.map { it.start to it.end })
        assertTrue(spans.all { (it.style as TextSpanStyle.Background).argb == 0x80FFD54AL })
    }

    @Test
    fun latinKeywordsMatchCaseInsensitively() {
        val rule = KeywordHighlightRule(id = "c", keyword = "K18", color = 0x807EE08AL)
        val out = KeywordHighlighter.apply(listOf(text("k18 和 K18")), listOf(rule)).single() as TextBlock
        val spans = out.spans.filter { it.style is TextSpanStyle.Background }
        assertEquals(2, spans.size)
        assertEquals(0, spans[0].start)
    }

    @Test
    fun overlappingRulesAreFirstComeFirstServed() {
        // 「小橙」与「池小橙」重叠：第一条规则先占住 0..3，第二条整段跳过，避免叠两层底色
        val inner = KeywordHighlightRule(id = "d", keyword = "小橙", color = 0x80FF9FB2L)
        val overlapped = KeywordHighlighter.apply(
            listOf(text("池小橙笑了")),
            listOf(blue, inner),
        ).single() as TextBlock
        val overlappedSpans = overlapped.spans.filter { it.style is TextSpanStyle.Background }
        assertEquals(1, overlappedSpans.size)
        assertEquals(0 to 3, overlappedSpans.single().start to overlappedSpans.single().end)
        assertEquals(0x807CC4FFL, (overlappedSpans.single().style as TextSpanStyle.Background).argb)

        // 不重叠的命中各自保留
        val disjoint = KeywordHighlighter.apply(
            listOf(text("池小橙和小橙")),
            listOf(blue, inner),
        ).single() as TextBlock
        assertEquals(
            listOf(0 to 3, 4 to 6),
            disjoint.spans.filter { it.style is TextSpanStyle.Background }
                .map { it.start to it.end },
        )
    }

    @Test
    fun disabledBlankAndNonTextBlocksAreLeftAlone() {
        val disabled = yellow.copy(enabled = false)
        val blank = KeywordHighlightRule(id = "e", keyword = "   ")
        val text = text("苏桃")
        val image = ImageBlock(paraIndex = 0, url = "u")

        val out = KeywordHighlighter.apply(listOf(text, image), listOf(disabled, blank))

        assertEquals(text, out[0])
        assertEquals(image, out[1])
        assertTrue((out[0] as TextBlock).spans.all { it.style == TextSpanStyle.NONE })
    }

    @Test
    fun unmatchedTextKeepsExactlyTheLegacyNoneSpan() {
        val block = text("没有命中的段落")
        val out = KeywordHighlighter.apply(listOf(block), listOf(yellow)).single() as TextBlock

        assertEquals(block, out)
        assertEquals(1, out.spans.size)
        assertEquals(TextSpanStyle.NONE, out.spans.single().style)
    }

    @Test
    fun backgroundSpanRendersAsComposeStyle() {
        val out = KeywordHighlighter.apply(listOf(text("苏桃")), listOf(yellow)).single() as TextBlock
        val annotated = out.toAnnotatedString()

        assertEquals("苏桃", annotated.text)
        assertEquals(1, annotated.spanStyles.size)
        val range = annotated.spanStyles.single()
        assertEquals(0, range.start)
        assertEquals(2, range.end)
        assertEquals(androidx.compose.ui.graphics.Color(0x80FFD54AL), range.item.background)
    }

    private fun text(value: String): TextBlock = TextBlock(
        paraIndex = 0,
        text = value,
        spans = listOf(TextSpan(0, value.length)),
    )
}
