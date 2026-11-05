package org.example.reader.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 简繁转换的**真实实现**测试（androidHostTest 直接跑 opencc4j）。
 *
 * 这些断言同时是选型依据：词组级转换必须正确，否则「头发→頭發」这类一简对多繁
 * 会露馅；换行数必须不变，否则配图的 paraIndex 会错位。
 */
class ChineseConverterAndroidHostTest {

    private val converter = OpenccChineseConverter()

    @Test
    fun traditionalUsesPhraseLevelConversionNotCharByChar() {
        assertEquals("頭髮", converter.convert("头发", ChineseMode.Traditional))
        assertEquals("一個乾枯的樹幹", converter.convert("一个干枯的树干", ChineseMode.Traditional))
        assertEquals("生命不息，奮鬥不止", converter.convert("生命不息，奋斗不止", ChineseMode.Traditional))
    }

    @Test
    fun traditionalMatchesTheWebConverterTaiwanVocabulary() {
        // 前端 opencc-js 用 from:'cn', to:'tw'：軟體 / 滑鼠 / 矽二極體
        assertEquals("軟體", converter.convert("软件", ChineseMode.Traditional))
        assertEquals("滑鼠", converter.convert("鼠标", ChineseMode.Traditional))
        assertEquals(
            "滑鼠裡面的矽二極體壞了",
            converter.convert("鼠标里面的硅二极管坏了", ChineseMode.Traditional),
        )
    }

    @Test
    fun simplifiedModeConvertsTraditionalSourceBack() {
        assertEquals(
            "生命不息，奋斗不止，头发",
            converter.convert("生命不息，奮鬥不止，頭髮", ChineseMode.Simplified),
        )
    }

    @Test
    fun conversionKeepsLineCountSoImageAnchorsStayValid() {
        val text = "第一段，头发。\n\n第三段，软件。\n"
        val out = converter.convert(text, ChineseMode.Traditional)

        assertEquals(text.count { it == '\n' }, out.count { it == '\n' })
        assertEquals(text.split('\n').size, out.split('\n').size)
        assertEquals("第一段，頭髮。\n\n第三段，軟體。\n", out)
    }

    @Test
    fun asciiEmojiAndEmptyTextPassThrough() {
        val raw = "ABC 123 🙂 !@#"
        assertEquals(raw, converter.convert(raw, ChineseMode.Traditional))
        assertEquals(raw, converter.convert(raw, ChineseMode.Simplified))
        assertEquals("", converter.convert("", ChineseMode.Traditional))
        assertEquals("", converter.convert("", ChineseMode.Simplified))
    }

    @Test
    fun unknownModeKeyFallsBackToSimplified() {
        assertEquals(ChineseMode.Simplified, ChineseMode.fromKey(null))
        assertEquals(ChineseMode.Simplified, ChineseMode.fromKey(""))
        assertEquals(ChineseMode.Simplified, ChineseMode.fromKey("tw"))
        assertEquals(ChineseMode.Traditional, ChineseMode.fromKey("traditional"))
        assertEquals("traditional", ChineseMode.Traditional.key)
        assertEquals("simplified", ChineseMode.Simplified.key)
    }
}
