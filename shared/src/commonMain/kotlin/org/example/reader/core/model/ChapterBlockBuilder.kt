package org.example.reader.core.model

/**
 * 章节正文唯一的生产切分入口，保证所有阅读容器共享文本、顺序与块下标。
 */
object ChapterBlockBuilder {

    /**
     * 严格沿用旧纯文本口径：按换行切分、逐段 trim、过滤空段，最后从 0 连续编号。
     * 字面 HTML（例如 `<b>`）只是普通文本，不做解释、实体处理或清洗。
     */
    fun build(content: ChapterContent): List<ChapterBlock> = build(content, emptyList())

    /**
     * 带配图的正文切分。
     *
     * 配图的 [ChapterImageInfo.paraIndex] 是**原始行号**（正文按 `\n` 切分，从 0 起，
     * 插在该行之前），而文本块的 `paraIndex` 是**过滤空段后的连续编号**。两者之间
     * 只在这里换算，避免每个渲染容器各算一套：
     * - 空行被过滤，落在空行上的图挂到「它后面第一个非空段」；
     * - 越界行号与 `null` 一律挂到章末（块数）；
     * - 同一锚点有多张图时保持接口返回顺序，先图后文；
     * - 同一 URL 只保留第一张（书源偶发重复返回同一张图，重复渲染只会撑长页面）。
     *
     * 文本块的编号与 `build(content)` 完全一致，加不加图都不改变已有 `paraIndex`。
     */
    fun build(content: ChapterContent, images: List<ChapterImageInfo>): List<ChapterBlock> {
        val lines = content.content.split('\n')
        val texts = ArrayList<TextBlock>(lines.size)
        // 原始行号 → 该行对应的文本块下标（空行指向「后面第一个非空段」）
        val anchorOfLine = IntArray(lines.size)
        var blockIndex = 0
        lines.forEachIndexed { raw, line ->
            anchorOfLine[raw] = blockIndex
            val text = line.trim()
            if (text.isNotEmpty()) {
                texts += TextBlock(
                    paraIndex = blockIndex,
                    text = text,
                    spans = listOf(TextSpan(start = 0, end = text.length)),
                )
                blockIndex++
            }
        }
        val total = texts.size
        if (images.isEmpty()) return texts

        val anchorOfImage: (ChapterImageInfo) -> Int = { image ->
            val line = image.paraIndex
            when {
                line == null -> total
                line < 0 -> 0
                line >= lines.size -> total
                else -> anchorOfLine[line]
            }
        }
        val grouped = images
            .asSequence()
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
            .groupBy(anchorOfImage)

        val blocks = ArrayList<ChapterBlock>(total + grouped.size)
        for (index in 0..total) {
            grouped[index]?.forEach { image ->
                val caption = image.caption.trim()
                // 番茄把说明文字也写进了正文（该段就是「配图（画师：…）」），
                // 正文里已有同一句话时不再重复渲染说明，与前端 insertChapterImages 同规则。
                val duplicated = caption.isNotEmpty() && caption == texts.getOrNull(index)?.text
                blocks += ImageBlock(
                    paraIndex = index,
                    url = image.url,
                    caption = if (duplicated) "" else caption,
                    width = image.width,
                    height = image.height,
                )
            }
            if (index < total) blocks += texts[index]
        }
        return blocks
    }
}
