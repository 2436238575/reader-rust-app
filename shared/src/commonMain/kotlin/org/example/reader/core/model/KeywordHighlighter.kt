package org.example.reader.core.model

import org.example.reader.core.highlight.KeywordHighlightRule

/**
 * 关键词高亮（P2-3）。
 *
 * 纯函数：把规则匹配到的区间写成 `TextSpanStyle.Background`，不改文字、不改换行，
 * 因此进度、配图锚点与简繁转换都不受影响。多规则重叠时**先到先得**（按规则顺序），
 * 避免同一段叠加多层底色。
 *
 * 划线（P2-1）与搜索命中（P2-4）以后复用同一套 span，不要另写一份匹配逻辑。
 */
object KeywordHighlighter {

    /** 超过这个长度就不做匹配：关键词高亮是辅助，不能拖慢整章切块。 */
    private const val MAX_TEXT_LENGTH = 20_000

    fun apply(
        blocks: List<ChapterBlock>,
        rules: List<KeywordHighlightRule>,
    ): List<ChapterBlock> {
        val active = rules.filter { it.enabled && it.keyword.isNotBlank() }
        if (active.isEmpty()) return blocks
        return blocks.map { block ->
            if (block !is TextBlock || block.text.length > MAX_TEXT_LENGTH) {
                block
            } else {
                highlight(block, active)
            }
        }
    }

    private fun highlight(block: TextBlock, rules: List<KeywordHighlightRule>): TextBlock {
        val taken = mutableListOf<IntRange>()
        val added = mutableListOf<TextSpan>()
        rules.forEach { rule ->
            val keyword = rule.keyword
            var from = 0
            while (from <= block.text.length - keyword.length) {
                val at = block.text.indexOf(keyword, from, ignoreCase = true)
                if (at < 0) break
                val range = at until at + keyword.length
                val overlaps = taken.any { it.first <= range.last && range.first <= it.last }
                if (!overlaps) {
                    taken += range
                    added += TextSpan(
                        start = range.first,
                        end = range.last + 1,
                        style = TextSpanStyle.Background(rule.normalizedColor),
                    )
                }
                from = at + keyword.length
            }
        }
        if (added.isEmpty()) return block
        // 保留原有的全量 NONE span（渲染时忽略），背景 span 按起点排序，便于测试与调试
        return block.copy(
            spans = (block.spans + added).sortedWith(compareBy({ it.start }, { it.end })),
        )
    }
}
