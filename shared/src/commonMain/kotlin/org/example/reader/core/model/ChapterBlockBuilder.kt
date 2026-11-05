package org.example.reader.core.model

/** 章节正文唯一的生产切分入口，保证所有阅读容器共享文本、顺序与块下标。 */
object ChapterBlockBuilder {

    /**
     * 严格沿用旧纯文本口径：按换行切分、逐段 trim、过滤空段，最后从 0 连续编号。
     * 字面 HTML（例如 `<b>`）只是普通文本，不做解释、实体处理或清洗。
     */
    fun build(content: ChapterContent): List<ChapterBlock> =
        content.content.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text ->
                TextBlock(
                    paraIndex = index,
                    text = text,
                    spans = listOf(TextSpan(start = 0, end = text.length)),
                )
            }
}
